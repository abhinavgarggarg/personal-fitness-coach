#!/usr/bin/env python3
"""Android backup rules keep health data out of any backup that is not end-to-end encrypted (Phase 2 section 16, review R4-18); a
device-to-device transfer to a new phone takes it on every Android version (Phase 4 Part 3 review).

Checks app/src/main/res/xml/data_extraction_rules.xml (Android 12+) and backup_rules.xml (Android 11 and lower), and that the
manifest points at both.
"""
import pathlib, sys, xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res/xml"
A = "{http://schemas.android.com/apk/res/android}"


def main():
    problems = []
    der = ET.parse(RES / "data_extraction_rules.xml").getroot()
    cloud = der.find("cloud-backup")
    if cloud is None or cloud.get("disableIfNoEncryptionCapabilities") != "true":
        problems.append("data_extraction_rules.xml: <cloud-backup> must set disableIfNoEncryptionCapabilities=\"true\"")
    fbc = ET.parse(RES / "backup_rules.xml").getroot()
    # Android 11 and lower: every include needs end-to-end encryption, or is a device-to-device transfer (as on Android 12+).
    allowed = {"clientSideEncryption", "deviceToDeviceTransfer"}
    for inc in fbc.findall("include"):
        if inc.get(A + "requireFlags", inc.get("requireFlags")) not in allowed:
            problems.append(f"backup_rules.xml: include domain={inc.get('domain')} needs requireFlags=\"clientSideEncryption\" or \"deviceToDeviceTransfer\"")
    for domain in ("database", "sharedpref"):
        flags = {inc.get("requireFlags") for inc in fbc.findall("include") if inc.get("domain") == domain}
        if flags != allowed:
            problems.append(f"backup_rules.xml: {domain} must be included for an encrypted backup and a device-to-device transfer, and nothing else")
    d2d = der.find("device-transfer")
    if d2d is None or {i.get("domain") for i in d2d.findall("include")} != {"database", "sharedpref"}:
        problems.append("data_extraction_rules.xml: <device-transfer> must include the database and settings")
    app = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot().find("application")
    if app.get(A + "dataExtractionRules") != "@xml/data_extraction_rules" or app.get(A + "fullBackupContent") != "@xml/backup_rules":
        problems.append("AndroidManifest.xml: <application> must reference both backup rule files")
    for p in problems:
        print("FAIL: " + p)
    if not problems:
        print("PASS: health data only in an end-to-end encrypted backup or a device-to-device transfer")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
