# Review C: Should the app use Jeff Nippard and Jeremy Ethier as references?

Prepared 9 Oct 2026 by the project's fitness-science reviewer, for the Product Owner. Inputs: Rule Registry 1.0.1 (140 approved rules) and the Research Update 1.1 proposal (FL-001..005, STEP-*, SAF-010, CAL-002). This is research only, and nothing in the repository was changed. The Product Owner asked for a final decision, and this review gives one.

## Decision (final)

| Creator | PO label | Decision | In one line |
|---|---|---|---|
| **Jeff Nippard** | "Science", "Learning" | **INCLUDE: pointer to primary evidence + design input only.** Not cited as evidence, and not a tier-6 practitioner reference. | His summaries of research are mostly accurate, he names his studies and he co-authors trials, so he is a good route to primary papers. His own training methods are aimed at young trained bodybuilders and go beyond the evidence for our users. |
| **Jeremy Ethier** | "Workout plans" | **INCLUDE: design input only, narrow scope.** **EXCLUDE** as a source of workout plans, as evidence, and as a source of exercise rankings. | His general advice is mainstream and agrees with our rules. His plans are paid, licence-restricted and built around nutrition. Some numbers he gives are off, his exercise rankings rest on a method that isn't valid, and he sells a protein supplement. |

**Why neither becomes a tier-6 "Expert Practice" citation:**
- Wherever either creator agrees with the evidence, the app can cite the primary study instead.
- Wherever they go beyond the evidence (training to failure, beyond-failure techniques, very low volume, long fixed rests), their practice is built for young, trained lifters, not adults 30+ losing fat with joint issues.
- Citing a creator would add a commercial association and no evidential value. Both are commercial competitors: Nippard co-owns MacroFactor, which launched a workouts app in 2026, and Ethier sells the BWS+ training app.
- The registry already has an expert-practice vocabulary source (Israetel2021).

**What the PO's labels get right and wrong:**
- **"Science/Learning" for Nippard is fair** for your own learning and for finding studies. His videos are not themselves evidence.
- **"Workout plans" for Ethier is the use I reject.** The app builds plans from its own rules (GEN-001, SCH-001), and copying or adapting his plans would break licensing rule D7 and his terms of use.

---

## 1. Method and access

- **Verification labels (as in the project):**
  - **checked**: I opened the page, abstract or full text in this session.
  - **located**: I confirmed the source exists but read only a citation record or a secondary summary, which is named.
  - **unverified**: not confirmed.
- **What I read:**
  - Creator pages: home, about, programme and product pages, articles, terms and disclaimer.
  - Third-party transcripts and summaries of their videos.
  - The app-store listing for BWS+.
  - The primary studies behind 10 specific claims, 5 per creator (Section 2a and 3a).
- **Access problems:**
  - Europe PMC's API refused my first request (HTTP 429, rate limited), so I used no Europe PMC abstracts.
  - PubMed and PMC showed a bot-check page after my first PubMed read.
  - Three PubMed links needed a permission prompt that timed out.
  - jeffnippard.com's programme list page returned an error.
  - Where this happened I used PEDro, journal pages, university repositories or SPONET/LIDA records, and the label says so.
- **YouTube:** I did not watch videos. Nippard's claims come from two auto-generated transcripts (rosetta.to) and third-party write-ups. Transcripts can mishear names, so I matched every cited study to its real paper before judging it.

---

## 2. Jeff Nippard

**Profile (checked):**
- Natural bodybuilder and powerlifter, with a BSc in biochemistry. His about page says he hopes to do a PhD but has not.
- Has coached competitive bodybuilders and powerlifters, and has lectured at conferences and two universities.
- Co-author of at least one peer-reviewed RCT: Wolf et al. 2025, PeerJ (author list checked in the RGU repository).

### 2a. Accuracy: 5 claims spot-checked

| # | His claim (my paraphrase) | Primary source checked | Verdict |
|---|---|---|---|
| N1 | Muscle growth rises with weekly sets in a dose-response across a wide range (he cites "Pelland and colleagues"). He treats about 10–20 sets/muscle/week as the standard range. | **Pelland 2026, Sports Med** (checked; it was a preprint when the registry cited it): 67 studies, 2,058 participants. More volume gave more hypertrophy and strength (posterior probability 100%), with diminishing returns, much steeper for strength. Fractional set counting fitted best. Frequency had negligible effect on hypertrophy. | **Accurate.** |
| N2 | 2–3 sets per exercise are about 40% more effective than 1 set (Krieger 2010). | **Krieger 2010, JSCR** (checked, PEDro): multiple sets gave about 40% larger effect sizes (ES 0.24 for 1 set, 0.34 for 2–3, 0.44 for 4–6). 2–3 sets vs 4–6 sets was not significant. | **Accurate.** |
| N3 | A classic study (Bickel) shows that cutting to 1 set per exercise once a week kept all size and strength for 16 weeks. | **Bickel 2011, MSSE** (checked, PubMed + PEDro): 70 adults, aged 20–35 or 60–75, trained 16 weeks, then 32 weeks of detraining or one-third or one-ninth of the dose. Both reduced doses kept the hypertrophy in the young but **not in the old**, and the authors conclude that older adults need a higher maintenance dose. Strength was largely kept in both age groups. | **Materially incomplete for our audience.** He did not mention the age groups or that older adults lost the muscle gains. For a 30+ app with many users over 60, the omitted half is the important half (see proposal P1). |
| N4 | Training to failure modestly helps hypertrophy; he cites a single-set study ("Herman" 2025) and Robinson 2023/2024. | **Hermann 2025, MSSE** (checked): 42 resistance-trained young adults, 1 set × 9 exercises, twice a week for 8 weeks, failure vs 2 RIR. Both groups grew. Some hypertrophy measures leaned toward failure, but differences were modest, and strength and endurance were similar. **Robinson 2024** (registry: checked in Phase 1; not re-opened). | **Accurate as stated.** His own practice, with nearly every set to failure, goes further than this evidence supports, and the evidence comes from young trained lifters. |
| N5 | The stretched (lengthened) part of the range matters most; lengthened partials match or beat full range of motion. | **Wolf 2023, IJSC meta-analysis** (checked): full vs partial ROM differences were trivial to small (SMD 0.12 favouring full ROM, CI −0.02 to 0.26). Long-length partials had a possible hypertrophy edge (SMD −0.28, CI −0.81 to 0.16, which crosses zero). **Wolf 2025, PeerJ** (checked; Nippard is a co-author): 25 trained completers, 8 weeks; lengthened partials and full ROM gave similar growth (Bayes factors 0.16–0.39, moderate support for no difference). | **Overstated before 2024, now balanced.** Credit: in 2024 he publicly reported his own trial's null result in trained lifters. He still leans toward lengthened partials for beginners, which rests on mixed and mostly untrained-subject evidence. A September 2026 third-party review of three newer studies (Henselmans; studies not identified, so unverified) shows the picture is still mixed. |

**Overall accuracy:** high on what the research says. Weaker on who it applies to: in N3 he generalised from a mixed-age trial, and in N4 and N5 his own practice reaches past the trial populations.

### 2b. Citation practice

- **Good.** In videos he names authors, year and design, and often gives sample sizes and caveats. His beginner programme lists 26 scientific references (product page, checked).
- He takes part in research (Wolf 2025) and reported a result that went against his own preference.
- **Weaknesses:**
  - Programme pages (Min-Max, Fundamentals) claim "backed by science" without citing anything on the page.
  - Population caveats are sometimes dropped (N3).

### 2c. Fit for our audience, and safety

- **Audience:** mainly young, trained, physique-focused lifters.
  - The programmes I checked assume access to machines and cables.
  - Fundamentals is described as unsuitable if you can't do the squat, bench press and deadlift.
  - Min-Max uses 1–2 sets per exercise, often to failure, adding drop sets and myo-reps after week 6. He sends complete beginners to Fundamentals first.
- **Not covered:** I found nothing for adults over 50, joint conditions or fat loss without a diet.
- **Safety:** neither programme page I checked had a medical disclaimer or a "who this is not for" section.
- **Methods that conflict with the app's safety rules for our users:**
  - Failure on most sets.
  - Partial reps continued past failure on pull-ups and rows (per a 2023 third-party write-up).
  - Drop sets and myo-reps.
  - The rules they conflict with are INT-003, SAF-001 conservative mode and SAF-010.

### 2d. Conflicts of interest

- **Programmes:** he sells training programmes, e.g. Min-Max at US$49.99 and Fundamentals at US$39.99 (checked).
- **MacroFactor:** he is **one of five co-equal owners of MacroFactor** (macrofactor.com/team, checked). His role there covers video marketing and the influencer/affiliate programme. MacroFactor sells a nutrition app (2021) and **MacroFactor Workouts (2026)**, a training app in the same space as ours.
- **Research:** co-authoring lengthened-partial research is a non-financial interest in that topic.
- **No supplement:** I found no supplement brand of his own.
- **What this limits:** his recommendations can't raise a rule's confidence, and his app or programmes can't be used as design templates.

### 2e. Licensing (D7)

- **Not allowed:**
  - His programmes, spreadsheets, exercise lists, set/rep tables, cue wording, video, images or thumbnails.
  - His product or brand names in the app.
  - Naming him in the app UI, because that implies endorsement.
- **Allowed:**
  - Non-copyrightable ideas restated in our own words, e.g. "an exercise can be done in a shortened range that keeps the stretched part".
  - The primary studies he points to, cited directly.
- **Licence:** I found no open licence or terms page, so treat everything as all rights reserved.

### 2f. Decision: Nippard

**INCLUDE: pointer to primary evidence + design input only.**

**Pointer: primary studies worth adding to the registry `sources` (all checked this session):**

1. **Bickel2011**: Bickel CS, Cross JM, Bamman MM. Exercise dosing to retain resistance training adaptations in young and older adults. Med Sci Sports Exerc. 2011;43(7):1177-1187. doi:10.1249/MSS.0b013e318207c15d. *checked* (PubMed abstract; PEDro record, score 5/10). Use: P1.
2. **Pelland2026**: Pelland JC, Remmert JF, Robinson ZP, Hinson SR, Zourdos MC. The resistance training dose response: meta-regressions exploring the effects of weekly volume and frequency on muscle hypertrophy and strength gains. Sports Med. 2026;56(2):481-505. doi:10.1007/s40279-025-02344-w. *checked* (SPONET abstract and citation). Use: P3, replacing the preprint `Pelland2024`.
3. **Wolf2023rom**: Wolf M, Androulakis-Korakakis P, Fisher J, Schoenfeld B, Steele J. Partial vs full range of motion resistance training: a systematic review and meta-analysis. Int J Strength Cond. 2023;3(1). doi:10.47206/ijsc.v3i1.182. *checked* (journal page abstract). Use: P2.
4. **Wolf2025lp**: Wolf M, Androulakis-Korakakis P, Piñero A, Mohan AE, Hermann T, Augustin F, Sapuppo M, Lin B, Coleman M, Burke R, Nippard J, Swinton PA, Schoenfeld BJ. Lengthened partial repetitions elicit similar muscular adaptations as full range of motion repetitions during resistance training in trained individuals. PeerJ. 2025;13:e18904. doi:10.7717/peerj.18904. *checked* (PeerJ abstract; authors from RGU repository). Use: P2. Note that a creator co-authored it.
5. **Hermann2025**: Hermann T, Mohan AE, Enes A, Sapuppo M, Piñero A, Zamanzadeh A, Roberts M, Coleman M, Korakakis PAA, Wolf M, Refalo M, Swinton PA, Schoenfeld BJ. Without fail: muscular adaptations in single set resistance training performed to failure or with repetitions-in-reserve. Med Sci Sports Exerc. 2025 (ahead of print). doi:10.1249/MSS.0000000000003728. *checked* (RGU repository abstract). Use: P6.
6. **Krieger2010**: Krieger JW. Single versus multiple sets of resistance exercise for muscle hypertrophy: a meta-analysis. J Strength Cond Res. 2010;24(4):1150-1159. *checked* (PEDro abstract). Optional supporting evidence for VOL-003 (multiple sets per exercise).

**Design input (no citation):**
- **Effort teaching:** his beginner programme rates only the last set's effort and uses simple linear progression, which supports the approach already in INT-006 and PROG-002.
- **Time-efficient formats:** a 45-minute, 1–2-hard-sets-per-exercise layout is a useful reference for the express session (ADH-004, TIME-002). Our own effort and failure rules stay in force.
- **Plain-language explanations of the stretch:** useful for writing original coaching text on range of motion (MOB-004, P2).

---

## 3. Jeremy Ethier

**Profile (checked):**
- Describes himself as a kinesiologist with NASM and FMS trainer certifications. The about page names no institution.
- Founded Built With Science. The disclaimer says he is not a doctor or a dietitian.
- A podcast listing (Feb 2026) says he co-authored a SportRxiv preprint on lengthened vs shortened training. I could not identify or open it (**unverified**).

### 3a. Accuracy: 5 claims spot-checked

| # | His claim (my paraphrase) | Primary source checked | Verdict |
|---|---|---|---|
| E1 | 10–20 sets per muscle per week is the target. A later review found no clear extra benefit above about 20. | **Schoenfeld 2017, J Sports Sci** (checked, full-text PDF at the registry's own URL): graded dose-response, each extra set adding ES 0.023. **Baz-Valle 2022, J Hum Kinet** (checked, PubMed abstract): 12–20 vs >20 sets, no difference for quads and biceps, triceps favoured higher; young trained men only. | **Accurate**, with the population caveat that this is young trained men. |
| E2 | 5–9 weekly sets give about 80% of the growth of 10+ sets. | **Schoenfeld 2017** results section (checked): 5–9 sets gave 6.6% (ES 0.378) vs 10+ sets 9.8% (ES 0.520). | **Overstated.** That is about two-thirds by percentage gain and about three-quarters by effect size, not 80%. Minor, but it shows numbers drift from the sources. |
| E3 | Rest at least 3 minutes on heavy compound lifts and about 2 minutes on isolation lifts for growth (he cites Schoenfeld 2016). | **Schoenfeld 2016, JSCR** (located: citation from a university repository, design from the lead author's own blog): 21 trained young men, 1 vs 3 min. Strength gains were greater with 3 min; muscle thickness only tended to be greater; volume load was not equated. **Singer 2024, Front Sports Act Living** (checked): more than 60 s may give a small benefit, with **no appreciable extra difference beyond about 90 s**. | **Too long as a general rule.** It rests on one non-equated study in young trained men, and the newer meta-analysis doesn't support it. The app's REST-002 and REST-003 are better aligned. |
| E4 | Exercises can be ranked by EMG, e.g. 16 chest exercises measured on three of his own staff. | **Vigotsky 2022, Sports Med** (checked, repository abstract): acute surface EMG amplitude is **not a validated predictor of hypertrophy**, and studies that rank exercises this way should be treated with scrutiny. | **Not valid evidence.** Three in-house subjects, one set of 5 reps per exercise, with acute EMG used as a stand-in for growth (BOXROX write-ups, checked). |
| E5 | For fat loss, do mostly low-intensity, low-impact cardio (incline walking, cycling), keep HIIT to 1–2 sessions a week, keep daily steps steady and keep lifting to protect muscle. | **Viana 2019, BJSM** (delta: checked abstract; not re-opened) and the Research Update 1.1 evidence: HIIT is not superior to continuous training for fat loss. **Rosenbaum 2003** (checked via a reference abstract), behind his claim that muscles become more efficient after weight loss: efficiency rose after 10% weight loss and explained 35% of the drop in activity energy use. | **Accurate in direction**, and it matches FL-003. His specific calorie figures were not verified, and they concern energy balance, which is out of the app's scope. |

**Overall accuracy:**
- His mainstream programming advice is sound.
- **Specific numbers sometimes drift** (E2).
- **Some advice rests on single studies or invalid methods** (E3, E4).
- **His fat-loss content relies heavily on diet, protein and protein timing,** which the app does not cover (FL-001: no food features).

### 3b. Citation practice

- **Mixed.** His articles hyperlink to PubMed and journal pages, which is better than most creators. But:
  - Authors, years and journals are often missing.
  - Some links can't be matched to the claim without opening them. Two PubMed IDs (12423180, 30289872) I could not open (unverified).
- **Product pages cite nothing.** For example, the Beginner Shred page calls its meal plans "scientifically proven" and says many members healed past injuries (product page, checked).

### 3c. Fit for our audience, and safety

- **Closer to our audience on paper:** beginners, fat loss, home or gym options, a "joint" bonus module and ranked exercise alternatives.
- **But not built for it:**
  - The programmes are built around a calorie deficit and meal plans.
  - I found no guidance for older adults.
  - The product page claims injury healing, which is the kind of claim the app must never make (Research Update 1.1, regulatory note; SAF-009).
- **Disclaimers:** the site has a proper medical disclaimer (checked), but the product page I checked did not repeat it.

### 3d. Conflicts of interest

- **What he sells (checked):**
  - The **BWS+ subscription app**: US$29.99/month or US$189/year, plus US$49 programme unlocks. Its AI assistant answers as if you were talking to him, and it includes step tracking and progressive-overload plans, so it is a direct competitor to our app.
  - Paid programmes from US$47 to US$249. The page lists inconsistent prices.
  - Coaching, custom workout and meal plans, a cookbook and resistance bands.
  - **His own whey protein isolate.**
- **The protein conflict:** his articles promote protein intake and protein timing while he sells protein. His disclaimer does not say whether links are affiliate or sponsored.
- **Licence terms:** his terms of use allow personal use only. They forbid commercial exploitation and **using the services to build a competing product** (checked).

### 3e. Licensing (D7)

- **Not allowed:**
  - Any BWS programme, app content, workout plan, exercise ranking, tutorial video, image or text.
  - Subscribing to BWS+ or buying a programme to mine it for our design. His terms forbid this, and it would also break D7.
  - Brand names ("Built With Science", "BWS", "Shred", "Build") or his name in the app.
- **Allowed:** general ideas from his free public articles, restated in our own words, and the primary studies themselves.
  - Example: putting easy incline walking after a lifting session.

### 3f. Decision: Ethier

**INCLUDE: design input only (narrow). EXCLUDE as a source of workout plans, as evidence, and as a source of exercise rankings.**

- **Design input (no citation, free public content only):**
  - His clear structure for explaining volume and effort to beginners.
  - The idea of a short, low-impact walking finisher after strength work. This matches FL-003 and PO decision 2 on cardio machines.
- **No new primary studies are worth adding from him:**
  - The relevant ones are already in the registry (Schoenfeld2017vol), or a newer registry source supersedes them (his rest study, Schoenfeld 2016, is superseded by Singer2024).
  - Or they are nutrition studies (out of scope).
  - Or they concern young trained men (Baz-Valle 2022), which adds nothing for our audience.
- **His workout plans:** excluded, for licensing, conflict-of-interest and audience reasons. The app's plans come from the rule engine.

---

## 4. Their programming vs the app's rules

### 4.1 Where they agree with the app

| Topic | Nippard | Ethier | App rule(s) |
|---|---|---|---|
| Weekly volume in the region of 10–20 hard sets/muscle, dose-response with diminishing returns | yes | yes | VOL-003 (beginner 8–12, intermediate 10–16, advanced 12–20; caps), VOL-006, PROG-007 |
| Only near-failure sets build muscle effectively | yes | yes | VOL-001 (hard set ≤4 RIR), INT-002 |
| Failure belongs on stable machine and cable exercises | partly: Min-Max, his to-failure programme, is built on machines and cables | not checked | INT-003 (FailureSafe list, ≤2 exercises, beginner lockout) |
| Each muscle trained at least twice a week | not checked | yes | FREQ-002 |
| Beginners: simple session-to-session progression and simple effort ratings | yes (Fundamentals) | not checked | PER-001 (beginners), PROG-001, PROG-002, INT-006 |
| Short, low-set sessions are a valid option | yes (Min-Max, about 45 min) | yes (30-min bonus) | ADH-004, TIME-002, VOL-008 |
| Longer rest for heavy compounds | not checked | yes | REST-001 |
| Stalled: add about 2 sets; poor recovery: drop sets; change one thing and wait 3–4 weeks | — | yes | VOL-006, PROG-007 (+2 sets/week cap), VOL-004, DEL-001/002 |
| Lift through a full range with emphasis on the stretched position | yes | yes | MOB-004 |
| Every exercise has substitutes | yes | yes | SUB-001, SUB-002 |
| Fat loss: mostly easy low-impact cardio, HIIT 1–2/week, steady daily steps, lifting to protect muscle | — | yes | FL-001, FL-003, STEP-001, CON-004, PH-001 |
| Fractional counting of indirect sets | not checked (he cites Pelland, which supports it) | **no**, see 4.3 | VOL-002 |

### 4.2 Evidence-backed improvements to consider

All of these go through change control as a change request with PO sign-off. None is applied by this review.

**P1. AGE-001 (1.0.0 → 1.1.0, MINOR): from age 60, strength volume never drops to "maintenance".**
- **Problem:**
  - VOL-003 maintenance is 4 fractional sets/muscle/week for beginners and intermediates.
  - The engine uses it in Conditioning blocks (`Blueprint.dose`: maint…start), the Review block and holiday maintenance weeks (PER-005).
  - The fat-loss year (FL-004) has two Conditioning blocks.
  - Bickel 2011's reduced doses would be about 3 and 9 sets/week if the training dose was 27 sets/week (Nippard's description; the abstract gives only the fractions). That brackets our 4, and neither kept the muscle gained in 60–75-year-olds, while both did in young adults.
- **Change:** add to AGE-001 `age_60`: `"strength_volume_floor": "block_start"`. For users 60+, every week that would plan maintenance volume uses block_start instead (beginner 6, intermediate 8, advanced 10), still split over ≥2 exposures (FREQ-002). That adds about 2–4 sets per muscle per week in those weeks. The time cost in Conditioning blocks should be checked in the Phase 4 simulation, which already includes one fat-loss user per age band.
- **Evidence:** Bickel2011 (checked).
- **Confidence:**
  - Direction (older adults need more than young): **Limited Evidence**. It is one RCT, PEDro 5/10, thigh muscles only, measured at myofiber level.
  - The block_start value: **Expert Practice**.
  - Strength was largely kept, so the change protects muscle mass, which is the explicit aim of FL-001 ("keep muscle").
- **Impact:** Blueprint.dose for Conditioning and Review, the PER-005 maintenance week, and at least 2 new tests named by rule ID.

**P2. MOB-004 (1.0.0 → 1.1.0, MINOR): say what a "shorter range" should keep, and key the rule to range-of-motion evidence.**
- **Problem:**
  - SAF-003 offers a "shorter range" for joint pain ≤3/10, and SAF-010 (arthritis, low back pain) limits range, but no rule says which part of the range to keep or how much training effect is lost.
  - MOB-004's only evidence (Afonso2021) is about strength training vs stretching for flexibility, not range of motion for muscle growth.
- **Change:** add this to the statement: *"When pain (SAF-003), a limitation tag or a SAF-010 entry restricts range, use the largest pain-free range. Where either end of the range could be kept, keep the part where the target muscle is lengthened. A reduced-range set still counts as a hard set (VOL-001)."* Add Wolf2023rom, Wolf2025lp and Larsen2025 to the evidence list.
- **Evidence (checked):**
  - **Wolf 2023:** partial vs full ROM differences trivial to small. Partial ROM is a reasonable choice when injury prevents full ROM, and long-length partials possibly have an edge.
  - **Larsen 2025, J Sports Sci:** 23 trained lifters, leg press to about 100° vs maximum knee bend (about 154°). Quad growth was similar (BF 0.14–0.22), so limiting depth, as a knee-arthritis user might need, costs little.
  - **Wolf 2025:** in trained lifters, lengthened partials matched full ROM.
- **Confidence:**
  - "A reduced range costs little hypertrophy": **Moderate Evidence**.
  - "Prefer the lengthened end": **Limited Evidence**. It rests on Wolf 2023's subgroup, whose CI crosses zero, and on Pedrosa 2022, which is only located.
  - Full ROM stays the default.

**P3. VOL-002, VOL-003, VOL-005, FREQ-002, FREQ-004 (PATCH, evidence list only): replace the preprint `Pelland2024` with the peer-reviewed `Pelland2026`.**
- **Why:**
  - The registry marks `Pelland2024` as "NOT PEER REVIEWED", and VOL-002's uncertainty note says "Preprint evidence".
  - The published version (checked) confirms fractional counting fitted best and that frequency has negligible effect on hypertrophy with volume equated. Both match VOL-002 and FREQ-002 as written.
- **Change:** update VOL-002's uncertainty to say the evidence is a peer-reviewed meta-regression, and that participants' mean age was about 25, so transfer to 30+ is assumed.
- **Confidence:** unchanged (Moderate).

**P4. Source record (PATCH): `Schoenfeld2017vol` verification "located" → "checked".** I opened the full-text PDF at the URL the registry already records.

**P5. FL-001 (PATCH, evidence list): add Murphy2022.**
- **Source:** Murphy C, Koehler K. Energy deficiency impairs resistance training gains in lean mass but not strength: a meta-analysis and meta-regression. Scand J Med Sci Sports. 2022;32(1):125-137. doi:10.1111/sms.14075. *checked* (LIDA abstract; authors from a search listing).
- **Findings:** an energy deficit impaired lean-mass gains (ES −0.57) but not strength gains, and a deficit of about 500 kcal/day prevented lean-mass gains.
- **Why it matters:** it supports FL-001's "keep muscle", not "build muscle", wording, and it justifies never echoing creators' "build muscle while cutting" marketing.
- **Confidence:** Moderate Evidence. No food feature is implied.

**P6. INT-003 and ADH-004 (PATCH, evidence lists): add Hermann2025.**
- **INT-003:** a single set to failure gives at most a modest hypertrophy edge over 2 RIR in trained adults, with similar strength. This supports keeping failure optional and limited.
- **ADH-004:** single-set full-body sessions grew muscle in trained adults over 8 weeks, which supports the express option.
- **Confidence:** unchanged.

### 4.3 What the app should NOT adopt

| Recommendation | Who | Why not for our users | Rule that stands |
|---|---|---|---|
| Taking most working sets to failure, plus partial reps past failure, drop sets and myo-reps | Nippard (Min-Max, own training) | The benefit is modest and comes from young trained lifters (Hermann 2025; Robinson 2024). It adds fatigue and technique risk for beginners, older users and people with joint conditions, and the creator himself sends beginners to another programme. | INT-003, SAF-001 conservative mode, SAF-004, SAF-010 |
| Lengthened partials as a default intensity technique | Nippard | No advantage in trained lifters (Wolf 2025), the meta-analysis subgroup is inconclusive (Wolf 2023), and newer studies are mixed. Used only as the reduced-range preference in P2. | MOB-004 (with P2) |
| "Once-a-week single sets keep all your gains" | Nippard (from Bickel 2011) | False for 60–75-year-olds in the very study cited (Bickel 2011). | VOL-003 + P1 |
| Counting indirect work as a full set for the secondary muscle | Ethier | The peer-reviewed dose-response analysis found fractional counting fitted best (Pelland 2026). Full counting would overstate volume for compound-heavy plans. | VOL-002 (0.5 for secondary) |
| At least 3 min rest on all heavy compounds for growth; about 2 min on isolation | Ethier | No appreciable hypertrophy difference beyond about 90 s (Singer 2024). ACSM 2026 says rest did not consistently change outcomes. Long fixed rests cost time that 30+ users need for cardio minutes (FL-002). | REST-001 to REST-003, REST-007 |
| Ranking exercises by EMG | Ethier | Acute EMG amplitude is not a validated predictor of growth (Vigotsky 2022), and his tests used three in-house subjects. **Recommended decision-log entry:** library stimulus scores (SUB-002 "objective") are never derived from EMG rankings. | SUB-002, library |
| Calorie targets, protein targets and timing, meal plans, whey, "reverse diets" | Ethier (and MacroFactor for Nippard) | Out of scope (FL-001: no food features), plus a direct commercial conflict. | FL-001 |
| Marketing claims: injuries "healed", "scientifically proven" meals, "build muscle while cutting" | Ethier; Nippard (Min-Max page) | Unsupported or overstated. Treatment-style claims would move the app toward medical-device territory (Research Update 1.1, regulatory note). | SAF-009, COACH-001, FL-001 |
| Their exercise demos, programme templates, rankings, cue text, images or names | both | D7, copyright, and Ethier's terms (no competing products). | D7, D-061 |

---

## 5. Proposed usage policy

This text is for DECISIONS.md, to be entered by the PO or engineering, not by this review.

> **D-0xx Creator content (Nippard, Ethier and similar).** YouTube and other commercial creators are never evidence sources and are not added to the registry `sources`. A creator may be used: (1) as a **pointer**: the primary study they cite is read, labelled and cited directly; (2) as **design input**: general, non-copyrightable ideas from free public content, rewritten in our own words, with no names, brands, text, images, video or programme structures. Purchased programmes and subscription apps are never used. Each design input is logged here with the idea taken and the date. Applied: Nippard = pointer + design input; Ethier = design input only; Ethier's workout plans excluded.

---

## 6. Sources (all opened this session unless stated)

### Creator and commercial pages

| Source | What it was used for | Label |
|---|---|---|
| jeffnippard.com (home); jeffnippard.com/about/ | Products, credentials | checked |
| jeffnippard.com/products/the-min-max-program | Programme design, price, no disclaimer | checked |
| jeffnippard.com/products/fundamentals-hypertrophy-program | Beginner programme, 26 references, price | checked |
| jeffnippard.com/collections/programs | — | not read (fetch error) |
| macrofactor.com/team/ (redirected from strongerbyscience.com) | Nippard is one of five co-equal owners; MacroFactor Workouts 2026 | checked |
| rosetta.to transcripts: "Everyone Thinks I'm Wrong About Training" (24 Sep 2024); "I Cut The Number Of Sets I Do In Half" (13 Oct 2025) | Nippard's claims N1–N5 | checked (auto transcripts, third party) |
| fitnessvolt.com (Nov 2023) on Nippard's lengthened partials | Partial reps past failure | checked (third party) |
| podwise.ai: Milo Wolf critique of a Nippard programme (1 Mar 2026) | Context | checked (third party) |
| builtwithscience.com (home); /about-us/; /terms-of-use/; /disclaimer/ | Products incl. whey; credentials; terms; disclaimer | checked |
| builtwithscience.com/fitness-tips/how-many-sets-per-muscle-group-per-week/ | E1, E2 | checked |
| builtwithscience.com/workouts/rest-between-sets | E3 | checked |
| builtwithscience.com/fitness-tips/cardio-to-lose-belly-fat/ | E5 | checked |
| builtwithscience.com/fitness-tips/lose-fat-gain-muscle/ | Recomposition, nutrition focus | checked |
| builtwithscience.com/programs/beginner-shred-program | Programme, prices, injury claim | checked |
| apps.apple.com: BWS+ (id6446000532) | App features, prices, AI assistant | checked |
| BOXROX write-ups of Ethier EMG videos (Nov 2022; May 2024) | E4 | checked (third party) |
| coachway.io (Sep 2026) summary of Ethier's programming principles | Agreement table | checked (third party) |
| castfox.net podcast listing (1 Feb 2026) | Ethier preprint co-authorship | checked; the preprint itself is **unverified** |
| mennohenselmans.com post (15 Sep 2026) on three new lengthened-partial studies | Context for N5 | checked (third party); the studies are **unverified** (not identified) |

### Primary research

| Key | Citation | Label |
|---|---|---|
| Bickel2011 | Bickel CS, Cross JM, Bamman MM. Med Sci Sports Exerc. 2011;43(7):1177-1187. doi:10.1249/MSS.0b013e318207c15d | checked (PubMed + PEDro) |
| Pelland2026 | Pelland JC, Remmert JF, Robinson ZP, Hinson SR, Zourdos MC. Sports Med. 2026;56(2):481-505. doi:10.1007/s40279-025-02344-w | checked (SPONET) |
| Krieger2010 | Krieger JW. J Strength Cond Res. 2010;24(4):1150-1159 | checked (PEDro) |
| Hermann2025 | Hermann T, et al. (13 authors, listed in 2f). Med Sci Sports Exerc. 2025, ahead of print. doi:10.1249/MSS.0000000000003728 | checked (RGU repository) |
| Wolf2023rom | Wolf M, Androulakis-Korakakis P, Fisher J, Schoenfeld B, Steele J. Int J Strength Cond. 2023;3(1). doi:10.47206/ijsc.v3i1.182 | checked (journal page) |
| Wolf2025lp | Wolf M, et al. (13 authors incl. Nippard J). PeerJ. 2025;13:e18904. doi:10.7717/peerj.18904 | checked (PeerJ + RGU) |
| Larsen2025 | Larsen S, Wolf M, Schoenfeld BJ, Sandberg NØ, Fredriksen AB, Kristiansen BS, van den Tillaar R, Swinton PA, Falch HN. Knee flexion range of motion does not influence muscle hypertrophy of the quadriceps femoris during leg press training in resistance-trained individuals. J Sports Sci. 2025;43(10):986-994. doi:10.1080/02640414.2025.2481534 | checked (RGU repository) |
| Schoenfeld2017vol | Schoenfeld BJ, Ogborn D, Krieger JW. J Sports Sci. 2017;35(11):1073-1082 | checked (full-text PDF at the registry URL); upgrades the registry's "located" |
| BazValle2022 | Baz-Valle E, Balsalobre-Fernández C, Alix-Fages C, Santos-Concejero J. A systematic review of the effects of different resistance training volumes on muscle hypertrophy. J Hum Kinet. 2022 (volume and pages not confirmed); PMID 35291645 | checked (PubMed abstract) |
| Singer2024 | Singer A, Wolf M, Generoso L, et al. Front Sports Act Living. 2024;6:1429789 | checked (re-opened this session) |
| Schoenfeld2016rest | Schoenfeld BJ, Pope ZK, Benik FM, et al. Longer interset rest periods enhance muscle strength and hypertrophy in resistance-trained men. J Strength Cond Res. 2016;30(7):1805-1812. doi:10.1519/JSC.0000000000001272 | located (citation from Univ. Navarra repository; design from the lead author's blog) |
| Vigotsky2022 | Vigotsky AD, Halperin I, Trajano GS, Vieira TM. Longing for a longitudinal proxy: acutely measured surface EMG amplitude is not a validated predictor of muscle hypertrophy. Sports Med. 2022;52(2):193-199. doi:10.1007/s40279-021-01619-2 | checked (Politecnico di Torino repository) |
| Murphy2022 | Murphy C, Koehler K. Scand J Med Sci Sports. 2022;32(1):125-137. doi:10.1111/sms.14075 | checked (LIDA abstract; authors from a search listing) |
| Rosenbaum2003 | Rosenbaum M, Vandenborne K, et al. Effects of experimental weight perturbation on skeletal muscle work efficiency in human subjects. Am J Physiol Regul Integr Comp Physiol. 2003 (volume and pages not confirmed) | checked (abstract via a JCI reference listing) |
| Pedrosa2022 | Pedrosa GF, Lima FV, Schoenfeld BJ, et al. Partial range of motion training elicits favorable improvements in muscular adaptations when carried out at long muscle lengths. Eur J Sport Sci. 2022 | located (Examine citation; abstract not opened) |
| Maeo2021 | Maeo S, Huang M, Wu Y, et al. Greater hamstrings muscle hypertrophy but similar damage protection after training at long versus short muscle lengths. Med Sci Sports Exerc. 2021;53(4):825-837 | located (figures from the sci-sport.com secondary summary; PubMed blocked). Candidate for library exercise choice (seated over lying leg curl); verify before use. |
| Maeo2023 | Maeo S, Isaka T, et al. Triceps brachii hypertrophy is substantially greater after elbow extension training performed in the overhead versus neutral arm position. Eur J Sport Sci. doi:10.1080/17461391.2022.2100279 | located (Ritsumeikan University news page). Candidate for library; verify before use. |
| Robinson2024, Viana2019 | As in the registry and the 1.1 delta | registry/delta: checked; not re-opened |
| PMIDs 12423180, 30289872 (cited by Ethier) | Not identified | unverified (PubMed blocked) |
