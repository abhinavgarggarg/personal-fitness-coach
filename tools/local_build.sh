#!/usr/bin/env bash
# Local build + test for the pure-Kotlin modules (decision D-032).
# Uses the Kotlin compiler and JUnit 4 that ship inside Gradle, so it works with no
# network access. CI uses Gradle with the pinned version catalog instead.
#
#   tools/local_build.sh            # compile engine and :data core (+ tests) and run all tests
#   tools/local_build.sh --no-test  # compile only
#   tools/local_build.sh --data     # skip the engine tests, run only the :data core tests
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
G="${GRADLE_LIB:-/opt/gradle/lib}"
OUT="${LOCAL_BUILD_DIR:-$ROOT/build-local}"
STDLIB="$G/kotlin-stdlib-2.0.21.jar"
COMPILER_CP="$G/kotlin-compiler-embeddable-2.0.21.jar:$STDLIB:$G/kotlin-script-runtime-2.0.21.jar:$G/kotlin-reflect-2.0.21.jar:$G/kotlin-daemon-embeddable-2.0.21.jar:$G/trove4j-1.0.20200330.jar:$G/annotations-24.0.1.jar:$G/kotlinx-coroutines-core-jvm-1.6.4.jar"
JUNIT="$G/junit-4.13.2.jar:$G/hamcrest-core-1.3.jar"
# :data core (plain Kotlin) needs kotlinx-serialization-json and coroutines; Gradle ships older builds of both, enough for the APIs used.
KX="$G/kotlinx-serialization-core-jvm-1.6.2.jar:$G/kotlinx-serialization-json-jvm-1.6.2.jar:$G/kotlinx-coroutines-core-jvm-1.6.4.jar"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Xss8m"

kotlinc() { java -Xmx2g -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 "$@"; }

python3 -I "$ROOT/tools/gen_registry_kotlin.py" --check
python3 -I "$ROOT/tools/gen_library_kotlin.py" --check
python3 -I "$ROOT/tools/gen_conditions_kotlin.py" --check
python3 -I "$ROOT/tools/check_registry.py" > /dev/null || { python3 -I "$ROOT/tools/check_registry.py"; exit 1; }
python3 -I "$ROOT/tools/gen_reason_texts.py" --check > /dev/null || { python3 -I "$ROOT/tools/gen_reason_texts.py" --check; exit 1; }
python3 -I "$ROOT/tools/check_strings.py" > /dev/null || { python3 -I "$ROOT/tools/check_strings.py"; exit 1; }

rm -rf "$OUT" && mkdir -p "$OUT/engine/main" "$OUT/engine/test" "$OUT/data/main" "$OUT/data/test"
echo "== compiling :engine"
kotlinc -cp "$STDLIB" -d "$OUT/engine/main" $(find "$ROOT/engine/src/main/kotlin" -name '*.kt') 2>&1 | grep -v "^Picked up" || true
test -d "$OUT/engine/main/com" || { echo "engine compile FAILED"; exit 1; }

echo "== compiling :data core"
kotlinc -cp "$STDLIB:$KX:$OUT/engine/main" -d "$OUT/data/main" $(find "$ROOT/data/src/main/kotlin/com/personalfitnesscoach/data/core" -name '*.kt') 2>&1 | grep -v "^Picked up" || true
test -d "$OUT/data/main/com" || { echo "data core compile FAILED"; exit 1; }

echo "== compiling :app flow (plain Kotlin screen logic)"
mkdir -p "$OUT/app/main" "$OUT/app/test"
kotlinc -cp "$STDLIB:$KX:$OUT/engine/main:$OUT/data/main" -d "$OUT/app/main" $(find "$ROOT/app/src/main/kotlin/com/personalfitnesscoach/app/flow" -name '*.kt') 2>&1 | grep -v "^Picked up" || true
test -d "$OUT/app/main/com" || { echo "app flow compile FAILED"; exit 1; }

if [[ "${1:-}" == "--no-test" ]]; then echo "compiled (tests skipped)"; exit 0; fi

run_tests() { # $1 = classes dir, $2 = classpath
  local classes
  classes=$(cd "$1" && find . -name '*Test.class' ! -name '*$*' | sed 's|^\./||; s|\.class$||; s|/|.|g' | sort | tr '\n' ' ')
  test -n "$classes" || { echo "test compile FAILED ($1)"; exit 1; }
  java -cp "$2" org.junit.runner.JUnitCore $classes 2>&1 | grep -v "^Picked up"
}

if [[ "${1:-}" != "--data" ]]; then
  echo "== compiling :engine tests"
  kotlinc -cp "$STDLIB:$JUNIT:$OUT/engine/main" -d "$OUT/engine/test" $(find "$ROOT/engine/src/test/kotlin" -name '*.kt') 2>&1 | grep -v "^Picked up" || true
  echo "== running :engine tests"
  run_tests "$OUT/engine/test" "$STDLIB:$JUNIT:$OUT/engine/main:$OUT/engine/test"
fi

echo "== compiling :data core tests"
kotlinc -cp "$STDLIB:$KX:$JUNIT:$OUT/engine/main:$OUT/data/main" -d "$OUT/data/test" $(find "$ROOT/data/src/test/kotlin/com/personalfitnesscoach/data/core" -name '*.kt') 2>&1 | grep -v "^Picked up" || true
echo "== running :data core tests"
run_tests "$OUT/data/test" "$STDLIB:$KX:$JUNIT:$OUT/engine/main:$OUT/data/main:$OUT/data/test"

echo "== compiling :app flow tests"
kotlinc -cp "$STDLIB:$KX:$JUNIT:$OUT/engine/main:$OUT/data/main:$OUT/app/main" -d "$OUT/app/test" $(find "$ROOT/app/src/test/kotlin/com/personalfitnesscoach/app/flow" -name '*.kt') 2>&1 | grep -v "^Picked up" || true
echo "== running :app flow tests"
run_tests "$OUT/app/test" "$STDLIB:$KX:$JUNIT:$OUT/engine/main:$OUT/data/main:$OUT/app/main:$OUT/app/test"
