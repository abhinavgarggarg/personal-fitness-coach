# CR-008: better-looking exercise pictures

Date: 12 October 2026 · Requested by the Product Owner · Status: **done: option B chosen (D-105); report `docs/phase3/cr008_report.md`**

## The request
The PO finds the Part 7 exercise figures (CR-004, D-100) not impressive enough for the app, and asked whether one of four free sources could be used instead:

1. wger, linked as `yuhonas/free-exercise-db`;
2. Everkinetic (`everkinetic/data`);
3. Good-GYM (`yo-WASSUP/Good-GYM`);
4. Smart-AI-Gym-Trainer (`Preethamn15/Smart-AI-Gym-Trainer-With-RealTime-Exercise-Tracking-and-Nutrition-Assistant`).

## What each source is (checked 12 October 2026)
The two data repositories were downloaded and read without running any of their code; the two AI projects were read on GitHub.

| Source | What it is | Licence and rights | Fit with the app | Verdict |
|---|---|---|---|---|
| free-exercise-db | 873 exercises, each with two studio photos (start and end, 850 × 567), about 200 MB in total | The repository says Unlicense (public domain). Its README says the data was restructured from `wrkout/exercises.json`, and neither project says who took the photos or shows that they may be given away. A public-domain label on a repository does not give rights in photos the uploader did not own | Could be bundled offline, but D7 needs original or openly licensed assets, and these can't be shown to be either | **Not safe to ship** |
| wger (the project itself) | A workout website with a REST API; its pictures are contributor uploads, each with its own licence and author, and many exercises have none | Mostly CC BY-SA | The API needs internet; the app has no internet permission (NFR-07). Its drawings are largely Everkinetic's | Covered by Everkinetic below |
| Everkinetic | 289 exercises; 269 have two black line drawings (start and end, "relaxation" and "tension") as PNG and traced SVG | CC BY-SA 4.0: use with a credit line; changed drawings stay CC BY-SA. Other open apps already reuse them under this licence | Bundled offline it fits D7 and NFR-07 | **Usable, with gaps** |
| Good-GYM | A Python desktop program that counts reps from a webcam with the RTMPose model | MIT | Contains no exercise pictures. Reusing its idea would need the camera permission and an on-device pose model | Not a picture source |
| Smart-AI-Gym-Trainer | Python/Flask camera rep counter (7 exercises) plus food recognition (YOLOv5) | No licence stated, so no permission to reuse; the author says the main folder isn't uploaded | Food features are excluded (D-060) | Not usable |

### Everkinetic coverage of our 157 exercises
Each of our 157 exercises was matched by hand to Everkinetic's list by name, movement and equipment:

| Match | Count |
|---|---|
| Exact (same movement and equipment) | 41 |
| Close (same movement, other equipment or variant, for example a barbell for a bodyweight box squat) | 35 |
| None | 81 |

Close matches would contradict the coaching text (the problem fixed in review R7-06), so only the 41 exact ones are usable. Nothing matches any kettlebell, carry, Pallof, dead bug, bird dog, bear, front plank, hip thrust or sit-to-stand exercise, and almost nothing matches the band exercises. Twenty Everkinetic entries have no drawing at all, among them the wide-grip lat pulldown, the dumbbell shoulder press, the machine shoulder press and the pec deck. The two drawings of an exercise are often framed at different sizes, so they can be shown in turn but not animated through the rep.

## Options put to the PO
A side-by-side page shows the same seven exercises in three styles, moving: today's figure, Everkinetic recoloured for the dark card, and a prototype "sculpted" figure. It is the artifact "Exercise Picture Options".

- **Option B (recommended): sculpted figure for all 157.**
  - The same poses and tests (D-100). The figure is drawn with shaped, shaded limbs and trunk, light from one side and a warm rim light.
  - The exercise's primary muscles (from the library) glow orange.
  - It stays original: no credits and no downloads, and the app grows by almost nothing.
  - Work involved:
    - the renderer in `app/ui/ExerciseFigure.kt`;
    - a muscle-to-body-part map;
    - tests and screen pictures;
    - a review of all 157 figures.
- **Option A: Everkinetic drawings for the 41 exact matches, with today's figure for the other 116.**
  - A workout would mix two styles.
  - The drawings are two still poses per exercise.
  - It needs a CC BY-SA credit in Settings and a few MB of images.
- **Keep today's figure** and judge it on the phone in Phase 4.

Camera-based form checking (the idea behind sources 3 and 4) is a separate possible change request. It would need the camera permission and an on-device pose model.

## Sources
- https://github.com/yuhonas/free-exercise-db
- https://github.com/wrkout/exercises.json
- https://github.com/MikeMoore1337/your-fitness-coach/issues/389 (a warning that a repository-level Unlicense does not cover the media)
- https://github.com/everkinetic/data (LICENSE.md: CC BY-SA 4.0)
- https://github.com/InlitX/GymMane/blob/master/CREDITS.md (an app reusing Everkinetic drawings under CC BY-SA 4.0)
- https://github.com/yo-WASSUP/Good-GYM
- https://github.com/Preethamn15/Smart-AI-Gym-Trainer-With-RealTime-Exercise-Tracking-and-Nutrition-Assistant
