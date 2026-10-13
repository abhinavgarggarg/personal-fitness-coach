# Phase 3 gate report: the app is built and ready for testing on your phone

Date: 13 October 2026 · Rule Registry 1.1.2 · Health-condition table 1.0.1 · Library 1.1.1 · Decisions D-106 to D-109 (Part 8), D-029 to D-109 (Phase 3)

This report has two halves. The first covers **Part 8**, the last part of Phase 3. The second is the **Phase 3 gate report** for the whole phase, in the same format as the Phase 2 gate. It ends with the sign-off request.

## Summary
Phase 3 is complete. The app does everything the Phase 2 specification asks of version 1:
- onboarding, the daily check-in, a planned session for the day, and a guided workout with a rest alarm;
- "Something hurts" and warning signs, history and progress;
- an offline coach that explains every decision;
- backup and restore, settings, the "Glow" look, and a moving figure for every exercise.

All **152 of 152** training rules have their tests. Everything runs on the phone, with no internet permission.

Part 8 checked the app as a whole, the way you will use it:
- **Three people went through 12 weeks each** inside the tests. Along the way the app was killed mid-workout, a week was missed, pain was reported mid-set, a warning sign stopped training, the time was changed, and a backup was restored. No step failed, nothing was lost, and the lifts got stronger.
- **New research now reaches the rules only through a checked change set**, approved by you (D-106). The first one, Registry 1.1.2, records the source checks made before Phase 4. It changes no rule.
- **The release version of the app is built and checked on every change.** It is 4.2 MB (the limit is 15 MB), contains no tracking or advertising libraries, and starts in about 1 second on an emulated phone (D-108).
- **A session now takes the time it promises.** When time is short, two exercises that don't compete can be done as a superset, alternating their sets (D-109).
- An independent reviewer found **10 problems** (1 high, 4 medium, 5 low). Its re-check found 3 more small gaps. All 13 are fixed.

**One important issue needs your decision before or early in Phase 4.** The two weekly safety limits (VOL-007 and LOAD-005) stop training from growing too fast. They work, but they hold most people's weeks well below the plan: in the test journeys, about half to four fifths of the planned work. After an interrupted week they can keep shrinking (see "The weekly limits" below). It is safe, but not yet a good programme. The rules' own uncertainty note says their numbers must be checked in Phase 4. I recommend this as the first Phase 4 task, and the options are set out below.

## Part 8: what was done

| Area | What it means for you | Status |
|---|---|---|
| **Journeys through the whole app** (integration) | Three 12-week journeys run through the same calls the screens make, on the real data layer and engine:<br>• a 41-year-old lifter in a full gym;<br>• a 66-year-old beginner at home with type 2 diabetes and knee osteoarthritis;<br>• a 35-year-old with bodyweight only.<br>Events along the way: the app killed mid-workout and resumed; a missed week; joint pain mid-set; a warning sign at the check-in, confirmed two days later; a 30-minute day; a backup exported, everything erased and the backup restored. After every step, the app showed no error, the stored history matched what was done, and at least one lift progressed for each person. The clock moves as in a gym (warm-up, rests, cool-down), so the measured session loads are realistic | VERIFIED |
| **Weekly limits spread over the week** (D-107) | The weekly limits used to be checked one session at a time, so the last session of a week could be cut to a warm-up. Now each session gets its fair share of what is left that week. A day with nothing left is shown as a recovery day with the reason, never as an empty workout. When the cuts went further than needed, the sets that still fit are given back | VERIFIED; tuning open (below) |
| **Sessions take the time they promise** (F10, D-109) | Shorter rests and supersets now reach the workout, not only the time estimate. Over 39 planned days × 5 time budgets, no session ran more than 10% over its time. At 30 minutes, when even the shortest full session doesn't fit, the preview offers the express session with the reason, and your "no thanks" is kept for the day. "Superset with …" shows in the preview and the workout. Skipping, "do it later", pain or a swap end a superset correctly | VERIFIED |
| **Research-update workflow** (D-106, `docs/research_update_workflow.md`) | A change set lists each rule change with its reason, sources, approval and, for safety rules, a written safety review. A tool checks it, reports what it would change, and applies it. CI rebuilds the rules from the approved history and fails on any difference. Only you can approve a change to a rule's wording, numbers or confidence | VERIFIED |
| **Source check before Phase 4** (Registry 1.1.2) | The sources Research Update 1.1 had only located were looked up again. Six are now checked, from the full text or the abstract:<br>• the WHO 2020 activity guidelines;<br>• the 2025 Lancet daily-steps meta-analysis;<br>• ACSM's 2015 screening update;<br>• how well people judge reps in reserve (2022);<br>• the metabolic-syndrome definition;<br>• ACSM's weight-loss activity guidance (2009).<br>Several citations were completed, and one wrong link was corrected. Nine could not be read from here and stay listed as pending, each with the reason. No rule changed | VERIFIED; applied under delegation for your review |
| **Release and quality checks** (D-108) | On every change, CI builds the shrunk release app and checks its size, permissions, app ID and minimum Android version. It also checks every library in it against an allow-list and measures how much of the engine the tests cover (96.8% of lines; the floor is 90%). On an emulated Android 14 phone, the release app is installed, taken through onboarding to today's session, and timed from a cold start. It is signed with a throw-away key made and deleted inside the test; the real key never leaves GitHub's secrets | VERIFIED |
| **Device tests** | The screen checks now wait for a still screen, which fixes the occasional false failure noted in CR-008. One late run failed on Android 10: in the test, the screens could update from the controller's thread. They now always take the controller's values in on the main thread, as on a phone | VERIFIED |
| **Acceptance traceability** (`docs/phase3/acceptance_traceability.md`) | Each Phase 2 acceptance criterion and quality target is matched to the test that proves it, or marked for your phone | Done |

## How it was checked
**On this computer:**
- 656 tests pass: engine 418, data core 169, app logic and figures 69.
- Rule coverage is 152/152 (304 of 304 registry test cases).
- The research-update tool's own 13 tests pass, and the rules rebuilt from the approved change sets match the registry exactly.

**On GitHub:**
- **CI** (run 38337218162 for the last code change, 1df62b9) is green. 689 tests pass: engine 418, data 186 and app 85 (3 of the app tests only draw the screen pictures, so they run in the Screenshots workflow).
- **Release app:** 4.21 MB, debuggable off, 6 permissions with no internet, 106 libraries all on the allow-list.
- **Engine line coverage:** 96.8%.
- **Devices** (run 38337237942): 9 of 9 device tests pass on each of the five emulated devices (Android 10 small phone, 12, 14 at 200% text, 16 phone, 16 tablet). The release check passes on Android 14.

**Measured:**

| What | Target | Measured |
|---|---|---|
| Plan a session (NFR-02) | under 500 ms | median 0.5–0.9 ms, slowest under 10 ms (300 sessions) |
| Log a set (F6, NFR-03) | under 100 ms | median 0.5 ms on the test machine; the phone's database adds a few ms |
| Replacement options (F8) | under 300 ms | median 3–7 ms |
| Cold start to Today (NFR-01) | under 1.5 s | medians of 970–1,152 ms over the last three runs (1,027 ms on the last), on an emulator with software graphics; confirmed on your phone in UAT |
| App size (NFR-10) | under 15 MB | 4.21 MB |
| Time budgets (F10) | within 10% | 68 sessions shortened within 10%; 26 switched to the express session at 30 minutes; 87 needed less than the time given |

## Independent review
A reviewer who had not seen the work being built checked Part 8. It read the code, ran the tests, and wrote its own probe tests. It found **10 problems**:

| # | What was wrong | Fix |
|---|---|---|
| R8-01 (high) | An interval block cut for the weekly limits was still shown and played at full length, for example 4 × 4 minutes when 14.5 minutes had been approved | Interval blocks are shortened by whole repeats, so what you see is exactly what was approved. A test checks it |
| R8-02 (medium) | A session much harder than predicted was counted as predicted, leaving too much room for the rest of the week | Each session done counts at its measured load (effort × minutes) or as predicted, whichever is higher |
| R8-03 (medium) | A day emptied by the weekly limits still kept the bone-loading block for osteoporosis | A recovery day has no bone-loading block, as on any recovery day |
| R8-04 (medium) | After "do it later" or skipping one half of a superset, the other half kept the short superset rest and the player jumped back to the deferred exercise | The superset ends; the exercise gets its own rest; the label follows |
| R8-05 (medium) | "I only have N minutes left" dropped both halves of a superset | Only the exercise that is cut goes |
| R8-06 to R8-10 (low) | • A weight still being found was counted too low in the week's plan.<br>• A swap could keep a superset that the gym rules forbid.<br>• A missed earlier day didn't keep its share of the week.<br>• The express session came back after you turned it off.<br>• Some tests could pass without checking their point, and the superset timing assumed the wrong order | All fixed, each with a test |

**The reviewer then re-checked the fixes.** Nine were fully fixed. It found 3 more small gaps, all now fixed:
- the superset order (the exercise with more sets now goes first, as timed);
- going back to the same exercise kept the 60-second superset rest;
- a rare tempo-interval case.

**After the review**, the realistic journeys showed that a day whose conditioning alone was over its weekly share lost all its other sets too. The sets that still fit are now given back (D-107, step 5, with a test). This removed the journeys' last recovery days, from 1, 1 and 0 to none.

## The weekly limits: an open issue for your decision
Two approved rules stop training from growing too fast:
- **VOL-007:** a week's total training stress may be at most 15% above the average of the last three weeks.
- **LOAD-005:** the week's predicted effort-load may be at most 20% above that average.

Both are "Expert Practice" rules. Their Phase 1 uncertainty note says their numbers are judgement, to be checked in Phase 4.

In the journeys they work, but they keep most weeks well below the plan. This is the share of the planned work kept each week (weeks 1 to 12; "–" is the missed week):

| Person | Weekly share of the plan | Overall |
|---|---|---|
| Beginner 66, trains every week | 94 94 78 81 71 66 67 73 74 80 85 94 % | 80% |
| Bodyweight 35, one missed week | 100 100 100 65 59 60 – 52 62 63 63 64 % | 68% |
| Lifter 41, a missed week and a warning-sign stop | 89 89 83 50 – 59 52 35 28 23 16 21 % | 49% |

**Why:**
1. The week planner increases the plan faster than the limits allow, so sessions are cut after they are planned. That is also why the reason "kept under your weekly load limit" shows on most sessions from week 4.
2. The limits compare with your own recent weeks. A week cut short, like the lifter's week with a warning-sign stop and one session, pulls the average down. The following weeks then shrink with it.

None of this is unsafe; it is too cautious to be a good programme. Changing it changes how two approved rules behave, so it is your decision. It would go through the new research-update workflow as a MINOR change.

**My recommendation, as the first Phase 4 task:**
- **(a) Plan within the limits.** The week planner plans each week within the limits, so sessions aren't cut afterwards and the reason isn't shown every day.
- **(b) Treat a week cut short like a missed week.** A week with fewer than half its planned sessions doesn't count in the 3-week average. A missed week already doesn't. In a quick trial, this stopped the lifter's slide: weeks 9 to 12 kept 34–43% instead of 16–28%.
- **(c) Re-check the two numbers (1.15 and 1.20) in Phase 4's simulations**, with a floor, so the limits only ever stop spikes. The floor would be that a week is never cut below the programme's maintenance volume.

If you prefer, I can make (a) and (b) before Phase 4 testing on your phone starts. You can say so in your reply.

## Phase 3 gate report — Application development sign-off

The app is built as specified in Phase 2. Development ran in 8 parts (D-091), plus one extra change request (CR-008). Each part was reviewed independently and checked on GitHub and five emulated devices before the next began.

**1. Completed work.**

| Part | Content |
|---|---|
| 1 | Project foundation; the engine's calculators; progression and calibration; time fitting; the safety kernel (screening, warning signs, illness and pain gates, limits, the final check of every session) |
| 2 | Exercise library (152 exercises, original wording); 12-month programme; week planner; session generator |
| 3 | Research Update 1.1 in the engine: "Lose fat, keep muscle" for adults 30+, the age-banded mix, 21 health-condition profiles, steps, starting from your own numbers, the away-from-gym day |
| 4 | Data layer: storage on the phone, history feeding the engine, the step counter, backup and restore with an optional password |
| 5 | The screens: onboarding, Today, check-in and preview, the workout with its rest alarm, the summary, the safety stop, settings |
| 6 | History and progress, the offline coach and "Why?", Ask Gemini (the exact text shown first), more settings |
| 7 | Look D "Glow", your choice, and a moving figure for each of the 157 exercises |
| CR-008 | Sculpted, shaded figures with the working muscles lit |
| 8 | Integration journeys, the research-update workflow, Registry 1.1.2, release checks, time promises and supersets, acceptance traceability |

**2. Key decisions** (all logged in `DECISIONS.md`, D-029 to D-109):

| ID | Decision |
|---|---|
| D-035, D-044 | Kotlin 2.4, Compose, Room 3; any phone or tablet with Android 10 or newer |
| D-045 | No paid AI: the offline coach explains everything; Ask Gemini only shares text you see first |
| D-058 | Health conditions come from a versioned data table (clinician review before any public release) |
| D-060 | "Lose fat, keep muscle" is the default goal for adults 30+; no food or calorie features |
| D-073, D-075, D-079 | One private database on the phone; backups with an optional password; Android's own backup only when end-to-end encrypted |
| D-083 | Screen logic in plain Kotlin, tested without a phone |
| D-099, D-105 | The "Glow" look; computed, sculpted exercise figures |
| D-106 | Research updates only as checked change sets that you approve |
| D-107 | Weekly limits spread over the week; recovery days instead of empty sessions |
| D-108 | The release build and its libraries are checked on every change |
| D-109 | Sessions take the time they promise, with supersets |

**3. Assumptions.**
- One user per phone, English only, kilograms only (D-097).
- The app is installed from GitHub Releases, not the Play Store (Phase 5).
- Your Motorola Edge 50 on Android 16 is the main test phone. The emulators stand in for other phones.

**4. Open issues** (none blocks moving to Phase 4, where they are handled):
1. **Weekly limits too cautious** (above). Your decision on (a)–(c); recommended as the first Phase 4 task.
2. **Extra time isn't filled.** On a day planned for 60 minutes when you give 90, the session stays at 60 minutes. The rule for filling time (TIME-003) is built and tested but not switched on, because it adds training. Your decision in Phase 4.
3. **Pounds.** Units are kilograms only (D-097); pounds would come as a later change if you want it.
4. **Sources still pending.** Nine sources were found but their text could not be opened from here. Each is listed in the registry with the reason. They back:
   - the notes for pregnancy, diabetes, asthma and cancer;
   - the regulatory position;
   - the guidance for older adults (AGE-001, FL-003);
   - the estimated-max formula (INT-004);
   - how steps are measured.

   Two rules rest on one of them alone: INT-005 (choosing a weight from the estimated max, LeSuer 1997) and WU-004 (a longer warm-up from age 50, Fragala 2019, whose abstract was checked). Both are standard practice; the sources are re-tried in Phase 4.
5. **Registry 1.1.2 for your review.** It was applied under delegation as an evidence-only change: sources and verification notes, no rule wording, numbers or confidence. Please review `rules/changes/registry_1.1.2.json`; if you disagree, it is undone with the same workflow.
6. **Things only your phone can show (UAT):**
   - the rest alarm on a locked phone;
   - start-up time;
   - onboarding in under 5 minutes;
   - a TalkBack pass;
   - the look in gym light;
   - the 157 figures;
   - one-handed use.
7. **The express-session choice is remembered until the app closes,** not after a restart. That is minor.
8. **Before any public release:** a clinician and a regulatory review of the condition table and the coach's wording (D-058).
9. **Housekeeping:** two old test branches on GitHub, `probe/gpu` and `probe/pre-cr008`, can be deleted from the GitHub website. I can't delete branches from here.

**5. Risks.**

| Risk | Mitigation |
|---|---|
| The weekly limits keep training too low, so progress is slow | Tuning is the first Phase 4 task (open issue 1); the journeys now report the kept share on every CI run, so any change is measured |
| Rest alarms delayed by the phone's battery saver | Exact system alarms; tested on your phone in UAT, with a one-screen guide if needed |
| Emulators don't show everything a real phone does | Phase 4 UAT on your phone; the release build is already tested on an emulator |
| Expert-practice numbers may need tuning | Phase 4 simulations and your feedback; every change goes through the research-update workflow |
| Health content used beyond personal use | Clinician and regulatory review before any public release (D-058) |
| Losing the signing key would block updates | The key lives only in GitHub secrets plus your own backup copy (Phase 5) |

**6. Deliverables and status.**

| Deliverable | Status |
|---|---|
| Engine (pure Kotlin, 152 rules) | Complete; 418 tests; 96.8% line coverage |
| Exercise library 1.1.1 and condition table 1.0.1 | Complete; condition table REQUIRES clinician review before a public release |
| Data layer (storage, history, steps, backup) | Complete; tested on the JVM and on 5 emulated devices |
| Screens, coach, settings, look and figures | Complete; screen pictures in `docs/phase3/screens/` |
| Research-update workflow and Registry 1.1.2 | Complete; 1.1.2 for your review |
| Release build and quality checks | Complete; run on every change |
| Acceptance traceability | Complete: 46 of 57 criteria proven by automated tests (6 of them also to be confirmed on your phone), 8 only on your phone, 1 is the Phase 4 suite of 30 scenarios, 2 open plus pounds (open issues 2 and 3) |
| Weekly-limit tuning | OPEN — Phase 4 |

**7. Validation status.**
- **Tests:** 656 locally; 689 on GitHub, all passing.
- **Devices:** 9 of 9 device tests on 5 emulated devices, plus the release-build check.
- **Rules:** 152/152 rules covered (304/304 test cases).
- **Journeys:** three 12-week journeys without an error. Engine line coverage is 96.8%.
- **Checks:** permissions (no internet), libraries, contrast, strings, backup rules and the 16 KB page check all pass.
- **Independent reviews:** 185 findings across the eight parts, all fixed.
- **Not yet tested:** nothing has run on your own phone; that is Phase 4.

**Before Phase 4 starts**, I'll send step-by-step instructions to install the test app on your Motorola Edge 50 from GitHub, and a short UAT checklist.

```text
================================================================================
PHASE 3 SIGN-OFF GATE
Please review the completed Phase 3 Application Development (Parts 1–8).
Reply 'APPROVED' to advance to Phase 4 (Testing, Validation & UAT),
or give feedback / adjustments.
================================================================================
```
