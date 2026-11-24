#!/bin/sh
# Cross-compile the gate JNI probe for arm64-v8a at Android API 21.
# This does not run the library and does not build an APK.
set -eu
cd "$(dirname "$0")/.."
NDK="${ANDROID_NDK_HOME:-/tmp/android-ndk/android-ndk-r26b}"
CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang"
READELF="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
NM="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm"
STRINGS="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strings"
Q="${LX_NATIVE_TOOLS:-$PWD/.local/tools}/quickjs-2026-06-04"
mkdir -p artifacts reports
"$CC" -shared -fPIC -O2 -DCONFIG_VERSION='"2026-06-04"' -I"$Q" \
  runtime/jni_gate.c "$Q/quickjs.c" "$Q/dtoa.c" "$Q/libregexp.c" "$Q/libunicode.c" "$Q/cutils.c" \
  -lm -o artifacts/liblxgate-arm64.so
{
  echo "NDK=$NDK"
  echo "CC=$CC"
  file artifacts/liblxgate-arm64.so
  sha256sum artifacts/liblxgate-arm64.so
  "$READELF" -h artifacts/liblxgate-arm64.so
  echo "---- note api word ----"
  "$READELF" -n artifacts/liblxgate-arm64.so
  echo "---- needed ----"
  "$READELF" -d artifacts/liblxgate-arm64.so
  echo "---- exports ----"
  "$NM" -D artifacts/liblxgate-arm64.so | grep -E 'GateNative|lx_gate' || true
  echo "---- build id ----"
  "$STRINGS" artifacts/liblxgate-arm64.so | grep 'lx-gate-api:' || true
  echo "NOT EXECUTED: no emulator, no device, no APK."
} | tee reports/android-arm64.txt
