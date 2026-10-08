#!/usr/bin/env bash
# Local build + test for the pure-Kotlin modules (decision D-032).
# Uses the Kotlin compiler and JUnit 4 that ship inside Gradle, so it works with no
# network access. CI uses Gradle with the pinned version catalog instead.
#
#   tools/local_build.sh            # compile engine (+ tests) and run all tests
#   tools/local_build.sh --no-test  # compile only
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
G="${GRADLE_LIB:-/opt/gradle/lib}"
OUT="${LOCAL_BUILD_DIR:-$ROOT/build-local}"
STDLIB="$G/kotlin-stdlib-2.0.21.jar"
COMPILER_CP="$G/kotlin-compiler-embeddable-2.0.21.jar:$STDLIB:$G/kotlin-script-runtime-2.0.21.jar:$G/kotlin-reflect-2.0.21.jar:$G/kotlin-daemon-embeddable-2.0.21.jar:$G/trove4j-1.0.20200330.jar:$G/annotations-24.0.1.jar:$G/kotlinx-coroutines-core-jvm-1.6.4.jar"
JUNIT="$G/junit-4.13.2.jar:$G/hamcrest-core-1.3.jar"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Xss8m"

kotlinc() { java -Xmx2g -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 "$@"; }

python3 -I "$ROOT/tools/gen_registry_kotlin.py" --check

rm -rf "$OUT" && mkdir -p "$OUT/engine/main" "$OUT/engine/test"
echo "== compiling :engine"
kotlinc -cp "$STDLIB" -d "$OUT/engine/main" $(find "$ROOT/engine/src/main/kotlin" -name '*.kt') 2>&1 | grep -v "^Picked up" || true
test -d "$OUT/engine/main/com" || { echo "engine compile FAILED"; exit 1; }

if [[ "${1:-}" == "--no-test" ]]; then echo "compiled (tests skipped)"; exit 0; fi

echo "== compiling :engine tests"
kotlinc -cp "$STDLIB:$JUNIT:$OUT/engine/main" -d "$OUT/engine/test" $(find "$ROOT/engine/src/test/kotlin" -name '*.kt') 2>&1 | grep -v "^Picked up" || true
CLASSES=$(cd "$OUT/engine/test" && find . -name '*Test.class' | sed 's|^\./||; s|\.class$||; s|/|.|g' | sort | tr '\n' ' ')
test -n "$CLASSES" || { echo "test compile FAILED"; exit 1; }
echo "== running tests"
java -cp "$STDLIB:$JUNIT:$OUT/engine/main:$OUT/engine/test" org.junit.runner.JUnitCore $CLASSES 2>&1 | grep -v "^Picked up"
