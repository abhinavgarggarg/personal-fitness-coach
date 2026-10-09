# PROJECT STATE — Personal Fitness Coach

Last updated: 2026-10-09 (Phase 3 Part 3 complete) · Master prompt: v3.0 (7 Oct 2026)

## Current phase
**Phase 3 — Application Development: IN PROGRESS. Parts 1–3 of 7 complete (Research Update 1.1 approved 9 Oct 2026 and built into the engine). Waiting for the Product Owner to reply CONTINUE for Part 4 (data layer).**

| Phase | Status | Date |
|---|---|---|
| 1 Fitness research & evidence engine | APPROVED | 2026-10-07 |
| 2 Product requirements, UX & architecture | APPROVED (D-028) | 2026-10-07 |
| 3 Application development | IN PROGRESS — Parts 1–3 of 7 complete | 2026-10-09 |
| 4 Testing, validation & UAT | NOT STARTED | — |
| 5 Production build & APK delivery | NOT STARTED | — |

## Phase 3 progress
| Part | Content | Status |
|---|---|---|
| 1 | Project foundation (Gradle, version catalog, wrapper, CI workflow, app shell); `:engine` calculators (readiness, e1RM, plate math, volume, SSU, workload, fatigue/deload); progression, autoregulation, calibration, return-to-training, warm-up; time budget, substitution; safety kernel (screening, red flags, illness gate, pain gate, caps, user additions, session validator, week checks); any-phone support (D-044) | **VERIFIED on GitHub Actions 2026-10-08**: CI run 37733788298 — engine 173/173 tests, app unit tests, debug APK built, permission allow-list PASS (no INTERNET), 16 KB check; Devices run 37733813409 — launch tests pass on Android 10 small phone, 12, 14 at 200% text, 16 phone, 16 tablet |
| 2 | Exercise library v1.0.1 (152 exercises, 31 drills, 10 modality guides, original text, generator with safety checks); reps, rest, effort, order; cardio and HIIT; bodyweight/core ladders; mobility; 12-month blueprint and block clock; week planner; session generator GEN-001; adherence; independent review (20 findings fixed, R01–R20) | **VERIFIED**: 345/345 engine tests locally and on GitHub Actions (CI run 37816759062, Kotlin 2.4.0; APK built; permissions PASS); property tests 10 seeds × 500 cases; 52-week simulation for 5 users with no rule broken. Report `docs/phase3/part2_report.md` |
| 3 | Research Update 1.1 in the engine: Registry 1.1.0 → 1.1.1 (12 new rules, 10 changed, per-person cardio machines), health-condition table 1.0.1 (21 entries, "strictest wins", applied by planner, generator and validator), library 1.1.1 (14 condition tags, "any of" equipment, low-impact power, balance drills, circuit moves, 5 machines); fat-loss goal and age-banded mix (FL-001–005), steps (STEP-001/002), own numbers (CAL-002), AGE-001/MOB-004/ADH-002/004/005, MOB-006 calm session, EQ-003 away-from-gym day; independent review (15 findings R3-01–R3-15 + 5 from the re-check R3-16–R3-20, all fixed) | **VERIFIED**: 406/406 engine tests locally; GitHub Actions CI green on every Part 3 commit (last: run 37946441731 for 7afcb8a — engine tests, app unit tests, debug APK, permissions PASS); property tests 10 seeds × 500 cases + condition-session checks 5 seeds × 300; 52-week simulation for 5 fat-loss users (ages 35–70, each with a condition) with no rule broken and strength gains for all. Report `docs/phase3/part3_report.md` |
| 4 | Data layer: Room storage seeded from `library/v1` and the condition table; repositories feeding the engine (history → e1RM, progression prescriptions per FS-5/D-055, weekly totals, weight/waist trends, step counter with ACTIVITY_RECOGNITION per D-062, own numbers per D-059, found calibration loads); DATA-001; backup/restore (optional password) | NEXT |
| 5 | Screens: onboarding (goal, conditions and doctor's-OK scopes, bodyweight and own numbers), today, session player, rest-timer alarms | NOT STARTED |
| 6 | History and progress (waist, weight trend, strength trend), Tier 1 coach (SAF-009, COACH-001), Ask Gemini share, settings, exercise animations (D-061) | NOT STARTED |
| 7 | Integration, research-update workflow, Phase 3 gate (152/152 rules) | NOT STARTED |

Rule coverage after Part 3: 149 of 152 rules have all their registry test cases (`docs/phase3/rule_coverage_part3.md`). Remaining: DATA-001 (Part 4), SAF-009 and COACH-001 (Part 6). `python3 tools/rule_coverage.py`; the Phase 3 gate requires 152/152 (`--require-all`).

## Where things live
In the claude.ai "Fitness App" Project, every repo file is stored under the `claude/` prefix (e.g. `claude/rules/rule_registry_v1.0.json`).

| What | Where |
|---|---|
| Phase 1 report (commentable) | https://claude.ai/code/artifact/277679ce-731c-4ce0-8870-9cb5347a5d51 · copy `docs/phase1/phase1_report.md` |
| Phase 2 report (commentable) | https://claude.ai/code/artifact/ddd84b34-8a3a-4466-b655-2c98bc041319 · copy `docs/phase2/phase2_report.md` |
| Phase 3 Part 1 report | `docs/phase3/part1_report.md` |
| Phase 3 Part 2 report | `docs/phase3/part2_report.md` · rule coverage `docs/phase3/rule_coverage_part2.md` |
| Phase 3 Part 3 report | `docs/phase3/part3_report.md` · rule coverage `docs/phase3/rule_coverage_part3.md` |
| Exercise library (source) | `library/v1/*.json` (v1.1.1) → `tools/gen_library_kotlin.py` (writes `engine/.../library/GeneratedLibrary*.kt`; `--check` fails if stale) |
| Rule Registry (approved, registry_version 1.1.1, 152 rules) | `rules/rule_registry_v1.0.json`, built by `tools/build_registry.py` from the Phase 1 rules plus `rules/changes/registry_1.1.0.json` and `registry_1.1.1.json`; Phase 1 snapshot `rules/archive/rule_registry_1.0.1.json`; `tools/check_registry.py` |
| Health-condition table (SAF-010, v1.0.1) | `rules/health_conditions_v1.0.json`, built by `tools/build_health_conditions.py`; `tools/gen_conditions_kotlin.py` writes `engine/.../safety/GeneratedConditions.kt` (`--check` fails if stale) |
| Registry → Kotlin generator | `tools/gen_registry_kotlin.py` (writes `engine/.../registry/GeneratedRegistry.kt`; `--check` fails if stale) |
| Engine source and tests | `engine/src/main/kotlin/com/personalfitnesscoach/engine/` (core, model, calc, progression, planning, safety, registry, library, dose, conditioning, program, generation) · `engine/src/test/...` (incl. `simulation/` property tests and 52-week simulation, `review/` regression tests) |
| App shell | `app/` (Compose, offline, permissions allow-list in the manifest) |
| Build | `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradlew` (Gradle 9.7.0) |
| CI | `.github/workflows/ci.yml` — registry check, Phase 1 checks, engine tests, rule coverage, app tests, debug APK, permission allow-list, 16 KB page check · `.github/workflows/devices.yml` — weekly emulator matrix (Android 10–16, small phone, tablet, 200% text) |
| Local engine build | `tools/local_build.sh` (Gradle's bundled Kotlin 2.0.21 + JUnit 4; no network) |
| Checks | `tools/check_phase1.py` (45 checks, 0 failures), `tools/check_registry.py`, `tools/rule_coverage.py`, `tools/check_permissions.py` |
| Decisions | `DECISIONS.md` (D-001 … D-072) |
| Change requests | `docs/change_requests/README.md` (CR-001 … CR-006) |
| Research Update 1.1 (approved 2026-10-09) | `docs/research_update_1_1/report.md` · evidence reviews A–D in the same folder · change set `rules/changes/registry_1.1.0.json` (builder `tools/build_change_1_1_0.py`) |
| Illustration sample (CR-004) | https://claude.ai/artifact/DYdTjK75unCNA4Rk3GoiBt (Form Guide Preview) |
| Code (source of truth) | https://github.com/abhinavgarggarg/personal-fitness-coach (branch `main`); Actions: CI on every push, Devices weekly / on demand |

## Environment (re-audited 2026-10-09)
- The session container can be reclaimed between conversations: re-clone from GitHub (the source of truth) when the folder is missing.
- Available: shell, Python 3, git, JDK 21, Gradle 8.14.3 with bundled Kotlin 2.0.21 compiler and JUnit 4.13.2, web search (allowed by default for this project), `gh` (run it without the session's GH_TOKEN variable).
- Blocked here: Google Maven, Maven Central, Gradle Plugin Portal, services.gradle.org (proxy 403); no Android SDK, emulator or device; GitHub Actions log/artifact downloads (blob storage 403); some developer.android.com pages need fetch permission.
- Available: push to and read the GitHub repository; `gh` for runs, workflow dispatch and annotations.
- Consequence: the pure-Kotlin engine is compiled and tested here; everything Android is built and tested by GitHub Actions (F3 Option B), which is now working.

## Product Owner inputs (8 Oct 2026)
- GitHub repository: `abhinavgarggarg/personal-fitness-coach` — Claude GitHub App installed; code pushed (branch `main`); CI and device matrix green.
- Phone: Motorola Edge 50, Android 16. Request: the app must work on any phone (→ D-044) and use the phone's Gemini instead of paid AI calls (→ D-045).

## Open items for the Product Owner
1. **Reply CONTINUE** for Phase 3 Part 4 (data layer). Part 3 report: `docs/phase3/part3_report.md`.
2. For awareness (D-071): where a condition's limits pull against targets, safety wins (type 2 diabetes with only back-to-back days → one strength day and a prompt to add a day; heart condition → express session about 34 minutes). Breastbone surgery is encoded strictly: no upper-body loading until the surgical team clears it.
3. D-036 adds one Android-12-only permission (SCHEDULE_EXACT_ALARM) — needed for any-phone support; no prompt is shown.
4. Part 0 profile still blank — collected in onboarding.
5. Before any public release: clinician and regulatory review of the condition table and wording (D-058).

## Next actions
1. On "CONTINUE": Phase 3 Part 4 — Room data layer seeded from `library/v1` and the condition table; repositories feeding the engine (history → e1RM, progression prescriptions per FS-5/D-055, found calibration loads, weekly totals, weight/waist and strength trends, step counter and its permission per D-062, own numbers per D-059, condition answers and doctor's-OK scopes); DATA-001; backup/restore. Verify the "located" sources listed in the registry's `verification_pending` before Phase 4 testing.
2. Every part ends with a push and green CI; run the Devices workflow for anything touching screens, alarms or permissions. Logs and artifacts are not downloadable from this environment — results are read through CI annotations (`tools/ci_summary.py`).
3. Keep the independent-review step for safety-relevant parts (Part 1: 14 defects; Part 2: 20 findings; Part 3: 15 + 5 on re-check — all fixed with regression tests).
