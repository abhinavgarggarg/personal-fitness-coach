# Phase 4, Part 5: your phone test (UAT, E7)

You test the app on your Motorola Edge 50 (Android 16). It takes about 60–90 minutes in total, spread over a few days is fine. You don't need to type any code. For every step, tick **Pass** or **Problem**. For a problem, write one line on what you saw (a screenshot helps) and send it to me.

The test version installs next to nothing else: it is called "Personal Fitness Coach (dev)" and keeps its own data, so it can't touch the final app later.

## A. Install the test version (once)

1. On your phone, open the GitHub page of the project and go to **Actions → CI → the newest green run on `main`**. At the bottom, under **Artifacts**, download **personal-fitness-coach-debug-…** (a zip, about 13 MB). I will also tell you the exact run number when the build is ready.
2. Open the zip in your Files app and tap the `.apk` file. Android asks to allow installs from this source: allow it, then tap **Install**.
3. If Android says "Play Protect blocked it", tap **More details → Install anyway**. This is normal for a test build.

## B. The 15 steps

| # | What to do | What you should see | Pass / Problem |
|---|---|---|---|
| 1 | **First start.** Open the app on a fresh install. Answer the questions (equipment, goals, days, health). Time yourself. | Each question is clear; no medical jargon; a first week appears. Under 5 minutes. If you answer "yes" to a warning sign, the app stays gentle. | |
| 2 | **Close and reopen in the middle of the questions** (swipe the app away, open it again). | It offers to continue at the same question. | |
| 3 | **Today screen.** Look at the screen when it opens. | It opens in about a second. Today's workout, the week, and one clear **Start** button. Text is easy to read in the gym. | |
| 4 | **How do you feel today?** Tap Start and answer the check-in. Time yourself. | Under 15 seconds. One line says why today is full, lighter or shorter. Tapping **Why?** explains. | |
| 5 | **First workout, finding your weights.** Do the first exercise. Use the weight the app suggests, and say how hard it felt. | Early sets build up gently; nothing feels like a max effort. After a few sets it settles on a working weight. | |
| 6 | **Log a set with one tap.** Finish a set exactly as planned. | One tap saves it, instantly. To change weight or reps you use + / − buttons, no keyboard. | |
| 7 | **Rest timer with the screen off.** After a set, lock the phone and wait. | The phone vibrates and sounds within a second of the end of the rest. Logging the next set stops any leftover alert. | |
| 8 | **Replace an exercise.** Pretend a machine is busy and tap **Replace**. | Three options, each with a short reason, all using equipment you have. | |
| 9 | **Something hurts.** In a workout tap the pain button and report a mild joint ache (say 3 or 4 out of 10). | The app lowers the weight or offers a pain-free swap. It never tells you to push through. Then report a strong, sharp pain: it stops that exercise. | |
| 10 | **Shorter session.** Another day, tell the app you only have 30 minutes. | It keeps the warm-up and the main lift and tells you what was moved or left out. | |
| 11 | **Finish and summary.** Complete a workout and answer how hard it was. | One effort question; then your records, what changed today and the next session. | |
| 12 | **History and progress.** Open History and Progress. | Every set you logged is there; the numbers match what you did. | |
| 13 | **Ask the coach.** Open the coach and tap two or three of the suggested questions, with Wi-Fi and data off (flight mode). | Plain, correct answers that name the reason; works with no connection. | |
| 14 | **Back up and restore (the real-phone test).** Settings → back up to a file you can find. Then Settings → Erase all my data (two confirmations). Then restore from the file. | Everything is back exactly: your workouts, weights and plan. A wrong or damaged file is refused and changes nothing. | |
| 15 | **TalkBack and large text.** Turn on TalkBack once, log a set with it, and turn it off. Then set the font size to the largest and open Today and a workout. | TalkBack reads each button clearly and a set can be logged; at the largest font nothing is cut off. | |

**Android's own backup (separate, optional):** the emulators already prove the app's side of this. Whether your Motorola actually restores the data after a reinstall depends on the phone's Google backup, which can only be seen on the real phone. If you want to test it, I will give you the exact taps in a short message after step 14; it costs about 10 minutes.

## C. Extra things to watch for while you use it

- Anything slow, jumpy or that makes you wait.
- Any word you don't understand.
- Any time the app suggests something you think is unsafe or unrealistic for you.
- Anything that makes you tap more than once when once should do.
- Buttons that are hard to hit with one hand in the gym.

## D. What I do with your answers

Every problem is classed as: **safety** (fixed first, may need your approval if it changes a rule), **broken** (fixed), or **taste** (your call). I fix, rebuild, and ask you to repeat only the steps that changed. When all 15 steps pass, I write the Phase 4 gate report for your sign-off.

## E. Choices where you said "no preference" (D-123)

At the gate I will ask you to confirm or reverse these cautious defaults I took:

1. Strong muscle pain reported at the check-in also limits that area for the rest of the day.
2. With osteoporosis and low back pain, the bone-loading block stays on heel drops.
3. After a warning-sign stop, you count as "back" only from sessions that started after the stop.
4. On lighter days, bodyweight and timed work keeps its easier version.
