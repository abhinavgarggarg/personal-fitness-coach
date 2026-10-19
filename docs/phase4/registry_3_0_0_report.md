# Finding your weights: Registry 3.0.0 for your approval

Date: 19 October 2026 · Decision D-116 · Change set `rules/proposals/registry_3.0.0.json` · Impact `docs/phase4/impact_registry_3_0_0.md`

## Summary
On 19 October you chose **option C**: faster dumbbell and kettlebell weights for experienced lifters, with beginners, people aged 60 or over, gentle mode and health conditions kept on the safe path.

I built it. The independent safety review then showed that my first version could, in some cases, put a weight close to the person's maximum. It also found a bigger problem: the weight-finding rule itself, as approved in Phase 1, can push a set close to failure on every kind of equipment.

Both are now fixed in one rule update, **Registry 3.0.0**. It changes one rule, CAL-001 (finding your weight). The reviewer re-checked it and found **nothing that blocks approval**. It needs your approval before it goes into the app.

## What I found

**1. The ramp can push a set close to failure.** The approved rule says that if a set feels like "5 or more left", the next set is 20–30% heavier, and if it feels like "4 left", 10–15% heavier. It also says "never above RPE 8", meaning always at least 2 reps left. At 10 reps, 10–15% more weight takes away about 5 reps. So a set with "4 left" is followed by one with almost nothing left.

In a 12-week simulation of an experienced lifter, **8 of 78 weight-finding sets went past RPE 8** under today's rules.

**2. "5+" can mean exactly 5.** My first version of option C took a doubled dumbbell (2.5 → 5 kg) whenever you tapped "5+". But for someone who really had only 5 left, a doubled weight can be at or above their maximum. The reviewer found people for whom that happened in most of the big jumps. It is the same for machines today, which have done this since Phase 3 (D-070), although a machine set is easier to stop safely.

## What changes

**Every weight-finding set stops with about 3 reps left.** You do the reps shown, but stop sooner if you could only do about 3 more. The screen now shows "Reps done", set to the target, so you change it if you stopped early. Then you tap how many more you could have done. If you tap 3 or fewer without changing the reps, the app asks once: "Did you do all 10 reps?"

A set that stops early still finds your weight. The working weight goes by the reps you actually did, so it is never more than that set supports.

**Big jumps only on a closer count.** When the next weight could only be a big jump (no step of 20–30% exists), the "5+" button is replaced by five choices: **5–9, 10–19, 20–29, 30–49, 50+**. The app jumps only as far as your answer allows with room to spare.
- For free weights it counts the range below the one you chose. That way, a guess one range too high is still safe.
- A plain "5+" never takes a big jump.

**Who gets the big jumps on free weights:** anyone who is not a beginner, is under 60, has a standard screening and no health condition or injury. An exercise made lighter after a pain report never gets one. Machines get the closer count for everyone, which is safer than today.

## What it does (12 simulated weeks, honest answers)

| | Today's rules | With 3.0.0 |
|---|---|---|
| Experienced lifter, all equipment: weight-finding sets past RPE 8 | 8 of 78 | 1 of 136 |
| Experienced lifter: week each dumbbell exercise reaches 85% of the real weight | weeks 7–11 | weeks 3–5 |
| Experienced 45-year-old at home: same measure | weeks 6–11 | weeks 3–5 |
| 66-year-old beginner (keeps the safe path) | weeks 3–7 | weeks 3–7, unchanged |
| Answers one range too high: big jumps leaving fewer than 4 possible reps | — | none, in any of 6 people tested |

The remaining set past RPE 8 is a 3–5-rep exercise after a 25% step. That is within today's rule, and the stop at 3 left keeps it to a single rep.

## What stays the same
At most 5 weight-finding sets per exercise, spread over at most 4 sessions, and never above RPE 8. Weekly limits, progression, pain and warning-sign rules are unchanged. Old data reads as before, and backups from earlier versions still restore.

## Also found, for later
A very weak person for whom the empty 20 kg bar is already too heavy gets 20 kg × 8–12 they can't do. This is the same in today's version. I'll fix it in Part 4 (safety audit), for example by offering the easier version of the exercise.

## Your options
- **Approve 3.0.0 (recommended).** Both changes go in, and I apply them through the research-update workflow and the full test run.
- **Approve only the stop at 3 left.** Free weights keep today's slow path: no big jumps. Machines need the closer count.
- **Not yet.** Nothing changes in the app.
