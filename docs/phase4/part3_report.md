# Phase 4, Part 3 report: interruptions, data integrity, backup and upgrade (E5)

Date: 19 October 2026 · Decision D-118 · Tool `tools/device_e5.py` · Devices workflow, job "Interruptions and data (E5)"

## Summary
Phase 2 promised that **a logged set is never lost**: not when a call comes in, not when the screen goes off, not when Android closes the app, and not when the phone restarts (NFR-06). Part 3 tests that promise on emulated phones, the way it would happen to you.

A script drives the app from outside, as a person would. It taps through onboarding, starts a session and logs sets, then interrupts the app for real. After every step it reads the app's own database directly, to check that every set logged is saved and stays saved.

**Results on Android 10 and Android 14: every check passes and no set was lost.** The test runs every week and before each release.

## What is tested

| Step | What happens to the app | What is checked |
|---|---|---|
| Logging | One tap logs a set | The set is in the database straight away |
| Home and back | The home button, then the app icon | Same screen, all sets saved |
| Screen off and on | The power button, then unlock | Same screen, all sets saved |
| Turned sideways and back | The screen rotates, a set is logged sideways | All sets saved; logging works sideways |
| A phone call | An incoming call on the emulator | All sets saved after the call |
| Low memory | Android asks the app to free memory | All sets saved |
| Killed in the background | Android closes the app while it is out of sight | "Continue where you stopped?", all sets saved |
| Stopped on screen | The app is stopped while in use (like a crash) | "Continue where you stopped?", all sets saved |
| Stopped while saving | Stopped the moment a set is tapped | The set is saved whole or not at all, and the next one adds exactly one |
| Phone restart | The emulator reboots | "Continue where you stopped?", all sets saved |
| Finish | End the session | All sets kept, the session closed and shown in History |
| Upgrade | The Phase 3 build (13 Oct) logs 3 sets and is left open, then this version is installed over it | The open session and its sets come back, and it can be finished on the new version |
| Android backup | Android's own backup with end-to-end encryption, then uninstall and reinstall | [to be added] |
| No backup without encryption | The same, without encryption | [to be added] |

Export, erase and restore of the app's own backup file is already checked on every device in the device tests (Part 4 of Phase 3), and in scenario S28.

## Results
[to be added]

## What the runs found
The app itself passed every interruption on the first full run. The fixes were all to the test script, which has to work on phones of different sizes:
- on a small screen the Start button is below the fold;
- sideways, the swipe and tap positions are different;
- the End button sits at the top of the screen;
- once every exercise is done, it adds a set to keep going;
- when a slow emulator's launcher stops responding, it closes the dialog.

## Next
Part 4: the safety and evidence audit.
