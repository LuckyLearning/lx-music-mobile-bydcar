#define _GNU_SOURCE
/* SPDX-License-Identifier: Apache-2.0
 * QuickJS session for the isolated Android process. Native calls block in a Java callback
 * that the parent broker implements. This file does not open sockets or files.
 */
#include <jni.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "quickjs.h"

#define WIRE_MAX (1024 * 1024)
#define SCRIPT_MAX (256 * 1024)

typedef struct {
    JSRuntime *rt;
    JSContext *ctx;
    jobject callback;
    jmethodID rpc;
    JNIEnv *env;
    pthread_mutex_t mu;
    int rpc_count;
    int fatal;
    int interrupted;
    double session_deadline;
    double cpu_deadline;
    double cpu_budget_ms;
    char *session_key;
    int preload_used;
} Session;

static double now_ms(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec * 1000.0 + t.tv_nsec / 1000000.0;
}

static int interrupt_cb(JSRuntime *rt, void *opaque) {
    Session *s = opaque;
    (void)rt;
    if (now_ms() > s->cpu_deadline || now_ms() > s->session_deadline) {
        s->interrupted = 1;
        return 1;
    }
    return 0;
}

static void arm_cpu(Session *s) {
    s->cpu_deadline = now_ms() + s->cpu_budget_ms;
    s->interrupted = 0;
}

static const char *ops[] = {"native", "timer", "str2b64", "b642buf", "md5", "aes", "rsa"};

static JSValue rpc(JSContext *ctx, JSValueConst self, int argc, JSValueConst *argv, int magic) {
    Session *s = JS_GetContextOpaque(ctx);
    (void)self;
    if (!s || !s->env || !s->callback) return JS_ThrowInternalError(ctx, "host disconnected");
    if (++s->rpc_count > 512) return JS_ThrowInternalError(ctx, "RPC quota");
    JSValue packet = JS_NewObject(ctx);
    JSValue args = JS_NewArray(ctx);
    JS_SetPropertyStr(ctx, packet, "op", JS_NewString(ctx, "rpc"));
    JS_SetPropertyStr(ctx, packet, "id", JS_NewInt32(ctx, s->rpc_count));
    JS_SetPropertyStr(ctx, packet, "fn", JS_NewString(ctx, ops[magic]));
    for (int i = 0; i < argc; i++) JS_SetPropertyUint32(ctx, args, (uint32_t)i, JS_DupValue(ctx, argv[i]));
    JS_SetPropertyStr(ctx, packet, "args", args);
    JSValue encoded = JS_JSONStringify(ctx, packet, JS_UNDEFINED, JS_UNDEFINED);
    JS_FreeValue(ctx, packet);
    size_t n = 0;
    const char *text = JS_ToCStringLen(ctx, &n, encoded);
    JS_FreeValue(ctx, encoded);
    if (!text || n > WIRE_MAX) {
        if (text) JS_FreeCString(ctx, text);
        return JS_ThrowRangeError(ctx, "wire limit");
    }
    JNIEnv *env = s->env;
    jstring request = (*env)->NewStringUTF(env, text);
    JS_FreeCString(ctx, text);
    if (!request) return JS_ThrowInternalError(ctx, "host disconnected");
    jstring reply = (*env)->CallObjectMethod(env, s->callback, s->rpc, request);
    (*env)->DeleteLocalRef(env, request);
    if ((*env)->ExceptionCheck(env) || !reply) {
        (*env)->ExceptionClear(env);
        s->fatal = 71;
        return JS_ThrowInternalError(ctx, "host disconnected");
    }
    const char *r = (*env)->GetStringUTFChars(env, reply, NULL);
    jsize rn = (*env)->GetStringUTFLength(env, reply);
    JSValue response = JS_ParseJSON(ctx, r ? r : "", r ? (size_t)rn : 0, "host");
    if (r) (*env)->ReleaseStringUTFChars(env, reply, r);
    (*env)->DeleteLocalRef(env, reply);
    if (JS_IsException(response)) return response;
    JSValue ok = JS_GetPropertyStr(ctx, response, "ok");
    int valid = JS_ToBool(ctx, ok);
    JS_FreeValue(ctx, ok);
    if (valid <= 0) {
        JS_FreeValue(ctx, response);
        return JS_ThrowInternalError(ctx, "host operation rejected");
    }
    JSValue v = JS_GetPropertyStr(ctx, response, "value");
    JS_FreeValue(ctx, response);
    return v;
}

static JSValue noop(JSContext *ctx, JSValueConst self, int argc, JSValueConst *argv) {
    (void)ctx; (void)self; (void)argc; (void)argv;
    return JS_UNDEFINED;
}

static int classify(Session *s, JSContext *ctx) {
    JSValue e = JS_GetException(ctx);
    const char *m = JS_ToCString(ctx, e);
    int oom = m && strstr(m, "out of memory");
    int quota = m && (strstr(m, "RPC quota") || strstr(m, "wire limit"));
    if (m) JS_FreeCString(ctx, m);
    JS_FreeValue(ctx, e);
    if (s->interrupted || oom || quota || s->fatal) {
        if (s->fatal != 72) s->fatal = 70;
        return -2;
    }
    return -1;
}

static int run_jobs(Session *s) {
    JSContext *job = NULL;
    int jobs = 0;
    int r;
    while ((r = JS_ExecutePendingJob(s->rt, &job)) > 0) {
        if (++jobs > 2048 || now_ms() > s->session_deadline) {
            s->fatal = 70;
            return -2;
        }
    }
    if (r < 0) return classify(s, job ? job : s->ctx);
    return 0;
}

static char *copy_prop(JSContext *ctx, JSValue obj, const char *name) {
    JSValue v = JS_GetPropertyStr(ctx, obj, name);
    if (JS_IsUndefined(v) || JS_IsNull(v)) {
        JS_FreeValue(ctx, v);
        return NULL;
    }
    const char *s = JS_ToCString(ctx, v);
    JS_FreeValue(ctx, v);
    if (!s) return NULL;
    size_t n = strlen(s);
    char *copy = malloc(n + 1);
    if (!copy) return NULL;
    memcpy(copy, s, n + 1);
    JS_FreeCString(ctx, s);
    return copy;
}

static int prop_i32(JSContext *ctx, JSValue obj, const char *name, int32_t *out) {
    JSValue v = JS_GetPropertyStr(ctx, obj, name);
    int rc = JS_ToInt32(ctx, out, v);
    JS_FreeValue(ctx, v);
    return rc;
}

static int eval_text(Session *s, const char *code, size_t n, const char *name, JSValue *out) {
    arm_cpu(s);
    JSValue value = JS_Eval(s->ctx, code, n, name, JS_EVAL_TYPE_GLOBAL);
    if (JS_IsException(value)) {
        JS_FreeValue(s->ctx, value);
        return classify(s, s->ctx);
    }
    int jobs = run_jobs(s);
    if (jobs < 0) {
        JS_FreeValue(s->ctx, value);
        *out = JS_UNDEFINED;
        return jobs;
    }
    *out = value;
    return 0;
}

static char *result_json(JSContext *ctx, int id, int ok, JSValue value) {
    JSValue o = JS_NewObject(ctx);
    JS_SetPropertyStr(ctx, o, "op", JS_NewString(ctx, "result"));
    JS_SetPropertyStr(ctx, o, "id", JS_NewInt32(ctx, id));
    JS_SetPropertyStr(ctx, o, "ok", JS_NewBool(ctx, ok));
    if (!ok) JS_SetPropertyStr(ctx, o, "error", JS_NewString(ctx, "rejected"));
    else if (JS_IsString(value) || JS_IsNumber(value) || JS_IsBool(value) || JS_IsNull(value))
        JS_SetPropertyStr(ctx, o, "value", JS_DupValue(ctx, value));
    if (ok == 0 && ctx) {
        /* keep the failure code available to the parent without the script text */
    }
    JSValue encoded = JS_JSONStringify(ctx, o, JS_UNDEFINED, JS_UNDEFINED);
    JS_FreeValue(ctx, o);
    size_t n = 0;
    const char *s = JS_ToCStringLen(ctx, &n, encoded);
    JS_FreeValue(ctx, encoded);
    if (!s) return NULL;
    char *copy = malloc(n + 1);
    if (copy) memcpy(copy, s, n + 1);
    JS_FreeCString(ctx, s);
    return copy;
}

static void install(Session *s) {
    JSValue global = JS_GetGlobalObject(s->ctx);
    JSValue console = JS_NewObject(s->ctx);
    const char *names[] = {
        "__lx_native_call__",
        "__lx_native_call__set_timeout",
        "__lx_native_call__utils_str2b64",
        "__lx_native_call__utils_b642buf",
        "__lx_native_call__utils_str2md5",
        "__lx_native_call__utils_aes_encrypt",
        "__lx_native_call__utils_rsa_encrypt",
    };
    for (int i = 0; i < 7; i++)
        JS_SetPropertyStr(s->ctx, global, names[i], JS_NewCFunctionMagic(s->ctx, rpc, names[i], 0, JS_CFUNC_generic_magic, i));
    const char *logs[] = {"log", "info", "warn", "error", "group", "groupEnd", "debug"};
    for (int i = 0; i < 7; i++) JS_SetPropertyStr(s->ctx, console, logs[i], JS_NewCFunction(s->ctx, noop, logs[i], 0));
    JS_SetPropertyStr(s->ctx, global, "console", console);
    JS_FreeValue(s->ctx, global);
    JS_SetContextOpaque(s->ctx, s);
}

JNIEXPORT jlong JNICALL Java_lx_compat_GateNative_sessionOpen(JNIEnv *env, jclass cls, jobject callback) {
    (void)cls;
    if (!callback) return 0;
    Session *s = calloc(1, sizeof(Session));
    if (!s) return 0;
    pthread_mutex_init(&s->mu, NULL);
    s->cpu_budget_ms = 3000;
    s->session_deadline = now_ms() + 30000;
    s->rt = JS_NewRuntime();
    if (!s->rt) { free(s); return 0; }
    JS_SetMemoryLimit(s->rt, 32 * 1024 * 1024);
    JS_SetMaxStackSize(s->rt, 2 * 1024 * 1024);
    JS_SetInterruptHandler(s->rt, interrupt_cb, s);
    s->ctx = JS_NewContext(s->rt);
    if (!s->ctx) { JS_FreeRuntime(s->rt); free(s); return 0; }
    s->callback = (*env)->NewGlobalRef(env, callback);
    jclass c = (*env)->GetObjectClass(env, callback);
    s->rpc = (*env)->GetMethodID(env, c, "rpc", "(Ljava/lang/String;)Ljava/lang/String;");
    (*env)->DeleteLocalRef(env, c);
    if (!s->rpc) { (*env)->DeleteGlobalRef(env, s->callback); JS_FreeContext(s->ctx); JS_FreeRuntime(s->rt); free(s); return 0; }
    install(s);
    return (jlong)s;
}

JNIEXPORT jstring JNICALL Java_lx_compat_GateNative_sessionCommand(JNIEnv *env, jclass cls, jlong handle, jstring command) {
    (void)cls;
    Session *s = (Session *)handle;
    if (!s || !command) return (*env)->NewStringUTF(env, "{\"op\":\"result\",\"id\":0,\"ok\":false,\"error\":\"rejected\"}");
    if (pthread_mutex_trylock(&s->mu) != 0) return (*env)->NewStringUTF(env, "{\"op\":\"result\",\"id\":0,\"ok\":false,\"error\":\"rejected\"}");
    if (now_ms() > s->session_deadline) {
        pthread_mutex_unlock(&s->mu);
        return (*env)->NewStringUTF(env, "{\"op\":\"result\",\"id\":0,\"ok\":false,\"error\":\"rejected\"}");
    }
    s->env = env;
    jsize n = (*env)->GetStringUTFLength(env, command);
    const char *text = (*env)->GetStringUTFChars(env, command, NULL);
    arm_cpu(s);
    JSValue cmd = text ? JS_ParseJSON(s->ctx, text, (size_t)n, "host command") : JS_EXCEPTION;
    if (text) (*env)->ReleaseStringUTFChars(env, command, text);
    int32_t id = 0;
    int rc = -1;
    JSValue produced = JS_UNDEFINED;
    if (!JS_IsException(cmd)) {
        prop_i32(s->ctx, cmd, "id", &id);
        char *op = copy_prop(s->ctx, cmd, "op");
        if (!op) rc = -1;
        else if (strcmp(op, "preload") == 0) {
            char *code = copy_prop(s->ctx, cmd, "code");
            if (s->preload_used || !code || strlen(code) > SCRIPT_MAX) rc = -1;
            else { s->preload_used = 1; rc = eval_text(s, code, strlen(code), "preload.js", &produced); }
            free(code);
        } else if (strcmp(op, "setup") == 0) {
            arm_cpu(s);
            JSValue g = JS_GetGlobalObject(s->ctx);
            JSValue fn = JS_GetPropertyStr(s->ctx, g, "lx_setup");
            char *key = copy_prop(s->ctx, cmd, "key");
            char *sid = copy_prop(s->ctx, cmd, "scriptId");
            char *name = copy_prop(s->ctx, cmd, "name");
            char *desc = copy_prop(s->ctx, cmd, "description");
            char *version = copy_prop(s->ctx, cmd, "version");
            char *author = copy_prop(s->ctx, cmd, "author");
            char *homepage = copy_prop(s->ctx, cmd, "homepage");
            char *raw = copy_prop(s->ctx, cmd, "rawScript");
            free(s->session_key);
            s->session_key = key ? strdup(key) : NULL;
            JSValue args[8];
            const char *vals[] = {key, sid, name, desc, version, author, homepage, raw};
            for (int i = 0; i < 8; i++) args[i] = JS_NewString(s->ctx, vals[i] ? vals[i] : "");
            produced = JS_Call(s->ctx, fn, g, 8, args);
            for (int i = 0; i < 8; i++) JS_FreeValue(s->ctx, args[i]);
            JS_FreeValue(s->ctx, fn);
            JS_FreeValue(s->ctx, g);
            free(sid); free(name); free(desc); free(version); free(author); free(homepage); free(raw); free(key);
            if (JS_IsException(produced)) {
                JS_FreeValue(s->ctx, produced);
                produced = JS_UNDEFINED;
                rc = classify(s, s->ctx);
            } else {
                int jobs = run_jobs(s);
                rc = jobs < 0 ? jobs : 0;
                if (jobs < 0) { JS_FreeValue(s->ctx, produced); produced = JS_UNDEFINED; }
            }
        } else if (strcmp(op, "eval") == 0) {
            char *code = copy_prop(s->ctx, cmd, "code");
            if (!code || strlen(code) > SCRIPT_MAX) rc = -1;
            else rc = eval_text(s, code, strlen(code), "user.js", &produced);
            free(code);
        } else if (strcmp(op, "call") == 0) {
            char *action = copy_prop(s->ctx, cmd, "action");
            char *data = copy_prop(s->ctx, cmd, "data");
            arm_cpu(s);
            JSValue global = JS_GetGlobalObject(s->ctx);
            JSValue fn = JS_GetPropertyStr(s->ctx, global, "__lx_native__");
            JSValue args[3];
            args[0] = JS_NewString(s->ctx, s->session_key ? s->session_key : "");
            args[1] = JS_NewString(s->ctx, action ? action : "");
            int argc = 2;
            if (data) { args[2] = JS_NewString(s->ctx, data); argc = 3; }
            produced = JS_Call(s->ctx, fn, global, argc, args);
            for (int i = 0; i < argc; i++) JS_FreeValue(s->ctx, args[i]);
            JS_FreeValue(s->ctx, fn);
            JS_FreeValue(s->ctx, global);
            free(action); free(data);
            if (JS_IsException(produced)) {
                JS_FreeValue(s->ctx, produced);
                produced = JS_UNDEFINED;
                rc = classify(s, s->ctx);
            } else {
                int jobs = run_jobs(s);
                rc = jobs < 0 ? jobs : 0;
                if (jobs < 0) { JS_FreeValue(s->ctx, produced); produced = JS_UNDEFINED; }
            }
        }
        free(op);
        JS_FreeValue(s->ctx, cmd);
    } else {
        classify(s, s->ctx);
    }
    s->env = NULL;
    if (s->fatal) {
        pthread_mutex_unlock(&s->mu);
        char buf[64];
        snprintf(buf, sizeof buf, "{\"op\":\"result\",\"id\":%d,\"ok\":false,\"error\":\"rejected\"}", id);
        return (*env)->NewStringUTF(env, buf);
    }
    char *json = result_json(s->ctx, id, rc == 0, produced);
    JS_FreeValue(s->ctx, produced);
    pthread_mutex_unlock(&s->mu);
    jstring out = (*env)->NewStringUTF(env, json ? json : "{\"op\":\"result\",\"id\":0,\"ok\":false,\"error\":\"rejected\"}");
    free(json);
    return out;
}

JNIEXPORT void JNICALL Java_lx_compat_GateNative_sessionClose(JNIEnv *env, jclass cls, jlong handle) {
    (void)cls;
    Session *s = (Session *)handle;
    if (!s) return;
    pthread_mutex_lock(&s->mu);
    if (s->callback) (*env)->DeleteGlobalRef(env, s->callback);
    free(s->session_key);
    if (s->ctx) JS_FreeContext(s->ctx);
    if (s->rt) JS_FreeRuntime(s->rt);
    pthread_mutex_unlock(&s->mu);
    pthread_mutex_destroy(&s->mu);
    free(s);
}
