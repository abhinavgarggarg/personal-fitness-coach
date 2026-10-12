#!/usr/bin/env python3
"""The research-update workflow (Phase 1 sections 37-38, change control F7; Phase 3 Part 8).

A research update reaches the app only as a *change set*: one JSON file in rules/changes/ that says, rule by rule, what changes
and why, who approved it, and (for safety rules) the safety review. This tool checks a change set against the versioning rules,
reports its impact, and applies it to the Rule Registry. The registry file is never edited by hand: CI replays every change set
from the archived baseline and fails if the result differs from rules/rule_registry_v1.0.json.

    python3 -I tools/research_update.py validate rules/changes/registry_1.1.2.json [--proposal]
    python3 -I tools/research_update.py impact   rules/changes/registry_1.1.2.json [--markdown out.md]
    python3 -I tools/research_update.py apply    rules/changes/registry_1.1.2.json
    python3 -I tools/research_update.py replay --check

--proposal: the change set is not approved yet (step 7, impact analysis before the Product Owner decides); approval is then not
required. `apply` always requires the approval.

Change-set format (every key optional unless marked *):
{
  "registry_version"*: "1.1.2",               the version after this change set
  "base_registry_version"*: "1.1.1",          must equal the current registry's version
  "kind"*: "PATCH" | "MINOR" | "MAJOR",      at least the largest change below
  "date"*: "YYYY-MM-DD",
  "title"*: "...",
  "summary": "...",
  "approval": {"by": "Product Owner", "date": "YYYY-MM-DD", "reference": "D-xxx"},     * for apply; an evidence-only PATCH
                                             may be approved "Delegated: ..." and reported at the next gate (D-106)
  "safety_review": "...",                    * when a safety rule's wording, parameters or confidence change, or a rule is retired
  "sources_add": {"Key": {"citation": "...", "verification": "checked YYYY-MM-DD (...)", "url": "..."}},
  "sources_update": {"Key": {"verification": "...", "url": "...", "note": "..."}},
  "verification_pending": [...],             replaces the registry's list of sources still to be checked
  "new_rules": [{full rule: rule_id, version "1.0.0", category, title, statement, parameters, confidence, evidence,
                 uncertainty, affected_algorithms, tests [TC-<id>a, TC-<id>b, ...], change_reason}],
  "changed_rules": [{"rule_id"*, "from_version"*, "to_version"*, "change"*: PATCH|MINOR|MAJOR, "reason"*,
                     "statement", "parameters_set": {"dotted.key": value}, "parameters_replace": {...},
                     "evidence_add": [...], "evidence_remove": [...], "uncertainty", "confidence", "tests_add": [...],
                     "verification_pending": [...] (or null to clear)}],
  "retired_rules": [{"rule_id"*, "from_version"*, "reason"*}]
}
Versioning (registry `versioning`): PATCH = wording, evidence list or test reference only; MINOR = a parameter value or a new rule;
MAJOR = training direction, a safety boundary, or a removed rule. A rule's to_version is its from_version bumped by its change kind.
"""
import copy
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REGISTRY = ROOT / "rules" / "rule_registry_v1.0.json"
CHANGES = ROOT / "rules" / "changes"
ARCHIVE = ROOT / "rules" / "archive"
# The last registry built by tools/build_registry.py; every later version comes from change sets applied by this tool.
BASELINE = "1.1.1"
KINDS = ("PATCH", "MINOR", "MAJOR")
RULE_FIELDS = ("rule_id", "version", "category", "title", "statement", "parameters", "confidence", "evidence", "uncertainty",
               "affected_algorithms", "tests", "change_reason")
SOURCE_DIRS = ("engine/src/main", "data/src/main", "app/src/main")
TEST_DIRS = ("engine/src/test", "data/src/test", "app/src/test", "app/src/androidTest")


def vtuple(v):
    p = v.split(".")
    if len(p) != 3 or not all(x.isdigit() for x in p):
        raise ValueError(f"not a semantic version: {v}")
    return tuple(int(x) for x in p)


def bump(v, kind):
    a, b, c = vtuple(v)
    return {"PATCH": f"{a}.{b}.{c + 1}", "MINOR": f"{a}.{b + 1}.0", "MAJOR": f"{a + 1}.0.0"}[kind]


def is_safety(rule):
    """A safety rule: the safety category, or a rule the final safety check (safety_validator) relies on."""
    return rule["category"] == "safety" or "safety_validator" in rule.get("affected_algorithms", [])


def evidence_only(cs):
    """A PATCH that changes no rule's wording, parameters or confidence, and adds or retires no rule: sources, evidence lists,
    uncertainty and verification notes only (D-106)."""
    return cs.get("kind") == "PATCH" and not cs.get("new_rules") and not cs.get("retired_rules") and all(
        not ({"statement", "parameters_set", "parameters_replace", "confidence"} & set(c)) for c in cs.get("changed_rules", []) or [])


def set_dotted(d, key, value):
    parts = key.split(".")
    for p in parts[:-1]:
        if p not in d or not isinstance(d[p], dict):
            raise KeyError(key)
        d = d[p]
    if parts[-1] not in d:
        raise KeyError(key)
    d[parts[-1]] = value


def load(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


# ------------------------------------------------------------------------------------------------------------------- validate

def validate(cs, reg, proposal=False):
    """Every problem with change set [cs] against registry [reg], as plain sentences (empty = valid)."""
    errs = []
    E = errs.append
    for k in ("registry_version", "base_registry_version", "kind", "date", "title"):
        if not cs.get(k):
            E(f"missing '{k}'")
    if errs:
        return errs
    kind = cs["kind"]
    if kind not in KINDS:
        return [f"kind must be one of {KINDS}, not {kind}"]
    if cs["base_registry_version"] != reg["registry_version"]:
        E(f"base_registry_version {cs['base_registry_version']} is not the current registry {reg['registry_version']}")
    try:
        if cs["registry_version"] != bump(cs["base_registry_version"], kind):
            E(f"a {kind} change set on {cs['base_registry_version']} makes {bump(cs['base_registry_version'], kind)}, not {cs['registry_version']}")
    except ValueError as e:
        E(str(e))
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", cs["date"]):
        E("date must be YYYY-MM-DD")
    if not proposal:
        a = cs.get("approval") or {}
        if not a.get("by") or not a.get("date"):
            E("approval (by, date) is required to apply a change set (Product Owner, Phase 1 step 8)")
        elif not a["by"].startswith("Product Owner") and not evidence_only(cs):
            # D-106: only an evidence-only PATCH may be applied under delegation (and is reported at the next gate).
            E("only the Product Owner can approve a change to a rule's wording, parameters or confidence, a new rule or a retired rule")

    rules = {r["rule_id"]: r for r in reg["rules"]}
    sources = dict(reg["sources"])
    for key, s in (cs.get("sources_add") or {}).items():
        if key in sources:
            E(f"source {key} already exists (use sources_update)")
        if not s.get("citation") or not s.get("verification"):
            E(f"new source {key} needs a citation and a verification note")
        sources[key] = s
    for key in (cs.get("sources_update") or {}):
        if key not in sources:
            E(f"sources_update: unknown source {key}")
    for k in cs.get("verification_pending", []) or []:
        if not isinstance(k, str) or not k.strip():
            E("verification_pending entries must be non-empty text")

    needed = 0   # the largest change kind actually present
    safety_touched = []
    seen = set()
    scale = reg["confidence_scale"]

    for nr in cs.get("new_rules", []) or []:
        rid = nr.get("rule_id", "?")
        needed = max(needed, KINDS.index("MINOR"))
        missing = [f for f in RULE_FIELDS if f not in nr]
        if missing:
            E(f"new rule {rid}: missing {', '.join(missing)}")
            continue
        if rid in rules or rid in seen:
            E(f"new rule {rid}: the ID is already used")
        seen.add(rid)
        if not re.fullmatch(r"[A-Z]+-\d{3}", rid):
            E(f"new rule {rid}: IDs look like ABC-001")
        if nr["version"] != "1.0.0":
            E(f"new rule {rid}: starts at version 1.0.0")
        if nr["confidence"] not in scale:
            E(f"new rule {rid}: confidence '{nr['confidence']}' is not on the scale")
        if len(nr["tests"]) < 2 or not all(t.startswith(f"TC-{rid}") for t in nr["tests"]):
            E(f"new rule {rid}: needs at least two tests named TC-{rid}a, TC-{rid}b")
        for ev in nr["evidence"]:
            if ev not in sources:
                E(f"new rule {rid}: evidence {ev} is not a source")
        if is_safety(nr):
            safety_touched.append(rid)

    for c in cs.get("changed_rules", []) or []:
        rid = c.get("rule_id", "?")
        for k in ("rule_id", "from_version", "to_version", "change", "reason"):
            if not c.get(k):
                E(f"changed rule {rid}: missing '{k}'")
        if rid not in rules:
            E(f"changed rule {rid}: no such rule")
            continue
        if rid in seen:
            E(f"changed rule {rid}: appears twice in this change set")
        seen.add(rid)
        r = rules[rid]
        ch = c.get("change")
        if ch not in KINDS:
            E(f"changed rule {rid}: change must be one of {KINDS}")
            continue
        needed = max(needed, KINDS.index(ch))
        if c.get("from_version") != r["version"]:
            E(f"changed rule {rid}: from_version {c.get('from_version')} but the rule is at {r['version']}")
        elif c.get("to_version") != bump(r["version"], ch):
            E(f"changed rule {rid}: a {ch} change on {r['version']} makes {bump(r['version'], ch)}, not {c.get('to_version')}")
        params = "parameters_set" in c or "parameters_replace" in c
        if params and ch == "PATCH":
            E(f"changed rule {rid}: a parameter change is at least MINOR (PATCH is wording, evidence or tests only)")
        if "confidence" in c and ch == "PATCH":
            E(f"changed rule {rid}: a confidence change is at least MINOR")
        if "confidence" in c and c["confidence"] not in scale:
            E(f"changed rule {rid}: confidence '{c['confidence']}' is not on the scale")
        trial = copy.deepcopy(r["parameters"])
        for key, v in (c.get("parameters_set") or {}).items():
            try:
                set_dotted(trial, key, v)
            except KeyError:
                E(f"changed rule {rid}: parameter '{key}' does not exist (use parameters_replace to restructure)")
        for ev in c.get("evidence_add", []) or []:
            if ev not in sources:
                E(f"changed rule {rid}: evidence {ev} is not a source")
        for ev in c.get("evidence_remove", []) or []:
            if ev not in r["evidence"]:
                E(f"changed rule {rid}: evidence {ev} is not on the rule")
        for t in c.get("tests_add", []) or []:
            if not t.startswith(f"TC-{rid}"):
                E(f"changed rule {rid}: test {t} must be named TC-{rid}<letter>")
        for k in c.get("verification_pending") or []:
            if k not in sources:
                E(f"changed rule {rid}: verification_pending {k} is not a source")
        substantive = params or "statement" in c or "confidence" in c
        if is_safety(r) and substantive:
            safety_touched.append(rid)
        known = {"rule_id", "from_version", "to_version", "change", "reason", "statement", "parameters_set", "parameters_replace",
                 "evidence_add", "evidence_remove", "uncertainty", "confidence", "tests_add", "verification_pending"}
        extra = set(c) - known
        if extra:
            E(f"changed rule {rid}: unknown keys {sorted(extra)}")

    for x in cs.get("retired_rules", []) or []:
        rid = x.get("rule_id", "?")
        needed = max(needed, KINDS.index("MAJOR"))
        if rid not in rules:
            E(f"retired rule {rid}: no such rule")
            continue
        if x.get("from_version") != rules[rid]["version"]:
            E(f"retired rule {rid}: from_version {x.get('from_version')} but the rule is at {rules[rid]['version']}")
        if not x.get("reason"):
            E(f"retired rule {rid}: needs a reason")
        if rid in seen:
            E(f"retired rule {rid}: also changed in this change set")
        safety_touched.append(rid)  # removing any rule can remove a limit

    if needed > KINDS.index(kind):
        E(f"the change set is {kind} but contains a {KINDS[needed]} change")
    if safety_touched and not (cs.get("safety_review") or "").strip():
        E("a safety review note is required: " + ", ".join(sorted(set(safety_touched))))
    return errs


# ------------------------------------------------------------------------------------------------------------------- apply

def apply(cs, reg):
    """The registry after change set [cs] (assumed valid)."""
    out = copy.deepcopy(reg)
    rules = {r["rule_id"]: r for r in out["rules"]}
    date = cs["date"]
    approved = cs.get("approval") or {}
    note = f"{cs['title']} (registry {cs['registry_version']}" + (f", {approved['reference']}" if approved.get("reference") else "") + ")"
    for key, s in (cs.get("sources_add") or {}).items():
        out["sources"][key] = s
    for key, s in (cs.get("sources_update") or {}).items():
        out["sources"][key].update(s)
    for nr in cs.get("new_rules", []) or []:
        r = copy.deepcopy(nr)
        r.update({"date_introduced": date, "date_reviewed": date, "status": "approved"})
        r["change_reason"] = f"{r['change_reason']} — {note}"
        out["rules"].append(r)
    for c in cs.get("changed_rules", []) or []:
        r = rules[c["rule_id"]]
        if "statement" in c:
            r["statement"] = c["statement"]
        if "parameters_replace" in c:
            r["parameters"] = copy.deepcopy(c["parameters_replace"])
        for key, v in (c.get("parameters_set") or {}).items():
            set_dotted(r["parameters"], key, v)
        for ev in c.get("evidence_add", []) or []:
            if ev not in r["evidence"]:
                r["evidence"].append(ev)
        r["evidence"] = [e for e in r["evidence"] if e not in (c.get("evidence_remove") or [])]
        if "uncertainty" in c:
            r["uncertainty"] = c["uncertainty"]
        if "confidence" in c:
            r["confidence"] = c["confidence"]
        for t in c.get("tests_add", []) or []:
            if t not in r["tests"]:
                r["tests"].append(t)
        if "verification_pending" in c:
            if c["verification_pending"]:
                r["verification_pending"] = list(c["verification_pending"])
            else:
                r.pop("verification_pending", None)
        r["version"] = c["to_version"]
        r["date_reviewed"] = date
        r["change_reason"] = f"{c['change']}: {c['reason']} — {note}"
    retired = {x["rule_id"] for x in cs.get("retired_rules", []) or []}
    if retired:
        out.setdefault("retired_rules", [])
        for x in cs["retired_rules"]:
            r = rules[x["rule_id"]]
            out["retired_rules"].append({"rule_id": r["rule_id"], "last_version": r["version"], "title": r["title"],
                                         "retired": date, "reason": x["reason"]})
        out["rules"] = [r for r in out["rules"] if r["rule_id"] not in retired]
    if "verification_pending" in cs:
        out["verification_pending"] = list(cs["verification_pending"])
    out["registry_version"] = cs["registry_version"]
    out["generated"] = date
    out["status"] = re.sub(r"(; research update [^;]*)*$", "", out["status"]) + \
        f"; research update {cs['registry_version']} approved by {approved.get('by', '?')} on {approved.get('date', '?')}"
    return out


def write_registry(reg):
    REGISTRY.write_text(json.dumps(reg, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


# ------------------------------------------------------------------------------------------------------------------- impact

def code_refs(rule_id):
    """Source files (outside tests) that use the rule's generated constants or ID."""
    ident = rule_id.replace("-", "_")
    pat = re.compile(r"\b" + re.escape(ident) + r"\b|\"" + re.escape(rule_id) + r"\"")
    hits = []
    for d in SOURCE_DIRS:
        for f in (ROOT / d).rglob("*.kt"):
            if f.name == "GeneratedRegistry.kt":
                continue
            if pat.search(f.read_text(encoding="utf-8")):
                hits.append(str(f.relative_to(ROOT)))
    return sorted(hits)


def test_refs(rule_id):
    pat = re.compile(r"TC-" + re.escape(rule_id) + r"[a-z]\b")
    hits = {}
    for d in TEST_DIRS:
        for f in (ROOT / d).rglob("*.kt"):
            for m in pat.finditer(f.read_text(encoding="utf-8")):
                hits.setdefault(m.group(0), set()).add(str(f.relative_to(ROOT)))
    return hits


def impact(cs, reg):
    """Phase 1 step 7: what the change touches — rules, algorithms, code, tests — as Markdown."""
    rules = {r["rule_id"]: r for r in reg["rules"]}
    lines = [f"# Impact of change set {cs.get('registry_version')} ({cs.get('kind')})", "",
             f"{cs.get('title', '')}", "", f"Base registry {cs.get('base_registry_version')} · {cs.get('date')}", ""]
    if cs.get("summary"):
        lines += [cs["summary"], ""]
    lines += ["| Rule | Change | Safety rule | Algorithms | Code that uses it | Tests (registry → found) |", "| --- | --- | --- | --- | --- | --- |"]

    def row(rid, rule, what):
        algos = ", ".join(rule.get("affected_algorithms", [])) or "—"
        code = ", ".join(Path(p).name for p in code_refs(rid)) or "none yet"
        found = test_refs(rid)
        tests = ", ".join(f"{t} {'✓' if t in found else '✗ to write'}" for t in rule.get("tests", []))
        lines.append(f"| {rid} | {what} | {'yes' if is_safety(rule) else 'no'} | {algos} | {code} | {tests} |")

    for nr in cs.get("new_rules", []) or []:
        row(nr["rule_id"], nr, "new rule (MINOR)")
    for c in cs.get("changed_rules", []) or []:
        r = rules.get(c["rule_id"])
        if r is None:
            continue
        parts = []
        if "statement" in c:
            parts.append("wording")
        for k, v in (c.get("parameters_set") or {}).items():
            old = r["parameters"]
            for p in k.split("."):
                old = old.get(p) if isinstance(old, dict) else None
            parts.append(f"{k}: {json.dumps(old)} → {json.dumps(v)}")
        if "parameters_replace" in c:
            parts.append("parameters replaced")
        if c.get("evidence_add"):
            parts.append("evidence + " + ", ".join(c["evidence_add"]))
        if c.get("evidence_remove"):
            parts.append("evidence − " + ", ".join(c["evidence_remove"]))
        if "confidence" in c:
            parts.append(f"confidence: {r['confidence']} → {c['confidence']}")
        if "uncertainty" in c:
            parts.append("uncertainty note")
        if "verification_pending" in c:
            parts.append("verification note")
        row(c["rule_id"], r, f"{c['change']} {c['from_version']}→{c['to_version']}: " + "; ".join(parts or ["—"]))
    for x in cs.get("retired_rules", []) or []:
        r = rules.get(x["rule_id"])
        if r is not None:
            row(x["rule_id"], r, "retired (MAJOR)")
    if cs.get("sources_add") or cs.get("sources_update"):
        lines += ["", "**Sources:** " + ", ".join(
            [f"{k} (new)" for k in (cs.get("sources_add") or {})] + [f"{k} ({v.get('verification', 'updated')})" for k, v in (cs.get("sources_update") or {}).items()])]
    lines += ["", "**Before release:** every rule above keeps its two tests passing (rule coverage 100%); safety rules need the safety "
              "review note; the Product Owner approves the change set; the app is rebuilt and the whole test suite runs (Phase 1 step 10)."]
    return "\n".join(lines) + "\n"


# ------------------------------------------------------------------------------------------------------------------- replay

def change_sets_after(version):
    sets = []
    for f in sorted(CHANGES.glob("registry_*.json")):
        cs = load(f)
        if vtuple(cs["base_registry_version"]) >= vtuple(version):
            sets.append((vtuple(cs["registry_version"]), f, cs))
    return [(f, cs) for _, f, cs in sorted(sets, key=lambda x: x[0])]


def replay():
    reg = load(ARCHIVE / f"rule_registry_{BASELINE}.json")
    for f, cs in change_sets_after(BASELINE):
        errs = validate(cs, reg)
        if errs:
            raise SystemExit(f"{f.name}: " + "; ".join(errs))
        reg = apply(cs, reg)
    return reg


# ------------------------------------------------------------------------------------------------------------------- main

def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    cmd = argv[1]
    if cmd == "replay":
        rebuilt = replay()
        current = load(REGISTRY)
        same = rebuilt == current
        print(("PASS" if same else "FAIL") + f" registry {current['registry_version']} is the baseline {BASELINE} plus "
              f"{len(change_sets_after(BASELINE))} change set(s)")
        if not same and "--check" not in argv:
            write_registry(rebuilt)
            print("rewrote", REGISTRY.relative_to(ROOT))
        return 0 if same or "--check" not in argv else 1
    if len(argv) < 3:
        print(__doc__)
        return 2
    cs = load(argv[2])
    reg = load(REGISTRY)
    if cmd == "validate":
        errs = validate(cs, reg, proposal="--proposal" in argv)
        for e in errs:
            print("FAIL", e)
        print(f"{'PASS' if not errs else 'FAIL'}: change set {cs.get('registry_version')} — {len(errs)} problem(s)")
        return 1 if errs else 0
    if cmd == "impact":
        md = impact(cs, reg)
        if "--markdown" in argv:
            Path(argv[argv.index("--markdown") + 1]).write_text(md, encoding="utf-8")
        print(md)
        return 0
    if cmd == "apply":
        errs = validate(cs, reg)
        if errs:
            for e in errs:
                print("FAIL", e)
            return 1
        ARCHIVE.mkdir(exist_ok=True)
        archived = ARCHIVE / f"rule_registry_{reg['registry_version']}.json"
        if not archived.exists():
            archived.write_text(json.dumps(reg, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        new = apply(cs, reg)
        write_registry(new)
        subprocess.run([sys.executable, "-I", str(ROOT / "tools" / "gen_registry_kotlin.py")], check=True)
        print(f"applied {Path(argv[2]).name}: registry {reg['registry_version']} → {new['registry_version']}; "
              f"previous version archived as {archived.relative_to(ROOT)}")
        missing = [t for r in new["rules"] for t in r["tests"] if t not in test_refs(r["rule_id"])]
        print(("next: write the tests still missing (" + ", ".join(missing) + "), then " if missing else "next: no new tests are needed; ") +
              "run the full test suite, and record the decision (D-xxx) and the changelog entry")
        return 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
