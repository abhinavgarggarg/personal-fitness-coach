# Phase 3 — Part 2 report: exercise library, 12-month programme, week planner, session generator

Date: 8 October 2026 · Rule Registry 1.0.1 · Library 1.0.1 · Decisions D-046 to D-057

## In one paragraph
The engine can now plan a full training year and produce each day's workout. It holds an exercise library written in our own words (152 exercises, 31 warm-up and mobility drills, 10 cardio machine guides). It lays out the 12-month programme: calibration, then blocks for foundation, building, strength, conditioning and power, each followed by a lighter or "pivot" week. Each week it picks your training days, fills them with exercises that suit your equipment and joints, and sets sets, reps, effort, rest and cardio. On the day, it turns that plan into the exact session you will see, with loads for your real plates and dumbbells, and runs it through the safety check from Part 1. All of this is plain Kotlin with no Android parts: 345 automated tests pass here. A simulated year for five very different users broke no hard rule in any session. An independent reviewer found 20 problems; all are fixed, and each has its own test.

## What was built

| Area | Rules | Status |
|---|---|---|
| Exercise library: 152 exercises (squat, hinge, lunge, push, pull, core, carry, isolation, power), 31 drills, 10 modality guides. Each exercise has original setup text, cues, mistakes and safety notes, plus muscles, equipment, joint stress (0–4), safety tags, difficulty, fatigue and ladders. A generator checks all of it (D-046) | EQ-001, EQ-002, BW-001, CORE-001, MOD-002, IND-001 | VERIFIED (tests and generator checks) |
| Reps, rest, effort and exercise order: rep ranges by goal and level, rest periods and how they shorten under time pressure, target RIR by level and week in the block, order of the session, supersets | REP-001 to REP-006, REST-001 to REST-007, INT-002, INT-006, ORD-001 to ORD-003 | VERIFIED |
| Cardio: training zones, at least 75% easy (Z1) work, weekly growth ≤ 15%, WHO activity floor, interval menus and progression, carries, how strength and cardio share a week | AER-001 to AER-003, PH-001, HIIT protocols, PROG-006, CON-001, CON-002, CON-005, CON-006, FREQ-005 | VERIFIED |
| Bodyweight and core progression ladders (e.g. incline push-up → push-up → deficit), core volume and frequency | BW-001, BW-002, PROG-005, CORE-002, CORE-003 | VERIFIED |
| Warm-up drills, cool-down, mobility between sets and on rest days, with range-of-motion limits for sore joints | MOB-001 to MOB-005 | VERIFIED |
| Training frequency and experience: days per week, how experience changes the plan, equipment roles | FREQ-001 to FREQ-004, EXP-001, EXP-002, IND-001, EQ-001 | VERIFIED |
| 12-month programme: the block sequence, 4–6 loading weeks plus a deload or pivot week, priority goals lengthening their blocks, block clock pauses and restarts after missed weeks or breaks | PER-001 to PER-006 | VERIFIED |
| Week planner: day templates for 2–6 days, heavy-leg days at least 48 h apart and no more than 3 hard days in a row; exercise choice; weekly sets per muscle within the caps; push/pull and knee/hip balance; core minimum; cardio plan; sessions shaped to your usual length (D-051) | SCH-001 to SCH-003, VOL rules, PAT-001 to PAT-004, CORE-003 | VERIFIED |
| Session generator: the 10 steps of GEN-001 ending in the safety validator; readiness tiers, deload weeks, swaps for missing equipment, illness rest days, a 20–30-minute express version, decision log | GEN-001, ADH-004, RDY-004, DEL-003, SAF-007, SAF-008 | VERIFIED |
| Habit features: forgiving weekly streak with a freeze, milestones, express sessions, when to ask for the session-effort rating | ADH-001 to ADH-004, LOAD-002 | VERIFIED |
| CI: the library generator runs with `--check` locally and on GitHub | — | VERIFIED locally; GitHub run below |

Rule coverage: **137 of 140 rules** now have all their registry test cases (Part 1: 73). The remaining three belong to later parts: DATA-001 (what data is stored, all on the phone) to Part 3; SAF-009 (the coach's boundaries) and COACH-001 (the coaching voice) to Part 5. Full table: `docs/phase3/rule_coverage_part2.md`.

## How it was checked
- **345 tests, all passing** (local compile with Gradle's bundled Kotlin 2.0.21 and JUnit 4; GitHub builds with Kotlin 2.4.0).
- **Property tests** (random but repeatable): random users — any level, 2–6 days, restricted days, four kinds of gym (full, home dumbbells, machines only, bodyweight only; some with a plyo box and jump rope), injuries, screening, deloads, crowded gyms. Over 10 seeds × 500 cases, every planned week stays within every cap. Weekly sets per muscle, per-session sets, HIIT count and placement, heavy-leg spacing (whatever days are available), one impact session a week and no duplicate exercises all hold. Every generated session, with random readiness, missing kit, pain and time, passes the safety validator.
- **A 52-week simulation for five users.** The simulated users have a "true" strength that grows slowly, perform every set, and report honest effort. In every session of the year, the validated workout broke no hard rule, the weekly caps held, and no load ever rose faster than allowed.

| Simulated user | Sessions | Result |
|---|---|---|
| Beginner, 3 days, full gym | 156 | Programme reached the review weeks; all 5 tracked lifts got stronger (median +7%) |
| Intermediate, 4 days, strength/muscle/cardio | 203 | 5 of 6 tracked lifts stronger (median +8.5%; the sixth only started in week 52) |
| Advanced, 5 days, home dumbbells | 260 | No rule broken; loads moved well off the lightest dumbbells |
| Age 68, 2 days, machines only | 104 | No rule broken; age-65 limits applied |
| Busy, 6 days, misses most of every 4th week | 260 planned | The block clock paused on missed weeks (week 40 of 52 by year end) instead of skipping blocks |

- **Phase 1 tables reproduced:** the block sequence and deload weeks (8, 14, 20, 26, 32, 38, 44), the dose per block and the HIIT protocol per block are pinned by tests to the Phase 1 blueprint (D-050).

## Independent review
A separate reviewer, who had not written this code, tested Part 2 against the registry with throwaway tests. It found **1 high, 11 medium and 8 low** issues. All are fixed. Each has a regression test (`ReviewRegressionTest`, R01–R20), and the property tests now check the same things for random users.

| # | Finding | Fix |
|---|---|---|
| 1 (high) | A fever day still produced 24 minutes of light cardio, warm-up and cool-down; SAF-007 says rest | Illness now gives a rest day with nothing in it |
| 2 | Turning jump rope into easy cardio lost its "impact" label, so a sore back's no-jumping limit missed it | Impact is kept through every conversion |
| 3 | Box jumps were not counted as impact, so a week could have two impact sessions | Box jumps count; at most one jumping exercise a week |
| 4 | With only adjacent days free (e.g. Mon–Tue), heavy leg days could be back to back | Fewer days, or a moderate leg day, instead |
| 5 | Some exercises were missing safety tags (deep knee bend, overhead, spinal loading), and carries had no leg stress | Tags and joint stress added; the generator now refuses an exercise without them |
| 6 | Overhead dumbbell triceps extension could be taken to failure (something you could drop on yourself) | Not failure-safe any more; same for step-ups and dead bugs |
| 7 | Volume maps could iterate in a different order between app launches, giving different trims | Fixed-order maps |
| 8 | Easy cardio could grow 20% in a week (limit 15%) | Weekly cap respected |
| 9 | Interval work could grow 12 minutes in a week (limit +2), the block protocols differed from Phase 1, and beginner work:rest drifted from 1:2 | Weekly work budget, Phase 1 protocols, beginner 1:2 kept |
| 10 | The express session could run 55 minutes | It now shrinks until it fits 30 minutes |
| 11 | "Moderate only" users could get Z2 tempo | Z1 only (D-056 — please confirm) |
| 12 | A painful wrist could get wrist drills first in the warm-up | Drills now carry joint stress and respect limits |
| 13 | Moving up a ladder could leave the squat slot empty | Ladders are matched by movement pattern |
| 14 | On a light day the reported load reduction could be untrue at the lightest dumbbell | Real reduction reported, or an easier variation used |
| 15 | Carry and crawl targets were capped at 12 m | Targets stay inside each exercise's own range |
| 16 | Asking for 4 days with 3 free gave the first 3 days of the 4-day plan (no hinge) | The 3-day plan is used |
| 17 | Short sessions could cut easy cardio below 10 minutes | 10-minute floor; weekly checks logged |
| 18 | A blocked box jump was replaced by a slow squat still labelled "power" | Swaps get a sensible role; main lifts keep their warm-up sets |
| 19 | Light days lacked the 5–10 minutes of mobility; interval details were not in the output | Both added |
| 20 | A long break during a pivot week restarted the finished block; a holiday week broke the streak | The next block starts; holiday weeks keep the streak |

Fixing these exposed three more problems, also fixed with tests:
- **Core minimum.** A 4-day week could plan only 5 core sets, because squats and deadlifts already fill most of the trunk's weekly allowance. The fix (D-054) counts loaded carries as core work, and when the allowance is full, it trims incidental trunk work first.
- **Box jump as a main lift.** A user whose knee limits removed every squat could get a box jump as the main squat, sometimes twice in one day (D-057).
- **Lifts that could never get heavier.** Some lifts could never move up, because the next dumbbell or plate step was bigger than the 5% weekly limit (D-055).

## Things you should know
1. **Please confirm D-056.** If your health screen says "moderate only" (a known condition, not yet cleared by a doctor), the app plans only easy Z1 cardio until you confirm clearance. Phase 1 locked only intervals and hard Z3, but our zone table counts Z2 tempo as vigorous, so I took the safer reading.
2. **Light weights can now go up (D-055).** Going from a 20 kg to a 22.5 kg dumbbell is a 12.5% jump, more than the approved 5% weekly limit. The app now takes that one step once your recent sets show you can lift it for the planned reps with the planned reps in reserve. It never takes more than one step a week. Before this fix, such lifts stayed stuck all year.
3. **Very easy sets (D-052).** If every set is rated 4 or more reps in reserve at the top of the rep range, the next available weight is used.
4. **Your bodyweight helps.** Without bodyweight or known working weights, a cable machine with 5 kg steps starts at the lightest plate, and calibration moves up only in steps it judges safe. A strong user could spend a few weeks climbing to their real working weight. Onboarding (Part 4) will ask for bodyweight and offer "I know my weights", and both are optional.
5. **Carries count as core work (D-054).** Farmer's and suitcase carries train the trunk to resist bending, so they count toward the 6–12 weekly core sets.
6. **Exercise text is original.** Nothing is copied from books or websites, and every exercise carries its own safety note.

## Risks and open issues
| Risk | Impact | Mitigation |
|---|---|---|
| New Expert Practice numbers (start fractions D-048, joint stress for drills and carries) | First loads or swaps slightly off | Calibration corrects loads within a few sessions; marked REQUIRES VALIDATION for Phase 4 |
| Simulated users always do the top of the rep range | No e1RM is ever set for 8–12-rep lifts in the simulation (a simulation artefact; real users stop at the target effort) | Loads still progressed through the progression engine (D-055); Phase 4 will test with your logged sessions |
| GitHub build of this part | — | CI green on GitHub for the review fixes (run 37816759062) |

## What I need from you
1. **Confirm or change D-056** ("moderate only" → easy cardio only until cleared).
2. Reply **CONTINUE** for Part 3.

## Next: Part 3
The data layer: the on-phone database (Room) for your profile, plan, sessions, sets and history, seeded from the same library JSON, storing only the data items Phase 1 allowed (DATA-001); backup and restore with an optional password. The engine will then read and write real history instead of test data.
