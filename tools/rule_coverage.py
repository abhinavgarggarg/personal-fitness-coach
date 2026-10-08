#!/usr/bin/env python3
"""Rule-coverage report: which registry rules have their test cases implemented.

Every rule in rules/rule_registry_v1.0.json lists test IDs (e.g. TC-PROG-001a). A test is
"implemented" when a Kotlin test name under */src/test/ starts with that ID. The script
also fails on test IDs that do not exist in the registry (typos, stale IDs).

  python3 tools/rule_coverage.py                 # report; fail only on unknown IDs
  python3 tools/rule_coverage.py --require-all   # also fail unless every rule is covered (Phase 3 gate)
  python3 tools/rule_coverage.py --markdown out.md
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
REG = json.loads((ROOT / "rules" / "rule_registry_v1.0.json").read_text())
ID = re.compile(r"`(TC-[A-Z]+-\d{3}[a-z])\b")


def found_ids():
    ids = {}
    for f in ROOT.glob("*/src/test/**/*.kt"):
        for m in ID.finditer(f.read_text(encoding="utf-8")):
            ids.setdefault(m.group(1), []).append(str(f.relative_to(ROOT)))
    return ids


def main(argv):
    found = found_ids()
    expected = {t: r["rule_id"] for r in REG["rules"] for t in r["tests"]}
    # Extra tests beyond the registry's two per rule are allowed when the rule ID exists (e.g. TC-PROG-001c).
    rule_ids = {r["rule_id"] for r in REG["rules"]}
    unknown = sorted(t for t in found if t not in expected and t[3:-1] not in rule_ids)
    rows, covered = [], 0
    for r in REG["rules"]:
        have = [t for t in r["tests"] if t in found]
        full = len(have) == len(r["tests"])
        covered += full
        rows.append((r["rule_id"], r["category"], len(have), len(r["tests"]), "yes" if full else ("partial" if have else "no")))
    total = len(REG["rules"])
    print(f"Rule coverage: {covered}/{total} rules have all their registry test cases implemented "
          f"({sum(1 for t in expected if t in found)}/{len(expected)} test IDs; {len(found)} distinct IDs in test sources)")
    by_cat = {}
    for rid, cat, h, n, st in rows:
        c = by_cat.setdefault(cat, [0, 0]); c[0] += st == "yes"; c[1] += 1
    for cat, (a, b) in sorted(by_cat.items()):
        print(f"  {cat:<22} {a:>3}/{b}")
    if "--markdown" in argv:
        out = pathlib.Path(argv[argv.index("--markdown") + 1])
        lines = ["| Rule | Category | Tests implemented | Covered |", "| --- | --- | --- | --- |"]
        lines += [f"| {rid} | {cat} | {h}/{n} | {st} |" for rid, cat, h, n, st in rows]
        out.write_text("\n".join(lines) + "\n")
    if unknown:
        print("ERROR: test IDs not in the registry: " + ", ".join(unknown))
        return 1
    if "--require-all" in argv and covered < total:
        missing = [rid for rid, _, _, _, st in rows if st != "yes"]
        print("ERROR: rules without complete tests: " + ", ".join(missing))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
