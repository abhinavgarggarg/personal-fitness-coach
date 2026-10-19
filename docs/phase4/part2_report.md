# Phase 4, Part 2 report: the 30 scenarios

Date: 19 October 2026 · Decisions D-114 and D-115 · Scenario list: `docs/phase4/scenarios.md` · Tests: `app/src/test/.../flow/ScenarioSuiteTest.kt`

## Summary
You asked me to write the 30 scenarios myself and carry on. I wrote them from the approved Phase 2 user journeys, test strategy and acceptance criteria. Scenario 29 ("ignore my knee, make it harder") and scenario 30 (upgrade over existing data) keep the meaning Phase 2 gave them.

Each scenario is an automated test. It drives the app through the same calls the screens make, on the real data layer and engine, over simulated days. A simulated person reports effort honestly from a modelled strength, so the weights found and progressed are realistic. All 30 run on every push and take about 15 seconds.

**All 30 pass.** On the first run, 9 failed. Some failures were my expectations being wrong. Four were real problems in the app, and I fixed them (D-115). None of the fixes changes a rule, a number or a wording.

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

   **Fixed:** once a weight is found, or is "too hard minus 5%", the next session does working sets with it. Only a ramp that is still easy after its 5 sets carries on next time, within the first four sessions. In the 12-week journey, the lifter's main lifts now start working sets in week 2 or 3, and the scenario that expects targets to go up (S05) shows it.

2. **No "welcome back" after a long break without opening the app** (S09). Missed sessions were only counted in weeks when the app had been opened. Most people who stop training also stop opening the app.

   **Fixed:** a whole week without the app now counts its usual planned sessions, as the end-of-week summary already did. The return rules weren't affected, because they also look at days off.

3. **"Only 15 minutes left" left about 4 minutes of training** (S13). When the time left can't hold everything, whole exercises go, least important first. Nothing came back afterwards, even when the last one taken freed far more time than needed.

   A weight-finding ramp was also counted as if it could be cut set by set. The workout plays it whole, so the time shown was wrong.

   **Fixed:** after the cut, what fits comes back, most important first: a cardio block as it was fitted, a ramp whole, then sets one at a time. The scenario now keeps about 11 of the 15 minutes. The rest would only fit a ramp, which can't be shortened.

4. **A past set could be corrected to 500 reps** (S27). Logging a set asks you to confirm anything unusual, but a correction in History accepted anything up to 1000 reps.

   **Fixed:** a correction above 100 reps or 500 kg is refused, as are negative values and effort answers out of range.

**Expectations I corrected** (the app was right):
- S11: the first weeks after finding the weights are ramped up for everyone. The check is now against someone who turns up every time, and the plan is kept at 94–100% either way.
- S25: the lighter week has half the sets of the normal week it replaces (12 of 28). The weeks before it had more sets, so my first check was too strict.
- S29: the coach doesn't refer "ignore my knee, make it harder" elsewhere. It explains that the rules and today's check-in set the level, as Phase 2 specified, and can only open the preview, where nothing harder is offered.
- S30: the strongest check of the upgrade is a twin. Old-format history gives the same weekly limits as for someone who had the new version all along, and it does.

## A decision for you: dumbbell and kettlebell weights

**What happens now.** The weight-finding ramp adds 20–30% per set, as the approved rule says, and never goes above "RPE 8" (2 reps in reserve). Dumbbells come in 2.5 kg steps, so below 10 kg the next pair is a bigger jump than that: 2.5 → 5 kg is +100%. The ramp therefore stops at the start weight. Kettlebells (8 → 12 → 16 kg) are the same.

The start weight is deliberately light (about 5% of body weight per hand for presses, D-048). After the fix above, progression then adds one pair at a time as your reps reach the top of the range.

For machines we already allow the bigger step at "5+" (D-070); free weights were kept to the band in a Phase 3 review.

**What it means for you.** In a 12-week simulation of an experienced 41-year-old with a true working weight of about 18.6 kg:

| | Week dumbbell exercises reach a real working weight | Ramp sets harder than RPE 8 |
|---|---|---|
| Now | about week 11 (2.5 kg → 17.5–20 kg) | 0 of 8 |
| Allow the next pair at "5+", like machines | week 2 (15–17.5 kg) | 5 of 66 |

For a 66-year-old beginner (true working weight about 8.4 kg), the bigger step finds 7.5 kg in week 1 instead of week 11. But 6 of their 31 ramp sets went harder than RPE 8.

**Options:**
- **A. Keep it as it is.** It is the safest. It's slow for anyone experienced. You can skip it by entering your usual weights for the common lifts at onboarding (the "Numbers" step).
- **B. Allow the next pair or kettlebell at "5+" for everyone.** It is fast, but some ramp sets end harder than the rule allows, mostly for weaker or older people.
- **C (my recommendation). B, except for beginners, people aged 60 or over, gentle mode and anyone with a health condition.** It is fast for experienced lifters like you, and those who most need the margin keep the safe ramp.

B and C change what CAL-001 allows ("never above RPE 8"). I would write either up as a small rule change through the research-update workflow, with an independent safety review, for your approval before it goes into the app.

## Tests
- Local: engine 427, data 175 and app 99 tests all pass. The app count includes the 30 scenarios.
- The 12-week simulations used in Part 1 give the same results as before: 80–100% of the plan kept, no week above twice the three before, no recovery days.
- On GitHub: [CI and Devices run IDs to be added].

## Independent review
[to be added]

## Next
Part 3: interruption, data-integrity and upgrade tests on emulators (E5).
