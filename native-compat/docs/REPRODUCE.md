# 已有检查的复现命令（不是兼容gate通过步骤）

环境：box Linux x86_64、cc14.2、OpenJDK21.0.13.1。原始命令/退出码在`reports/handoff-checks.txt`；日志里的临时smoke文件可能已经删除，按下文重建即可。所有耗时仅宿主命令耗时，不是App启动/性能。

```sh
cd native-compat
python3 tools/bootstrap.py
TOOLS=${LX_NATIVE_TOOLS:-/workspace/lx-native-tools}
mkdir -p artifacts
java -cp "$TOOLS/kotlin/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 1.8 \
  -classpath "$TOOLS/kotlin/kotlin-stdlib-2.1.20.jar:$TOOLS/kotlin/annotations-13.0.jar" \
  src/main/kotlin/lx/compat/*.kt -d artifacts/compat-core.jar
Q="$TOOLS/quickjs-2026-06-04"
cc -O2 -D_GNU_SOURCE '-DCONFIG_VERSION="2026-06-04"' -I"$Q" \
  runtime/runner.c "$Q/quickjs.c" "$Q/dtoa.c" "$Q/libregexp.c" \
  "$Q/libunicode.c" "$Q/cutils.c" -lm -lpthread -o artifacts/gate-runner
```

bootstrap只下载公共固定工具，不需要登录；观察到的SHA256在`BUILD-TOOLS.observed-lock.json`，来源HTTPS，**不是独立签名**。QuickJS归档有代码中硬pin；Kotlin工具目前只记录下载hash，仍需云端review后加入强校验。不得把工具编译依赖清单当Android产品完整依赖闭包。

## 同一sandbox命令的3个无害合成输入

把下列任一个合成输入保存到临时`synthetic.js`，从native-compat目录运行：

```sh
printf '{}\n' | timeout --kill-after=1s 8s bwrap \
  --unshare-all --die-with-parent --new-session --cap-drop ALL --clearenv \
  --ro-bind /usr /usr --ro-bind /lib /lib --ro-bind /lib64 /lib64 \
  --proc /proc --dev /dev \
  --ro-bind "$PWD/artifacts/gate-runner" /runner \
  --ro-bind "$PWD/synthetic.js" /bundle.js /runner /bundle.js
```

1. globals/Promise（预期exit0，有isolation与ready）：
```js
if (["require","process","Java","std","os","fetch","XMLHttpRequest"].some(k=>typeof globalThis[k]!=="undefined")) throw Error("unexpected global");
Promise.resolve(1).then(v=>{if(v!==1)throw Error("job")});
```
2. 计算中断（预期exit70，非超时工具的124）：`while(true) {}`
3. 内存上限（预期exit70）：`const kept=[]; while(true) kept.push(new Uint8Array(1024*1024));`

原实际执行器使用Python subprocess timeout=45s作为外部终止兜底、发送同一个`{}\n`；原日志包含完整bwrap参数和真实输出。上面8s为云端更短兜底，不声称它曾是原日志的参数。

没有运行baseline preload、host crypto vectors或任何第三方音源。请勿直接把第三方script换进上面命令：宿主桥与墙钟watchdog等还未完成，先看SECURITY-GAPS。
