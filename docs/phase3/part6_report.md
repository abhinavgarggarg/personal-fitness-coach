# Phase 3, Part 6 report: history, progress, the coach and Ask Gemini

Date: 11 October 2026 · Rule Registry 1.1.1 · Health-condition table 1.0.1 · Library 1.1.1 · Decisions D-092 to D-098

## Summary
The app now has four tabs at the bottom: Today, History, Progress and Coach.

- **History:** every session you finished. You can open one to see each set, and correct or delete a set if you logged it wrong.
- **Progress:** your strength trend for each lift, your active minutes each week against the 150-minute goal, the weeks you trained, milestones, weight and waist as averages, and steps.
- **Coach:** answers 12 common questions about your plan, such as "Why is today's workout different?" or "I only have 40 minutes", using what the app actually decided. "Why?" shows the rules behind a decision and how strong the evidence is. Questions about symptoms, medicines, health conditions, pain, illness, diet or supplements are never answered. You are pointed to the right person, or to the warning signs.
- **Ask Gemini:** shows you exactly what would be shared before it hands your question to the Gemini app on your phone. The app still has no internet access.
- **Settings:** you can now change your goals and weight tracking, your training days, your gym and machines, and your injuries. You can also bring back exercises you skipped and choose a dark or light look.

All 152 rules now have their tests (152/152), which is the target for the end of Phase 3.

An independent reviewer found 16 problems: 5 high, 6 medium and 5 low. All are fixed with tests. Two more rounds of checking found 3 further problems that matter and 4 small ones, also fixed.

- **Tests:** 638 automated tests pass on GitHub.
- **Devices:** the device tests pass on five phone and tablet set-ups.
- **Screen pictures:** the new screens are added to the screen pictures.

## What was built

| Area | Rules | Status |
|---|---|---|
| **Tab bar:** Today, History, Progress, Coach, as text labels for now (icons and the new look come in Part 7). It is hidden during the check-in, the workout and its safety screens | Phase 2 §5 | VERIFIED |
| **History:** finished, ended-early, stopped and skipped sessions, newest first, 12 weeks at a time; after a long break it reaches back to your last session. Opening a session shows every set and the decisions behind it, each with "Why?" | Phase 2 H1/H2 | VERIFIED |
| **Correcting a past set:** buttons, no typing. The same limits apply as when logging. Your estimated maximum and next targets are worked out again, and the correction is recorded. Saving without a change does nothing. Delete asks once more (D-096) | FS-5, PROG-001, INT-004 | VERIFIED |
| **Progress:** see the section below the table | FL-005, PH-001, ADH-001/002, STEP-001 | VERIFIED |
| **Exercise page:** your history for one exercise, your best set and next target. You can mark it as a favourite, or say "don't suggest this", which takes it out of plans and swap options until you bring it back in Settings | SUB-002, ADH-003 | VERIFIED |
| **Coach:** see the section below the table (D-092, D-093) | SAF-009, COACH-001, FS-9 | VERIFIED (152/152) |
| **"Why?"** (on Today, the preview, the summary, a load change during a workout, history and coach answers): the plain-language reason, then each rule's title, its ID and how sure the evidence is, in plain words | Phase 2 §9 | VERIFIED |
| **Ask Gemini:** see the section below the table (D-094) | D-045 | VERIFIED |
| **Settings:** goals and weight tracking (only the goals your health answers allow), training days, gym and machines, injuries. These use the same pages as onboarding, and the week's plan follows at once. Also skipped exercises and the look (dark by default, light, or the phone's setting) (D-098) | FL-001, FREQ, PROG-003, MOD-001, IND-001, A9 | VERIFIED |

**Progress** shows:
- strength per lift, from your estimated maximum, with its trend;
- active minutes each week against the 150-minute goal with 2 strength days, shown as bars;
- weeks in a row, which appear once you have some, and the weeks you trained this year, which never go down;
- milestones, which stay once earned;
- weight as weekly averages, with the 7-day average and the 4-week trend. You enter weight with nothing filled in for you. Waist is the average of up to three readings;
- steps for the week.

Nothing about weight shows when weight tracking is off (D-097).

**The coach:**
- It suggests 12 questions, and you can also type your own.
- Everything runs on the phone, and the answers come from your plan, the decision log and the rules.
- It can only open a screen or ask the engine for a shorter or easier session. It can never make training harder, and it never sets weights or reps.
- Text it can't place gets the three closest questions and a "Feeling unwell?" link.

**Ask Gemini:**
- You first see the exact text: today's session and level, its exercise names, the reasons, and your question.
- That text never includes health, pain, illness or body details.
- Questions about symptoms, medicines, medical problems, pain or illness are not shared; the referral is shown instead.
- Pressing Share sends the text to the Gemini app, or to the share sheet if Gemini isn't installed.

**Rule coverage: 152 of 152 rules** have all their registry test cases (304 of 304 test IDs, `docs/phase3/rule_coverage_part6.md`). SAF-009 is covered by `TC-SAF-009a` and `TC-SAF-009b`, which check that the coach refers rather than answers and that it can't write or raise anything. COACH-001 is covered by `TC-COACH-001a` and `TC-COACH-001b`, which go through every word on screen looking for body-shaming, guilt, punishment, hype and medical claims, and check that answers are brief and that referrals name a professional.

## How it was checked

**Locally:**
- 610 tests pass: engine 408, data core 159, app screen logic 43.
- The strings check passes: 950 strings, all used, none written directly into the code, none defined twice.
- The reason-text check passes: 159 reasons.

**GitHub CI** (run 38151728188, green): 638 tests pass, with the permission list unchanged (6 permissions, no internet) and the debug APK built. The tests are:
- engine 408;
- data 176, including the real database;
- app 54, including the Part 6 screens on Robolectric, text at 200% and touch-target size.

**On five emulated devices** (Android 10 small phone, 12, 14 at 200% text, 16 phone, 16 tablet; Devices run 38152039848): 9 of 9 device tests pass on each. A new device test drives the tab bar, a coach answer, "Why?", the Ask Gemini text, History and Progress. It runs next to the Part 5 device tests (a workout from Today, the rest alarm, encrypted backup and restore, erase, step counter without permission, no internet).

**Screen pictures:** 9 new pictures (37 in all, `docs/phase3/screens/` 29–37): History, a session's sets, Progress, the coach (a question, an answer, a referral, "Why?", Ask Gemini) and the goals editor. They were rendered on GitHub and checked by eye. They led to these fixes:
- the rule codes on answer cards were removed;
- "Weeks in a row: 0" is now hidden;
- the "measure your waist again" note now appears only after a first measurement.

## Independent review
A separate reviewer, who had not written this code, tested Part 6 against the approved design and the safety rules. It drove the app exactly as the screens do. It found **16 problems** (R6-01 to R6-16). All are fixed with tests (`CoachTest`, `InsightsTest`, `Part6ReviewFlowTest`):

| # | What was wrong | Fix |
|---|---|---|
| R6-01 (high) | The coach missed many ways of describing warning signs ("my chest is tight", "I fainted", "chest pian", "shortness of breath") and sent "my chest hurts" to the pain route | Warning signs are checked first, with word endings and small typing slips. Chest or heart with pain, tightness or pressure always counts. Text the coach can't place offers the warning signs (D-093) |
| R6-02 (high) | Questions mentioning medicines, pregnancy, a condition, surgery, a doctor's advice or an injury got normal coaching answers | All of these are referred, whatever else the question says (D-093) |
| R6-03 (high) | After the pain gate stopped the day, the coach's "shorter session" or "easier level" could still lead to a workout | One check for every way into a session (D-095) |
| R6-04 (high) | The check-in opened by the coach started blank, so a hard morning could be undone and the day raised to full | It starts from the morning's answers (D-095) |
| R6-05 (high) | "Why?" opened during a workout showed the tab bar. Leaving through it lost the workout's place, could redo the check-in or change the gym mid-session, and stopped the rest alarm | No tabs over a workout. Today, the tabs and the editors lead back to the workout, and the alarm keeps running (D-095) |
| R6-06 (medium) | Ask Gemini shared any question, including "I have chest pain", and described a session on a stopped day | Symptom, medicine, medical, pain and illness questions get the referral instead. Stopped days share no session (D-094) |
| R6-07 to R6-11 (medium) | Several smaller errors:<br>• "I only have 40 minutes" answered 30.<br>• Asking for 90 minutes offered a longer session.<br>• "Why did my target change?" found nothing for one exercise.<br>• Milestones vanished or were wrong.<br>• History looked empty after a break.<br>• Daily weights were charted against the screen's own advice | Each fixed (D-096, D-097) |
| R6-12 to R6-16 (low) | Smaller items:<br>• A correction saved without a change was still logged.<br>• Delete didn't ask first.<br>• "Why?" lost the answer it came from.<br>• Weight entry started at 75 kg.<br>• Some wording ("Skipped · not done", rule codes, "Confidence: Product Rule") | Fixed and tested |

The reviewer then **re-checked the fixes**. 15 were fully fixed and 1 partly. It found 4 more problems, all now fixed:

| # | What was wrong | Fix |
|---|---|---|
| N1 (high) | Illness answered in the morning could be cleared by a later check-in, raising the day back to full | Illness answers stay for the day: a later check-in can add, never remove, and the forms start filled in (D-095) |
| N2 (medium) | The wider word lists referred ordinary training questions ("Medicine ball throw", "I train at lunch", "a heavy chest session", "I'm sick of lunges") | Exercise names and everyday phrases are taken out first. About 40 such questions are now in the tests as must-not-refer cases |
| N3, N4 (low) | A few more warning signs: tingling, blurred vision, pain down the arm or in the jaw. Typing "Why did the app reduce my training?" wasn't understood | Added |

**A final check** found the new typing-slip rule taking "fantastic", "faith" and "lighthearted" for warning signs. Now a slip can't change the first letter and must keep a normal word ending, and "feel" counts only when it is next to "chest" or "heart". These cases are in the tests too.

## What this means for you
- **The coach explains; it doesn't advise on health.** Ask it about your plan. For anything about symptoms, medicines, a condition, pregnancy, pain, illness, food or supplements, it tells you who to ask, or shows the warning signs. If it isn't sure, it refers you rather than guessing. You may occasionally see a referral for an ordinary question; that is on purpose.
- **Ask Gemini shows you the text first.** Only your session, its reasons and your question go to Gemini; your health answers and body measurements never do. Gemini's answer stays in the Gemini app and never changes your plan.
- **Fixing a logged set** updates your next targets, and the change is recorded. Deleting a set can't be undone.
- **Changing goals** in Settings changes the weekly mix from this week. The year's block order stays as planned, as for health-condition changes (D-086).
- **The coach is English-only for now.** Text in other languages gets the suggested questions and the warning-signs link.

## Known limits
- Tested on emulators only. **Phase 4 (testing and UAT) puts the app on your Motorola Edge 50**, where Ask Gemini can be tried with the real Gemini app.
- The look is still plain. Part 7 (CR-007) brings the new design, icons, illustrations and the exercise animations (CR-004).
- Units are kilograms and centimetres only (D-097).
- Before any public release, the condition table and the coach's wording still need a clinician and a regulatory review (D-058).

## Phase 3 plan (8 parts, D-091)

| Part | Content | Status |
|---|---|---|
| 1 | Engine core, safety kernel, project foundation | Done |
| 2 | Exercise library, 12-month programme, week planner, session generator | Done |
| 3 | Research Update 1.1 in the engine | Done |
| 4 | Data layer: storage, history → engine, steps, own numbers, backup and restore | Done |
| 5 | Screens: onboarding, today, check-in, preview, workout and rest timer, summary, safety stop, settings | Done |
| 6 | History and progress, coach and "Why?" (SAF-009, COACH-001), Ask Gemini, more settings | **Done (this report)** |
| 7 | Look and feel (CR-007): 2–3 visual directions for you to choose from, then the chosen design applied everywhere; exercise animations (CR-004) | Next |
| 8 | Integration, research-update workflow, Phase 3 gate (152/152 rules) | — |

```
================================================================================
PHASE 3 — PART 6 OF 8 COMPLETE. Reply CONTINUE.
================================================================================
```
