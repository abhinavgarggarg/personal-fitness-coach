# Personal Fitness Coach

A personal, offline-first Android app that plans and coaches your gym training like a knowledgeable personal trainer — built step by step through five approved phases.

**Where we are:** Phases 1 (fitness science) and 2 (product and architecture design) are approved. Phase 3 (building the app) is under way: the training engine and its safety layer are written and tested; the Android app itself is a starter shell until the screens are built in later parts.

**What's in this folder**
- `PROJECT_STATE.md` — where the project stands and what happens next. Start here in any new session.
- `DECISIONS.md` — every important decision, the alternatives, and why.
- `docs/` — the Phase 1 research report, the Phase 2 design, and Phase 3 progress reports.
- `rules/rule_registry_v1.0.json` — the 140 approved training rules the app follows, each with its evidence and confidence.
- `engine/` — the training brain: pure Kotlin, no Android parts, every rule tested.
- `app/` — the Android app (Jetpack Compose). It has no internet permission.
- `tools/` — scripts that generate and check the rules, run the engine tests locally and check permissions.
- `.github/workflows/ci.yml` — the automatic build that produces the installable APK on GitHub.

**Building**
- Engine only, no network needed: `tools/local_build.sh`
- Full app: `./gradlew :engine:test :app:assembleDebug` (runs on GitHub Actions; needs the Android SDK)

**The five phases:** 1 Research → 2 Product design → 3 Build → 4 Test → 5 Deliver the APK. Each ends with your sign-off.
