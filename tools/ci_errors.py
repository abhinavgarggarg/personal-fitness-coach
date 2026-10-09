#!/usr/bin/env python3
"""Turn compiler and test failures in Gradle logs into GitHub error annotations (job logs cannot always be
downloaded, but annotations can be read through the API). Usage: ci_errors.py gradle-*.log"""
import re, sys

PATTERNS = [
    # Kotlin compiler: "e: file:///home/runner/work/x/x/data/src/main/kotlin/A.kt:12:5 Unresolved reference"
    re.compile(r"^e: (?:file://)?(?P<file>/\S+?\.kts?):(?P<line>\d+):(?P<col>\d+) (?P<msg>.*)$"),
    # KSP / Room: "e: [ksp] /path/File.kt:12: message"
    re.compile(r"^e: \[ksp\] (?P<file>/\S+?\.kt):(?P<line>\d+): (?P<msg>.*)$"),
    # Java / generated sources: "/path/File.java:12: error: message"
    re.compile(r"^(?P<file>/\S+?\.java):(?P<line>\d+): error: (?P<msg>.*)$"),
]
OTHER = re.compile(r"^(e: .*|> Task .* FAILED|.*FAILED$|What went wrong:|\s+> .*)$")
seen = set()
out = []
for path in sys.argv[1:]:
    try:
        lines = open(path, encoding="utf-8", errors="replace").read().splitlines()
    except OSError:
        continue
    for i, line in enumerate(lines):
        for p in PATTERNS:
            m = p.match(line)
            if m:
                f = m.group("file")
                rel = re.sub(r"^.*?/(?=(engine|data|app|coach|simulator)/src/)", "", f)
                key = (rel, m.group("line"), m.group("msg")[:80])
                if key not in seen:
                    seen.add(key)
                    msg = m.group("msg").replace("%", "%25").replace("\n", " ")[:300]
                    out.append(f"::error file={rel},line={m.group('line')},title=Compile error::{msg}")
                break
        else:
            if "What went wrong:" in line:
                ctx = " ".join(l.strip() for l in lines[i + 1:i + 4] if l.strip())[:300]
                key = ("what", ctx)
                if key not in seen:
                    seen.add(key)
                    out.append(f"::error title=Gradle failure::{ctx}")
for a in out[:9]:
    print(a)
if len(out) > 9:
    print(f"::error title=More errors::{len(out) - 9} more not shown")
