# Change requests

Changes to the approved scope (Phases 1–2) follow change control (master prompt F7): request → impact analysis → implement → test → regression → version → release. Fitness-rule changes name the affected rules, workouts, users and data, the scientific rationale and the regression tests.

| ID | Date | Request (Product Owner) | Decision | Needs | Status |
|---|---|---|---|---|---|
| CR-001 | 2026-10-09 | Let users choose their health conditions; make it dynamic for a possible market release | D-058: condition picker driven by a versioned data table; SAF-001 screen kept; clinician and regulatory review before public release | Research Update 1.1 (condition entries), registry 1.1, data model, onboarding/settings | Research Update 1.1 ready for sign-off |
| CR-002 | 2026-10-09 | Let users enter their starting weights and personal records; the app suggests the next loads | D-059: recent set or best lift → e1RM (INT-004) → 90% start (CAL-001), age of entry scaled by REG rules | Engine function + tests; onboarding and exercise screens | Approved; build in Parts 3–4 |
| CR-003 | 2026-10-09 | Make weight loss for people over 30 the main selling point; the mix of strength, cardio and HIIT should change with age; no food in the app | D-060: default goal "Lose fat, keep muscle (30+)", other goals kept; age-banded training mix; weekly weight trend and waist; no nutrition features | Research Update 1.1, registry 1.1 (new goal, age bands, activity targets), blueprint and planner changes, data model | Research Update 1.1 ready for sign-off |
| CR-004 | 2026-10-09 | Show a picture of each exercise and its best form | D-061: original animated illustrations (sample: Form Guide Preview) | Pose data for 152 exercises, renderer in the app, per-exercise review | Approved; build with the screens |
| CR-005 | 2026-10-09 | Count daily steps (chosen: phone step counter) | D-062: step-counter sensor, ACTIVITY_RECOGNITION asked only when step tracking is turned on | Step goal rule (Research Update 1.1), permission allow-list, sensor service | Research Update 1.1 ready for sign-off |
| — | 2026-10-09 | "What if my gym has no box?" | D-063: already handled by the equipment profile; fixed the box jump → tempo squat swap (commit 5cd739d); low-impact power options and "any of" equipment to add with CR-003 | Library additions | Partly done |

Research Update 1.1: `docs/research_update_1_1/report.md` (proposed registry 1.1.0 delta and health-condition table draft in `rules/proposed/`). Phase 3 continues after sign-off with a new Part 3 (engine changes for 1.1); the data layer moves to Part 4, so Phase 3 becomes 7 parts.
