# Phase 4, Part 1 report: weekly limits that follow how often you train

Date: REPORT_DATE · Proposal: Rule Registry 2.0.0 (MAJOR), changing VOL-007 and LOAD-005 · Decisions D-110 to D-112 · **Waiting for your approval**

## Summary
Two of the app's safety rules stop training from growing too fast:
- **VOL-007** limits the week's total training stress.
- **LOAD-005** limits the week's effort-load.

Both compare this week with your last three weeks. At the Phase 3 gate they held weeks at about half to four-fifths of the plan. You chose to tune them as the first task of Phase 4.

**What the year-long tests showed.** I simulated a full year of training for eight test people through the app's own data layer and engine. Each person was tried three ways: every session done; a missed week plus a week cut short by illness; and about 4 sessions in 5, missed at random. The problem was worse than the Phase 3 journeys suggested. A missed session made its week look light, and the next week's limit followed it down. Anyone missing about 1 session in 5 was down to recovery days within 2–3 months: 82 to 125 empty days a year. The bodyweight-only person fell to recovery days even when training every time.

**The proposed fix** keeps the limits' purpose: stopping sudden jumps. It stops them shrinking your training when you miss sessions. With it, six of the eight test people keep 93–100% of their planned training in every pattern, and nobody gets a recovery day from the limits. No week went above twice the three weeks before it.

**What needs your decision.** The fix changes where a safety limit applies, so it is a MAJOR rule change (Registry 2.0.0). Under the research-update workflow only you can approve it (D-106). The code is ready and tested on a separate branch and isn't in the app until you approve.

## What changes, in plain words

1. **Missed sessions no longer shrink your limit.** A past week is counted at the level of the sessions you did. If you did 2 of 3 sessions, the week counts as if all 3 had been like those 2. A week with fewer than half its sessions done is left out, as a fully missed week already is.
2. **A weight still being found counts as it is done.** When the app is finding your working weight, that exercise counts as two sets at the target effort, in the plan and once done. Before, the easy ramp-up sets counted nothing, so the first weeks looked light and set a low starting point.
3. **The week is planned within the limits.** The week planner now fits the whole week inside the limits before you start. Sessions are no longer cut on the day, and "kept under your weekly load limit" no longer shows on most days. When something must give, it goes in this order:
   - other exercises' sets;
   - steady cardio, shortened by at most 40% (never below 10 minutes) or made easy;
   - main lifts, last.
4. **Cardio is made easy before it is dropped.** When a day's cardio would have to lose more than 40% to fit, it becomes easy cardio instead of disappearing.
5. **A floor.** The limits never cut a week below half of its planned training stress. Half is about the stress of a deload week (DEL-003).
6. **A spike guard.** Above the floor, a week can never be more than twice the average of your last three weeks as you actually did them, missed weeks counting as zero. It starts three weeks after your first session. It doesn't apply during a return ramp or a deload, which have their own rules.

The two numbers that set how fast training may grow stay the same: 15% for VOL-007 and 20% for LOAD-005.

## The year-long results

The share of the planned training kept over the year, and the days emptied by the limits ("recovery days"), before → after:

| Test person | Every session | A missed week + illness | About 4 in 5 sessions |
|---|---|---|---|
| P1 — like you (intermediate, 3 × 60 min, gym without treadmill running, bikes or stairs, fat loss) | 74% → 96% | 55% → 94% | 9% → 94%; 82 → 0 recovery days |
| P2 — returning beginner | 94% → 98% | 86% → 97% | 8% → 93%; 92 → 0 |
| P3 — time-crunched, 4 × 35 min | 96% → 98% | 88% → 97% | 19% → 93%; 83 → 0 |
| P5 — poor sleeper | 70% → 66% | 63% → 72% | 6% → 63%; 90 → 0 |
| Advanced lifter, 4 days | 76% → 94% | 63% → 93% | 4% → 94%; 125 → 0 |
| Beginner 66, type 2 diabetes and knee osteoarthritis | 96% → 100% | 92% → 99% | 14% → 100%; 86 → 0 |
| Fat loss 52, high blood pressure | 94% → 98% | 82% → 97% | 4% → 95%; 104 → 0 |
| Bodyweight only, 35, at home | 12% → 84%; 94 → 0 | 12% → 80%; 101 → 0 | 6% → 81%; 100 → 0 |

How to read it:
- **The poor sleeper** keeps about two-thirds by design. Days after a short night are planned lighter by the readiness check (RDY rules), not by these limits.
- **The bodyweight-only person** keeps about four-fifths. Their cardio circuits are heavy in training-stress terms, so some weeks are still trimmed. Nothing is emptied.
- **Strength still grows.** For the person like you, the main lifts' estimated maximum rises by about 13–14% over the year in every pattern. Before, it was only +4% when 1 session in 5 was missed.
- **Sessions cut by the limits on the day** fell from about two-thirds of all sessions to a few per year for most people. The planner now fits the week in advance.

## What gets looser, and why it stays safe
The fix makes the limits less strict in three ways:
- after missed sessions, a week can go back to its planned size faster than +15% a week;
- the limits never cut a week below half its plan;
- a weight being found counts as two sets.

**What keeps it safe:**
- **Session size is unchanged.** The programme and its progression rules set the size of each session, never these limits. A session is never bigger than it would have been.
- **The new spike guard bounds the loosening.** In the simulations no week went above 2× the three weeks before it; the largest was 1.98×. With every session done, no week went above 1.5×.
- **Everything else stays as it was:**
  - the ceilings for each level;
  - the weekly set limit per muscle (SAF-005);
  - interval limits and spacing;
  - the return-to-training ramps after 7 or more days off (REG-003/004/005);
  - deloads;
  - the readiness check;
  - the pain gate and warning signs;
  - the soft "workload is high" flag (LOAD-004).
- **Every session still passes the full safety check.** In all simulations, every session shown passed the full validator.

**What remains.** Someone who trained very little for three weeks can get up to half of the planned week at once (the floor). Each session is still its normal size, and after 7 or more days off the return ramp applies.

## Independent review
REVIEW_SECTION

## How it was checked
TESTS_SECTION

## Your decision
Please approve Registry 2.0.0, or tell me what to change. On approval I will:
1. move the change set into the approved history and apply it with the research-update tool;
2. move the new numbers into the rule registry;
3. merge the branch into the app, then run CI and the device tests;
4. report back, and go on to Phase 4 Part 2 (the 30 test scenarios).

If you don't approve, the app keeps today's limits. Please still don't train with the test build, because they shrink training when sessions are missed.
