#!/usr/bin/env python3
"""Turn JUnit XML results into GitHub annotations, so results can be read through the GitHub API (job logs and artifacts are not
always reachable): one summary per label, plus one error per failing test (name and the start of its message, at most 8 per label)."""
import glob, sys, xml.etree.ElementTree as ET


def esc(s):
    return s.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


def excerpt(label, path):
    """A text file (such as the phone's crash log) as one annotation, when it has anything in it: its last lines, which hold the cause."""
    try:
        lines = [l.rstrip() for l in open(path, errors="replace") if l.strip()]
    except OSError:
        return
    if lines:
        print(f"::error title={label.replace(',', ' ·').replace(':', ' ')}::{esc(chr(10).join(lines[-40:]))[:4000]}")


for arg in sys.argv[1:]:
    if "@=" in arg:
        excerpt(*arg.split("@=", 1))
        continue
    label, pattern = arg.split("=", 1)
    tests = failures = skipped = 0
    failed = []
    for f in glob.glob(pattern, recursive=True):
        r = ET.parse(f).getroot()
        for s in ([r] if r.tag == "testsuite" else r.iter("testsuite")):
            tests += int(s.get("tests", 0)); failures += int(s.get("failures", 0)) + int(s.get("errors", 0)); skipped += int(s.get("skipped", 0))
            # A failure outside any test (the app's process crashed, the run stopped): say so with whatever the suite recorded.
            listed = sum(1 for c in s.iter("testcase") for _ in list(c.findall("failure")) + list(c.findall("error")))
            if int(s.get("failures", 0)) + int(s.get("errors", 0)) > listed:
                err = " ".join(((s.findtext("system-err") or "") + " " + (s.findtext("system-out") or "")).split())[:900]
                if not err:
                    err = "suite XML: " + " ".join(ET.tostring(s, encoding="unicode").split())[:1500]
                failed.append((s.get("name", "suite") + " (outside a test)", err or "no details recorded"))
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
