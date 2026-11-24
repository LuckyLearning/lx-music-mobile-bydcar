/* SPDX-License-Identifier: Apache-2.0
 * Linux gate runner. QuickJS core only: no quickjs-libc, no std/os, no Java, no Node.
 * The parent process is the wall-clock watchdog. This process also stops on its own session
 * deadline while blocked in poll, and on a per-eval CPU deadline via the QuickJS interrupt.
 * Seccomp is an allow-list. A denied syscall returns EPERM; it is not a successful call.
 */
#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include <errno.h>
#include <poll.h>
#include <signal.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>
#include <linux/audit.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include "quickjs.h"

#define WIRE_MAX (8 * 1024 * 1024)
#define SCRIPT_MAX (1024 * 1024)

static int rpc_count;
static int fatal_code;
static int interrupted;
static double session_deadline;
static double cpu_deadline;
static double cpu_budget_ms;
static char *spill;
static size_t spill_len;
static char *preload;
static size_t preload_len;
static int preload_used;

static double now_ms(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec * 1000.0 + t.tv_nsec / 1000000.0;
}

static void xexit(int code) {
    syscall(__NR_exit_group, code);
    for (;;) {}
}

static void die(const char *s) {
    size_t n = strlen(s);
    if (n) write(2, s, n);
    write(2, "\n", 1);
    xexit(71);
}

static void write_all(int fd, const char *s, size_t n) {
    size_t off = 0;
    while (off < n) {
        ssize_t w = write(fd, s + off, n - off);
        if (w < 0) {
            if (errno == EINTR) continue;
            die("write failed");
        }
        off += (size_t)w;
    }
}

static void emit(const char *s) {
    write_all(1, s, strlen(s));
    write_all(1, "\n", 1);
}

static int interrupt_cb(JSRuntime *rt, void *opaque) {
    (void)rt;
    (void)opaque;
    double n = now_ms();
    if (n > cpu_deadline || n > session_deadline) {
        interrupted = 1;
        return 1;
    }
    return 0;
}

static void arm_cpu(void) {
    cpu_deadline = now_ms() + cpu_budget_ms;
    interrupted = 0;
}

static char *read_frame(void) {
    size_t cap = spill_len + 4096;
    if (cap > WIRE_MAX) cap = WIRE_MAX;
    char *buf = malloc(cap + 1);
    size_t n = 0;
    if (!buf) die("protocol allocation");
    if (spill_len) {
        memcpy(buf, spill, spill_len);
        n = spill_len;
        free(spill);
        spill = NULL;
        spill_len = 0;
    }
    for (;;) {
        for (size_t i = 0; i < n; i++) {
            if (buf[i] != '\n') continue;
            buf[i] = 0;
            size_t rest = n - i - 1;
            if (rest) {
                spill = malloc(rest);
                if (!spill) die("protocol allocation");
                memcpy(spill, buf + i + 1, rest);
                spill_len = rest;
            }
            return buf;
        }
        if (now_ms() > session_deadline) {
            free(buf);
            fatal_code = 72;
            return NULL;
        }
        struct pollfd pfd;
        pfd.fd = 0;
        pfd.events = POLLIN;
        int rc = poll(&pfd, 1, 100);
        if (rc < 0) {
            if (errno == EINTR) continue;
            free(buf);
            fatal_code = 71;
            return NULL;
        }
        if (rc == 0) continue;
        if (n >= WIRE_MAX) {
            free(buf);
            die("protocol limit");
        }
        if (n + 2048 > cap) {
            size_t next = cap * 2;
            if (next > WIRE_MAX) next = WIRE_MAX;
            char *grown = realloc(buf, next + 1);
            if (!grown) die("protocol allocation");
            buf = grown;
            cap = next;
        }
        ssize_t r = read(0, buf + n, cap - n);
        if (r < 0) {
            if (errno == EINTR) continue;
            free(buf);
            fatal_code = 71;
            return NULL;
        }
        if (r == 0) {
            free(buf);
            return NULL;
        }
        n += (size_t)r;
    }
}

static const char *ops[] = {"native", "timer", "str2b64", "b642buf", "md5", "aes", "rsa"};

static JSValue rpc(JSContext *ctx, JSValueConst self, int argc, JSValueConst *argv, int magic) {
    (void)self;
    if (++rpc_count > 512) return JS_ThrowInternalError(ctx, "RPC quota");
    JSValue packet = JS_NewObject(ctx);
    JSValue args = JS_NewArray(ctx);
    JS_SetPropertyStr(ctx, packet, "op", JS_NewString(ctx, "rpc"));
    JS_SetPropertyStr(ctx, packet, "id", JS_NewInt32(ctx, rpc_count));
    JS_SetPropertyStr(ctx, packet, "fn", JS_NewString(ctx, ops[magic]));
    for (int i = 0; i < argc; i++) JS_SetPropertyUint32(ctx, args, (uint32_t)i, JS_DupValue(ctx, argv[i]));
    JS_SetPropertyStr(ctx, packet, "args", args);
    JSValue encoded = JS_JSONStringify(ctx, packet, JS_UNDEFINED, JS_UNDEFINED);
    JS_FreeValue(ctx, packet);
    size_t n = 0;
    const char *s = JS_ToCStringLen(ctx, &n, encoded);
    if (!s || n > WIRE_MAX) {
        if (s) JS_FreeCString(ctx, s);
        JS_FreeValue(ctx, encoded);
        return JS_ThrowRangeError(ctx, "wire limit");
    }
    write_all(1, s, n);
    write_all(1, "\n", 1);
    JS_FreeCString(ctx, s);
    JS_FreeValue(ctx, encoded);
    char *line = read_frame();
    if (!line) {
        if (!fatal_code) fatal_code = 71;
        return JS_ThrowInternalError(ctx, "host disconnected");
    }
    JSValue response = JS_ParseJSON(ctx, line, strlen(line), "host");
    free(line);
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
    (void)ctx;
    (void)self;
    (void)argc;
    (void)argv;
    return JS_UNDEFINED;
}

static int classify(JSContext *ctx) {
    if (fatal_code == 72) return -2;
    JSValue e = JS_GetException(ctx);
    const char *m = JS_ToCString(ctx, e);
    int oom = m && strstr(m, "out of memory");
    int quota = m && (strstr(m, "RPC quota") || strstr(m, "wire limit"));
    if (m) JS_FreeCString(ctx, m);
    JS_FreeValue(ctx, e);
    if (interrupted || oom || quota || fatal_code) {
        if (fatal_code != 72) fatal_code = 70;
        const char *note = "JS execution rejected (details redacted)\n";
        write(2, note, strlen(note));
        return -2;
    }
    return -1;
}

static int run_jobs(JSContext *ctx) {
    JSContext *job = NULL;
    int jobs = 0;
    int r;
    while ((r = JS_ExecutePendingJob(JS_GetRuntime(ctx), &job)) > 0) {
        if (++jobs > 2048) {
            fatal_code = 70;
            return -2;
        }
        if (fatal_code) return -2;
    }
    if (r < 0) return classify(job ? job : ctx);
    return 0;
}

static void send_result(JSContext *ctx, int id, int ok, JSValue value) {
    JSValue o = JS_NewObject(ctx);
    JS_SetPropertyStr(ctx, o, "op", JS_NewString(ctx, "result"));
    JS_SetPropertyStr(ctx, o, "id", JS_NewInt32(ctx, id));
    JS_SetPropertyStr(ctx, o, "ok", JS_NewBool(ctx, ok));
    if (!ok) JS_SetPropertyStr(ctx, o, "error", JS_NewString(ctx, "rejected"));
    else if (JS_IsString(value) || JS_IsNumber(value) || JS_IsBool(value) || JS_IsNull(value))
        JS_SetPropertyStr(ctx, o, "value", JS_DupValue(ctx, value));
    JSValue encoded = JS_JSONStringify(ctx, o, JS_UNDEFINED, JS_UNDEFINED);
    JS_FreeValue(ctx, o);
    size_t n = 0;
    const char *s = JS_ToCStringLen(ctx, &n, encoded);
    if (!s) {
        JS_FreeValue(ctx, encoded);
        die("result encode");
    }
    write_all(1, s, n);
    write_all(1, "\n", 1);
    JS_FreeCString(ctx, s);
    JS_FreeValue(ctx, encoded);
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
    if (!copy) die("protocol allocation");
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

#define ALLOW(nr) BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, (nr), 0, 1), BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW)

static void harden(void) {
    struct rlimit cpu = {8, 8};
    struct rlimit fds = {16, 16};
    struct rlimit core = {0, 0};
    struct rlimit as = {192 * 1024 * 1024, 192 * 1024 * 1024};
    struct rlimit fsize = {0, 0};
    if (setrlimit(RLIMIT_CPU, &cpu) || setrlimit(RLIMIT_NOFILE, &fds) || setrlimit(RLIMIT_CORE, &core) ||
        setrlimit(RLIMIT_AS, &as) || setrlimit(RLIMIT_FSIZE, &fsize))
        die("rlimit unavailable");
    struct sock_filter filter[] = {
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, arch)),
#if defined(__x86_64__)
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, AUDIT_ARCH_X86_64, 1, 0),
#elif defined(__aarch64__)
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, AUDIT_ARCH_AARCH64, 1, 0),
#else
#error Unsupported host architecture
#endif
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_KILL_PROCESS),
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr)),
        BPF_JUMP(BPF_JMP | BPF_JGE | BPF_K, 0x40000000, 0, 1),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_KILL_PROCESS),
        ALLOW(__NR_read), ALLOW(__NR_write), ALLOW(__NR_close), ALLOW(__NR_fstat), ALLOW(__NR_lseek),
        ALLOW(__NR_brk), ALLOW(__NR_mmap), ALLOW(__NR_mprotect), ALLOW(__NR_munmap), ALLOW(__NR_madvise),
#ifdef __NR_mremap
        ALLOW(__NR_mremap),
#endif
        ALLOW(__NR_exit), ALLOW(__NR_exit_group), ALLOW(__NR_futex),
        ALLOW(__NR_clock_gettime),
#ifdef __NR_gettimeofday
        ALLOW(__NR_gettimeofday),
#endif
        ALLOW(__NR_nanosleep),
#ifdef __NR_clock_nanosleep
        ALLOW(__NR_clock_nanosleep),
#endif
        ALLOW(__NR_poll),
#ifdef __NR_ppoll
        ALLOW(__NR_ppoll),
#endif
        ALLOW(__NR_rt_sigaction), ALLOW(__NR_rt_sigprocmask), ALLOW(__NR_rt_sigreturn),
#ifdef __NR_restart_syscall
        ALLOW(__NR_restart_syscall),
#endif
        ALLOW(__NR_getpid),
#ifdef __NR_gettid
        ALLOW(__NR_gettid),
#endif
        ALLOW(__NR_getrandom), ALLOW(__NR_sched_yield),
#ifdef __NR_set_tid_address
        ALLOW(__NR_set_tid_address),
#endif
#ifdef __NR_set_robust_list
        ALLOW(__NR_set_robust_list),
#endif
#ifdef __NR_rseq
        ALLOW(__NR_rseq),
#endif
        ALLOW(__NR_fcntl),
#ifdef __NR_dup
        ALLOW(__NR_dup),
#endif
#ifdef __NR_dup3
        ALLOW(__NR_dup3),
#endif
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | EPERM),
    };
    struct sock_fprog program = {.len = (unsigned short)(sizeof(filter) / sizeof(filter[0])), .filter = filter};
    if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) || prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &program))
        die("seccomp unavailable");
    errno = 0;
    if (syscall(__NR_openat, -100, "/should-not-open", 0, 0) != -1 || errno != EPERM) die("file policy ineffective");
    errno = 0;
    if (syscall(__NR_socket, 2, 1, 0) != -1 || errno != EPERM) die("socket policy ineffective");
    errno = 0;
    if (syscall(__NR_execve, "/not-an-executable", NULL, NULL) != -1 || errno != EPERM) die("exec policy ineffective");
}

static int eval_text(JSContext *ctx, const char *code, size_t n, const char *name, JSValue *out) {
    arm_cpu();
    JSValue value = JS_Eval(ctx, code, n, name, JS_EVAL_TYPE_GLOBAL);
    if (JS_IsException(value)) {
        JS_FreeValue(ctx, value);
        return classify(ctx);
    }
    int jobs = run_jobs(ctx);
    if (jobs < 0) {
        JS_FreeValue(ctx, value);
        *out = JS_UNDEFINED;
        return jobs;
    }
    *out = value;
    return 0;
}

static int call_native(JSContext *ctx, const char *key, const char *action, const char *data, JSValue *out) {
    arm_cpu();
    JSValue global = JS_GetGlobalObject(ctx);
    JSValue fn = JS_GetPropertyStr(ctx, global, "__lx_native__");
    JSValue args[3];
    args[0] = JS_NewString(ctx, key ? key : "");
    args[1] = JS_NewString(ctx, action ? action : "");
    int argc = 2;
    if (data) {
        args[2] = JS_NewString(ctx, data);
        argc = 3;
    }
    JSValue value = JS_Call(ctx, fn, global, argc, args);
    for (int i = 0; i < argc; i++) JS_FreeValue(ctx, args[i]);
    JS_FreeValue(ctx, fn);
    JS_FreeValue(ctx, global);
    if (JS_IsException(value)) {
        JS_FreeValue(ctx, value);
        return classify(ctx);
    }
    int jobs = run_jobs(ctx);
    if (jobs < 0) {
        JS_FreeValue(ctx, value);
        *out = JS_UNDEFINED;
        return jobs;
    }
    *out = value;
    return 0;
}

int main(int argc, char **argv) {
    if (argc != 4) die("usage: runner preload.js session_ms cpu_ms");
    double session_ms = atof(argv[2]);
    cpu_budget_ms = atof(argv[3]);
    if (session_ms < 100 || cpu_budget_ms < 50) die("budget");
    FILE *f = fopen(argv[1], "rb");
    if (!f) die("preload unavailable");
    preload = malloc(SCRIPT_MAX + 1);
    if (!preload) die("preload allocation");
    preload_len = fread(preload, 1, SCRIPT_MAX + 1, f);
    fclose(f);
    if (preload_len > SCRIPT_MAX) die("preload too large");
    preload[preload_len] = 0;

    JSRuntime *rt = JS_NewRuntime();
    if (!rt) die("runtime allocation");
    JS_SetMemoryLimit(rt, 32 * 1024 * 1024);
    /* Official preload walks and freezes a large object graph. 512KiB was the first draft and
     * rejects that walk; 2MiB is still a fixed cap, not an unlimited stack. */
    JS_SetMaxStackSize(rt, 2 * 1024 * 1024);
    JS_SetInterruptHandler(rt, interrupt_cb, NULL);
    JSContext *ctx = JS_NewContext(rt);
    if (!ctx) die("context allocation");
    JSValue global = JS_GetGlobalObject(ctx);
    JSValue console = JS_NewObject(ctx);
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
        JS_SetPropertyStr(ctx, global, names[i], JS_NewCFunctionMagic(ctx, rpc, names[i], 0, JS_CFUNC_generic_magic, i));
    const char *logs[] = {"log", "info", "warn", "error", "group", "groupEnd", "debug"};
    for (int i = 0; i < 7; i++) JS_SetPropertyStr(ctx, console, logs[i], JS_NewCFunction(ctx, noop, logs[i], 0));
    JS_SetPropertyStr(ctx, global, "console", console);
    JS_FreeValue(ctx, global);

    harden();
    session_deadline = now_ms() + session_ms;
    emit("{\"kind\":\"isolation\",\"seccomp\":true,\"memoryLimit\":33554432}");

    char *session_key = NULL;
    for (;;) {
        if (fatal_code) xexit(fatal_code);
        char *line = read_frame();
        if (!line) xexit(fatal_code ? fatal_code : 0);
        arm_cpu();
        JSValue cmd = JS_ParseJSON(ctx, line, strlen(line), "host command");
        free(line);
        if (JS_IsException(cmd)) {
            if (classify(ctx) < -1) xexit(fatal_code ? fatal_code : 70);
            continue;
        }
        char *op = copy_prop(ctx, cmd, "op");
        int32_t id = 0;
        prop_i32(ctx, cmd, "id", &id);
        int rc = 0;
        JSValue produced = JS_UNDEFINED;
        if (!op) {
            rc = -1;
        } else if (strcmp(op, "close") == 0) {
            JS_FreeValue(ctx, cmd);
            free(op);
            xexit(0);
        } else if (strcmp(op, "budget") == 0) {
            int32_t ms = 0;
            if (prop_i32(ctx, cmd, "ms", &ms) || ms < 50) rc = -1;
            else session_deadline = now_ms() + ms;
        } else if (strcmp(op, "preload") == 0) {
            if (preload_used) rc = -1;
            else {
                preload_used = 1;
                rc = eval_text(ctx, preload, preload_len, "preload.js", &produced);
            }
        } else if (strcmp(op, "setup") == 0) {
            arm_cpu();
            JSValue g = JS_GetGlobalObject(ctx);
            JSValue fn = JS_GetPropertyStr(ctx, g, "lx_setup");
            char *key = copy_prop(ctx, cmd, "key");
            char *sid = copy_prop(ctx, cmd, "scriptId");
            char *name = copy_prop(ctx, cmd, "name");
            char *desc = copy_prop(ctx, cmd, "description");
            char *version = copy_prop(ctx, cmd, "version");
            char *author = copy_prop(ctx, cmd, "author");
            char *homepage = copy_prop(ctx, cmd, "homepage");
            char *raw = copy_prop(ctx, cmd, "rawScript");
            if (session_key) free(session_key);
            session_key = key ? strdup(key) : NULL;
            JSValue args[8];
            const char *vals[] = {key, sid, name, desc, version, author, homepage, raw};
            for (int i = 0; i < 8; i++) args[i] = JS_NewString(ctx, vals[i] ? vals[i] : "");
            produced = JS_Call(ctx, fn, g, 8, args);
            for (int i = 0; i < 8; i++) JS_FreeValue(ctx, args[i]);
            JS_FreeValue(ctx, fn);
            JS_FreeValue(ctx, g);
            free(sid); free(name); free(desc); free(version); free(author); free(homepage); free(raw); free(key);
            if (JS_IsException(produced)) {
                JS_FreeValue(ctx, produced);
                produced = JS_UNDEFINED;
                rc = classify(ctx);
            } else {
                int jobs = run_jobs(ctx);
                if (jobs < 0) {
                    JS_FreeValue(ctx, produced);
                    produced = JS_UNDEFINED;
                    rc = jobs;
                }
            }
        } else if (strcmp(op, "eval") == 0) {
            char *code = copy_prop(ctx, cmd, "code");
            if (!code || strlen(code) > SCRIPT_MAX) rc = -1;
            else rc = eval_text(ctx, code, strlen(code), "user.js", &produced);
            free(code);
        } else if (strcmp(op, "call") == 0) {
            char *action = copy_prop(ctx, cmd, "action");
            char *data = copy_prop(ctx, cmd, "data");
            rc = call_native(ctx, session_key, action, data, &produced);
            free(action);
            free(data);
        } else rc = -1;
        free(op);
        JS_FreeValue(ctx, cmd);
        if (rc <= -2 || fatal_code) xexit(fatal_code ? fatal_code : 70);
        send_result(ctx, id, rc == 0, produced);
        JS_FreeValue(ctx, produced);
    }
}
