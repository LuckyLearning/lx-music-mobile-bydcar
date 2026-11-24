/* SPDX-License-Identifier: Apache-2.0
 * Android JNI entry used only by the isolated :source process of the test app.
 * It is not the Linux bubblewrap runner and it is not, by itself, a sandbox.
 *
 * Reviewed hazards:
 * - evalSynthetic used to share one static deadline across calls. Each call now
 *   keeps its deadline on the stack and a mutex rejects re-entry.
 * - The source length is GetStringUTFLength, not strlen.
 * - Exception text is discarded. A hostile script cannot echo itself into the result.
 * - No file, process, reflection, or socket helpers are registered with QuickJS.
 * - The 64KiB source cap and 512KiB JS stack are probe limits. Official preload is
 *   not executed through this entry.
 */
#include <jni.h>
#include <pthread.h>
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <time.h>
#include "quickjs.h"

#ifndef __ANDROID_API__
#define __ANDROID_API__ 0
#endif
#define API_TEXT_1(v) #v
#define API_TEXT(v) API_TEXT_1(v)

const char lx_gate_build_id[] = "lx-gate-api:" API_TEXT(__ANDROID_API__);
const int lx_gate_min_api = __ANDROID_API__;

static pthread_mutex_t eval_mu = PTHREAD_MUTEX_INITIALIZER;

typedef struct {
    double deadline;
} EvalLimit;

static double now_ms(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec * 1000.0 + t.tv_nsec / 1000000.0;
}

static int interrupt_cb(JSRuntime *rt, void *opaque) {
    EvalLimit *limit = opaque;
    (void)rt;
    return limit != NULL && now_ms() > limit->deadline;
}

static int eval_code(const char *text, size_t n, int memory_bytes, int cpu_ms, char *out, size_t out_cap) {
    if (pthread_mutex_trylock(&eval_mu) != 0) return 75;
    EvalLimit limit;
    limit.deadline = now_ms() + cpu_ms;
    JSRuntime *rt = JS_NewRuntime();
    int code = 71;
    if (!rt) {
        pthread_mutex_unlock(&eval_mu);
        return 71;
    }
    JS_SetMemoryLimit(rt, (size_t)memory_bytes);
    JS_SetMaxStackSize(rt, 512 * 1024);
    JS_SetInterruptHandler(rt, interrupt_cb, &limit);
    JSContext *ctx = JS_NewContext(rt);
    if (ctx) {
        JSValue value = JS_Eval(ctx, text, n, "synthetic.js", JS_EVAL_TYPE_GLOBAL);
        if (JS_IsException(value)) {
            JSValue exc = JS_GetException(ctx);
            JS_FreeValue(ctx, exc);
            code = 70;
        } else {
            code = 0;
            JSContext *job = NULL;
            int jobs = 0;
            int r;
            while ((r = JS_ExecutePendingJob(rt, &job)) > 0) {
                if (++jobs > 2048 || now_ms() > limit.deadline) {
                    code = 70;
                    break;
                }
            }
            if (r < 0) code = 70;
            if (code == 0 && out && out_cap > 0) {
                out[0] = 0;
                if (JS_IsString(value)) {
                    const char *s = JS_ToCString(ctx, value);
                    if (s) {
                        size_t len = strlen(s);
                        if (len >= out_cap) len = out_cap - 1;
                        memcpy(out, s, len);
                        out[len] = 0;
                        JS_FreeCString(ctx, s);
                    }
                }
            }
        }
        JS_FreeValue(ctx, value);
        JS_FreeContext(ctx);
    }
    JS_FreeRuntime(rt);
    pthread_mutex_unlock(&eval_mu);
    return code;
}

static int check_args(jstring source, jint memory_bytes, jint cpu_ms) {
    if (!source || memory_bytes < 1024 * 1024 || memory_bytes > 32 * 1024 * 1024 || cpu_ms < 50 || cpu_ms > 5000) return 0;
    return 1;
}

JNIEXPORT jint JNICALL Java_lx_compat_GateNative_nativeApiLevel(JNIEnv *env, jclass cls) {
    (void)env;
    (void)cls;
    return lx_gate_min_api;
}

JNIEXPORT jint JNICALL Java_lx_compat_GateNative_evalSynthetic(JNIEnv *env, jclass cls, jstring source, jint memory_bytes, jint cpu_ms) {
    (void)cls;
    if (!check_args(source, memory_bytes, cpu_ms)) return 71;
    jsize n = (*env)->GetStringUTFLength(env, source);
    if (n < 0 || n > 65536) return 71;
    const char *text = (*env)->GetStringUTFChars(env, source, NULL);
    if (!text) return 71;
    int code = eval_code(text, (size_t)n, memory_bytes, cpu_ms, NULL, 0);
    (*env)->ReleaseStringUTFChars(env, source, text);
    return code;
}

JNIEXPORT jstring JNICALL Java_lx_compat_GateNative_evalReport(JNIEnv *env, jclass cls, jstring source, jint memory_bytes, jint cpu_ms) {
    (void)cls;
    if (!check_args(source, memory_bytes, cpu_ms)) return (*env)->NewStringUTF(env, "71");
    jsize n = (*env)->GetStringUTFLength(env, source);
    if (n < 0 || n > 65536) return (*env)->NewStringUTF(env, "71");
    const char *text = (*env)->GetStringUTFChars(env, source, NULL);
    if (!text) return (*env)->NewStringUTF(env, "71");
    char value[129];
    value[0] = 0;
    int code = eval_code(text, (size_t)n, memory_bytes, cpu_ms, value, sizeof value);
    (*env)->ReleaseStringUTFChars(env, source, text);
    char line[160];
    if (code == 0) snprintf(line, sizeof line, "0\n%s", value);
    else snprintf(line, sizeof line, "%d", code);
    return (*env)->NewStringUTF(env, line);
}
