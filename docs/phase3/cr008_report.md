# CR-008 report: sculpted exercise figures

Date: 12 October 2026 · Decision D-105 · Change request CR-008 (`docs/change_requests/CR-008_exercise_pictures.md`)

## Summary
You found the exercise figures not impressive enough. You asked whether one of four free sources could replace them.

I checked all four:
- **free-exercise-db (with wger):** the photos look good, but nobody shows they may be given away, and wger needs internet.
- **Everkinetic:** usable, but it matches only 41 of our 157 exercises exactly.
- **Good-GYM and Smart-AI-Gym-Trainer:** camera rep counters with no exercise pictures.

I then built a comparison page with today's figure, the Everkinetic drawings and a new "sculpted" figure. You chose the sculpted figure (option B).

**Every one of the 157 exercises now shows a sculpted, lit figure:**
- **Shaped body parts:** thigh and calf muscles, a trunk with chest, shoulder blades and glutes, a head and hands.
- **Light and shadow:** light from the upper left, a warm rim on the shadow side and a soft shadow on the floor.
- **Working muscles:** the muscles the exercise works glow orange, most strongly at the end position of each rep.

The poses, the movement and the form line are the same as before. Only the drawing changed.

## What changed

| Area | What you see | Status |
|---|---|---|
| Figures (D-105) | A sculpted, shaded figure for all 157 exercises, in the same slow loop, standing still when the phone's animations are off | VERIFIED |
| Working muscles | Orange glow on the exercise's primary muscles from the library. Examples: legs and glutes on squats; chest and arms on presses; the back on rows and pulldowns; the shoulders on raises; the middle on core exercises | VERIFIED |
| Equipment | Plates, dumbbells and kettlebells drawn as shaded metal; benches and boxes lit from above | VERIFIED |
| Screens | The figures sit where they were: on Today's workout card and on the workout screen | VERIFIED |
| How it is drawn | The figure is drawn into a picture by the phone's processor, then shown. It is redrawn only when the figure moves, up to 24 times a second, at three quarters of the screen's resolution | VERIFIED |

## How it was checked

**Locally: 632 tests pass.**
- 408 engine, 159 data core, 65 app screen logic and figures.
- Nine new tests check the sculpted drawing:
  - every body part is drawn in every moment of every rep, as a real closed shape;
  - nothing sinks through the floor;
  - far limbs stay behind the body and the near arm in front;
  - every exercise shows its working muscles, and every muscle the library uses can light up in both side and front views;
  - all parts are lit from the same side;
  - the picture stays inside its frame between the moments it was measured on;
  - the chest faces the right way (forward when standing, up on a bench, down in a push-up).

**By eye:** all 157 figures were drawn at their resting position and mid-rep, and looked at.

**On GitHub:**
- **CI run 38256469900: green** for the last code change, 8a20f4f.
  - 665 tests pass: engine 408, data 176 and app 81.
  - The app tests include a new picture test: the body is drawn, the working muscles show in orange, and without them far less orange shows.
  - One picture for the workout card took 5.3 ms on the test machine.
- **Other CI checks:**
  - rule coverage 152/152;
  - permissions unchanged, with no internet;
  - contrast and strings checks pass;
  - the APK builds.
- **Devices** (run 38256478962): 9 of 9 device tests pass on all five emulated devices. Android 10 needed a second try; see "Known limits".
- **Screen pictures** (Screenshots run 38256481920): every screen and all 157 figures, redrawn in the new style, are in `docs/phase3/screens/`.

## A problem found and fixed on the way
**The problem.** At first the app drew the figure's shaded shapes straight to the screen. The Android 14 and 16 test emulators then stopped completely. The app recorded no crash; the emulator itself disappeared.

**Finding the cause.**
- Each drawing operation on its own worked, so I could not single out one shape or colour fill.
- The version from before this change passed on all five devices.
- Switching the emulators' graphics mode did not help.

**The fix.** The app now draws the figure into a picture with the processor, and the screen shows that picture as one simple image. This also redraws only when the figure moves, and fewer times a second than the screen updates. The device tests now pass on all five devices.

## What this means for you
- **Battery and speed.** Drawing the figure on the processor costs a little work while it moves: about a tenth of one processor core on the test machine. Phase 4 will measure it on your Motorola Edge 50. If it turns out too heavy, the picture can be made smaller or redrawn less often.
- **Original drawing.** The figure is still our own, so there is nothing to credit and no licence to track. It works offline and adds almost nothing to the app's size.
- **Working muscles.** The glow shows the main working muscles, not every muscle involved. The coaching cues still sit beside the figure.
- **On your phone.** Phase 4 puts the app on your Motorola Edge 50. There you can judge the figures in real gym light, and any one that looks wrong can be adjusted.

## Known limits
- **Android 10 device test.** It failed twice in about nine runs and passed when run again. Both times the test's own screen checks ran at the same moment as the app's layout, and Compose (the screen toolkit) refused. This is a timing race in how the device test reads the screen, which this change made a little more likely. Part 8 (integration) will make those checks wait for the screen to be still first.
- **Temporary test branches.** Two branches used only to find the emulator problem remain on GitHub, `probe/gpu` and `probe/pre-cr008`. I can't delete branches from here; they can be deleted on GitHub and nothing depends on them.

## Not done
- **No outside photos or drawings.** Third-party pictures were not added, for the reasons in the CR-008 evaluation.
- **No camera form checking.** Checking your form with the phone's camera (the idea behind Good-GYM) would be a separate change request. It would need the camera permission and a built-in pose model.
