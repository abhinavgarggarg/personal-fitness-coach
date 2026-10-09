#!/usr/bin/env python3
"""Fail the build if the APK asks for any permission outside the Phase 2 allow-list.

Reads the output of `aapt2 dump permissions <apk>` on stdin (CI), or checks the source manifest
when given --manifest <path> (local, no Android SDK needed).
"""
import re, sys, xml.etree.ElementTree as ET

ALLOWED = {
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.VIBRATE",
    "android.permission.USE_EXACT_ALARM",
    "android.permission.SCHEDULE_EXACT_ALARM",  # maxSdkVersion 32 only (D-036)
    "android.permission.ACTIVITY_RECOGNITION",  # phone step counter, asked only when step tracking is switched on (D-062)
}
FORBIDDEN = {"android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"}
# AndroidX core declares and uses this app-private signature permission for its own receivers.
PRIVATE_SUFFIX = ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
A = "{http://schemas.android.com/apk/res/android}"
T = "{http://schemas.android.com/tools}"


def check(perms):
    bad = sorted(p for p in perms if p not in ALLOWED and not p.endswith(PRIVATE_SUFFIX))
    for p in sorted(perms):
        print(("  ok    " if p not in bad else "  NOT ALLOWED ") + p)
    if bad or (set(perms) & FORBIDDEN):
        print("FAIL: permissions outside the allow-list: " + ", ".join(bad))
        return 1
    print(f"PASS: {len(perms)} permission(s), all on the allow-list; no INTERNET")
    return 0


def main(argv):
    if argv[:1] == ["--manifest"]:
        root = ET.parse(argv[1]).getroot()
        perms = [e.get(A + "name") for e in root.iter("uses-permission") if e.get(T + "node") != "remove"]
        return check(perms)
    text = sys.stdin.read()
    perms = re.findall(r"uses-permission(?:-sdk-23)?: name='([^']+)'", text)
    if "package:" not in text:
        print("FAIL: no aapt2 output on stdin")
        return 1
    return check(perms)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
