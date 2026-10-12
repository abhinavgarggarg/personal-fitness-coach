#!/usr/bin/env python3
"""NFR-13: line coverage of the fitness engine by its tests, from the JaCoCo XML report (Part 8).

    python3 -I tools/coverage_summary.py engine/build/reports/jacoco/test/jacocoTestReport.xml [--min 90]

Prints the engine's line and branch coverage and the five least covered packages as a GitHub notice. With --min, fails below it.
"""
import sys
import xml.etree.ElementTree as ET


def ratio(e, kind):
    for c in e.findall("counter"):
        if c.get("type") == kind:
            missed, covered = int(c.get("missed")), int(c.get("covered"))
            return covered, missed + covered
    return 0, 0


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return 2
    minimum = None
    if "--min" in args:
        minimum = float(args[args.index("--min") + 1])
    root = ET.parse(args[0]).getroot()
    lc, lt = ratio(root, "LINE")
    bc, bt = ratio(root, "BRANCH")
    line = 100.0 * lc / lt if lt else 0.0
    branch = 100.0 * bc / bt if bt else 0.0
    packages = []
    for p in root.findall("package"):
        c, t = ratio(p, "LINE")
        if t:
            packages.append((100.0 * c / t, p.get("name").replace("com/personalfitnesscoach/engine/", "").replace("/", "."), t))
    low = ", ".join(f"{n} {pct:.0f}%" for pct, n, _ in sorted(packages)[:5])
    print(f"::notice title=Engine coverage (NFR-13)::lines {line:.1f}% ({lc}/{lt}), branches {branch:.1f}%; least covered: {low}")
    if minimum is not None and line < minimum:
        print(f"::error title=Engine coverage (NFR-13)::line coverage {line:.1f}% is below {minimum:.0f}%")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
