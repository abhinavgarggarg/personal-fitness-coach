#!/usr/bin/env python3
"""Android backup rules keep health data out of any backup that is not end-to-end encrypted (Phase 2 section 16, review R4-18).

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
    for inc in fbc.findall("include"):
        if inc.get("requireFlags") != "clientSideEncryption":
            problems.append(f"backup_rules.xml: include domain={inc.get('domain')} needs requireFlags=\"clientSideEncryption\"")
    app = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot().find("application")
    if app.get(A + "dataExtractionRules") != "@xml/data_extraction_rules" or app.get(A + "fullBackupContent") != "@xml/backup_rules":
        problems.append("AndroidManifest.xml: <application> must reference both backup rule files")
    for p in problems:
        print("FAIL: " + p)
    if not problems:
        print("PASS: backups of health data only when end-to-end encrypted")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
