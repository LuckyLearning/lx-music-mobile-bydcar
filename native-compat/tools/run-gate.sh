#!/bin/sh
# Reproducible compatibility-gate run. Host wall time only; not an app performance claim.
set -eu
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
REPO="$(cd .. && pwd)"
TOOLS="${LX_NATIVE_TOOLS:-$ROOT/.local/tools}"
export LX_NATIVE_TOOLS="$TOOLS"
python3 tools/bootstrap.py
CP="$TOOLS/kotlin/kotlin-stdlib-2.1.20.jar:$TOOLS/kotlin/annotations-13.0.jar"
mkdir -p artifacts reports
echo "CHECK kotlin"
java -cp "$TOOLS/kotlin/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 1.8 -classpath "$CP" \
  $(find src -name '*.kt' | sort) -d artifacts/compat-core.jar
Q="$TOOLS/quickjs-2026-06-04"
echo "CHECK runner"
cc -O2 -D_GNU_SOURCE -DCONFIG_VERSION=\"2026-06-04\" -I"$Q" \
  runtime/runner.c "$Q/quickjs.c" "$Q/dtoa.c" "$Q/libregexp.c" \
  "$Q/libunicode.c" "$Q/cutils.c" -lm -lpthread -o artifacts/gate-runner
echo "CHECK tests"
java -cp "artifacts/compat-core.jar:$CP" lx.compat.GateMainKt "$REPO" artifacts/gate-runner fixtures/baseline/user-api-preload.js
echo "GATE_COMMANDS_DONE"
