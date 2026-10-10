# Phase 3 — Part 4 report: the data layer (storage, history, steps, backup)

Date: 10 October 2026 · Rule Registry 1.1.1 · Health-condition table 1.0.1 · Library 1.1.1 · Decisions D-073 to D-082

## In one paragraph
The app now remembers everything the coach needs, on the phone only, and the engine plans from that history:
- **Storage:** one private database on the phone. Workouts are stored set by set, and the other records (profile, conditions, check-ins, weight, waist, steps, your own numbers) are stored as separate, versioned entries.
- **The coach learns from your history:** your estimated max, the next prescription, calibration and weekly totals all come from the sets you log. Breaks, illness, deloads, red flags and health conditions are all applied from what is stored.
- **Steps:** the phone's own step counter is read when you open the app, and only after you allow "physical activity".
- **Backup and restore:** one file, with an optional password (strong encryption). Restoring checks the whole file first, saves your current data, and then replaces everything in one step.
- **Privacy (DATA-001):** only the listed data items are stored, with no free-text notes. "Erase all my data" leaves nothing readable behind, and Android's own backup is allowed only when it is end-to-end encrypted.

An independent reviewer tested the part and found 32 problems; all are fixed with tests. A second check of those fixes found 13 more (4 high, 5 medium, 4 low), also all fixed and tested. **516 automated tests pass on GitHub** (engine 408, data 107 including the real database, app 1), and the device tests pass on five phone and tablet set-ups (results below).

## What was built

| Area | Rules | Status |
|---|---|---|
| Database: Room 3 on the phone's own SQLite, private storage. Workouts → exercises → sets with foreign keys, plus the one open session; every other record is a typed, versioned entry. Schema version 1 exported and checked in CI; no destructive migrations ever (D-073, D-074) | DATA-001, SAF-008 | VERIFIED (local contract tests; Room tests under Robolectric and on devices in CI) |
| 22 record types besides workouts, each mapped to the DATA-001 list (required / optional / never). A test fails if a record stores an item that is not on the list, or one the list excludes | DATA-001 | VERIFIED |
| Session logging: start, log a set, swap, finish, discard, correct or delete a past set (history and state rebuilt), each one atomic. Sessions done on another day keep their planned weekday | SAF-008, PROG-*, CAL-001 | VERIFIED |
| From history to the engine: estimated max, prescriptions, calibration loads found (handover after the 4th calibration session), weekly totals, workload ratios (EWMA only from real weeks; personal k = median), block clock (pauses for weeks the app was not opened) | E1RM, PROG-*, CAL-001/002, VOL-007, WL-* | VERIFIED |
| Return to training applied from stored history: days off, missed sessions and illness give the REG-002 to REG-005 ramps. A miss inside a ramp keeps the ramp. After 56+ days each exercise recalibrates once, and intervals come back after 3 weeks (D-076). Ramp loads are always scaled from the level before the break (D-082) | REG-002–005, HIIT-003 | VERIFIED |
| Red flags at any time (check-in, start of a session, during a session): the session ends, the stop stays until you confirm, and the first session back is LIGHT. A stopped session counts towards limits, never towards progression (D-081) | SAF-002 | VERIFIED |
| Deloads: lighter week (3 sessions capped at MODIFIED) and mid-block deloads; the week after any deload adds 1 rep in reserve (DEL-004) | DEL-002, DEL-004 | VERIFIED |
| Type 2 diabetes: no strength on consecutive days, whichever planned day is opened; the final safety check enforces it | SAF-010 (D-071) | VERIFIED |
| Weight and waist entries feeding the 7-day average, 4-week trend and waist averaging; your own numbers (recent set or best lift) | FL-005, CAL-002 | VERIFIED |
| Steps: read on app open (no background work), the "physical activity" permission is asked only when you turn steps on, restarts are detected by boot count, steps are split across days in proportion to time, and anything over 250 a minute is treated as a glitch (D-078). Brisk walks are counted once | STEP-001, STEP-002, D-062 | VERIFIED (ledger locally; sensor under Robolectric and on devices) |
| Backup file with an optional password: AES-256-GCM, PBKDF2 600,000 rounds, nothing readable without the password. Restore: full check → safety copy → replace in one step; what is restored is re-written as this app writes it (D-075) | DATA-001 | VERIFIED |
| Android system backup only when end-to-end encrypted (D-079); "Erase all" zeroes deleted data and compacts the file; the decision log keeps about a year (D-080) | DATA-001 | VERIFIED (CI check `check_backup_rules.py`; erase checked in the database files on a device) |
| Exercise library and condition table stay inside the app (not copied into the database); records refer to them by ID and version (D-077) | — | VERIFIED |

Rule coverage: **150 of 152 rules** have all their registry test cases (`docs/phase3/rule_coverage_part4.md`). DATA-001 is now covered. SAF-009 and COACH-001 (the coach's limits and voice) come in Part 6.

## How it was checked
- **Locally:** 498 tests pass (engine 408, data core 90; Gradle's bundled Kotlin 2.0.21).
- **GitHub CI** (Kotlin 2.4.0, run 38013819308, green): 516 tests — engine 408, data 107 (the 90 above plus the Room storage tests on the same contract as the local in-memory store, and the step-sensor tests under Robolectric), app 1. It also runs the backup-rules check and the permission allow-list (6 permissions, still no internet), and builds the debug APK.
- **On five emulated devices** (Android 10 small phone, Android 12, Android 14 at 200% text, Android 16 phone and tablet; Devices run 38013824144, 6 of 6 tests pass on each), each device:
  - plans a workout, logs it, makes a password-protected backup with the phone's own encryption, erases everything and restores it identically;
  - erases everything again and searches the database files for a marker (none found);
  - confirms the step counter stays silent without permission and that data opens in private storage.
- **Simulations run through the real storage:**

| Simulated user | Result |
|---|---|
| Intermediate, 3 days, full gym, 10 weeks (30 sessions) | Every stored session passes the final safety check and the weekly load cap holds. Calibration hands over to progression, and 4 lifts have an estimated max that keeps rising. State built step by step equals a rebuild from the logged sets. A full backup restores identically |
| 62, beginner, fat loss, type 2 diabetes, own numbers, steps on, 8 weeks (24 sessions) | Never strength on consecutive days. Steps recorded every day, and a step target set once the first week existed. A recent own number set the starting max and an old one only capped calibration. Backup round trip identical |

## Independent review
A separate reviewer, who had not written this code, tested Part 4 by running each scenario against the built code. It found **32 problems** (R4-01 to R4-32). All are fixed, each with a test (`Part4ReviewRegressionTest`), or with a CI check for the Android backup rules. The main ones:

| # | What was wrong | Fix |
|---|---|---|
| R4-01 | Breaks and illness were not read from history, so a session after weeks off came at full load | REG-002 to REG-005 applied from stored days off, misses and illness |
| R4-02 | A red-flag stop was forgotten the next day | Kept until you confirm; first session back LIGHT |
| R4-03, 05, 06 | Mid-block deloads and lighter weeks did not reach the plan | Planned as deloads, the clock waits, 3 sessions capped |
| R4-04 | Type 2 diabetes: strength possible on Monday after Sunday strength | Checked across the weekend |
| R4-07 to 11, 16, 17, 32 | Weekly counts (impact, intervals, last active week, next heavy day, finished weeks, unopened weeks, workload history) could switch limits off | Each counted as the rules intend |
| R4-13 to 15, 27 | Injuries, severe pain and unanswered health questions could be treated as less serious than they are | Conservative from their own date; unanswered means cautious |
| R4-18, 19, 26 | Backups: Android's own backup was not limited to encrypted ones; restore accepted invalid records; encrypted files showed a summary | Encrypted system backup only; restore checks everything; no readable summary |
| R4-20 to 25, 28 to 31 | Smaller items: interval progression per block, walks counted once, step restarts, no free-text notes, decision log pruning, erase compaction | Fixed and tested |

The reviewer then **re-checked the fixes** and found 13 more: 4 high, 5 medium and 4 low. All are fixed (`Part4RecheckRegressionTest`, RC-01 to RC-13) and the reviewer's own probes now show the right behaviour:

| # | What was wrong | Fix |
|---|---|---|
| RC-01 (high) | After 56+ days off, recalibration never ended: no progression, no intervals, weekly load cap off | Each exercise recalibrates once; intervals back after 3 weeks (D-076) |
| RC-02 (high) | One missed session cancelled a return ramp (full load again) | The ramp and the miss are combined, the stricter wins |
| RC-03 (high) | A red flag outside the check-in was shown but not kept; no way to stop mid-session | Always kept; a red flag during a session ends it at once (D-081) |
| RC-04 (high) | Type 2 diabetes rule only applied to the suggested day | Applied to any day opened and by the final safety check |
| RC-05, 08 (medium) | Restore kept unknown old fields (such as an old note) and accepted impossible numbers | Every record re-written as this app writes it; logging limits applied |
| RC-06, 07, 09 (medium) | Editing history ended a lighter week; reduced loads compounded week after week; DEL-004's +1 rep in reserve missing | Lighter week kept; reduced loads stored with their factor (D-082); +1 RIR added |
| RC-10 to 13 (low) | Erase order, personal k as an average, step reading on restore, damaged encryption header | Fixed; erase checked in the database files on a device |

## What this means for you
- **Your data never leaves the phone** unless you export a backup yourself, or Android makes an end-to-end encrypted backup with your screen lock.
- **If you set a backup password and forget it, the backup cannot be opened.** Nobody can recover it. The backup screen (Part 5) will say so when you set one.
- **Steps update when you open the app.** Open it once a day and every day is exact. After a longer gap, the steps in between are spread evenly across those days.
- **After a long break (8+ weeks)** each exercise starts with a light finding-your-weight session once, then progresses normally. Intervals return after 3 weeks of easy cardio.

## Known limits
- The data layer has no screens yet. Onboarding, today, the session player and settings (including backup, restore and erase) come in Part 5.
- Restore replaces everything; it never merges two phones' histories (by design, D-075).
- Part 4 was built and tested on emulators, not on your Motorola Edge 50. Phase 4 (UAT) uses your phone.
- Before any public release, the condition table and its wording still need a clinician and a regulatory review (D-058).

## Phase 3 plan (7 parts)

| Part | Content | Status |
|---|---|---|
| 1 | Engine core, safety kernel, project foundation | Done |
| 2 | Exercise library, 12-month programme, week planner, session generator | Done |
| 3 | Research Update 1.1 in the engine | Done |
| 4 | Data layer: storage, history → engine, steps, own numbers, backup and restore, DATA-001 | **Done (this report)** |
| 5 | Screens: onboarding (goals, conditions, doctor's-OK scopes, your numbers), today, session player, rest timer, backup/restore/erase | Next |
| 6 | History and progress, coach (SAF-009, COACH-001), Ask Gemini share, settings, exercise animations (D-061) | — |
| 7 | Integration, research-update workflow, Phase 3 gate (152/152 rules) | — |

```
================================================================================
PHASE 3 — PART 4 OF 7 COMPLETE. Reply CONTINUE.
================================================================================
```
