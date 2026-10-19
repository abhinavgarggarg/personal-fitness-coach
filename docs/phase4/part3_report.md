# Phase 4, Part 3 report: interruptions, data integrity, backup and upgrade (E5)

Date: 19 October 2026 · Decision D-118 · Tool `tools/device_e5.py` · Devices workflow, job "Interruptions and data (E5)"

## Summary
Phase 2 promised that **a logged set is never lost** (NFR-06). That covers a call, the screen going off, Android closing the app, and the phone restarting. Part 3 tests that promise on emulated phones, the way it would happen to you.

A script drives the app from outside, as a person would:
1. It taps through onboarding.
2. It starts a session and logs sets.
3. It interrupts the app for real.

After every step it reads the app's own database directly. It checks three things:
- every set is saved, with the same weight, reps and effort;
- the session would resume at the same place: the same stage, exercise, next weight and rest;
- the app hasn't crashed or stopped responding.

**On Android 10, 14 and 16 every check passes: no set was lost, changed or doubled, and every session came back where it stopped.** The test runs every week and before each release. Android 16 is what your phone runs.

## What is tested

| Step | What happens to the app | What is checked |
|---|---|---|
| Logging | One tap logs a set | Exactly one new set, complete, saved at once; the earlier ones unchanged |
| Home and back | The home button, then the app icon | Same process, the same sets, the same resume point, no "continue?" question |
| Screen off and on | The power button, then unlock | The same |
| Turned sideways and back | The screen rotates, and a set is logged sideways | The same; logging works sideways |
| A phone call | An incoming call is answered and takes over the screen, then ends | The same |
| Low memory | Android asks the app to free memory | The same |
| Closed in the background | Android closes the app while it is out of sight | "Continue where you stopped?" must appear; the same sets and resume point |
| Stopped on screen | The app is stopped while in use (as a crash would) | The same as above |
| Stopped while saving | Stopped 0 to 0.3 s after a tap, four times | Each time, either the set and the session's next step are saved together, or nothing is |
| Phone restart | The emulator reboots | "Continue where you stopped?"; the same sets and resume point; a running rest has its alert set again |
| Finish | End the session | Every set kept, the session closed and shown in History |
| Upgrade | The Phase 3 build (13 Oct) logs 3 sets and is left open; this version is installed over it | "Continue where you stopped?"; the same sets and resume point; the session finishes on the new version |
| Not encrypted | Android's backup without end-to-end encryption | The database is not in the backup |
| Device-to-device | A transfer to a new phone (Android 12 and later) | The database is in it |
| Encrypted backup | Android's end-to-end encrypted backup (Android 12 and later), uninstall, reinstall | The database is in the backup, and every set, exercise and session comes back identical |

Two things these tests don't cover:
- **The encrypted backup before Android 12.** Android's test backup service has no real encryption setting before then, so the encrypted case is tested on Android 12 and later, and on your phone in UAT. On Android 10 the test checks that nothing goes into an unencrypted backup.
- **A database migration.** The database layout hasn't changed since the Phase 3 build (it is still version 1), so the upgrade checks the records stored inside it, not a migration.

Export, erase and restore of the app's own backup file is checked on every device in the device tests (Phase 3), and in scenario S28.

## Results
GitHub Devices run 39297906315, branch `phase4/e5-device-tests` at 8feced0:

| | Android 16 | Android 14 | Android 10 |
|---|---|---|---|
| Checks passed | 29 of 29 | 29 of 29 | 28 of 28 |
| Sets logged through the interruptions, all kept unchanged | 11 | 11 | 11 |
| Upgrade from the Phase 3 build with a session left open | passed | passed | passed |
| Stopped 0 / 0.05 / 0.15 / 0.3 s after a tap | not saved, saved, saved, saved | the same | the same |
| Rest alert set again after reopening | yes | yes | yes |
| Not encrypted: the database stays out | yes (0 files) | yes (0 files) | yes (0 files) |
| Device-to-device: the database goes | yes | yes | can't be simulated |
| Encrypted backup: back identical after a reinstall | yes, at install | yes, at install | can't be simulated |

The four stops right after a tap gave both outcomes, and each was whole. Either the set and the session's next step were saved together, or nothing changed.

## What the runs and the review found
**In the app:** on Android 10 and 11, the rules sent the training history only into an encrypted backup, so a phone-to-phone transfer (for example, when you get a new phone) would have left it behind. Android 12 and later already allowed it. The rules now allow a device-to-device transfer on every version, and `tools/check_backup_rules.py` checks both.

**In the tests:** an independent review found 4 high, 5 medium and several low problems. The main ones were ways the suite could have stayed green while something was wrong:
- a crash being cleared from the log;
- the "continue?" question not being required;
- sets only being counted, not compared;
- a backup check that could pass because the backup simply failed.

All are fixed as described above. The rest were fixes for phones of different sizes: a small screen, sideways, the End button at the top, a slow emulator's launcher.

## Not covered yet (follow-ups)
- **Changing the phone's clock or time zone mid-session.** The rest timer already uses the elapsed-time clock.
- **A full phone storage.**
- **The shipped release build.** The release build has its own smoke test (Phase 3).

## Next
Part 4: the safety and evidence audit.
