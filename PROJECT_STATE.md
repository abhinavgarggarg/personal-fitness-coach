# PROJECT STATE — Personal Fitness Coach

Last updated: 2026-10-08 · Master prompt: v3.0 (7 Oct 2026)

## Current phase
**Phase 3 — Application Development: IN PROGRESS. Part 1 (engine core, safety kernel, project foundation) complete; waiting for the Product Owner to reply CONTINUE.**

| Phase | Status | Date |
|---|---|---|
| 1 Fitness research & evidence engine | APPROVED | 2026-10-07 |
| 2 Product requirements, UX & architecture | APPROVED (D-028) | 2026-10-07 |
| 3 Application development | IN PROGRESS — Part 1 complete | 2026-10-08 |
| 4 Testing, validation & UAT | NOT STARTED | — |
| 5 Production build & APK delivery | NOT STARTED | — |

## Phase 3 progress
| Part | Content | Status |
|---|---|---|
| 1 | Project foundation (Gradle, version catalog, wrapper, CI workflow, app shell); `:engine` calculators (readiness, e1RM, plate math, volume, SSU, workload, fatigue/deload); progression, autoregulation, calibration, return-to-training, warm-up; time budget, substitution; safety kernel (screening, red flags, illness gate, pain gate, caps, user additions, session validator, week checks) | Engine: **VERIFIED** (compiled and 173 tests passing locally, 2026-10-08). Android app shell + CI: **IMPLEMENTED — PENDING ENVIRONMENT VERIFICATION** (needs GitHub) |
| 2 | Exercise library (original text), week templates and session generation pipeline (GEN-001), remaining engine rules (reps, rest, aerobic, periodisation, adherence) | NOT STARTED |
| 3+ | Room data layer, screens, rest-timer alarms, Tier 1 coach, backup/export, research-update workflow, Phase 3 gate | NOT STARTED |

Rule coverage after Part 1: 73 of 140 rules have all their registry test cases (`python3 tools/rule_coverage.py`). The Phase 3 gate requires 140/140 (`--require-all`).

## Where things live
In the claude.ai "Fitness App" Project, every repo file is stored under the `claude/` prefix (e.g. `claude/rules/rule_registry_v1.0.json`).

| What | Where |
|---|---|
| Phase 1 report (commentable) | https://claude.ai/code/artifact/277679ce-731c-4ce0-8870-9cb5347a5d51 · copy `docs/phase1/phase1_report.md` |
| Phase 2 report (commentable) | https://claude.ai/code/artifact/ddd84b34-8a3a-4466-b655-2c98bc041319 · copy `docs/phase2/phase2_report.md` |
| Phase 3 Part 1 report | `docs/phase3/part1_report.md` |
| Rule Registry (approved, registry_version 1.0.1) | `rules/rule_registry_v1.0.json` |
| Registry → Kotlin generator | `tools/gen_registry_kotlin.py` (writes `engine/.../registry/GeneratedRegistry.kt`; `--check` fails if stale) |
| Engine source and tests | `engine/src/main/kotlin/com/personalfitnesscoach/engine/` (core, model, calc, progression, planning, safety, registry) · `engine/src/test/...` |
| App shell | `app/` (Compose, offline, permissions allow-list in the manifest) |
| Build | `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradlew` (Gradle 9.7.0) |
| CI | `.github/workflows/ci.yml` — registry check, Phase 1 checks, engine tests, rule coverage, app tests, debug APK, permission allow-list |
| Local engine build | `tools/local_build.sh` (Gradle's bundled Kotlin 2.0.21 + JUnit 4; no network) |
| Checks | `tools/check_phase1.py` (45 checks, 0 failures), `tools/rule_coverage.py`, `tools/check_permissions.py` |
| Decisions | `DECISIONS.md` (D-001 … D-043) |

## Environment (re-audited 2026-10-08)
- Available: shell, Python 3, git, JDK 21, Gradle 8.14.3 with bundled Kotlin 2.0.21 compiler and JUnit 4.13.2, web search.
- Blocked: Google Maven, Maven Central, Gradle Plugin Portal, services.gradle.org (proxy 403); no Android SDK, emulator or device; no GitHub account linked to this session; some developer.android.com pages need fetch permission.
- Consequence: the pure-Kotlin engine is compiled and tested here; everything Android is built and tested by GitHub Actions (F3 Option B).

## Open items for the Product Owner
1. **GitHub (blocks verification of the Android build):** connect GitHub in claude.ai settings and create an empty private repository; share its owner/name.
2. **Phone model and Android version** (confirms minSdk 29 and whether D-036 matters).
3. D-036 adds one Android-12-only permission (SCHEDULE_EXACT_ALARM) to the approved list — no prompt is shown to you; noted for awareness.
4. Part 0 profile still blank — collected in onboarding.

## Next actions
1. On "CONTINUE": Phase 3 Part 2 — exercise library seed (original wording), week templates (SCH-001/002), session generation pipeline (GEN-001) wired through the validator, remaining engine rules with tests.
2. When GitHub is connected: push the repo, run CI, fix anything the first real Gradle/AGP build finds, then pin GitHub Actions to exact versions (D-035).
3. Keep the independent-review step for safety-relevant parts (Part 1 review: 14 defects found and fixed with regression tests).
