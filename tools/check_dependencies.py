#!/usr/bin/env python3
"""NFR-07: the shipped app contains no analytics, ads, trackers or other third-party SDKs (Phase 2 section 16, Part 8).

    ./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath > deps.txt
    python3 -I tools/check_dependencies.py deps.txt

Every library in the release runtime classpath must come from an allowed group: AndroidX (Jetpack, Compose, Room), the Kotlin
standard library and kotlinx, and the small annotation libraries they bring with them. Anything else fails, so a new library is
looked at before it ships.
"""
import re
import sys

ALLOWED_GROUPS = (
    "androidx.",                 # Jetpack, Compose, Room, SQLite
    "org.jetbrains.kotlin",      # standard library
    "org.jetbrains.kotlinx",     # coroutines, serialization (JSON tree only)
    "org.jetbrains",             # org.jetbrains:annotations
    "com.google.guava",          # listenablefuture placeholder that AndroidX depends on
    "org.jspecify",              # nullness annotations used by AndroidX
)
PROJECTS = ("project :engine", "project :data")


def coordinates(text):
    found = set()
    for line in text.splitlines():
        m = re.search(r"[+\\]--- ([A-Za-z0-9_.\-]+):([A-Za-z0-9_.\-]+)(?::([^\s]+))?", line)
        if m:
            found.add((m.group(1), m.group(2)))
    return found


def allowed(group):
    return any(group == g or group.startswith(g) for g in ALLOWED_GROUPS)


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    text = open(sys.argv[1], encoding="utf-8", errors="replace").read()
    deps = coordinates(text)
    if not deps:
        print("::error title=Dependencies::no libraries found in the dependency report")
        return 1
    bad = sorted(f"{g}:{a}" for g, a in deps if not allowed(g))
    groups = sorted({g for g, _ in deps})
    for b in bad:
        print(f"::error title=Dependency not on the allow-list (NFR-07)::{b}")
    print(f"::notice title=Dependencies (NFR-07)::{len(deps)} libraries from {len(groups)} groups, "
          f"{'all on the allow-list' if not bad else str(len(bad)) + ' not allowed'}: {', '.join(groups)}"[:900])
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
