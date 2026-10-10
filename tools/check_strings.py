#!/usr/bin/env python3
"""Screen text checks (NFR-16), run locally and in CI.

1. Every R.string.<name> the app's code uses exists in res/values/*.xml, and every string there is used.
2. Each stringResource(...) / getString(...) call passes exactly as many values as the string has placeholders
   (a mismatch crashes the screen at run time).
3. The screens hold no hard-coded words: a Kotlin string literal under app/.../ui/ with letters and a space is an error
   (IDs, symbols and separators are fine).
4. Every registry ID list the screens label (red flags, illness symptoms, pain descriptors) has a string.

    python3 tools/check_strings.py
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res/values"
SRC = ROOT / "app/src/main/kotlin"
UI = SRC / "com/personalfitnesscoach/app/ui"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
REGISTRY = ROOT / "engine/src/main/kotlin/com/personalfitnesscoach/engine/registry/GeneratedRegistry.kt"

PLACEHOLDER = re.compile(r"%(\d+)\$")


def strings():
    out = {}
    for f in sorted(RES.glob("*.xml")):
        for e in ET.parse(f).getroot().findall("string"):
            text = "".join(e.itertext())
            idx = [int(i) for i in PLACEHOLDER.findall(text)]
            out[e.get("name")] = (max(idx) if idx else 0, f.name, text)
    return out


def calls(src, fn):
    """Yield (name, argument count) for each fn(R.string.name, ...) call, with balanced parentheses."""
    for m in re.finditer(re.escape(fn) + r"\(\s*R\.string\.(\w+)", src):
        i = m.end()
        depth, parts, quote = 1, 0, None
        while i < len(src) and depth > 0:
            ch = src[i]
            if quote:
                if ch == "\\":
                    i += 1
                elif ch == quote:
                    quote = None
            elif ch == '"':
                quote = '"'
            elif ch in "([{":
                depth += 1
            elif ch in ")]}":
                depth -= 1
            elif ch == "," and depth == 1:
                parts += 1
            i += 1
        yield m.group(1), parts


def literals(line):
    """String literals on one line of Kotlin (no templates across lines in the screens)."""
    return re.findall(r'"((?:[^"\\]|\\.)*)"', line)


def main():
    errors = []
    defined = strings()
    used = set()
    files = sorted(SRC.rglob("*.kt"))
    for f in files:
        src = f.read_text(encoding="utf-8")
        used |= set(re.findall(r"R\.string\.(\w+)", src))
        for fn in ("stringResource", "getString"):
            for name, args in calls(src, fn):
                if name not in defined:
                    continue
                want = defined[name][0]
                if args != want:
                    errors.append(f"{f.relative_to(ROOT)}: {fn}(R.string.{name}) passes {args} value(s), the string has {want} placeholder(s)")
    used |= set(re.findall(r"@string/(\w+)", MANIFEST.read_text(encoding="utf-8")))
    for name in sorted(used - set(defined)):
        errors.append(f"missing string: {name}")
    for name in sorted(set(defined) - used):
        errors.append(f"unused string: {name} ({defined[name][1]})")

    for f in sorted(UI.rglob("*.kt")):
        if f.name == "ReasonTexts.kt":
            continue
        for n, line in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
            code = line.split("//")[0] if not line.strip().startswith(("*", "/*", "/**")) else ""
            if re.search(r"\b(error|require|check|requireNotNull|checkNotNull)\(", code):
                continue  # developer messages in exceptions, never shown as screen text
            for lit in literals(code):
                if re.search(r"[A-Za-z]{2}", lit) and " " in lit.strip():
                    errors.append(f"{f.relative_to(ROOT)}:{n}: hard-coded text \"{lit}\" (use a string resource)")

    reg = REGISTRY.read_text(encoding="utf-8")
    labels = (UI / "text/Labels.kt").read_text(encoding="utf-8")
    for obj, field, mapname in (("SAF_002", "symptoms", "RED_FLAGS"), ("SAF_007", "systemic_symptoms", "ILLNESS"),
                                ("SAF_003", "stop_region_descriptors", "PAIN_DESCRIPTORS")):
        m = re.search(r"object " + obj + r" \{.*?val " + field + r": List<String> = listOf\(([^)]*)\)", reg, re.S)
        ids = re.findall(r'"([^"]+)"', m.group(1)) if m else []
        block = re.search(r"val " + mapname + r": Map<String, Int> = mapOf\((.*?)\n    \)", labels, re.S)
        have = set(re.findall(r'"([^"]+)" to R\.string', block.group(1))) if block else set()
        for i in ids:
            if i not in have:
                errors.append(f"Labels.{mapname} has no string for registry ID {obj}.{field} '{i}'")

    if errors:
        print("\n".join(errors))
        print(f"FAIL: {len(errors)} problem(s)")
        sys.exit(1)
    print(f"strings OK: {len(defined)} strings, {len(used)} used, {len(files)} Kotlin files checked")


if __name__ == "__main__":
    main()
