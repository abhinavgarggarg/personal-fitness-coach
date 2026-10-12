#!/usr/bin/env python3
"""NFR-10 and release checks on the shrunk (R8) release APK, from `aapt2 dump badging` (Phase 2 section 23, Part 8).

    python3 -I tools/check_release_apk.py <release.apk> <badging.txt>

Fails when the APK is 15 MB or larger, is debuggable, is not the real application id, or targets a different minimum Android version
than the build file. Prints one GitHub notice with the size and version either way.
"""
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LIMIT_BYTES = 15 * 1024 * 1024
APP_ID = "com.personalfitnesscoach"


def check(apk_bytes, badging, gradle):
    problems = []
    pkg = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not pkg:
        return ["no package line in the badging output"], "?"
    name, code, version = pkg.groups()
    if name != APP_ID:
        problems.append(f"application id is {name}, not {APP_ID}")
    if "application-debuggable" in badging:
        problems.append("the release APK is debuggable")
    sdk = re.search(r"(?:minSdkVersion|sdkVersion):'(\d+)'", badging)
    want = re.search(r"minSdk\s*=\s*(\d+)", gradle)
    if sdk and want and sdk.group(1) != want.group(1):
        problems.append(f"minimum Android API {sdk.group(1)}, build file says {want.group(1)}")
    if apk_bytes >= LIMIT_BYTES:
        problems.append(f"{apk_bytes / 1048576:.1f} MB is over the 15 MB limit (NFR-10)")
    summary = f"{name} {version} ({code}): {apk_bytes / 1048576:.2f} MB of 15 MB"
    return problems, summary


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    apk, badging = Path(sys.argv[1]), Path(sys.argv[2]).read_text(encoding="utf-8", errors="replace")
    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    problems, summary = check(os.path.getsize(apk), badging, gradle)
    for p in problems:
        print(f"::error title=Release APK::{p}")
    print(f"::notice title=Release APK (R8 shrunk)::{summary}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
