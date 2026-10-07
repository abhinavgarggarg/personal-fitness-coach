# Personal Fitness Coach — Phase 1: Fitness Research & Evidence Engine

Oct 7, 2026 · Prepared for @Abhinav Garg · Prompt v3.0

Phase 1 turns the exercise science into 140 numbered, testable rules for the app's fitness engine, and it is ready for your sign-off. The evidence firmly supports a simple core — train each muscle twice a week, about 10+ hard sets per muscle weekly, most sets 1–3 reps short of failure, heavier loads for strength, cardio alongside — while readiness scoring, deload triggers and HIIT limits are honest coaching judgement, labelled as such, to be stress-tested in Phase 4.

**How to review (about 20 minutes).** Read this summary, the gate report at the end, and skim sections 22–25 (readiness, deloads, the 12-month plan) and 35 (safety). Everything else is reference detail the engine will follow. Comment anywhere you disagree; reply **APPROVED** in chat when you're happy.

**What you'll notice as a user.**

- A 4-tap check each day sets a FULL, MODIFIED, LIGHT or RECOVERY session; pain or warning symptoms bypass it entirely.
- Workouts fit the time you have by keeping the most valuable work, not by cutting the end off.
- Weights rise in the smallest steps your gym allows, reps first when the next dumbbell is a big jump.
- No treadmill running, bikes or stair machines; rower, SkiErg, elliptical, sleds, ropes and carries instead.
- Deloads happen when your data say you need one, not on a fixed calendar.
- Every change has a one-line "why", traceable to a rule.

**Labels used throughout.** Evidence confidence: High, Moderate, Limited, Expert Practice, plus Product rule for engineering decisions. Status: VERIFIED (and how), REQUIRES VALIDATION, ASSUMPTION, NOT STARTED. Sources: Checked, Located or Unverified (see Sources).

**What happens next.** On approval, Phase 2 writes the product requirements, screens and technical architecture. No code is written before Phase 2 is approved.

## 1. Environment & capability audit

This environment can research, write and run analysis scripts, but it cannot build or run an Android app. APK delivery will therefore use the cloud-build route (F3 Option B: GitHub Actions). Phases 1 and 2 are unaffected. Audited 7 October 2026; re-audit due before Phase 3 and Phase 5.

| Capability | Available? | How verified | Impact on plan |
| --- | --- | --- | --- |
| Web research / search | Yes | Used for every source check in this report | Sources located this session are marked VERIFIED; anything recalled but not located is UNVERIFIED |
| Terminal / shell | Yes | Ran commands | Can run scripts to check maths and simulate rules |
| File system | Yes, session-only | Wrote files | Working copies live here; permanent copies go to the "Fitness App" Project |
| git | Yes | Command present | Local version history possible |
| GitHub access | **No** | GitHub login failed (no valid token) | Phase 3 needs a free GitHub account from you, one-time setup |
| Android SDK | **No** | Not installed; Google's download host blocked by network policy | Cannot compile Android here |
| Flutter SDK | **No** | Not installed; Flutter download host blocked | Cannot compile Flutter here |
| Gradle | Installed (8.14.3) | `gradle --version` | Unusable for Android: Maven Central, Google Maven and the Gradle Plugin Portal are blocked |
| Kotlin | Only bundled inside Gradle (2.0.21) | `gradle --version` | No standalone compiler |
| Java / JDK | Yes (OpenJDK 21) | `java` present | Not enough on its own for Android |
| Emulator | **No** | No SDK, no hardware virtualisation (`/dev/kvm` absent) | Cannot run the app here |
| Physical device | **No** | No device connection tools, no device | Device testing happens on your phone during UAT |
| Package tools | Partly | Python (pip) and Node (npm) registries reachable | Python available for algorithm prototypes and the 12-month simulations |
| Automated testing | Partly | Python test tools installable | Engine logic testable here; Android UI tests must run in the cloud build |

**What this means for you:** nothing changes in Phases 1–2. Before Phase 3, you will create a free GitHub account (about 10 minutes, step-by-step instructions provided). From then on the app is compiled, tested and packaged in the cloud on every change, and the APK appears on a GitHub Releases page you open on your phone.

## 2–4. Evidence map, confidence matrix and seed-parameter validation

The evidence strongly supports a simple core: train each muscle about twice a week, accumulate roughly 10+ hard sets per muscle per week, stop most sets 1–3 reps short of failure, use heavier loads for strength, and do aerobic work alongside without fear of cancelling strength gains. Most numbers beyond that core (readiness weights, deload triggers, workload ratios) are coaching practice, not proven science, and are labelled as such.

**How sources are labelled.** *Checked* = abstract or publisher summary read this session. *Located* = title, year and journal confirmed this session; author list, pages and the finding come from prior knowledge. *Unverified* = not located this session. Full list in the Sources section.

### 2. Evidence map

| Domain | Main sources | What the evidence says | Transfer to you |
| --- | --- | --- | --- |
| Overall resistance-training prescription | ACSM Position Stand 2026 (checked); Currier 2023 (checked) | ≥2 days/week; ≥80% 1RM and 2–3 sets for strength; ≥10 sets/muscle/week for size; failure, equipment type and complex periodisation not required for average adults | High: healthy-adult populations |
| Volume dose–response | Pelland 2024 preprint (checked); Schoenfeld 2017 (located) | More sets → more growth, with diminishing returns; strength plateaus sooner than size | Moderate: mostly young, short studies |
| Frequency | Schoenfeld 2016, 2019; Grgic 2018 (located); Pelland 2024 | With volume equal, frequency barely matters for size; it modestly helps strength | Moderate |
| Load | Schoenfeld 2017, 2021 (located); Currier 2023 | Size grows across \~30–85% 1RM if sets are hard; strength needs heavier loads | High |
| Proximity to failure | Robinson 2024 (checked); Refalo 2023; Grgic 2022 (located) | Strength similar across a wide RIR range; size improves as sets end closer to failure; failure itself adds little | Moderate |
| RPE / RIR accuracy | Zourdos 2016; Helms 2016; Halperin 2022 (located) | RIR estimates err by \~1 rep, worse far from failure and in novices | Moderate |
| Rest intervals | Singer 2024 (checked); Grgic 2018 (located) | >60 s gives a small size benefit; no extra benefit beyond \~90 s; strength favours ≥2 min | Moderate |
| Exercise order | ACSM 2026; Nunes 2021 (located) | Exercises done first gain more strength; size is order-insensitive | Moderate |
| Concurrent training | Schumann 2022; Wilson 2012; Murlasits 2018; Robineau 2016; Sabag 2018 (located) | Little interference with size or max strength; power is blunted, especially same-session; strength-first helps lower-body strength | Moderate |
| Aerobic & HIIT | Milanović 2015; Wen 2019; Buchheit & Laursen 2013; Weston 2014 (located); Foster 2018 talk test (checked) | HIIT and steady work both raise VO2max; longer intervals and longer programmes work best; talk test tracks ventilatory threshold | Moderate |
| Public-health floor | WHO 2020 (Bull 2020); Garber 2011 (located) | 150–300 min moderate or 75–150 min vigorous aerobic; strength ≥2 days | High |
| Screening | Riebe 2015; Warburton 2011 PAR-Q+ (located) | Screen for disease, symptoms and current activity; clearance only when indicated | High (consensus) |
| Training load | Foster 2001, 1998 (located); Saw 2016 (checked) | Session-RPE is a valid load measure; self-reported wellbeing tracks load better than many objective markers | Moderate |
| Workload ratio (ACWR) | Gabbett 2016; Williams 2017; Lolli 2019; Impellizzeri 2021 (located) | Popular but statistically flawed; weak causal evidence for injury prediction | Low |
| Sleep | Craven 2022 (checked) | Acute sleep loss cuts performance \~7.6% on average; worse in afternoon sessions | Moderate |
| Deloading | Bell 2023 Delphi (checked); Coleman 2024 (checked); Meeusen 2013 (located) | Coaches deload every 4–6 weeks for \~7 days by cutting volume; a week of total rest gave no benefit and slightly less strength | Low–Expert |
| Periodisation | Moesgaard 2022 (located); ACSM 2026 | Periodisation helps strength a little, not size; complexity not needed for general fitness | Moderate |
| Detraining | Bosquet 2013 (located) | Strength holds for a few weeks off, then declines; older adults lose more | Moderate |
| Mobility & warm-up | Behm 2016; Afonso 2021; Fradkin 2010; Jeffreys RAMP (located) | Short static stretches barely affect strength; full-range strength training improves mobility as well as stretching; warm-ups improve performance | Moderate |
| Older adults | Fragala 2019 NSCA (located); WHO 2020 | Add power and balance work; progress gradually | High |
| Sex differences | Roberts 2020 (located) | Similar relative gains; no separate programme needed | Moderate |
| Adherence | Lally 2010; Mazeas 2022 (located) | Habits take weeks to months (median \~66 days); gamification gives small–moderate activity gains | Moderate |
| Volume landmarks (MEV/MAV/MRV) | Israetel et al. (book, located) | Useful coaching vocabulary; not directly tested | Expert Practice |

### 3. Evidence confidence matrix

| Claim the engine relies on | Confidence |
| --- | --- |
| ≥2 resistance sessions/week improve strength and size | High Evidence |
| Weekly volume drives hypertrophy with diminishing returns | High Evidence |
| \~10+ hard sets/muscle/week as a hypertrophy target | Moderate Evidence |
| Heavier loads (≥80% 1RM) maximise strength | High Evidence |
| Hypertrophy across a wide load range when sets are hard | High Evidence |
| Stopping 1–3 reps short of failure is enough for most goals | Moderate Evidence |
| RIR = 10 − RPE between RPE 5 and 10 | Moderate Evidence |
| Rest >60 s (ideally \~90–180 s) for hypertrophy; ≥2 min for heavy strength | Moderate Evidence |
| Aerobic training does not meaningfully blunt size or max strength | Moderate Evidence |
| Concurrent training blunts power, especially same-session | Moderate Evidence |
| Strength before cardio in the same session helps lower-body strength | Moderate Evidence |
| Session-RPE × minutes is a valid load measure | Moderate Evidence |
| ACWR predicts injury | Limited Evidence (treated as unproven) |
| Readiness score weights and thresholds | Expert Practice |
| Deload triggers and prescription | Expert Practice (Limited Evidence) |
| MEV/MAV/MRV landmark values | Expert Practice |
| HIIT ceiling of 2–3 sessions/week for general fitness | Expert Practice |
| Pain thresholds for continuing exercise | Limited Evidence (rehab data) + Expert Practice |

### 4. Seed parameter validation (S01–S22)

| ID | Seed | Decision | Final value | Confidence | Rule |
| --- | --- | --- | --- | --- | --- |
| S01 | 2–6 days/week | Confirmed | 2–6 days; at least 1 full rest day | High (≥2) / Expert (upper bound) | FREQ-001 |
| S02 | 1×, 2× or 3× per muscle | Adjusted | Default 2×; volume split so no muscle exceeds the per-session cap | Moderate | FREQ-002, VOL-005 |
| S03 | MV/MEV/MAV/MRV | Adjusted | Kept as vocabulary; numeric bands anchored to dose–response data; MRV replaced by a hard engine cap | Expert Practice | VOL-003 |
| S04 | RIR = 10 − RPE | Confirmed with limits | Valid for RPE 5–10; RPE <5 not used for working sets; beginners get rep targets first | Moderate | INT-001 |
| S05 | 1–5 reps at ≥85% | Adjusted | 3–6 reps at \~80–88% for strength blocks; 1–3 reps only for advanced users at RPE ≤8 | High (load) / Expert (exact range) | REP-001 |
| S06 | 6–15 reps at 65–80% | Confirmed as practical default | Compounds 6–12, isolation 8–20; not a physiological boundary | High | REP-002 |
| S07 | 15–30 reps at <60% | Confirmed | 15–30 reps for endurance-emphasis sets | Moderate | REP-003 |
| S08 | Velocity-matched loading | Adjusted (no device) | 3–5 reps at \~30–70% 1RM with maximal intent; stop when speed visibly drops; reps × sets < 24 | Moderate | REP-004 |
| S09 | Rest 180–300 s heavy | Adjusted | 120–300 s, default 180 s | Moderate | REST-001 |
| S10 | Rest 60–120 s moderate | Adjusted | Compounds 90–150 s (default 120); isolation 60–90 s (default 75) | Moderate | REST-002, REST-003 |
| S11 | Rest 30–60 s conditioning | Confirmed | Set by work:rest ratio of the chosen protocol | Moderate | REST-005, HIIT-002 |
| S12 | HIIT ≤2–3/week | Confirmed, tightened | Default ≤2; 3 only for intermediate/advanced on FULL days; hard circuits count | Expert Practice | HIIT-001 |
| S13 | HIIT 1:1, 1:2, 2:1 | Confirmed as menu | Ratio chosen by interval type, not by popularity | Moderate | HIIT-002 |
| S14 | +2.5–5% when targets met | Confirmed | Smallest real increment ≥2.5%, ≤5%; rep progression when the jump is too large | Moderate / Expert | PROG-001–003 |
| S15 | RPE > target + 1.5 → hold/reduce | Confirmed, specified | +1 to +1.5 → hold; > +1.5 → −5%; twice in a row → −10% and review | Expert Practice | PROG-004 |
| S16 | sRPE = RPE × minutes | Confirmed | Collected at summary, after cool-down; editable for 24 h | Moderate | LOAD-001, LOAD-002 |
| S17 | ACWR 0.8–1.3; spike >1.5 | Downgraded | EWMA ratio used only as a soft spike flag after 28 days; never an injury prediction | Limited | LOAD-004 |
| S18 | Readiness 0–100; <40 low | Adjusted | Weighted score, personal-baseline blend after 14 check-ins, four tiers; pain is a separate gate | Expert Practice | RDY-001–004 |
| S19 | Stagnation ≥2 sessions → deload | Adjusted | Six fatigue signals; 3+ → deload now, 2 → lighter week | Expert Practice | DEL-001, DEL-002 |
| S20 | Cardio >6 h from strength | Adjusted | Only when both are hard and on the same day; otherwise strength first in one session | Moderate | CON-001, CON-002 |
| S21 | <4 h sleep → protect | Specified | <4 h: cap at LIGHT; 4–5 h: cap at MODIFIED | Moderate (effect) / Expert (caps) | RDY-005 |
| S22 | Readiness ≤15 s | Confirmed (UX) | 4 taps + pain yes/no | UX constraint | RDY-001 |

## 5–6. Core principles and how the ten objectives fit together

The engine runs on twelve principles, and it treats strength, size, endurance, power and mobility as distinct qualities with distinct doses. Strength, size and aerobic fitness coexist well; power and hard intervals need protecting from each other; body composition is mostly decided outside the gym.

### 5. Core fitness principles

1. **Safety outranks everything.** No rule, request or AI output can push past a safety limit (SAF rules).
2. **Consistency beats complexity.** The best programme is the one you actually do (ACSM 2026). Plans stay simple and forgiving.
3. **Effort defines a set.** A set counts as "hard" only if it ends within 4 reps of failure; most work ends 1–3 reps short (VOL-001, INT-002).
4. **Volume must be recoverable.** Start near the low end, add sets only while performance rises and recovery holds; returns diminish (VOL-003, VOL-006).
5. **Specific stimulus, specific result.** Heavy loads for strength, hard sets for size, fast intent for power, minutes for aerobic fitness (REP rules).
6. **Progress in small, real steps.** Loads rise by the smallest plate or dumbbell jump you own; reps rise first when the jump is too big (PROG rules).
7. **Balanced movement.** Every main movement pattern is trained weekly; pulling at least matches pushing (PAT rules).
8. **Strength and cardio coexist.** Strength first in a shared session; hard intervals kept away from heavy legs and power work (CON rules).
9. **Today counts.** The plan bends to today's readiness and today's performance, within fixed bounds (RDY, INT-007).
10. **Fatigue is managed by signals, not calendars.** Deloads happen when signals say so (DEL rules).
11. **Measure honestly.** Session-RPE, reps, loads and readiness are proxies; the app never claims to measure fatigue directly (LOAD rules).
12. **Every rule shows its confidence.** Expert-practice rules are labelled and reviewed as evidence arrives (B6, B7).

### 6. Training objective interaction model

**What each quality needs** (the boundaries the engine enforces):

| Quality | Primary driver | Default dose in the engine | Stimulus check |
| --- | --- | --- | --- |
| Maximal / relative strength | Heavy load, skill practice | 3–6 reps at \~80–88% 1RM, RIR 1–3, 2–3 exposures/week per main pattern | Rising e1RM on main lifts |
| Hypertrophy | Hard sets accumulated weekly | 6–20 reps, RIR 0–3, \~10–16 fractional sets/muscle/week | Sets logged within target RIR |
| Muscular endurance | Higher reps, short rest | 15–30 reps or timed sets, 30–60 s rest | Reps at fixed load rise |
| Power | Maximal intent, moderate load | 3–5 reps at 30–70% 1RM, stop when speed drops | Quality reps, no grinding |
| Aerobic capacity | Minutes at easy–moderate effort | Mostly talk-test-comfortable work, 90–300 min/week | More distance or watts at the same effort |
| High-intensity conditioning | Repeated hard intervals | ≤2 sessions/week (≤3 if advanced) | Interval output held across reps |
| Core & trunk | Bracing against movement | 3 anti-movement categories weekly | Longer holds, harder levers |
| Mobility | Full-range loading + dynamic prep | Every session warm-up; optional off-day routine | Range gained in key positions |
| GPP / functional capacity | Carries, sleds, unilateral and varied work | Woven through accessories and conditioning | Carry distance and load rise |
| Body composition | Mostly nutrition; training preserves muscle | No calorie-driven programming | Bodyweight/measurements, optional |

**Which qualities share a session and which compete** (read across a row):

|  | Strength | Hypertrophy | Power | Aerobic (easy) | HIIT | Mobility |
| --- | --- | --- | --- | --- | --- | --- |
| **Strength** | — | Good | Good (power first) | Good (strength first) | Manage: not before heavy legs | Good |
| **Hypertrophy** | Good | — | Good | Good | Manage: recovery cost | Good |
| **Power** | Good | Good | — | Manage: power first | Separate: never after HIIT | Good |
| **Aerobic (easy)** | Good | Good | Manage | — | Good | Good |
| **HIIT** | Manage | Manage | Separate | Good | — | Good |
| **Mobility** | Good | Good | Good | Good | Good | — |

**Emphasis by training phase.** Every phase trains all ten objectives; only the emphasis rotates. Foundation favours technique, hypertrophy-range work and aerobic base. Build adds volume and interval work. Strength blocks shift main lifts to heavier loads while accessories hold size. Conditioning blocks raise interval work while strength runs at maintenance. The 12-month blueprint (section 24–25) sequences these.

**Body composition, honestly.** Training protects and builds muscle; fat loss depends mainly on nutrition, which is outside V1. The app never adds exercise to "burn off" food.

## 7–12. Dosage rules: frequency, volume, intensity, reps, rest and order

The default dose is 2 exposures per muscle per week, 8–16 hard sets per muscle per week depending on experience, most sets stopped 1–3 reps short of failure, and rests of 75–180 s. Every number below lives in the Rule Registry, not in code.

### 7. Frequency rules

| Rule | Statement | Confidence |
| --- | --- | --- |
| FREQ-001 | Train 2–6 days/week; always at least 1 full rest day. Default 3 days if you leave it blank (ASSUMPTION). | High (≥2) / Expert (cap 6) |
| FREQ-002 | Each major muscle gets ≥2 exposures/week. An exposure = ≥2 fractional hard sets in one session. | Moderate |
| FREQ-003 | Resistance training on ≥2 days every week (WHO floor). | High |
| FREQ-004 | In strength blocks, each main pattern (squat, hinge, horizontal push, horizontal pull) is loaded 2–3×/week. | Moderate (Pelland 2024, Currier 2023) |
| FREQ-005 | Aerobic work appears on ≥3 days/week when training ≥3 days (short post-strength blocks count). | Expert Practice |

**Minimum effective dose vs default.** The minimum keeps you progressing slowly or maintaining; the default is what the engine plans on FULL days.

| Objective | Minimum effective dose | Engine default | Hard ceiling |
| --- | --- | --- | --- |
| Strength | 1 hard set of 6–12 reps at 70–85%, 2–3×/week per lift (Androulakis-Korakakis 2020) | 2–3 heavy sets per main pattern, 2–3×/week | VOL caps |
| Hypertrophy | \~4 sets/muscle/week | 8–16 fractional sets/muscle/week | 12 / 16 / 20 (beginner / intermediate / advanced) |
| Aerobic | 150 min moderate or 75 min vigorous (WHO) | 90–180 min, mostly easy, 1–2 harder sessions | Prescribed ≤300 min |
| HIIT | 1 session/week | 1–2 sessions/week | 2 (3 if eligible) |
| Core | 2 sessions/week | 3 sessions/week | — |
| Mobility | Warm-up every session | + optional 2 off-day routines | — |

### 8. Volume rules

| Rule | Statement | Confidence |
| --- | --- | --- |
| VOL-001 | A **hard set** is a working set ending at RIR ≤4 (RPE ≥6). Warm-up sets and sets at RIR ≥5 count zero. | Moderate |
| VOL-002 | **Fractional counting:** 1.0 set to each primary muscle, 0.5 to each secondary muscle, 0 to stabilisers. | Moderate (Pelland 2024) |
| VOL-003 | **Volume landmarks** per muscle per week (table below). The engine starts each block at "start" and never plans above "cap". | Expert Practice |
| VOL-004 | **Fatigue-driven reduction:** if ≥2 fatigue signals (DEL-001) persist across ≥2 sessions, accessory sets drop 30% until they clear. | Expert Practice |
| VOL-005 | **Per-session cap:** ≤6 / 8 / 10 direct hard sets per muscle per session (beginner / intermediate / advanced). | Limited / Expert |
| VOL-006 | **Volume progression:** each week of a block, a muscle may gain +1 set (beginner) or +1–2 sets (others) only if its exercises held or improved and no fatigue signal is active. | Expert Practice |
| VOL-007 | **Systemic stress units (SSU)** estimate whole-session stress for planning (equation below). | Expert Practice |
| VOL-008 | **Session cap:** ≤20 / 25 / 30 working sets per session, including core. | Expert Practice |

| Fractional sets / muscle / week | Beginner | Intermediate | Advanced |
| --- | --- | --- | --- |
| Maintenance (MV) | 4 | 4 | 6 |
| Block start (≈MEV) | 6 | 8 | 10 |
| Usual working range (≈MAV) | 8–12 | 10–16 | 12–20 |
| Engine cap (replaces MRV) | 12 | 16 | 20 |

The landmark names are coaching vocabulary (Israetel et al.), not measured physiology. Numbers are anchored to dose–response data: growth keeps rising past 10 sets/week but with diminishing returns, and strength gains flatten sooner (Pelland 2024, Schoenfeld 2017, ACSM 2026). The cap is kept below what lifters tolerate in studies because you also do conditioning.

**Total systemic stress equation (VOL-007).** One planning number for strength, bodyweight, circuits, conditioning and HIIT:

```latex
\mathrm{SSU}_{\text{session}} = \sum_{\text{sets}} C_{\text{ex}} \cdot E_{\text{RIR}} \; + \sum_{\text{conditioning}} t_{\min} \cdot Z_{\text{zone}} \cdot J_{\text{modality}}
```

| Factor | Values |
| --- | --- |
| C (exercise cost) | Isolation or core 0.5 · machine/cable compound 0.8 · free-weight compound, carry or sled 1.0 · heavy bilateral squat/hinge at ≥80% 1RM 1.5 |
| E (effort) | RIR 0 → 1.2 · RIR 1–2 → 1.0 · RIR 3–4 → 0.8 |
| Z (per minute) | Easy Z1 0.4 · moderate-hard Z2 0.8 · interval work Z3 1.5 · interval rest 0.2 |
| J (modality) | Rower, SkiErg, elliptical 0.9 · ropes, kettlebell, sled, carries 1.0 · jump rope, jumping circuits 1.1 |

Planning limits: weekly SSU ≤ 100 / 140 / 180 by level, and (after 4 weeks) ≤ 1.15 × your 3-week average. After 4 weeks the engine also learns your personal ratio k = median(sRPE load ÷ SSU) so planned SSU can be checked against real session-RPE load (LOAD-005).

*Worked example.* A session with 6 free-weight compound sets at RIR 2 (6.0), 2 heavy squat sets at RIR 2 (3.0), 4 cable sets at RIR 2 (3.2), 6 isolation sets at RIR 1 (3.0), 3 core sets at RIR 3 (1.2), then 12 min Z2 rowing (12 × 0.8 × 0.9 = 8.64) totals **25.0 SSU**. Four such sessions = 100 SSU, inside the intermediate weekly ceiling of 140 (a beginner session would hold fewer sets, per VOL-008).

### 9. Intensity rules: %1RM, RPE, RIR, failure, e1RM

| RPE | 10 | 9.5 | 9 | 8.5 | 8 | 7 | 6 | ≤5 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| RIR | 0 | 0 (could add load) | 1 | 1–2 | 2 | 3 | 4 | ≥5 — not a hard set |

| Rule | Statement | Confidence |
| --- | --- | --- |
| INT-001 | RIR = 10 − RPE is used only from RPE 6 to 10 on working sets; below that the scale is too imprecise (Zourdos 2016, Halperin 2022). | Moderate |
| INT-002 | **RIR targets.** Beginner (first 8 weeks): compounds RIR 3, isolation RIR 2. Intermediate: compounds 1–3, isolation 0–2. Advanced: compounds 1–2, isolation 0–1. Week 1 of each block is +1 RIR easier; the last loading week −1 (not below the floor). | Moderate / Expert |
| INT-003 | **Failure policy.** RIR 0 is planned only on the last set of machine, cable, isolation-dumbbell or safe bodyweight exercises, ≤2 exercises per session, on FULL days, never in a deload, never for beginners in their first 8 weeks, never on exercises tagged failure-unsafe (barbell squat, bench, overhead press, deadlift, carries, Olympic lifts, anything you could drop or get pinned under). | Moderate (failure adds little: Robinson 2024, Grgic 2022, ACSM 2026) |
| INT-004 | **e1RM** (Epley, RIR-adjusted): e1RM = load × (1 + (reps + RIR) / 30). Updated only from sets with reps + RIR ≤ 12 and RIR ≤ 3 (most accurate near failure, LeSuer 1997). Session value = best valid set. Smoothed: E ← E + 0.4 × (session − E); a single jump is capped at +5%. Bodyweight exercises track reps, not e1RM. | Moderate |
| INT-005 | **Load selection:** load = e1RM ÷ (1 + (target reps + target RIR) / 30), then rounded per PROG-003. With no e1RM yet, the calibration protocol (CAL-001) sets the first load. | Moderate |
| INT-006 | **Beginners:** first 4 weeks show rep targets plus "how many more could you have done? 0 / 1 / 2 / 3 / 4+". RPE language is introduced gradually; targets stay conservative because novices misjudge RIR most. | Moderate / Expert |
| INT-007 | **In-session autoregulation:** after the first working set, RPE < target − 1 with reps hit → next set +1 increment (≤5%); RPE > target + 1 or reps below range → next set −5%. Net change ≤ ±10% per exercise per session. | Expert Practice |

*Worked example (INT-004/005, PROG-003).* Last session: bench press 60 kg × 8 at RPE 8 (RIR 2). e1RM = 60 × (1 + 10/30) = **80.0 kg**. Today's target is 5 reps at RIR 2: 80 ÷ (1 + 7/30) = **64.9 kg**. Your bar plus plates allow 62.5 or 65 kg; 65 kg is only 0.2% above target (within the 2% tolerance), so the engine prescribes **65 kg × 5 @ RIR 2**.

**Autoregulation method.** Rep ranges with RIR targets, adjusted set to set (INT-007) and session to session (PROG rules). Evidence that autoregulation beats fixed percentages is short-term and athlete-only (Zhang 2021), so the engine uses it mainly to keep effort on target, not as a performance promise.

### 10. Rep-range rules

| Rule | Use | Reps / dose | Load guide |
| --- | --- | --- | --- |
| REP-001 | Strength (main lifts in strength blocks) | 3–6 (1–3 only for advanced, never above RPE 8 outside planned check-ins) | \~80–88% 1RM |
| REP-002 | Hypertrophy | Compounds 6–12; isolation 8–20 | \~60–80% 1RM, near target RIR |
| REP-003 | Muscular endurance | 15–30 or 30–60 s | <60% 1RM |
| REP-004 | Power | 3–5 per set, reps × sets < 24, stop when speed visibly drops | 30–70% 1RM, maximal intent |
| REP-005 | Core | Holds 10–45 s or 6–12 controlled reps per side | Progress lever before load |
| REP-006 | Conditioning | Measured in minutes, metres, watts, calories or reps | By zone (AER-001) |

No range is treated as universally optimal: size grows across wide ranges when sets are hard (Schoenfeld 2021), and light, very-high-rep sets are kept short because they are uncomfortable and RIR is hardest to judge there.

### 11. Rest rules

| Rule | Context | Range | Default |
| --- | --- | --- | --- |
| REST-001 | Heavy compounds (≤6 reps or ≥80%) | 120–300 s | 180 s |
| REST-002 | Moderate compounds (6–12 reps) | 90–150 s | 120 s |
| REST-003 | Isolation / accessories | 60–90 s | 75 s |
| REST-004 | Non-competing supersets | 45–75 s between the paired exercises | 60 s |
| REST-005 | Circuits | 15–45 s transitions | 30 s |
| REST-006 | Core | 30–60 s | 45 s |
| REST-007 | Autoregulated rest | You may start the next set any time inside the range; the timer alerts at the default. Under a time budget, rests shrink toward the minimum — accessories first, heavy compounds last and never below their minimum. | Expert Practice |

Basis: rests over 60 s give a small hypertrophy benefit with no clear gain past \~90 s (Singer 2024); heavy strength work benefits from ≥2 min (Grgic 2018). Conditioning rest is set by the protocol's work:rest ratio (HIIT-002).

### 12. Exercise-order rules

**ORD-001 — session sequence** (seed validated, Moderate):

1. Warm-up (RAMP), including ramp-up sets
2. Power or high-skill work, if scheduled
3. Primary compound strength work
4. Secondary compound work
5. Accessories (isolation, unilateral, carries)
6. Core
7. Conditioning / HIIT
8. Cool-down and mobility

Exercises done first gain the most strength (Nunes 2021); hypertrophy is largely order-insensitive, so the engine puts your current priority first.

**ORD-002 — exceptions.** On a conditioning-priority day, conditioning moves to step 3 and lower-body strength volume drops 30% that day. A user-flagged weak point may move ahead of secondary compounds. Mobility for a restricted joint moves into the warm-up.

**ORD-003 — superset eligibility.** Only non-competing pairs (push + pull, upper + lower, compound + core) at the same station or within a few steps. Never for heavy main lifts at RIR ≤2. Never across two stations in a crowded gym (EQ-002).

```text
PSEUDOCODE allocate_weekly_volume(profile, block, week, history)
  for muscle in MUSCLES:
      level   = profile.experience                      # EXP-001
      target  = VOL-003.start[level] if week == 1 else last_week[muscle]
      if week > 1 and progressed(muscle, history) and not fatigue_active():   # VOL-006
          target += VOL-006.step[level]
      target = min(target, VOL-003.cap[level])
      if fatigue_signals(history) >= 2 for >= 2 sessions:                     # VOL-004
          target = target * (1 - 0.30)   (accessory sets only)
      sessions = exposures(muscle, schedule)               # FREQ-002, >= 2
      per_session = split_evenly(target, sessions)
      assert max(per_session) <= VOL-005.cap[level]       # else add exposure or trim
  return plan            # each change logged with rule IDs
```

## 13–16. Cardio, HIIT and conditioning

Cardio and strength coexist: aerobic work barely dents muscle size or maximal strength, but it does blunt power, especially in the same session (Schumann 2022). So the engine puts strength first, keeps hard intervals away from heavy leg and power days, prefers low-impact machines (rower, SkiErg, elliptical, sled), and caps HIIT at 2 sessions a week by default.

### 13. Concurrent-training rules

| Rule | Statement | Confidence |
| --- | --- | --- |
| CON-001 | In a shared session, resistance training comes first and conditioning after, except on conditioning-priority days (ORD-002). | Moderate (Murlasits 2018) |
| CON-002 | Two hard sessions on one day (strength plus HIIT or ≥30 min of Z2+) are separated by ≥6 h; otherwise they merge into one session, strength first. | Limited–Moderate (Robineau 2016) |
| CON-003 | No HIIT in the \~24 h before a heavy lower-body or power session; on the same day, HIIT goes after strength. | Expert Practice (Sabag 2018: HIIT can blunt lower-body strength) |
| CON-004 | Conditioning paired with strength uses low-impact, mostly concentric modalities first (rower, SkiErg, elliptical, sled push). Impact work (jump rope, jumping circuits) ≤1×/week and never the day before heavy legs. | Moderate (Wilson 2012: impact-heavy modes interfere more) |
| CON-005 | Power work happens at the start of a session and never after HIIT the same day. | Moderate (Schumann 2022) |
| CON-006 | In strength-emphasis blocks, aerobic work stays mostly easy (Z1) and ≤150 prescribed min/week. | Limited (interference rises with endurance frequency and duration, Wilson 2012) |

The molecular "AMPK vs mTOR switch" is treated as background only; the rules rest on outcome studies.

### 14. Aerobic intensity rules

No heart-rate monitor is assumed. Intensity is set with the talk test, which tracks the ventilatory threshold well (Foster 2018), and the Borg CR10 effort scale (Borg 1982).

| Zone (AER-001) | Talk test | CR10 effort | Optional %HR reserve | Counts toward WHO as |
| --- | --- | --- | --- | --- |
| Z1 Easy | Full sentences, comfortably | 3–4 | 40–59% | Moderate |
| Z2 Tempo / threshold | Can talk, but not comfortably ("yes, but…") | 5–6 | 60–79% | Vigorous |
| Z3 Hard intervals | A few words only | 7–9 | ≥80% | Vigorous |
| Z4 Sprint | Cannot talk | 10 | — | Vigorous |

| Rule | Statement | Confidence |
| --- | --- | --- |
| AER-002 | About 75–80% of weekly aerobic minutes are Z1; the rest is Z2/Z3, including HIIT. | Limited (athlete data, transferred) |
| AER-003 | Progress duration first (+2–5 min per session, ≤+15% weekly minutes) up to \~30–40 min continuous Z1; then add a Z2 tempo block; then intervals. | Expert Practice |
| PH-001 | **WHO floor accounting:** equivalent minutes = Z1 + 2 × (Z2 + Z3 work), plus optional logged walking. Target ≥150/week. If your schedule cannot reach it in the gym, the weekly plan says so and suggests walking. | High (WHO 2020) |

### 15. HIIT rules

| Rule | Statement | Confidence |
| --- | --- | --- |
| HIIT-001 | **Ceiling:** ≤2 HIIT sessions/week. A 3rd is allowed only for intermediate/advanced users, on a FULL readiness day, with no active fatigue signal or workload flag, in a conditioning-emphasis block. Any segment with ≥6 min of Z3 work, or a circuit held at CR10 ≥7 for ≥8 min, counts as HIIT. | Expert Practice |
| HIIT-002 | **Protocol menu** (table below); the protocol is chosen by block and purpose, never by popularity. | Moderate (Buchheit & Laursen 2013; Wen 2019) |
| HIIT-003 | **Prerequisites:** screening clear (or cleared by a doctor), calibration done, ≥3 weeks of easy aerobic base. First HIIT uses short 1:2 intervals at CR10 7–8. | Expert Practice |
| HIIT-004 | **Spacing:** ≥48 h between HIIT sessions preferred, 24 h minimum, plus CON-003. | Expert Practice |
| HIIT-005 | **Per-session work caps:** long intervals ≤20 min of work, short ≤10 min, sprints ≤2.5 min; ≤35 min of conditioning after a strength session. | Expert Practice |

| Protocol | Work | Recovery | Work:rest | Effort | Best modalities | Used in |
| --- | --- | --- | --- | --- | --- | --- |
| Long intervals | 3–5 × 3–4 min | 2–3 min easy | \~1:0.75–1:1 | CR10 7–8 | Rower, SkiErg, elliptical | Build, Conditioning |
| Medium intervals | 6–10 × 1–2 min | 1–2 min | 1:1 | CR10 8 | Rower, SkiErg, sled | Build, Conditioning |
| Short intervals | 10–20 × 15–30 s | 15–60 s | 1:1 or 1:2 (beginners) | CR10 8–9 | Rower, SkiErg, ropes, slams | Foundation (1:2), Conditioning |
| Sprint intervals | 4–8 × 10–20 s | 90–180 s | ≥1:6 | CR10 10 | Rower, SkiErg, sled | Advanced only, ≤1/week |
| Tempo (not HIIT) | 2–3 × 8–12 min | 2–3 min | \~4:1 | CR10 5–6 | Any steady modality | All blocks |

Longer intervals with more total work over 4–12 weeks produce the largest VO2max gains in adults (Wen 2019), so long intervals are the default HIIT format.

### 16. Conditioning modality matrix

**MOD-001 — excluded:** treadmill running, all stationary bikes, stair machines. Air/fan bikes and treadmill walking are also excluded until you say otherwise (ASSUMPTION).

**MOD-002 — modality ranking** (1 = low, 5 = high; Expert Practice; "interference" = expected cost to same-day or next-day leg strength):

| Modality | Cardio stimulus | Local fatigue | Joint impact | Skill | Recovery cost | Interference | Steady-state | Intervals | Measurable |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Rowing ergometer | 5 | 3 | 1 | 3 | 2 | 2 | 5 | 5 | 5 (m, W, split) |
| SkiErg | 4 | 3 | 1 | 2 | 2 | 1 | 4 | 5 | 5 (m, W) |
| Elliptical | 4 | 2 | 1 | 1 | 1 | 1 | 5 | 3 | 3 (time, level) |
| Sled push / pull | 4 | 4 | 1 | 1 | 2 | 3 | 2 | 5 | 3 (kg, m) |
| Battle ropes | 3 | 4 | 1 | 2 | 2 | 1 | 1 | 5 | 2 (time) |
| Kettlebell conditioning | 4 | 3 | 2 | 3 | 3 | 3 | 2 | 5 | 3 (reps) |
| Loaded carries | 3 | 3 | 1 | 1 | 2 | 2 | 2 | 4 | 4 (kg × m) |
| Med-ball slams / throws | 3 | 2 | 2 | 2 | 2 | 1 | 1 | 4 | 2 (reps) |
| Bodyweight circuits | 3 | 3 | 2 | 1 | 2 | 2 | 2 | 4 | 3 (reps, time) |
| Jump rope | 4 | 2 | 3 | 3 | 2 | 2 | 3 | 4 | 2 (time) |

```text
PSEUDOCODE choose_conditioning(day, profile, plan)
  candidates = MODALITIES - MOD-001.excluded - unavailable_today - blocked_by_limitations
  for m in candidates:
      score = 0.35 * fit(m.stimulus, day.zone)              # steady vs intervals
            + 0.25 * (1 - m.interference/5) * leg_priority(tomorrow, today)
            + 0.20 * (1 - m.impact/5) * joint_sensitivity(profile)
            + 0.10 * variety(m, last_14_days)
            + 0.10 * preference(m, profile)
  return top(candidates), with one-line reason       # logged: MOD-002, CON-004
```

*Worked example.* Tomorrow is a heavy squat day and you report mild knee sensitivity. Jump rope and sled push lose points for impact and leg interference; the rower and SkiErg score highest, and SkiErg wins on lower leg interference. The coach says: "SkiErg today — it spares your legs for tomorrow's squats."

## 17–21. Movement patterns, bodyweight, core, mobility and warm-up

Every week covers all seven lifting patterns plus carries and three core categories, with pulling at least matching pushing. Core work is built on bracing against movement rather than crunches, mobility comes mainly from full-range lifting plus short dynamic prep, and warm-ups run 8–12 minutes and shrink to 5 under time pressure but never disappear.

### 17. Movement-pattern framework and balance rules

| Pattern | Barbell | Dumbbell / kettlebell | Cable / machine | Bodyweight |
| --- | --- | --- | --- | --- |
| 1 Squat | Back / front squat | Goblet squat | Leg press, hack squat | Box squat |
| 2 Hinge | Romanian deadlift, deadlift | DB RDL, KB swing | Hip thrust machine, back extension | Hip bridge |
| 3 Lunge (unilateral) | Barbell split squat | DB split squat, reverse lunge | Smith split squat | Split squat, step-up |
| 4 Horizontal push | Bench press | DB bench | Chest press, cable press | Push-up |
| 5 Horizontal pull | Barbell row | One-arm DB row | Seated cable row | Inverted row |
| 6 Vertical push | Overhead press | DB shoulder press | Machine shoulder press | Pike push-up |
| 7 Vertical pull | — | — | Lat pulldown, assisted pull-up | Pull-up |
| 8 Loaded carry | — | Farmer / suitcase carry | — | — |
| 9 Rotation | Landmine rotation | Med-ball rotational throw | Cable chop / lift | — |
| 10 Anti-extension | Rollout (barbell) | — | — | Dead bug, plank |
| 11 Anti-rotation | — | Renegade row | Pallof press | — |
| 12 Anti-lateral flexion | — | Suitcase carry | — | Side plank |

| Rule | Statement | Confidence |
| --- | --- | --- |
| PAT-001 | **Weekly coverage (≥3 days):** patterns 1–7 each ≥1×; squat, hinge, horizontal push and horizontal pull ≥2× (variations count); carry ≥1×; patterns 10–12 each ≥1×; rotation ≥1× per 2 weeks. With 2 days: 1–7 and 10–12 weekly; carry and rotation alternate weeks. | Expert Practice |
| PAT-002 | Weekly pulling sets ÷ pushing sets between 1.0 and 1.5. | Expert Practice |
| PAT-003 | Knee-dominant sets (squat + lunge) ÷ hip-dominant sets (hinge) between 0.67 and 1.5. | Expert Practice |
| PAT-004 | ≥1 unilateral lower-body exercise per week (≥2 when training ≥4 days). | Expert Practice |

### 18. Bodyweight and calisthenics framework

Bodyweight exercises serve four jobs: warm-up activation, accessories, main strength drivers when equipment is missing, and substitutions.

| Rule | Statement |
| --- | --- |
| BW-001 | **Progression levers, in order:** reps → range of motion (deficit, full depth) → tempo or pauses (3-s lowering) → leverage (feet elevated, longer lever) → unilateral or less assistance → added load (vest, dumbbell). Regressions run the same list backwards. |
| BW-002 | **Move up a step** when every set reaches the top of the range (push-up family 15, pull-up family 10–12, squat family 20) at RIR ≥1 for 2 consecutive sessions. Assisted pull-ups lose one assistance step after 3 × 8 at RIR 2. |

| Family | Ladder (easiest → hardest) |
| --- | --- |
| Push-up | Incline (bench) → floor → feet-elevated → deficit → weighted |
| Pull-up | Machine/band-assisted → slow negatives (3–5 s) → full pull-up → weighted |
| Dip | Assisted dip machine → bar dip → weighted (skipped for shoulder-limited users) |
| Squat | Box squat → bodyweight squat → tempo squat → split squat → rear-foot-elevated split squat |
| Crawl | Bear hold → bear crawl → lateral crawl |

Confidence: Expert Practice (progression order), Moderate for the principle that harder variations near failure drive adaptation.

### 19. Core framework

| Rule | Statement | Confidence |
| --- | --- | --- |
| CORE-001 | Core work emphasises resisting movement (anti-extension, anti-rotation, anti-lateral flexion) and bracing. Rotation is trained with controlled chops and med-ball throws. High-rep crunches and sit-ups are not prescribed by default; you may add them as a preference. | Expert Practice (Kibler 2006) |
| CORE-002 | Progress by lever before load: when 3 sets of 30–45 s (or 10–12 reps per side) feel solid at about RIR 2 for 2 sessions, move one rung up the ladder. | Expert Practice |
| CORE-003 | 2–4 sessions/week, 6–12 total sets/week across categories, ≤4 core sets per session, placed after accessories or as a superset partner. | Expert Practice |

| Category | Ladder |
| --- | --- |
| Anti-extension | Dead bug → front plank → long-lever plank → body saw → kneeling rollout → standing rollout (advanced) |
| Anti-rotation | Half-kneeling Pallof press → standing Pallof → Pallof walkout → single-arm cable row → renegade row |
| Anti-lateral flexion | Kneeling side plank → side plank → side plank with reach → suitcase carry → offset overhead carry |
| Rotation | Half-kneeling cable lift/chop → standing chop → med-ball rotational throw |
| Bracing | Breathing + brace drill → front-loaded (goblet) carry |

### 20. Mobility framework

| Rule | Phase | Protocol | Confidence |
| --- | --- | --- | --- |
| MOB-001 | Pre-workout | 3–5 min of dynamic drills for the joints the session loads (hips and ankles before squats; upper back and shoulders before pressing), inside the warm-up | Moderate |
| MOB-002 | Static stretching | Not part of the default warm-up. If you prefer it, ≤30 s per muscle followed by dynamic work, because short holds barely affect strength (Behm 2016). After training: 30–60 s per chosen muscle in the cool-down, plus 1–2 min of slow breathing to wind down | Moderate (performance) / Expert (wind-down) |
| MOB-003 | Intra-workout | Optional mobility drill for the *next* exercise during rest periods, only if it does not tire the working muscle | Expert Practice |
| MOB-004 | Every session | Lift through full range by default: strength training improves range of motion about as well as stretching (Afonso 2021) | Moderate |
| MOB-005 | Off-day | Optional 10–20 min routine, 2–3×/week: hips, upper back, shoulders, ankles; 2 × 30–60 s per position or slow controlled joint circles | Expert Practice |

### 21. Warm-up framework (RAMP)

| Rule | Statement | Confidence |
| --- | --- | --- |
| WU-001 | **RAMP structure** (Jeffreys): **Raise** 3–5 min easy rower, SkiErg or elliptical (CR10 2–3) → **Activate & Mobilise** 3–4 min, 2–4 drills matched to the session → **Potentiate** with ramp-up sets (plus 2–3 throws or jumps on power days). Total 8–12 min. HIIT days: 5 min easy, then 3 × 30 s builds at CR10 5 → 7 → 8. | Moderate (warm-ups improve performance, Fradkin 2010) / Expert (structure) |
| WU-002 | **Ramp-up sets for the first main lift:** empty bar (or lightest dumbbell) × 8–10 → 50% × 5 → 70% × 3 → 85% × 1–2. The 85% step is used only if working reps ≤6 or load ≥80% e1RM. If the working load is ≤ bar + 10 kg, one set at \~50% × 8 is enough. Second main lift: 60% × 5 → 80% × 2–3. Other exercises: 0–1 lighter "feeler" set. | Expert Practice |
| WU-003 | **Compression:** under a tight time budget the warm-up shrinks to a 5-min floor (2 min raise, 1–2 drills, 2–3 ramp sets). It is never removed. | Expert Practice |
| WU-004 | **Age:** ≥50 adds 2–3 min to Raise; ≥65 adds one balance drill (AGE-001). | Expert Practice |

*Worked example (WU-002).* Working sets: back squat 100 kg × 5. Ramp: 20 kg × 10 → 50 kg × 5 → 70 kg × 3 → 85 kg × 1. Four sets, about 6 minutes including the change-overs.

## 22, 23, 28. Readiness, deloading and workload

A four-tap check produces a 0–100 readiness score that, blended with your own baseline, picks one of four session tiers; pain and red-flag symptoms bypass the score entirely. Deloads are triggered by six fatigue signals rather than a calendar, and workload is tracked with session-RPE, with the popular acute:chronic ratio kept only as a soft warning.

### 22. Recovery and readiness model

**Inputs (each 1–5, where 3 = "normal for me"):** sleep quality S, energy E, muscle soreness D (5 = none), stress M (5 = calm). Optional: hours slept. Separate gate: "Any pain today?" yes/no → location and 0–10 rating.

```latex
R_{\text{raw}} = 25\,[\,0.30(S-1) + 0.30(E-1) + 0.25(D-1) + 0.15(M-1)\,]
```

```latex
z = \frac{R_{\text{raw}} - \mu_{28}}{\max(\sigma_{28},\,8)}, \qquad R_{\text{pers}} = 50 + 15\,\mathrm{clamp}(z,-2,2), \qquad R = 0.6\,R_{\text{raw}} + 0.4\,R_{\text{pers}}
```

| Rule | Statement | Confidence |
| --- | --- | --- |
| RDY-001 | Score R\_raw from four weighted items; all-normal (all 3s) = 50. | Expert Practice (self-report tracks load well: Saw 2016) |
| RDY-002 | After ≥14 check-ins in the last 28 days, blend in your personal baseline (formula above), so habitually low or high raters are judged against themselves. Floor: if R\_raw < 15 the tier is at most LIGHT, whatever the baseline says. | Expert Practice |
| RDY-003 | **Tiers:** R ≥ 40 FULL · 28–39 MODIFIED · 15–27 LIGHT · < 15 RECOVERY. Sleep or energy rated 1, or soreness rated 1, caps the day at MODIFIED. | Expert Practice |
| RDY-004 | **Tier changes** (table below). | Expert Practice |
| RDY-005 | **Sleep hours:** < 4 h caps the day at LIGHT; 4 to < 5 h caps it at MODIFIED. | Moderate effect (Craven 2022: −7.6% performance, worse later in the day) / Expert caps |
| RDY-006 | ≥2 active fatigue signals (DEL-001) lower the tier by one step, but never below LIGHT on signals alone. | Expert Practice |
| RDY-007 | You may always choose an easier tier. A harder tier is allowed one step up, with a warning, and never when a safety rule, the pain gate or screening conservative mode set the limit. | Product rule |

| Tier | What changes | Typical length |
| --- | --- | --- |
| FULL | Session as planned | As planned |
| MODIFIED | Sets −30% (lowest-priority accessories go first, ≥1 set per kept exercise); RIR target +1; no RIR-0 sets; main-lift loads ≤95% of plan; HIIT becomes 15–20 min of steady Z1–Z2 | 40–50 min |
| LIGHT | Sets −50%; main lifts ≤85% of plan at RIR ≥3, technique focus; no HIIT; 10–20 min Z1; 5–10 min mobility | 30–45 min |
| RECOVERY | No resistance training; optional 10–30 min Z1 plus mobility, or full rest | 0–30 min |
| SAFETY STOP | Session ends; calm guidance shown (SAF-002, SAF-003) | — |

*Worked example.* Sleep 2, energy 3, soreness 2, stress 3 → R\_raw = 36.25. Your 28-day baseline is 58 (SD 9), so z = −2.42, clamped to −2, giving R\_pers = 20 and R = 0.6 × 36.25 + 0.4 × 20 = **29.75 → MODIFIED**. You slept 4.5 h, which also caps the day at MODIFIED. The coach says: "Shorter session today — about 30% fewer sets, one rep further from failure. You slept under 5 hours and you're sorer than usual."

The model answers one question — *can you productively train hard today?* — and never claims to be a medical assessment.

### 23. Deload model

| Rule | Statement | Confidence |
| --- | --- | --- |
| DEL-001 | **Six fatigue signals:** F1 performance — on ≥2 main lifts, best e1RM (or reps at the same load) ≥3% below the recent best on 2 consecutive exposures, or rep targets missed on ≥2 lifts in 2 consecutive sessions. F2 effort creep — session RPE ≥ plan + 1.5 in 3 of the last 4 sessions. F3 readiness — 7-day mean R more than 1 SD below baseline (or mean R\_raw < 35 before a baseline exists). F4 soreness — soreness ≤2 on 3 of the last 5 check-ins, or the same non-red-flag ache reported twice in 7 days. F5 workload — EWMA ratio > 1.5 or monotony > 2.0. F6 self-report — "I feel run down" tapped twice in 7 days. | Expert Practice (Meeusen 2013 for the signal types) |
| DEL-002 | **Decision**, checked after every session: ≥3 signals → deload from the next session. 2 signals for ≥2 sessions → a lighter week (next 3 sessions at MODIFIED), then re-check; still ≥2 → deload. At the end of a block: ≥1 signal → deload week; none → "pivot" week (next block starts easier by design). If 10 weeks pass with neither, the coach *offers* a lighter week. | Expert Practice (Bell 2023: coaches deload every 4–6 weeks) |
| DEL-003 | **Prescription:** 5–7 days; same training days; sets −50%; loads 85–90% of recent working loads at RIR ≥3; no HIIT; conditioning Z1 only; extra mobility. Never a week of total rest by default. | Limited (Coleman 2024: a full week off gave no benefit and slightly less strength; Bell 2023: cut volume, intensity may stay) |
| DEL-004 | **Resumption:** the next block starts at block-start volumes (VOL-003), loads at 95–100% of pre-deload working loads, RIR +1 in week 1. | Expert Practice |

### 28. Fatigue and workload engine

| Rule | Statement | Confidence |
| --- | --- | --- |
| LOAD-001 | **Session load (AU) = session RPE (CR10, 0–10) × session minutes** (warm-up to cool-down, pauses >10 min removed). | Moderate (Foster 2001) |
| LOAD-002 | Session RPE is asked on the summary screen after the cool-down (ideally ≥10 min after the last hard effort); if skipped, the app asks once within 24 h. | Moderate |
| LOAD-003 | Weekly load = sum of 7 days. Monotony = mean daily load ÷ SD of daily load (7 days, rest days as 0, sample SD). Strain = weekly load × monotony. Monotony > 2.0 raises a soft flag. | Moderate (Foster 1998) |
| LOAD-004 | **EWMA ratio** (Williams 2017): acute λ = 2/8 = 0.25, chronic λ = 2/29 ≈ 0.069 on daily loads. Shown only after ≥28 days; > 1.5 raises a soft "workload jumped" flag. It is never described as injury risk. | Limited (Lolli 2019, Impellizzeri 2021: ratio flaws, weak causal evidence) |
| LOAD-005 | **Planning cap (the real guard):** predicted weekly load (k × planned SSU, VOL-007) ≤ 1.20 × mean of the last 3 completed weeks. Before 4 weeks of history: ≤ +20% over the previous week. Post-deload and return-to-training weeks follow their own ramps (DEL-004, REG-004). | Expert Practice |
| LOAD-006 | **Cold start:** < 14 days → only per-session caps and calibration; 14–27 days → week-over-week rule; ≥ 28 days → all signals. | Expert Practice |

The app combines workload with performance and how you feel, never acting on one number alone. It does not claim to measure physiological fatigue; it only has proxies.

*Worked example (why the planning cap matters).* Five weeks at 1,600 AU/week (four sessions of 400 AU), then a user-driven week of 3,100 AU. On day 42 the EWMA acute load is 460 and chronic 326: ratio **1.41**, still below the 1.5 flag, because averages lag. The planner itself could never have prescribed that week: LOAD-005 caps it at 1,920 AU. The ratio exists to catch work you add yourself; the cap stops the engine overreaching.

```text
PSEUDOCODE readiness_tier(checkin, history, profile)
  if checkin.red_flag:            return SAFETY_STOP            # SAF-002
  if checkin.pain:                apply pain_protocol()          # SAF-003, separate gate
  r_raw = RDY-001(checkin)
  r = r_raw
  if history.checkins_28d >= 14:  r = RDY-002.blend(r_raw, baseline(history))
  tier = RDY-003.band(r)
  if r_raw < 15:                  tier = min(tier, LIGHT)
  if checkin.item_is_1(sleep|energy|soreness): tier = min(tier, MODIFIED)
  tier = min(tier, RDY-005.cap(checkin.sleep_hours))
  if active_signals(history) >= 2 and tier > LIGHT: tier = step_down(tier)   # RDY-006 (order: RECOVERY < LIGHT < MODIFIED < FULL)
  if profile.screening == CONSERVATIVE: remove HIIT and Z3      # SAF-001
  return tier, reason_text, rule_ids                             # Decision Log
```

## 24–25. Periodisation and the 12-month blueprint

The year is a sequence of eight 5-week blocks after a 2-week calibration, each block rotating the emphasis (foundation, build, strength, conditioning, then again with power), with deload or "pivot" weeks placed by fatigue signals rather than dates. Within each week the main lifts undulate between heavier and lighter days, and every session is autoregulated by readiness and RIR.

### 24. Periodisation model

| Model | What it does | Evidence for general fitness | Verdict |
| --- | --- | --- | --- |
| Linear | Load rises week to week, reps fall | Works well for beginners; stalls in trained lifters | Used for beginners only |
| Daily undulating (DUP) | Heavy and lighter days within a week | Slightly better strength than linear in trained people (Moesgaard 2022) | Used within weeks |
| Weekly undulating (WUP) | Emphasis changes week to week | Similar to DUP, more complex to schedule | Not used |
| Block | Multi-week blocks with one emphasis | Suits multiple goals; strongest support in athletes | Used for the year's structure |
| Autoregulated | Load set by RPE/RIR and readiness | Keeps effort on target; performance advantage unproven outside athletes (Zhang 2021) | Used every session |
| Non-periodised | Same programme throughout | Similar hypertrophy; slightly less strength; ACSM 2026 says complex periodisation isn't required | Rejected (adherence and variety value) |

| Rule | Statement | Confidence |
| --- | --- | --- |
| PER-001 | **Architecture: autoregulated hybrid.** Blocks set the emphasis; within each week, main patterns alternate a heavier and a lighter exposure (DUP); each session is adjusted by readiness and RIR. Beginners progress session to session inside a simple weekly structure; intermediates use full DUP; advanced users get sharper block emphasis and more targeted volume. | Moderate (strength) / Expert (structure) |
| PER-002 | **Blocks** run 4–6 loading weeks (default 5), followed by a deload or pivot week per DEL-002. | Expert Practice (Bell 2023) |
| PER-003 | **Block sequence** as in the blueprint below; blocks shift in time, they are never skipped silently. | Expert Practice |
| PER-004 | **Check-ins** at each block end, all non-maximal: e1RM trends from logged sets (no 1RM test); a 10-min row or SkiErg at CR10 5 (distance and average watts); bodyweight reps at RIR 1; optional bodyweight and measurements. A 2,000 m row test is opt-in for intermediate and advanced users. | Expert Practice |
| PER-005 | **Disruptions:** a week with <50% of sessions pauses the block clock. Travel switches to bodyweight/dumbbell substitution. Marked holidays become deload, pivot or 2-session maintenance weeks. Breaks ≥14 days follow REG-004; if ≥4 weeks, the current block restarts. | Expert Practice |
| PER-006 | **Your priorities shape the year:** your #1 priority's blocks get +1 week and the lowest priority's blocks −1 week (minimum 4); the #1 priority's muscles or qualities start at block-start + 2 sets. | Product rule |

### 25. The 12-month training blueprint

&#91;embedded content: 12-month blueprint · 10 phases, 7 deload or pivot points\]

The table below gives each block's dose; when a week is missed, every later block starts later rather than being cut.

| Block | Weeks (default) | Main lifts | Accessories | Sets / muscle / week | Aerobic | HIIT / week | Power |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 0 Calibrate | 1–2 | Find working loads (CAL-001), RIR ≥3 | 10–15 reps, RIR 3 | Block start −2 | Z1 only, 2–3 × 15–25 min | 0 | None |
| 1 Foundation | 3–7 | 8–12 reps, RIR 2–3 | 10–15, RIR 2 | Start → +3 | Z1 base, build to 30 min | 0 → 1 (short 1:2) | Med-ball throws (technique) |
| 2 Build | 9–13 | 6–10, RIR 1–3 | 8–15, RIR 1–2 | Working range | Z1 + one Z2 tempo | 1 (long) | Light |
| 3 Strength | 15–19 | 3–6, RIR 1–3, DUP | 8–15, maintain size | Lower-middle of range | Z1, ≤150 min | 1 | Introduced |
| 4 Conditioning | 21–25 | 4–6, 2 heavy sets × 2/week | 10–15, near maintenance | MV → block start | Z1 + Z2 | 2 (long + short) | Maintained |
| 5 Build II | 27–31 | 6–10, RIR 1–2 | 8–20, RIR 0–2 | Working range, upper half | Z1 + Z2 | 1 | Light |
| 6 Strength II | 33–37 | 3–6, RIR 1–2 (advanced: optional 2–3 rep sets at RPE ≤8) | 8–12 | Lower-middle | Z1, ≤150 min | 1 | Yes |
| 7 Power & athleticism | 39–43 | 3–5 power + 4–6 strength | Unilateral, carries, sleds | Block start → middle | Z1 + short intervals | 1–2 | Emphasis |
| 8 Consolidation | 45–49 | Mixed DUP | Your preferences | Working range | Balanced | 1–2 | Maintained |
| Review & plan year 2 | 50–52 | Retest (non-maximal), deload, set new priorities | — | — | — | — | — |

Weeks 8, 14, 20, 26, 32, 38, 44 are the default positions of deload or pivot weeks; DEL-002 can move them earlier or turn them into normal weeks. Weeks 50–52 hold the review plus two flex weeks; when holidays or illness use a flex week earlier, every later block shifts by that week. The coach shows only *this* block and *this* week; the year view is one tap away.

## 26, 27, 31. Progression, setbacks and first-week calibration

You progress by adding reps first and load second, in the smallest steps your gym actually offers, and only when the target was hit at the intended effort with solid form. Missed sessions shift the plan instead of cramming it; longer breaks re-enter at reduced load and volume; and the first two weeks find your working weights without any maximal testing.

### 26. Progressive overload algorithm

| Rule | Statement | Confidence |
| --- | --- | --- |
| PROG-001 | **Load progression:** if every working set reached the top of its rep range, average RPE ≤ target + 0.5, form was "solid", and recovery is adequate (tier FULL or MODIFIED, <2 fatigue signals), the next exposure adds the smallest available load step that is 2.5–5%. | Moderate / Expert |
| PROG-002 | **Double progression:** keep the load and add reps (≥1 rep on ≥1 set per exposure) until all sets reach the top of the range; then add load and restart at the bottom. When the next load step is >5% (light dumbbells), first extend the range by up to 3 reps (max 15 for compounds, 20 for isolation); on the jump, the new rep target = max(range bottom, ⌊reps × old load ÷ new load⌋). | Expert Practice |
| PROG-003 | **Rounding:** loads come from your inventory (bar + plate pairs, dumbbell list, machine stack steps, kettlebells). Pick the nearest load ≤ 2% above target; otherwise the highest load below it. | Product rule |
| PROG-004 | **Hold or reduce:** RPE between target + 1 and target + 1.5 → hold. RPE > target + 1.5, or reps below range on ≥2 sets → −5% next exposure. Two reductions in a row → −10% from the original and REG-001 review. | Expert Practice |
| PROG-005 | **Bodyweight progression** follows BW-001 and BW-002. | Expert Practice |
| PROG-006 | **Conditioning progression:** steady work adds 2–5 min per session (≤ +15% weekly minutes) up to the target duration, then more distance or watts at the same CR10. Intervals add 1 repeat per session up to the protocol maximum, then longer work bouts, then shorter rests within the protocol's ratio, then +1–2% target pace. Sleds and carries add distance, then 5–10% load. | Expert Practice |
| PROG-007 | **Caps:** prescribed e1RM-based intensity rises ≤5% per week per lift (≤7.5% for beginners); +2 sets/muscle/week max; aerobic minutes ≤ +15%/week; HIIT work ≤ +2 min/week. | Expert Practice |
| PROG-008 | **Technique check** after the last set of each main lift and any new exercise: "Form felt solid?" yes / unsure / no. Unsure → hold; no → hold plus a coaching cue; two "no" in a row → offer an easier variation. | Expert Practice |

```text
PSEUDOCODE next_prescription(exercise, last_exposure, state)
  r_lo, r_hi = exercise.rep_range ; tgt = exercise.target_rpe
  sets = last_exposure.working_sets ; rpe = mean(s.rpe for s in sets)
  if technique_flag(last_exposure) in {UNSURE, NO}:         return HOLD           # PROG-008
  if rpe > tgt + 1.5 or count(s.reps < r_lo) >= 2:
      return REDUCE(5%) (10% and REG-001 review if 2nd in a row)                  # PROG-004
  if rpe > tgt + 1:                                         return HOLD
  if all(s.reps >= r_hi) and rpe <= tgt + 0.5 and recovery_ok(state):          # PROG-001
      step = smallest_available_step(exercise.load)                            # PROG-003
      if step / load <= 5%:  return LOAD(load + step), reps = r_lo
      if r_hi < exercise.max_extended_range: return REPS(range = r_lo..r_hi+1) # PROG-002
      return LOAD(load + step), reps = max(r_lo, floor(r_hi * load / (load+step)))
  return REPS(aim +1 rep on >= 1 set)                                          # PROG-002
  # every return writes a Decision Log entry with rule IDs and a one-line reason
```

*Worked examples.* **Barbell:** back squat, range 5–5, target RPE 8; you logged 100 kg × 5, 5, 5 at RPE 7.5 with solid form → step 2.5 kg = 2.5% → next time **102.5 kg × 5**. **Light dumbbells:** one-arm row, range 8–12, already extended to its compound maximum of 8–15; you did 30 kg × 15, 15, 15 at target effort. The next dumbbell is 32.5 kg (+8.3%, above 5%), so the jump happens with a new target of ⌊15 × 30 ÷ 32.5⌋ = **13 reps** at 32.5 kg.

### 27. Regression, missed sessions and return to training

| Rule | Situation | What the engine does | Confidence |
| --- | --- | --- | --- |
| REG-001 | Failed target loads | In-session: INT-007. Across sessions: PROG-004. Three failed exposures on one lift within 3 weeks → swap to a same-pattern variation for the rest of the block, or −10% and rebuild; counts toward fatigue signal F1. | Expert Practice |
| REG-002 | 1–2 missed sessions | The plan shifts: you do the next planned session, never two at once. If a missed session held a pattern that would drop below PAT-001 coverage, its top exercise is merged into the next session within VOL-005 and VOL-008 caps. | Expert Practice |
| REG-003 | 3 consecutive missed sessions, or 7–13 days off | First session back runs at MODIFIED (−30% sets, loads −5%), then normal. | Expert Practice |
| REG-004 | 14+ days off | 14–27 days: week 1 at 90% load and 70% sets, week 2 at 95% and 85%, then normal; HIIT returns after 1 week. 28–55 days: week 1 at 80% load and 60% sets, then +5% load and +15% sets weekly; HIIT returns after 2 weeks. 56+ days: re-run calibration (CAL-001) and restart the block. Age ≥60 or a flagged screen uses the next longer band. | Moderate direction (Bosquet 2013: strength holds a few weeks, then declines) / Expert numbers |
| REG-005 | Returning after illness | Fever, chills, body aches, chest symptoms, vomiting or diarrhoea → no training (SAF-007). Return once symptom-free for 24–48 h without fever-reducing medicine: LIGHT, then MODIFIED, then normal; HIIT after 2 normal sessions. Illness ≥7 days also applies REG-004 by days off. Mild head-cold symptoms without fever → you may choose LIGHT or MODIFIED. | Expert Practice |

### 31. Initial load calibration protocol (CAL-001)

The first \~4 sessions find a working load for each exercise using effort ratings only. No maximal lifts, no failure, nothing above RPE 8.

1. **Starting guess:** empty bar (or lighter bar if available), the lightest comfortable dumbbells, the second plate on a machine stack, or the easiest bodyweight rung. If you already know your working weights, you can enter them; the engine starts at 90% of them.
2. **Ramp:** do the target reps, then answer "how many more could you have done?" RIR 5+ → add 20–30%. RIR 4 → add 10–15%. RIR 3 → this is your working load. RIR ≤2 → stop; next session uses this load −5%.
3. **Limits:** at most 5 ramp sets per exercise; stop early if form degrades.
4. **Confirm** at the second exposure with working sets at RIR 3, then normal progression rules apply. e1RM starts from the final calibration set where valid (INT-004).
5. **Conditioning:** one 10-min easy session per modality anchors your Z1 pace or watts by talk test; a Z2 anchor follows in week 2.

*Worked example.* Bench press, target 8 reps. 20 kg × 8 → "5+" → 25 kg × 8 → "5+" → 30 kg × 8 → "4" → +12.5% = 33.75 kg target; 35 kg is more than 2% above, so **32.5 kg** → "3" → working load found: **32.5 kg × 8 @ RIR 3**. Starting e1RM = 32.5 × (1 + 11/30) = **44.4 kg**.

## 29–30. Exercise data, equipment and substitution

Each exercise carries about 35 fields so the engine can reason about pattern, muscles, equipment, joint stress, fatigue and safety. Swaps first filter out anything unavailable, painful or too advanced, then score candidates with movement pattern weighted highest; "same muscle" alone never qualifies. No equipment class is treated as universally superior (ACSM 2026 found no consistent difference between machines and free weights).

### 29. Exercise metadata schema

| Field | Type | Values / notes |
| --- | --- | --- |
| ExerciseID, Version | string, semver | Stable ID, e.g. `EX-HPULL-012`; version bumps on any data change |
| Name, Aliases | text, list | Aliases power search and the coach ("seated row" = "cable row") |
| MovementPattern | enum (12) | Section 17 patterns; one primary, optional secondary |
| PrimaryMuscles, SecondaryMuscles | list of {muscle, credit} | Credit 1.0 primary, 0.5 secondary (VOL-002) |
| EquipmentRequired, EquipmentAlternatives | list of equipment IDs | Must match the inventory schema |
| Station | enum | Single-station / multi-station / floor — used for crowded-gym rules (EQ-002) |
| Laterality | enum | Bilateral / unilateral / alternating |
| DifficultyLevel, SkillRequirement, StabilityRequirement | 1–5 each | Filters by experience |
| JointStressRating | {shoulder, elbow, wrist, spine, hip, knee, ankle} 0–4 | Matched against limitation tags |
| FatigueCostLocal, FatigueCostSystemic | 1–5 | Systemic maps to SSU class C (VOL-007) |
| ImpactLevel | 0–3 | Jumping and running-type impact |
| TrainingObjectives | list | Strength, hypertrophy, endurance, power, conditioning, core, mobility, GPP |
| DefaultRepRange, MaxExtendedReps | ints | PROG-002 range extension limit |
| LoadType, LoadIncrement | enum, kg | Barbell / dumbbell / stack / kettlebell / bodyweight / time / distance |
| TrackE1RM | bool | True for main barbell, dumbbell and machine lifts |
| FailureSafe | bool | False blocks planned RIR 0 (INT-003) |
| PowerCapable | bool | Usable for intent-based power sets |
| TempoDefault, SetupTimeSec | seconds | Feed the time model (TIME-004) |
| ProgressionIDs, RegressionIDs, SubstitutionIDs | lists | Curated ladders; substitution still scores all candidates |
| ContraindicationTags, LimitationTags | lists | E.g. `shoulder_overhead_pain`, `knee_deep_flexion_pain` |
| SafetyNotes | text | E.g. "Use safety pins" |
| SetupInstructions, CoachingCues, CommonMistakes | text | Written originally (D7) |
| MediaRef, MediaLicense | URI, string | Licence recorded per asset |

### 30. Exercise substitution algorithm

| Rule | Statement | Confidence |
| --- | --- | --- |
| SUB-001 | **Hard filters:** equipment available today; no pain or limitation region exceeded; not on your exclusion list; skill ≤ your level + 1; not excluded by MOD-001. | Product rule / safety |
| SUB-002 | **Weighted score** 0–1: pattern 0.30 · objective & stimulus 0.20 · primary-muscle overlap 0.15 · equipment fit 0.10 · joint-stress fit 0.10 · difficulty fit 0.07 · fatigue-cost parity 0.05 · preference 0.03. Pattern scores 1.0 same, 0.5 adjacent (squat↔lunge, hinge↔hip thrust, horizontal↔vertical pull), 0 otherwise; candidates below 0.5 on pattern are never auto-picked. | Expert Practice |
| SUB-003 | **Learning:** each accept/reject moves that exercise's preference score by ±0.05 within 0–1. Preference can reorder close candidates, never override pattern or safety. | Product rule |

```text
PSEUDOCODE substitute(original, context)
  pool = library - original
  pool = filter(pool, available_today(context.equipment), within_limits(context.pain),
                not_excluded(context.user), skill_ok(context.level))          # SUB-001
  for c in pool:  c.score = sum(SUB-002.weight[f] * fit_f(c, original, context))
  ranked = sort_desc(pool, by=score)
  if context.reason == OCCUPIED: also offer "do it later" (reorder session)    # C9
  return top_3(ranked) with one-line reasons                                   # Decision Log
```

*Worked example.* The cable row is occupied. Scores: seated machine row **0.955**, chest-supported dumbbell row 0.940, inverted row 0.875, barbell bent-over row 0.848 (loses on lower-back stress and fatigue), lat pulldown 0.760 (adjacent pattern only). The coach offers: "Seated machine row — same pull, same muscles, no extra back fatigue," plus "come back to the cable row later."

### Equipment matrix (EQ-001, B3.26)

| Equipment | Strengths | Limitations | Best use |
| --- | --- | --- | --- |
| Barbell | Heaviest loading, fine increments, measurable progress | Skill; needs racks/pins; failure can be unsafe | Main strength lifts once technique is reliable |
| Dumbbells | Unilateral, joint-friendly paths, many units | Big jumps at light weights | Accessories; beginner main lifts; crowded gyms |
| Kettlebells | Ballistic hinge, carries, conditioning | Coarse increments | Swings, carries, conditioning, goblet work |
| Cables | Constant tension, quick load changes | Often busy; one user per station | Isolation, Pallof presses, chops |
| Machines | Stable, low skill, safe near failure | Fixed path may not fit you | Beginners, accessories, planned RIR-0 sets |
| Bands | Portable, assistance | Load hard to quantify | Warm-ups, assisted pull-ups, travel |
| Bodyweight | Always available, scalable by leverage | Hard to load legs heavily | Activation, accessories, travel, substitutions |

**EQ-002 — crowded gym.** Supersets only within one station or a few steps; sessions never need more than 2 stations at once; the "Occupied" button offers a swap or a reorder; dumbbell options are preferred for accessories when you flag peak hours.

## 32–34. Building each session and each week

A short session keeps the highest-value work and compresses before it cuts: warm-up and the main lift survive, today's key conditioning comes next, and accessories shrink first. Each workout is assembled by a fixed ten-step pipeline that ends in a safety check, and each week follows a template matched to how many days you train, with heavy leg days at least 48 hours apart.

### 32. Session time-budget algorithm

| Rule | Statement | Confidence |
| --- | --- | --- |
| TIME-001 | **Priority tiers:** P0 warm-up (compressed, never removed) and a ≥2-min cool-down · P1 primary compound for today's objective · P2 today's key conditioning stimulus · P3 secondary compounds · P4 accessories · P5 extra core and mobility. | Expert Practice |
| TIME-002 | **Compression order** (stop when it fits): 1) P4/P5 rests to minimum → 2) pair eligible P3–P5 into non-competing supersets → 3) P5 to 1 set, then drop (its weekly coverage moves to another day) → 4) P4 to 2 sets, then 1, then drop the lowest-priority one → 5) warm-up to its 5-min floor → 6) P3 −1 set each → 7) P2 shortened by ≤40%, same stimulus → 8) P1 to 2 working sets, never below REST-001 minimum. Below \~20 min the app offers a 20-min express session instead. | Expert Practice (Iversen 2021 for time-saving tactics) |
| TIME-003 | **Longer sessions (75–90 min):** add, in order, P4 sets toward weekly targets, Z1 aerobic minutes, core/mobility, then skill or power practice — always within VOL-005, VOL-008 and HIIT caps. No junk volume. | Expert Practice |
| TIME-004 | **Time model:** set time = setup (first set) + reps × tempo (default 3.5 s) + 10 s; plus rests and 60–90 s per station change; plus a 5% buffer. Your real durations train a personal factor (bounded 0.8–1.3). | Product rule |

*Worked example: 60-min plan, "I only have 30 minutes."*

| Item | Priority | 60-min plan | 30-min version |
| --- | --- | --- | --- |
| Warm-up | P0 | 10 min | 5 min (floor) |
| Back squat 4 × 5 | P1 | 14 min | 3 × 5, 150 s rest — 9 min |
| Dumbbell bench 3 × 10 | P3 | 8 min | 2 × 10, superset — 6 min |
| Cable row 3 × 12 | P4 | 5 min | Swapped to chest-supported dumbbell row at the same bench, 2 × 12 in the superset |
| Romanian deadlift 3 × 10 | P3 | 8 min | 2 × 10 — 4 min |
| Lateral raise 3 × 15 | P4 | 4 min | Dropped; 3 sets move to the next upper session |
| Pallof press 2 × 10/side | P5 | 3 min | Dropped; core coverage moves to the next session |
| Rower 30 s hard / 30 s easy | P2 | 6 rounds — 6 min | 4 rounds — 4 min |
| Cool-down | P0 | 2 min | 2 min |
| **Total** |  | **60 min** | **30 min** |

The coach explains: "Kept your squats and intervals; paired bench with rows; moved lateral raises and Pallof presses to Thursday."

### 33. Dynamic workout generation (GEN-001)

1. **Load state:** profile, equipment, plan position (block, week, session), history, Rule Registry version.
2. **Safety and readiness gates:** red flags, pain exclusions (SAF-002–004), screening mode (SAF-001), readiness tier (RDY).
3. **Session slots** from the weekly template (SCH-001), e.g. P1 heavy squat pattern, P3 hinge, P3 horizontal push, P4 pull, P2 intervals.
4. **Exercise per slot:** the block's stable core lift if available (ADH-003); otherwise the best candidate after SUB-001 filters and SUB-002 scoring.
5. **Dose:** sets from weekly volume allocation (VOL); reps and RIR from block and REP/INT rules; loads from e1RM, history and PROG; rests from REST.
6. **Tier changes** applied (RDY-004).
7. **Warm-up** generated from the session's first lifts (WU).
8. **Time budget** fitted (TIME).
9. **Safety validator** (SAF-008): caps, balance, HIIT count, workload cap, contraindications. Failures are corrected and logged; an unvalidated session is never shown.
10. **Output** with Decision Log entries and "Why?" text.

The pipeline is deterministic: the same inputs always give the same workout (ties broken by exercise ID).

### 34. Weekly scheduling algorithm

| Rule | Statement | Confidence |
| --- | --- | --- |
| SCH-001 | **Templates by days available** (table below); DUP alternates heavier and lighter exposures of each main pattern. | Expert Practice (ACSM 2026, Currier 2023 for ≥2×/week) |
| SCH-002 | **Spacing:** heavy lower-body sessions ≥48 h apart (≥72 h preferred for advanced heavy squat + deadlift weeks); no more than 3 consecutive hard days; HIIT per HIIT-004 and CON-003. On consecutive preferred days, hard and easier sessions alternate. | Expert Practice |
| SCH-003 | **Rescheduling:** training on an unplanned day runs the next session in sequence. "Only 2 days this week" re-plans the week by priority: main patterns first, PAT-001 coverage kept, HIIT within its cap. | Product rule |

| Days | Structure | Conditioning placement |
| --- | --- | --- |
| 2 | Full-body A (squat focus) · Full-body B (hinge focus) | 10–15 min after strength both days |
| 3 | Full-body A / B / C, undulating heavy and moderate | After strength on 2–3 days; HIIT ≤1–2 |
| 4 | Upper (heavy) · Lower (heavy) · rest · Upper (moderate) · Lower (moderate) | After upper days, protecting legs |
| 5 | Upper · Lower · Conditioning + core · Upper · Lower | HIIT or tempo on the conditioning day |
| 6 | Upper · Lower · Conditioning · Upper · Lower · Long easy aerobic + mobility | ≥1 full rest day |

### Experience levels, age and individual factors (B3.27–28)

| Rule | Statement | Confidence |
| --- | --- | --- |
| EXP-001 | **Onboarding classification:** Beginner = <12 months of consistent (≥2×/week) lifting, or not comfortable with ≥2 of squat, hinge, press, row. Intermediate = 12–36 months and comfortable with all four. Advanced = >36 months with structured programming and confident effort ratings. Calibration may move you down, never up, in the first 4 weeks. | Expert Practice |
| EXP-002 | **Promotion by performance, not calendar:** beginner → intermediate when ≥2 main lifts stop progressing session to session for 3 weeks despite FULL readiness and ≥80% adherence (or after 9 months). Intermediate → advanced after ≥24 months with <0.5%/week e1RM progress over 12 weeks and ≥80% adherence. A break of ≥8 weeks treats you one level lower for 4 weeks. | Expert Practice |
| AGE-001 | **Age ≥50:** longer warm-up (WU-004); HIIT ceiling 2; deload at 2 signals; year-round power work at moderate load and fast intent (Fragala 2019). **≥60:** next longer return band (REG-004). **≥65:** balance work ≥3 days/week (WHO 2020) and progression caps × 0.8. | High (direction) / Expert (numbers) |
| IND-001 | **Sex and history:** same algorithms for everyone (similar relative gains, Roberts 2020); calibration start points scale with bodyweight if provided. Prior injuries become limitation tags, with that region in conservative mode for the first 4 weeks. | Moderate |

### Adherence design (B3.29)

| Rule | Statement | Confidence |
| --- | --- | --- |
| ADH-001 | **Forgiving weekly streak:** a week counts if you complete ≥ planned − 1 sessions (min 2, or 1 if you plan 2). One "freeze" per 4 weeks. A missed week never resets to zero: the app shows best streak and weeks trained this year. | Expert Practice |
| ADH-002 | **Micro-achievements:** first session, calibration done, personal records (e1RM, reps, distance), block completed, 10/25/50/100 sessions, weeks meeting the WHO floor. Never rewards for extreme volume. | Moderate (Mazeas 2022: small–moderate effects) |
| ADH-003 | **Stable core, rotating edges:** main lifts stay fixed for a block; accessories rotate between blocks; favourites are kept. | Expert Practice |
| ADH-004 | **Time efficiency:** a 20–30-min express option is always one tap away. Messaging expects habits to take about 2–3 months (Lally 2010: median 66 days, range 18–254). | Moderate |

## 35. Safety boundaries, screening, pain and red flags

Safety is a separate layer that runs before and after the fitness engine: a short screen at onboarding, a red-flag stop that ends the session, a pain protocol that removes the painful movement rather than "lightening" it, hard caps the engine can never exceed, and a final validator that checks every workout before you see it. The app is not a medical device and says so once, plainly, at onboarding.

&#91;embedded content: runtime flow and authority order (A3) · 6 steps, 6 ranks\]

Every workout passes the safety layer on the way in and the validator on the way out; the AI coach sits between them and can only ask the engine for changes.

### Pre-participation screening (SAF-001)

Eight questions written for the app (the logic follows ACSM's 2015 screening algorithm and the PAR-Q+ domains; PAR-Q+ wording will only be used verbatim if its licence permits):

1. Has a doctor said you have a heart condition, or high blood pressure that isn't controlled?
2. Do you have diabetes, kidney disease or lung disease?
3. In the past 12 months, have you had chest pain, fainting, unexplained dizziness, or unusual breathlessness at rest or with light effort?
4. Do you have palpitations or a known heart-rhythm problem?
5. Has a doctor told you to limit exercise for any reason, or are you pregnant or recently postpartum?
6. Do you have a bone, joint or soft-tissue problem that exercise could worsen?
7. Do you take medicine for a long-term condition such as blood pressure or heart disease?
8. For the last 3 months, have you exercised on purpose at least 3 days a week for 30+ minutes at moderate effort?

| Answers | Result |
| --- | --- |
| Q3 or Q4 = yes (symptoms) | Medical clearance recommended. Conservative mode: no Z3 or HIIT, RIR ≥3, no failure, until you confirm clearance. |
| Q1 or Q2 = yes and Q8 = no | Medical clearance recommended; conservative mode as above. |
| Q1 or Q2 = yes and Q8 = yes, no symptoms | Continue at moderate effort; clearance recommended before vigorous work (HIIT and Z3 locked until confirmed). |
| Q5 = yes | Follow your clinician's guidance; conservative mode; pregnancy programming is outside V1. |
| Q6 or Q7 = yes | Limitation tags added; coach note to check with your clinician if unsure. |
| All no | Standard programming. |

Re-screen every 12 months, or whenever you report a new condition. Confidence: High (consensus logic, Riebe 2015; Warburton 2011).

### Red flags → SAFETY STOP (SAF-002)

Chest pain, pressure or tightness · fainting or near-fainting · dizziness or light-headedness · unusual breathlessness · palpitations or a racing, irregular heartbeat · sudden severe pain · sudden severe headache, one-sided weakness or numbness, confusion or trouble speaking.

Any of these ends the session immediately. The screen says, calmly: stop, sit or lie down, and if symptoms are severe, include chest pain, or don't settle within a few minutes, call emergency services (112 in India; editable). Training resumes only after you confirm the symptoms resolved or you've seen a doctor, and the first session back is LIGHT. A red flag is never turned into a lighter workout the same day.

### Pain protocol (SAF-003, SAF-004)

The "Something hurts" button is always visible. It asks: where (body map), what kind (muscle burn/ache, or joint/sharp/pins-and-needles), how bad (0–10), and whether it's getting worse during the set.

| What you report | What happens |
| --- | --- |
| Muscle burn during a set | Normal effort sensation; information card; continue |
| General next-day muscle soreness (DOMS) | Feeds readiness soreness, not the pain gate |
| Joint or tendon pain ≤3/10, not worsening, settles by next day | Continue with caution: load −10–20% or shorter range; check again next set |
| 4–6/10, or worsening during the exercise | Stop that exercise; offer pain-free substitutes (joint-stress ≤1 on that joint); region enters conservative mode |
| ≥7/10, sharp or stabbing, swelling, giving way, locking, numbness or tingling, or pain after a fall | Stop all loading of that region; end the session if it affects whole-body movement; suggest a qualified professional |
| Same region painful in ≥2 sessions or for >7 days (SAF-004) | Suggest a physiotherapist or doctor; the region stays in conservative mode (joint-stress ≤1, RIR ≥3, no failure, no jumping) until 2 consecutive pain-free sessions, then steps back up one stress level per week |

Thresholds are Expert Practice; rehab data allow pain up to 5/10 under clinical supervision (Silbernagel 2007), so the unsupervised app uses 3/10.

### Hard caps (SAF-005) — the engine never prescribes beyond these

| Cap | Beginner | Intermediate | Advanced |
| --- | --- | --- | --- |
| Fractional sets per muscle per week | 12 | 16 | 20 |
| Direct sets per muscle per session | 6 | 8 | 10 |
| Working sets per session | 20 | 25 | 30 |
| Weekly systemic stress (SSU) | 100 | 140 | 180 |
| HIIT sessions per week | 2 | 2 (3 if HIIT-001 allows) | 2 (3 if HIIT-001 allows) |
| Planned RIR-0 exercises per session | 0 for first 8 weeks, then ≤2 | ≤2 | ≤2 |
| Training days per week | 6 | 6 | 6 |
| Planned weekly load increase | ≤ +20% week over week; after 4 weeks ≤ 1.20 × 3-week mean | same | same |
| Prescribed intensity rise per lift | ≤7.5%/week | ≤5%/week | ≤5%/week |

### User additions and autonomy (SAF-006)

You may skip, swap, add sets or lift heavier. The app records it honestly and adapts. When an addition would pass a cap, it warns and asks you to confirm *that* addition; nothing beyond a cap happens silently. An absolute ceiling stops accidental runaway: 1.5 × the weekly per-muscle cap, 40 working sets per session, or 4 HIIT sessions per week. Additions to a painful region are blocked.

### Illness (SAF-007)

Fever, chills, body aches, chest symptoms, vomiting or diarrhoea lock the day to RECOVERY (rest). Return follows REG-005.

### Final safety validator (SAF-008)

Every generated or modified session is checked before display: per-muscle and per-session sets, weekly SSU and planned load, HIIT count and spacing, pattern balance (PAT), pain-region exclusions, contraindication tags, readiness-tier compliance, screening mode, failure policy. On failure it auto-corrects (trims sets, swaps exercises, removes HIIT) and logs why. If it cannot correct, it falls back to a LIGHT session. It never crashes and never shows an unvalidated workout.

### AI coach limits and voice (SAF-009, COACH-001)

The coach explains decisions the engine already made, citing the Decision Log and rule IDs. It gives no diagnosis, no medication or supplement advice and no extreme-diet advice, and refers you to qualified professionals. It can only *request* engine actions (shorter session, swap), which the engine validates. Its voice is calm, competent, honest and brief: no body-shaming, no guilt, no punishment exercise, no "no pain, no gain". Instructions hidden in notes or retrieved text are treated as data. Offline, it falls back to deterministic explanations.

## 36. Data taxonomy (DATA-001)

The app collects 11 required items, 6 optional ones, and nothing else; every item names the rule that uses it, and all of it stays on your phone.

| Data item | Class | Used by | Why it earns its place |
| --- | --- | --- | --- |
| Age | Required | AGE-001, WU-004, REG-004 | Changes warm-up, recovery and return rules |
| Training experience | Required | EXP-001, VOL-003, INT-002 | Sets volume, effort and progression style |
| Days available, preferred days | Required | FREQ-001, SCH-001 | Builds the week |
| Session length (default 60 min) | Required | TIME rules | Fits each session |
| Equipment inventory and smallest increments | Required | SUB-001, PROG-003 | Real loads and valid swaps |
| Screening answers | Required | SAF-001 | Sets conservative mode when needed |
| Priorities (ranked top 3) | Required | PER-006 | Shapes block lengths and volume |
| Logged sets (load, reps, RIR/RPE, form check) | Required | PROG, INT-004, DEL-001 | Progression and fatigue detection |
| Session RPE and duration | Required | LOAD rules | Workload tracking |
| Daily readiness (4 items) | Required | RDY rules | Today's tier |
| Pain reports (region, type, 0–10) | Required when pain occurs | SAF-003, SAF-004 | Safety |
| Sleep hours | Optional | RDY-005 | Sharper sleep caps |
| Bodyweight | Optional | IND-001, bodyweight exercise loads | Calibration and progress |
| Body measurements | Optional | Progress view only | Progress beyond the scale |
| Heart-rate data (typed in) | Optional | AER-001 | HR zones instead of talk test |
| Sex | Optional | IND-001 | Only where defaults differ (start loads) |
| Exercise likes and dislikes | Optional | SUB-002, SUB-003 | Enjoyment and adherence |

**Not collected:** location or GPS, contacts, photos or camera, microphone, advertising IDs, social profiles, step counts from other apps (V1), calorie intake, and vanity metrics such as total kilograms lifted per lifetime. They add complexity or privacy risk without improving your training.

Health-related items (screening, pain, bodyweight) are stored only on the device, included in your export file, and erased by "Delete all my data".

## 37–38. Research updates, rule versioning and the Rule Registry v0.1

Rule Registry v0.1 holds **140 rules** and 64 sources in one machine-readable file; every number in this report comes from it. Only 44 rules rest on moderate or high evidence; 75 are expert practice and 15 are product or engineering rules, which is exactly why new research can propose changes but can never change your training without review, testing and your approval.

### 37. Research update architecture

A developer-side workflow, not something running inside the app. It can run offline (papers you or I supply) or online (search). The app never edits its own rules; changes reach your phone only in a new, tested release.

1. **Monitor** sources weighted by the B1 hierarchy; preprints and single studies are flagged; low-credibility sources ignored.
2. **Summarise** each new finding in plain language with its population and design.
3. **Compare** it with the rules it touches (by rule ID).
4. **Identify conflicts** and why they exist.
5. **Assign confidence** using the B1 scale.
6. **Propose a change** as a draft rule edit (old value → new value, rationale).
7. **Impact analysis:** affected rules, algorithms, typical workouts, and your logged data.
8. **Human approval** by you (the Product Owner). Safety parameters additionally need an explicit safety review note.
9. **Version** the rule (semantic version below) and record the reason.
10. **Regression-test** every algorithm the rule affects before release.

Retrieved papers and web pages are treated as data, never as instructions.

### 38. Rule versioning

| Version change | When | Example |
| --- | --- | --- |
| MAJOR (2.0.0) | Changes training direction or a safety boundary, or removes a rule | Lowering the HIIT ceiling to 1 |
| MINOR (1.1.0) | Changes a parameter value or adds a rule | Rest default 120 → 105 s |
| PATCH (1.0.1) | Wording, evidence list or test reference only | Adding a new supporting citation |

Each rule records: ID, version, category, title, statement, parameters, confidence, evidence (source keys), uncertainty, date introduced, date reviewed, affected algorithms, linked tests, change reason and status. All rules are now **0.1.0 / proposed**; on your approval they become **1.0.0 / approved**. Updates always follow DISCOVER → REVIEW → VALIDATE → VERSION → TEST → RELEASE.

### Rule Registry v0.1 — contents by confidence

| Category | Rules | High | Moderate | Limited | Expert Practice | Product rule |
| --- | --- | --- | --- | --- | --- | --- |
| Safety & coaching voice | 10 | 2 | 0 | 0 | 4 | 4 |
| Frequency & public health | 6 | 3 | 2 | 0 | 1 | 0 |
| Volume | 8 | 0 | 2 | 1 | 5 | 0 |
| Intensity & reps | 13 | 2 | 8 | 0 | 3 | 0 |
| Rest & order | 10 | 0 | 4 | 0 | 6 | 0 |
| Concurrent, aerobic, HIIT, modality | 16 | 0 | 5 | 3 | 7 | 1 |
| Patterns, bodyweight, core, mobility, warm-up | 18 | 0 | 4 | 0 | 14 | 0 |
| Readiness, workload, deload | 17 | 0 | 4 | 2 | 10 | 1 |
| Periodisation, progression, regression, calibration | 20 | 0 | 3 | 0 | 15 | 2 |
| Substitution, time, generation, scheduling | 11 | 0 | 0 | 0 | 6 | 5 |
| Experience, individual, equipment, adherence, data | 11 | 1 | 4 | 0 | 4 | 2 |
| **Total** | **140** | **8** | **36** | **6** | **75** | **15** |

The registry file (`rules/rule_registry_v0.1.json`) is attached to this conversation and saved in the Fitness App project. A sample entry:

```json
{
  "rule_id": "PROG-001",
  "version": "0.1.0",
  "category": "progression",
  "title": "Load progression",
  "parameters": { "min_step_pct": 2.5, "max_step_pct": 5.0, "rpe_tolerance": 0.5 },
  "confidence": "Moderate Evidence",
  "evidence": ["ACSM2026"],
  "date_introduced": "2026-10-07",
  "affected_algorithms": ["progressive_overload"],
  "tests": ["TC-PROG-001a", "TC-PROG-001b"],
  "status": "proposed"
}
```

## 39. Research-to-algorithm traceability matrix

Thirty key findings are traced from evidence to the screen you will see and the automated test that will prove it in Phase 4; every one of the 140 registry rules carries at least two test IDs (`TC-<rule>a`, `TC-<rule>b`).

| # | Finding (source) | Principle | Rule | Algorithm | What you see | Test |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Volume drives growth with diminishing returns (Pelland 2024; Schoenfeld 2017) | Enough volume, but recoverable | VOL-003, VOL-006 | Weekly volume allocation | "Chest gets 10 sets this week, up from 8 — your presses keep improving." | TC-VOL-006a: improving history, no signals → +step, ≤ cap |
| 2 | Unrecoverable volume stalls progress (Meeusen 2013) | Volume must be recoverable | VOL-004 | Fatigue-driven reduction | "I've cut two accessory sets — your recent load has been high." | TC-VOL-004a: rising sRPE + stalled lifts → −30% accessories, log cites VOL-004 |
| 3 | Frequency barely matters for size at equal volume (Schoenfeld 2019) | Frequency distributes volume | FREQ-002, VOL-005 | Exposure split | (Invisible) balanced sessions | TC-FREQ-002a: 12 weekly sets with cap 8 → ≥2 exposures |
| 4 | Heavy loads maximise strength (ACSM 2026; Currier 2023) | Specificity | REP-001 | Block rep ranges | "Heavier sets this block to build strength." | TC-REP-001a: strength-block main lifts 3–6 reps, ≥80% e1RM |
| 5 | Failure adds little (Robinson 2024; Grgic 2022) | Effort without needless risk | INT-003 | Failure policy | Only machine/isolation last sets marked "to failure" | TC-INT-003a: 10,000 generated sessions, zero RIR-0 on unsafe lifts |
| 6 | Novices misjudge RIR (Halperin 2022) | Teach effort gradually | INT-006 | Beginner effort prompts | "How many more could you have done?" | TC-INT-006a: weeks 1–4 show RIR picker, not RPE |
| 7 | e1RM formulas degrade at high reps (LeSuer 1997) | Measure honestly | INT-004 | e1RM update filter | Strength trend chart | TC-INT-004a: 50 kg × 15 @ RIR 2 does not update e1RM |
| 8 | Rest >60 s helps size slightly (Singer 2024) | Rest enough, not too much | REST-002, REST-003 | Timer defaults | Rest timer 2:00 / 1:15 | TC-REST-002a: defaults inside ranges |
| 9 | Strength-first helps lower-body strength (Murlasits 2018) | Strength and cardio coexist | CON-001 | Session ordering | Conditioning after lifting | TC-CON-001a: shared sessions order strength first |
| 10 | Concurrent training blunts power (Schumann 2022) | Protect power | CON-005 | Ordering + HIIT spacing | Power work first | TC-CON-005a: no power after HIIT same day |
| 11 | Impact modes interfere more (Wilson 2012) | Prefer low impact | CON-004, MOD-002 | Modality score | "SkiErg today — it spares your legs." | TC-CON-004a: heavy-leg tomorrow → lowest-interference modality |
| 12 | Talk test tracks ventilatory threshold (Foster 2018) | Intensity without a monitor | AER-001 | Zone prescription | "Easy: you can talk in full sentences." | TC-AER-001a: Z1 maps to CR10 3–4 |
| 13 | Longer intervals raise VO2max most (Wen 2019) | Effective intervals | HIIT-002 | Protocol choice | "4 × 4 min hard, 3 min easy." | TC-HIIT-002a: Build block picks long intervals |
| 14 | HIIT has a high recovery cost (expert practice) | Cap hard work | HIIT-001 | Weekly ceiling | "Third hard session changed to an easy row." | TC-HIIT-001a: property — never >2 (3 only if eligible) |
| 15 | WHO minimums (Bull 2020) | Health floor first | PH-001 | Weekly accounting | "120 of 150 minutes — a 30-min walk closes the gap." | TC-PH-001a: equivalent minutes computed correctly |
| 16 | Self-report tracks load (Saw 2016) | Ask how you feel | RDY-001 | Readiness score | Tier with one-line reason | TC-RDY-001a: all 3s → 50 → FULL |
| 17 | Sleep loss cuts performance (Craven 2022) | Protect poor-sleep days | RDY-005 | Sleep caps | "Under 5 hours' sleep — shorter session today." | TC-RDY-005a: 4.5 h caps at MODIFIED |
| 18 | Session-RPE is valid (Foster 2001) | Measure load simply | LOAD-001 | Session load | One effort question at the end | TC-LOAD-001a: load = RPE × minutes |
| 19 | ACWR is statistically flawed (Lolli 2019; Impellizzeri 2021) | Don't over-trust one number | LOAD-004, LOAD-005 | Soft flag + planning cap | "Your workload jumped this week" (never "injury risk") | TC-LOAD-005a: planned week ≤ 1.20 × 3-week mean |
| 20 | Deloads cut volume; a full week off didn't help (Bell 2023; Coleman 2024) | Recover without detraining | DEL-002, DEL-003 | Deload trigger + prescription | "Lighter week: half the sets, similar weights." | TC-DEL-002a: 3 signals → deload next session |
| 21 | Strength declines after a few weeks off (Bosquet 2013) | Re-enter gradually | REG-004 | Return ramp | "Welcome back — 90% loads this week." | TC-REG-004a: 20 days off → week 1 at 90%/70% |
| 22 | Periodisation helps strength slightly; complexity not needed (Moesgaard 2022; ACSM 2026) | Simple structure | PER-001 | Blocks + DUP | Year view, this-block focus | TC-PER-001a: main lifts alternate heavy/lighter within week |
| 23 | Screening logic (Riebe 2015) | Safety first | SAF-001 | Conservative mode | "Please check with your doctor before hard intervals." | TC-SAF-001a: symptoms → no HIIT until clearance |
| 24 | Warning symptoms need immediate stop (Riebe 2015) | Safety first | SAF-002 | SAFETY STOP | Calm stop screen | TC-SAF-002a: chest pain → session ends, never lighter workout |
| 25 | Pain monitoring guides activity (Silbernagel 2007) | Remove the painful movement | SAF-003 | Pain flow | "Let's skip this one — try the machine row." | TC-SAF-003a: 5/10 knee pain → no knee-stress >1 |
| 26 | Short static stretches barely affect strength (Behm 2016) | Dynamic prep first | MOB-002 | Warm-up composition | Dynamic warm-up | TC-MOB-002a: no static holds >30 s pre-lift |
| 27 | Lifting improves range of motion (Afonso 2021) | Full range | MOB-004 | Cueing | "Full depth if pain-free." | TC-MOB-004a: default ROM = full |
| 28 | Older adults benefit from power and balance (Fragala 2019; WHO 2020) | Age-aware | AGE-001 | Adjustments | "Balance drill added." | TC-AGE-001a: age 66 → balance ≥3 days/week |
| 29 | Habits take \~66 days; gamification helps modestly (Lally 2010; Mazeas 2022) | Forgiving consistency | ADH-001, ADH-002 | Weekly streak | "Week 6 — a missed week won't reset it." | TC-ADH-001a: missed week keeps best streak |
| 30 | Time-efficient methods preserve results (Iversen 2021) | Prioritise, don't truncate | TIME-002 | Compression | "Kept your squats and intervals." | TC-TIME-002a: 60→30 min keeps P1 and P2 |

## 40. Unresolved scientific uncertainties and research gaps

Sixteen open questions remain; none blocks building the app, because each has a conservative default, a confidence label and a planned check. The biggest are the readiness weights, the volume caps and the new systemic-stress model, all of which Phase 4's 12-month simulations will stress-test before you rely on them.

| # | Uncertainty | What the app does now | How it gets resolved |
| --- | --- | --- | --- |
| 1 | Readiness weights and tier thresholds have no direct evidence | Conservative tiers; personal-baseline blend; you can always choose easier | Phase 4 simulations; after 8 weeks of your data, check whether readiness predicts your session performance |
| 2 | Individual volume tolerance varies widely; caps are judgement | Start low, add only when progressing; hard caps | Volume progression responds to your own performance signals |
| 3 | The systemic-stress (SSU) coefficients are new | Used only for planning caps, cross-checked against your session-RPE load | Calibrate k from your first 4 weeks; tune in Phase 4 |
| 4 | HIIT ceiling for general fitness is not well studied | Default 2/week with strict conditions for a 3rd | Research Update Agent watches for new trials |
| 5 | Deload timing rests on a Delphi survey and one small trial | Signal-based triggers; reduced-volume (not zero) deloads | Review as trials appear |
| 6 | The acute:chronic workload ratio is statistically weak | Soft flag only; the planning cap is the real guard | Keep under review; may be removed |
| 7 | Pain thresholds for *unsupervised* training | 3/10 cut-off, stricter than rehab studies | Expert review in Phase 4 audit |
| 8 | RIR estimates are least accurate on light, high-rep sets and for beginners | Conservative RIR targets; e1RM ignores high-rep sets | Optional RIR-check sets on safe machines from week 5 |
| 9 | Concurrent-training data for rowers and SkiErgs are scarce (most studies use running or cycling) | Modality scores are expert judgement | Monitor your leg-day performance after conditioning |
| 10 | Sleep-cap thresholds are approximations; chronic short sleep behaves differently from one bad night | Caps at <4 h and <5 h | Revisit with your data |
| 11 | e1RM formula accuracy differs by exercise (machines, dumbbells) | Used for trends and load suggestions, never as a test result | Track prediction error from your logs |
| 12 | Pelland 2024 is a preprint | Used for direction (diminishing returns), not exact numbers | Re-check on journal publication |
| 13 | ACSM 2026 position stand read via official summaries, not full text (full text blocked from this environment) | Its headline recommendations only | Read the full text before Phase 2 sign-off |
| 14 | Talk-test validation is mostly on treadmills and bikes | CR10 effort ratings back it up | Your Z1 pace/watts become a personal anchor |
| 15 | Return-to-training percentages and age-specific numbers are judgement | Conservative ramps; older users get longer ramps | Phase 4 scenario tests 9 and 30 |
| 16 | A 2026 study on upper volume limits in trained athletes was located but not reviewed | Caps unchanged | Review in the first research-update cycle |

**Unknowns about you (ASSUMPTIONS until onboarding):** age (adult, no age adjustment), experience (beginner rules applied until classified), 3 training days, 60-minute sessions, a typical commercial gym with barbells, dumbbells, cables, machines, rower, SkiErg and elliptical, no injuries, and kilograms. None of these blocks Phase 1; all are collected in onboarding.

## Phase 1 gate report — Fitness Science sign-off

All 40 Phase 1 deliverables are complete as a reviewed draft, backed by 64 sources and a 140-rule registry; no app code or workout templates exist yet, only a small script that builds and checks the registry. The science is ready for product design; about half the rules are expert practice that Phase 4 must stress-test.

**1. Completed work.** Environment audit; evidence search and appraisal (64 sources: 11 checked in detail, 52 located, 1 unverified); seed parameters S01–S22 validated, adjusted or downgraded; every B3 domain turned into rules with IDs, confidence and pseudocode; Rule Registry v0.1 written and validated by script; all worked-example arithmetic re-computed by script.

**2. Key decisions.**

- Periodisation: autoregulated hybrid (blocks + within-week undulation + session autoregulation).
- Volume: fractional set counting; block start 6/8/10 and caps 12/16/20 sets/muscle/week by level.
- Effort: most sets 1–3 reps short of failure; failure only on safe exercises.
- Cardio: strength first; low-impact machines preferred; HIIT ≤2/week by default.
- Readiness: 4-item score blended with personal baseline; pain and red flags are separate gates.
- Workload: session-RPE is the measure; the acute:chronic ratio is only a soft flag; a +20% planning cap is the real guard.
- Deloads: triggered by fatigue signals, reduced volume (not rest).
- A new systemic-stress equation (SSU) caps total weekly stress across lifting and conditioning.

**3. Assumptions.** Adult with no age adjustment; beginner rules until classified; 3 days × 60 min; typical commercial gym including rower, SkiErg and elliptical; no injuries; kilograms; air/fan bikes and treadmill walking excluded; no heart-rate monitor; no velocity device.

**4. Open issues.** Readiness weights, volume caps and SSU coefficients need simulation; ACSM 2026 full text to be read; Pelland 2024 still a preprint; screening wording needs a licence check before any PAR-Q+ text is reused.

**5. Risks.** Expert-practice rules may be too conservative (slower progress) or too generous for some users; mitigated by personal-baseline logic, caps and Phase 4 longitudinal simulations. Self-reported RIR and readiness can be noisy; mitigated by conservative targets and trend-based decisions. Android build depends on a GitHub account (Section 1).

**6. Deliverables and status.**

| Deliverables | Section | Status |
| --- | --- | --- |
| 1 Environment audit | 1 | VERIFIED (commands run 7 Oct 2026) |
| 2–4 Evidence map, confidence matrix, seed validation | 2–4 | Complete; sources VERIFIED as labelled (checked / located), one UNVERIFIED |
| 5–6 Principles, objective interaction model | 5–6 | Complete |
| 7–21 Dosage, cardio, movement, warm-up rules | 7–21 | Complete; expert-practice values REQUIRE VALIDATION |
| 22–28 Readiness, deload, periodisation, blueprint, progression, regression, workload | 22–28 | Complete; worked examples VERIFIED by script; thresholds REQUIRE VALIDATION |
| 29–34 Schema, substitution, calibration, time budget, generation, scheduling | 29–34 | Complete as specification; not implemented (no code by design) |
| 35 Safety | 35 | Complete; REQUIRES expert review in Phase 4 |
| 36 Data taxonomy | 36 | Complete |
| 37–38 Research update architecture, versioning, Rule Registry v0.1 | 37–38 | Complete; registry JSON VERIFIED (parsed, unique IDs, all evidence keys resolve) |
| 39 Traceability matrix | 39 | Complete; test IDs defined, tests NOT STARTED (Phase 3–4) |
| 40 Uncertainties | 40 | Complete |

**7. Validation status.** Arithmetic and registry integrity VERIFIED by script. Rules and thresholds are a specification, not yet executed: they become testable in Phase 3 and are validated in Phase 4 (scenario tests, 12-month simulations, safety audit).

**What was established:** the core dose (≥2×/week per muscle, \~10+ hard sets, RIR 1–3, heavier loads for strength, generous rests), concurrent-training compatibility, the public-health floor, and screening logic — all moderate to high evidence. **What remains uncertain:** section 40. **Safety decisions:** section 35 — screen-driven conservative mode, red-flag stop, pain gate at 3/10, hard caps, final validator. **Algorithms ready for implementation:** readiness, workload, deload, progression, calibration, substitution, time budget, generation, scheduling — each with pseudocode, parameters in the registry and test IDs.

```text
================================================================================
PHASE 1 SIGN-OFF GATE
Please review the complete Phase 1 Research & Evidence deliverable above.
Do you approve the research findings, algorithms and training rules?
Reply 'APPROVED' to advance to Phase 2 (Product Requirements & Architecture),
or give feedback / adjustments.
================================================================================
```

## Sources

Verification as of 7 October 2026. *Checked* = abstract or publisher summary read. *Located* = title, year and journal confirmed; author list, volume, pages and findings from prior knowledge. *Unverified* = not located. The key in brackets is the source's ID in the Rule Registry.

- [Afonso J, Ramirez-Campillo R, Moscão J, et al. Strength training versus stretching for improving range of motion: a systematic review and meta-analysis. Healthcare (Basel). 2021;9(4):427.](https://estudogeral.uc.pt/handle/10316/104612) — Located \[Afonso2021\]
- [Androulakis-Korakakis P, Fisher JP, Steele J. The minimum effective training dose required to increase 1RM strength in resistance-trained men: a systematic review and meta-analysis. Sports Med. 2020;50(4):751-765.](https://research.bond.edu.au/en/publications/the-minimum-effective-training-dose-required-for-1rm-strength-in-/) — Located \[AndroulakisKorakakis2020\]
- [Behm DG, Blazevich AJ, Kay AD, McHugh M. Acute effects of muscle stretching on physical performance, range of motion, and injury incidence in healthy active individuals: a systematic review. Appl Physiol Nutr Metab. 2016;41(1):1-11.](https://pure.northampton.ac.uk/en/publications/acute-effects-of-muscle-stretching-on-physical-performance-range-/) — Located \[Behm2016\]
- [Bell L, Strafford BW, Coleman M, Androulakis Korakakis P, Nolan D. Integrating deloading into strength and physique sports training programmes: an international Delphi consensus approach. Sports Med Open. 2023;9:87.](https://link.springer.com/article/10.1186/s40798-023-00633-0) — Checked \[Bell2023\]
- [Borg GA. Psychophysical bases of perceived exertion. Med Sci Sports Exerc. 1982;14(5):377-381.](https://explore.openalex.org/works/w2004008756) — Located \[Borg1982\]
- [Bosquet L, Berryman N, Dupuy O, et al. Effect of training cessation on muscular performance: a meta-analysis. Scand J Med Sci Sports. 2013;23(3):e140-e149.](https://ekoizpen-zientifikoa.ehu.eus/documentos/5f1cda5129995265e44d37a4?lang=en) — Located \[Bosquet2013\]
- [Brzycki M. Strength testing: predicting a one-rep max from reps-to-fatigue. J Phys Educ Recreat Dance. 1993;64(1):88-90.](https://brzycki.scholar.princeton.edu/publications/strength-testing-%E2%80%93-predicting-one-rep-max-reps-fatigue) — Located \[Brzycki1993\]
- [Buchheit M, Laursen PB. High-intensity interval training, solutions to the programming puzzle. Part I: cardiopulmonary emphasis. Sports Med. 2013;43(5):313-338.](https://www.iat.uni-leipzig.de/datenbanken/iks/dsv-xc/Record/4056671) — Located \[Buchheit2013\]
- [Bull FC, Al-Ansari SS, Biddle S, et al. World Health Organization 2020 guidelines on physical activity and sedentary behaviour. Br J Sports Med. 2020;54(24):1451-1462.](https://digibug.ugr.es/handle/10481/66573) — Located \[Bull2020\]
- [Coleman M, et al. Gaining more from doing less? The effects of a one-week deload period during supervised resistance training on muscular adaptations. PeerJ. 2024;12:e16777.](https://peerj.com/articles/16777) — Checked \[Coleman2024\]
- [Craven J, McCartney D, Desbrow B, et al. Effects of acute sleep loss on physical performance: a systematic and meta-analytical review. Sports Med. 2022;52(11):2669-2690.](https://nutrition-evidence.com/article/415006/effects-of-acute-sleep-loss-on-physical-performance-a-systematic-and-meta-analytical-review) — Checked \[Craven2022\]
- [Currier BS, McLeod JC, Banfield L, et al. Resistance training prescription for muscle strength and hypertrophy in healthy adults: a systematic review and Bayesian network meta-analysis. Br J Sports Med. 2023;57(18):1211-1220.](https://research-information.bris.ac.uk/en/publications/resistance-training-prescription-for-muscle-strength-and-hypertro/) — Checked \[Currier2023\]
- Epley B. Poundage Chart. Boyd Epley Workout. Lincoln, NE: Body Enterprises; 1985. (origin of the Epley formula; not peer reviewed) — Unverified \[Epley1985\]
- [Exploring the upper limits of resistance training volume for muscle hypertrophy and strength in trained athletes (authors not recorded). J Sci Sport Exerc. 2026. Located, not reviewed; not used by any rule.](https://journal.hep.com.cn/josisae/EN/10.1007/s42978-026-00387-7) — Located \[UpperLimits2026\]
- [Foster C, Florhaug JA, Franklin J, et al. A new approach to monitoring exercise training. J Strength Cond Res. 2001;15(1):109-115.](https://sponet.de/sponet/Record/4006210) — Located \[Foster2001\]
- [Foster C, Porcari JP, Ault S, et al. Exercise prescription when there is no exercise test: the talk test. Kinesiology. 2018;50(Suppl 1):33-48.](https://hrcak.srce.hr/en/192696) — Checked \[Foster2018talk\]
- [Foster C. Monitoring training in athletes with reference to overtraining syndrome. Med Sci Sports Exerc. 1998;30(7):1164-1168.](https://lida.sport-iat.de/ta/Record/4001707?lng=en) — Located \[Foster1998\]
- [Fradkin AJ, Zazryn TR, Smoliga JM. Effects of warming-up on physical performance: a systematic review with meta-analysis. J Strength Cond Res. 2010;24(1):140-148.](https://lida.sport-iat.de/ta/Record/4017835) — Located \[Fradkin2010\]
- [Fragala MS, Cadore EL, Dorgo S, et al. Resistance training for older adults: position statement from the National Strength and Conditioning Association. J Strength Cond Res. 2019;33(8):2019-2052.](https://scholarworks.utep.edu/kines_papers/60) — Located \[Fragala2019\]
- [Gabbett TJ. The training-injury prevention paradox: should athletes be training smarter and harder? Br J Sports Med. 2016;50(5):273-280.](https://lida.sport-iat.de/ta/Record/4040728) — Located \[Gabbett2016\]
- [Garber CE, Blissmer B, Deschenes MR, et al. ACSM position stand: quantity and quality of exercise for developing and maintaining cardiorespiratory, musculoskeletal, and neuromotor fitness in apparently healthy adults. Med Sci Sports Exerc. 2011;43(7):1334-1359.](https://digitalcommons.uri.edu/kinesiology_facpubs/137) — Located \[Garber2011\]
- [Grgic J, Schoenfeld BJ, Davies TB, et al. Effect of resistance training frequency on gains in muscular strength: a systematic review and meta-analysis. Sports Med. 2018;48(5):1207-1220.](https://vuir.vu.edu.au/37695/) — Located \[Grgic2018freq\]
- [Grgic J, Schoenfeld BJ, Orazem J, Sabol F. Effects of resistance training performed to repetition failure or non-failure on muscular strength and hypertrophy: a systematic review and meta-analysis. J Sport Health Sci. 2022;11(2):202-211.](https://doaj.org/article/dc36ba7ccd994798964f66b93bd8f662) — Located \[Grgic2022fail\]
- [Grgic J, Schoenfeld BJ, Skrepnik M, Davies TB, Mikulic P. Effects of rest interval duration in resistance training on measures of muscular strength: a systematic review. Sports Med. 2018;48(1):137-151.](https://vuir.vu.edu.au/38537/) — Located \[Grgic2018rest\]
- [Halperin I, Malleron T, Har-Nir I, et al. Accuracy in predicting repetitions to task failure in resistance exercise: a scoping review and exploratory meta-analysis. Sports Med. 2022;52(2):377-390.](https://pure.solent.ac.uk/en/publications/accuracy-in-predicting-repetitions-to-task-failure-in-resistance-/) — Located \[Halperin2022\]
- [Helms ER, Cronin J, Storey A, Zourdos MC. Application of the repetitions in reserve-based rating of perceived exertion scale for resistance training. Strength Cond J. 2016;38(4):42-49.](https://openrepository.aut.ac.nz/items/92cefeac-7c32-44f1-bfbc-f48f4a5dd8cf/full) — Located \[Helms2016\]
- [Impellizzeri FM, Woodcock S, Coutts AJ, et al. What role do chronic workloads play in the acute to chronic workload ratio? Time to dismiss ACWR and its underlying theory. Sports Med. 2021;51(3):581-592.](https://iris.univr.it/handle/11562/1139126) — Located \[Impellizzeri2021\]
- [Israetel M, Hoffmann J, Smith CW. Scientific Principles of Hypertrophy Training. Renaissance Periodization; 2021 (book; source of the MV/MEV/MAV/MRV vocabulary).](https://rpstrength.com/the-scientific-principles-of-hypertrophy-training) — Located \[Israetel2021\]
- [Iversen VM, Norum M, Schoenfeld BJ, Fimland MS. No time to lift? Designing time-efficient training programs for strength and hypertrophy: a narrative review. Sports Med. 2021;51(10):2079-2095.](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC8449772/) — Located \[Iversen2021\]
- [Jeffreys I. The Warm-Up: Maximise Performance and Improve Long-Term Athletic Development. Human Kinetics; 2019 (RAMP protocol).](https://pure.southwales.ac.uk/en/publications/the-warm-up-maximise-performance-and-improve-long-term-athletic-d/) — Located \[Jeffreys2019\]
- [Kibler WB, Press J, Sciascia A. The role of core stability in athletic function. Sports Med. 2006;36(3):189-198.](https://vivo.weill.cornell.edu/display/pubid16526831) — Located \[Kibler2006\]
- [Lally P, van Jaarsveld CHM, Potts HWW, Wardle J. How are habits formed: modelling habit formation in the real world. Eur J Soc Psychol. 2010;40(6):998-1009.](https://discovery.ucl.ac.uk/id/eprint/10144814/) — Located \[Lally2010\]
- [LeSuer DA, McCormick JH, Mayhew JL, Wasserstein RL, Arnold MD. The accuracy of prediction equations for estimating 1-RM performance in the bench press, squat, and deadlift. J Strength Cond Res. 1997;11(4):211-213.](https://researchconnect.suny.edu/en/publications/accuracy-of-prediction-equations-for-determining-one-repetition-m/) — Located \[LeSuer1997\]
- [Lolli L, Batterham AM, Hawkins R, et al. Mathematical coupling causes spurious correlation within the conventional acute-to-chronic workload ratio calculations. Br J Sports Med. 2019;53(15):921-922.](https://bjsm.bmj.com/content/53/15/921) — Located \[Lolli2019\]
- [Mazeas A, Duclos M, Pereira B, Chalabaev A. Evaluating the effectiveness of gamification on physical activity: systematic review and meta-analysis of randomized controlled trials. J Med Internet Res. 2022;24(1):e26779.](https://www.jmir.org/2022/1/E26779/) — Located \[Mazeas2022\]
- [Meeusen R, Duclos M, Foster C, et al. Prevention, diagnosis, and treatment of the overtraining syndrome: joint consensus statement of the ECSS and the ACSM. Med Sci Sports Exerc. 2013;45(1):186-205.](https://hal.inrae.fr/hal-02651426) — Located \[Meeusen2013\]
- [Milanović Z, Sporiš G, Weston M. Effectiveness of high-intensity interval training (HIT) and continuous endurance training for VO2max improvements: a systematic review and meta-analysis of controlled trials. Sports Med. 2015;45(10):1469-1481.](https://research.tees.ac.uk/en/publications/effectiveness-of-high-intensity-interval-training-hit-and-continu-3/) — Located \[Milanovic2015\]
- [Moesgaard L, Beck MM, Christiansen L, Aagaard P, Lundbye-Jensen J. Effects of periodization on strength and muscle hypertrophy in volume-equated resistance training programs: a systematic review and meta-analysis. Sports Med. 2022;52(7):1647-1666.](https://pubmed.ncbi.nlm.nih.gov/35044672/) — Located \[Moesgaard2022\]
- [Murlasits Z, Kneffel Z, Thalib L. The physiological effects of concurrent strength and endurance training sequence: a systematic review and meta-analysis. J Sports Sci. 2018;36(11):1212-1219.](https://pubmed.ncbi.nlm.nih.gov/28783467/) — Located \[Murlasits2018\]
- [Nunes JP, Grgic J, Cunha PM, et al. What influence does resistance exercise order have on muscular strength gains and muscle hypertrophy? A systematic review and meta-analysis. Eur J Sport Sci. 2021;21(2):149-157.](https://vuir.vu.edu.au/44202/) — Located \[Nunes2021\]
- [Pelland JC, Remmert JF, Robinson ZP, Hinson SR, Zourdos MC. The resistance training dose-response: meta-regressions exploring the effects of weekly volume and frequency on muscle hypertrophy and strength gain. SportRxiv preprint (version 2), 2024 — not peer reviewed.](https://sportrxiv.org/index.php/server/preprint/view/460) — Checked \[Pelland2024\]
- [Phillips SM (chair), Currier BS, D'Souza AC, Fiatarone Singh MA, et al. ACSM Position Stand. Resistance training prescription for muscle function, hypertrophy, and physical performance in healthy adults: an overview of reviews. Med Sci Sports Exerc. April 2026.](https://acsm.org/science-spotlight-acsm-releases-new-position-stand-on-resistance-training/) — Checked via ACSM's summary and slide deck; full text not accessed \[ACSM2026\]
- [Refalo MC, Helms ER, Trexler ET, Hamilton DL, Fyfe JJ. Influence of resistance training proximity-to-failure on skeletal muscle hypertrophy: a systematic review with meta-analysis. Sports Med. 2023;53(3):649-665.](https://dro.deakin.edu.au/articles/journal_contribution/Influence_of_Resistance_Training_Proximity-to-Failure_on_Skeletal_Muscle_Hypertrophy_A_Systematic_Review_with_Meta-analysis/22030796) — Located \[Refalo2023\]
- [Riebe D, Franklin BA, Thompson PD, et al. Updating ACSM's recommendations for exercise preparticipation health screening. Med Sci Sports Exerc. 2015;47(11):2473-2479.](https://digitalcommons.uri.edu/kinesiology_facpubs/180) — Located \[Riebe2015\]
- [Roberts BM, Nuckols G, Krieger JW. Sex differences in resistance training: a systematic review and meta-analysis. J Strength Cond Res. 2020;34(5):1448-1460.](https://pubmed.ncbi.nlm.nih.gov/32218059/) — Located \[Roberts2020\]
- [Robineau J, Babault N, Piscione J, Lacome M, Bigard AX. Specific training effects of concurrent aerobic and strength exercises depend on recovery duration. J Strength Cond Res. 2016;30(3):672-683.](https://pubmed.ncbi.nlm.nih.gov/25546450/) — Located \[Robineau2016\]
- [Robinson ZP, Pelland JC, Remmert JF, et al. Exploring the dose-response relationship between estimated resistance training proximity to failure, strength gain, and muscle hypertrophy: a series of meta-regressions. Sports Med. 2024;54(9):2209-2231.](https://rke.abertay.ac.uk/en/publications/exploring-the-dose-response-relationship-between-estimated-resist/) — Checked \[Robinson2024\]
- [Sabag A, Najafi A, Michael S, et al. The compatibility of concurrent high intensity interval training and resistance training for muscular strength and hypertrophy: a systematic review and meta-analysis. J Sports Sci. 2018;36(21):2472-2483.](https://researchers.westernsydney.edu.au/en/publications/the-compatibility-of-concurrent-high-intensity-interval-training-/) — Located \[Sabag2018\]
- [Saw AE, Main LC, Gastin PB. Monitoring the athlete training response: subjective self-reported measures trump commonly used objective measures: a systematic review. Br J Sports Med. 2016;50(5):281-289.](https://bjsm.bmj.com/content/50/5/281) — Checked \[Saw2016\]
- [Schoenfeld BJ, Grgic J, Krieger J. How many times per week should a muscle be trained to maximize muscle hypertrophy? A systematic review and meta-analysis. J Sports Sci. 2019;37(11):1286-1295.](https://mennohenselmans.com/training-frequency-2018-meta-analysis-review/) — Located \[Schoenfeld2019freq\]
- [Schoenfeld BJ, Grgic J, Ogborn D, Krieger JW. Strength and hypertrophy adaptations between low- vs. high-load resistance training: a systematic review and meta-analysis. J Strength Cond Res. 2017;31(12):3508-3523.](https://pubmed.ncbi.nlm.nih.gov/28834797/) — Located \[Schoenfeld2017load\]
- [Schoenfeld BJ, Grgic J, Van Every DW, Plotkin DL. Loading recommendations for muscle strength, hypertrophy, and local endurance: a re-examination of the repetition continuum. Sports. 2021;9(2):32.](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC7927075/) — Located \[Schoenfeld2021reps\]
- [Schoenfeld BJ, Ogborn D, Krieger JW. Dose-response relationship between weekly resistance training volume and increases in muscle mass: a systematic review and meta-analysis. J Sports Sci. 2017;35(11):1073-1082.](https://paulogentil.com/pdf/Dose-response%20relationship%20between%20weekly%20resistance%20training%20volume%20and%20increases%20in%20muscle%20mass%20-%20A%20systematic%20review%20and%20metaanalysis.pdf) — Located \[Schoenfeld2017vol\]
- [Schoenfeld BJ, Ogborn D, Krieger JW. Effects of resistance training frequency on measures of muscle hypertrophy: a systematic review and meta-analysis. Sports Med. 2016;46(11):1689-1697.](https://pubmed.ncbi.nlm.nih.gov/27102172/) — Located \[Schoenfeld2016freq\]
- [Schumann M, Feuerbacher JF, Sünkeler M, et al. Compatibility of concurrent aerobic and strength training for skeletal muscle size and function: an updated systematic review and meta-analysis. Sports Med. 2022;52(3):601-612.](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC8891239/) — Located \[Schumann2022\]
- [Silbernagel KG, Thomeé R, Eriksson BI, Karlsson J. Continued sports activity, using a pain-monitoring model, during rehabilitation in patients with Achilles tendinopathy: a randomized controlled study. Am J Sports Med. 2007;35(6):897-906.](https://search.pedro.org.au/search-results/record-detail/18389) — Located \[Silbernagel2007\]
- [Singer A, Wolf M, Generoso L, et al. Give it a rest: a systematic review with Bayesian meta-analysis on the effect of inter-set rest interval duration on muscle hypertrophy. Front Sports Act Living. 2024;6:1429789.](https://www.frontiersin.org/articles/10.3389/fspor.2024.1429789/full) — Checked \[Singer2024\]
- [Warburton DER, Jamnik VK, Bredin SSD, Gledhill N. The Physical Activity Readiness Questionnaire for Everyone (PAR-Q+) and electronic Physical Activity Readiness Medical Examination (ePARmed-X+). Health Fit J Can. 2011;4(2):3-23.](https://hfjc.library.ubc.ca/index.php/HFJC/article/view/103) — Located \[Warburton2011\]
- [Wen D, Utesch T, Wu J, et al. Effects of different protocols of high intensity interval training for VO2max improvements in adults: a meta-analysis of randomised controlled trials. J Sci Med Sport. 2019;22(8):941-947.](https://research.uni-luebeck.de/en/publications/effects-of-different-protocols-of-high-intensity-interval-trainin/) — Located \[Wen2019\]
- [Weston KS, Wisløff U, Coombes JS. High-intensity interval training in patients with lifestyle-induced cardiometabolic disease: a systematic review and meta-analysis. Br J Sports Med. 2014;48(16):1227-1234.](https://bjsm.bmj.com/content/48/16/1227) — Located \[Weston2014\]
- [Williams S, West S, Cross MJ, Stokes KA. Better way to determine the acute:chronic workload ratio? Br J Sports Med. 2017;51(3):209-210.](https://pubmed.ncbi.nlm.nih.gov/27650255/) — Located \[Williams2017\]
- [Wilson JM, Marin PJ, Rhea MR, et al. Concurrent training: a meta-analysis examining interference of aerobic and resistance exercises. J Strength Cond Res. 2012;26(8):2293-2307.](https://search.pedro.org.au/search-results/record-detail/34457) — Located \[Wilson2012\]
- [Zhang X, Li H, Bi S, et al. Auto-regulation method vs. fixed-loading method in maximum strength training for athletes: a systematic review and meta-analysis. Front Physiol. 2021;12:651112.](https://www.frontiersin.org/articles/10.3389/fphys.2021.651112/full) — Checked \[Zhang2021\]
- [Zourdos MC, Klemp A, Dolan C, et al. Novel resistance training-specific rating of perceived exertion scale measuring repetitions in reserve. J Strength Cond Res. 2016;30(1):267-275.](https://openrepository.aut.ac.nz/items/efef3b25-6701-4fb5-bb82-55fcd2a26027/full) — Located \[Zourdos2016\]
