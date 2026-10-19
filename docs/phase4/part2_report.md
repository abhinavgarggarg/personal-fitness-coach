# Phase 4, Part 2 report: the 30 scenarios

Date: 19 October 2026 · Decisions D-114 and D-115 · Scenario list: `docs/phase4/scenarios.md` · Tests: `app/src/test/.../flow/ScenarioSuiteTest.kt`

## Summary
You asked me to write the 30 scenarios myself and carry on. I wrote them from the approved Phase 2 user journeys, test strategy and acceptance criteria. Scenario 29 ("ignore my knee, make it harder") and scenario 30 (upgrade over existing data) keep the meaning Phase 2 gave them.

Each scenario is an automated test. It drives the app through the same calls the screens make, on the real data layer and engine, over simulated days. A simulated person reports effort honestly from a modelled strength, so the weights found and progressed are realistic. All 30 run on every push and take about 15 seconds.

**All 30 pass.** The first run found four real problems in the app, and I fixed them. An independent review then made the tests stricter. It also found two ways my first fix could set working weights too heavy, which I closed. None of the fixes changes a rule, a number or a wording (D-115).

**One decision is yours** (below): how fast the app should find your dumbbell and kettlebell weights.

## The scenarios

| Group | Scenarios |
|---|---|
| First days | S01 first day · S02 a warning sign in screening · S03 onboarding interrupted · S04 two health conditions at 66 |
| Everyday training | S05 a normal gym day, week after week · S06 a bad night · S07 a red flag at the check-in · S08 illness · S09 back after three weeks off at 66 · S10 a whole week missed · S11 turning up two sessions in three |
| Time | S12 only 30 minutes · S13 "only 15 minutes left" mid-workout · S14 90 minutes on a 60-minute plan |
| In the workout | S15 the machine is busy · S16 knee pain 5/10 mid-set · S17 sharp pain 8/10 · S18 fainting during the workout · S19 finding a working weight · S20 a superset on a short day · S21 the app killed mid-workout |
| The coach | S22 "Why?" on today's session · S23 out-of-scope questions · S24 Ask Gemini |
| Progress and data | S25 tired for days in a row · S26 six weeks of training · S27 correcting a past set · S28 backup, erase and restore |
| Edge cases | S29 "ignore my knee, make it harder" · S30 upgrade over existing data |

`docs/phase4/scenarios.md` gives each one's expected result and the rules it exercises.

## What the scenarios found, and the fixes

1. **Finding your weights took weeks with no real sets** (S19, S05, S26). The approved calibration rule (CAL-001) says:
   - when a ramp set feels like "3 more in the tank", that's your working weight;
   - when a set is too hard, next time uses that weight minus 5%;
   - the next session confirms the weight with normal working sets.

   The app instead ramped again, up to four sessions in a row. It did this whenever the found weight gave no strength estimate, which is the case for the usual 8–12 rep range. On those days the exercise had only warm-up-style ramp sets and no working sets. With exercises coming round once a week, a main lift could go a month without a working set.

   **Fixed:** once a weight is found, the next session does working sets with it. Only a ramp that is still easy after its 5 sets carries on next time, within the first four sessions, and it now says so.

   The review then found that a ramp set falling short of its reps (6 of 10, say) could have led to working sets near failure. The working weight now goes by what you actually did. Main lifts now start working sets in week 2 or 3, and their targets go up from there (S05).

2. **No "welcome back" after a long break without opening the app** (S09). Missed sessions were only counted in weeks when the app had been opened. Most people who stop training also stop opening the app.

   **Fixed:** a whole week without the app now counts its usual planned sessions, as the end-of-week summary already did. The return rules weren't affected, because they also look at days off.

3. **"Only 15 minutes left" left about 4 minutes of training** (S13). When the time left can't hold everything, whole exercises go, least important first. Nothing came back afterwards, even when the last one taken freed far more time than needed.

   **Fixed:** what fits comes back, most important first. A weight-finding ramp can't be shortened set by set, so it now stays or goes whole and is timed at its full length. The scenario now keeps 14.7 of the 15 minutes.

4. **A past set could be corrected to 500 reps** (S27). Logging a set asks you to confirm anything unusual, but a correction in History accepted anything up to 1000 reps.

   **Fixed:** a set logged or corrected above 100 reps or 500 kg is refused.

**Expectations I corrected** (the app was right):
- S11: in the first weeks after the weights are found, the plan builds up for everyone, so the check now compares two-in-three with someone who turns up every time. Both keep 94–100% of the plan, and 96–100% from week 4.
- S25: the deload week has at most half the sets of the normal week it replaces: 12 of 28, because each day rounds down. It feels bigger than half because the week before had 37 sets.
- S29: the coach doesn't refer "ignore my knee, make it harder" elsewhere. It explains that the rules and today's check-in set the level, as Phase 2 specified. It can only open the preview, where nothing harder and no knee-loading exercise is offered, not even as a swap.
- S30: the strongest check of the upgrade is a twin. History saved in the old format gives exactly the same weekly limits and the same week as for someone who had the new version all along, and it does.

## A decision for you: dumbbell and kettlebell weights

**What happens now.** The weight-finding ramp adds 20–30% per set, as the approved rule says, and never goes above "RPE 8" (2 reps in reserve). Dumbbells come in 2.5 kg steps, so below 10 kg the next pair is a bigger jump than that: 2.5 → 5 kg is +100%. The ramp therefore stops at the start weight, which is deliberately light (about 5% of body weight per hand for presses, D-048). Kettlebells (8 → 12 → 16 kg) are the same.

After the fix above, that start weight becomes the first working weight. Progression then adds one pair at a time as your reps reach the top of the range.

For machines we already allow the bigger step at "5+" (D-070); free weights were kept to the band in a Phase 3 review.

**What it means.** In a 12-week simulation, I measured the week each dumbbell exercise reached 85% of the person's real working weight:

| | Experienced 41-year-old (real weight about 18.6 kg) | Ramp sets harder than RPE 8 | 66-year-old beginner (about 8.4 kg) | Ramp sets harder than RPE 8 |
|---|---|---|---|---|
| Now | weeks 7–11 | 0 of 8 | weeks 3–7 | 0 of 10 |
| Allow the next pair at "5+", like machines | weeks 3–5 | 5 of 66 | weeks 1–4 | 6 of 31 |

**Options:**
- **A. Keep it as it is.** It is the safest. It's slow for anyone experienced. You can skip it by entering your usual weights for the common lifts at onboarding (the "Numbers" step).
- **B. Allow the next pair or kettlebell at "5+" for everyone.** It is fast, but some ramp sets end harder than the rule allows, mostly for weaker or older people.
- **C (my recommendation). B, except for beginners, people aged 60 or over, gentle mode and anyone with a health condition.** It is fast for experienced lifters like you, and those who most need the margin keep the safe ramp.

B and C change what CAL-001 allows ("never above RPE 8"). I would write either up as a small rule change through the research-update workflow, with an independent safety review, for your approval before it goes into the app.

## Independent review
A separate reviewer checked the suite and the fixes and found 12 problems: 2 high, 4 medium and 6 low. All are addressed (D-115).

The two high ones were in the tests themselves:
- the weight-finding scenario couldn't have caught the bug it was written for. It now checks every ramp in six weeks of training against the rule's own words, and fails on the old code;
- the "15 minutes left" check could never fail. It now requires the time to be used.

The safety ones:
- a ramp set short of its reps could lead to working sets near failure; fixed as described above;
- a weight found for few reps might be used for more reps on another day. It can't be: without a strength estimate, the weight keeps its own rep range on every day of that exercise. A test now covers this.

The rest made scenarios less forgiving, gave the "still easy after five sets" case its own message, and made the year-long simulation follow the app exactly.

The same reviewer then re-checked the fixes. It confirmed the scenarios now pin them down, and found six smaller things, all now done:
- a ramp set logged with 0 reps could have set working sets at 95% of a weight never lifted. Now the last weight you did lift is the working weight; if it was the first set, the ramp carries on next time from 5% lower;
- when even the most important work can't fit the time left, the app tells you the shortest the session can be. That figure now counts a weight-finding ramp at its full five sets, as the fitting does;
- a weight-finding ramp now clears an exercise's saved weights only after the break the return rules call for: 8 weeks, or 4 weeks at 60 or over or after a flagged screening. One check used 4 weeks for everyone;
- the weight and reps fields in the workout and in History now stop at 500 kg and 100 reps, so nothing typed is refused only when you save;
- two test checks were tightened.

## Tests
- Local: engine 428, data 178 and app 99 tests all pass. The app count includes the 30 scenarios.
- The year-long simulations give the same weekly-limit results as in Part 1: no week above twice the three before, no recovery days from the limits.
- On GitHub: [CI run to be added].

## Next
- Your decision on the dumbbell ramp (A, B or C).
- Part 3: interruption, data-integrity and upgrade tests on emulators (E5).
