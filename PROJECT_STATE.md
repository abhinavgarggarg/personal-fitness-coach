# PROJECT STATE — Personal Fitness Coach

Last updated: 2026-10-07 · Master prompt: v3.0 (7 Oct 2026)

## Current phase
**Phase 2 — Product Requirements, UX & Technical Architecture: COMPLETE (draft), AWAITING PRODUCT OWNER SIGN-OFF.**
Do not start Phase 3 (no app code) until the Product Owner replies "APPROVED".

| Phase | Status | Date |
|---|---|---|
| 1 Fitness research & evidence engine | APPROVED | 2026-10-07 |
| 2 Product requirements, UX & architecture | Draft complete; gate presented | 2026-10-07 |
| 3 Application development | NOT STARTED | — |
| 4 Testing, validation & UAT | NOT STARTED | — |
| 5 Production build & APK delivery | NOT STARTED | — |

## Where things live
In the claude.ai "Fitness App" Project, every repo file is stored under the `claude/` prefix (e.g. `claude/rules/rule_registry_v1.0.json`).

| What | Where |
|---|---|
| Phase 1 report (commentable) | https://claude.ai/code/artifact/277679ce-731c-4ce0-8870-9cb5347a5d51 · copy `docs/phase1/phase1_report.md` |
| Phase 2 report (commentable) | https://claude.ai/code/artifact/ddd84b34-8a3a-4466-b655-2c98bc041319 · copy `docs/phase2/phase2_report.md` |
| Rule Registry (approved, registry_version 1.0.1) | `rules/rule_registry_v1.0.json` (v0.1 draft kept for history) |
| Registry generator (single source of truth) | `tools/build_registry.py` |
| Checks | `tools/check_phase1.py` — 45 checks, 0 failures on 2026-10-07 |
| Decisions | `DECISIONS.md` (D-001 … D-027) |

## Phase 2 key decisions (summary)
- Stack: Kotlin + Jetpack Compose + Material 3 + Room 3; modules `:engine` (pure Kotlin), `:coach` (pure Kotlin), `:data`, `:app`, `:simulator`.
- V1 offline by construction: **no INTERNET permission**; Tier 1 deterministic coach; Tier 2 LLM interface off.
- Rest alerts: exact alarm (setAlarmClock + USE_EXACT_ALARM) + countdown notification; permissions = POST_NOTIFICATIONS, USE_EXACT_ALARM, VIBRATE only.
- Data: 26 Room tables, schema v1, explicit tested migrations only; registry + exercise library bundled read-only.
- Backup: Android Auto Backup + `.pfcbackup` export (optional password, default off) + restore with automatic pre-restore export.
- minSdk 29 ASSUMPTION; versions pinned at Phase 3 start (ref 7 Oct 2026: Kotlin 2.4, AGP 9.x, Compose 1.12.1, Material3 1.4.0, Room 3.0.3, Android 17/API 37).

## Environment (A8, 2026-10-07) — re-audit before Phase 3
- Available: web search/fetch, shell, Python 3.13, git, JDK 21, Gradle 8.14.3.
- Not available: Android SDK, Flutter, emulator (/dev/kvm absent), device, GitHub auth.
- Network policy blocks Google Maven, Maven Central, Gradle Plugin Portal, dl.google.com → all Android builds/tests run in GitHub Actions (F3 Option B).
- PubMed/PMC blocked by bot check; some developer.android.com pages need fetch permission.

## Open items for the Product Owner (none block Phase 2 sign-off)
1. Phone model and Android version (confirms minSdk and on-device AI eligibility).
2. GitHub account (existing one is fine) — required before Phase 3; send step-by-step instructions on approval.
3. Part 0 profile still blank — collected in onboarding.

## Next actions
1. Wait for Phase 2 sign-off or feedback; on feedback revise the Phase 2 doc, log changes, re-present the gate.
2. On approval: log D-028 approval; re-run A8 audit; walk the PO through GitHub setup (repo, Actions, secrets for signing key); pin versions in a version catalog.
3. Phase 3, Module 1 (project foundation + CI producing a debug APK) first; then modules 2–18 per D2, each building green in CI.
