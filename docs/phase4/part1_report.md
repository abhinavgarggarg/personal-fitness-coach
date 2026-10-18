# Phase 4, Part 1 report: weekly limits that follow how often you train

Date: 18 October 2026 · Proposal: Rule Registry 2.0.0 (MAJOR), changing VOL-007 and LOAD-005 · Decisions D-110 to D-112 · **Waiting for your approval**

## Summary
Two of the app's safety rules stop training from growing too fast:
- **VOL-007** limits the week's total training stress.
- **LOAD-005** limits the week's effort-load.

Both compare this week with your last three weeks. At the Phase 3 gate they held weeks at about half to four-fifths of the plan. You chose to tune them as the first task of Phase 4.

**What the year-long tests showed.** I simulated a full year of training for eight test people through the app's own data layer and engine. Each person was tried in several ways, including every session done and about 4 sessions in 5, missed at random. The problem was worse than the Phase 3 journeys suggested. A missed session made its week look light, and the next week's limit followed it down. Anyone missing about 1 session in 5 was down to recovery days within 2–3 months: 82 to 125 empty days a year. The bodyweight-only person fell to recovery days even when training every time.

**The proposed fix** keeps the limits' purpose: stopping sudden jumps. It stops them shrinking your training when you miss sessions or sleep badly. With it, the person like you keeps 94–96% of the planned training whether they train every time, miss a session in five or have a week interrupted. Across all eight test people and all four patterns, it's 80–100%. Nobody gets a recovery day from the limits, and no week goes above twice the three weeks before it.

**What needs your decision.** The fix changes where a safety limit applies, so it is a MAJOR rule change (Registry 2.0.0). Under the research-update workflow only you can approve it (D-106). The code is ready and tested on a separate branch and isn't in the app until you approve.

## What changes, in plain words

1. **Missed sessions and tired days no longer shrink your limit.** A past week is counted at the level it was planned and done:
   - if you did 2 of 3 sessions, the week counts as if all 3 had been like those 2;
   - if the readiness check made a day lighter after a poor night, that day counts at its planned size, in proportion to how much of it you did;
   - a week with fewer than half its sessions done is left out, as a fully missed week already is;
   - only the last 8 weeks count.
2. **A weight still being found counts as it is done.** When the app is finding your working weight, that exercise counts as two sets at the target effort, in the plan and once done, never more than planned. Before, the easy ramp-up sets counted nothing, so the first weeks looked light and set a low starting point.
3. **The week is planned within the limits.** The week planner now fits the whole week inside the limits before you start, so sessions are rarely cut on the day. When something must give, it goes in this order:
   - other exercises' sets;
   - steady cardio, shortened by at most 40% (never below 10 minutes) or made easy;
   - intervals, shortened by whole repeats or made easy;
   - main lifts, last.

   A day made lighter this way says so in its preview.
4. **Cardio is made easy before it is dropped.** When a day's cardio would have to lose more than 40% to fit, it becomes easy cardio instead of disappearing. Easy cardio is on a low-impact machine: easy jump rope becomes the elliptical, for example.
5. **A floor.** The limits never cut a week below half of its planned training stress. Half is about the training stress of a deload week, although the sessions keep their normal exercises and effort.
6. **A spike guard.** No week can be more than twice the average of your last three weeks as you actually did them, missed weeks counting as zero. The guard wins over the floor. It starts three weeks after your first session. It doesn't apply during a return ramp or a deload, which have their own rules.

The two numbers that set how fast training may grow stay the same: 15% for VOL-007 and 20% for LOAD-005.

## The year-long results
Each person was simulated for a year through the app's own data layer and engine, in four ways of turning up:
- **every session**;
- **about 4 sessions in 5**, missed at random;
- **interrupted:** a missed week, an illness reported at the check-in, and a warning sign that stops training until it is confirmed, each twice in the year;
- **sparse:** one session a week for ten weeks, then every session.

The share of the planned training stress kept over the year, and the days emptied by the limits ("recovery days"). "Before" is today's app.

| Test person | Every session (before → after) | About 4 in 5 (before → after) | Interrupted | Sparse |
|---|---|---|---|---|
| P1 — like you (intermediate, 3 × 60 min, gym without treadmill running, bikes or stairs, fat loss) | 74% → 96% | 9% → 94%; 82 → 0 recovery days | 94% | 86% |
| P2 — returning beginner | 94% → 98% | 8% → 94%; 92 → 0 | 96% | 87% |
| P3 — time-crunched, 4 × 35 min | 96% → 98% | 19% → 94%; 83 → 0 | 96% | 88% |
| P5 — poor sleeper | 70% → 86% | 6% → 81%; 90 → 0 | 86% | 80% |
| Advanced lifter, 4 days | 76% → 95% | 4% → 95%; 125 → 0 | 93% | 88% |
| Beginner 66, type 2 diabetes and knee osteoarthritis | 96% → 100% | 14% → 100%; 86 → 0 | 98% | 94% |
| Fat loss 52, high blood pressure | 94% → 98% | 4% → 96%; 104 → 0 | 96% | 90% |
| Bodyweight only, 35, at home | 12% → 84%; 94 → 0 | 6% → 80%; 100 → 0 | 81% | 94% |

With the change, no person had a recovery day caused by the limits, in any of the 32 simulated years.

How to read it:
- **The poor sleeper** now keeps about 80–86%. The rest is their short-sleep days, which the readiness check plans lighter by design. Before this change they were cut twice, by the readiness check and then by the limits.
- **The bodyweight-only person** keeps about four-fifths. Their cardio circuits carry a lot of training stress, so some weeks are still trimmed. Nothing is emptied.
- **After sparse weeks** (one session a week for ten weeks) training comes back to the full plan over a few weeks, never more than doubling in a week.
- **Strength still grows.** For the person like you, the main lifts' estimated maximum rises by about 11–14% over the year in every pattern. Before, it was only +4% when 1 session in 5 was missed.
- **Sessions cut on the day by the limits** fell from about two-thirds of all sessions to a few per year for most people. The planner now fits the week in advance, and each lighter day says so in its preview.
- **Sudden jumps stay bounded.** No week went above twice the three weeks before it, outside a return or a deload; the largest was exactly 2.0×, after ten weeks of one session a week. With every session done, no week went above 1.6×. The simulation now fails if any week breaks this, so every future change is checked against it.

## What gets looser, and why it stays safe
The fix makes the limits less strict in four ways:
- after missed sessions, a week can go back to its planned size faster than +15% a week;
- a day the readiness check made lighter counts at its planned size in the weeks the limits compare with;
- the limits never cut a week below half its plan (unless that would break the spike guard);
- a weight being found counts as two sets.

**What keeps it safe:**
- **Session size is unchanged.** The programme and its progression rules set the size of each session, never these limits. A session is never bigger than it would have been.
- **The new spike guard bounds the loosening.** A week is never more than twice the three weeks before it as you actually did them, floor included. It is off only during a return ramp or a deload, which have their own rules. In the simulations the largest week was exactly 2.0× the three before it; with every session done, no week went above 1.6×.
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

**What remains:**
- **After a few very light weeks, a week can be up to twice what you did.** Each session is still its normal size, and after 7 or more days off the return ramp applies instead.
- **The floor is half the plan's training stress, which is about what a deload week has.** But the sessions keep their normal exercises and effort, unlike a real deload (DEL-003), which is easier.
- **Tired days no longer hold the next weeks down.** The readiness check still protects each day: a short night still means a lighter session that day.

## Independent review
A reviewer who had not seen the work being built checked the proposal. It read the code, ran the tests and wrote its own probe tests and simulations. It found **1 high, 4 medium and 4 low** problems, and said the proposal was not yet safe to put to you. All nine are fixed:

| # | What was wrong | Fix |
|---|---|---|
| 1 (high) | In the week after a lighter week, the guard against sudden jumps was switched off by mistake. LOAD-005 is also off that week, so one test week was allowed about 4 times the three weeks before it | The guard is off only in a return ramp or a deload, which have their own rules. A test checks the week after a lighter week |
| 2 (medium) | The floor (half the plan) could override the guard, so a week could still be 2.4–3.9 times the three before it. Also, "about a deload week" is only true for the total training stress, not for how hard the sessions are | The guard now always wins: the floor never goes above it. The report says plainly what the floor is |
| 3 (medium) | When the planner trimmed a week in advance, you were never told | Each day the planner made lighter says so in its preview, with a clearer reason |
| 4 (medium) | Some tests could pass without proving anything, and nothing tested how the guard is switched on and off | New tests cover the guard's on/off cases, the floor actually stopping the planner, and the load limit working with real history. The year simulation now fails if any week goes above the guard, and it has more ways of turning up: illness reported at the check-in, a warning-sign stop, and weeks of one session each |
| 5 (medium) | The planner cut main lifts before shortening intervals | Intervals are now shortened, then made easy, before any main lift is touched |
| 6 (low) | Reference weeks could be months old | Only the last 8 weeks count |
| 7 (low) | Making jump-rope intervals "easy" kept the jumping, for longer | Easy work moves to a low-impact machine, or to a circuit without jumps |
| 8 (low) | The planner counted a weight still being found as all its sets, while the session check counts two | Both count two |
| 9 (low) | Some comments didn't match the code. Review weeks were treated differently by the planner and the session check | Fixed and aligned |

**The reviewer then re-checked the fixes.** All nine hold, and it judged the proposal safe to put to you. It found two more problems, both now fixed:

| # | What was wrong | Fix |
|---|---|---|
| A (medium) | The poor sleeper kept only about 55% of their plan: they were cut twice. The readiness check lightened their bad days, then the weekly limits followed those lighter weeks and lightened the good days too. The planner also padded short interval blocks to 10 easy minutes, which could make a day heavier rather than lighter | A day the readiness check made lighter now counts at its planned size in the weeks the limits compare with, as the reviewer suggested; the spike guard still uses the weeks as done. Intervals are made easy only when that lowers the day's training stress, and never for longer than the block itself. The poor sleeper now keeps about 80–86% |
| B (low) | The year simulation didn't check weeks that started with a return session | It checks them now. That showed one small counting mismatch (a weight-finding exercise planned as one set but logged as up to five counted as two), fixed |

## How it was checked
TESTS_SECTION

## Your decision
Please approve Registry 2.0.0, or tell me what to change. On approval I will:
1. move the change set into the approved history and apply it with the research-update tool;
2. move the new numbers into the rule registry;
3. merge the branch into the app, then run CI and the device tests;
4. report back, and go on to Phase 4 Part 2 (the 30 test scenarios).

If you don't approve, the app keeps today's limits. Please still don't train with the test build, because they shrink training when sessions are missed.
