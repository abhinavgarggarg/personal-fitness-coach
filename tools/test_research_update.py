#!/usr/bin/env python3
"""Tests for the research-update workflow (tools/research_update.py). Run: python3 -I tools/test_research_update.py"""
import copy
import importlib.util
import json
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("research_update", HERE / "research_update.py")
ru = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ru)


def registry():
    return {
        "registry_version": "1.1.1", "status": "APPROVED — Product Owner Phase 1 sign-off on 2026-10-07", "generated": "2026-10-09",
        "confidence_scale": ["High Evidence", "Moderate Evidence", "Limited Evidence", "Expert Practice", "Product Rule (not an evidence claim)"],
        "versioning": {}, "verification_pending": ["A2020 (full text)"],
        "sources": {"A2020": {"citation": "A. 2020.", "verification": "located", "url": ""},
                    "B2021": {"citation": "B. 2021.", "verification": "checked", "url": ""}},
        "rules": [
            {"rule_id": "VOL-001", "version": "1.2.0", "category": "volume", "title": "Weekly sets", "statement": "10-20 sets.",
             "parameters": {"sets": {"min": 10, "max": 20}, "unit": "hard sets"}, "confidence": "Moderate Evidence", "evidence": ["A2020"],
             "uncertainty": "", "date_introduced": "2026-10-07", "date_reviewed": "2026-10-07", "affected_algorithms": ["volume_planner"],
             "tests": ["TC-VOL-001a", "TC-VOL-001b"], "change_reason": "Initial", "status": "approved", "verification_pending": ["A2020"]},
            {"rule_id": "SAF-002", "version": "1.0.0", "category": "safety", "title": "Red flags", "statement": "Stop.",
             "parameters": {"flags": 9}, "confidence": "High Evidence", "evidence": ["B2021"], "uncertainty": "", "date_introduced": "2026-10-07",
             "date_reviewed": "2026-10-07", "affected_algorithms": ["safety_validator"], "tests": ["TC-SAF-002a", "TC-SAF-002b"],
             "change_reason": "Initial", "status": "approved"},
        ],
    }


def change(**kw):
    cs = {"registry_version": "1.1.2", "base_registry_version": "1.1.1", "kind": "PATCH", "date": "2026-10-12", "title": "Test",
          "approval": {"by": "Product Owner", "date": "2026-10-12", "reference": "D-999"}}
    cs.update(kw)
    return cs


class Validate(unittest.TestCase):
    def ok(self, cs, reg=None):
        errs = ru.validate(cs, reg or registry())
        self.assertEqual([], errs)

    def bad(self, cs, fragment, reg=None, proposal=False):
        errs = ru.validate(cs, reg or registry(), proposal=proposal)
        self.assertTrue(any(fragment in e for e in errs), f"expected '{fragment}' in {errs}")

    def test_an_evidence_patch_is_valid(self):
        self.ok(change(changed_rules=[{"rule_id": "VOL-001", "from_version": "1.2.0", "to_version": "1.2.1", "change": "PATCH",
                                       "reason": "evidence checked", "evidence_add": ["B2021"], "verification_pending": None}],
                       sources_update={"A2020": {"verification": "checked 2026-10-12"}}))

    def test_the_versions_must_follow_the_kind(self):
        self.bad(change(registry_version="1.2.0"), "makes 1.1.2")
        self.bad(change(base_registry_version="1.1.0", registry_version="1.1.1"), "not the current registry")
        self.bad(change(changed_rules=[{"rule_id": "VOL-001", "from_version": "1.2.0", "to_version": "1.3.0", "change": "PATCH", "reason": "x"}]),
                 "makes 1.2.1")
        self.bad(change(changed_rules=[{"rule_id": "VOL-001", "from_version": "1.1.0", "to_version": "1.1.1", "change": "PATCH", "reason": "x"}]),
                 "the rule is at 1.2.0")

    def test_a_parameter_change_is_at_least_minor(self):
        c = {"rule_id": "VOL-001", "from_version": "1.2.0", "to_version": "1.2.1", "change": "PATCH", "reason": "x", "parameters_set": {"sets.max": 22}}
        self.bad(change(changed_rules=[c]), "at least MINOR")
        c = dict(c, change="MINOR", to_version="1.3.0")
        self.bad(change(changed_rules=[c]), "contains a MINOR change")   # the set itself is still PATCH
        self.ok(change(kind="MINOR", registry_version="1.2.0", changed_rules=[c]))

    def test_unknown_parameters_and_evidence_are_refused(self):
        c = {"rule_id": "VOL-001", "from_version": "1.2.0", "to_version": "1.3.0", "change": "MINOR", "reason": "x", "parameters_set": {"sets.top": 22}}
        self.bad(change(kind="MINOR", registry_version="1.2.0", changed_rules=[c]), "does not exist")
        c = {"rule_id": "VOL-001", "from_version": "1.2.0", "to_version": "1.2.1", "change": "PATCH", "reason": "x", "evidence_add": ["Nope2030"]}
        self.bad(change(changed_rules=[c]), "is not a source")

    def test_a_safety_rule_change_needs_a_safety_review(self):
        c = {"rule_id": "SAF-002", "from_version": "1.0.0", "to_version": "1.1.0", "change": "MINOR", "reason": "x", "parameters_set": {"flags": 10}}
        cs = change(kind="MINOR", registry_version="1.2.0", changed_rules=[c])
        self.bad(cs, "safety review")
        self.ok(dict(cs, safety_review="Reviewed: one more red flag stops a session; nothing becomes less strict."))
        # Evidence alone on a safety rule is a PATCH and needs no safety review.
        self.ok(change(changed_rules=[{"rule_id": "SAF-002", "from_version": "1.0.0", "to_version": "1.0.1", "change": "PATCH", "reason": "x",
                                       "evidence_add": ["A2020"]}]))

    def test_retiring_a_rule_is_major_and_reviewed(self):
        cs = change(kind="MINOR", registry_version="1.2.0", retired_rules=[{"rule_id": "VOL-001", "from_version": "1.2.0", "reason": "superseded"}])
        self.bad(cs, "contains a MAJOR change")
        cs = dict(cs, kind="MAJOR", registry_version="2.0.0")
        self.bad(cs, "safety review")
        self.ok(dict(cs, safety_review="Reviewed."))

    def test_a_new_rule_needs_its_fields_tests_and_sources(self):
        nr = {"rule_id": "VOL-009", "version": "1.0.0", "category": "volume", "title": "t", "statement": "s", "parameters": {},
              "confidence": "Expert Practice", "evidence": ["A2020"], "uncertainty": "", "affected_algorithms": [], "tests": ["TC-VOL-009a", "TC-VOL-009b"],
              "change_reason": "new"}
        self.ok(change(kind="MINOR", registry_version="1.2.0", new_rules=[nr]))
        self.bad(change(new_rules=[nr]), "contains a MINOR change")
        self.bad(change(kind="MINOR", registry_version="1.2.0", new_rules=[dict(nr, tests=["TC-VOL-009a"])]), "at least two tests")
        self.bad(change(kind="MINOR", registry_version="1.2.0", new_rules=[dict(nr, rule_id="VOL-001")]), "already used")
        self.bad(change(kind="MINOR", registry_version="1.2.0", new_rules=[dict(nr, confidence="Certain")]), "not on the scale")
        self.bad(change(kind="MINOR", registry_version="1.2.0", new_rules=[{k: v for k, v in nr.items() if k != "statement"}]), "missing statement")

    def test_only_an_evidence_patch_may_be_delegated(self):
        delegated = {"by": "Delegated: evidence verification, reported at the Phase 3 gate", "date": "2026-10-12"}
        evidence = {"rule_id": "VOL-001", "from_version": "1.2.0", "to_version": "1.2.1", "change": "PATCH", "reason": "checked",
                    "verification_pending": None}
        self.ok(change(approval=delegated, changed_rules=[evidence]))
        wording = dict(evidence, statement="10-20 hard sets.")
        self.bad(change(approval=delegated, changed_rules=[wording]), "only the Product Owner")
        self.ok(change(changed_rules=[wording]))

    def test_applying_needs_the_product_owners_approval(self):
        cs = change()
        del cs["approval"]
        self.bad(cs, "approval")
        self.assertEqual([], ru.validate(cs, registry(), proposal=True))


class Apply(unittest.TestCase):
    def test_apply_versions_the_rule_and_the_registry(self):
        cs = change(kind="MINOR", registry_version="1.2.0",
                    changed_rules=[{"rule_id": "VOL-001", "from_version": "1.2.0", "to_version": "1.3.0", "change": "MINOR", "reason": "new trial",
                                    "parameters_set": {"sets.max": 22}, "evidence_add": ["B2021"], "verification_pending": None}],
                    sources_update={"A2020": {"verification": "checked 2026-10-12 (abstract)"}}, verification_pending=[])
        reg = registry()
        out = ru.apply(cs, reg)
        r = {x["rule_id"]: x for x in out["rules"]}["VOL-001"]
        self.assertEqual("1.3.0", r["version"])
        self.assertEqual(22, r["parameters"]["sets"]["max"])
        self.assertEqual(10, r["parameters"]["sets"]["min"])
        self.assertEqual(["A2020", "B2021"], r["evidence"])
        self.assertNotIn("verification_pending", r)
        self.assertEqual("2026-10-12", r["date_reviewed"])
        self.assertTrue(r["change_reason"].startswith("MINOR: new trial"))
        self.assertEqual("1.2.0", out["registry_version"])
        self.assertEqual("checked 2026-10-12 (abstract)", out["sources"]["A2020"]["verification"])
        self.assertEqual([], out["verification_pending"])
        self.assertIn("research update 1.2.0 approved by Product Owner on 2026-10-12", out["status"])
        self.assertEqual("1.2.0", registry()["rules"][0]["version"], "the input is not changed")
        # Applying again on top is refused: the base no longer matches.
        self.assertTrue(any("not the current registry" in e for e in ru.validate(cs, out)))

    def test_retired_rules_leave_a_record(self):
        cs = change(kind="MAJOR", registry_version="2.0.0", safety_review="Reviewed.",
                    retired_rules=[{"rule_id": "VOL-001", "from_version": "1.2.0", "reason": "superseded by VOL-009"}])
        out = ru.apply(cs, registry())
        self.assertEqual(["SAF-002"], [r["rule_id"] for r in out["rules"]])
        self.assertEqual("VOL-001", out["retired_rules"][0]["rule_id"])
        self.assertEqual("1.2.0", out["retired_rules"][0]["last_version"])

    def test_impact_lists_every_rule_touched(self):
        cs = change(changed_rules=[{"rule_id": "SAF-002", "from_version": "1.0.0", "to_version": "1.0.1", "change": "PATCH", "reason": "x",
                                    "evidence_add": ["A2020"]}])
        md = ru.impact(cs, registry())
        self.assertIn("| SAF-002 | PATCH 1.0.0→1.0.1: evidence + A2020 | yes |", md)


class Repository(unittest.TestCase):
    """The real registry: the current file is the baseline plus the change sets in rules/changes, and every change set there is valid."""

    def test_the_registry_is_reproduced_from_its_change_sets(self):
        self.assertEqual(json.loads(ru.REGISTRY.read_text(encoding="utf-8")), ru.replay())


if __name__ == "__main__":
    unittest.main(verbosity=1)
