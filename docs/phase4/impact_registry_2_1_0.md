# Impact of change set 2.1.0 (MINOR)

Finding a weight: a stop at 3 reps left on every ramp set, and bigger steps on light free weights outside the risk groups (Phase 4 Part 2)

Base registry 2.0.0 · 2026-10-19

Two changes to CAL-001, the weight-finding ramp. (1) The Product Owner's choice on 19 Oct 2026 (D-116, option C of the Part 2 report): when the equipment has no step inside the +20-30% band (2.5 -> 5 kg dumbbells is +100%), a rating of '5+' takes the next step, up to double the load, on free weights as already on machine stacks (D-070) - but only for someone who is not a beginner, is under 60, has a standard screening and no health condition; everyone else keeps the band and the start weight becomes the working weight. (2) Found while measuring (1): the bands alone don't keep a ramp set within RPE 8. At a 10-rep ramp set, +10-15% after '4 more' is about 5 reps fewer (Epley), so the next set ends near failure; in a 12-week simulation of an experienced lifter about 1 ramp set in 10 went past RPE 8 and 16 of 34 ramps ended 'too hard', with or without (1). Every ramp set now stops at its target reps or with about 3 reps left, whichever comes first; RIR 3 is already CAL-001's working-load rating, and the reps done give the working load or its estimate (INT-004), as D-115 already reads them. With honest ratings, ramp sets past RPE 8 fall to about 1 in 100. MINOR: new parameters and wording inside CAL-001's existing limits (never above RPE 8, at most 5 ramp sets, sessions 1-4).

| Rule | Change | Safety rule | Algorithms | Code that uses it | Tests (registry → found) |
| --- | --- | --- | --- | --- | --- |
| CAL-001 | MINOR 1.0.0→1.1.0: wording; parameters replaced; uncertainty note | no | calibration | Coach.kt, ExerciseProgress.kt, SessionPlayer.kt, Calibration.kt, KnownLoads.kt | TC-CAL-001a ✓, TC-CAL-001b ✓ |

**Before release:** every rule above keeps its two tests passing (rule coverage 100%); safety rules need the safety review note; the Product Owner approves the change set; the app is rebuilt and the whole test suite runs (Phase 1 step 10).
