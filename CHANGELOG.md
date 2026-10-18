# CHANGELOG

## 2026-10-18 — Phase 4 Part 1: weekly limits that follow how often you train (proposal, waiting for approval)
- Twelve-month simulations through the data layer (`YearOfTrainingTest`): 8 personas × every session, interrupted (a missed week, illness at the check-in, a warning-sign stop), about 4 in 5 sessions, and sparse (one a week for ten weeks). Today's limits fell week after week whenever sessions were missed: 82–125 recovery days a year at 4 in 5 sessions.
- Proposal Registry 2.0.0 (MAJOR, D-112), on branch `phase4/weekly-limits`: reference weeks at the level planned and done (missed sessions, cut-short weeks, readiness-lightened days; last 8 weeks); a weight being found counts two sets as planned; the week planned within the limits (accessory sets, conditioning shortened or eased, intervals, then main lifts), with the reason shown on lighter days; easy conditioning on a low-impact modality before dropping it; a floor at half the plan under a 2× spike guard on the weeks as done. Result: 80–100% of the plan kept across all 32 simulated years, no recovery days from the limits, no week above 2× the three before.
- Independent safety review: 1 high, 4 medium, 4 low; re-check: 2 more; all fixed before the proposal went to the PO. Report `docs/phase4/part1_report.md`.

## 2026-10-13 — Phase 3 approved; Phase 4 started
- The Product Owner approved Phase 3 (D-110). The weekly limits (VOL-007, LOAD-005) are tuned in Phase 4, all three recommended steps together, as its first task.
- Phase 4 plan in 5 parts (D-111): weekly-limit tuning with 12-month simulations; the 30-scenario suite; interruption, data-integrity and upgrade tests; safety and evidence audit; UAT on the PO's phone and the Phase 4 gate.

## 2026-10-13 — Phase 3 Part 8: integration, research updates, release checks; Phase 3 gate
- Integration: three 12-week journeys through the app's own calls on the real data layer and engine (a lifter in a full gym; a 66-year-old beginner at home with type 2 diabetes and knee osteoarthritis; bodyweight only), with a killed app, a missed week, pain mid-set, a warning sign at the check-in, a 30-minute day and a backup restore. No step fails, history matches what was done, lifts progress. The clock moves as in a gym, and each journey reports how much of the planned work the weekly limits kept.
- Weekly limits spread over the week (D-107): each session gets its share of what is left of the week's VOL-007 / LOAD-005 budget; calibration counted as it is done; done sessions count at their measured load or as predicted, whichever is higher; trimming in TIME-001 order, intervals by whole repeats; a day with nothing left is a recovery day with the reason; sets that still fit after the cuts are given back.
- Sessions take the time they promise (F10, D-109): the fit's rests and supersets reach the workout; supersets alternate in the player and end correctly on later, skip, pain, swap or a time change; a cut that went too far is partly given back; the express session is offered at 30 minutes when nothing else fits, and turning it off is kept for the day.
- Research-update workflow (D-106): `tools/research_update.py` checks, reports and applies change sets; CI rebuilds the registry from the approved history and requires 152/152 rule coverage. Registry 1.1.2 (evidence-only PATCH, under delegation): six sources checked, citations completed, one wrong link corrected, nine still pending with reasons; no rule changed.
- Release and quality checks (D-108): the R8-shrunk release APK is built and checked on every push (4.21 MB, permissions, app ID, minimum Android version), libraries checked against an allow-list (106, no trackers), engine line coverage measured (96.8%, floor 90%); the Devices workflow installs the release APK signed with a throw-away key and goes from onboarding to today's session, measuring cold start (about 1 s on the emulator). Device tests wait for a still screen; the screens take the controller's values in on the main thread (an Android 10 device-test failure).
- Acceptance traceability (`docs/phase3/acceptance_traceability.md`): every Phase 2 criterion and NFR mapped to its test, or to UAT on the phone.
- Independent review: 1 high, 4 medium, 5 low (R8-01–R8-10), all fixed; re-check: 3 more small gaps, fixed.
- Open for the PO: the weekly limits keep weeks at about 50–80% of the plan, and less after an interrupted week; recommended as the first Phase 4 task.
- Tests: 656 locally (engine 418, data 169, app 69); on GitHub engine 418, data 186, app 85; 9/9 device tests on 5 emulated devices plus the release check. Report and Phase 3 gate: `docs/phase3/part8_report.md`.

## 2026-10-12 — CR-008: sculpted exercise figures
- Four free picture sources checked for the PO (free-exercise-db/wger, Everkinetic, Good-GYM, Smart-AI-Gym-Trainer), with a side-by-side comparison page. None was safe to ship or covered the library: the free-exercise-db photos have no shown rights, wger needs internet, Everkinetic matches only 41 of 157 exactly, and the other two are camera rep counters. Evaluation: `docs/change_requests/CR-008_exercise_pictures.md`.
- The PO chose option B (D-105): every one of the 157 figures is now a sculpted, shaded mannequin. It has shaped limbs and a trunk, a head and hands, light from the upper left with a warm rim, and a soft floor shadow, and the exercise's primary muscles glow orange. The poses, the tests and the form line are unchanged.
- The figure is drawn into a picture on the processor, at 3/4 resolution and up to 24 times a second, and redrawn only when it moves. Drawing it straight to the screen stopped the Android 14 and 16 test emulators.
- Device tests now report the phone's crash log and failures outside a test as annotations. The emulators use the supported `-gpu swiftshader` mode.
- Tests: 632 locally (nine new sculpt tests); 665 on GitHub (engine 408, data 176, app 81, including a picture test); 9/9 device tests on 5 emulated devices; new screen pictures of every screen and figure. Report `docs/phase3/cr008_report.md`.

## 2026-10-12 — Phase 3 Part 7: the new look ("Glow") and moving exercise figures
- Look D "Glow" chosen by the Product Owner from four directions (D-099): deep navy, frosted-glass cards, an orange glow on the one main button, teal for done, very large white numbers, Plus Jakarta Sans built in (SIL OFL), the app's own line icons, a floating icon-only tab bar with spoken names; dark by default, light in Settings. Today (greeting and date, week ring, tiles, workout studio card, reason rows, week tiles), the workout (studio card, big target numbers with set dots, glass effort buttons, rest ring, tinted safety buttons), Coach (question and answer bubbles), Progress (tiles, rounded bars, warm trend lines) and every other screen through the shared components. A short tick when a set is logged.
- Exercise figures (CR-004, D-100): one original moving figure for each of the 157 library exercises, computed from joint angles with fixed segment lengths, with its equipment and the main form point; a slow loop of one rep, still when the phone's animations are off. 14 figure tests (every exercise covered, nothing through the floor, limbs keep length, reaches land, key positions as defined, nothing jumps, deadlifts start on the floor, …).
- Contrast checked in CI (`tools/check_contrast.py`, D-104); the button orange deepened to keep white words at 3:1 or better. Large text: numbers stay whole and never shrink, the safety buttons and week wrap instead of cutting words (D-103). Decisions D-099 to D-104.
- Independent review: 3 high, 5 medium, 6 low (R7-01–R7-14), all fixed; re-check: 1 medium and 3 low more plus a remaining part of R7-01, fixed.
- Tests: 623 locally; 654 on GitHub (engine 408, data 176, app 70); 9/9 device tests on 5 emulated phones and tablets; screen pictures of every screen, the light theme, the rest ring and all 157 figures. Report `docs/phase3/part7_report.md`.

## 2026-10-11 — Phase 3 Part 6: history, progress, the coach and Ask Gemini
- Four tabs (Today, History, Progress, Coach). History with every set of a session; a logged set can be corrected (limits as for logging, targets rebuilt, the change recorded) or deleted after a second tap. Progress: strength trends, weekly active minutes against 150 with 2 strength days, weeks in a row and weeks this year, milestones that stay, weight as weekly averages, waist, steps; an exercise page with "favourite" and "don't suggest this".
- Tier 1 coach, offline: 12 suggested questions plus typed ones, answered from the plan, the decision log and the rules; it can only ask the engine for a shorter or easier session. Warning signs, medicines, medical questions, pregnancy, pain, illness, diet and supplements are referred, never answered. "Why?" on Today, the preview, the summary, workout notices, history and coach answers shows each rule's title and how sure the evidence is in plain words. Ask Gemini: the exact text first, allow-listed reasons only, health questions not shared, share to the Gemini app or the share sheet. SAF-009 and COACH-001 covered: rule coverage 152/152.
- Settings: goals and weight tracking, training days, gym and machines, injuries (onboarding pages, plan follows at once), skipped exercises, dark/light/system look. One guard for every way into a session; illness answers kept for the day; a workout in progress always comes first. Decisions D-092 to D-098.
- Independent review: 5 high, 6 medium, 5 low (R6-01–R6-16), all fixed; re-check: 1 high, 1 medium, 2 low more (N1–N4), fixed; final check: everyday words taken for warning signs by the typo rule, fixed. The strings check now also refuses a string defined twice.
- Tests: 610 locally; 638 on GitHub (engine 408, data 176, app 54); 9/9 device tests on 5 emulated phones and tablets; 37 screen pictures. Report `docs/phase3/part6_report.md`.

## 2026-10-10 — Phase 3 Part 5: the screens
- The app is usable end to end: 11-step onboarding; Today with the check-in and session preview; the workout (big numbers, one-tap logging, finding-your-weight sets, rest countdown and an exact end-of-rest alarm, interval timer, Replace/busy, something hurts, warning signs, change time, added sets); summary; safety-stop screen; Settings (steps, backup with password, restore with a safety copy, erase, conditions editor, health questions again, doctor's OK per scope).
- Behind the screens: a plain-Kotlin controller tested on the JVM; the session player in the data core; taps one at a time with double taps ignored; results from the phone never dropped; session minutes without idle gaps; every word in string resources (checked in CI); screen pictures rendered on GitHub. Decisions D-083 to D-090.
- Independent review: 1 critical, 3 high, 8 medium, 14 low (R5-01–R5-26), all fixed; re-check of the fixes: 1 high, 4 medium, 3 low more (N1–N8), all fixed — including backup and restore results being dropped on a phone and the backup password being lost if the phone turned while the file picker was open.
- Tests: 564 locally; 589 on GitHub (engine 408, data 149, app 32); device tests on 5 emulated phones and tablets. Report `docs/phase3/part5_report.md`.

## 2026-10-10 — Phase 3 Part 4: data layer
- New `:data` module: Room 3 database in private storage (schema v1 exported and drift-checked; no destructive migrations), relational workout log plus 22 typed, versioned record types mapped to DATA-001 (D-073, D-074); the exercise library and condition table stay compiled into the app, records refer to them by ID and version (D-077).
- History drives the engine: estimated max, prescriptions, calibration handover, weekly totals, workload ratios, block clock; return to training (REG-002 to REG-005) from stored days off, misses and illness (D-076); red flags at any time end the session and stay until confirmed (D-081); deloads and DEL-004 resume; reduced loads recorded with their factor (D-082); type 2 diabetes spacing enforced by the final check.
- Steps from the phone's step counter, read on app open, permission only when tracking is turned on, restarts by boot count (D-078). Backup and restore with an optional password (AES-256-GCM, PBKDF2 600k), checked fully, safety copy first, replaced in one step and normalised (D-075). Android system backup only when end-to-end encrypted (D-079); no free-text notes, decision log ~1 year, erase leaves nothing readable (D-080).
- Independent review: 32 findings (R4-01–R4-32), all fixed with tests; re-check of the fixes: 4 high, 5 medium, 4 low more (RC-01–RC-13), all fixed. Rule coverage 150/152 (DATA-001 now covered).
- Tests: 498 locally; 516 on GitHub (engine 408, data 107 incl. Room and Robolectric, app 1); device tests on 5 emulated phones and tablets. Report `docs/phase3/part4_report.md`.

## 2026-10-09 — Phase 3 Part 3: Research Update 1.1 in the engine
- Research Update 1.1 approved by the Product Owner (decision 2 as recommended: cardio machines are per-person choices). Registry 1.1.0 (12 new rules, 10 changed), then 1.1.1 (SAF-010 table reference only); health-condition table 1.0.1; library 1.1.0 → 1.1.1.
- Engine: fat-loss goal and age-banded mix, weekly activity target (never below 150), steps and brisk-walk counting, progress trends (weight, waist, strength), health-condition profiles applied by planner, generator and validator, starting from your own numbers, 60+ volume floor, reduced-range rule, habit features, calm mobility session, away-from-gym day.
- Independent review: 6 high, 7 medium, 2 low findings, all fixed (R3-01–R3-15); re-check of the fixes found 1 medium and 4 low more, fixed (R3-16–R3-20). Decisions D-068 to D-072.
- 406 tests pass locally and on GitHub; property tests across seeds; 52-week simulation for five fat-loss users aged 35–70 with conditions: no rule broken, all got stronger. Report `docs/phase3/part3_report.md`.

## 2026-10-09 — Product Owner change requests CR-001 to CR-005
- Health-condition picker driven by a data table (D-058); starting weights and personal records (D-059); default goal "Lose fat, keep muscle (30+)" with an age-banded training mix and no food features (D-060); original animated exercise illustrations (D-061, sample published); phone step counter (D-062); no-box gyms (D-063).
- Fix: a missing plyo box no longer turns a box jump into a slow tempo squat; power work swaps only to power work (346 tests).
- Part 3 paused for Research Update 1.1. Change log: `docs/change_requests/README.md`.
- Research Update 1.1 ready for sign-off: evidence reviews (fat loss without diet, age and muscle, HIIT, steps, progress measures; 11 health conditions plus 3 optional; medical-device boundaries), proposed Registry 1.1.0 (9 new rules FL-001–005, STEP-001–002, SAF-010, CAL-002; CON-004, CON-006, PH-001, DATA-001 changed; MOD-001 decision) and health-condition table v1.0 draft. Independently checked: 18 findings fixed before release.
- Creator review (CR-006, D-064): six creators reviewed by the expert agents; used only as pointers to research or design ideas, never cited; Heather Robertson excluded. Added to the update: AGE-001 60+ strength-volume floor, MOB-004 reduced-range rule, ADH-002 "welcome back", ADH-004 habit-time range, new ADH-005 planning prompt, MOB-006 calm mobility session and EQ-003 away-from-gym preset; Pelland 2026 replaces the preprint; Lally 2010 link fixed. Now 12 new rules and 8 changed (152 rules).

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
