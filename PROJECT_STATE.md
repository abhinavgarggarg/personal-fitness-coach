# PROJECT STATE — Personal Fitness Coach

Last updated: 2026-10-12 (Phase 3 Part 7 started: look directions ready for the PO) · Master prompt: v3.0 (7 Oct 2026)

## Current phase
**Phase 3 — Application Development: IN PROGRESS. Parts 1–6 of 8 complete (engine, library and planner, Research Update 1.1, data layer, screens, history/progress/coach). Rule coverage 152/152. Part 7 (look and feel, CR-007) started 12 Oct 2026: three visual directions are ready for the PO to choose from.**

| Phase | Status | Date |
|---|---|---|
| 1 Fitness research & evidence engine | APPROVED | 2026-10-07 |
| 2 Product requirements, UX & architecture | APPROVED (D-028) | 2026-10-07 |
| 3 Application development | IN PROGRESS — Parts 1–6 of 8 complete | 2026-10-11 |
| 4 Testing, validation & UAT | NOT STARTED | — |
| 5 Production build & APK delivery | NOT STARTED | — |

## Phase 3 progress
| Part | Content | Status |
|---|---|---|
| 1 | Project foundation (Gradle, version catalog, wrapper, CI workflow, app shell); `:engine` calculators (readiness, e1RM, plate math, volume, SSU, workload, fatigue/deload); progression, autoregulation, calibration, return-to-training, warm-up; time budget, substitution; safety kernel (screening, red flags, illness gate, pain gate, caps, user additions, session validator, week checks); any-phone support (D-044) | **VERIFIED on GitHub Actions 2026-10-08**: CI run 37733788298 — engine 173/173 tests, app unit tests, debug APK built, permission allow-list PASS (no INTERNET), 16 KB check; Devices run 37733813409 — launch tests pass on Android 10 small phone, 12, 14 at 200% text, 16 phone, 16 tablet |
| 2 | Exercise library v1.0.1 (152 exercises, 31 drills, 10 modality guides, original text, generator with safety checks); reps, rest, effort, order; cardio and HIIT; bodyweight/core ladders; mobility; 12-month blueprint and block clock; week planner; session generator GEN-001; adherence; independent review (20 findings fixed, R01–R20) | **VERIFIED**: 345/345 engine tests locally and on GitHub Actions (CI run 37816759062, Kotlin 2.4.0; APK built; permissions PASS); property tests 10 seeds × 500 cases; 52-week simulation for 5 users with no rule broken. Report `docs/phase3/part2_report.md` |
| 3 | Research Update 1.1 in the engine: Registry 1.1.0 → 1.1.1 (12 new rules, 10 changed, per-person cardio machines), health-condition table 1.0.1 (21 entries, "strictest wins", applied by planner, generator and validator), library 1.1.1 (14 condition tags, "any of" equipment, low-impact power, balance drills, circuit moves, 5 machines); fat-loss goal and age-banded mix (FL-001–005), steps (STEP-001/002), own numbers (CAL-002), AGE-001/MOB-004/ADH-002/004/005, MOB-006 calm session, EQ-003 away-from-gym day; independent review (15 findings R3-01–R3-15 + 5 from the re-check R3-16–R3-20, all fixed) | **VERIFIED**: 406/406 engine tests locally; GitHub Actions CI green on every Part 3 commit (last: run 37946441731 for 7afcb8a — engine tests, app unit tests, debug APK, permissions PASS); property tests 10 seeds × 500 cases + condition-session checks 5 seeds × 300; 52-week simulation for 5 fat-loss users (ages 35–70, each with a condition) with no rule broken and strength gains for all. Report `docs/phase3/part3_report.md` |
| 4 | Data layer (`:data`): Room 3 database in private storage, schema v1 exported (D-073/074); library and condition table compiled, referenced by ID and version (D-077); session log; history → e1RM, prescriptions, calibration handover, weekly totals, workload, block clock; REG-002–005 from stored history (D-076), SAF-002 at any time (D-081), deloads and DEL-004, reduced loads recorded (D-082), T2D spacing in the validator; weight/waist; own numbers; step counter with ACTIVITY_RECOGNITION (D-078); backup/restore with optional password (D-075); encrypted-only Android backup (D-079); DATA-001 (D-080); independent review (32 findings R4-01–R4-32 + 13 on re-check RC-01–RC-13, all fixed) | **VERIFIED**: 498 tests locally (engine 408, data core 90); CI run 38013819308 for 2cc4c2f green — engine 408, data 107 (incl. Room and Robolectric), app 1, permissions PASS (6, no INTERNET), backup rules PASS, debug APK; Devices run 38013824144 — data layer on device (plan, log, encrypted backup and restore, erase leaves no trace, sensor silent without permission) — 6/6 tests pass on all 5 devices (Android 10 small phone, 12, 14 at 200% text, 16 phone, 16 tablet). Report `docs/phase3/part4_report.md` |
| 5 | Screens (`:app`): plain-Kotlin controller and screen models tested on the JVM (D-083); 11-step onboarding; Today with check-in (pain gate → no session) and preview (levels capped by the day's gate); session player in `:data` (stages, sets, CAL-001 ramp, INT-007, rest, intervals, swaps, something hurts, warning signs, change time with floors and the shortest-safe notice, added sets, summary); rest alarm on the elapsed clock (D-084); session minutes without idle gaps (D-085); Settings: steps, backup/restore with the password held across the file picker, erase, conditions editor with FL-001 re-applied (D-086), re-screen, doctor's OK; words in resources, checked (D-088); screen pictures (D-089); independent review (26 findings R5-01–R5-26 + 8 on re-check N1–N8, all fixed) | **VERIFIED**: 564 tests locally (engine 408, data core 132, app flow 24); CI run 38079715387 for 3bc069f green — engine 408, data 149, app 32 (Robolectric screens incl. 200% text and touch targets), permissions PASS (6, no INTERNET), debug APK; Devices run 38079955034 — 8/8 device tests on all 5 devices (workout from Today on Room, rest alarm, encrypted backup and restore, erase, sensor without permission, no INTERNET); 28 screen pictures in `docs/phase3/screens/`. Report `docs/phase3/part5_report.md` |
| 6 | Tabs; History with set corrections (D-096); Progress: strength trends, weekly minutes vs 150, weeks in a row and this year, milestones, weekly weight averages, waist, steps; exercise page with favourite / don't suggest (D-097); Tier 1 coach offline with SAF-009 referrals and "Why?" with plain evidence words (D-092, D-093); Ask Gemini share with the exact text first and health questions not shared (D-094); one guard for every way into a session, illness kept for the day, a running workout first (D-095); settings editors for goals, days, gym and machines, injuries, skipped exercises, theme (D-098); independent review (16 findings R6-01–R6-16 + 4 on re-check N1–N4 + final check, all fixed) | **VERIFIED**: 610 tests locally (engine 408, data core 159, app flow 43); CI run 38151728188 for 8aa77c3 green — engine 408, data 176, app 54, rule coverage 152/152, permissions PASS (6, no INTERNET), debug APK; Devices run 38152039848 — 9/9 device tests on all 5 devices (new: tabs, coach, Why?, Ask Gemini text); 37 screen pictures. Report `docs/phase3/part6_report.md` |
| 7 | Look and feel (CR-007, D-091): visual directions for the PO to choose, design system applied through the shared theme and components; exercise animations (CR-004, D-061) in the chosen style | IN PROGRESS — 3 directions mocked up on 4 key screens each (A Floodlight, B Morning Calm, C Instrument): https://claude.ai/artifact/HEdjiUbaBMjmef6EcvUpKa (Fitness Coach Look Directions); waiting for the PO's choice |
| 8 | Integration, research-update workflow, Phase 3 gate (152/152 rules) | NOT STARTED |

Rule coverage after Part 6: **152 of 152 rules** have all their registry test cases (304/304 test IDs; `docs/phase3/rule_coverage_part6.md`). `python3 tools/rule_coverage.py`; the Phase 3 gate requires 152/152 (`--require-all`).

## Where things live
In the claude.ai "Fitness App" Project, every repo file is stored under the `claude/` prefix (e.g. `claude/rules/rule_registry_v1.0.json`).

| What | Where |
|---|---|
| Phase 1 report (commentable) | https://claude.ai/code/artifact/277679ce-731c-4ce0-8870-9cb5347a5d51 · copy `docs/phase1/phase1_report.md` |
| Phase 2 report (commentable) | https://claude.ai/code/artifact/ddd84b34-8a3a-4466-b655-2c98bc041319 · copy `docs/phase2/phase2_report.md` |
| Phase 3 Part 1 report | `docs/phase3/part1_report.md` |
| Phase 3 Part 2 report | `docs/phase3/part2_report.md` · rule coverage `docs/phase3/rule_coverage_part2.md` |
| Phase 3 Part 3 report | `docs/phase3/part3_report.md` · rule coverage `docs/phase3/rule_coverage_part3.md` |
| Phase 3 Part 4 report | `docs/phase3/part4_report.md` · rule coverage `docs/phase3/rule_coverage_part4.md` |
| Phase 3 Part 5 report | `docs/phase3/part5_report.md` · rule coverage `docs/phase3/rule_coverage_part5.md` · screen pictures `docs/phase3/screens/` · all screens in one page: https://claude.ai/artifact/ENfS9tFet1rQ21x4xHRXsY (Personal Fitness Coach Screens) |
| Phase 3 Part 6 report | `docs/phase3/part6_report.md` · rule coverage `docs/phase3/rule_coverage_part6.md` · screen pictures 29–37 in `docs/phase3/screens/` (also on the screens page) |
| Exercise library (source) | `library/v1/*.json` (v1.1.1) → `tools/gen_library_kotlin.py` (writes `engine/.../library/GeneratedLibrary*.kt`; `--check` fails if stale) |
| Rule Registry (approved, registry_version 1.1.1, 152 rules) | `rules/rule_registry_v1.0.json`, built by `tools/build_registry.py` from the Phase 1 rules plus `rules/changes/registry_1.1.0.json` and `registry_1.1.1.json`; Phase 1 snapshot `rules/archive/rule_registry_1.0.1.json`; `tools/check_registry.py` |
| Health-condition table (SAF-010, v1.0.1) | `rules/health_conditions_v1.0.json`, built by `tools/build_health_conditions.py`; `tools/gen_conditions_kotlin.py` writes `engine/.../safety/GeneratedConditions.kt` (`--check` fails if stale) |
| Registry → Kotlin generator | `tools/gen_registry_kotlin.py` (writes `engine/.../registry/GeneratedRegistry.kt`; `--check` fails if stale) |
| Engine source and tests | `engine/src/main/kotlin/com/personalfitnesscoach/engine/` (core, model, calc, progression, planning, safety, registry, library, dose, conditioning, program, generation) · `engine/src/test/...` (incl. `simulation/` property tests and 52-week simulation, `review/` regression tests) |
| Data layer | `data/src/main/kotlin/com/personalfitnesscoach/data/` — `core/` plain Kotlin (store contract, codecs, session log, session player, onboarding service, engine bridge, steps, backup, DATA-001 taxonomy; compiled and tested locally), `room/` (Room 3 database, DAO, row store), `android/` (step sensor, app data) · schema `data/schemas/` · tests `data/src/test/...` (incl. `review/` regression tests) |
| App | `app/` — `flow/` plain-Kotlin controller and screen models (compiled and tested locally), `ui/` Compose screens (onboarding, today, workout, history, progress, coach, settings, text, theme), `platform/` (rest alarms, step sensor bridge); every word in `res/values/strings*.xml`; offline, permissions allow-list, Android backup end-to-end encrypted only · tests `app/src/test/` (flow, Robolectric screens, screenshots) and `app/src/androidTest/` (devices) |
| Build | `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradlew` (Gradle 9.7.0) |
| CI | `.github/workflows/ci.yml` — registry check, Phase 1 checks, strings and reason-text checks, engine tests, rule coverage, data tests (Room, Robolectric), app tests (Robolectric screens), debug APK, permission allow-list, backup rules, 16 KB page check · `.github/workflows/devices.yml` — weekly emulator matrix (Android 10–16, small phone, tablet, 200% text) · `screenshots.yml` (renders every screen, commits `docs/phase3/screens/`) · `room-schema.yml` (schema export), `versions.yml` (dependency versions) |
| Local build | `tools/local_build.sh` — engine, data core and app flow (Gradle's bundled Kotlin 2.0.21 + JUnit 4; no network; no friend paths, so tests see only public API) |
| Checks | `tools/check_phase1.py` (45 checks, 0 failures), `tools/check_registry.py`, `tools/rule_coverage.py`, `tools/check_permissions.py`, `tools/check_backup_rules.py`, `tools/check_strings.py`, `tools/gen_reason_texts.py --check` |
| Decisions | `DECISIONS.md` (D-001 … D-098) |
| Change requests | `docs/change_requests/README.md` (CR-001 … CR-007) |
| Research Update 1.1 (approved 2026-10-09) | `docs/research_update_1_1/report.md` · evidence reviews A–D in the same folder · change set `rules/changes/registry_1.1.0.json` (builder `tools/build_change_1_1_0.py`) |
| Illustration sample (CR-004) | https://claude.ai/artifact/DYdTjK75unCNA4Rk3GoiBt (Form Guide Preview) |
| Code (source of truth) | https://github.com/abhinavgarggarg/personal-fitness-coach (branch `main`); Actions: CI on every push, Devices weekly / on demand |

## Environment (re-audited 2026-10-09)
- The session container can be reclaimed between conversations: re-clone from GitHub (the source of truth) when the folder is missing.
- Available: shell, Python 3, git, JDK 21, Gradle 8.14.3 with bundled Kotlin 2.0.21 compiler and JUnit 4.13.2, web search (allowed by default for this project), `gh` (run it without the session's GH_TOKEN variable).
- Blocked here: Google Maven, Maven Central, Gradle Plugin Portal, services.gradle.org (proxy 403); no Android SDK, emulator or device; GitHub Actions log/artifact downloads (blob storage 403); some developer.android.com pages need fetch permission.
- Available: push to and read the GitHub repository; `gh` for runs, workflow dispatch and annotations.
- Consequence: the pure-Kotlin engine and the data core are compiled and tested here; everything Android (Room, Robolectric, the app, device tests) is built and tested by GitHub Actions (F3 Option B).

## Product Owner inputs (8 Oct 2026)
- GitHub repository: `abhinavgarggarg/personal-fitness-coach` — Claude GitHub App installed; code pushed (branch `main`); CI and device matrix green.
- Phone: Motorola Edge 50, Android 16. Request: the app must work on any phone (→ D-044) and use the phone's Gemini instead of paid AI calls (→ D-045).

## Open items for the Product Owner
1. **Choose a look** for Part 7: A Floodlight, B Morning Calm or C Instrument (or a mix), on the "Fitness Coach Look Directions" canvas. Nothing is rebuilt until you choose. Part 6 report: `docs/phase3/part6_report.md`.
2. For awareness (D-093): the coach refers rather than guesses — an ordinary question may occasionally get a referral; it is English-only in V1. Ask Gemini never shares health, pain, illness or body details (D-094).
3. For awareness (D-086, D-087): if a new condition rules out "Lose fat, keep muscle", the goal changes and weight tracking switches off (the app says so); the type 2 diabetes medicine question is not asked (its low-sugar advice is shown to everyone with the condition).
4. For awareness (D-075): a forgotten backup password cannot be recovered. Steps update when the app is opened (D-078). After 8+ weeks off each exercise recalibrates once and intervals return after 3 weeks (D-076, REQUIRES VALIDATION).
5. For awareness (D-071): where a condition's limits pull against targets, safety wins (type 2 diabetes with only back-to-back days → one strength day and a prompt to add a day; heart condition → express session about 34 minutes). Breastbone surgery is encoded strictly: no upper-body loading until the surgical team clears it.
6. D-036 adds one Android-12-only permission (SCHEDULE_EXACT_ALARM) — needed for any-phone support; no prompt is shown.
7. Part 0 profile still blank — you fill it in through the app's onboarding on your phone in Phase 4.
8. Before any public release: clinician and regulatory review of the condition table and wording (D-058).

## Next actions
1. On "CONTINUE": Phase 3 Part 7 — look and feel (CR-007, D-091): 2–3 visual directions as mockups of the key screens (Today, workout, Progress, Coach) for the PO to choose; then one design system (colour, type, shapes, icons, illustrations, motion, haptics) applied through the shared theme and components, keeping A9 (dark by default, high contrast, large numerals, one hand, touch targets ≥ 48 dp, 200% text); exercise animations (CR-004, D-061) in the chosen style.
2. Part 8: integration, research-update workflow, Phase 3 gate (152/152). Verify the "located" sources listed in the registry's `verification_pending` before Phase 4 testing.
