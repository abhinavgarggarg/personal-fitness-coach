# Phase 3 — Part 5 report: the screens (onboarding, today, the workout, settings)

Date: 10 October 2026 · Rule Registry 1.1.1 · Health-condition table 1.0.1 · Library 1.1.1 · Decisions D-083 to D-090

## In one paragraph
The app can now be used from start to finish:
- **Onboarding (11 steps):** health questions, conditions, goal, week, gym, injuries and your own numbers.
- **Today:** what to do now, with a quick check-in and a preview of the session.
- **The workout:** big numbers and one-tap logging, a rest timer that buzzes when the phone is locked, and three buttons always at the bottom (something hurts, warning signs, change time).
- **The end:** a summary, plus a calm stop screen if a warning sign is reported.
- **Settings:** steps, backup and restore, erase, and changing your health answers at any time.

An independent reviewer found 26 problems: 1 critical, 3 high, 8 medium and 14 low. All are fixed with tests. A second check of those fixes found 8 more (1 high, 4 medium, 3 low), also all fixed and tested. **589 automated tests pass on GitHub**, and the device tests pass on five phone and tablet set-ups. Every screen is rendered as a picture in `docs/phase3/screens/`, and all 28 are on one page, "Personal Fitness Coach Screens" (https://claude.ai/artifact/ENfS9tFet1rQ21x4xHRXsY), so they can be looked at without a phone.

## What was built

| Area | Rules | Status |
|---|---|---|
| **Onboarding** (11 steps): welcome and "not a medical device"; the 8 health questions; age (adults only) and experience; health conditions with their follow-up questions and doctor's-OK scopes; goal with the realistic expectation; days and session length; gym equipment with real plate and dumbbell steps; injuries; your own numbers; the year plan; alerts | SAF-001, SAF-010, EXP-001, FL-001, FREQ, PROG-003, IND-001, CAL-002, PER-005 | VERIFIED |
| **Today:** the week strip, anything that needs attention first (a safety stop, gentle mode and why, a back flare switch, the 12-month health re-check), the next session | SCH-002, SAF-001/002, ADH-002 | VERIFIED |
| **Check-in and preview:** sleep, energy, soreness, stress, time available, pain and how you feel (illness and warning signs) in under a minute. Pain that ends training gives no session today. The preview shows the level the check-in allows (never harder than the day's limits), plus time, an express option and the away-from-gym option | RDY-001…007, SAF-003, SAF-004, TIME-002, EQ-003 | VERIFIED |
| **The workout:** warm-up, then each set with big numbers and one tap to log it with the effort answer. It covers finding-your-weight sets, the rest countdown, the interval timer, Replace and "busy", and adding a set. Something hurts, warning signs and change time sit in a fixed bottom bar. Every re-plan passes the final safety check | FS-4…FS-7, INT-007, CAL-001, SUB-001/002, EQ-002, SAF-002/003/006/008, TIME-001…004 | VERIFIED |
| **Rest timer:** an exact alarm at the end of each rest that works with the phone locked, and a countdown notification while the app is hidden; nothing else runs in the background (D-084) | Phase 2 §11 | VERIFIED (alarm fires on Android 10, 12, 14 and 16 in the device tests) |
| **Summary and stop:** sets, minutes, new personal bests, what changes next time, the session rating at the right moment and the next session. Warning signs give a calm stop screen with the emergency number; training waits until you confirm | LOAD-001/002, ADH-002, SAF-002 | VERIFIED |
| **Settings:** steps (permission asked only when turned on); backup with an optional password typed twice; restore with a safety copy first; two-step erase; change health conditions (goals adjust if one is ruled out, D-086); answer the health questions again; doctor's OK per scope | D-062, D-075, SAF-001, SAF-010, FL-001 | VERIFIED |
| **Behind the screens:** taps handled one at a time and double taps ignored (D-083); session minutes that leave out pauses and idle gaps (D-085); every word in string resources and checked (D-088) | NFR-03, NFR-16, LOAD-001 | VERIFIED |

Rule coverage stays at **150 of 152 rules** (`docs/phase3/rule_coverage_part5.md`). The last two, SAF-009 and COACH-001 (the coach's limits and voice), come with the coach in Part 6.

## How it was checked
- **Locally:** 564 tests pass (engine 408, data core 132, app screen logic 24). The strings check passes (710 strings, all used, nothing hard-coded), and so does the reason-text check (157 reasons).
- **GitHub CI** (Kotlin 2.4.0, run 38079715387, green): **589 tests**, made up of:
  - engine 408;
  - data 149, including the real database;
  - app 32, including the screens on Robolectric: onboarding, a workout, a warning sign, erase, text at 200% and touch-target size.

  It also confirms the permission list (6 permissions, no internet) and builds the debug APK.
- **On five emulated devices** (Android 10 small phone, 12, 14 at 200% text, 16 phone, 16 tablet; Devices run 38079955034), 8 of 8 device tests pass on each. They cover:
  - a workout started from Today and logged into the real database;
  - the end-of-rest alarm going off;
  - an encrypted backup and restore;
  - erase leaving no trace;
  - the step counter staying silent without permission;
  - the app having no internet permission.

  One earlier run failed once on the Android 16 phone without any test reporting a failure. The same code then passed there, so it was most likely the emulator. The workflow now always reports each device test's result, pass or fail.
- **Screen pictures:** all 28 screens rendered on GitHub and checked by eye (D-089). The wording and layout mistakes found that way were fixed, the last ones being counts that read "1 weeks" or "1 sets".

## Independent review
A separate reviewer, who had not written this code, tested Part 5 against the approved design and the safety rules. It drove the app logic exactly as the screens do. It found **26 problems** (R5-01 to R5-26). All are fixed with tests (`Part5ReviewRegressionTest`, `Part5ReviewFlowTest`):

| # | What was wrong | Fix |
|---|---|---|
| R5-01 (critical) | Pain at the check-in bad enough to end training could still lead to a normal session | No session today; Today says why and hides Start |
| R5-02 (high) | Stopping a lift for knee pain left a treadmill or rower block that loads the knee | Conditioning that loads the painful joint moves to a joint-friendly machine or is dropped |
| R5-03 (high) | After "change time", the app could jump past the warm-up | It only moves on for work actually done |
| R5-04 (high) | Typing a much heavier weight raised the next set beyond the in-session limit | The next target never goes past the limit or the day's level |
| R5-05 to R5-08 (medium) | Session minutes included hours with the phone in a bag; cardio-only work could be thrown away; skipping after logged sets lost them; choosing a lift during the cardio block skipped stages | Each fixed; minutes follow D-085 |
| R5-09, R5-12 (medium) | Health answers could only be given once: no way to answer the health questions again, add a pregnancy later, report a back flare or say you want impact work | Settings: conditions editor and the health questions again (with a 12-month reminder); a back-flare switch on Today; the impact question asked |
| R5-10, R5-11 (medium) | A late second tap could log the next set; the safety buttons scrolled out of view | Taps name the set they are for; the safety buttons are fixed at the bottom |
| R5-13 to R5-26 (low) | Smaller items: rest timer on the phone's elapsed clock, not read aloud every second, kept when the phone turns; no logging without a weight; a notifications-off warning; the backup password typed twice; "Add a set" on finished exercises; the next session named on the summary; and others | Fixed and tested |

The reviewer then **re-checked the fixes**. 20 were fully fixed and 6 partly. It also found 8 new problems; all are now fixed (`Part5RecheckRegressionTest`, `Part5RecheckFlowTest`, `ActionsTest`), and the reviewer's own probes show the right behaviour:

| # | What was wrong | Fix |
|---|---|---|
| N4 (high) | **Backup and restore did nothing on a phone:** the file picker's answer arrived while the app's return-to-screen refresh was still running, and was dropped | Answers from the phone are never dropped (D-083). Related fix found while fixing: turning the phone while the picker was open lost the typed backup password, so the file would have been saved without it. The password now waits safely in memory and is wiped once used |
| N1, N5, N6 (medium) | Session time was wrong in three cases: a 30-minute ride after the lifts didn't count; a workout reopened the next day before the first tap stored 600 minutes; a long idle gap ended by Pause counted | Each screen gets its own allowance (its length + 10 minutes), measured before each tap changes anything (D-085) |
| N2 (medium) | Adding a pregnancy in Settings kept the fat-loss goal and weight tracking | Goals no longer offered are dropped, weight tracking switches off, and Settings says so (D-086) |
| N3, N7, N8 (low) | A pregnancy answer reloaded as a different answer; "change time" below a heart condition's longer warm-up gave no explanation; the summary could name a day already done | Stored as given (D-087); the shortest safe session is shown (D-090); done days are skipped |

## What this means for you
- **Your health answers can change at any time** (Settings → Health conditions). If a new condition rules out "Lose fat, keep muscle", your goal changes to general fitness, weight tracking switches off, and the app tells you. Choosing goals yourself comes with Settings in Part 6.
- **The type 2 diabetes medicine question isn't asked:** the advice on low blood sugar is shown to everyone with that condition instead, so no medicine information is stored (D-087).
- **Leaving the phone untouched for a long time doesn't inflate your training time.** A gap longer than the current screen allows (for example a 30-minute ride plus 10 minutes) is left out (D-085).
- **"Change time" can't cut a condition's longer warm-up or cool-down.** If you ask for less time than they need, the app says how long the shortest safe session is.

## Known limits
- Tested on emulators only. **Phase 4 (testing and UAT) puts the app on your Motorola Edge 50.**
- Not in Part 5 (they come in Part 6): history and progress charts, the coach and "Why?" screens, the Ask Gemini share, settings for goals, units and machines, and exercise animations.
- "Change time" can sometimes cut more than asked. When a whole block has to go to fit, for example a 12-minute walk, you may get 12 minutes when you asked for 20. It never gives you longer than asked, except for the safety floors.
- Before any public release, the condition table and its wording still need a clinician and a regulatory review (D-058).

## Phase 3 plan (7 parts)

| Part | Content | Status |
|---|---|---|
| 1 | Engine core, safety kernel, project foundation | Done |
| 2 | Exercise library, 12-month programme, week planner, session generator | Done |
| 3 | Research Update 1.1 in the engine | Done |
| 4 | Data layer: storage, history → engine, steps, own numbers, backup and restore | Done |
| 5 | Screens: onboarding, today, check-in, preview, workout and rest timer, summary, safety stop, settings | **Done (this report)** |
| 6 | History and progress, coach and "Why?" (SAF-009, COACH-001), Ask Gemini share, more settings (goals, units, machines), exercise animations (D-061) | Next |
| 7 | Integration, research-update workflow, Phase 3 gate (152/152 rules) | — |

```
================================================================================
PHASE 3 — PART 5 OF 7 COMPLETE. Reply CONTINUE.
================================================================================
```
