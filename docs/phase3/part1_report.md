# Phase 3 — Part 1 report: engine core, safety kernel, project foundation

Date: 8 October 2026 · Rule Registry 1.0.1 · Decisions D-028 to D-043

## In one paragraph
The training "brain" of the app is built: it scores your daily check-in, picks the session tier, works out loads from your real plates and dumbbells, decides when to add weight or reps, handles setbacks and breaks, fits sessions into the time you have, finds swaps for busy equipment, and runs every workout through a safety check before you could ever see it. It is plain Kotlin with no Android parts, and 173 automated tests pass on it here, including the worked examples from the Phase 1 report. The Android project, the app's starter screen and the automatic GitHub build are written too, but they cannot be built here (the Android download servers are blocked), so they wait for your GitHub connection.

## What was built

| Area | Rules implemented | Status |
|---|---|---|
| Readiness score, personal baseline, tiers, sleep caps, user tier choice | RDY-001 to RDY-007 | VERIFIED (tests) |
| Effort (RIR/RPE), e1RM, load from e1RM, rounding to your equipment | INT-001, INT-004, INT-005, PROG-003 | VERIFIED |
| Weekly volume, landmarks, per-session caps, accessory cuts | VOL-001 to VOL-008 | VERIFIED |
| Session-RPE load, monotony, strain, EWMA flag, planning cap | LOAD-001, LOAD-003 to LOAD-006 | VERIFIED |
| Six fatigue signals, deload decision, prescription, resumption | DEL-001 to DEL-004 | VERIFIED |
| Progression (load, reps, range extension, hold/reduce, technique gate, weekly caps), in-session autoregulation | PROG-001 to PROG-004, PROG-007, PROG-008, INT-007 | VERIFIED |
| First-week calibration, return after breaks and illness, warm-up ramps and compression | CAL-001, REG-001 to REG-005, WU-001 to WU-004 | VERIFIED |
| Session time budget and extension; substitution scoring and learning | TIME-001 to TIME-004, SUB-001 to SUB-003 | VERIFIED |
| Safety kernel: screening, red-flag stop, illness gate, pain gate, persistent-pain regions, hard caps, user additions, final validator, weekly balance checks | SAF-001 to SAF-008, INT-003, HIIT-001 to HIIT-005, CON-003, CON-004, MOD-001, PAT-001 to PAT-004, AGE-001 | VERIFIED |
| Android project: Gradle wrapper 9.7.0, version catalog, app shell (offline, 4 permissions), CI workflow producing a debug APK | Phase 2 F3 Option B | IMPLEMENTED — PENDING ENVIRONMENT VERIFICATION |

## How it was checked
- **173 tests, all passing** (local compile with Gradle's bundled Kotlin 2.0.21 and JUnit 4). Test names carry rule IDs, so coverage is counted automatically: **73 of 140 rules** have all their registry test cases so far; the rest belong to later parts (exercise library, generation, reps/rest/aerobic rules, adherence, data, coach).
- **Property tests:** 3,000 random sessions with random pain, screening, tiers, weekly totals and conditioning go through the safety validator; every output passes every check, and nothing crashes. Similar randomised tests cover progression, autoregulation, calibration, time fitting and substitution.
- **Phase 1 worked examples reproduced exactly:** bench 60 × 8 @ RIR 2 → e1RM 80.0 → 65 kg × 5; readiness 36.25 → 29.75 → MODIFIED; SSU session 25.0; EWMA ratio 1.41 on day 42; squat 100 → 102.5 kg; dumbbell row 30 → 32.5 kg × 13; calibration 20 → 25 → 30 → 32.5 kg, e1RM 44.4; ramp 20/50/70/85 kg; substitution 0.955 and 0.760 with the same ranking of five rows.
- **Independent review:** a separate reviewer, who had not written the code, checked the safety kernel against the registry in two passes. It found 14 defects (for example, a painful knee could still be given jump rope; 20 minutes of Z3 was not counted as HIIT; a 30-day illness skipped the layoff ramp). All are fixed, each with a regression test, and missing safety inputs now fail closed.

## Things you should know
1. **One permission added for Android 12 phones (D-036).** On Android 12/12L the rest-timer alarm needs "schedule exact alarm"; it is granted automatically there and you will see no prompt. Phones on Android 13+ are unaffected. The app still has no internet permission, and the build fails if one ever appears.
2. **The 30-minute example comes out slightly differently (D-034).** With the approved time model, the Phase 1 "60-minute" plan models at about 67 minutes. Squeezed to 30 minutes it keeps squats 3 × 5, bench and Romanian deadlifts 2 × 10 and 4 rowing intervals, as in Phase 1, but also drops the cable row (Phase 1's illustrative table kept it). Your real session times will tune this.
3. **Light dumbbell jumps (D-043).** Going from 10 to 12.5 kg is a 25% jump, more than the approved weekly limit. The engine now lowers the rep target to stay within the limit, or holds and suggests another variation when even the bottom of the rep range would be too big a jump.
4. **Tests use JUnit 4 rather than the Kotest library named in Phase 2 (D-029),** so the engine can be tested here as well as on GitHub.

## Risks and open issues
| Risk | Impact | Mitigation |
|---|---|---|
| Android versions chosen without access to the download servers (D-035) | First GitHub build may need small version fixes | First CI run is the check; fixes are routine |
| New coefficients (modality joint stress D-042, fit functions D-033, time model) | Recommendations slightly off | Labelled REQUIRES VALIDATION; tuned in Phase 4 with simulations and your data |
| Week-level balance is reported, not yet auto-corrected | None yet (no week planner exists) | Week planner in Part 2 re-plans on any issue |

## What I need from you
1. **GitHub** — sign in at github.com (or create a free account), create a new **private** repository (for example `personal-fitness-coach`, with no README), connect GitHub in claude.ai under **Settings → Connectors**, give it access to that repository, and reply with the repository name. Then I can push the code and run the first real Android build.
2. **Your phone model and Android version** (Settings → About phone).

## Next: Part 2
Exercise library (original wording), weekly templates and the full session-generation pipeline wired through the safety validator, plus the remaining engine rules and their tests.
