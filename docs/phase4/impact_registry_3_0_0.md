# Impact of change set 3.0.0 (MAJOR)

Finding a weight: a stop at about 3 reps left on every ramp set, and bigger steps on light weights only as far as a closer count allows (Phase 4 Part 2)

Base registry 2.0.0 · 2026-10-19

Changes to CAL-001, the weight-finding ramp, after the Product Owner chose option C on 19 Oct 2026 (D-116) and an independent safety review of the first draft (2.1.0). (1) Every ramp set stops at its target reps or with about 3 reps left, whichever comes first, and the reps done are entered: the bands alone don't keep a ramp set within RPE 8 (at a 10-rep set, +10-15% after '4 more' is about 5 reps fewer); in 12 simulated weeks of an experienced lifter 8 of 78 ramp sets went past RPE 8 on the current rules, 1 of 136 with the stop. A set stopped short of its reps ends the ramp whatever was rated, and a set's working load is never more than it supports for the bottom of the rep range. (2) Big steps (no step inside the band, at most double): '5+' can mean exactly 5, so a doubled weight could be at or above the person's maximum (review finding 2). Before a big step the screen now asks 'about how many more?' (5-9, 10-19, 20-29, 30-49, 50 or more) and the step is only as big as the lower end of that answer allows with 2 reps to spare before the stop - on free weights the range below the one chosen, so a count one range too high is still safe; a plain '5+' never takes one. This applies to machine stacks (D-070, until now on '5+') and, per option C, to dumbbells, kettlebells and barbells for someone who is not a beginner, is under 60, has a standard screening and no health condition or injury; an exercise lightened after a pain report never takes one. MAJOR: it changes a safety boundary of the ramp (a free-weight limit set in the Part 1 review, and D-070 on machines).

| Rule | Change | Safety rule | Algorithms | Code that uses it | Tests (registry → found) |
| --- | --- | --- | --- | --- | --- |
| CAL-001 | MAJOR 1.0.0→2.0.0: wording; parameters replaced; uncertainty note | no | calibration | Coach.kt, ExerciseProgress.kt, SessionPlayer.kt, Calibration.kt, KnownLoads.kt | TC-CAL-001a ✓, TC-CAL-001b ✓ |

**Before release:** every rule above keeps its two tests passing (rule coverage 100%); safety rules need the safety review note; the Product Owner approves the change set; the app is rebuilt and the whole test suite runs (Phase 1 step 10).
