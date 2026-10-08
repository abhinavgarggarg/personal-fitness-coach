#!/usr/bin/env python3
"""Turn JUnit XML results into one GitHub annotation per test suite folder, so results can be read
through the GitHub API (job logs and artifacts are not always reachable)."""
import glob, sys, xml.etree.ElementTree as ET

for label, pattern in (a.split("=", 1) for a in sys.argv[1:]):
    tests = failures = skipped = 0
    for f in glob.glob(pattern, recursive=True):
        r = ET.parse(f).getroot()
        for s in ([r] if r.tag == "testsuite" else r.iter("testsuite")):
            tests += int(s.get("tests", 0)); failures += int(s.get("failures", 0)) + int(s.get("errors", 0)); skipped += int(s.get("skipped", 0))
    level = "error" if failures or tests == 0 else "notice"
    title = label.replace(",", " ·").replace(":", " ")  # commas and colons end a workflow-command property
    print(f"::{level} title={title}::{tests} tests, {failures} failed, {skipped} skipped")
