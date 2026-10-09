"""Integrity checks for the current Rule Registry (rules/rule_registry_v1.0.json, 1.x line) and the
health-condition table (rules/health_conditions_v1.0.json). Run in CI: python3 -I tools/check_registry.py
Phase 1 report checks run against the Phase 1 snapshot instead: tools/check_phase1.py."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
reg = json.loads((ROOT / "rules" / "rule_registry_v1.0.json").read_text(encoding="utf-8"))
cond = json.loads((ROOT / "rules" / "health_conditions_v1.0.json").read_text(encoding="utf-8"))
R = {r["rule_id"]: r for r in reg["rules"]}
fails = []


def check(name, ok):
    print(("PASS " if ok else "FAIL ") + name)
    if not ok:
        fails.append(name)


check(f"unique rule IDs ({len(R)})", len(R) == len(reg["rules"]))
check("all evidence keys resolve", all(e in reg["sources"] for r in reg["rules"] for e in r["evidence"]))
check("every rule has >=2 tests", all(len(r["tests"]) >= 2 for r in reg["rules"]))
check("every rule approved", all(r["status"] == "approved" for r in reg["rules"]))
check("versions are semantic", all(len(r["version"].split(".")) == 3 and all(p.isdigit() for p in r["version"].split(".")) for r in reg["rules"]))
check("confidence labels on the scale", all(r["confidence"] in reg["confidence_scale"] for r in reg["rules"]))
check("verification_pending keys are sources", all(k in reg["sources"] for r in reg["rules"] for k in r.get("verification_pending", [])))
check("SUB-002, MOD-002, RDY-001 weights sum to 1",
      all(abs(sum(R[i]["parameters"]["weights"].values()) - 1) < 1e-9 for i in ("SUB-002", "MOD-002", "RDY-001")))
check("SAF-010 lists exactly the table's entries", R["SAF-010"]["parameters"]["entries"] == [e["id"] for e in cond["entries"]])
check("condition sources resolve", all(s in reg["sources"] for e in cond["entries"] for s in e["sources"]))
orders = cond["merge_orders"]
ok = True
for e in cond["entries"]:
    for k in ("max_zone", "hiit", "impact", "clearance"):
        v = e.get(k)
        if v not in orders[k] and v not in ("by_phase", "none_at_start"):
            ok = False
            print("   no merge order:", e["id"], k, v)
check("every condition control has a merge order", ok)
print(f"\n{len(fails)} failure(s)")
raise SystemExit(1 if fails else 0)
