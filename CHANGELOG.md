# CHANGELOG

## 2026-10-07 — Phase 2 draft (awaiting sign-off)
- Phase 2 report: 19 C19 deliverables, navigation map, 6 wireframes, state machine and system diagrams — https://claude.ai/code/artifact/ddd84b34-8a3a-4466-b655-2c98bc041319 (markdown copy `docs/phase2/phase2_report.md`).
- Decisions D-019 to D-027 (stack, Tier 1 coach, no internet permission, alarm-based rest alerts, no SQLCipher, minSdk 29 assumption, manual DI, optional backup password, registry patch).
- Rule Registry 1.0.1 (patch): ACSM 2026 citation corrected; 16 rules at 1.0.1; no parameter changes.

## 2026-10-07 — Phase 1 approved; Phase 2 started
- Product Owner approved Phase 1. Rule Registry v1.0.0 (`rules/rule_registry_v1.0.json`): same 140 rules and parameters as v0.1.0, status "approved".
- Phase 2 doc created: https://claude.ai/code/artifact/ddd84b34-8a3a-4466-b655-2c98bc041319

## 2026-10-07 — Phase 1 draft
- Environment & capability audit (A8).
- Phase 1 report: all 40 B8 deliverables, 2 diagrams, 30-row traceability matrix, 16 open uncertainties.
- Rule Registry v0.1.0: 140 rules (8 High, 36 Moderate, 6 Limited, 75 Expert Practice, 15 Product rules), 64 sources (11 checked, 52 located, 1 unverified). Status: proposed.
- Tools: `build_registry.py`, `check_phase1.py` (44 checks passing).
