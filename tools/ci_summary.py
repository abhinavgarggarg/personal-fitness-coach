#!/usr/bin/env python3
"""Turn JUnit XML results into GitHub annotations, so results can be read through the GitHub API (job logs and artifacts are not
always reachable): one summary per label, plus one error per failing test (name and the start of its message, at most 8 per label)."""
import glob, sys, xml.etree.ElementTree as ET


def esc(s):
    return s.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


for label, pattern in (a.split("=", 1) for a in sys.argv[1:]):
    tests = failures = skipped = 0
    failed = []
    for f in glob.glob(pattern, recursive=True):
        r = ET.parse(f).getroot()
        for s in ([r] if r.tag == "testsuite" else r.iter("testsuite")):
            tests += int(s.get("tests", 0)); failures += int(s.get("failures", 0)) + int(s.get("errors", 0)); skipped += int(s.get("skipped", 0))
            for case in s.iter("testcase"):
                for bad in list(case.findall("failure")) + list(case.findall("error")):
                    text = (bad.get("message") or "") + "\n" + (bad.text or "")
                    lines = [l for l in text.splitlines() if l.strip()]
                    # the message and the first frames from the project's own code
                    own = [l.strip() for l in lines[1:] if "com.personalfitnesscoach" in l][:3]
                    failed.append((case.get("classname", "").split(".")[-1] + " > " + case.get("name", ""), "\n".join(lines[:2] + own)[:900]))
    level = "error" if failures or tests == 0 else "notice"
    title = label.replace(",", " ·").replace(":", " ")  # commas and colons end a workflow-command property
    print(f"::{level} title={title}::{tests} tests, {failures} failed, {skipped} skipped")
    for name, msg in failed[:8]:
        print(f"::error title={esc(name.replace(',', ' ').replace(':', ' '))[:120]}::{esc(msg)}")
