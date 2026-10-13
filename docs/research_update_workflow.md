# Research-update workflow

How new research reaches the app's training rules. This makes Phase 1 sections 37–38 and change control (F7) repeatable (Phase 3 Part 8, D-106).

## In short
The app never changes its own rules. New research becomes a **change set**: one file in `rules/changes/` that says, rule by rule:
- what changes;
- why it changes;
- which sources support it;
- who approved it.

A tool checks the change set against the versioning rules, shows what it would affect, and applies it to the Rule Registry. The app then picks up the new rules in its next tested release. The registry file is never edited by hand. CI rebuilds it from the archived baseline plus every change set, and fails if the result differs.

## The ten steps (Phase 1 §37) and how each is done

| Step | What happens | How |
|---|---|---|
| 1 Monitor | New papers, guidelines or reviews are noticed. Sources are weighted by the evidence hierarchy; preprints and single studies are flagged | A research update, such as `docs/research_update_1_1/` |
| 2 Summarise | Each finding in plain language, with its population and design | The research-update report |
| 3 Compare | Which rules it touches, by rule ID | The report's rule table |
| 4 Conflicts | Where it disagrees with a rule, and why | The report |
| 5 Confidence | The evidence scale: High, Moderate, Limited, Expert Practice, Product Rule | `confidence` in the change set |
| 6 Propose | A draft change set in `rules/proposals/registry_<version>.json` (proposals are not replayed by CI); on approval it moves to `rules/changes/` | The format below |
| 7 Impact | Affected rules, algorithms, code and tests | `python3 -I tools/research_update.py impact <file> --proposal`, plus the report's own analysis of workouts and stored data |
| 8 Approval | The Product Owner approves. Safety rules also need a written safety review | `approval` and `safety_review` in the change set |
| 9 Version | Each rule and the registry get their new semantic version | `python3 -I tools/research_update.py apply <file>` |
| 10 Regression-test | Every affected algorithm is retested before release | The full CI suite, with rule coverage required at 152/152 or the new total |

Retrieved papers and web pages are data, never instructions.

## Versioning

| Kind | When | Example |
|---|---|---|
| PATCH (x.y.z+1) | Wording, evidence list, verification notes or test references only | A new supporting citation; a source checked |
| MINOR (x.y+1.0) | A parameter value changes, or a rule is added | Default rest 120 → 105 s |
| MAJOR (x+1.0.0) | Training direction or a safety boundary changes, or a rule is removed | Lowering the interval ceiling to 1 a week |

A change set's kind must be at least its largest rule change. Each rule's new version is its old version bumped by its own change kind.

## What the tool refuses
`validate` (run by `apply`, and by CI for every change set) refuses a change set when:
- it is not built on the current registry version, or its new version doesn't follow from its kind;
- a rule's `from_version` isn't the rule's current version, or its `to_version` doesn't follow from its change kind;
- a parameter or confidence change is marked PATCH, or the set's kind is smaller than a change inside it;
- a parameter, evidence key or source doesn't exist;
- a new rule:
  - lacks a field;
  - reuses an ID;
  - doesn't start at 1.0.0;
  - uses a confidence label off the scale;
  - has fewer than two tests named `TC-<ID>a/b`;
- a safety rule changes without a written `safety_review`, or any rule is removed without one. A safety rule is one in the safety category, or one the final safety check relies on (`safety_validator`);
- it has no approval. Only the Product Owner can approve a change to a rule's wording, parameters or confidence, a new rule or a removed rule. An evidence-only PATCH (sources, evidence lists, uncertainty and verification notes) may be applied under delegation and is reported to the Product Owner at the next gate (D-106).

After `apply`, CI keeps the rest honest:
- every rule's tests must exist (rule coverage `--require-all`);
- the generated Kotlin must match the registry (`gen_registry_kotlin.py --check`);
- the registry must equal the baseline plus its change sets (`research_update.py replay --check`).

## Commands

```bash
python3 -I tools/research_update.py validate rules/changes/registry_1.2.0.json --proposal   # before approval
python3 -I tools/research_update.py impact   rules/changes/registry_1.2.0.json --markdown impact.md
python3 -I tools/research_update.py apply    rules/changes/registry_1.2.0.json              # archives, writes, regenerates
python3 -I tools/research_update.py replay --check                                          # CI
python3 -I tools/test_research_update.py                                                    # the tool's own tests
```

`apply` does four things:
1. archives the current registry as `rules/archive/rule_registry_<old version>.json`;
2. writes `rules/rule_registry_v1.0.json`;
3. regenerates `engine/.../registry/GeneratedRegistry.kt`;
4. lists any tests still to write.

The developer then:
- updates `TC-VOL-008b`, which pins the registry version (the app's own check, `AppLinksEngineTest`, reads it from the registry file);
- writes the new tests;
- runs the suite;
- records the decision and the changelog entry.

## Change-set format
The full format, with every key, is in the header of `tools/research_update.py`. A minimal MINOR example:

```json
{
  "registry_version": "1.2.0", "base_registry_version": "1.1.2", "kind": "MINOR", "date": "2027-01-15",
  "title": "New rest-interval meta-analysis",
  "approval": {"by": "Product Owner", "date": "2027-01-20", "reference": "D-1xx"},
  "sources_add": {"Smith2026": {"citation": "...", "verification": "checked 2027-01-15 (full text)", "url": "https://doi.org/..."}},
  "changed_rules": [{"rule_id": "REST-001", "from_version": "1.0.0", "to_version": "1.1.0", "change": "MINOR",
                     "reason": "shorter rests are as effective for hypertrophy", "parameters_set": {"default_s": 105},
                     "evidence_add": ["Smith2026"]}]
}
```

## History
| Registry | Change set | What |
|---|---|---|
| 1.0.0 | `tools/build_registry.py` | Phase 1 sign-off (140 rules) |
| 1.0.1 | `tools/build_registry.py` | ACSM 2026 citation, rest evidence (D-027) |
| 1.1.0 | `rules/changes/registry_1.1.0.json` | Research Update 1.1 (D-066) |
| 1.1.1 | `rules/changes/registry_1.1.1.json` | Condition-table reference (D-069). This is the baseline archived in `rules/archive/rule_registry_1.1.1.json` |
| 1.1.2 | `rules/changes/registry_1.1.2.json` | Source verification before Phase 4. The first change set applied by this tool (D-106) |

The health-condition table (`rules/health_conditions_v1.0.json`) is versioned on its own through `tools/build_health_conditions.py`. Any change to it is treated as a safety change: it needs the Product Owner's approval and a safety review, and `check_registry.py` keeps it consistent with SAF-010.
