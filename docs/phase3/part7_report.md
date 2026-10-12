# Phase 3, Part 7 report: the new look ("Glow") and moving exercise figures

Date: 12 October 2026 · Rule Registry 1.1.1 · Health-condition table 1.0.1 · Library 1.1.1 · Decisions D-099 to D-104 · Change requests CR-007 and CR-004

## Summary
The app now has the look you chose, **look D "Glow"**, which was made from your reference picture. It applies to every screen in both the dark theme (the default) and the light theme:

- a deep navy background with frosted-glass cards;
- an orange glow on the one main button of each screen, and teal for things that are done;
- very large white numbers for weights, reps and timers;
- the Plus Jakarta Sans font, built into the app so it works offline;
- the app's own line icons, and a floating tab bar of icons only, each with its spoken name for TalkBack.

**Every one of the 157 exercises in the library now has its own moving figure (CR-004).** Each figure does one repetition, from the start of the movement to the end and back, in a slow loop. It is an original drawing, made from joint angles rather than copied from anywhere, and it shows the equipment: bar and plates, dumbbells, kettlebells, cables, bands, benches, boxes and machines. A teal dashed line marks the main form point, such as a flat back on hinges or a straight body on planks. If you have turned on your phone's "remove animations" setting, the figure stands still. You see the figures on Today's workout card and on the workout screen.

An independent reviewer found 14 problems: 3 high, 5 medium and 6 low. Its re-check found 4 more. All 18 are fixed, and tests guard most of them.

- **Tests:** 654 automated tests pass on GitHub.
- **Devices:** the device tests pass on five phone and tablet set-ups.
- **Screen pictures:** every screen is pictured in the new look, including the light theme and all 157 figures.

## What was built

| Area | What you see | Status |
|---|---|---|
| **Design system** (D-099, `docs/phase3/design_system.md`) | The colours, type, shapes, glow, icons and touch feedback, in dark and light. A short tick confirms each logged set | VERIFIED |
| **Today** (D-102) | See the section below the table | VERIFIED |
| **Workout** | See the section below the table | VERIFIED |
| **Coach** | Your question appears as an orange bubble on the right, and the coach's answer as a glass bubble with a teal edge on the left. The suggested questions are full-width glass rows | VERIFIED |
| **Progress** | Tiles for weeks in a row, weeks trained this year and sessions. Your weekly minutes are rounded bars: orange, turning teal once a week reaches the 150-minute goal, which shows as a dashed line. Strength lines are orange with a soft fill underneath | VERIFIED |
| **Other screens** (onboarding, check-in, preview, history, settings, sheets) | Restyled through the shared buttons, cards, chips and fields, with no change to how they work | VERIFIED |
| **Exercise figures** (D-100, CR-004) | All 157 exercises: seen from the side, or from the front when the movement goes across the body (lateral raises, flyes, side planks, shrugs, the Pallof press) | VERIFIED |

**Today** shows, from top to bottom:
- "Good morning," with the date and a round settings button;
- anything that needs your attention first;
- this week's ring, showing sessions done out of sessions planned, with a line of words beside it;
- small tiles for steps (when counted, with the day's target) and weeks in a row (once you have some, with your best run);
- today's workout on a dark "studio" card: the first exercise moves on the right, and the session's name and length are on the left;
- one glowing Start button;
- each reason as a glass row with its "Why?";
- your week as seven day tiles.

**The workout screen** shows:
- the current exercise moving on its studio card, with its first coaching cue;
- the set ("Set 2 of 3", with dots) and the target in very large numbers ("60 kg × 8 reps");
- "Done as planned", glowing;
- the effort answers as large glass buttons;
- the rest as a ring that empties, with −30 s, +30 s and Skip;
- "Something hurts" (amber), "Warning signs" (red) and "Change time" in a bar that never scrolls away.

## How it was checked

**Locally:**
- 623 tests pass: engine 408, data core 159, app screen logic and figures 56.
- The strings check passes: 971 strings, all used, none written directly into the code.
- The new contrast check passes: 56 colour pairs in the two themes.

**The figures** are checked by 14 tests:
- Every library exercise has exactly one figure.
- No part of the body sinks through the floor at any moment of the rep.
- Arms and legs never change length.
- A hand or foot that reaches for something (a bar, a handle, the floor) gets there, at the start, middle and end of the movement.
- Every key position is drawn exactly as it was defined, elbows and knees included.
- Nothing jumps between two moments of the rep.
- Deadlifts start with the weight on the floor.
- In the overhead triceps extension, the hands go behind the head, never through it.
- Each single-leg Romanian deadlift holds the weight in the hand opposite the standing leg.
- Every picture fits a card.

**On GitHub:**
- **CI:** the run is green. 654 tests pass: engine 408, data 176 and app 70. The app tests include the screens with 200% text and touch targets, plus a new test that a 102.5 kg target and a 25:00 timer stay whole and full size at 200% text.
- **Other CI checks:**
  - **Contrast:** the new contrast check passes.
  - **Permissions:** unchanged, with no internet.
  - **Rule coverage:** 152/152.
  - **Build:** the debug APK built.
- **Devices:** 9 of 9 device tests pass on each of the five emulated devices:
  - Android 10, small phone;
  - Android 12;
  - Android 14 at 200% text;
  - Android 16 phone;
  - Android 16 tablet.

**Screen pictures** are in `docs/phase3/screens/`:
- every screen, numbers 01 to 37;
- the rest ring (81);
- Today in the light theme at evening (82);
- all 157 exercise figures (91 to 94).

They were checked by eye, which led to these fixes:
- the safety buttons no longer break a word ("Somethin / g hurts");
- the "× 10 reps" part of the target moves down as a whole when the line is too short;
- a lonely minutes tile was removed;
- deadlifts now rest on their start position instead of standing up.

## Independent review
A separate reviewer, who had not written this code, measured text widths with the app's font, worked out every colour contrast, compiled and checked all 157 figures, and looked at the pictures. It found **14 problems** (R7-01 to R7-14):

| # | What was wrong | Fix |
|---|---|---|
| R7-01 (high) | 10 pull and row figures (pulldowns, cable and band rows, face pull, shoulder tap, renegade row) ended with the arm hanging down instead of pulling | When a limb changes from one kind of position to another, the movement now blends where the hand actually is. A test checks that every hand and foot reaches its target |
| R7-02 (high) | White on the bright orange button was below the 3:1 minimum (2.2–3.1:1) in the dark theme | The button is a slightly deeper orange in both themes (3.2:1 or better). A new check in CI tests every colour pair (D-104) |
| R7-03 (high) | At very large text the reviewer expected "102.5 kg" and the timers to break mid-number | The big numbers now have a size limit at large text, and a 200% test guards them (D-103). The re-check found the reviewer's premise was wrong, because Android already grows big text less; the limit's first version made the numbers smaller, which is fixed (N1 below) |
| R7-04 (medium) | Some figures moved oddly between their start and end: the triceps extension swung through the head, the side-plank reach swung over it, and the bird dog and step-up snapped halfway | Fixed, with a test that nothing jumps during a rep |
| R7-05 (medium) | At 200% text, day names were cut off and the ring's words spilled out of it | The week wraps onto a second line, and the ring's words fit |
| R7-06 (medium) | Four figures contradicted the coaching text:<br>• the slider curl dropped the hips;<br>• the single-leg Romanian deadlift held the weight on the wrong side;<br>• the calf raise had the heel on the step;<br>• the trap-bar plates floated | Each fixed and tested |
| R7-07 (medium) | On Today's card, a wide figure (a bench press) could sit under the session's name | The figure keeps to the right side and the words sit on a dark veil |
| R7-08 (medium) | The design document and decisions didn't match the code in places | Corrected |
| R7-09 to R7-14 (low) | • The step target and best run were only spoken by TalkBack, not shown.<br>• A focused field label was too faint in the light theme.<br>• Safety-button words could still split at very large text.<br>• The figure halo used a blur that not every phone draws.<br>• The rest ring refilled after ±30 s.<br>• The date followed the phone's language instead of the app's; a few icons were unused | All fixed |

The reviewer then **re-checked the fixes**. It found 4 more problems, all now fixed:

| # | What was wrong | Fix |
|---|---|---|
| N1 (medium) | The large-text limit made the big numbers smaller for people who chose bigger text | The limit now works through the phone's own text-size conversion and never goes below normal size. A test checks the size at 200% |
| R7-01, remainder | The pulldowns still ended with the elbow pointing up | Each pulling arm keeps one elbow direction from start to end. A test checks that every key position is drawn exactly as defined |
| N2 (low) | At large text the three safety buttons stacked into a tall bar, and on narrow phones a word could still split | The bar measures the words on that phone and uses three, two or one buttons to a line, whichever keeps every word whole |
| N3, N4 (low) | The rest ring refilled after turning the phone, and a stray compiled file was committed | Fixed |

## What this means for you
- **The look is the one you chose.** The bright orange of the mockup is now a little deeper on the buttons, so their white words stay readable in a bright gym; the glow and the accents keep the brighter orange. Light theme is in Settings.
- **The figures show the movement, not your form.** They are simple, original drawings of one repetition, with the main form point marked. The coaching cues are still written beside them. Any figure that looks wrong to you on your phone can be adjusted quickly, because each is a few numbers.
- **Large text works.** At your phone's largest text size every word grows. The very large numbers keep their size, which is already bigger than the enlarged text, so they never break.
- **Calories, heart rate and photos from the reference picture aren't in the app.** Your sessions, minutes, steps and the drawn figures take their place (D-099).

## Known limits
- Tested on emulators and in pictures only. **Phase 4 puts the app on your Motorola Edge 50**, where you can judge the look, the figures and the glow in real gym light.
- Screens change at once, without sliding animations. The figures and the rest ring are the moving parts.
- The figures are drawings for guidance, reviewed by eye against each exercise's cues, not by a coach. Phase 4 includes your review of them.
- Before any public release, the condition table and the coach's wording still need a clinician and a regulatory review (D-058).

## Phase 3 plan (8 parts, D-091)

| Part | Content | Status |
|---|---|---|
| 1 | Engine core, safety kernel, project foundation | Done |
| 2 | Exercise library, 12-month programme, week planner, session generator | Done |
| 3 | Research Update 1.1 in the engine | Done |
| 4 | Data layer: storage, history → engine, steps, own numbers, backup and restore | Done |
| 5 | Screens: onboarding, today, check-in, preview, workout and rest timer, summary, safety stop, settings | Done |
| 6 | History and progress, coach and "Why?" (SAF-009, COACH-001), Ask Gemini, more settings | Done |
| 7 | Look and feel (CR-007): your choice of look D "Glow" applied everywhere; exercise figures for all 157 exercises (CR-004) | **Done (this report)** |
| 8 | Integration, research-update workflow, Phase 3 gate (152/152 rules) | Next |

```
================================================================================
PHASE 3 — PART 7 OF 8 COMPLETE. Reply CONTINUE.
================================================================================
```
