# Acceptance traceability — Phase 2 criteria to Phase 3 evidence

Phase 3 Part 8. Each Phase 2 acceptance criterion (section 19) and each quality target (NFR-01 to NFR-16, section 3) is matched to the check that proves it. Some can only be judged on your own phone; those go to Phase 4 user acceptance testing (UAT).

**How to read the status column**
- **Automated:** a test or CI check proves it on every push.
- **Automated + UAT:** proven by tests, and also to be confirmed on your phone.
- **UAT:** can only be judged by you, on your phone (for example "it vibrates when the phone is locked").
- **Open:** not met yet. Listed in the gate report as an open issue, with a recommendation.

Test names are given as `Class › test`. Engine tests are in `engine/src/test`, data-layer tests in `data/src/test`, screen-logic tests in `app/src/test`, and device tests in `app/src/androidTest` (run on 5 emulators by the Devices workflow).

## Features F1–F16

| Feature | Criterion | Evidence | Status |
|---|---|---|---|
| F1 Onboarding | A fresh install with all required answers gets a profile, screening result, equipment and a first week | `OnboardingTest › the whole flow ends with a programme, today's plan and the answers stored`; `ScreensTest › J1 onboarding through the screens reaches Today`; the release smoke test does the same on the shrunk release build | Automated |
| | A symptom answer always sets conservative mode | `SafetyTest › TC-SAF-001a`; `ReviewFindingsTest › TC-SAF-001c` | Automated |
| | Killing the app midway resumes at the same step | `OnboardingTest › each answer is saved as it is given, so an interrupted onboarding resumes at the same step` | Automated |
| | Median completion ≤ 5 minutes | — | UAT |
| F2 Calibration | With no working weight, the session ramps by the effort answers; nothing above RPE 8 or to failure; a working weight and starting e1RM after ≤ 5 ramp sets | `ProgressionTest › TC-CAL-001a/b`; `ReviewFindingsTest › TC-CAL-001c`; `ExerciseProgressTest › after the fourth calibration session progression takes over`; `WeekBudgetTest › TC-VOL-007f` | Automated |
| F3 Readiness check | All-normal gives FULL; under 4 h sleep gives at most LIGHT; a red flag ends in a safety stop; the tier has a one-line reason with Why | `CalcTest › TC-RDY-001a … TC-RDY-007b`; `SafetyTest › TC-SAF-002a`; `AppControllerTest › J3 a bad night caps the day at MODIFIED and says why` | Automated |
| | An easier tier always works; harder by one step at most, with a warning | `CalcTest › TC-RDY-007a/b`; `Part5ReviewFlowTest › R5-14` | Automated |
| | Completes in ≤ 15 seconds | — | UAT |
| F4 Workout generation | Every session passes the validator | `GenerationTest › TC-GEN-001b`; `SafetyTest › TC-SAF-008b` (3,000 random sessions) | Automated |
| | Generation < 500 ms | `GenerationSpeedTest` (300 sessions on CI: median under 1 ms, 95th percentile about 5 ms) | Automated |
| | Same inputs, same session | `GenerationTest › TC-GEN-001a` | Automated |
| | Excluded machines never appear | `SafetyTest › TC-MOD-001a` | Automated |
| F5 Guided workout | Targets (sets, reps, real equipment weights, RIR, rest), cues, last performance, warm-up sets for main lifts | `SessionPlayerTest` (ramp-up sets, last time, rest); `ScreensTest › J2 … the first sets of a workout through the screens`; `LibraryTest › every exercise … has original setup text, cues and mistakes` | Automated + UAT |
| | The screen stays on | `WorkoutScreen` keeps the screen on while a workout is open | UAT |
| F6 Set logging | "Done as planned" saves the set in one tap within 100 ms | `PlayerSpeedTest` (median about 1–2 ms on the test machine; the phone's database write adds a few ms) | Automated + UAT |
| | Load and reps adjusted with steppers, no keyboard | The workout screen design (Part 5) | UAT |
| | The set survives an immediate force-close | `SessionPlayerTest › logging a set saves it at once … and survives a restart`; `AppControllerTest › R1`; `JourneyIntegrationTest` (killed app) | Automated |
| F7 Rest timer | Starts after a set; ±30 s and skip work | `SessionPlayerTest › logging a set saves it at once …` | Automated |
| | The alarm fires at the end of the rest | `ScreensOnDeviceTest › theEndOfRestAlarmFires` (5 emulators) | Automated |
| | With the phone locked it vibrates and sounds within 1 s; logging the next set replaces the alert | — | UAT |
| F8 Replace / occupied | 3 options, each with a reason, matching today's equipment and pain limits | `SubstitutionTest › TC-SUB-001a/b/c, TC-SUB-002a/b`; `PlanningTest`; `ProfileTest › TC-EQ-002b` | Automated |
| | In under 300 ms | `PlayerSpeedTest` (about 8–17 ms) | Automated |
| | "Do it later" moves the exercise later | `SessionPlayerTest › EQ-002 occupied offers do-it-later, which moves the exercise to the end` | Automated |
| F9 Something hurts / red flags | One tap on every workout screen | `ScreensOnDeviceTest › aWorkoutStartsFromTodayThroughTheScreens` (Hurts opened and closed on the device) | Automated |
| | 4–6/10 joint pain stops the exercise and offers pain-free swaps; ≥ 7/10 or red-flag descriptors stop the region or the session; a red flag never gives a lighter workout | `SafetyTest › TC-SAF-002a/b, TC-SAF-003a/b, TC-SAF-004a/b`; `ReviewFindingsTest › TC-SAF-003c/d`; `Part4RecheckRegressionTest › RC-03`; `ScreensTest › A5`; `JourneyIntegrationTest` (pain mid-set, red flag at the check-in) | Automated |
| F10 Time adaptation | For 30, 40, 60, 75 and 90 minutes the session is within ±10% of the time, keeps the warm-up and the primary lift, and lists what moved | `TimeBudgetAcceptanceTest` (39 planned days × 5 budgets: never more than 10% over; shortened sessions within 10%; a 30-minute day that the full session can't fit gets the express session); `AppControllerTest › F10 30 minutes` (express offered, the user's opt-out kept); `TimeBudgetTest › TC-TIME-001a … 004b`; `SupersetPlayerTest` (supersets played as timed, also after later, skip, pain and a time change); `WeekPacingTest › TC-LOAD-005i` (interval blocks by whole repeats) | Automated |
| | Extra time (75 or 90 minutes on a day planned shorter) is filled | TIME-003 is implemented and tested (`TimeBudgetTest › TC-TIME-003a/b`) but not used when generating a session: it would add training volume | **Open** (decision for Phase 4) |
| F11 Summary | One 0–10 effort question; session load saved; records, "what changed" and the next session shown | `SessionPlayerTest › finishing gives a summary, records the minutes and asks the rating …`; `AppControllerTest › J1`; `Part5ReviewFlowTest › R5-10 and R5-26 … the summary names the next session`; `CalcTest › TC-LOAD-001a/b` | Automated |
| F12 History & progress | Every completed set appears in history; edits recompute the estimated max and records and are logged | `InsightsTest › history lists finished sessions …`, `› correcting a past set rebuilds the estimated max and is logged …`; `Part6FlowTest › a past set is corrected from history …`; `JourneyIntegrationTest` (stored sessions = sessions done) | Automated |
| | Weekly sets per muscle and WHO minutes match the engine exactly | `InsightsTest › progress shows lifts, weeks, streak …`; `ConditioningTest › TC-PH-001a/b`; `FatLossTest › TC-PH-001c` | Automated |
| F13 Coach (Tier 1) | The 12 suggested questions get correct, plain answers citing a real decision or rule; unknown questions get 3 alternatives | `CoachTest` (all 12 questions, 3 closest questions, Why shows each rule) | Automated |
| | Works in flight mode | The app has no internet permission at all (NFR-04) | Automated |
| F14 Backup | Export → erase → restore gives identical data; corrupt or newer files are refused with nothing changed | `BackupTest` (identical data, refusals, safety copy, password); `AppControllerTest › backup with a password, erase everything, then restore it`; `JourneyIntegrationTest` (restore mid-journey, training carries on) | Automated |
| | The reminder appears after 8 workouts when the last export is over 30 days old | `BackupTest › the reminder card appears after 8 workouts …` | Automated |
| F15 Streaks | A missed week never resets the best streak; one freeze per 4 weeks | `ProfileTest › TC-ADH-001a/b`; `ReviewRegressionTest › R20` | Automated |
| F16 Settings | "Erase all my data" needs two confirmations and returns to first launch | `ScreensTest › settings - erase needs two confirmations …`; `BackupTest › erase all removes every record` | Automated |
| | Units switch kg ↔ lb everywhere | Deferred: kilograms only in V1 (D-097) | **Open** (your decision) |
| All | No internet permission in the APK | CI checks the debug and the release APK; `LaunchSmokeTest › appHasNoInternetPermission` on 5 emulators | Automated |
| | No crash in the full 30-scenario suite | The 30 scenarios are the Phase 4 suite; the three 12-week journeys, the device tests on 5 emulators and the release smoke test (onboarding to today's session on the shrunk release build) run crash-free now | Phase 4 |
| | TalkBack can complete a set log | Pictures, colours and icons always come with words (design system, D-102); a manual TalkBack pass is planned | UAT |
| | Text readable at 200% font size | Device matrix includes Android 14 at 200%; `ScreensTest › 200 percent text …` (3 tests) | Automated |

## Quality targets NFR-01–NFR-16

| NFR | Target | Evidence | Status |
|---|---|---|---|
| NFR-01 Cold launch | < 1.5 s to a usable Today on a 2022+ mid-range phone | The release smoke test measures cold start of the shrunk release build on an Android 14 emulator with software graphics: 970 ms median (775–1,043 ms) | Automated (measured) + UAT |
| NFR-02 Generation | < 500 ms | `GenerationSpeedTest` | Automated |
| NFR-03 Set logging | < 100 ms, UI never blocks | `PlayerSpeedTest`; taps are handled one at a time and a double tap logs nothing (D-083) | Automated + UAT |
| NFR-04 Offline | Everything works with no connection | No INTERNET or network-state permission (CI on the debug and release APK, device test) | Automated |
| NFR-05 Battery | No background work outside a session, no wake locks | The permission allow-list refuses wake-lock, boot and foreground-service permissions; the only background component is the rest alarm receiver (not exported) | Automated (static) + UAT |
| NFR-06 Data integrity | Zero lost sets across interruptions | `SessionPlayerTest` (restart), `AppControllerTest › R1` (killed app), `JourneyIntegrationTest` (killed app, restore), `RowStoreContract` | Automated |
| NFR-07 Privacy | No analytics, ads, trackers or third-party SDKs | CI: every library in the release build must be on the allow-list (`tools/check_dependencies.py`) | Automated |
| NFR-08 Accessibility | 200% font; contrast ≥ 4.5:1 / 3:1; TalkBack labels; colour never the only signal | `tools/check_contrast.py` (CI); 200% device and Robolectric tests; manual TalkBack pass | Automated + UAT |
| NFR-09 Gym usability | Primary touch targets ≥ 56 dp; one-handed reach; dark theme default | Main buttons are at least 60 dp high; dark theme is the default (`SettingsRecord`) | UAT |
| NFR-10 Size | < 15 MB | CI fails the release APK at 15 MB; it is 4.2 MB | Automated |
| NFR-11 Compatibility | Android 10 (API 29) to latest | Devices workflow: Android 10, 12, 14 (200% text), 16 phone, 16 tablet | Automated |
| NFR-12 Reliability | ≥ 99.5% crash-free sessions; a crash never loses data | No crash in any journey, device or smoke run; sets are saved as they are logged (NFR-06). The crash-free rate is measured on your phone in UAT | UAT |
| NFR-13 Maintainability | Engine pure Kotlin; ≥ 90% engine line coverage; 100% rule coverage | The engine module has no Android dependency; rule coverage 152/152 required in CI; line coverage reported in CI (JaCoCo) | Automated |
| NFR-14 Determinism | Same inputs, same workout | `GenerationTest › TC-GEN-001a`; `ReviewRegressionTest › R07` | Automated |
| NFR-15 Units & time | kg default, lb option; UTC times with a local training date, correct across time zones | `StepLedgerTest › time zones decide the day`; dates stored as local training days; lb deferred (D-097) | Automated (time) / **Open** (lb) |
| NFR-16 Language | No hard-coded UI strings | `tools/check_strings.py` (CI) | Automated |
