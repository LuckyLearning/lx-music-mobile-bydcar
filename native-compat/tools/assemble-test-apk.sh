#!/bin/sh
# Assemble the independent debug test APK. Does not install it and does not sign a release.
set -eu
cd "$(dirname "$0")/.."
TOOLS="${LX_NATIVE_TOOLS:-$PWD/.local/tools}"
export QUICKJS_DIR="$TOOLS/quickjs-2026-06-04"
if [ ! -f "$QUICKJS_DIR/quickjs.c" ]; then
  python3 tools/bootstrap.py
fi
SDK="${ANDROID_SDK_ROOT:-/tmp/android-sdk}"
NDK="${ANDROID_NDK_HOME:-/tmp/android-ndk/android-ndk-r26b}"
printf 'sdk.dir=%s\nndk.dir=%s\n' "$SDK" "$NDK" > android-test/local.properties
cd android-test
if [ ! -x ./gradlew ]; then
  GRADLE_BIN="${GRADLE_BIN:-/tmp/gradle-8.7/bin/gradle}"
  if [ ! -x "$GRADLE_BIN" ]; then
    echo "gradle wrapper missing and $GRADLE_BIN is not installed" >&2
    exit 2
  fi
  "$GRADLE_BIN" wrapper --gradle-version 8.7
fi
./gradlew :app:assembleDebug :app:processDebugMainManifest --no-daemon
echo "APK $(pwd)/app/build/outputs/apk/debug/app-debug.apk"
