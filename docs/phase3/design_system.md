# Design system "Glow" (look D)

Chosen by the Product Owner on 12 October 2026 (D-099) from four directions on the canvas "Fitness Coach Look Directions". Look D was made from the PO's own reference picture. It applies to every screen through the shared theme (`app/ui/theme/Theme.kt`) and components (`app/ui/Components.kt`, `app/ui/Glow.kt`).

## Principles
- **Dark by default.** A deep navy background, frosted-glass cards, and an orange-to-teal glow for the things that matter. A light version is available in Settings.
- **Big numbers first.** Weights, reps and timers are the largest thing on a workout screen.
- **One glowing action per screen.** Only the main button glows: Start, Done or I'm ready.
- **The words carry the meaning.** A colour never says anything on its own; every state also has words, and TalkBack reads them (NFR-08).
- **Gym-proof.**
  - Every touch target is at least 48 dp; main buttons are at least 56 dp.
  - Body text contrast is at least 4.5:1.
  - White text on the orange button is bold and at least 18 sp; it is the large-text case at 3:1 or better.
  - Nothing is cut off at 200% text.

## Colours

| Token | Dark | Light | Use |
|---|---|---|---|
| Background | #101A2B → #0A101C → #080D17 (top to bottom) | #F4F7FB → #EEF2F7 | Every screen |
| Glass card | white 5.5% on the background, 1 dp white 10% border | white, 1 dp #DCE3EC border | Cards, tiles, chips, the tab bar |
| Text | #F4F7FB | #0E1726 | Headings and body text |
| Muted text | #9DB0C6 | #4F5F73 | Notes and labels |
| Warm (accent) | #FF6B2C | #E8551C | The active tab, rings, the current day, selected answers, glows |
| Main button | gradient #D9480F → #EE6424, white words | the same | Start, Done as planned, I'm ready |
| Cool | #2DE2D0 | #0E9C90 | Done marks, the coach's answers, goals met, form guides |
| Link | #5BE7D9 | #0A7268 | "Why?" and other text actions |
| Caution | words #FFCF7A on #FFB020 at 10% | words #8A5600 on #FFB020 at 12% | "Something hurts", things to watch |
| Alert | words #FF9DAA on #FF5A6E at 12% | words #B4203A on #FF5A6E at 10% | "Warning signs", stops |

Every pair a screen uses is checked by `tools/check_contrast.py` (in CI): words at least 4.5:1, large words and icons at least 3:1. The white words on the main button are 18 sp extra bold (large text) and keep 3:1 or better from one end of the gradient to the other.

## Type
- **Font:** Plus Jakarta Sans, built into the app so it works offline. It uses the SIL Open Font License; the licence ships in `assets/licenses/`.
- **Numbers (set targets, timers):** 76 sp, ExtraBold, tight letter-spacing; the rest timer inside its ring is 60 sp.
- **Screen titles:** 26 sp, ExtraBold.
- **Card and section titles:** 22 sp, ExtraBold.
- **Body:** 16 sp, Medium.
- **Labels and notes:** 13–14 sp, Medium or SemiBold.
- **Large text (200%):** every word grows with the phone's text size (Android grows large text less than small text). The very large numbers are held at most at their normal size on screen and the medium numbers at 1.4 times, never smaller than at normal size, so a weight like "102.5" or a 25:00 timer never breaks. The safety buttons sit three, two or one to a line, whichever keeps every word whole on that phone; the week's days wrap onto a second line rather than cut words.

## Shapes
- **Cards:** 22 dp corners.
- **Chips and small buttons:** 16 dp corners.
- **Main buttons and the tab bar:** fully rounded pills; the tab bar floats above the bottom edge.
- **Progress:** rings with round caps; bars with rounded ends.

## Glow, motion and touch
- **Glow:** a soft warm halo behind the main button, the active tab marker and the progress rings (still), and behind the exercise figures (it moves with the figure); the exercise's working muscles glow on the figure.
- **Motion:** the exercise figures loop slowly (about 4 s per rep, 24 pictures a second) and the rest ring runs down; screens change at once, without sliding. The figures stand still when the phone's "remove animations" setting is on.
- **Touch:** a short tick when a set is logged (it follows the phone's touch-feedback setting); the existing buzz when a rest ends. Nothing else vibrates.

## Icons
- **Style:** simple 2 dp line icons drawn in the app, with no icon library.
- **Tab bar:** home, history (clock), progress (bars), coach (speech bubble).
- **Safety bar:** heart with a plus (something hurts), triangle (warning signs), stopwatch (change time).
- **Other:** settings (sliders), swap and check.
- **Labels:** the tab bar shows icons only, as in the chosen look. Each icon has its spoken name for TalkBack, and the current tab is marked.

## Exercise pictures (CR-004, CR-008)
- **Figures:** one original figure for every exercise in the library (157) on a dark "studio" card, drawn as a sculpted mannequin (D-105):
  - shaped body parts: thigh and calf bellies, a trunk with chest, shoulder blades and glutes, a head and hands;
  - light from the upper left, a warm rim on the shadow side and a soft contact shadow on the floor;
  - a faint warm halo behind the body;
  - the exercise's primary muscles glow orange, strongest at the end position of the rep.
- **Far side:** the far arm and leg are darker and a little behind, so left and right read apart.
- **Equipment:** shaded metal plates, dumbbells and kettlebells; benches and boxes; cables (thin lines to a pulley); bands (teal); bars; machine frames.
- **How they are made:** each figure is computed from a few joint angles for the start and the end of one rep (or a point a hand or foot reaches for), with fixed segment lengths, so every exercise shares one style and nothing is a copied picture. Most are seen from the side; movements across the body (lateral raises, flyes, side planks, shrugs, the Pallof press) are seen from the front. Each frame's body shapes are tagged with their segment, and `app/figure/Sculpt.kt` turns them into outlines, shading directions and muscle glows. The screen draws the figure into a picture on the processor (3/4 of the screen's resolution, redrawn only when it moves, 24 times a second) and shows the picture. Code: `app/figure/` (plain Kotlin, tested on the computer), drawn by `ui/ExerciseFigure.kt`.
- **Motion:** start → end → start in a slow loop (about 4 s; 5 s for holds, under 2 s for steps and carries, a quick move for jumps and swings). The loop begins at the figure's "still" position, which is also the picture shown when the phone's "remove animations" setting is on: the end of the rep for most exercises, the start where it tells more (a deadlift or a pull-through starts with the weight low; a sit-to-stand starts seated).
- **Form point:** a dashed line in the cool colour for the main form point where one applies: a flat back on hinges and rows, a straight body on planks and push-ups.
- **Checked by tests:** every library exercise has a figure (and no figure is left over); no part of the body sinks through the floor at any point of the rep; arms and legs never change length; a hand or foot that reaches for a point (a bar, a handle, the floor) gets there in every key position; nothing jumps between two moments of the rep; deadlifts start with the weight on the floor; every picture fits a card. For the sculpted drawing: every body part is drawn in every frame, as a closed shape with real area; nothing sinks through the floor; far limbs go behind the body and the near arm in front; every exercise shows its working muscles; all parts are lit from the same side; the picture stays inside its camera between measured frames; the chest faces the right way.
- **Where:** the Today workout card (the session's first exercise) and the workout screen (the current exercise, with its first coaching cue on the card).
- **No photos:** the app has no photo library and no internet. The figures are original drawings, not copies. Free photo and drawing sets were checked in CR-008; none was safe to ship or covered the library (`docs/change_requests/CR-008_exercise_pictures.md`).

## Screens (Part 7)
- **Today:** "Good morning," and the date with a round settings button; anything that needs attention first; this week's ring (sessions done out of planned) with a line of words; tiles for steps (when counted, with the day's target) and the run of weeks (once there is one, with the best run); the workout on a studio card with its first exercise moving on the right and its name and minutes on the left; one glowing Start; each reason as a glass row with its "Why?"; the week as seven glass day tiles.
- **Workout:** the exercise on a studio card; the set as "Set 2 of 3" with dots, and the target as very large numbers ("60 kg × 8 reps"); "Done as planned" glowing; the effort answers as large glass buttons; the rest as a ring with the time inside, ±30 s and skip; "Something hurts" (amber), "Warning signs" (red) and "Change time" as tinted pills in a bar that never scrolls away.
- **Coach:** the question as a warm bubble on the right, the answer as a glass bubble with a teal edge on the left; suggested questions as full-width glass rows.
- **Progress:** tiles for the run of weeks, weeks this year and sessions; weekly minutes as rounded bars (warm, teal once the 150-minute goal is met) with the goal as a dashed line; strength lines in the warm colour with a soft fill.
