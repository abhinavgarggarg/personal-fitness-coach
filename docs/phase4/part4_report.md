# Phase 4, Part 4 report: safety and evidence audit

Date: 26 October 2026 · Decisions D-119 to D-123 · Change sets `rules/changes/registry_3.0.1.json`, `registry_4.0.0.json`, `registry_5.0.0.json` · Health-condition table 1.0.2 · Tests `Part4AuditEngineTest`, `Part4AuditDataTest`, `AccessibilityOnDeviceTest`, `SpeedOnDeviceTest`

## Summary
Part 4 checked three things: that the app does what its rules say and tells you only what is true; that each rule's evidence is what we claim; and that the app works for people using a screen reader, large text or a slow phone.

Two independent reviewers looked at the app. One thought like a cautious strength coach and tester, the other like an exercise scientist who checks sources. Between them they found **25 problems: 5 high, 12 medium and 8 low**. Their re-checks of my fixes found **18 more**, all addressed (section 8).

- **Fixed in the app without changing any rule (D-120).** The most important: a person too weak for the empty bar was given a weight they couldn't lift; bodyweight exercises disappeared on every lighter day; confirming a warning sign the same day skipped the gentle first session back; and several on-screen messages promised things that didn't happen.
- **You decided eight things on 25 October**, each time choosing the cautious option I recommended. Four became **Registry 4.0.0** (D-121) and four became **Registry 5.0.0 and health-condition table 1.0.2** (D-122). On three follow-up questions you kept one as built and left two to me; I took the cautious option (D-123).
- **On the test phones:** Android's own accessibility checks found **no errors** on any of the 5 phones. Planning a session takes 0.07–0.47 s and saving a set 0.01–0.07 s, within the targets. The app starts in 0.64 s.
- **All tests pass:** 741 locally (engine 447, data 195, app 99), GitHub CI, and the phone tests on 5 emulators, including the interruption, backup and upgrade tests from Part 3 (Devices run 40196311542).

## 1. What the safety review found, and what happens now
| What was wrong | What happens now |
|---|---|
| **High.** Someone for whom even the empty 20 kg bar is too heavy was given 20 kg × 8–12, which they couldn't do. My first fix picked "an easier version" from all similar exercises, which could be a *harder* lift (a conventional deadlift instead of a Romanian deadlift) or start heavier | The app goes down the exercise's own ladder of easier versions (bench press → dumbbell bench → incline push-up), each graded easier, the same kind of movement, and never starting heavier than the weight that was too heavy. A dumbbell version finds its weight from the lightest dumbbell; among bodyweight versions the easiest comes first and steps up when it gets easy. If nothing fits, the exercise is left out and the app says so. At the next training block it checks the original again with a few weight-finding sets. Lighter days use the same path |
| **High.** Confirming a warning-sign stop the same day skipped the gentle (LIGHT) first session back; so did reporting one in the evening after a morning session | The stop now records its time, and only a session started after it counts as the first session back |
| **High.** Osteoporosis: small hops from the very first session, even for a 71-year-old beginner | Your decision (below): heel drops first |
| Bodyweight and timed exercises were left out on every lighter day and in deload weeks, with a message about weights being too heavy | They stay, made easier with more reps in reserve and fewer sets |
| After a break, the planned 10–20% lighter weights weren't applied when you were already at the lightest weight | The easier version is used for the return weeks |
| A hard set at the lightest weight said "the next one is a little lighter", but it wasn't | It says this is already the lightest weight, so stop the next set with the planned reps in reserve |
| "An easier version is offered" was shown after two "form not solid" answers, but nothing offered one, and for 93 of 157 exercises there is none | The workout screen shows "Try the easier version" with one tap. Where there is none, the message says the weight stays the same and Replace offers other exercises. Bodyweight exercises now hold their step when form isn't solid |
| "Carry on carefully: the weight is lower" after a pain report, when it couldn't go lower | The message says to keep the range comfortable and that the weight can't go lower |
| One bad session at the lightest weight could switch an exercise to its easier version for weeks | It takes two in a row |
| Replace could list an exercise that was then refused | Only exercises that can really be planned are listed |
| A sore knee counted as "pain-free" after two upper-body sessions | Only sessions that worked the sore area count |
| Muscle pain 9/10, and form not solid with missed reps | Your decisions (below) |

Each fix has a test that reproduces the reviewer's case.

## 2. Your decisions (25 October)
| Question | You chose | Now in |
|---|---|---|
| Osteoporosis bone block | Heel drops for the first 4 weeks; then small hops, except for beginners, people 65 or over, people with low back pain (you left this to me, D-123), or after hip, knee or ankle pain in the last 4 weeks | CON-004 2.0.0 (Registry 4.0.0) |
| Osteoporosis with controlled high blood pressure | Keep the bone block | CON-004 2.0.0 |
| Very strong muscle pain | 7/10 or more stops that exercise; the rest of today goes easy on that area, also when it is reported at the check-in (you left this last part to me, D-123) | SAF-003 2.0.0 |
| Form not solid and reps missed | 5% lighter next time, with the form tip | PROG-008 2.0.0 |
| Cancer that has spread to the bones | No training plan: follow a supervised programme from the care team | Condition table 1.0.2 |
| Pregnancy | The stricter of the Canadian and US (ACOG) guidelines wherever they differ, with the warning signs of both | Condition table 1.0.2 |
| Evidence ratings | Match what was actually read | Registry 5.0.0 |
| Diabetes with foot numbness | A past foot ulcer or Charcot foot needs a doctor's OK first | Condition table 1.0.2 |
| Back after a break, already at the lightest weight | The easier version until the return ends (as built) | D-123 |

For pregnancy this means: placenta praevia from 26 weeks (not 28); twins at risk of early labour; severe anaemia; high blood pressure caused by the pregnancy; ongoing bleeding in the second or third trimester; and restrictive lung disease all now mean "follow your provider instead of a training plan". ACOG's other cautions, such as an unchecked irregular heartbeat, heavy smoking or very little activity before pregnancy, need your provider's OK. So do any anaemia and any other significant medical condition. The warning signs now include any chest pain or dizziness, headache, calf pain or swelling, muscle weakness that affects balance, and tummy pain. The tips say to avoid scuba diving and to check with your provider before exercising above 1,800 m.

## 3. Evidence: what was read
I looked up the sources still pending again (19 October), and the evidence reviewer checked my notes and read further (25 October).

| Source | What was read | What it says, for the app |
|---|---|---|
| Peterson 2011 (strength training and muscle, 50+) | Abstract | About +1.1 kg lean mass; agrees with FL-003 |
| Fragala 2019 (strength training for older adults) | Full text | 2–3 days a week, working towards 70–85% of the maximum, power work at 40–60%; agrees with FL-003 |
| ADA Standards of Care 2026, section 5 (diabetes) | Part of the section (a hosted copy that stopped part-way) | The recommendations seen agree with the type 2 diabetes entry. Its list of reasons to see a doctor first includes a past foot ulcer or Charcot foot, now in the app |
| ACOG 650 (2015, pregnancy) | Full text (a hosted copy) | Stricter than the Canadian guideline in places; now applied everywhere they differ |
| ACOG 804 (2020, pregnancy) | Not opened (refused three times) | A summary of it lists the same warning signs plus tummy pain; a comparison table gives the same 26-week cut-off. **Correction:** my note said three details came from ACOG; they came from the Canadian guideline |
| Campbell 2022 (exercise with bone metastases) | Full text (a university copy) | Supervised start; near the affected bone, avoid fast or loaded end-of-range movement and start with little or no load. **Correction:** my note said the app agreed; it didn't, and now it follows the care team |
| Campbell 2019 (exercise in cancer) | Abstract | The lymphoedema caution is still to read in full |
| LeSuer 1997 (estimating the maximum) | Part of the abstract, plus two papers describing it | Estimates differ by formula and lift. The app's version wasn't tested, so INT-004 and INT-005 are now rated Limited |
| WHO waist protocol | Through a paper that uses it | The app's instructions match; the WHO report itself still to read |
| FDA general wellness (Jan 2026), EU MDCG 2019-11 | Full texts | See section 6 |

**Ratings that were higher than what was read (Registry 5.0.0):**
- 14 rules whose sources were found but never opened are now "expert practice". Examples are rep ranges, rest times and warm-ups. Two more follow after the re-check: the timing of the session-effort question, and teaching beginners to rate effort.
- The health screening rules are now "moderate (consensus)".
- AGE-001 is now "moderate".
- The two rules that estimate your maximum, and the rule that turns "reps left" into effort, are now "limited".
- The coach's wording for "moderate" now reads "good research evidence or an expert consensus", since the screening rules are a consensus.

Nothing the app does changes; only the coach's "How sure?" answers do. A rating goes back up when its sources are read. All 47 sources found but not yet opened are now listed as pending.

**Wording on screen:** the fat-loss expectation now says it is the average in studies of people carrying extra weight who weren't put on a diet, counts minutes of exercise, and says results vary. "Strength training keeps your muscle" now says "helps keep".

## 4. Accessibility on the phones
A new device test runs Android's Accessibility Test Framework on every main screen:
- onboarding;
- Today, the check-in and the preview;
- the workout, the rest timer, "Something hurts" and ending a session;
- History, Progress, the coach and Settings.

This is the same framework Google Play's pre-launch report uses. It checks four things: that every control has a name a screen reader can read, the size of touch targets, text contrast measured on the real screen, and duplicate labels.

**Results: 0 errors on all 5 phones** (Devices runs 39315171051 and 40196311542). The warnings:
- **The eight Yes/No answers in health screening all read "Yes" and "No".** Fixed: each answer now names its question, and the warning is gone in the latest run.
- **White text on the orange main button measures about 3.7:1.** The button's words are large and bold, so the standard is 3:1, which they pass. The check can't tell the text size, so it warns.
- **A few readings of about 1:1** on glass cards, where the check couldn't find the text on the blurred background. On the screen the text is clear, as the contrast check on our colours confirms (`tools/check_contrast.py`).

Earlier tests already check touch targets of at least 48 dp on every screen, contrast in light and dark mode, and that everything stays usable at 200% text size.

Listening with TalkBack yourself is part of UAT, if you want to try it.

## 5. Speed on the phones
| | Target | Measured |
|---|---|---|
| Cold start to Today (release build) | under 1.5 s | 638 ms median (Android 14 emulator, software graphics) |
| Plan today's session, with the safety check | under 0.5 s | 66–125 ms on Android 12 and 16; 344 ms on Android 14 at 200% text; 467 ms on the small Android 10 phone |
| Save a logged set | under 0.1 s | median 12–41 ms; 69 ms on the small Android 10 phone (its slowest set 180 ms) |

These run on the app's real database file through the same controller the screens use (`SpeedOnDeviceTest`). The small Android 10 emulator is the slowest of the five, and it is close to the planning target. Your phone is much faster than these emulators; UAT confirms it there.

## 6. Your app and medical-device rules (a note, not legal advice)
The US guidance (FDA, January 2026) and the EU guidance (MDCG 2019-11) both treat fitness and wellness apps as outside medical-device rules. Both also say something that matters here:
- **FDA:** software whose screens name specific diseases, or prompt specific clinical action, is not "general wellness".
- **EU:** software that recommends personalised exercises to relieve pain counts as medical-device software.

The app's health-condition list names conditions and adapts training to them. Its handling of arthritis and back pain is close to the EU example.

**For your own use this changes nothing.** Before any public release, the condition table already needs a clinician's and a regulatory review (D-058). These two points are the questions to put to them.

## 7. Kept for later
**Before any public release (software):** people who answered the health questions before table 1.0.2 aren't asked the new ones until they edit their conditions. Nobody is affected today; a release would ask again on upgrade.

**For the clinician review before any public release:**
These were found, but changing them would loosen a limit or needs a clinician's judgement, so they stay as they are:
- cancer spread to the bones: a way back to the app's plan once the care team's supervised start is done;
- lymphoedema: slow upper-body progression and a supervised start;
- diabetic eye disease: easy effort only, even after an eye doctor's OK;
- spine fractures: a physio's OK instead of a doctor's;
- cancer: moderate effort only, even long after treatment;
- power exercises at 50+: 30–70% of the maximum, where the source says 40–60%.

## 8. Re-checks
Both reviewers re-checked my fixes.

**Safety re-check.** It confirmed 14 of the 15 findings fixed and found 10 more:
- one high: a warning sign reported in the evening, after a morning session, still skipped the gentle first session back;
- four medium: the alternatives after a muscle-pain stop couldn't be added; the check-in message after strong muscle pain promised something that didn't happen; small hops with low back pain; and a lighter day's replacement still found its weight from body weight;
- five low.

All are fixed (D-123), except two. During a return at the lightest weight the easier version stays (your choice). Asking existing users the new health questions waits for a public release.

**Evidence re-check.** It confirmed that the bone-spread, diabetic-foot and rating changes are encoded correctly. It found 8 more points:
- the pregnancy lists took the stricter guideline only in part;
- three source notes still overstated what was read;
- three more ratings rested on unread sources.

All are fixed: pregnancy now takes the stricter guideline everywhere, the notes say exactly what was read, and the three ratings are lowered.

**Final safety check of the last fixes:** a third independent check found three small gaps in the new rules. All are fixed:
- strong muscle pain "all over" now ends the session, as joint pain already did;
- after a muscle-pain stop the app looks further for a replacement exercise, so you are not left with an empty list;
- the bone-loading block is skipped on a flare day.

Where you said "no preference" I took the cautious option and logged it as D-123 (check-in muscle pain day limit, heel drops with low back pain, the stop time on a red-flag stop, the protected easier version on lighter days). You can confirm or reverse these at the gate. Final local result: engine 448, data 195, app 99 tests all pass.

## Next
Part 5: you try the app on your Motorola Edge 50, including Android's own backup. Then the Phase 4 gate.
