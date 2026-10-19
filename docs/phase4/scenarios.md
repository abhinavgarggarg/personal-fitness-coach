# Phase 4 scenario suite (E2): the 30 scenarios

Phase 4 Part 2 (D-111, D-114). The master prompt's own list of 30 scenarios wasn't available, so the Product Owner asked Claude to write them (19 Oct 2026). They follow:
- the approved Phase 2 user journeys (J1–J4);
- the test strategy (section 18) and acceptance criteria (section 19);
- the scenario numbers already fixed earlier:
  - 29 is "ignore my knee, make it harder" (Phase 2, section 14);
  - 30 is the upgrade over existing data (Phase 2, section 7). Phase 1's open question 15 also named scenarios 9 and 30 for return-to-training and age. Here scenario 9 covers both, and scenario 30 keeps the Phase 2 meaning.

Each scenario runs as an automated test through the app's own controller — the same calls the screens make — on the real data layer and engine. It runs on every push: `app/src/test/.../flow/ScenarioSuiteTest.kt`, test names `S01` to `S30`.

The **Rules** column names the main rules each scenario exercises.

## First days

| # | Scenario | Expected | Rules |
|---|---|---|---|
| S01 | **First day** (J1): a 41-year-old intermediate lifter in a full gym installs the app, answers onboarding, checks in normally and does the first session | A profile, a first week and today's session. FULL tier. Loaded exercises without a known weight start with a ramp to find it. Every set logged is saved; the summary asks for one effort rating and Today shows the day as done | F1, F2, F11, CAL-001, GEN-001 |
| S02 | **A warning sign in screening**: chest discomfort on exertion is answered "yes" | Gentle mode: no intervals or hard cardio in the plan, a "check with your doctor" line on Today. A doctor's OK recorded in Settings ends gentle mode | SAF-001, D-056 |
| S03 | **Onboarding interrupted**: the app is closed after three steps | Opened again, onboarding is at the same step with the answers kept | F1 |
| S04 | **Two health conditions**: a 66-year-old at home with type 2 diabetes and knee osteoarthritis | No jumping or impact work all week. Strength days aren't on consecutive days. The condition prompts are shown with the session. Balance work is in the plan | SAF-010, AGE-001, FL-003 |

## Everyday training

| # | Scenario | Expected | Rules |
|---|---|---|---|
| S05 | **A normal gym day, week after week** (J2): every session as planned for five weeks, targets met | Once the weights are found, a main lift's target load or reps goes up | PROG-001, PROG-002 |
| S06 | **A bad night** (J3): 4.5 hours' sleep | The day is MODIFIED at most, with the reason. The user may choose easier, never more than one step harder | RDY-005, RDY-007 |
| S07 | **A red flag at the check-in**: chest pain | Training stops at once. The stop stays until the user confirms it was checked. The first session back is LIGHT | SAF-002 |
| S08 | **Illness**: a fever at the check-in | No training that day. The first session after it is lighter and says why | SAF-007, REG-005 |
| S09 | **Back after three weeks off at 66** | "Welcome back". The first session back is lighter than before the break, with the return reason | REG-003, REG-004, AGE-001 |
| S10 | **Three missed sessions** (J4): a whole week skipped | "Welcome back", not guilt. The best streak is kept. The plan carries on from the next session | SCH-003, ADH-001, ADH-002 |
| S11 | **Turning up two sessions in three for six weeks** | The weekly limits don't fall with the missed sessions: each week keeps about the same share of the plan as for someone who turns up every time, and at least 90% of it from week 4 (Registry 2.0.0) | VOL-007, LOAD-005 |

## Time

| # | Scenario | Expected | Rules |
|---|---|---|---|
| S12 | **Only 30 minutes today** | The session takes at most 33 minutes and keeps the warm-up and the first main lift, or the express session is offered with the reason | TIME-001, TIME-002, ADH-004, F10 |
| S13 | **"I only have 15 minutes left"** mid-workout | What is left is fitted again into the 15 minutes (or the shortest safe rest is shown). The warm-up already done, the cool-down and the logged sets stay | TIME-001, TIME-002, D-090 |
| S14 | **90 minutes on a 60-minute plan** | The session stays the planned length: extra time isn't filled with extra training (TIME-003 extension is off, an open decision) | TIME-003 |

## In the workout

| # | Scenario | Expected | Rules |
|---|---|---|---|
| S15 | **The machine is busy** | Up to three replacements, each with a reason, all available in this gym. A swap changes the exercise; "do it later" moves it to the end | EQ-002, SUB-001, SUB-002, F8 |
| S16 | **Knee pain 5/10 mid-set** | That exercise stops. Pain-free alternatives are offered, and nothing loads the knee more than a little for the rest of the session | SAF-003 |
| S17 | **Sharp pain 8/10** | The region or the session stops. No exercise for that region is offered again that day | SAF-003, SAF-004 |
| S18 | **A warning sign during the workout**: fainting | The workout ends at once and the stop screen shows; nothing is left running | SAF-002, D-081 |
| S19 | **Finding a working weight** | Each ramp takes at most 5 sets and never asks for failure. Once a weight is found, the next session does working sets with it; a ramp still easy after 5 sets goes on next time, within the first 4 sessions | CAL-001, INT-003 |
| S20 | **A short day with a superset** | Two exercises that don't compete alternate their sets with a short rest. Skipping one ends the superset and the other gets its own rest | ORD-003, REST-004, D-109 |
| S21 | **The app is killed mid-workout** | Opened again, the workout is offered back exactly where it stopped, with every set saved | NFR-06, F6 |

## The coach and explanations

| # | Scenario | Expected | Rules |
|---|---|---|---|
| S22 | **"Why?" on today's session** | Each reason opens its rules, with their titles and how sure the evidence is, in plain words | SAF-009, COACH-001 |
| S23 | **Out-of-scope questions**: a supplement question, then "I have chest pain" | The supplement question is referred, not answered. Chest pain leads to the warning-sign stop | SAF-009, SAF-002, D-093 |
| S24 | **Ask Gemini** | The exact text is shown before anything is shared. A health question isn't shared at all; it's referred | D-045, D-094 |

## Progress and data

| # | Scenario | Expected | Rules |
|---|---|---|---|
| S25 | **Tired for days in a row**: hard sessions, poor check-ins and stalled lifts | A lighter week is planned, with about half the sets of the week it replaces, easier effort, and the reason | DEL-001, DEL-002, DEL-003 |
| S26 | **Six weeks of training** | Progress shows rising strength, weekly minutes against 150, and the weeks in a row | F12, PH-001, ADH-001 |
| S27 | **Correcting a past set** | The corrected set is saved, the estimated maximum follows, and impossible values (negative or 500 reps, 2000 kg, 12 in reserve) are refused with nothing changed | F12, D-096 |
| S28 | **Backup, erase, restore**, with a password | Everything comes back. A wrong password or a damaged file is refused with nothing changed | F14, D-075 |

## Edge cases

| # | Scenario | Expected | Rules |
|---|---|---|---|
| S29 | **"Ignore my knee, make it harder"**: knee pain at the check-in, then asking for a harder level and telling the coach to ignore the knee | The pain limits stay. No harder level than the rules allow. The coach explains that the rules set the level (it can only open the preview), and no knee-loading exercise is offered, not even as a swap | SAF-003, RDY-007, SAF-009 |
| S30 | **Upgrade over existing data**: history saved by the earlier version, before Registry 2.0.0 | It is read in full, with nothing lost. The weekly limits come out the same as for someone who had the new version all along, and new sessions are saved alongside it | DATA-001, D-073, D-113 |
