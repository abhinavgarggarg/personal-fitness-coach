# Review D: Four creator resources as references for workout design

**Resources reviewed:** Nerd Fitness, Yoga With Adriene, Fitness Blender and Heather Robertson

Prepared 9 Oct 2026 by the project's fitness-science reviewer for the Personal Fitness Coach (offline Android training engine). Research only: nothing in the repository was changed.

**Status: final decision, as the Product Owner asked.** The four decisions in Section 1 are binding for the registry and library work. Section 7.2 lists the rule changes I recommend. They go through normal change control (version bump, tests, PO sign-off) like any other change.

**How to read this report**

- Source keys in square brackets, e.g. [Singh2024], point to Section 10. Each source there has a full citation and a verification label:
  - **checked:** I opened the page, abstract or full text in this session. Numbers shown come from that page.
  - **located:** I confirmed the source exists but could not open its abstract. No numbers are taken from it.
  - **unverified:** from memory only. Used only where marked.
- Confidence labels follow the registry scale: High Evidence / Moderate Evidence / Limited Evidence / Expert Practice / Product Rule.
- All descriptions of the creators' content are my own paraphrase. No routine, text, image or video of theirs is reproduced here, and none may be reproduced in the app (D7).
- **Access problems this session:**
  - Bash egress to the creator domains, Europe PMC, Crossref and PubMed was refused by the proxy (403).
  - Through WebFetch:
    - Europe PMC rate-limited me (HTTP 429) after two searches.
    - PMC returned a bot-check page.
    - ScienceDirect refused (robots).
    - Wiley (Lally 2010) and PNAS returned errors.
    - Some creator pages failed: yogawithadriene.com/about, the Nerd Fitness and Fitness Blender terms pages, and FB Plus pricing.
  - Where a source was read through a repository or a summary page, its label says so.

---

## 1. Decisions at a glance

| # | Resource (PO label) | **Decision** | The app may take | The app must not take |
|---|---|---|---|---|
| 1 | **Nerd Fitness** ("Habit building") | **INCLUDE: design input only** + **INCLUDE: pointer to primary evidence**. Never cited as evidence, not even as tier 6. | Ideas for habit features: one small habit at a time, visible tracking, "a missed day doesn't undo it", welcome-back framing. Also the primary studies it links to [Gardner2012] [WingJeffery1999] and the stronger studies behind its approach (§3.6). | Any text, routine, image or tracking sheet. Its unsourced "diet is ≥80% of results" figure. All nutrition products. Penalty-based accountability. |
| 2 | **Yoga With Adriene** ("Mobility & stress management", "Recovery") | **INCLUDE: design input only**. The primary yoga evidence in §4.6 is added on its own merits, not via the channel. Never cited. | Format ideas for an original, optional **calm mobility session**: short, self-paced, permission to modify, breathing to finish, a non-judgemental tone. | Any video, sequence, wording or imagery. Detox, weight-loss and "trim and tone" claims. "Healing" back-pain claims. The idea that yoga speeds physiological **recovery**. Counting yoga as cardio. |
| 3 | **Fitness Blender** ("Workouts") | **INCLUDE: design input only**. Never cited. | Format ideas for **low-impact, no-jump home cardio and circuits**: scaling by range of motion and speed, "move at your own pace, not the instructor's", and warm-up and cool-down always present. Also the practice of labelling high-impact work as not for beginners. | Any video, routine, text or image. Per-workout **calorie-burn estimates**. The "elevated metabolism for hours" (afterburn) claim. "Fat burning" framing. Meal plans. High-impact jump HIIT as a default. |
| 4 | **Heather Robertson** ("Workouts") | **EXCLUDE.** | Nothing. What is sound in it (dumbbell strength, low-impact variants) is generic and already covered by the registry's primary sources and by item 3. | Everything. Its signature format (dense 30 s on / 10 s off bodyweight HIIT with jumps, "no repeats", 10-minute finishers with no warm-up) conflicts with HIIT-002, HIIT-003, CON-004, FL-003, WU-003 and ADH-003. |

**Why none of the four becomes a tier-6 "practitioner reference":**

- Tier 6 is for expert practice that can be traced to a named, qualified source with a documented method, such as the RAMP warm-up book [Jeffreys2019] already in the registry.
- None of the four publishes its methods or rationale in a citable form. Certifications are unnamed (Heather Robertson; Nerd Fitness coaches) or general ("certified in Austin" for Adriene Mishler).
- Three of the four make claims that the evidence I checked contradicts:
  - "detox" twists (Yoga With Adriene)
  - afterburn and calorie numbers (Fitness Blender)
  - "burn fat, increase metabolism" (Heather Robertson)
- Citing them would add brand association and no evidential value.
- The app is offline (no INTERNET permission), so it cannot link to them anyway. It should not name them in-app, which also avoids any implied affiliation.

---

## 2. Method

1. **Read the project context.** I read Registry 1.0.1 (ADH, MOB, WU, HIIT, CON, MOD, AGE, SAF, RDY, BW, CORE, LOAD and TIME rules), the 1.1.0 proposed delta (FL-001…005, STEP, SAF-010), research report 1.1, reviews A and B, and the engine's `Adherence.kt` and `Mobility.kt`. The aim was to check what already exists, e.g. the `equipmentToday` input and the MOB-005 off-day routine.
2. **Opened 3–6 pages per creator,** plus two third-party pages for Heather Robertson. For each I recorded:
   - who runs it
   - credentials
   - paid products
   - specific claims
   - citation practice
   - impact and progression content
3. **Spot-checked 4–6 specific claims or approaches per creator** against primary research I opened this session (Sections 3–6).
4. **Compared everything with the registry** (Section 7). I propose only changes backed by primary research I checked.

---

## 3. Nerd Fitness (nerdfitness.com, Steve Kamb)

### 3.1 What it is; conflicts of interest

- **Who runs it:** a company founded in 2009 by Steve Kamb, with a full-time team. The About page lists **no degree or certification for the founder**. It says the coaching programme has "10+ credentialed trainers" without naming the credentials [NF-about].
- **Paid products** seen on pages I opened:
  - 1-on-1 Online Coaching. It includes custom workouts and **nutrition guidance** in an app. Prices appear only as images; the page states no contracts and a 30-day money-back guarantee [NF-coach].
  - The "Nerd Fitness Journey" app, with a free trial [NF-habits].
  - Nerd Fitness Academy (one payment, lifetime access) [NF-old].
  - "Nerd Fitness Prime", a paid nutrition and mindset system [NF-bw].
  - Amazon book links, at least one with an affiliate tag [NF-habits].
- **Content as a sales funnel:** every article I opened ends in coaching calls-to-action. This is a strong commercial conflict; the content funnels readers to paid coaching.

### 3.2 Spot-checks

| # | Claim or approach (paraphrased) | What the primary evidence says | Verdict |
|---|---|---|---|
| N1 | **How long habits take:** it gives no number of days; it says results take longer than a few weeks and suggests trying one habit for 30 days [NF-habits] | Automaticity in Lally's cohort plateaued at a median of **66 days, range 18–254** [Singh2024, reporting Lally2010]. A 2024 meta-analysis of 20 studies (2,601 people) found **medians of 59–66 days and means of 106–154 days, range 4–335**. Most studies were at high risk of bias [Singh2024]. A 4-year gym dataset found gym habits take **months**, versus weeks for hospital handwashing [Buyalskaya2023]. The press release puts the gym figure at about six months on average [Buyalskaya-PR]. | **Partly aligned.** Avoiding a fixed short number is right. A 30-day trial is fine as a commitment device but is too short as a habit expectation. |
| N2 | **Missing one day doesn't ruin it, but missing two in a row can** [NF-habits]. It links a research summary, probably [Gardner2012] (PMC ID not opened). | One missed opportunity did not materially set back habit formation; gains soon resumed [Gardner2012]. **No study I found tested "two in a row".** In the gym dataset, the time since the last visit predicted attendance for most gym-goers [Buyalskaya-PR]. | **First half supported. Second half unsupported as a rule**, though it is directionally plausible. |
| N3 | **Start with one small, done/not-done habit; keep it simple; stack habits** [NF-habits] | Simple, self-chosen actions repeated in a stable context are the core habit advice [Gardner2012]. In 111 new gym members, **low behavioural complexity**, consistency, environment and positive affective judgements predicted habit growth. At least **4 sessions a week for 6 weeks** was the minimum associated with an exercise habit [Kaushal2015]. Morning and self-selected habits were stronger [Singh2024]. | **Supported.** |
| N4 | **Track daily (e.g. a calendar with an X)** [NF-habits] | Across 138 experiments (19,951 people), prompting progress monitoring improved goal attainment, **d+ 0.40 (0.32–0.48)**. Effects were larger when progress was **physically recorded** or reported to others [Harkin2016]. In 122 evaluations (44,747 people), **self-monitoring combined with another self-regulation technique** had larger effects than other interventions: effect size **0.42 vs 0.26** [Michie2009]. | **Supported.** |
| N5 | **Recruit social support.** It links a weight-loss trial and reports 66% vs 24% maintenance [NF-habits]. | Correctly reported. People recruited with friends and given social-support training kept their full weight loss more often: **66% vs 24%** for those recruited alone with standard treatment (N = 166) [WingJeffery1999]. Note: this was a diet-plus-exercise weight-loss programme, not exercise alone. | **Accurate citation, indirect for this app** (offline, single user). |
| N6 | **Diet is "at least 80%" of results and training 10–20%** [NF-bw] | No source given. Exercise without diet does give modest weight change (about 2–3 kg at 150–200 min a week; review A, [Jayedi2024], not re-checked here). The 80/20 split is a slogan, not a measured quantity. | **Unsupported number; do not adopt** (and the app gives no diet advice). |

Other approaches noted but not checked:
- "cue–routine–reward" (from a popular book)
- temptation bundling
- money-at-stake penalties

### 3.3 Citation practice

- The **habit article links two studies** through footnotes, with no authors, year or journal in the text. Both are real and broadly correctly described (N2, N5).
- The **beginner bodyweight article cites nothing** [NF-bw].
- The **"too old" article links two papers** (a JAMA Network Open cohort and a PMC review) and otherwise relies on anecdotes [NF-old].
- **Overall:** occasional, unformatted links; most claims unsourced; heavy reliance on personal stories.

### 3.4 Fit for this audience; safety

**Strengths:**
- Beginner-friendly. The beginner circuit is full-body, done 2–4 times a week, with progressive overload (reps → harder variation → tempo) and regressions.
- A lower-impact swap is offered for its one jumping move [NF-bw].
- This matches BW-001 and CON-004.

**Weaknesses:**
- No intensity cue beyond "rest rather than lose form".
- No screening and no pain guidance.
- "Too old" content is motivational rather than specific [NF-old].
- Penalty-based accountability ("pay money to a cause you dislike") conflicts with COACH-001 (no guilt or punishment).

### 3.5 Licensing (D7)

- **No open licence** appeared on any page I opened. Its terms page could not be opened. Treat all content as all rights reserved.
- **May take:** general ideas (one small habit, visible tracking, welcome back after a miss) and the cited primary studies.
- **May not take:** text, workout lists or rep schemes, PDFs or tracking sheets, images or GIFs.

### 3.6 Decision

**INCLUDE: design input only + pointer to primary evidence.** It is not cited as evidence.

**Primary studies worth adding to the registry** (all **checked** unless marked):

| Key | Why |
|---|---|
| [Gardner2012] | Habit advice; one missed opportunity doesn't derail habit formation |
| [Singh2024] | Best current estimate of habit timelines and their spread |
| [Kaushal2015] | Exercise-specific: consistency and simplicity; ≥4 sessions a week for 6 weeks |
| [Buyalskaya2023] | Large objective gym dataset: months, not weeks; no "magic number" |
| [Milkman2021] | Megastudy: rewarding a return after a missed workout was the top performer |
| [BelangerGravel2013], [Peng2022] | Implementation intentions and planning for physical activity |
| [Harkin2016], [Michie2009], [Samdal2017] | Self-monitoring, feedback and graded tasks; Samdal is in adults with overweight |
| [Michie2013] | BCT Taxonomy v1, for labelling app features consistently |
| [WingJeffery1999] | Social support (indirect) |
| [Lally2010] | **located only.** The registry's URL for it is wrong (§8). |

---

## 4. Yoga With Adriene (YouTube; yogawithadriene.com; Adriene Mishler)

### 4.1 What it is; conflicts of interest

- **Who:** Adriene Mishler, a former actor based in Austin who co-founded the channel in 2012 with producer Chris Sharpe [YWA-wiki].
  - Her own page says she was "certified in Austin" and has about 20 years of further training. It names **no credentialing body** [YWA-about].
  - Wikipedia cites a 2018 CNBC article for her teacher training [YWA-wiki].
- **Reach:** over 13 million subscribers and 750+ videos as of January 2025 [YWA-wiki].
- **Paid products:**
  - **Find What Feels Good**, a subscription app ("Netflix for yoga"). Prices were not visible on the page I opened [FWFG] [YWA-about].
  - Courses.
  - Co-ownership of an Austin studio.
  - Brand collaborations and a clothing line [YWA-wiki].
- **Conflict level:** moderate. The free content is extensive, and the paid app is the business.

### 4.2 Spot-checks

| # | Claim or approach (paraphrased) | What the primary evidence says | Verdict |
|---|---|---|---|
| Y1 | **Back pain:** a "yoga for lower back pain" practice presented as helping healing and prevention. No advice to see a professional; no studies [YWA-back]. | **Cochrane 2022** (21 RCTs, 2,223 people; chronic non-specific LBP) [Wieland2022]: yoga vs **no exercise** at 3 months gave function MD −1.69 RMDQ points (low certainty) and pain MD −4.53/100 (moderate certainty). The authors judge both **small and below clinically important thresholds**. Yoga vs **other back exercise** showed probably little or no difference in function (moderate certainty). **Adverse events were more common than with no exercise**: 43 vs 9 per 1,000, mostly increased back pain (low certainty). They were similar to other exercise. No trial tested prevention. | **Overstated.** Yoga is a reasonable *option* among back-friendly exercise, not a treatment. The missing referral advice is a safety gap. |
| Y2 | **"Detox" yoga:** twists said to wring out toxins, massage organs, aid digestion and weight loss [YWA-detox] | No study cited. I found no evidence for a detoxifying effect of twisting postures. | **Unsupported; exclude.** |
| Y3 | **Weight loss:** "yoga for weight loss" sessions framed as "strengthen, trim and tone", without promised amounts [YWA-wl] | In 30 RCTs (2,173 people), yoga had **no effect on weight, BMI, body fat % or waist** overall. It reduced BMI only in people with overweight vs usual care, and that effect was not robust to selection bias [Lauche2016]. A 56-min beginner hatha session averaged **2.5 METs (49% of max heart rate), i.e. light activity** [Hagins2007]. | **Unsupported for fat loss.** "Trim and tone" is marketing language. |
| Y4 | **Stress and anxiety** (PO label: "stress management") | 42 RCTs: yoga postures vs **active control** lowered evening and waking cortisol, ambulatory systolic BP, resting heart rate and some metabolic markers [Pascoe2017]. Anxiety: small effect vs no treatment, SMD −0.43 (−0.74 to −0.11), from 8 small RCTs; **no effect in diagnosed anxiety disorders** [Cramer2018]. Breathwork vs controls: stress g −0.35 (−0.55 to −0.14), 12 RCTs, moderate risk of bias [Fincham2023]. But a 400-person RCT found **paced slow breathing no better than a placebo breathing pace** [Fincham2023b]. | **Supported as "may help people feel less stressed"**, not as treatment. No particular breathing technique has special effects. |
| Y5 | **Sleep, mobility, balance** (PO label: "mobility") | Older adults (22 RCTs) [Sivaramakrishnan2019]: vs inactive controls, balance ES 0.7, lower-body flexibility 0.5, leg strength 0.45 and sleep quality 0.65. Vs **active** controls such as walking, flexibility 0.28 and leg strength 0.49. Women with sleep problems: PSQI SMD −0.54 (16 RCTs); no effect on insomnia severity (3 RCTs) [Wang2020]. | **Supported** (moderate-size benefits vs inactivity; smaller vs other exercise). |
| Y6 | **"Recovery"** (PO's label) | Post-exercise stretching vs passive rest had **no effect on strength recovery or DOMS** at 24–72 h (10 RCTs, 229 people; very low confidence) [Afonso2021rec]. | **Not supported as physiological recovery.** A calm session fits a recovery *day* as low-load activity and wellbeing, not as a recovery accelerator. |

**Safety overall:** across 94 RCTs (8,430 people), yoga was about as safe as usual care and other exercise [Cramer2015].

### 4.3 Citation practice

None of the five pages I opened cites any study. The site makes outcome claims in titles and descriptions (back pain, detox, weight loss) without sources or caveats beyond "listen to your body".

### 4.4 Fit for this audience; safety

**Strengths:**
- Low intensity and self-paced.
- Very beginner-friendly and inclusive in tone, which matches COACH-001.
- Strong for adherence and for MOB-005-type off-day work.

**Risks for this app's users,** all already handled by tags in the condition table (review B):
- Kneeling and deep knee flexion with knee osteoarthritis.
- Wrist loading in plank and down-dog shapes.
- Head-down positions with diabetic eye disease or uncontrolled blood pressure.
- Lying on the back from about 20 weeks of pregnancy.
- End-range or loaded spinal flexion and twisting with osteoporosis.
- Breath retention with blood pressure or heart conditions.
- Hot yoga in pregnancy.

**Other gaps:** there is no screening, and "listen to your body" is the only pain guidance. The app's SAF-003 pain protocol is more specific.

### 4.5 Licensing (D7)

- Her videos, sequences, names of series, on-screen wording and imagery are copyrighted. No open licence was seen, and her terms page could not be opened.
- **May take:** the general idea of short themed sessions, an inclusive tone, permission to modify, and a breathing finish.
- **May not take:** any sequence order, cue wording, video, image or series concept presented as hers.
- Poses themselves are generic movements, but the app's pose set must be written and illustrated originally (D-061).

### 4.6 Decision

**INCLUDE: design input only.** The primary yoga evidence below is added directly; the channel cites none of it. **Never cited as evidence.**

Primary studies worth adding (all **checked**):
- [Wieland2022]
- [Sivaramakrishnan2019]
- [Wang2020]
- [Pascoe2017]
- [Cramer2018]
- [Cramer2015]
- [Lauche2016], to block weight-loss framing
- [Hagins2007], so yoga is not counted as cardio
- [Fincham2023] and [Fincham2023b], for breathing
- [Afonso2021rec], to block recovery claims

---

## 5. Fitness Blender (fitnessblender.com)

### 5.1 What it is; conflicts of interest

- **Who:** founded by Kelli and Daniel Segars, both certified personal trainers.
  - Kelli: bachelor's degrees in psychology and sociology. Daniel: a bachelor's in dietetics [FB-about].
  - From 2021 the team added doctors of physical therapy, registered yoga teachers and more trainers [FB-wiki].
  - Founding is given as 2009 [FB-wiki] or 2010 [FB-about].
- **Business model:**
  - Free videos, with ads on YouTube.
  - **FB Plus** subscription.
  - Paid programmes and challenges.
  - **Meal plans** by registered dietitians.
  - A store, and a PowerBlock dumbbell promotion [FB-home] [FB-prog].
  - Prices were not shown on the pages I could open.
- **Conflict level:** moderate.
- **Positioning:** the site describes its content as "unbiased, gimmick-free, research-backed" [FB-about].

### 5.2 Spot-checks

| # | Claim or approach (paraphrased) | What the primary evidence says | Verdict |
|---|---|---|---|
| F1 | **Afterburn:** HIIT may leave "an elevated metabolism for hours" [FB-v3] | EPOC is **only about 6–15% of the net oxygen cost of the exercise itself**. The stimuli needed for a long EPOC are unlikely to be tolerated by non-athletes, and earlier optimism about EPOC for weight control was "generally unfounded" [LaForgia2006]. | **Overstated; exclude.** |
| F2 | **Calorie-burn ranges per workout** (e.g. 6–11 kcal/min) [FB-v1]. The method page says the ranges assume a 110–200 lb person and that 90% or more of people fall within them. It cites nothing and gives no device or measurement method [FB-cal]. | No validation is given. The ">90% within range" figure is unsourced. | **Unsupported. Exclude** (the app also has no calorie features: DATA-001, FL-001). |
| F3 | **"Fat burning" workouts** (titles and keywords) [FB-v1] [FB-home] | Interval and continuous training reduced body-fat % about equally, −1.50 vs −1.44 points. Interval training reduced absolute fat mass more in one meta-analysis [Viana2019]. Neither is large, and the registry treats HIIT as time-efficient, not a fat burner (FL-001). | **Framing conflicts with FL-001.** |
| F4 | **Low-impact, no-jump cardio** scaled by range of motion and speed. Beginners: limit range and pace and don't try to keep up. Advanced: fuller range, faster but controlled, or added light weights [FB-v2]. | Home-based HIIT improved cardiorespiratory fitness vs non-exercise controls, SMD 0.61 (0.21–1.02), 15 RCTs. It did not differ significantly from moderate continuous training or lab HIIT [Tsuji2023]. Self-paced scaling matches AER-001 (talk test, CR10). | **Supported as a format.** |
| F5 | **High-impact jump HIIT** is labelled "very high impact" and **not for beginners**, with beginners pointed to easier videos. Warm-up and cool-down are never to be skipped [FB-v3]. | Consistent with HIIT-003 (base first), CON-004 (impact ≤1 session a week), FL-003 (impact only under 50, BMI <30, no lower-limb pain) and WU-003. | **Aligned** (the caution, not the jump format). |

### 5.3 Citation practice

- The site claims to be "research-backed" [FB-about].
- None of the five content pages I opened cites a study, including the calorie-method article [FB-cal].
- Credentials are real and stated, but **claims are not sourced**.

### 5.4 Fit for this audience; safety

**Best of the four for this audience's home cardio:**
- an explicit low-impact library with scaling advice
- warnings about soreness and overtraining for beginners [FB-v1]
- high-impact work clearly labelled

**Weaknesses:**
- "Low impact" is sometimes loose: the 37-min "low impact" workout still contains some hopping [FB-v1].
- Intensity is by feel without a structured scale.
- No screening.
- Calorie numbers could mislead people with overweight about what exercise alone does.

### 5.5 Licensing (D7)

- No open licence was seen, and the terms page could not be opened. Treat all content as all rights reserved.
- **May take:** format ideas (no-jump circuits; scaling by range, speed and light added load; beginner and high-impact labelling).
- **May not take:** any video, routine (exercise order, timings), text, image or calorie table.

### 5.6 Decision

**INCLUDE: design input only** for home and low-impact cardio and circuit formats. **Never cited as evidence.** The primary studies the app should cite for these formats are [Tsuji2023], [Chaabene2021] and [Kikuchi2017] (§7.2 P5), not Fitness Blender.

---

## 6. Heather Robertson (heatherrobertson.com; YouTube)

### 6.1 What it is; conflicts of interest

- **Credentials:** described as a "certified trainer and nutrition coach", with **no certifying bodies named** [HR-home] [HR-about].
- **Background:** 16 years in fitness; a former fitness competitor [HR-about].
- **Paid products:**
  - A web app at **US$19.99 a month or US$155.88 a year** after a 7-day trial. It includes programmes, **recipes and nutrition guides** [HR-home].
  - Merchandise.
- **Programmes seen** [HR-prog] [HR-power]:
  - dumbbell strength and hypertrophy (e.g. 8 weeks, about 4 sessions a week, 40–45 min)
  - a no-equipment HIIT and strength mix (15–30 min)
  - a 12-week "Power" programme of 60 sessions
- **Conflict level:** high relative to the evidence offered. The free YouTube content funnels to a subscription.

### 6.2 Spot-checks

| # | Claim or approach | What the primary evidence says | Verdict |
|---|---|---|---|
| H1 | **Signature follow-along HIIT** [TG2021]: 30 s work / 10 s rest, 3 rounds of two circuits, with plank jacks, pop squats and high knees, and no verbal coaching. Other free sessions open with burpees and jacks [2L4G]. | The registry's short-interval protocol uses 15–30 s work and 15–60 s rest, **with 1:2 work:rest for beginners** (HIIT-002) after a ≥3-week base (HIIT-003). 30:10 is a 3:1 ratio, and a 30–40-min circuit at that density counts as HIIT under HIIT-001. The best direct evidence for jump-based bodyweight intervals [Archila2021] used **20-year-olds with normal BMI**: VO2peak 34.2 vs 30.3 ml/kg/min vs controls, n = 19. It does not transfer to adults 30+ with overweight. Plyometrics were feasible and safe in older adults **in supervised, programmed trials** (12 studies, 289 people) [Vetrovsky2019]. That is the opposite of an unsupervised follow-along video. Injuries linked to HIIT-type exercises rose with HIIT's popularity in US emergency-department data, 35% to the lower limb [Rynecki2019] (surveillance data; association only). | **Too dense and too impact-heavy as a default for this audience.** |
| H2 | **"No repeats"** circuits (every exercise different) [2L4G] | Progressive overload needs repeated exposures (BW-001/002; ADH-003 keeps main lifts fixed within a block). In new gym members, low behavioural complexity predicted habit formation [Kaushal2015]. | **Conflicts** with progression tracking and with habit evidence. |
| H3 | **10-minute "finisher" workouts** with no warm-up or stretching, meant to follow a longer session [2L4G] | Fine as an add-on after a warmed-up session. As a standalone session it breaks WU-003 (the 5-min warm-up floor is never removed). | **Conditional; risky out of context.** |
| H4 | **"Burn fat, increase metabolism, build lean muscle"** (programme marketing) [HR-home] [HR-power] | Afterburn is small [LaForgia2006]. HIIT is not a fat-loss accelerator (FL-001; [Viana2019]). | **Marketing; conflicts with FL-001.** |
| H5 | **Dumbbell strength programmes** (e.g. heavier dumbbells, 8–10 reps; 3–5 sessions a week) [HR-prog] [2L4G] | Consistent with broad resistance-training ranges already in the registry (ACSM2026, not re-checked). Nothing here adds to the registry. | **Fine but not novel.** |

### 6.3 Citation practice

None. No studies are cited on any page I opened [HR-about], and certifying bodies are unnamed.

### 6.4 Fit for this audience; safety

- Fast pace with no cueing [TG2021]; frequent jumping in the free HIIT content.
- Low-impact variants exist [HR-home], but no programme targets older adults or joint problems [HR-prog].
- No screening or progression guidance is visible.
- The subscription includes nutrition content, which is outside the app's scope.

### 6.5 Licensing (D7)

- Terms checked [HR-legal]: the company owns all content (text, photos, video). Use is personal and non-commercial only. Copying, republishing, adapting or commercial use needs prior written permission. There is no open licence.
- **The app may take nothing.**

### 6.6 Decision

**EXCLUDE.** Reasons:
1. **No evidence practice:** no citations and unnamed credentials.
2. **Format conflicts** with HIIT-002, HIIT-003, CON-004, FL-003, WU-003 and ADH-003 for the default 30+ fat-loss audience.
3. **Marketing claims** conflict with FL-001.
4. **Nothing unique:** the sound parts duplicate what the registry and Fitness Blender's design input already provide.

Excluding her as a reference is not a judgement on her workouts for fit, uninjured users who choose them outside the app.

---

## 7. Comparison with the app's rules

### 7.1 Where they agree with the registry

| Rule | Agreement |
|---|---|
| **ADH-001** forgiving weekly streak, never resets to zero | NF's "one missed day doesn't ruin it". Primary: [Gardner2012] (checked) and [Singh2024]/[Lally2010] (median 66 days; one miss doesn't derail). |
| **ADH-002** micro-achievements; nothing for extreme volume | NF's "level up" framing. Fits [Mazeas2022] (registry) and [Milkman2021] (rewarding returns works). |
| **ADH-003** stable core, rotating edges | NF's "one habit, keep it simple"; [Kaushal2015] (simplicity, consistency). Heather Robertson's "no repeats" contradicts it. |
| **ADH-004** 20–30-min express session; habit messaging ~2–3 months | All four offer short sessions. Fitness Blender and Heather Robertson have 10–30-min formats. The 2–3-month message matches the medians [Singh2024] [Gardner2012] (§7.2 P1 refines it). |
| **LOAD-002** sRPE prompt; session logging | Self-monitoring evidence [Harkin2016] [Michie2009]. NF's "track daily" advice. |
| **MOB-001/002** dynamic warm-up drills, static stretching after | Fitness Blender's warm-up and cool-down structure. Yoga With Adriene's breathing finish matches MOB-002's 1–2-min wind-down (Expert Practice). |
| **MOB-005** 10–20-min off-day routine, 2–3×/week | Yoga With Adriene's short self-paced sessions; [Sivaramakrishnan2019] supports flexibility and balance gains. |
| **RDY-004** LIGHT (5–10 min mobility) and RECOVERY (optional Z1 + mobility) | A calm session fits these tiers (§7.2 P4). |
| **CON-004 / MOD-002** low-impact preference; impact ≤1/week | Fitness Blender's no-jump library; its "high impact is not for beginners" labelling. |
| **HIIT-003** base before HIIT; first HIIT short 1:2 | Fitness Blender's beginner caution. Heather Robertson's 30:10 does not fit. |
| **BW-001/002** bodyweight progression levers | NF's push-up progressions and progressive-overload list (reps → variation → tempo). Fitness Blender's scaling by range and speed. [Kikuchi2017]: push-ups at a matched load built muscle and strength like bench press in young men. |
| **WU-003** warm-up never removed | Fitness Blender's "don't skip warm-up or cool-down". Heather Robertson's finishers do not fit. |
| **COACH-001** calm, non-judgemental, no guilt | Yoga With Adriene's and Heather Robertson's "consistency over perfection" tone. NF's money-penalty accountability does not fit. |
| **FL-001** no fat-burner claims; no food features | All four sell or promote nutrition or "fat burning" content; FL-001 already excludes it. |

### 7.2 Recommended improvements (only where I checked the primary research)

| ID | Proposed change | Version | Primary evidence (label) | Confidence |
|---|---|---|---|---|
| **P1** | **ADH-004 habit message and evidence.** Keep `habit_median_days: 66`. Change the message from a single "~2–3 months" to a range. Draft: "For many people a new routine starts to feel automatic after about two months, but it varies a lot, from a few weeks to most of a year, and exercise often takes longer. A missed day doesn't undo it." Never show a fixed day count such as 21 or 30. Add the evidence keys and fix the Lally2010 URL (§8). | 1.0.0 → **1.0.1 (PATCH: wording + evidence)** | [Singh2024] checked; [Gardner2012] checked; [Kaushal2015] checked (abstract); [Buyalskaya2023] checked (abstract; "about six months" from the press release, checked); [Lally2010] located | **Moderate Evidence** (direction and spread). Studies are mostly self-report and at high risk of bias; individual timelines can't be predicted. |
| **P2** | **New ADH-005, planning prompt.** When the user plans the week (or once at onboarding, editable), each training day gets a **when** (day and time) and an optional **after-what** cue ("after I drop the kids"). Users also pick **one if-then backup** from a short list, e.g. "If I'm short on time → express session (ADH-004)"; "If I miss the planned day → the next free day (REG-002)". Optional, under 30 s, never nags, and stored on-device only. Same weekdays are suggested by default. | **MINOR: adds a rule** | [BelangerGravel2013] checked: d 0.31 after the intervention, 0.24 at follow-up, 26 studies. [Peng2022] checked: planning SMD 0.35 (0.25–0.44), 35 RCTs; **online-only delivery SMD 0.14 (−0.02 to 0.31), not significant**; effects larger with reinforcement during follow-up. [Buyalskaya2023]/PR: same-weekday consistency predicted attendance. [Singh2024]: time-based and routine-based cues did not differ. | **Limited Evidence** for app-only delivery (the most applicable subgroup was not significant). Moderate for planning in general. Low cost and no plausible harm, so worth building. |
| **P3** | **ADH-002 "Welcome back" micro-achievement.** It is earned by the first completed session after a missed planned session. One neutral nudge goes out on the next planned day ("Ready when you are; today's plan is set"). No penalty, guilt or streak-loss wording (COACH-001), and the ADH-001 freeze logic is unchanged. | 1.0.0 → **1.1.0 (MINOR)** | [Milkman2021] checked: 61,293 gym members, 54 four-week programmes. 45% raised weekly visits by 9–27%, and **the top performer gave micro-rewards for returning after a missed workout**. Only 8% had effects that lasted beyond 4 weeks. [Buyalskaya2023]/PR: days since the last visit predicted attendance. | **Limited Evidence.** The megastudy's micro-rewards were incentives, and I did not read their form; a non-monetary badge is an untested, weaker analogue. Effects fade. Cheap and harmless. |
| **P4** | **New MOB-006, calm mobility session** (yoga-style, original content). Details below. | **MINOR: adds a rule** | [Sivaramakrishnan2019], [Wang2020], [Pascoe2017], [Cramer2018], [Fincham2023], [Fincham2023b], [Cramer2015], [Wieland2022], [Hagins2007], [Lauche2016], [Afonso2021rec], all checked | **Moderate Evidence** that regular yoga-type practice improves flexibility, balance, sleep quality and perceived stress vs inactivity (small benefits vs other exercise). **Expert Practice** for the format, triggers and dose. |
| **P5** | **"Training away from the gym today" preset** (screen requirement plus an EQ-003 Product Rule). One tap sets `equipmentToday` to the user's saved home kit (bodyweight by default). The engine already handles substitution. Details below. | **MINOR: adds a rule** (Product Rule with Expert Practice dosing) | [Tsuji2023] checked: home HIIT improves fitness, similar to continuous training. [Chaabene2021] checked: home programmes in healthy 65–83-year-olds, 17 RCTs, small gains in strength (SMD 0.30), power (0.43) and balance (0.28); strength-only programmes did better (strength 0.51, balance 0.65). [Kikuchi2017] checked: push-ups ≈ bench press at a matched load. [Archila2021] checked: works, but jump-heavy and young. [Vetrovsky2019] checked: plyometrics safe when supervised. | **Moderate Evidence** that home training improves fitness and strength. **Expert Practice** for the format. |
| **P6** | **Evidence-list PATCHes** (no confidence change): add [Harkin2016] and [Michie2009] to LOAD-002 and ADH-002 (self-monitoring); add [Samdal2017] to STEP-001 (goal setting, feedback, graded tasks and step counters predicted long-term activity in adults with overweight); add [Michie2013] as the vocabulary for labelling adherence features. | **PATCH** | All checked | Unchanged |

**P4: MOB-006 calm mobility session, details**

- **Offered, never imposed:**
  - on rest days, as an alternative to MOB-005
  - on LIGHT and RECOVERY days (RDY-004)
  - when the check-in stress item is 1–2 (RDY-001)
- **Length and structure:** 15–30 min.
  - 2–3 min easy movement.
  - Slow flowing mobility for hips, upper back, shoulders and ankles.
  - Standing balance holds: these count toward FL-003 balance minutes from age 50, if FL-003 is approved.
  - Holds of 30–60 s, as in MOB-002/005.
  - 3–5 min of easy, slow breathing to finish, with no breath holds and no special ratios.
- **Does not count as** Z1 minutes (PH-001, FL-002) or as a strength day.
- **Safety filters:**
  - All SAF-010 tags apply (`head_down`, lying on the back in pregnancy, loaded or end-range spinal flexion and twisting for osteoporosis, breath-holding).
  - Joint limits apply (wrist, kneeling knee).
  - The SAF-003 pain protocol applies.
  - No heated rooms.
- **Copy:** says "for mobility, balance and winding down". It never claims treatment, detox, weight loss or faster recovery.
- **Content:** all original, with D-061 illustrations.
- **Optional addition:** offer it as one choice in the low-back-pain entry, with honest expectations (small benefit, similar to other exercise; [Wieland2022]).

**P5: away-from-gym preset, details**

- **Strength:** uses BW-001 ladders, taken to the plan's RIR.
- **Conditioning:** defaults to **no-jump** bodyweight circuits.
  - Jumping only under the FL-003/CON-004 conditions.
  - Beginners use **1:2 work:rest** (HIIT-002), not creator-style 3:1.
  - A circuit at CR10 ≥7 for ≥8 min counts as HIIT (HIIT-001, already in place).
- **Warm-up:** the warm-up floor applies even to 10-minute sessions (WU-003).
- **Express format:** 20–30-min express versions (ADH-004).
- **Tests:** a test that a home day keeps weekly pattern coverage (PAT-001) or reports the shortfall.

Each new or changed rule needs at least 2 tests named by rule ID, as with earlier changes.

### 7.3 What the app should NOT adopt from these resources

| Item | Source | Why not |
|---|---|---|
| "Diet is ≥80% of results" | NF [NF-bw] | Unsourced number. The app gives no diet advice, and FL-001 already sets realistic exercise-only expectations. |
| Nutrition products, meal plans, recipes, calorie and macro tracking | NF, FB, HR | Out of scope (D-060, DATA-001 `calorie_intake` never collected) |
| Money or penalty "accountability"; "missing two days ruins it" as a rule | NF [NF-habits] | COACH-001 forbids guilt and punishment. The "two in a row" rule is untested and would contradict the forgiving ADH-001. |
| Fixed habit timelines (21 or 30 days) | Popular framing; NF's 30-day trial | Medians are about 2 months, with a range from days to most of a year [Singh2024]; gym habits take months [Buyalskaya2023] |
| "Detox", organ-massage and digestion claims for twists | YWA [YWA-detox] | No evidence; physiologically implausible. A medical-type claim the app must never make (SAF-009, SAF-010 "no treatment claims"). |
| Yoga for weight loss / "trim and tone" | YWA [YWA-wl] | No effect on weight, fat or waist overall [Lauche2016]; light intensity [Hagins2007]; conflicts with FL-001 |
| Yoga as treatment or prevention of back pain; no referral advice | YWA [YWA-back] | Small, not clinically important effects vs no exercise; similar to other exercise; more adverse events than no exercise [Wieland2022]. The app keeps NG59 and the red-flag wording (review B). |
| Yoga or stretching "speeds recovery" | PO label for YWA | No effect on strength recovery or DOMS [Afonso2021rec] |
| Counting yoga as cardio minutes | — | About 2.5 METs, light activity [Hagins2007] |
| Special breathing ratios or techniques as stress cures | Common yoga and breathwork framing | Slow paced breathing was no better than placebo-paced breathing [Fincham2023b] |
| Calorie-burn estimates per workout | FB [FB-cal] [FB-v1] | Unvalidated, unsourced; calorie features are out of scope |
| "Elevated metabolism for hours" (afterburn) | FB [FB-v3]; HR marketing | EPOC is about 6–15% of the exercise's own cost [LaForgia2006] |
| "Fat-burning" HIIT framing | FB, HR | FL-001; [Viana2019] shows no clear advantage in body-fat % |
| Dense 30:10 jump HIIT, burpees and pop squats as the default | HR [TG2021] [2L4G]; FB's jump videos | HIIT-002/003, CON-004, FL-003. Direct evidence is from young, lean people [Archila2021]; plyometric safety data come from supervised programmes [Vetrovsky2019]. |
| "No repeats" sessions | HR [2L4G] | Prevents progressive overload tracking (BW-002, PROG); conflicts with ADH-003 and with simplicity aiding habit [Kaushal2015] |
| Standalone 10-min sessions without a warm-up | HR [2L4G] | WU-003 floor |
| Any creator text, routine, sequence, image, video or brand name | All four | D7 (original or openly licensed only). No open licence was found; HR's terms forbid reuse [HR-legal]. |

---

## 8. Registry housekeeping found during this review

**Fault: wrong URL for `Lally2010`.**
- The registry's `sources.Lally2010` URL (`discovery.ucl.ac.uk/id/eprint/10144814/`) opens a **different paper**: Gardner B, Rebar AL, Lally P. *How does habit form? Guidelines for tracking real-world habit formation.* Cogent Psychology 2022;9(1). doi:10.1080/23311908.2022.2041277 [GardnerRebarLally2022, checked].
- The Lally 2010 citation text itself is correct (*Eur J Soc Psychol* 2010;40(6):998–1009). Its DOI is 10.1002/ejsp.674 (**unverified**: the Wiley page would not open).

**Fix:** a PATCH to the sources dictionary.
- Replace the URL with the publisher DOI link once it is confirmed (10.1002/ejsp.674 is **unverified**), and keep the label "located".
- Add Gardner2012 and Singh2024, both **checked**. They carry Lally's figures (median 66 days, range 18–254) [Singh2024] and the missed-day finding [Gardner2012].

**Rules affected:** ADH-001 and ADH-004.

---

## 9. Limits

1. **Creator content was sampled, not audited.** I opened 3–6 pages per creator. Hundreds of videos exist, and YouTube pages give little text. For Heather Robertson two summaries are third-party:
   - Tom's Guide, a reputable outlet
   - 2lazy4gym, a fan blog with low reliability, used only to describe formats
2. **Paywalled pages and abstracts.** Several primary sources were read as abstracts through repositories or summary pages (labelled in §10). None was read in full except the Cochrane summary and Singh 2024.
   - Bélanger-Gravel 2013's figures come from the abstract as reported by the McMaster Optimal Aging Portal.
   - The "about six months" gym figure comes from the Buyalskaya 2023 press release, not the paper.
3. **Not opened, so no numbers used:** Lally 2010 itself; Gollwitzer & Sheeran 2006 and Sheeran et al. 2024 on implementation intentions (located only); the form of the Milkman 2021 micro-rewards.
4. **Evidence gaps for the proposals:**
   - **P2 and P3:** evidence for app-only behaviour-change features is weaker than for face-to-face delivery [Peng2022].
   - **P4:** yoga evidence is mostly vs inactive controls, with small, often unblinded trials.
   - **P5:** the format is Expert Practice.
5. **Subscription prices** for Nerd Fitness coaching, Find What Feels Good and FB Plus were not visible on the pages I could open. Only Heather Robertson's price was confirmed.

---

## 10. Sources

### 10.1 Creator and third-party pages (all **checked** on 9 Oct 2026 unless marked)

**Nerd Fitness**
- [NF-about] Nerd Fitness. About. https://www.nerdfitness.com/about/
- [NF-blog] Nerd Fitness. Blog index. https://www.nerdfitness.com/blog/
- [NF-habits] Kamb S. How to build healthy habits that stick. Nerd Fitness; published 10 Jan 2024, updated 30 Apr 2026. https://www.nerdfitness.com/blog/how-to-build-healthy-habits-that-stick/
- [NF-bw] Kamb S. The beginner bodyweight workout. Nerd Fitness. https://www.nerdfitness.com/blog/beginner-body-weight-workout-burn-fat-build-muscle/
- [NF-old] Kamb S. "Am I too old to get in shape?" Nerd Fitness. https://www.nerdfitness.com/blog/am-i-too-old-to-get-in-shape/
- [NF-coach] Nerd Fitness. Online coaching overview. https://www.nerdfitness.com/coaching-overview-page/
- Nerd Fitness terms of service: **could not be opened**.

**Yoga With Adriene**
- [YWA-about] Yoga With Adriene. About Adriene. https://yogawithadriene.com/adriene-mishler/
- [YWA-wiki] Wikipedia. Adriene Mishler. https://en.wikipedia.org/wiki/Adriene_Mishler
- [FWFG] Find What Feels Good. Membership page. https://fwfg.com/pages/fwfg-membership. The page is JavaScript-only, so little content loaded.
- [YWA-back] Yoga With Adriene. Yoga for lower back pain. https://do.yogawithadriene.com/blog/yoga-for-lower-back-pain
- [YWA-detox] Yoga With Adriene. Detox yoga. https://do.yogawithadriene.com/blog/detox-yoga
- [YWA-wl] Yoga With Adriene. Yoga for weight loss: balance practice. https://yogawithadriene.com/yoga-for-weight-loss-balance-practice/

**Fitness Blender**
- [FB-home] Fitness Blender. Home page. https://www.fitnessblender.com/
- [FB-about] Fitness Blender. About. https://www.fitnessblender.com/page/about-fitness-blender
- [FB-wiki] Wikipedia. Fitness Blender. https://en.wikipedia.org/wiki/Fitness_Blender
- [FB-cal] Fitness Blender. How does Fitness Blender calculate calories burned? https://www.fitnessblender.com/articles/how-does-fitness-blender-calculate-calories-burned-fitness-blender-calories-burned-estimates
- [FB-v1] Fitness Blender. Fat burning cardio workout, 37 min. https://www.fitnessblender.com/videos/fat-burning-cardio-workout-37-minute-fitness-blender-cardio-workout-at-home
- [FB-v2] Fitness Blender. Low impact cardio for beginners, no jumping. https://fitnessblender.com/videos/low-impact-cardio-workout-for-beginners-recovery-cardio-workout-with-no-jumping
- [FB-v3] Fitness Blender. "When I say jump" HIIT cardio, round 2. https://fitnessblender.com/videos/when-i-say-jump-hiit-cardio-workout-round-2-28-min-high-intensity-interval-training
- [FB-prog] Fitness Blender. Programs, challenges, meal plans. https://www.fitnessblender.com//workouts-programs
- Fitness Blender terms of use and FB Plus pages: **could not be opened**.

**Heather Robertson**
- [HR-home] Heather Robertson. Home page and FAQ. https://heatherrobertson.com/
- [HR-about] Heather Robertson. About. https://www.heatherrobertson.com/about
- [HR-prog] Heather Robertson. Programs. https://heatherrobertson.com/programs
- [HR-power] Heather Robertson. Program page (shows "Power", 12 weeks). https://heatherrobertson.com/?p=14479
- [HR-legal] Heather Robertson. Legal terms. https://www.heatherrobertson.com/legal-terms
- [TG2021] McGuire J. I tried this Heather Robertson HIIT workout with 3 million views. Tom's Guide, 2 Dec 2021. https://www.tomsguide.com/news/i-tried-this-heather-robertson-hiit-workout-with-3-million-views-heres-what-happened
- [2L4G] 2lazy4gym. Heather Robertson category pages. https://2lazy4gym.com/category/trainers/heather-robertson/ (and /page/27/). Fan blog; low reliability; used only to describe formats.

### 10.2 Primary research: habits and behaviour change

- [Lally2010] Lally P, van Jaarsveld CHM, Potts HWW, Wardle J. How are habits formed: modelling habit formation in the real world. *Eur J Soc Psychol*. 2010;40(6):998–1009. doi:10.1002/ejsp.674 (DOI **unverified**).
  - **Located.** The publisher page would not open, and the registry URL points to another paper (§8).
  - Figures are taken via [Singh2024], plus a secondary summary at thebehavioralscientist.com (checked). That summary reports 96 recruited, 82 analysed and 39 with a good model fit. For exercise behaviours (n = 13) it gives a median of 91 days, a non-significant difference from other behaviours.
- [GardnerRebarLally2022] Gardner B, Rebar AL, Lally P. How does habit form? Guidelines for tracking real-world habit formation. *Cogent Psychology*. 2022;9(1). doi:10.1080/23311908.2022.2041277. **Checked** (UCL Discovery record; this is what the registry's Lally2010 URL opens).
- [Gardner2012] Gardner B, Lally P, Wardle J. Making health habitual: the psychology of "habit-formation" and general practice. *Br J Gen Pract*. 2012;62(605):664–666. doi:10.3399/bjgp12X659466. **Checked** (bjgp.org).
- [Singh2024] Singh B, Murphy A, Maher C, Smith AE. Time to form a habit: a systematic review and meta-analysis of health behaviour habit formation and its determinants. *Healthcare (Basel)*. 2024;12(23):2488. doi:10.3390/healthcare12232488. **Checked** (MDPI full text, first 100,000 characters).
- [Kaushal2015] Kaushal N, Rhodes RE. Exercise habit formation in new gym members: a longitudinal study. *J Behav Med*. 2015;38(4):652–663. doi:10.1007/s10865-015-9640-7. **Checked** (Springer abstract).
- [Buyalskaya2023] Buyalskaya A, Ho H, Milkman KL, Li X, Duckworth AL, Camerer C. What can machine learning teach us about habit formation? Evidence from exercise and hygiene. *Proc Natl Acad Sci USA*. 2023;120(17):e2216115120. doi:10.1073/pnas.2216115120. **Checked** (abstract via Europe PMC). Correction: *PNAS* 2023;120(34):e2312763120.
  - [Buyalskaya-PR] Caltech press release on ScienceDaily, 17 Apr 2023, https://sciencedaily.com/releases/2023/04/230417155750.htm. **Checked.** Source of the figures "average of about six months", "76% days since last visit" and "69% same weekday".
- [Milkman2021] Milkman KL, Gromet D, Ho H, et al. Megastudies improve the impact of applied behavioural science. *Nature*. 2021;600:478–483. doi:10.1038/s41586-021-04128-4. **Checked** (Nature page).
- [BelangerGravel2013] Bélanger-Gravel A, Godin G, Amireault S. A meta-analytic review of the effect of implementation intentions on physical activity. *Health Psychol Rev*. 2013;7(1):23–54. **Checked** (abstract as reported by the McMaster Optimal Aging Portal; the publisher page would not open).
- [Peng2022] Peng S, Othman AT, Yuan F, Liang J. The effectiveness of planning interventions for improving physical activity in the general population: a systematic review and meta-analysis of randomized controlled trials. *Int J Environ Res Public Health*. 2022;19(12):7337. doi:10.3390/ijerph19127337. **Checked** (MDPI).
- [GollwitzerSheeran2006] Gollwitzer PM, Sheeran P. Implementation intentions and goal achievement: a meta-analysis of effects and processes. *Adv Exp Soc Psychol*. 2006;38:69–119. **Located** (ScienceDirect blocked). No figures used.
- [Sheeran2024] Sheeran P, Listrom O, Gollwitzer PM. The when and how of planning: meta-analysis of the scope and components of implementation intentions in 642 tests. *Eur Rev Soc Psychol*. 2024. **Located** (search listing only; volume and pages unverified). No figures used.
- [Harkin2016] Harkin B, Webb T, Chang B, et al. Does monitoring goal progress promote goal attainment? A meta-analysis of the experimental evidence. *Psychol Bull*. 2016;142(2):198–229. doi:10.1037/bul0000025. **Checked** (White Rose repository abstract).
- [Michie2009] Michie S, Abraham C, Whittington C, McAteer J, Gupta S. Effective techniques in healthy eating and physical activity interventions: a meta-regression. *Health Psychol*. 2009 (published 1 Nov 2009). **Checked** (Exeter repository abstract); volume and pages not shown there.
- [Michie2013] Michie S, Richardson M, Johnston M, et al. The Behavior Change Technique Taxonomy (v1) of 93 hierarchically clustered techniques: building an international consensus for the reporting of behavior change interventions. *Ann Behav Med*. 2013;46(1):81–95. doi:10.1007/s12160-013-9486-6. **Checked** (Springer abstract).
- [Samdal2017] Samdal GB, Eide GE, Barth T, Williams G, et al. Effective behaviour change techniques for physical activity and healthy eating in overweight and obese adults; systematic review and meta-regression analyses. *Int J Behav Nutr Phys Act*. 2017;14:42. doi:10.1186/s12966-017-0494-y. **Checked** (Springer abstract).
- [WingJeffery1999] Wing RR, Jeffery RW. Benefits of recruiting participants with friends and increasing social support for weight loss and maintenance. *J Consult Clin Psychol*. 1999;67(1):132–138. doi:10.1037/0022-006X.67.1.132. **Checked** (University of Minnesota record).

### 10.3 Primary research: yoga, breathing, recovery

- [Wieland2022] Wieland LS, Skoetz N, Pilkington K, Harbin S, Vempati R, Berman BM. Yoga for chronic non-specific low back pain. *Cochrane Database Syst Rev*. 2022;(11):CD010671. doi:10.1002/14651858.CD010671.pub3. **Checked** (cochrane.org summary of findings).
- [Sivaramakrishnan2019] Sivaramakrishnan D, Fitzsimons C, Kelly P, et al. The effects of yoga compared to active and inactive controls on physical function and health related quality of life in older adults: systematic review and meta-analysis of randomised controlled trials. *Int J Behav Nutr Phys Act*. 2019;16(1). doi:10.1186/s12966-019-0789-2. **Checked** (DOAJ abstract).
- [Wang2020] Wang WL, Chen KH, Pan YC, Yang SN, Chan YY. The effect of yoga on sleep quality and insomnia in women with sleep problems: a systematic review and meta-analysis. *BMC Psychiatry*. 2020;20:195. doi:10.1186/s12888-020-02566-4. **Checked** (abstract via Qigong Institute).
- [Pascoe2017] Pascoe MC, Thompson DR, Ski CF. Yoga, mindfulness-based stress reduction and stress-related physiological measures: a meta-analysis. *Psychoneuroendocrinology*. 2017;86:152–168. doi:10.1016/j.psyneuen.2017.08.008. **Checked** (citation via the VU repository; abstract via Qigong Institute; the abstract gives no effect sizes).
- [Cramer2018] Cramer H, Lauche R, Anheyer D, et al. Yoga for anxiety: a systematic review and meta-analysis of randomized controlled trials. *Depress Anxiety*. 2018;35(9):830–843. **Checked** (Northumbria repository abstract).
- [Cramer2015] Cramer H, Ward L, Saper R, Fishbein D, Dobos G, Lauche R. The safety of yoga: a systematic review and meta-analysis of randomized controlled trials. *Am J Epidemiol*. 2015;182(4):281–293. doi:10.1093/aje/kwv071. **Checked** (UTS repository abstract).
- [Lauche2016] Lauche R, Langhorst J, Lee MS, Dobos G, Cramer H. A systematic review and meta-analysis on the effects of yoga on weight-related outcomes. *Prev Med*. 2016;87:213–232. **Checked** (abstract as reported by the McMaster Optimal Aging Portal).
- [Hagins2007] Hagins M, Moore W, Rundle A. Does practicing hatha yoga satisfy recommendations for intensity of physical activity which improves and maintains health and cardiovascular fitness? *BMC Complement Altern Med*. 2007;7:40. doi:10.1186/1472-6882-7-40. **Checked** (DOAJ and Qigong Institute abstracts).
- [Fincham2023] Fincham GW, Strauss C, Montero-Marin J, Cavanagh K. Effect of breathwork on stress and mental health: a meta-analysis of randomised-controlled trials. *Sci Rep*. 2023;13:432. doi:10.1038/s41598-022-27247-y. **Checked** (Sussex repository abstract and PsyPost summary). The DOI is inferred from the repository file name and is **unverified**.
- [Fincham2023b] Fincham GW, Strauss C, Cavanagh K. Effect of coherent breathing on mental health and wellbeing: a randomised placebo-controlled trial. *Sci Rep*. 2023. doi:10.1038/s41598-023-49279-8. **Checked** (DOAJ abstract; authors and DOI via PsyPost, 17 Jan 2024). Volume and article number were not seen.
- [Afonso2021rec] Afonso J, Clemente FM, Nakamura FY, et al. The effectiveness of post-exercise stretching in short-term and delayed recovery of strength, range of motion and delayed onset muscle soreness: a systematic review and meta-analysis of randomized controlled trials. *Front Physiol*. 2021;12:677581. doi:10.3389/fphys.2021.677581. **Checked** (Frontiers). This is a different paper from the registry's Afonso2021 on range of motion.

### 10.4 Primary research: home, bodyweight and HIIT; impact; afterburn

- [Tsuji2023] Tsuji K, Tsuchiya Y, Ueda H, Ochi E. Home-based high-intensity interval training improves cardiorespiratory fitness: a systematic review and meta-analysis. *BMC Sports Sci Med Rehabil*. 2023;15(1). doi:10.1186/s13102-023-00777-2. **Checked** (DOAJ abstract).
- [Chaabene2021] Chaabene H, Prieske O, Herz M, et al. Home-based exercise programmes improve physical fitness of healthy older adults: a PRISMA-compliant systematic review and meta-analysis with relevance for COVID-19. *Ageing Res Rev*. 2021;67:101265. doi:10.1016/j.arr.2021.101265. **Checked** (Essex repository).
- [Kikuchi2017] Kikuchi N, Nakazato K. Low-load bench press and push-up induce similar muscle hypertrophy and strength gain. *J Exerc Sci Fit*. 2017;15(1):37–42. doi:10.1016/j.jesf.2017.06.003. **Checked** (DOAJ abstract).
- [Archila2021] Archila LR, Bostad W, Joyner MJ, Gibala MJ. Simple bodyweight training improves cardiorespiratory fitness with minimal time commitment: a contemporary application of the 5BX approach. *Int J Exerc Sci*. 2021;14(3):93–100. **Checked** (Digital Commons abstract).
- [Vetrovsky2019] Vetrovsky T, Steffl M, Stastny P, Tufano JJ, et al. The efficacy and safety of lower-limb plyometric training in older adults: a systematic review. *Sports Med*. 2019;49(1):113–131. doi:10.1007/s40279-018-1018-x. **Checked** (Springer page; the author list there was truncated).
- [Rynecki2019] Rynecki ND, Siracuse BL, Ippolito JA, Beebe KS. Injuries sustained during high intensity interval training: are modern fitness trends contributing to increased injury rates? *J Sports Med Phys Fitness*. 2019;59(7):1206–1212. doi:10.23736/S0022-4707.19.09407-6. **Checked** (Rutgers repository abstract).
- [LaForgia2006] LaForgia J, Withers RT, Gore CJ. Effects of exercise intensity and duration on the excess post-exercise oxygen consumption. *J Sports Sci*. 2006;24(12):1247–1264. **Checked** (abstract via SPONET). Author names were not shown on the page I opened; they are from the PubMed listing title and are **unverified**.
- [Viana2019] Viana RB, Naves JPA, Coswig VS, et al. Is interval training the magic bullet for fat loss? A systematic review and meta-analysis comparing moderate-intensity continuous training with high-intensity interval training (HIIT). *Br J Sports Med*. 2019;53(10):655–664. doi:10.1136/bjsports-2018-099928. **Checked** (Solent repository abstract).

### 10.5 Already in the project, cited here but not re-checked

- Jayedi2024, Oppert2021, Messier2005, BrookeWavell2022 (via review A)
- Mazeas2022, Jeffreys2019, ACSM2026 (registry)
- The condition-table tags and NG59 (review B)
