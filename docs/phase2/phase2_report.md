# Personal Fitness Coach — Phase 2: Product Requirements, UX & Architecture

Oct 7, 2026 · @Abhinav Garg

Phase 2 turns the approved fitness science into a buildable product: a native Android app (Kotlin, Jetpack Compose, Room) that works entirely offline, never asks for internet access, explains every change in one line, and protects your training history three ways. It is ready for your sign-off; no code has been written.

**How to review (about 15 minutes).** Look at the wireframes and navigation map (section 5), read the user journeys (section 4), and check the gate report at the end. The architecture sections are reference detail for the build. Comment anywhere; reply **APPROVED** in chat when you're happy.

**Three things worth your attention.**

- **The AI coach is offline and rule-based in V1.** It answers "why" questions from the app's own decision records, so it is always accurate and free. A chatty AI model is designed in but switched off until you decide you want it.
- **No internet permission at all.** Your data physically cannot leave the phone except through your own export or Android's encrypted backup.
- **Rest alerts use the system alarm**, so your phone buzzes at the end of rest even when locked in your pocket.

## Phase 1 carry-overs

Phase 1 was approved on 7 October 2026, and all four open items from it are now closed; none changed a training rule.

| Item | Outcome | Registry effect |
| --- | --- | --- |
| Phase 1 sign-off | Approved by you | All 140 rules promoted to version 1.0.0, status "approved" |
| ACSM 2026 position stand: read the full text | Done. It confirms every rule that cites it (≥2 sessions/week, ≥80% 1RM and 2–3 sets for strength, ≥10 sets/week for size, power at 30–70% 1RM with ≤24 total reps, failure and complex periodisation not required). It also found inter-set rest did not consistently change outcomes, which supports compressing rests when time is short. | **Patch 1.0.1** on 16 rules: citation corrected to Currier BS et al., Med Sci Sports Exerc 2026;58(4):851–872; ACSM 2026 added as evidence to REST-002, REST-003, REST-007. No numbers changed. |
| Pelland 2024 volume/frequency meta-regression | Still a preprint (no journal version found) | None; stays flagged as preprint |
| PAR-Q+ wording licence | Terms not confirmed from an official source | None; the app keeps its own original screening wording (decision D-015) |
| Air/fan bikes and treadmill walking | No reply, so the default stands | Stay excluded (D-018); change any time from Settings → Equipment |

## 1. Product Requirements Document

Personal Fitness Coach V1 is a single-user, offline Android app that tells you exactly what to do in the gym today, adapts it to how you feel and how much time you have, and explains every change in one line. It has 14 must-have features, no accounts, no cloud and no internet permission at all in V1.

### Vision

> "I have a highly intelligent personal trainer inside my phone" — not "I am operating a fitness-management system."

### Problem statement

| Problem today | What the app does instead |
| --- | --- |
| Generic programmes ignore how you feel, your equipment and your schedule | Today's workout = long-term plan + today's readiness + recent performance (A12) |
| Not knowing what weight to lift or when to add more | Suggested load per set, rounded to your real plates and dumbbells, with clear progression rules |
| Short on time → the session gets abandoned or cut off at the end | Time-budget engine keeps the most valuable work (TIME-001, TIME-002) |
| Fear of overdoing it, or of a niggle becoming an injury | Readiness tiers, pain gate, hard caps and a red-flag stop |
| Logging is fiddly between sets | One tap to log a set done as prescribed |
| Apps that shame missed days | Forgiving weekly streaks, no guilt language |

### Personas

| Persona | Who | Drives which tests |
| --- | --- | --- |
| **P1 — You (primary)** | Non-technical professional, trains in a commercial gym, values clear guidance and evidence, no treadmill running, bikes or stair machines | Every feature; UAT |
| P2 — Returning beginner | Hasn't lifted for years; unsure of loads and technique | Calibration, beginner effort prompts, screening |
| P3 — Time-crunched | Often has 30–40 minutes | Time-budget compression |
| P4 — Niggly shoulder | Recurring shoulder pain on pressing | Pain gate, substitution, conservative region mode |
| P5 — Poor sleeper | High stress, short sleep | Readiness tiers, deload triggers |

P2–P5 are synthetic personas for testing and Phase 4 simulations; you are the only real user.

### V1 features (MoSCoW)

| # | Feature | Priority | Key rules |
| --- | --- | --- | --- |
| F1 | Onboarding: screening, profile, schedule, equipment, priorities, limitations | Must | SAF-001, EXP-001, DATA-001 |
| F2 | Initial calibration (first \~4 sessions) | Must | CAL-001 |
| F3 | Daily readiness check (≤15 s) with tier and "why" | Must | RDY-001–007 |
| F4 | Today's workout generation (12-month plan aware) | Must | GEN-001, PER, SCH |
| F5 | Guided workout: targets, cues, last time, warm-up sets | Must | WU-002, ORD-001 |
| F6 | One-tap set logging, steppers, RPE/RIR picker, form check | Must | INT-001, INT-006, PROG-008 |
| F7 | Rest timer that alerts with the screen off | Must | REST rules |
| F8 | Replace exercise / equipment occupied | Must | SUB-001–003, EQ-002 |
| F9 | "Something hurts" and red-flag flows | Must | SAF-002–004 |
| F10 | "I only have N minutes" time adaptation | Must | TIME-001–004 |
| F11 | Session summary with session-RPE | Must | LOAD-001–002 |
| F12 | History, personal records, progress trends, weekly WHO-minutes | Must | PH-001, INT-004 |
| F13 | Coach: plain-language "Why?" answers (offline, Tier 1) | Must | SAF-009, COACH-001 |
| F14 | Backup: export, restore, reminders | Must | Section 12 |
| F15 | Forgiving streak and micro-achievements | Should | ADH-001–002 |
| F16 | Settings: units, theme, equipment edits, data erase | Should | DATA-001 |
| F17 | Conversational AI coach (Tier 2) | Could (built behind a switch, off by default) | SAF-009 |
| F18 | Body measurements | Could | DATA-001 |

### Non-goals for V1 (A14)

Nutrition and calorie tracking, wearables and Health Connect, iOS, accounts or cloud sync, social features, camera form analysis, payments and ads. Each is a roadmap candidate requiring change control.

### Safety, privacy and offline boundaries

- Safety: Phase 1 section 35 applies unchanged; the app is not a medical device.
- Privacy: all data stays on the phone; V1 does not request the internet permission, so it physically cannot send data anywhere.
- Offline: 100% of A15 works with no connection, by construction.

### Success metrics

| Metric | Target | How measured |
| --- | --- | --- |
| Weekly adherence | ≥80% of planned sessions over any 8 weeks | Logged sessions ÷ planned |
| Time to log a set done as prescribed | ≤2 s, 1 tap | UI test + UAT stopwatch |
| Time for readiness check | ≤15 s | UAT |
| Logged data loss | Zero sets lost, ever | Interruption tests (E5) |
| Progress | e1RM on main lifts trending up across each block, or explained by deload/illness | Progress screen |
| WHO floor | Met in ≥75% of weeks when your schedule allows it | PH-001 weekly accounting |
| Crash-free sessions | ≥99.5% | Local crash counter |
| Safety | Zero sessions shown that fail the validator | Validator log + property tests |

## 2. Functional specifications

Ten pipelines carry every feature; each takes defined inputs, runs named Phase 1 rules, writes a Decision Log entry for anything it changes, and has its edge cases specified up front.

| # | Pipeline | Input | Process (rules) | Output | Edge cases |
| --- | --- | --- | --- | --- | --- |
| FS-1 | Onboarding & profile | Answers to \~12 screens; equipment checklist with smallest increments | Screening logic (SAF-001) → experience class (EXP-001) → plan seeding (PER-003, PER-006, SCH-001) → calibration queue (CAL-001) | Profile, ScreeningResult, EquipmentInventory, Program v1, first week scheduled | Skipped optional fields → ASSUMPTION defaults shown and editable; screening flagged → conservative mode banner; app killed mid-onboarding → resumes at the same screen |
| FS-2 | Daily readiness & adaptation | 4 ratings, pain yes/no, optional sleep hours, minutes available, equipment missing today | Red flag → SAF-002; pain → SAF-003; score and tier (RDY-001–006); fatigue signals (DEL-001); user tier choice (RDY-007) | Tier + one-line reason + modified session | Check skipped → uses last 3-day average, shown as "estimated"; check done twice in a day → latest wins before the session starts, locked after |
| FS-3 | Workout generation | Plan position, tier, history, inventory, time budget, registry | GEN-001 ten steps; SAF-008 validator last | Validated Workout (planned) + Decision Log | No valid exercise for a slot → slot dropped with reason; validator cannot fix → LIGHT fallback; first-ever session → calibration session |
| FS-4 | Guided execution | Validated Workout | Ordered exercises (ORD-001), warm-up sets (WU-002), cues, last performance | Active session screens; ActiveSessionState saved continuously | Phone call, lock, background, process death → resume exactly; screen kept on while active |
| FS-5 | Set logging, progression, history | Tap "Done as planned" or adjusted reps/load/RIR; form check | Write LoggedSet immediately; e1RM update (INT-004); in-session autoregulation (INT-007); next-exposure prescription (PROG-001–008); volume accounting (VOL-001–002) | Updated history, PRs, next targets | Absurd entries (e.g. 500 kg, 99 reps) → confirm prompt; edits after the session recompute downstream values and are logged |
| FS-6 | Mid-session adaptation | Early-set RPE, missed reps, pain, overtime, occupied equipment | INT-007; SAF-003; TIME-002 re-run on remaining items; SUB / reorder (EQ-002) | Changed remaining plan + Decision Log | Multiple changes stack but never exceed caps (SAF-005); red flag ends everything |
| FS-7 | Time adaptation | "I have N minutes" (before or during) | TIME-001–004 | Re-fitted session with a list of what moved where | N < 20 → express session offer; N > 90 → extensions only within caps |
| FS-8 | Equipment adaptation | "X is occupied / unavailable today / gone for good" | SUB-001–003; reorder option; inventory update for permanent changes | Swap with one-line reason, or "come back later" | No acceptable swap → skip with volume carried forward; permanent removal → future plans regenerated |
| FS-9 | Coach & questions | Tap a suggested question or type one | Tier 1: intent match → Decision Log / registry → template answer; Tier 2 (if enabled): LLM with engine-function tools, safety filter (SAF-009) | Plain-language answer citing the rule in "Why?" detail; any action goes through the engine | Unknown question → offers the closest 3 questions; medical topics → refers to a professional |
| FS-10 | Backup, export, restore | Export tap / reminder; restore file | Serialise all data + versions + checksum; restore validates, migrates, replaces after automatic pre-restore export | Backup file (+ optional CSV) | Corrupt or newer-version file → refuse with clear message, nothing changed |

## 3. Non-functional requirements

Every quality target below is measurable and has a named test in section 18; the strictest are zero lost sets, no internet permission, and one-tap logging in under two seconds.

| ID | Area | Requirement | Target | Verified by |
| --- | --- | --- | --- | --- |
| NFR-01 | Performance | Cold launch to usable Today screen | < 1.5 s on a mid-range phone from 2022 or later | Macrobenchmark on emulator + your phone in UAT |
| NFR-02 | Performance | Workout generation (incl. validator) | < 500 ms | Engine benchmark test |
| NFR-03 | Performance | Set logging: tap → saved | < 100 ms; UI never blocks | Instrumented test |
| NFR-04 | Offline | Everything in A15 works with no connection | 100% | App has no internet permission (static check on the APK) |
| NFR-05 | Battery | No background work outside an active session; no wake locks; only the rest-timer alarm while resting | Zero scheduled jobs when idle | Manifest + dumpsys check in CI emulator |
| NFR-06 | Data integrity | Each set written the moment it is logged; active session persisted continuously | Zero lost sets across E5 interruptions | Interruption test suite |
| NFR-07 | Privacy | No analytics, ads, trackers or third-party SDKs that phone home | None present | Dependency allow-list check in CI |
| NFR-08 | Accessibility | System font scaling up to 200%; contrast ≥ 4.5:1 (text) / 3:1 (large numerals); TalkBack labels on all controls; colour never the only signal | All screens pass | Compose accessibility checks + manual TalkBack pass |
| NFR-09 | Gym usability | Touch targets ≥ 56 dp for primary actions (Android minimum is 48 dp); one-handed reach for log/rest controls; dark theme default | All active-session controls | UI review + UAT |
| NFR-10 | Size | Lean APK, shrunk and optimised | < 15 MB | CI reports APK size; fails above limit |
| NFR-11 | Compatibility | Minimum Android 10 (API 29) — ASSUMPTION until you share your phone model; built against the latest Android release at Phase 3 start | Runs on API 29–latest | CI emulator matrix (oldest + newest) |
| NFR-12 | Reliability | Crash-free sessions | ≥ 99.5%; no crash ever loses data | Local crash counter; chaos tests |
| NFR-13 | Maintainability | Fitness engine is pure Kotlin with no Android code; every rule ID has ≥2 tests | ≥ 90% line coverage on the engine; 100% rule coverage | CI coverage + rule-coverage script |
| NFR-14 | Determinism | Same inputs → same workout | 100% reproducible | Golden-file tests |
| NFR-15 | Units & time | kg default, lb option; times stored in UTC with a local training date | Correct across time-zone travel | Unit tests |
| NFR-16 | Language | English, all text in resource files (ready for translation later) | No hard-coded UI strings | Lint rule |

## 4. User journeys

Four journeys cover the moments that matter most: the first day takes about 5 minutes of setup before a gentle calibration session, a normal day takes 15 seconds before training starts, and bad days or missed weeks are absorbed without guilt or cramming.

### J1 — First day

| Step | You | The app |
| --- | --- | --- |
| 1 | Install from GitHub Releases, open | Welcome; one-time plain-language disclaimer (not a medical device) |
| 2 | Answer 8 screening questions | All "no" → standard mode; any symptom → "please check with your doctor" + conservative mode (SAF-001) |
| 3 | Age, experience, days and times, session length, priorities, injuries | Classifies experience (EXP-001); fills defaults you can skip (\~3–5 min total) |
| 4 | Tick your gym's equipment and smallest plates/dumbbells | Builds load increments (PROG-003); hides exercises you can't do |
| 5 | See "Your first 2 weeks" | Explains calibration: no maximal lifts, finding comfortable working weights (CAL-001) |
| 6 | Start session 1 | Calibration session: ramps each lift by your effort answers; easy cardio only |
| 7 | Finish | Summary asks one effort rating; shows "Next: Thursday, upper body" |

### J2 — A normal gym day

| Step | You | The app |
| --- | --- | --- |
| 1 | Open the app in the changing room | Today: "Lower body · 58 min" with a big Start button |
| 2 | 4 taps + "no pain" | FULL tier, "Ready to train as planned" (RDY rules) |
| 3 | Warm-up | Raise on the rower, 2 drills, then ramp-up sets calculated from today's load (WU-001, WU-002) |
| 4 | Each set: lift, tap "Done" | Set saved instantly; rest timer starts; phone buzzes when rest ends, even locked (F7) |
| 5 | Squat felt easy (RPE 6 vs target 8) | Next set +2.5 kg, with a one-line reason (INT-007) |
| 6 | Cable station busy | "Occupied" → seated machine row offered, or "do it later" (SUB-002) |
| 7 | Intervals on the SkiErg | Interval timer with work/rest beeps (HIIT-002) |
| 8 | Finish, rate the session 7/10 | Session load saved (LOAD-001); 1 new personal record shown; streak week ticks |

### J3 — A bad day

| Step | You | The app |
| --- | --- | --- |
| 1 | Slept 4.5 h, sore, "left knee 3/10", 35 minutes available | Tier capped at MODIFIED (RDY-005); knee-friendly swaps (SAF-003); session fitted to 35 min (TIME-002) |
| 2 | Read the reason | "Shorter and lighter today: under 5 hours' sleep and a sore knee. Squats swapped for leg press with a shorter range." |
| 3 | Knee climbs to 5/10 mid-set, tap "Something hurts" | Stops that exercise; offers a pain-free option or ends lower-body work; knee enters conservative mode (SAF-003) |
| 4 | Finish early | Partial session saved honestly; no streak penalty; tomorrow's plan re-checked |
| 5 | Knee still sore 2 sessions later | Suggests seeing a physiotherapist; knee stays protected until 2 pain-free sessions (SAF-004) |

### J4 — A missed week

| Step | You | The app |
| --- | --- | --- |
| 1 | Away for 10 days, open the app | "Welcome back" — no guilt, streak shows best and current weeks (ADH-001) |
| 2 | Start | First session back at MODIFIED with loads −5% (REG-003); the plan shifted rather than being crammed (REG-002) |
| 3 | Next sessions | Back to normal progression; block clock paused for the missed week (PER-005) |

## 5. Screens, navigation and wireframes

The app has four tabs (Today, History, Progress, Coach) plus Settings, a 9-step onboarding, and a focused workout mode with no tabs at all, so between sets you only ever see the current set, the rest timer, or a one-tap sheet. Wireframes for the six most-used screens are drawn below.

&#91;embedded content: navigation map · 4 tabs, workout mode, onboarding\]

The summary returns you to Today; "Why?" links from any screen open the Coach tab at the matching answer.

### Screen inventory

| Area | Screen | Purpose | Key elements |
| --- | --- | --- | --- |
| Onboarding | O1 Welcome | First impression, disclaimer once | What the app does; "not a medical device" |
|  | O2 Screening | SAF-001 | 8 yes/no questions, plain words |
|  | O3 About you | Age, experience, optional sex and bodyweight | Skippable optional fields |
|  | O4 Schedule | Days, preferred times, session length | Day chips, 30–90 min slider |
|  | O5 Equipment | Inventory + smallest increments | Category checklists, plate and dumbbell pickers |
|  | O6 Priorities | Top 3 goals, likes and dislikes | Drag to rank |
|  | O7 Limitations | Injuries and painful movements | Body map, tags |
|  | O8 Your plan | 12-month view, calibration explained | Block strip, "Start" |
|  | O9 Alerts | Ask notification permission with the reason | "So the rest timer can buzz when your phone is locked" |
| Today tab | T1 Today | Next session, check-in, week strip, streak | Big Start button |
|  | T2 Check-in | Readiness, pain, time, missing equipment | 4 dot rows, pain yes/no, time stepper |
|  | T3 Session preview | List, duration, why it looks like this | Edit time, swap, start |
| Workout mode | A1 Exercise | Current set targets, log | Big numerals, "Done as planned", steppers, last time, cues |
|  | A2 Rest timer | Countdown, alert | ±30 s, skip, next-set preview |
|  | A3 Replace sheet | Swap or do later | Top 3 swaps with reasons |
|  | A4 Something hurts | Pain flow | Body map, type, 0–10, result |
|  | A5 Safety stop | Red flag | Calm guidance, emergency number |
|  | A6 Change time | Re-fit remaining session | Minutes picker, what moves |
|  | A7 Interval timer | Conditioning | Work/rest phases, beeps, round count |
|  | A8 Summary | Session effort, records, changes | 0–10 effort, "What changed", next session |
|  | R1 Resume prompt | After interruption | "Continue where you stopped?" |
| History tab | H1 History | Calendar and list | Sessions with tier and duration |
|  | H2 Session detail | Review and correct | Edit any set (changes logged) |
| Progress tab | P1 Progress | Trends | Main-lift strength, weekly sets vs target, WHO minutes, streak, achievements |
|  | P2 Exercise detail | One lift over time | e1RM and best sets |
| Coach tab | C1 Coach | Questions and answers | Suggested questions, answer cards, "Why?" links |
| Settings | S1 Settings | Profile, equipment, schedule, units, theme, alerts, backup, privacy, about | "Erase all my data" with two-step confirm |

### Wireframes

&#91;embedded content: wireframes · Today, check-in, logging a set\]

Today leads with one big Start button; the check-in is four rows of dots; a set done as planned is one tap on the largest button.

&#91;embedded content: wireframes · rest timer, something hurts, summary\]

The rest countdown keeps "Something hurts" in reach; the pain sheet ends in a concrete action, never a lighter version of the same movement; the summary asks just one question.

## 6. App state machine

The app runs two linked state machines: a one-time setup machine (first launch → ready) and a daily session machine that every workout passes through. Every state is saved to the database on entry, so a crash, reboot or phone call always lands back in a known state; there is no separate "offline" state because the app is always offline.

&#91;embedded content: state machine · setup and daily session\]

The table below defines each state, what moves you in and out of it, and where it is saved; conservative mode and calibration are flags that run alongside these states.

| State | Meaning | Entered when | Leaves when | Saved as |
| --- | --- | --- | --- | --- |
| FIRST\_LAUNCH | No profile yet | App installed or data erased | Welcome accepted | — |
| ONBOARDING | Steps O1–O9 | Welcome accepted | All required answers given | `UserProfile.onboardingStep` |
| CONSERVATIVE\_MODE (flag) | Screening flagged or region in pain mode | SAF-001 / SAF-004 | Clearance confirmed / 2 pain-free sessions | `ScreeningResult`, `PainReport` |
| CALIBRATION (flag) | First \~4 sessions | Onboarding finished | All main lifts have working loads | `Program.calibrationComplete` |
| READY | Profile complete, plan exists | Onboarding done | — (home state) | `Program` |
| PLANNED | Today's session generated | Training day arrives or user opens Today | Check-in started, or day skipped | `Workout(status=PLANNED)` |
| CHECKIN | Readiness check open | Start tapped | Tier decided | `ReadinessLog` |
| TIER\_DECIDED | FULL / MODIFIED / LIGHT / RECOVERY | Check-in done | Start session or rest day accepted | `Workout.tier` |
| ACTIVE.EXERCISE | Showing a set | Session started / rest ended | Set logged, sheet opened, pause | `ActiveSessionState` |
| ACTIVE.RESTING | Rest timer running | Set logged | Timer ends or skip | `ActiveSessionState.restEndsAt` |
| ACTIVE.SHEET | Replace, Something hurts or Change time open | User taps | Choice made or dismissed | `ActiveSessionState.sheet` |
| ACTIVE.PAUSED | User paused | Pause | Resume or end | `ActiveSessionState.pausedAt` |
| INTERRUPTED | App killed or phone restarted mid-session | Detected at next launch | Resume or end | `ActiveSessionState` present at launch |
| COMPLETED | All planned work done | Last item finished | Summary saved | `Workout(status=COMPLETED)` |
| PARTIAL | Ended early by choice, time or pain | End tapped / time out / pain gate | Summary saved | `Workout(status=PARTIAL)` |
| SKIPPED | Day not trained | User skips, or day passes | — | `Workout(status=SKIPPED)` |
| RECOVERY\_DAY | Tier RECOVERY accepted | Check-in result | Optional easy work done or day ends | `Workout(status=RECOVERY)` |
| SAFETY\_STOP | Red flag reported | SAF-002 at any point | Acknowledged; next session LIGHT only after you confirm you're OK | `Workout(status=SAFETY_STOP)` + `PainReport` |
| ERROR\_SAFE | Database could not open | Startup failure | Fixed by retry or restore | No writes; offers export of whatever is readable |

## 7. Data model and database schema

One on-device SQLite database (via Room) holds 26 tables at schema version 1; the training rules and the exercise library ship inside the app as versioned, read-only files, and every workout records which rule version produced it. Migrations are always explicit and tested; the app is never allowed to wipe data to upgrade.

### Entities

| Group | Entity | Key fields | Notes |
| --- | --- | --- | --- |
| You | User | id, units (kg/lb), theme, createdAt | Single row |
|  | UserProfile | birthYear, sex?, bodyweightKg?, experience, trainingDays, preferredTimes, sessionMinutes, priorities\[3\], onboardingStep, aiTier | `?` = optional |
|  | ScreeningResult | takenAt, answers, outcome, clearanceConfirmedAt? | Drives conservative mode |
|  | EquipmentItem | type, name, availability (always / unavailable until date / removed), increments | Plates, dumbbell list, stack step |
|  | UserPreference | key, value; exercise preference score 0–1, excluded flag | SUB-003 learning lives here |
| Library | Exercise | all fields of the Phase 1 schema (section 29), libraryVersion | Seeded from a bundled file; upgraded by version on app update |
|  | ExerciseVariant | exerciseId, your note (seat height, grip) | Your personal notes, kept across library updates |
| Plan | Program | registryVersion, startDate, blockSequence, currentBlock, blockWeek, pausedWeeks, calibrationComplete | One active programme |
|  | PeriodizationPhase | programId, type, plannedWeeks, started, ended, endReason | The blocks of the year |
|  | Mesocycle | phaseId, index, startVolumes | Volume landmarks per muscle at start |
|  | Week | mesocycleId, index, plannedSessions, isDeload, plannedSSU |  |
| Sessions | Workout | date (local), status, tier, plannedMin, actualMin, startedAt, endedAt, sessionRpe, sessionLoad, ssuPlanned, ssuActual, registryVersion, template | Planned and actual in one row |
|  | WorkoutExercise | workoutId, order, exerciseId, priority P0–P5, plannedSets, repRange, targetRir, targetLoadKg, restSec, status, swappedFrom? |  |
|  | LoggedSet | workoutExerciseId, setIndex, kind (warm-up/working), loadKg, reps, seconds?, metres?, watts?, rpe?, rir?, formCheck, deviation (as planned/adjusted/user-added), loggedAt | Written the moment you tap |
|  | ActiveSessionState | workoutId, state, exerciseIdx, setIdx, restEndsAt, sheet, pausedAt, updatedAt | Single row; powers resume |
| Body & recovery | ReadinessLog | date, sleep, energy, soreness, stress, sleepHours?, minutesAvailable, rRaw, rFinal, tier, signalsActive |  |
|  | PainReport | at, workoutId?, region, side, type, rating, worsening, action, resolvedAt? |  |
|  | RecoveryLog | date, illness flag, run-down flag, note | Feeds SAF-007 and signal F6 |
|  | WorkloadLog | date, dailyLoad, acuteEwma, chronicEwma, ratio, weeklyLoad, monotony, strain | Recomputed when the app opens (no background jobs) |
| Progress | PerformanceMetric | exerciseId, date, e1rmKg, bestSet |  |
|  | PersonalRecord | exerciseId, type, value, achievedAt, setId |  |
|  | ProgressMetric | weekStart, muscle, fractionalSets, target; WHO minutes |  |
|  | Achievement | type, achievedAt, data |  |
| Explainability | DecisionLog | at, workoutId?, kind, ruleIds\[\], inputs, outputs, reasonText, registryVersion | Every engine change; powers "Why?" |
|  | RuleVersion | registryVersion, checksum, activatedAt | Which rule bundle was active when |
|  | ResearchUpdate | fromVersion, toVersion, summary | Plain-language "what changed in the rules" after an app update |

### Rules for the database

1. **Schema versioning:** release 1.0 ships schema version 1. Every later change increments the version and ships an explicit migration; Room's schema files are committed so every version is reproducible.
2. **No destructive fallback:** the build fails if destructive-migration fallback appears anywhere (CI text check).
3. **Migration tests:** every migration is tested from every earlier version with Room's migration test helper (Phase 4 scenario 30: upgrade over existing data).
4. **Atomic writes:** logging a set writes the LoggedSet and updates ActiveSessionState in one transaction; write-ahead logging is on; foreign keys are enforced.
5. **Time:** instants stored as UTC milliseconds plus a local training date, so travel across time zones never moves a session to the wrong day.
6. **Structured fields** (lists, parameter maps) are stored as versioned JSON via Kotlin serialization.
7. **Rules and library are not in the database:** `rule_registry_v1.0.json` and the exercise library ship as read-only app files; the database stores only which version was used.

## 8. Fitness engine architecture

The fitness engine is a self-contained, pure Kotlin library with no Android code, no clock and no database: it takes a snapshot of your data, applies registry rules, and returns a result plus a list of decisions. That makes every rule testable in milliseconds and lets Phase 4 run 12-month simulations on the same code the phone runs.

### Design principles

1. **Pure functions:** input snapshot → output + decisions. No I/O, no hidden state; time is passed in.
2. **Registry-driven:** at build time a generator reads `rule_registry_v1.0.json` and produces typed parameter accessors and rule-ID constants. Code that references an unknown rule ID or parameter does not compile, so there can be no unexplained "magic numbers".
3. **Every change explains itself:** each function returns `EngineResult(value, decisions)`; a decision carries rule IDs, inputs, outputs and a reason key. The engine never writes sentences; the coach layer renders them.
4. **Deterministic:** ties broken by exercise ID; same snapshot → same workout.
5. **Safety last and always:** every public function that produces a session ends in the safety validator (SAF-008).

### Components

| Component | Responsibility | Rules |
| --- | --- | --- |
| RegistryLoader | Parse, schema-check and checksum the bundled registry; expose typed parameters | B6 |
| ProgramPlanner | 12-month block sequence, priority weighting, disruptions | PER-001–006 |
| WeekScheduler | Templates, spacing, rescheduling | SCH-001–003, FREQ, PAT |
| VolumeAllocator | Weekly sets per muscle, fractional counting, per-session caps | VOL-001–008 |
| SessionGenerator | The 10-step pipeline | GEN-001, ORD |
| ExerciseSelector + SubstitutionScorer | Slot filling, swaps | SUB-001–003, EQ |
| ConditioningSelector | Modality and protocol choice | MOD, AER, HIIT, CON |
| LoadCalculator + PlateMath | e1RM, load selection, rounding to your equipment | INT-004–005, PROG-003 |
| WarmupBuilder | RAMP and ramp-up sets | WU-001–004 |
| TimeBudgetFitter | Priority tiers, compression, extension, time model | TIME-001–004 |
| ReadinessScorer + FatigueSignals | Score, tiers, six signals | RDY, DEL-001 |
| WorkloadTracker | Session load, EWMA, monotony, SSU | LOAD, VOL-007 |
| ProgressionEngine | Next-exposure targets, in-session autoregulation, technique gate | PROG, INT-007 |
| DeloadDecider + ReturnToTraining | Deloads, missed sessions, layoffs, illness | DEL, REG |
| Calibrator | First-weeks load finding | CAL-001 |
| SafetyKernel | Screening policy, pain gate, caps, validator, user-addition checks | SAF-001–008 |

### Public interface (sketch)

```kotlin
interface FitnessEngine {
    fun planProgram(profile: Profile, gym: Inventory, start: LocalDate): EngineResult<Program>
    fun generateSession(state: EngineState, checkIn: CheckIn?, minutes: Int): EngineResult<Workout>
    fun onSetLogged(state: EngineState, set: LoggedSet): EngineResult<InSessionAdjustment>
    fun adaptSession(state: EngineState, change: SessionChange): EngineResult<Workout> // time, equipment, pain
    fun finishSession(state: EngineState, summary: SessionSummary): EngineResult<PostSession>
    fun checkUserAddition(state: EngineState, addition: Addition): AdditionVerdict  // SAF-006
}

data class EngineResult<T>(val value: T, val decisions: List<Decision>)
data class Decision(
    val kind: DecisionKind, val ruleIds: List<RuleId>,
    val inputs: Map<String, Any>, val outputs: Map<String, Any>,
    val reason: ReasonKey, val reasonArgs: Map<String, Any>
)
```

The app loads a snapshot from the database, calls the engine, and saves the result and its decisions in one transaction. If the engine ever throws, the app shows the last validated session or a LIGHT fallback and records the error; it never shows an unvalidated workout.

## 9, 14, 15. AI coach, agents and the LLM boundary

V1's coach is fully offline and deterministic: it answers from the Decision Log and the rule registry, so every answer is true by construction and costs nothing. A conversational language model is designed in behind a switch but ships **off**; switching it on later is a scope change that needs your approval. Of the eight "agents" the prompt lists, seven are deterministic engine components and the research agent stays on the developer side.

### 9. AI architecture

| Tier | Status in V1 | How it works | Cost / network |
| --- | --- | --- | --- |
| **Tier 1 — explanation coach** | Built and on | "Why?" on any change opens the Decision Log entry and renders a plain-language template for its reason key, with the rule's title, confidence and sources one tap deeper. The Coach tab offers suggested questions; typed questions are matched offline to \~12 intents (the C10 list); unknown questions get the 3 closest suggestions. | Free; offline |
| **Tier 2 — conversational coach** | Interface built, switched off | `LlmProvider` with two possible back-ends: on-device Gemini Nano through Google's ML Kit Prompt API on supported phones (alpha when last checked, Oct 2025; re-check at Phase 3), or a cloud model with your own API key (needs the internet permission, so it would be a separate opt-in build with costs shown first). | On-device: free; cloud: pay-per-use |

**Recommended default:** stay on Tier 1 for V1, then decide in V1.1 once you have used the app and we know your phone model.

### 14. Agent communication protocol

| Agent (prompt C11) | V1 implementation | Receives | Returns |
| --- | --- | --- | --- |
| Orchestrator (SessionController) | App component; the only one that changes stored state | UI events, ActionRequests | Updated screens, persisted results |
| Workout Planner | SessionGenerator | PlanRequest | CandidateSession + decisions |
| Exercise Selector | ExerciseSelector | Slot + context | Exercise + decisions |
| Recovery Analyst | ReadinessScorer + FatigueSignals | Check-in + history | Tier + signals |
| Substitution Agent | SubstitutionScorer | Original + reason + context | Top 3 + reasons |
| Progress Analyst | ProgressSummariser | Date range | Trends, records, plain summary |
| Coaching Agent | Tier 1 templates (Tier 2 optional) | CoachQuery | CoachAnswer (+ optional ActionRequest) |
| Safety Auditor | SafetyKernel | Any candidate session or addition | ValidationReport (pass / corrected / fallback) |
| Research Agent | Developer-side only (Phase 1 §37) | Papers | Proposed registry changes for your approval |

Protocol rules:

1. **Typed messages only:** Kotlin sealed classes (`PlanRequest`, `CandidateSession`, `ValidationReport`, `ActionRequest`, `ActionResult`, `Decision`, `CoachQuery`, `CoachAnswer`); in-process calls, no strings passed between agents.
2. **Hub and spoke:** agents never call each other; the Orchestrator calls them in order and the Safety Auditor always runs last.
3. **Single writer:** only the Orchestrator writes to the database, one transaction per user action.
4. **Validation points:** after generation, after every adaptation, after every user addition, and before anything is displayed.
5. **Failure behaviour:** any agent error → last validated session or a LIGHT fallback, a calm message, and an error record; never a crash, never an unvalidated screen.
6. **Logging:** state changes go to the Decision Log; technical errors go to a small on-device error log with no personal data.

### 15. API and LLM boundary

These rules apply the moment Tier 2 is ever switched on:

| Boundary | Rule |
| --- | --- |
| What the model sees | A minimal structured summary: today's tier, plan outline, recent decisions with rule IDs, your question. No name, no exact birth date, no screening answers (only "conservative mode: yes/no" when relevant). |
| What the model can do | Call engine functions only: `explainDecision`, `getRule`, `requestShorterSession(minutes)`, `requestSubstitution(exercise, reason)`, `requestEasierTier`, `getProgressSummary(range)`. No function can set sets, reps or loads. |
| Who decides | The engine validates every request; rejected requests return a reason the coach must relay. |
| Output filter | Blocks diagnosis, medication, supplement and extreme-diet content; removes any training numbers not in the validated plan; enforces the coaching voice (COACH-001); length cap. |
| Prompt injection | Your notes and any retrieved text are passed as quoted data. The real protection is structural: the model has no write path. |
| API key (cloud only) | Typed in by you, encrypted with the Android Keystore, excluded from backups and exports, never in source code, the repository or the APK. |
| Offline or timeout | Falls back to the Tier 1 answer. |
| Adversarial check | "Ignore my knee, make it harder" → engine refuses beyond pain and tier rules; coach explains why (Phase 4 scenario 29). |

## 10. Safety architecture

Safety runs at three points — before a session is shown, during it, and after it — through one component (SafetyKernel) inside the engine, so the same checks protect generated workouts, mid-session changes, your own additions and any future AI request. A failed check is corrected and logged; if it cannot be corrected the app falls back to a LIGHT session, and it never crashes or shows an unchecked workout.

| Stage | Check | Rule | On failure |
| --- | --- | --- | --- |
| Before (interception layer) | Screening mode respected (no HIIT/Z3, RIR ≥3 when conservative) | SAF-001 | Remove or downgrade items |
|  | Readiness-tier compliance | RDY-004 | Re-apply tier changes |
|  | Pain-region exclusions and conservative regions | SAF-003, SAF-004 | Swap or drop exercises |
|  | Contraindication and limitation tags | SUB-001 | Swap |
|  | Sets per muscle per session and per week; sets per session | VOL-005, VOL-008, SAF-005 | Trim lowest priority first |
|  | Weekly SSU and planned load cap | VOL-007, LOAD-005 | Trim accessories, then conditioning |
|  | HIIT count, spacing, prerequisites | HIIT-001, HIIT-003, HIIT-004 | Convert to steady Z1–Z2 |
|  | Pattern balance (pull:push, knee:hip) | PAT-002, PAT-003 | Rebalance at next opportunity; warn if impossible |
|  | Failure policy and excluded modalities | INT-003, MOD-001 | Set RIR ≥1; swap modality |
| During | "Something hurts" always one tap away | SAF-003 | Stop exercise / region / session |
|  | Red-flag symptoms | SAF-002 | SAFETY STOP |
|  | In-session load changes bounded (±10%) | INT-007 | Clamp |
|  | Your additions (sets, exercises, heavier) | SAF-006 | Warn and confirm past a cap; block at the absolute ceiling or on a painful region |
|  | Mid-session re-plans (time, equipment) | SAF-008 | Re-validate before display |
| After | Workload, monotony and fatigue signals updated | LOAD, DEL-001 | Feed tomorrow's tier and deload decision |
|  | Pain persistence | SAF-004 | Professional suggestion; region stays protected |
|  | Safety stop follow-up | SAF-002 | Next session LIGHT only after you confirm you're OK |

The kernel is shared code, so a rule like "no RIR-0 on barbell squats" is enforced identically whether the engine, you or a future AI coach is the source of the request. Its invariants are tested with property-based tests over thousands of random histories (section 18).

## 11–12. Offline operation, backup, export and restore

V1 is offline by construction: it does not declare the internet permission, so Android itself prevents any network access, and a CI check fails the build if that ever changes. Your history is protected three ways: Android's own encrypted backup, a one-tap export file you can keep anywhere, and a restore that always saves your current data first.

### 11. Offline architecture

| Concern | Design |
| --- | --- |
| Network | No internet permission in V1; build check fails if one appears (NFR-04) |
| Content | Rule registry, exercise library, cues and simple illustrations are bundled in the app |
| Rest alerts with the screen off | While the app is open, the timer counts down from a saved end time. When the phone locks or you switch apps, the app sets an exact system alarm for the end of rest and shows an ongoing notification with a live countdown. At zero, a high-priority notification vibrates and sounds, even in battery-saving idle. Logging the next set cancels it. |
| Why alarms, not a background service | Android's "health" foreground-service type requires declaring a body-sensor or activity-recognition permission the app doesn't need, and the short-service type is limited to about 3 minutes, shorter than a 5-minute rest. A system alarm survives the app being closed and uses no battery while waiting. |
| Permissions this needs | Notifications (asked once, with the reason) and exact alarms (granted automatically for timer-type apps installed outside the Play Store; a Play release would ask you instead) |
| If notifications are refused | Timer works in the app only; a banner explains the trade-off and how to switch alerts on |
| Screen | Kept awake during an active session; normal timeout otherwise |
| Interruptions | Session state is saved on every change; after a call, crash or reboot the app offers to resume exactly where you stopped |
| Clock changes and travel | Countdowns use the phone's elapsed-time clock; sessions are stored in UTC with a local training date |

### 12. Backup, export and restore specification

| Feature | Specification |
| --- | --- |
| Android backup | Database and settings included in Android's built-in backup to your Google account (25 MB limit per app). On Android 9 and later it is end-to-end encrypted with your phone's screen lock, per Android's documentation (confirmed on a real device in Phase 4). API keys and caches are excluded. |
| Export | Settings → Backup → Export: one file, `PersonalFitnessCoach-YYYY-MM-DD.pfcbackup`, saved wherever you choose (Downloads, Google Drive). Contains every table plus format, app, schema and rule-registry versions and a SHA-256 checksum. Optional extra: a CSV bundle (sessions, sets, readiness) for spreadsheets. |
| Optional password | Off by default. When on, the backup is encrypted (AES-256-GCM, key derived from your password with PBKDF2-SHA256). A forgotten password means the file cannot be opened; the app says so before you set one. |
| Restore | Pick a file → checksum and format checked → older formats migrated, newer ones refused → summary shown (date range, sessions, sets) → confirm → **your current data is exported automatically first** → data replaced → app reloads. Merging two histories is not supported in V1. |
| Reminder | After every 8 completed workouts, if your last export is over 30 days old, a dismissible card on Today: "Back up your training history — 10 seconds". |
| Erase all my data | Two-step confirmation; deletes database, settings, alarms and notifications; app returns to first launch. |
| Tests | Export → erase → restore must reproduce identical data; corrupt, truncated, older and newer files; Android backup and restore exercised on an emulator in CI (Phase 4, E5). |

## 13. Technical stack decision

**Decision: Kotlin, Jetpack Compose and Room — Google's own toolkit for Android apps.** In plain words: it is the native language and toolkit of Android, so lock-screen alarms, notifications and on-device AI work without workarounds, the fitness engine can be tested on any computer in seconds, and the app is built by one standard command on GitHub's free servers. It scored 48/50 against 41 for Flutter and 35 for React Native.

| Criterion (C17) | Kotlin + Compose + Room | Flutter + Drift | React Native + SQLite |
| --- | --- | --- | --- |
| 1 Reliability | 5 | 4 | 3 |
| 2 Android delivery (alarms, notifications, platform APIs) | 5 | 3 | 3 |
| 3 Development speed | 4 | 4 | 4 |
| 4 Maintainability | 5 | 4 | 3 |
| 5 Offline-first | 5 | 5 | 4 |
| 6 Testing (pure engine tests, migration tests) | 5 | 4 | 3 |
| 7 Simplicity for you (one language, one build tool) | 5 | 4 | 3 |
| 8 Future extensibility (iOS later) | 4 | 5 | 4 |
| 9 AI integration (on-device Gemini Nano is an Android library) | 5 | 3 | 3 |
| 10 Zero infrastructure cost | 5 | 5 | 5 |
| **Total** | **48** | **41** | **35** |

The one area where Flutter wins, iOS, is a V1 non-goal; if iOS is ever wanted, the pure-Kotlin engine and Room 3 both support Kotlin Multiplatform, so the training logic could be reused.

### Components

| Layer | Choice | Why |
| --- | --- | --- |
| Language | Kotlin | Android's primary language |
| UI | Jetpack Compose + Material 3 | Declarative UI, dark theme, large-numeral layouts, accessibility built in |
| Database | Room 3 (SQLite) | Stable major release (3.0.3, Sept 2026); typed queries; migration test helper |
| Async | Kotlin coroutines and Flow | Standard; testable |
| Serialisation | kotlinx.serialization | Registry, library, backup files |
| Navigation | Navigation Compose | Standard; simple |
| Dependency wiring | A small hand-written container, no framework | Fewer moving parts (A4.7) |
| Alerts | AlarmManager exact alarm + notifications | Section 11 |
| Tests | JUnit 5 + Kotest property tests (engine); Robolectric + Compose UI tests on the JVM; AndroidX instrumented tests on a CI emulator; Macrobenchmark for startup | Section 18 |
| Quality gates | Android Lint, ktlint, detekt; dependency allow-list; APK permission and size checks | NFR-04, NFR-07, NFR-10 |
| Build and delivery | Gradle wrapper + version catalog; GitHub Actions; signed release APK to GitHub Releases | F3 Option B (Section 1 audit) |

### Project modules

| Module | Kind | Contains |
| --- | --- | --- |
| `:engine` | Pure Kotlin (JVM) | Fitness engine, SafetyKernel, generated rule constants, bundled registry |
| `:coach` | Pure Kotlin (JVM) | Tier 1 templates, intent matcher, `LlmProvider` interface (no-op in V1) |
| `:data` | Android library | Room database, DAOs, exercise library loader, backup/export/restore |
| `:app` | Android application | Compose screens, view-models, Orchestrator, alarms and notifications |
| `:simulator` | Pure Kotlin (JVM) | Phase 4 personas and 12-month simulations, run in CI |

### Version policy

Latest stable versions are pinned in one version catalog at the start of Phase 3 and change only through change control. For reference, on 7 October 2026: Kotlin 2.4 (June 2026), Android Gradle Plugin 9.x, Compose 1.12.1 and Material 3 1.4.0 (September 2026), Room 3.0.3 (September 2026), Android 17 / API 37 (June 2026). Exact numbers are re-checked when Phase 3 starts.

## 16. Security and privacy architecture

The app asks for three permissions — notifications, exact alarms and vibration — and nothing that touches the internet, location, camera, contacts or files. Your data lives in the app's private storage, encrypted by Android itself, and leaves the phone only when you export it or through Android's own encrypted backup.

### Permissions

| Permission | Why | Asked at runtime? |
| --- | --- | --- |
| Post notifications | Rest-timer alert when the phone is locked | Yes, once (O9), with the reason |
| Use exact alarms | Rest ends on time even in battery-saving idle | No (granted at install) |
| Vibrate | Buzz at the end of rest and intervals | No |
| **Not requested** | Internet, location, camera, microphone, contacts, storage, body sensors, activity recognition | — |

Exports and restores use Android's file picker, which needs no storage permission.

### Threats and protections

| Threat | Protection |
| --- | --- |
| Lost or stolen phone | Screen lock + Android file-based encryption of app data (standard on phones launched with Android 10 or later) |
| Other apps reading your data | Android app sandbox; no exported data providers; only the launcher screen and an internal, non-exported alarm receiver are declared |
| Cloud backup exposure | Android backup is end-to-end encrypted with your screen lock (Android 9+); API keys excluded |
| A shared export file | Optional password encryption (section 12); the app warns before export that the file contains health information |
| Network leakage | No internet permission at all in V1 |
| Logs | No personal data in the on-device error log; developer logging stripped from release builds |
| Supply chain | Pinned dependency versions, an allow-list of libraries, Gradle wrapper checksum verification, CI actions pinned to exact versions |
| Signing key | One permanent release key, stored as encrypted GitHub secrets; you keep a backup copy (F2) |
| Future AI key | Encrypted with the Android Keystore; never in code, repository, APK, backups or exports |

### Decision: no extra database encryption layer in V1

Adding a second encryption layer (e.g. SQLCipher) with a key locked to the phone would make Android's backup unreadable after a restore to a new phone, and a lost key would mean lost history. Android already encrypts app data at rest, so V1 relies on that and keeps backups restorable. This trade-off is logged as decision D-023 and can be revisited if your threat model changes.

### Data deletion

"Erase all my data" (two-step confirm) deletes the database, settings, alarms and notifications. Uninstalling the app also removes everything except files you exported yourself.

## 17. System architecture

Everything that runs on your phone sits inside one app with no network access; the cloud is used only to build, test and publish the app, never to store your data.

&#91;embedded content: system architecture · phone app and cloud build\]

Arrows show who calls whom: screens talk only to the Orchestrator, which calls the engine and coach, writes the database, and sets rest alarms; the engine reads the rule registry and never touches storage.

## 18. Test strategy

Testing is layered so that most checks run in seconds on any computer: every one of the 140 rules gets at least two automated tests, safety limits are checked against thousands of random training histories, and the app is exercised end to end on an Android emulator in the cloud before you ever install it. A build that fails any gate never produces an APK.

| Level | What it proves | Tools | Runs |
| --- | --- | --- | --- |
| Rule tests | Each rule behaves as specified (`TC-<rule>a/b` from the registry) | JUnit 5 on `:engine` | Every push |
| Rule coverage | Every registry rule ID has ≥2 passing tests; no test cites an unknown rule | Script comparing registry and test names | Every push |
| Property tests | Invariants hold for random histories: weekly sets ≤ cap; HIIT ≤ ceiling and spaced; loads always real plate/dumbbell values; no RIR-0 on failure-unsafe lifts; pain regions never loaded; validator never passes a failing session | Kotest property testing | Every push |
| Golden tests | Fixed personas produce byte-identical sessions (determinism, NFR-14) | Snapshot files | Every push |
| Scenario suite | All 30 Phase 4 scenarios (E2), e.g. 30-min day, red flag, 3 missed sessions, "ignore my knee" | JUnit on `:engine` + `:coach` | Every push |
| Longitudinal simulation | 5 personas × 52 weeks: caps never exceeded, progression realistic, deloads trigger, balance holds | `:simulator` | Nightly and before each phase gate |
| Database | Schema export matches; every migration from every older version; no destructive fallback | Room migration test helper on emulator; text check | Every push (text check) / nightly (emulator) |
| Interruption & data integrity | Set never lost across call, background, lock, process death, reboot; export → erase → restore identical | Instrumented tests + scripted `adb` process kill on emulator | Nightly |
| UI | Core flows (check-in, log set, rest, replace, pain, summary); one-tap logging | Compose UI tests on Robolectric (JVM) + a smoke run on emulator | Every push / nightly |
| Accessibility | Labels, contrast, touch-target size, 200% font | Compose accessibility checks + manual TalkBack pass | Every push / UAT |
| Alarms & notifications | Rest alert fires with screen off and in idle | Instrumented test on emulator + your phone in UAT | Nightly / UAT |
| Performance | Cold start < 1.5 s; generation < 500 ms | Macrobenchmark (emulator), engine benchmark | Nightly |
| APK checks | No internet permission; only allowed libraries; size < 15 MB; signed with the release key; correct version | `aapt`, `apksigner`, size script | Every release build |
| UAT | You complete the 15-step checklist on your phone | Plain-language checklist (Phase 4, E7) | Phase 4 |

**CI pipeline (GitHub Actions):** every push → lint and static checks → engine, coach and JVM UI tests → rule-coverage check → debug APK uploaded. Nightly and on demand → emulator tests, simulations, benchmarks. Version tag → release build, signing, APK verification, publish to GitHub Releases.

**Coverage targets:** engine ≥ 90% lines; rules 100%; every P0/P1 bug gets a regression test before it is closed.

## 19. Acceptance criteria

Each V1 feature is accepted only when every criterion below passes in automated tests and, where marked UAT, on your phone.

| Feature | Acceptance criteria |
| --- | --- |
| F1 Onboarding | Given a fresh install, when all required answers are given, then a profile, screening result, equipment inventory and first week exist; median completion ≤ 5 min in UAT. A symptom answer always sets conservative mode. Killing the app mid-way resumes at the same step. |
| F2 Calibration | Given no working load for an exercise, the session ramps by your effort answers; no set is prescribed above RPE 8 and none to failure; after ≤5 ramp sets a working load and starting e1RM exist. |
| F3 Readiness check | Completes in ≤ 15 s (UAT). All-normal ratings give FULL; sleep < 4 h gives at most LIGHT; any red flag ends in SAFETY STOP; the tier shows a one-line reason with "Why?". Choosing an easier tier always works; a harder tier is one step at most, with a warning. |
| F4 Workout generation | Every generated session passes the validator; generation < 500 ms; same inputs give the same session; excluded modalities never appear. |
| F5 Guided workout | Each exercise shows target sets, reps, load (real equipment values), RIR, rest, cues, last performance and warm-up sets for main lifts; the screen stays on. |
| F6 Set logging | "Done as planned" saves the set in one tap within 100 ms; adjusting load or reps uses steppers (no keyboard); the set survives an immediate force-close. |
| F7 Rest timer | Starts automatically after a set; with the phone locked it vibrates and sounds within 1 s of the end (UAT); ±30 s and skip work; logging the next set cancels the alert. |
| F8 Replace / occupied | Returns 3 options in < 300 ms, each with a one-line reason, all matching today's equipment and pain limits; "do it later" moves the exercise later in the session. |
| F9 Something hurts / red flags | Reachable in one tap on every workout screen; 4–6/10 joint pain stops the exercise and offers pain-free swaps; ≥7/10 or red-flag descriptors stop loading the region or the session; a red flag never yields a lighter workout. |
| F10 Time adaptation | For 30, 40, 60, 75 and 90 min, the fitted session's estimated time is within ±10% of the budget, keeps the warm-up and the primary lift, and lists what moved where. |
| F11 Summary | Asks one 0–10 effort question; saves session load; shows records, "what changed today" and the next session. |
| F12 History & progress | Every completed set appears in history; edits recompute e1RM and records and are logged; weekly sets per muscle and WHO minutes match the engine's numbers exactly. |
| F13 Coach (Tier 1) | Each of the 12 suggested questions gets a correct, plain answer citing a real decision or rule; unknown questions offer 3 alternatives; works in flight mode. |
| F14 Backup | Export → erase → restore reproduces identical data; corrupt or newer files are refused with nothing changed; the reminder appears after 8 workouts when the last export is > 30 days old. |
| F15 Streaks | A missed week never resets the best streak; one freeze per 4 weeks applies automatically. |
| F16 Settings | Units switch kg ↔ lb everywhere without changing stored values; "Erase all my data" needs two confirmations and returns to first launch. |
| All | No internet permission in the APK; no crash in the full 30-scenario suite; TalkBack can complete a set log; text readable at 200% font size. |

## Phase 2 gate report — Product & Architecture sign-off

All 19 Phase 2 deliverables are complete as a specification, with five drawings; no app code exists yet. The main decisions are a native Android app (Kotlin, Compose, Room), a fully offline V1 with no internet permission, an offline explanation coach with conversational AI switched off, and rest alerts built on system alarms rather than a background service.

**1. Completed work.** Phase 1 carry-overs closed (registry patch 1.0.1, citation only); PRD; 10 functional pipelines; 16 non-functional requirements; 4 user journeys; 27-screen inventory, navigation map and 6 wireframes; state machine; 26-table data model; engine architecture; AI, agent and LLM-boundary design; safety architecture; offline, backup and restore design; stack decision; security design; system diagram; test strategy; acceptance criteria.

**2. Key decisions** (logged D-019 to D-027):

| ID | Decision |
| --- | --- |
| D-019 | Stack: Kotlin + Jetpack Compose + Room 3 (scored 48/50) |
| D-020 | V1 coach is Tier 1 only (offline, deterministic); Tier 2 AI built behind a switch, off |
| D-021 | No internet permission in V1 |
| D-022 | Rest alerts via exact system alarms + notifications, not a foreground service |
| D-023 | No extra database encryption layer; rely on Android encryption so backups stay restorable |
| D-024 | Minimum Android 10 (API 29) — ASSUMPTION until your phone model is known |
| D-025 | Hand-written dependency wiring, no framework |
| D-026 | Backup password optional, off by default |
| D-027 | Registry patch 1.0.1: ACSM 2026 citation corrected and rest-interval evidence added; no parameter changes |

**3. Assumptions.** Your phone is a mid-range model from 2022 or later running Android 10+; the app is installed from GitHub Releases, not the Play Store; one user; English; kilograms by default.

**4. Open issues** (none blocks sign-off):

- Your phone model and Android version (Settings → About phone) — confirms the minimum Android version and on-device AI eligibility.
- Android backup's end-to-end encryption to be confirmed on a real device in Phase 4.
- ML Kit Prompt API maturity to be re-checked if you ever want Tier 2.
- Exact library versions are pinned when Phase 3 starts.

**5. Risks.**

| Risk | Mitigation |
| --- | --- |
| Some phone brands aggressively restrict background activity, which could delay rest alerts | Exact alarms are the most protected mechanism; tested on your phone in UAT; a one-screen guide to exempt the app if needed |
| Room 3 is a newer major version | Same annotations as Room 2; fallback to Room 2.8 is a small change |
| Free cloud build minutes and emulator time | Most tests run on the JVM; emulator tests nightly, not on every push |
| Losing the signing key blocks updates | Key stored as encrypted GitHub secrets plus your own backup copy (F2) |
| Expert-practice rules may need tuning | Phase 4 simulations and your feedback; changes go through rule versioning |

**6. Deliverables and status.**

| Deliverable | Section | Status |
| --- | --- | --- |
| 1 PRD | 1 | Complete |
| 2 Functional specifications | 2 | Complete |
| 3 Non-functional requirements | 3 | Complete; targets measurable, NOT STARTED (verified in Phase 4) |
| 4 User journeys | 4 | Complete |
| 5 Screens, navigation map, wireframes | 5 | Complete (low fidelity); REQUIRES your review |
| 6 State machine | 6 | Complete |
| 7 Data model & schema | 7 | Complete as design; schema code in Phase 3 |
| 8 Fitness engine architecture | 8 | Complete as design |
| 9 AI architecture | 9 | Complete as design |
| 10 Safety architecture | 10 | Complete as design |
| 11 Offline architecture | 11 | Complete as design |
| 12 Backup / export / restore | 12 | Complete as design |
| 13 Stack decision | 13 | Complete; versions pinned in Phase 3 |
| 14 Agent protocol | 14 | Complete as design |
| 15 API / LLM boundary | 15 | Complete as design (Tier 2 off) |
| 16 Security / privacy | 16 | Complete as design |
| 17 System architecture diagram | 17 | Complete |
| 18 Test strategy | 18 | Complete; tests NOT STARTED |
| 19 Acceptance criteria | 19 | Complete |

**7. Validation status.** Design only: nothing is implemented or tested yet. Platform facts (alarm and foreground-service rules, library versions) were checked against Android documentation on 7 October 2026; the registry patch passed the registry checks (0 failures).

**Before Phase 3 starts** you'll need a GitHub account (an existing one is fine); I'll send step-by-step instructions on approval.

```text
================================================================================
PHASE 2 SIGN-OFF GATE
Please review the complete Phase 2 Product Specification & System Architecture.
Reply 'APPROVED' to advance to Phase 3 (Application Development),
or give feedback / adjustments.
================================================================================
```

## Sources

Checked 7 October 2026. *Opened* = page read this session; *Located* = found in search results, not opened.

- [Currier BS, et al. ACSM Position Stand: Resistance training prescription for muscle function, hypertrophy, and physical performance in healthy adults. Med Sci Sports Exerc. 2026;58(4):851–872](https://doi.org/10.1249/MSS.0000000000003897) — Opened (full text)
- [Android 14: foreground service types are required](https://developer.android.com/about/versions/14/changes/fgs-types-required) — Opened
- [Android: Schedule alarms](https://developer.android.com/training/scheduling/alarms) — Opened
- [Room 3 release notes](https://developer.android.com/jetpack/androidx/releases/room3) — Opened
- [Jetpack Compose release notes](https://developer.android.com/jetpack/androidx/releases/compose) — Opened
- [Android Gradle Plugin migration timeline](https://developer.android.com/build/releases/gradle-plugin-roadmap) — Opened
- [ML Kit's Prompt API: on-device Gemini Nano](https://developer.android.com/blog/posts/ml-kit-s-prompt-api-unlock-custom-on-device-gemini-nano-experiences) — Opened
- [Kotlin 2.4.0 released (JetBrains blog)](https://blog.jetbrains.com/kotlin/2026/06/kotlin-2-4-0-released/) — Located
- [Android 17](https://en.wikipedia.org/wiki/Android_17) — Located
- [Android file-based encryption](https://source.android.com/docs/security/encryption/file-based) — Located
- [Pelland et al. 2024 preprint, version 2](https://sportrxiv.org/index.php/server/preprint/view/460) — Located (still a preprint)
- Android Auto Backup documentation (quota and end-to-end encryption) — not reachable from this environment; facts from prior knowledge, to be confirmed on a device in Phase 4
- Phase 1 sources: see the Phase 1 report
