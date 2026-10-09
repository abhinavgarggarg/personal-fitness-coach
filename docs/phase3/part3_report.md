# Phase 3 — Part 3 report: Research Update 1.1 in the engine (fat loss for 30+, health conditions, steps, your own numbers)

Date: 9 October 2026 · Rule Registry 1.1.1 · Health-condition table 1.0.1 · Library 1.1.1 · Decisions D-066 to D-072

## In one paragraph
The engine now follows everything you approved in Research Update 1.1:
- **The default plan for adults 30+ is "Lose fat, keep muscle"**, and it changes with age. Strength stays at 3 days a week. Hard cardio shrinks and becomes joint-friendly from 50. Balance work and light, fast "power" work grow from 50.
- **Weekly activity and steps:** a weekly activity target that never drops below 150 minutes, a step target that grows from your own first week, and brisk walks counted once.
- **Health conditions:** you can tick any of 21 conditions, and the strictest limit of each kind always wins. The limits apply in the weekly plan, in each day's session and in the final safety check.
- **Your own numbers:** you can start from a weight you lift now or your best lift.
- **Smaller features:** a calm mobility session, a one-tap "training away from the gym" day, a planning prompt and a "welcome back" badge.
- **Cardio machines:** treadmills and bikes are now a per-person choice; your own profile still excludes them.

An independent reviewer found 15 problems, 6 of them serious. All are fixed with their own tests, and a second check of the fixes found five more, also fixed. **406 automated tests pass.** A simulated year for five fat-loss users aged 35 to 70, each with a condition, broke no rule, and every one of them got stronger.

## What was built

| Area | Rules | Status |
|---|---|---|
| Rule Registry 1.1.0 applied by the registry script: 12 new rules, 10 changed; treadmills, bikes and stair machines became per-person choices; then 1.1.1, a table-reference patch from the review (D-066, D-069) | all of Research Update 1.1 | VERIFIED (registry checks, CI) |
| Exercise library 1.1.1: 14 condition-profile safety tags, enforced by the library generator; "any of" equipment (a step-up works with a box, a bench or a step); 5 low-impact power exercises for dumbbell-only and bodyweight gyms; 5 supported balance drills; bodyweight circuit moves (8 no-jump, 2 jumping); the 5 cardio machines (D-063, D-067) | SAF-010, EQ-003, MOD-001/002, D-063 | VERIFIED |
| Fat-loss goal: default from 30, every goal still selectable, a weight-features off switch, the realistic expectation shown; not offered in pregnancy or during cancer treatment | FL-001 | VERIFIED |
| Weekly activity target by age (200–300, 180–250, 150–250 minutes), growing ≤ 15% a week (12% at 65+), never below 150; brisk walks close the gap | FL-002, PH-001, CON-006 | VERIFIED |
| The mix by age band (table below) and the fat-loss year (Conditioning II, low-impact Athletic block) | FL-003, FL-004, AGE-001 | VERIFIED |
| Progress without food tracking: 7-day average weight, 4-week trend, the 1 kg/week check-in, waist averaging and schedule, and a strength trend from your lifts | FL-005 | VERIFIED |
| Steps: target from your first 7 valid days, weekly rises only after 5 of 7 days met, holds and step-downs; brisk-walk bouts (10+ min at 100+ steps/min) counted once | STEP-001, STEP-002 | VERIFIED (engine); the phone's step sensor arrives with the data layer |
| Health conditions: the table compiled into the app, phases, control status, doctor's-OK scopes, unlocks, sub-flags, "strictest wins", and every limit applied by the planner, session generator and final safety check | SAF-010 | VERIFIED |
| Starting from your own numbers (recent set or best lift; reduced if old; a ceiling if very old) | CAL-002, CAL-001 | VERIFIED |
| 60+: strength volume never drops to "maintenance" | AGE-001 1.1.0 | VERIFIED |
| Shorter-range sets when pain or a condition limits range, keeping the stretched end | MOB-004 1.1.0 | VERIFIED |
| Habits: "welcome back" badge with one neutral nudge; habit messages give a range ("about two months"); optional 30-second planning prompt with a backup | ADH-002, ADH-004, ADH-005 | VERIFIED |
| Calm 15–30-minute mobility session (rest, light and high-stress days) and the away-from-gym day (bodyweight only by default, no-jump circuits) | MOB-006, EQ-003 | VERIFIED |

**What changes with age (the fat-loss plan):**

| | 30–39 | 40–49 | 50–59 | 60–64 | 65+ |
|---|---|---|---|---|---|
| Strength days | 3 (min 2) | 3 (min 2) | 3 + power slot | 3 + power slot | 3 combined balance-and-strength days |
| Tempo cardio (min/week) | 20–40 | 20–40 | 15–30 | 0–20 | 0–15 |
| Interval sessions | 2 | 1, low-impact preferred | 1, low-impact only | offered after 6 weeks | offered after 8 weeks if you were already active |
| Balance (min/week) | — | — | 10 | 20 | 30 |
| Daily steps target | 9,000 | 9,000 | 8,000 | 7,500 | 7,000 |

Rule coverage: **149 of 152 rules** have all their registry test cases (`docs/phase3/rule_coverage_part3.md`). The remaining three belong to later parts:
- DATA-001 (what is stored): the data layer, now Part 4.
- SAF-009 and COACH-001 (the coach's boundaries and voice): Part 6.

## How it was checked
- **406 tests, all passing** locally (Gradle's bundled Kotlin 2.0.21). GitHub's build, with Kotlin 2.4.0, the app tests, the debug APK and the permission check, was green for every Part 3 commit.
- **Property tests over random users:** any age 30–79, level, days, gym, injuries, and 0–3 random conditions with random answers.
  - Every planned week and every generated session keeps the merged condition limits and passes the safety check.
  - Checked over 10 seeds × 500 cases, plus a session-level check of warm-ups, positions, breastbone and impact limits over 5 seeds × 300 condition sets.
- **A 52-week simulation for one fat-loss user per age band:**

| Simulated user | Sessions | Result |
|---|---|---|
| 35, intermediate, 3 days, full gym | 156 | Weekly target 150 → 200 by week 4; 34 interval sessions; 4 of 4 tracked lifts stronger |
| 45, beginner, home dumbbells, controlled high blood pressure, misses half of every 8th week | 196 | Target reaches 200 and never drops below 150 after short weeks; 4 of 5 lifts stronger |
| 55, intermediate, knee arthritis | 208 | Intervals on low-impact machines only; knee limits held; 3 of 4 lifts stronger |
| 62, beginner, machines only, type 2 diabetes and obesity | 156 | Never strength on consecutive days; no intervals (not accepted); 5 of 5 lifts stronger |
| 70, beginner, 2 days, machines, osteoporosis | 104 | Bone-loading block, balance and back-extensor work; no intervals; 4 of 4 lifts stronger |

The general simulation's 68-year-old machine user went from no tracked lift to 4 of 4 improving, thanks to the calibration fix below.

## Independent review
A separate reviewer, who had not written this code, tested Part 3 by running each scenario. It found **6 high, 7 medium and 2 low** issues. All are fixed, each with a test (`Part3ReviewRegressionTest`, R3-01 to R3-15). The reviewer then re-checked the fixes: 14 were confirmed fixed, 1 partly. It found five more (1 medium, 4 low), which are now also fixed and tested (R3-16 to R3-20).

| # | What was wrong | Fix |
|---|---|---|
| 1 (high) | After breastbone surgery, presses, rows and push presses were still planned | Nothing that loads the arms, shoulders or chest, in exercises or drills, and no rower, SkiErg, air bike or sled, until your surgical team clears you |
| 2 (high) | A condition's extra warm-up (for example +10 minutes for a heart condition) was cut when time was short | It is part of the minimum and is never cut; the final check restores it; an express session that then needs more than 30 minutes says so and offers a walk |
| 3 (high) | Pregnancy with no week entered allowed lying on the back | An unknown week counts as past week 20 |
| 4 (high) | "Diabetes with numb feet" on its own dropped the diabetes limits | It brings type 1 and type 2 limits until you say which |
| 5 (high) | After giving birth, skipping the "healed" confirmation unlocked harder work at 12 weeks | The confirmation gates every later phase |
| 6 (high) | Type 2 diabetes could get strength three days in a row, lose walks in lighter weeks, and get strength the day after strength when a session moved | Strength never on consecutive days (a hard rule, also Sunday to Monday); walks kept; moved sessions skip strength after strength |
| 7–8 | An unknown condition name dropped all its limits; "not sure" blood pressure kept the "controlled" limits | Unknown names give the cautious mode and ask you to pick again; the status answer decides |
| 9–11 | No-jump rules for 50+ or obesity didn't reach home sessions; at home a rower could be prescribed; the bone-loading hops ignored knee pain | All three reach the session and the final check; at home only bodyweight cardio; hops become heel drops or are left out |
| 12 | A short week pulled the activity target down to 115 minutes for a month | Never below 150 (D-068) |
| 13 | Machine-only users started calibration at 5 kg and stayed far too light for about 20 weeks | On a machine stack the next plate is taken when it feels very easy (D-070); a strength trend works without an estimated max |
| 14–15 | Smaller items (one-band rule for fresh entries, 65+ cardio growth cap, habit wording, defaults that failed open, missing drill tags, test gaps) | Fixed and tested |

## What this means for you
- **No change for your own profile:** treadmills, stationary bikes and stair machines stay excluded for you.
- **Where condition limits pull against targets, safety wins:**
  - With only two back-to-back days a week, type 2 diabetes gets one strength day, and the plan asks you to add a day with a gap.
  - A heart condition's longer warm-up makes the express session about 34 minutes.
- **Breastbone surgery** is encoded strictly: no upper-body loading at all until you say your surgical team has cleared you. That is stricter than "no heavy pushing or pulling", on purpose.

## Known limits
- **Steps** are counted by the engine, but the phone's step sensor, its permission and storage come with the data layer (Part 4).
- **The away-from-gym dumbbell user in the general simulation still never gets an estimated max.** The simulated user always does the top of the rep range, so no set falls in the valid window for estimating one. Real logged sets will. Part 4 records the load calibration actually found and every logged set.
- **Before any public release:** the condition table and its wording still need a clinician and a regulatory review (D-058).

## Phase 3 plan (now 7 parts)

| Part | Content | Status |
|---|---|---|
| 1 | Engine core, safety kernel, project foundation | Done |
| 2 | Exercise library, 12-month programme, week planner, session generator | Done |
| 3 | Research Update 1.1 in the engine | **Done (this report)** |
| 4 | Data layer: storage seeded from the library and condition table; history → estimated max, progression, weekly totals; weight, waist, steps (sensor and permission); your own numbers; backup and restore | Next |
| 5 | Screens: onboarding (goals, conditions, doctor's-OK scopes, your numbers), today, session player, rest timer | — |
| 6 | History and progress, coach (SAF-009, COACH-001), Ask Gemini share, settings, exercise animations (D-061) | — |
| 7 | Integration, research-update workflow, Phase 3 gate (152/152 rules) | — |

```
================================================================================
PHASE 3 — PART 3 OF 7 COMPLETE. Reply CONTINUE.
================================================================================
```
