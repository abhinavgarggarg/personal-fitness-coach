# Personal Fitness Coach

A personal, offline-first Android app that plans and coaches your gym training like a knowledgeable personal trainer — built step by step through five approved phases.

**Where we are:** Phases 1 (fitness science) and 2 (product and architecture design) are approved. Phase 3 (building the app) is under way: the training engine, its safety layer, the exercise library, the 12-month programme, the week planner and the daily session generator are written and tested, including Research Update 1.1 (the "Lose fat, keep muscle" default for adults 30+, health-condition profiles, steps and starting from your own numbers). The Android app itself is a starter shell until the data layer and screens are built in later parts.

**What's in this folder**
- `PROJECT_STATE.md` — where the project stands and what happens next. Start here in any new session.
- `DECISIONS.md` — every important decision, the alternatives, and why.
- `docs/` — the Phase 1 research report, the Phase 2 design, Phase 3 progress reports, Research Update 1.1 and the change-request log.
- `rules/rule_registry_v1.0.json` — the 152 approved training rules the app follows (registry 1.1.1), each with its evidence and confidence; `rules/changes/` holds each approved change set.
- `rules/health_conditions_v1.0.json` — the health-condition table (21 conditions): what each one limits, when a doctor's OK is suggested, and the prompts and stop signs shown.
- `library/v1/` — the exercise library (original wording): exercises, warm-up and mobility drills, cardio machine guides. `tools/gen_library_kotlin.py` checks it and turns it into engine code.
- `engine/` — the training brain: pure Kotlin, no Android parts, every rule tested.
- `app/` — the Android app (Jetpack Compose). It has no internet permission.
- `tools/` — scripts that build, generate and check the rules, the condition table and the library, run the engine tests locally, report rule coverage and check permissions.
- `.github/workflows/ci.yml` — the automatic build that produces the installable APK on GitHub.

**Building**
- Engine only, no network needed: `tools/local_build.sh`
- Full app: `./gradlew :engine:test :app:assembleDebug` (runs on GitHub Actions; needs the Android SDK)

**The five phases:** 1 Research → 2 Product design → 3 Build → 4 Test → 5 Deliver the APK. Each ends with your sign-off.
