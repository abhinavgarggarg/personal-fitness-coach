# CHANGELOG

## 2026-10-09 — Product Owner change requests CR-001 to CR-005
- Health-condition picker driven by a data table (D-058); starting weights and personal records (D-059); default goal "Lose fat, keep muscle (30+)" with an age-banded training mix and no food features (D-060); original animated exercise illustrations (D-061, sample published); phone step counter (D-062); no-box gyms (D-063).
- Fix: a missing plyo box no longer turns a box jump into a slow tempo squat; power work swaps only to power work (346 tests).
- Part 3 paused for Research Update 1.1. Change log: `docs/change_requests/README.md`.
- Research Update 1.1 ready for sign-off: evidence reviews (fat loss without diet, age and muscle, HIIT, steps, progress measures; 11 health conditions plus 3 optional; medical-device boundaries), proposed Registry 1.1.0 (9 new rules FL-001–005, STEP-001–002, SAF-010, CAL-002; CON-004, CON-006, PH-001, DATA-001 changed; MOD-001 decision) and health-condition table v1.0 draft. Independently checked: 18 findings fixed before release.

## 2026-10-08 — Phase 3 Part 2: exercise library, 12-month programme, week planner, session generator
- Exercise library v1.0.1 (`library/v1/*.json`, original wording): 152 exercises, 31 drills, 10 modality guides; generator with safety checks (`tools/gen_library_kotlin.py --check` in CI).
- Engine: reps, rest, effort, order and supersets; cardio zones, progression and interval menus; bodyweight and core ladders; mobility; frequency and experience; 12-month blueprint with block clock; week planner (days, exercise choice, volume within caps, balance, core minimum, cardio, time shaping); session generator (GEN-001, 10 steps ending in the validator; tiers, deload, swaps, illness rest day, express session); streak, milestones and session-RPE prompt.
- 345 tests passing locally and on GitHub (Kotlin 2.4.0); rule coverage 137/140 (remaining: DATA-001, SAF-009, COACH-001). Property tests over random users, gyms, injuries and schedules; 52-week simulation for five users with no rule broken.
- Independent review: 1 high, 11 medium, 8 low findings, all fixed with regression tests (R01–R20); three more issues found while fixing (core minimum, box jump as a main lift, lifts stuck behind coarse equipment steps).
- Decisions D-046 to D-057. **D-056 (moderate-only screening → Z1 cardio only) awaits Product Owner confirmation.**

## 2026-10-08 — First real Android build on GitHub
- Code pushed to github.com/abhinavgarggarg/personal-fitness-coach. CI green on the first run: engine 173/173 tests (Kotlin 2.4.0 via Gradle 9.7.0, AGP 9.4.0), app unit tests, debug APK, permission allow-list (no INTERNET), 16 KB page check.
- Device matrix green: launch tests on Android 10 (small phone), 12, 14 (200% text), 16 phone and 16 tablet emulators.
- GitHub Actions pinned to commit SHAs (checkout v7.0.1, setup-java v6.0.1, setup-python v7.0.0, upload-artifact v7.0.2, gradle/actions v6.4.0, android-emulator-runner v2.38.0); results reported as annotations.

## 2026-10-08 — Any-phone support and zero-cost AI (PO request)
- D-044: app targets any Android phone or tablet with Android 10+; device tests (`app/src/androidTest`) and a weekly emulator matrix (`.github/workflows/devices.yml`); 16 KB page-size check in CI; first screen scrolls at large text sizes.
- D-045: no AI service calls or tokens; "Ask Gemini" share button planned for the coach screens; on-device Gemini Nano only on Google-supported phones, later and optional.
- GitHub repository attached (read-only until the Claude GitHub App is installed).

## 2026-10-08 — Phase 3 Part 1: engine core, safety kernel, project foundation
- `:engine` (pure Kotlin): readiness and tiers, effort, e1RM, plate math, volume, SSU, session-RPE workload and EWMA, fatigue signals and deload; progression, in-session autoregulation, calibration, return to training, warm-up; time budget; substitution; safety kernel (screening, red flags, illness gate, pain gate, hard caps, user additions, session validator, week checks). Every engine output carries Decision Log entries with rule IDs.
- Rule Registry compiled into Kotlin (`GeneratedRegistry.kt`, registry 1.0.1, SHA-256 recorded); CI fails if it is stale.
- 173 JUnit tests passing locally, including seeded property tests (3,000 random sessions through the validator) and the Phase 1 worked examples. Rule coverage 73/140.
- Independent review of the safety kernel (two passes): 14 defects fixed, each with a regression test; safety inputs now fail closed.
- Project foundation: Gradle 9.7.0 wrapper, version catalog (AGP 9.4.0, Kotlin 2.4.0, Compose BOM 2026.09.00), app shell with permission allow-list and no INTERNET, GitHub Actions CI producing a debug APK — not yet run (GitHub not connected).
- Decisions D-028 to D-043.

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
