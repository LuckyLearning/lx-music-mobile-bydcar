/* SPDX-License-Identifier: Apache-2.0
 * Android JNI probe for the same QuickJS core limits. No file, process, reflection or socket API
 * is exported. This is not the Linux seccomp runner and it is not an isolatedProcess sandbox.
 */
#include <jni.h>
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

static double deadline;
static double now_ms(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec * 1000.0 + t.tv_nsec / 1000000.0;
}
static int interrupt_cb(JSRuntime *rt, void *opaque) {
    (void)rt;
    (void)opaque;
    return now_ms() > deadline;
}

JNIEXPORT jint JNICALL Java_lx_compat_GateNative_evalSynthetic(JNIEnv *env, jclass cls, jstring source, jint memory_bytes, jint cpu_ms) {
    (void)cls;
    if (!source || memory_bytes < 1024 * 1024 || memory_bytes > 32 * 1024 * 1024 || cpu_ms < 50 || cpu_ms > 5000) return 71;
    const char *text = (*env)->GetStringUTFChars(env, source, NULL);
    if (!text) return 71;
    size_t n = strlen(text);
    if (n > 65536) {
        (*env)->ReleaseStringUTFChars(env, source, text);
        return 71;
    }
    JSRuntime *rt = JS_NewRuntime();
    if (!rt) {
        (*env)->ReleaseStringUTFChars(env, source, text);
        return 71;
    }
    JS_SetMemoryLimit(rt, (size_t)memory_bytes);
    JS_SetMaxStackSize(rt, 512 * 1024);
    deadline = now_ms() + cpu_ms;
    JS_SetInterruptHandler(rt, interrupt_cb, NULL);
    JSContext *ctx = JS_NewContext(rt);
    int code = 71;
    if (ctx) {
        JSValue value = JS_Eval(ctx, text, n, "synthetic.js", JS_EVAL_TYPE_GLOBAL);
        if (JS_IsException(value)) code = 70;
        else {
            code = 0;
            JSContext *job = NULL;
            int jobs = 0;
            int r;
            while ((r = JS_ExecutePendingJob(rt, &job)) > 0) {
                if (++jobs > 2048) {
                    code = 70;
                    break;
                }
            }
            if (r < 0) code = 70;
        }
        JS_FreeValue(ctx, value);
        JS_FreeContext(ctx);
    }
    JS_FreeRuntime(rt);
    (*env)->ReleaseStringUTFChars(env, source, text);
    (void)lx_gate_min_api;
    (void)lx_gate_build_id;
    return code;
}
