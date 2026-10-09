# PROJECT STATE — Personal Fitness Coach

Last updated: 2026-10-09 (change requests CR-001–CR-005) · Master prompt: v3.0 (7 Oct 2026)

## Current phase
**Phase 3 — Application Development: IN PROGRESS. Parts 1 and 2 of 6 complete. Part 3 is paused for Research Update 1.1 (change requests CR-001, CR-003, CR-005: health-condition picker, "Lose fat, keep muscle (30+)" default goal with an age-banded training mix, phone step counter), which goes to the Product Owner for sign-off before the data layer is built.**

| Phase | Status | Date |
|---|---|---|
| 1 Fitness research & evidence engine | APPROVED | 2026-10-07 |
| 2 Product requirements, UX & architecture | APPROVED (D-028) | 2026-10-07 |
| 3 Application development | IN PROGRESS — Parts 1–2 complete; Research Update 1.1 awaiting sign-off; Phase 3 becomes 7 parts | 2026-10-09 |
| 4 Testing, validation & UAT | NOT STARTED | — |
| 5 Production build & APK delivery | NOT STARTED | — |

## Phase 3 progress
| Part | Content | Status |
|---|---|---|
| 1 | Project foundation (Gradle, version catalog, wrapper, CI workflow, app shell); `:engine` calculators (readiness, e1RM, plate math, volume, SSU, workload, fatigue/deload); progression, autoregulation, calibration, return-to-training, warm-up; time budget, substitution; safety kernel (screening, red flags, illness gate, pain gate, caps, user additions, session validator, week checks); any-phone support (D-044) | **VERIFIED on GitHub Actions 2026-10-08**: CI run 37733788298 — engine 173/173 tests, app unit tests, debug APK built, permission allow-list PASS (no INTERNET), 16 KB check; Devices run 37733813409 — launch tests pass on Android 10 small phone, 12, 14 at 200% text, 16 phone, 16 tablet |
| 2 | Exercise library v1.0.1 (152 exercises, 31 drills, 10 modality guides, original text, generator with safety checks); reps, rest, effort, order; cardio and HIIT; bodyweight/core ladders; mobility; 12-month blueprint and block clock; week planner; session generator GEN-001; adherence; independent review (20 findings fixed, R01–R20) | **VERIFIED**: 345/345 engine tests locally and on GitHub Actions (CI run 37816759062, Kotlin 2.4.0; APK built; permissions PASS); property tests 10 seeds × 500 cases; 52-week simulation for 5 users with no rule broken. Report `docs/phase3/part2_report.md` |
| 3 | Room data layer seeded from the library JSON, repositories, DATA-001 data taxonomy, backup/restore (optional password) | NOT STARTED |
| 4 | Screens: onboarding (bodyweight and known weights encouraged), today, session player, rest-timer alarms | NOT STARTED |
| 5 | History and progress, Tier 1 coach (SAF-009, COACH-001), Ask Gemini share, settings | NOT STARTED |
| 6 | Integration, research-update workflow, Phase 3 gate (140/140 rules) | NOT STARTED |

Rule coverage after Part 2: 137 of 140 rules have all their registry test cases (Part 1: 73). Remaining: DATA-001 (Part 3), SAF-009 and COACH-001 (Part 5). `python3 tools/rule_coverage.py`; the Phase 3 gate requires 140/140 (`--require-all`).

## Where things live
In the claude.ai "Fitness App" Project, every repo file is stored under the `claude/` prefix (e.g. `claude/rules/rule_registry_v1.0.json`).

| What | Where |
|---|---|
| Phase 1 report (commentable) | https://claude.ai/code/artifact/277679ce-731c-4ce0-8870-9cb5347a5d51 · copy `docs/phase1/phase1_report.md` |
| Phase 2 report (commentable) | https://claude.ai/code/artifact/ddd84b34-8a3a-4466-b655-2c98bc041319 · copy `docs/phase2/phase2_report.md` |
| Phase 3 Part 1 report | `docs/phase3/part1_report.md` |
| Phase 3 Part 2 report | `docs/phase3/part2_report.md` · rule coverage `docs/phase3/rule_coverage_part2.md` |
| Exercise library (source) | `library/v1/*.json` (v1.0.1) → `tools/gen_library_kotlin.py` (writes `engine/.../library/GeneratedLibrary*.kt`; `--check` fails if stale) |
| Rule Registry (approved, registry_version 1.0.1) | `rules/rule_registry_v1.0.json` |
| Registry → Kotlin generator | `tools/gen_registry_kotlin.py` (writes `engine/.../registry/GeneratedRegistry.kt`; `--check` fails if stale) |
| Engine source and tests | `engine/src/main/kotlin/com/personalfitnesscoach/engine/` (core, model, calc, progression, planning, safety, registry, library, dose, conditioning, program, generation) · `engine/src/test/...` (incl. `simulation/` property tests and 52-week simulation, `review/` regression tests) |
| App shell | `app/` (Compose, offline, permissions allow-list in the manifest) |
| Build | `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradlew` (Gradle 9.7.0) |
| CI | `.github/workflows/ci.yml` — registry check, Phase 1 checks, engine tests, rule coverage, app tests, debug APK, permission allow-list, 16 KB page check · `.github/workflows/devices.yml` — weekly emulator matrix (Android 10–16, small phone, tablet, 200% text) |
| Local engine build | `tools/local_build.sh` (Gradle's bundled Kotlin 2.0.21 + JUnit 4; no network) |
| Checks | `tools/check_phase1.py` (45 checks, 0 failures), `tools/rule_coverage.py`, `tools/check_permissions.py` |
| Decisions | `DECISIONS.md` (D-001 … D-065) |
| Change requests | `docs/change_requests/README.md` (CR-001 … CR-006) |
| Research Update 1.1 | `docs/research_update_1_1/report.md` · evidence reviews A–D in the same folder · proposed rules `rules/proposed/registry_v1.1.0_proposed_delta.json` · condition table `rules/proposed/health_conditions_v1.0_draft.json` · builders `tools/proposed_build_*.py` |
| Illustration sample (CR-004) | https://claude.ai/artifact/DYdTjK75unCNA4Rk3GoiBt (Form Guide Preview) |
| Code (source of truth) | https://github.com/abhinavgarggarg/personal-fitness-coach (branch `main`); Actions: CI on every push, Devices weekly / on demand |

## Environment (re-audited 2026-10-08)
- Available: shell, Python 3, git, JDK 21, Gradle 8.14.3 with bundled Kotlin 2.0.21 compiler and JUnit 4.13.2, web search.
- Blocked here: Google Maven, Maven Central, Gradle Plugin Portal, services.gradle.org (proxy 403); no Android SDK, emulator or device; GitHub Actions log/artifact downloads (blob storage 403); some developer.android.com pages need fetch permission.
- Available: push to and read the GitHub repository; `gh` for runs, workflow dispatch and annotations.
- Consequence: the pure-Kotlin engine is compiled and tested here; everything Android is built and tested by GitHub Actions (F3 Option B), which is now working.

## Product Owner inputs (8 Oct 2026)
- GitHub repository: `abhinavgarggarg/personal-fitness-coach` — Claude GitHub App installed; code pushed (branch `main`); CI and device matrix green.
- Phone: Motorola Edge 50, Android 16. Request: the app must work on any phone (→ D-044) and use the phone's Gemini instead of paid AI calls (→ D-045).

## Open items for the Product Owner
1. **Research Update 1.1 sign-off:** `docs/research_update_1_1/report.md` — 12 new rules, 8 changed, health-condition table v1.0 draft (21 entries), creator review outcomes (D-064), and decision MOD-001 (treadmill walking and bikes as a per-person choice). Change log: `docs/change_requests/README.md`.
2. D-036 adds one Android-12-only permission (SCHEDULE_EXACT_ALARM) — needed for any-phone support; no prompt is shown.
3. Part 0 profile still blank — collected in onboarding.

## Next actions
1. Research Update 1.1 (evidence, proposed registry 1.1, impact analysis) → PO sign-off → engine changes with regression tests → Phase 3 Part 3: Room data layer seeded from `library/v1` and the condition table, repositories feeding the engine (history → e1RM, progression prescriptions per FS-5/D-055, weekly totals, bodyweight/waist trend, steps, starting weights and PRs per D-059), DATA-001, backup/restore.
2. Every part ends with a push and green CI; run the Devices workflow for anything touching screens, alarms or permissions. Logs and artifacts are not downloadable from this environment — results are read through CI annotations (`tools/ci_summary.py`).
3. Keep the independent-review step for safety-relevant parts (Part 1: 14 defects; Part 2: 20 findings — all fixed with regression tests).
