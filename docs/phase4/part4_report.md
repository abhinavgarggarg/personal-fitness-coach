# Phase 4, Part 4 report: safety and evidence audit

Date: 25 October 2026 · Decisions D-119 to D-122 · Change sets `rules/changes/registry_3.0.1.json`, `registry_4.0.0.json`, `registry_4.1.0.json` · Health-condition table 1.0.2 · Tests `Part4AuditEngineTest`, `Part4AuditDataTest`, `AccessibilityOnDeviceTest`, `SpeedOnDeviceTest`

## Summary
Part 4 checked three things: that the app does what its rules say and tells you only what is true; that each rule's evidence is what we claim; and that the app works for people using a screen reader, large text or a slow phone.

Two independent reviewers looked at the app. One thought like a cautious strength coach and tester, the other like an exercise scientist who checks sources. Between them they found **25 problems: 5 high, 12 medium and 8 low**.

- **Fixed in the app without changing any rule (D-120).** The most important: a person too weak for the empty bar was given a weight they couldn't lift; bodyweight exercises disappeared on every lighter day; confirming a warning sign the same day skipped the gentle first session back; and several on-screen messages promised things that didn't happen.
- **You decided eight things on 25 October**, each time choosing the cautious option I recommended. Four became **Registry 4.0.0** (D-121) and four became **Registry 4.1.0 and health-condition table 1.0.2** (D-122).
- **On the test phones:** Android's own accessibility checks found **no errors** on any of the 5 phones. (RESULTS FROM THE LAST RUN: see section 5.)
- **Re-checks:** (TO FILL)

## 1. What the safety review found, and what happens now
| What was wrong | What happens now |
|---|---|
| **High.** Someone for whom even the empty 20 kg bar is too heavy was given 20 kg × 8–12, which they couldn't do. My first fix picked "an easier version" from all similar exercises, which could be a *harder* lift (a conventional deadlift instead of a Romanian deadlift) or start heavier | The app goes down the exercise's own ladder of easier versions (bench press → dumbbell bench → push-up → incline push-up), each graded easier and never starting heavier than the weight that was too heavy. It finds its weight from the lightest dumbbell. If nothing fits, the exercise is left out and the app says so. At the next training block it checks the original again with a few weight-finding sets |
| **High.** Confirming a warning-sign stop the same day skipped the gentle (LIGHT) first session back | The stopped session no longer counts as the first session back |
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
| Osteoporosis bone block | Heel drops for the first 4 weeks; then small hops, except for beginners, people 65 or over, or after hip, knee or ankle pain in the last 4 weeks | CON-004 2.0.0 (Registry 4.0.0) |
| Osteoporosis with controlled high blood pressure | Keep the bone block | CON-004 2.0.0 |
| Very strong muscle pain | 7/10 or more stops that exercise for today (joint pain rules unchanged) | SAF-003 2.0.0 |
| Form not solid and reps missed | 5% lighter next time, with the form tip | PROG-008 2.0.0 |
| Cancer that has spread to the bones | No training plan: follow a supervised programme from the care team | Condition table 1.0.2 |
| Pregnancy | The stricter of the Canadian and US (ACOG) guidelines wherever they differ, with the warning signs of both | Condition table 1.0.2 |
| Evidence ratings | Match what was actually read | Registry 4.1.0 |
| Diabetes with foot numbness | A past foot ulcer or Charcot foot needs a doctor's OK first | Condition table 1.0.2 |

For pregnancy this means: placenta praevia from 26 weeks (not 28); twins at risk of early labour; severe anaemia; and high blood pressure caused by the pregnancy all now mean "follow your provider instead of a training plan". ACOG's other cautions, such as an unchecked irregular heartbeat, heavy smoking or very little activity before pregnancy, need your provider's OK. The warning signs now include headache, calf pain or swelling, muscle weakness that affects balance, and tummy pain.

## 3. Evidence: what was read
I looked up the sources still pending again (19 October), and the evidence reviewer checked my notes and read further (25 October).

| Source | What was read | What it says, for the app |
|---|---|---|
| Peterson 2011 (strength training and muscle, 50+) | Abstract | About +1.1 kg lean mass; agrees with FL-003 |
| Fragala 2019 (strength training for older adults) | Full text | 2–3 days a week, working towards 70–85% of the maximum, power work at 40–60%; agrees with FL-003 |
| ADA Standards of Care 2026, section 5 (diabetes) | Full section (a hosted copy) | Agrees with the type 2 diabetes entry; lists a past foot ulcer or Charcot foot as a reason to see a doctor first, now in the app |
| ACOG 650 (2015, pregnancy) | Full text (a hosted copy) | Stricter than the Canadian guideline in places; now applied |
| ACOG 804 (2020, pregnancy) | Not opened (refused three times) | Two summaries report the same lists as ACOG 650. **Correction:** my note said three details came from ACOG; they came from the Canadian guideline |
| Campbell 2022 (exercise with bone metastases) | Full text (a university copy) | Supervised start, avoid loaded bending and twisting at the lesion. **Correction:** my note said the app agreed; it didn't, and now it follows the care team |
| Campbell 2019 (exercise in cancer) | Abstract | The lymphoedema caution is still to read in full |
| LeSuer 1997 (estimating the maximum) | Part of the abstract, plus two papers describing it | Estimates differ by formula and lift. The app's version wasn't tested, so INT-004 and INT-005 are now rated Limited |
| WHO waist protocol | Through a paper that uses it | The app's instructions match; the WHO report itself still to read |
| FDA general wellness (Jan 2026), EU MDCG 2019-11 | Full texts | See section 6 |

**Ratings that were higher than what was read (Registry 4.1.0):**
- 14 rules whose sources were found but never opened are now "expert practice". Examples are rep ranges, rest times and warm-ups.
- The health screening rules are now "moderate (consensus)".
- AGE-001 is now "moderate".
- The two rules that estimate your maximum are now "limited".

Nothing the app does changes; only the coach's "How sure?" answers do. A rating goes back up when its sources are read. All 47 sources found but not yet opened are now listed as pending.

**Wording on screen:** the fat-loss expectation now says it is the average in studies of people carrying extra weight, and that results vary. "Strength training keeps your muscle" now says "helps keep".

## 4. Accessibility on the phones
A new device test runs Android's Accessibility Test Framework on every main screen:
- onboarding;
- Today, the check-in and the preview;
- the workout, the rest timer, "Something hurts" and ending a session;
- History, Progress, the coach and Settings.

This is the same framework Google Play's pre-launch report uses. It checks four things: that every control has a name a screen reader can read, the size of touch targets, text contrast measured on the real screen, and duplicate labels.

**Results (Devices run 39315171051): 0 errors on all 5 phones.** The warnings:
- **The eight Yes/No answers in health screening all read "Yes" and "No".** Fixed: each answer now names its question.
- **White text on the orange main button measures about 3.7:1.** The button's words are large and bold, so the standard is 3:1, which they pass. The check can't tell the text size, so it warns.
- **A few readings of about 1:1** on glass cards, where the check couldn't find the text on the blurred background. On the screen the text is clear, as the contrast check on our colours confirms (`tools/check_contrast.py`).

Earlier tests already check touch targets of at least 48 dp on every screen, contrast in light and dark mode, and that everything stays usable at 200% text size.

Listening with TalkBack yourself is part of UAT, if you want to try it.

## 5. Speed on the phones
| | Target | Measured |
|---|---|---|
| Cold start to Today (release build) | under 1.5 s | 1,099 ms median on the Android 14 emulator (software graphics) |
| Plan today's session, with the safety check | under 0.5 s | (TO FILL from the Devices run) |
| Save a logged set | under 0.1 s | (TO FILL) |

Your own phone is faster than these emulators; UAT confirms it there.

## 6. Your app and medical-device rules (a note, not legal advice)
The US guidance (FDA, January 2026) and the EU guidance (MDCG 2019-11) both treat fitness and wellness apps as outside medical-device rules. Both also say something that matters here:
- **FDA:** software whose screens name specific diseases, or prompt specific clinical action, is not "general wellness".
- **EU:** software that recommends personalised exercises to relieve pain counts as medical-device software.

The app's health-condition list names conditions and adapts training to them. Its handling of arthritis and back pain is close to the EU example.

**For your own use this changes nothing.** Before any public release, the condition table already needs a clinician's and a regulatory review (D-058). These two points are the questions to put to them.

## 7. Kept for the clinician review before any public release
These were found, but changing them would loosen a limit or needs a clinician's judgement, so they stay as they are:
- lymphoedema: slow upper-body progression and a supervised start;
- diabetic eye disease: easy effort only, even after an eye doctor's OK;
- spine fractures: a physio's OK instead of a doctor's;
- cancer: moderate effort only, even long after treatment;
- power exercises at 50+: 30–70% of the maximum, where the source says 40–60%.

## 8. Re-checks
(TO FILL)

## Next
Part 5: you try the app on your Motorola Edge 50, including Android's own backup. Then the Phase 4 gate.
