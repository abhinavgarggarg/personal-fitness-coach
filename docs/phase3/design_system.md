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
| Warm (main) | #FF6B2C, gradient #FF5A1F → #FF6B2C → #FF9248 | #E8551C | The main button, the active tab, today's ring, the current week, selected answers |
| Cool | #2DE2D0, links #5BE7D9 | #0B7F75 | Done states, coach answers, "Why?" links, goals met |
| Caution | #FFB020 (tint 10%) | #9A6200 | "Something hurts", things to watch |
| Alert | #FF5A6E (tint 12%) | #C2253C | "Warning signs", stops |

## Type
- **Font:** Plus Jakarta Sans, built into the app so it works offline. It uses the SIL Open Font License; the licence ships in `assets/licenses/`.
- **Numbers (set targets, rest timer):** 76 sp, ExtraBold, tight letter-spacing.
- **Screen titles:** 26 sp, ExtraBold.
- **Card titles:** 20 sp, ExtraBold.
- **Body:** 16 sp, Medium.
- **Labels and notes:** 14 sp, SemiBold.

## Shapes
- **Cards:** 22 dp corners.
- **Chips and small buttons:** 16 dp corners.
- **Main buttons and the tab bar:** fully rounded pills; the tab bar floats above the bottom edge.
- **Progress:** rings with round caps; bars with rounded tops.

## Glow, motion and touch
- **Glow:** a soft blurred halo in the warm colour behind the main button, the active tab marker and the progress rings. It is drawn once, never animated.
- **Motion:** 150–250 ms ease for screens and cards. The exercise animations loop slowly (about 4 s per rep), and they become still pictures when the phone's "remove animations" setting is on.
- **Touch:** a short tick when a set is logged; the existing buzz when a rest ends. Nothing vibrates otherwise.

## Icons
- **Style:** simple 2 dp line icons drawn in the app, with no icon library.
- **Tab bar:** home, history (clock), progress (bars), coach (speech bubble).
- **Safety bar:** heart with a plus (something hurts), triangle (warning signs), stopwatch (change time).
- **Other:** settings (sliders), swap and check.
- **Labels:** the tab bar shows icons only, as in the chosen look. Each icon has its spoken name for TalkBack, and the current tab is marked.

## Exercise pictures (CR-004)
- **Figures:** one original figure for every exercise in the library (157), light grey on a dark "studio" card with a soft warm halo. The far arm and leg are drawn darker and a little behind, so left and right read apart. Equipment is drawn simply: plates, dumbbells, kettlebells, benches, boxes, cables (thin lines to a pulley), bands (teal), bars and machine frames.
- **How they are made:** each figure is computed from a few joint angles for the start and the end of one rep (or a point a hand or foot reaches for), with fixed segment lengths, so every exercise shares one style and nothing is a copied picture. Most are seen from the side; movements across the body (lateral raises, flyes, side planks, shrugs, the Pallof press) are seen from the front. Code: `app/figure/` (plain Kotlin, tested on the computer), drawn by `ui/ExerciseFigure.kt`.
- **Motion:** start → end → start in a slow loop (about 4 s; 5 s for holds, under 2 s for steps and carries, a quick move for jumps and swings). The loop starts at the end position, and the figure stands still at the end position when the phone's "remove animations" setting is on.
- **Form point:** a dashed line in the cool colour for the main form point where one applies: a flat back on hinges and rows, a straight body on planks and push-ups.
- **Checked by tests:** every library exercise has a figure (and no figure is left over); no part of the body sinks through the floor at any point of the rep; arms and legs never change length; every picture fits a card.
- **Where:** the Today workout card (the session's first exercise) and the workout screen (the current exercise, with its first coaching cue on the card).
- **No photos:** the app has no photo library and no internet. The figures are original drawings, not copies.

## Screens (Part 7)
- **Today:** "Good morning," and the date with a round settings button; anything that needs attention first; this week's ring (sessions done out of planned) with a line of words; tiles for steps (when counted), the session's minutes and the run of weeks (once there is one); the workout on a studio card with its first exercise moving; one glowing Start; each reason as a glass row with its "Why?"; the week as seven glass day tiles.
- **Workout:** the exercise on a studio card; the set as "Set 2 of 3" with dots, and the target as very large numbers ("60 kg × 8 reps"); "Done as planned" glowing; the effort answers as large glass buttons; the rest as a ring with the time inside, ±30 s and skip; "Something hurts" (amber), "Warning signs" (red) and "Change time" as tinted pills in a bar that never scrolls away.
- **Coach:** the question as a warm bubble on the right, the answer as a glass bubble with a teal edge on the left; suggested questions as full-width glass rows.
- **Progress:** tiles for the run of weeks, weeks this year and sessions; weekly minutes as rounded bars (warm, teal once the 150-minute goal is met) with the goal as a dashed line; strength lines in the warm colour with a soft fill.
