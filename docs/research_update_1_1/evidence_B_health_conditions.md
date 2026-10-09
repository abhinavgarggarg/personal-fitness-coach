# B. Health conditions → encodable training controls (evidence review)

Prepared 9 Oct 2026 for the Personal Fitness Coach app (offline engine, gym strength + cardio).
Scope: 11 core conditions plus 3 optional ones (stroke, COPD, cancer survivors), and a regulatory boundary note for India, EU, UK and US.

The app is **not** a medical device. It never diagnoses and never gives medication or diet advice. Everything below is written as **conservative safety limits on general fitness training**. None of it is a treatment programme.

---

## 0. How to read this report

**Source labels** (strict, per brief)
- **checked**: I opened the abstract, guideline page, publisher page, repository full text or PubMed-equivalent page during this session. Quoted numbers come from that page.
- **checked (secondary)**: I opened a reputable secondary summary (for example an ACC "Ten points to remember" page, a law-firm or regulatory-consultancy note, or a guideline-summary site), not the primary text. Numbers come from that summary.
- **located**: I confirmed the source exists (citation metadata seen) but could not read the relevant content.
- **unverified**: from memory only. Do not encode it until it has been checked.

**Confidence ratings** (per condition, and per control where they differ)
- **High Evidence**: recommended in current guidelines on the basis of RCTs or meta-analyses.
- **Moderate Evidence**: a guideline or consensus recommendation with moderate or low-certainty evidence, or consistent consensus across bodies.
- **Limited Evidence**: few trials or low certainty, and guidance is conflicting or sparse.
- **Expert Practice**: consensus or expert opinion, *or my own translation of guidance into app numbers* (for example "min RIR 3"). Most numeric caps in the table entries fall here. That is normal. Guidelines describe intensity in %HRR, %1RM or RPE, and they do not use the app's own control vocabulary.

**Access problems this session** (so nothing is silently missing)
- PubMed and PMC pages (pubmed.ncbi.nlm.nih.gov, pmc.ncbi.nlm.nih.gov) returned a reCAPTCHA page, so their abstracts could not be read there. NCBI E-utilities, Europe PMC, Crossref and OpenAlex were blocked by the session's egress policy (HTTP 403). **The Europe PMC REST page was refused by the fetch proxy (HTTP 429, rate limited) and was not retried.** I used publisher, university-repository and guideline pages instead.
- Permission requests for fda.gov (General Wellness guidance), health.ec.europa.eu (MDCG 2019-11 PDF) and jacc.org (2025 AHA/ACC BP guideline) timed out and were withdrawn. Those primary texts are therefore **located** only, and their content comes from secondary summaries.
- acog.org and the LWW full text of ACOG CO 804 failed to load (client error). The GINA 2026 PDF text extraction stopped at about 108k characters, before the exercise/EIB section.
- Some NICE pages loaded only partially. NG226 rec. 1.3.x and NG59 rec. 1.2.1–1.2.6 were read; NG59 red-flag wording was not.

**App intensity vocabulary used below.** I assume these definitions; check them against the app's own definitions.
- **Z1 easy**: conversational, RPE ≤ 11/20.
- **Z2 tempo**: "moderate–somewhat hard", RPE about 12–14.
- **Z3 hard**: vigorous, RPE about 15–17.
- **HIIT**: intervals above Z3.

For reference, ACSM/Kanaley define moderate as RPE 11–12 (40–59 % HRR) and vigorous as RPE 14–17 (60–89 % HRR) [KAN22]. "Moderate only" therefore maps best to **Z1–low Z2**.

---

## 1. Cross-cutting design rules (apply to every entry)

1. **Combination rule.** When a user selects several conditions, take the **most restrictive value of each control**: lowest max zone, HIIT = no if any entry says no, highest min-RIR, impact = no if any says no, the union of avoid-tags and positions, the lowest joint limit per joint, the highest extra warm-up, and the union of prompts and stop signs. *(Expert Practice)*
2. **Self-reported status, not clinical values.** Ask "Has a doctor or nurse told you this is currently under control / stable?" (yes / no / not sure) rather than having the engine read blood pressure, glucose or ketone numbers and make threshold decisions. Value-based gating is the feature most likely to look like a medical device (see section 15). Treat "not sure" as "not controlled". *(Expert Practice + regulatory reasoning)*
3. **"Doctor's OK" is a user attestation.** The app records the scope the user says they were cleared for (none / light–moderate / vigorous / intervals) and unlocks only that scope. This mirrors the ACSM 2015 logic: known cardiovascular, metabolic or renal disease leads to clearance, then light-to-moderate work, then gradual progression [RIE15].
4. **The existing universal red flags stay on for everyone.** These are chest pain, fainting, severe breathlessness, etc. The condition entries add *condition-specific* stop signs only.
5. **Proposed new tags and fields** (they are needed by several entries below):
   - tags: `breath_hold_max` (1–3RM attempts, max-effort grinding reps), `isometric_heavy` (long, hard holds such as a wall-sit to failure or heavy static holds), `loaded_spinal_rotation`, `head_down` (inversions, decline positions with the head below the heart), `supine_lying`, `prone_lying`, `high_fall_risk` (box jumps, unstable-surface balance under load, Olympic lifts), `contact`, `uneven_surface_running`.
   - fields: `extra_cooldown_min`; `max_consecutive_rest_days` (for diabetes); `impact_unlock_rule` (for postpartum, OA, obesity); `phase_by_weeks` (for postpartum and pregnancy trimester).
6. **Wording.** Every prompt is phrased as a general fitness or safety tip. It must never name a dose, a drug, a glucose or BP target, or a diagnosis. Section 15 explains why.

---

## 2. Master matrix (summary of proposed table entries)

| # | Entry | Clearance | Max zone | HIIT | Min RIR | Failure | Impact | Key avoid-tags | Joint limits | +Warm-up | Overall confidence |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1a | High BP – controlled | Not needed if controlled | Z3 | Yes (after ≥4 wk base) | 2 | No | Yes | breath_hold_max | – | 0 (+5 cool-down) | Moderate |
| 1b | High BP – not controlled / not sure | Suggest now; required before vigorous | Z2 | No | 3 | No | Low–mod | breath_hold_max, isometric_heavy | – | +5 (+5 cool-down) | Moderate / Expert Practice |
| 2 | Type 2 diabetes | Before vigorous (before moderate if inactive: handled by screen) | Z3 | Yes after clearance + base | 2 | No | Yes | – | – | +5 | High (training) / Moderate (safety) |
| 2r | + diabetic eye disease | Always | Z2 (RPE ≤12 if severe) | No | 3 | No | No (no jarring) | breath_hold_max, head_down, jumping, overhead heavy | – | +5 | Moderate / Expert Practice |
| 2n | + nerve damage in feet | Not needed unless foot wound | Z3 (non-weight-bearing) | Yes, on bike/rower | 2 | No | No | jumping, uneven_surface_running, high_fall_risk | ankle/foot ≤2 (EP) | +5 | Moderate / Expert Practice |
| 2a | + autonomic neuropathy | Always | Z2 by RPE | No | 3 | No | Low | – | – | +10 | Moderate / Expert Practice |
| 3 | Type 1 diabetes | Before vigorous | Z3 | Yes (with glucose prompts) | 2 | No | Yes | – | – | +5 | Moderate |
| 4 | Known heart condition (until cleared) | Always | Z2 | No | 3 | No | Low | breath_hold_max, isometric_heavy | – | +10 (+10 cool-down) | Moderate / Expert Practice |
| 5 | Asthma | Not needed if controlled | Z3 | Yes if controlled | default | default | Yes | – | – | +10 (interval-style) | Moderate |
| 6 | Knee / hip osteoarthritis | Not needed | Z3 (low-impact modes) | Yes, low-impact | 2 | No | Low by default, unlockable | jumping (start), deep_knee_flexion pain-limited | affected joint ≤2 → 3 | +5 | High (exercise) / Expert Practice (limits) |
| 7 | Low back pain | Not needed (red flags) | Z3 | Yes as tolerated | 2 on spinal loading | No | As tolerated | in flare: heavy spinal_loading, loaded spinal_flexion | spine ≤2 in flare | +5 | Moderate / Expert Practice |
| 8a | Osteoporosis / osteopenia | Suggested | Z3 | Yes | 1–2 | No | Moderate impact encouraged | loaded spinal_flexion, loaded_spinal_rotation | – | +5 | Moderate |
| 8b | + spine (vertebral) fracture or multiple fractures | Strongly suggested (physio) | Z2–Z3 | No | 3 | No | No (up to brisk walking) | jumping, spinal_flexion, loaded_spinal_rotation, high_fall_risk | spine ≤2 | +5 | Moderate / Expert Practice |
| 9 | Pregnancy | Always (pregnancy care provider) | Z2 (Z3 only if previously vigorous + OK) | No | 3 | No | As tolerated if already doing it | breath_hold_max, high_fall_risk, contact, Olympic lifts, supine_lying | – | +5 | Moderate |
| 10 | After giving birth (≤12 mo) | Suggested; pelvic health check from 6 wk | Phased Z1→Z3 | From about 3 mo, after impact unlocked | 3 → 2 | No | None before 3 mo + tests | jumping, running (early) | – | +5 | Moderate (MVPA, PFMT) / Expert Practice (timeline) |
| 11a | Obesity, BMI ≥30 (South Asian ≥27.5) | Not needed alone | Z3 | Yes, low-impact | 2 | No | Limited volume | high-volume jumping | knee ≤3 | +5 | Moderate / Expert Practice |
| 11b | Obesity, BMI ≥35 (South Asian ≥32.5) | Suggested | Z2 → Z3 | After 6–8 wk base | 2 | No | Low only at start | jumping, running (start) | knee ≤2 → 3 | +5 | Expert Practice |
| 12* | Previous stroke (optional) | Always | Z2 | No | 3 | No | Low | high_fall_risk | – | +10 | Moderate |
| 13* | COPD (optional) | Always (pulmonary rehab) | Z2 by breathlessness | No (default) | 3 | No | Low | – | – | +10 | Limited (not fully checked) |
| 14* | Cancer survivor (optional) | Suggested; always if bone metastases / lymphoedema / in treatment | Z2–Z3 | Not by default | 2 | No | Yes unless bone metastases | – | – | +5 | Moderate (abstract only) |

EP = Expert Practice. Detailed rationale, prompts, stop signs and wording cautions follow.

---

## 3. High blood pressure (hypertension)

### (a) Guidance

- Physical activity lowers BP and slows CVD progression in adults with hypertension (strong evidence). BP falls immediately after a session and stays lower for up to about 24 h [PES19 checked; ACSM-HT checked]. ACSM's current FITT advice is 90–150 min/week of multimodal, moderate-intensity exercise (aerobic, resistance or both) on most days. Resistance exercise now "measures up to" aerobic exercise as antihypertensive lifestyle therapy [ACSM-HT checked].
- **Thresholds where exercise should wait** (sources differ, and none is a universal "never exercise" line):
  - ESC 2020: resting SBP >160 mmHg means a maximal exercise test "should be postponed until the BP is controlled". Uncontrolled BP means temporary restriction from competitive sport [ESC20 checked].
  - AAESS/SMA 2009: resting BP ≥180 systolic or ≥110 diastolic means postpone training and see a doctor promptly. During exercise, SBP >250 or DBP >115 means stop [AAESS09 checked]. The 2019 ESSA update exists, but only its abstract was readable [ESSA19-HT located].
  - AHA 2013: resting BP >200/110 mmHg is a *relative* contraindication to exercise *testing*. An exaggerated response (>250/115) is a relative indication to stop a test [AHA13 checked].
- **Breath-holding / Valsalva.** ESC 2020 recommends avoiding the Valsalva manoeuvre and advises resistance training 2–3 days/week. Correctly performed higher-intensity dynamic resistance training does not raise BP more than low-intensity training [ESC20 checked]. AAESS 2009 advises "no breath holding" and avoiding heavy isometric lifting because of its "pronounced pressor effect" [AAESS09 checked]. The ACSM T2D consensus also says people with hypertension should avoid heavy lifting and breath-holding [KAN22 checked]. Paluch/AHA says controlled hypertension can safely do low-to-moderate resistance training with proper breathing, and to use extended cool-downs with antihypertensive medicines to prevent post-exercise hypotension [AHA24-RT checked].
- **Isometric exercise training (IET), newer evidence:**
  - A network meta-analysis of 270 RCTs (n = 15,827) found these SBP/DBP reductions [EDW23 checked]:

    | Mode | SBP (mmHg) | DBP (mmHg) |
    |---|---|---|
    | Isometric | −8.24 | −4.00 |
    | Combined | −6.04 | −2.54 |
    | Dynamic resistance | −4.55 | −3.04 |
    | Aerobic | −4.49 | −2.53 |
    | HIIT | −4.08 | −2.50 |

    IET ranked first, and wall squat ranked first among sub-modes. The authors urge caution: there were few IET trials and no head-to-head comparisons.
  - A 2024 review [EDW24 checked] describes the typical protocols:
    - handgrip 4 × 2 min at 30 % MVC with 1–4 min rest, 3×/week (the only mode endorsed in guidelines);
    - wall squat 4 × 2 min.
  - The same review reports the safety evidence:
    - wall-squat acute peaks averaged about 171/113 mmHg;
    - adverse-event data are limited (8 events in IET groups vs 1 in controls in one meta-analysis);
    - people "often naturally hold their breath", so teach continuous breathing;
    - IET is contraindicated in connective-tissue disorders and thoracic aortic disease, and needs caution in coronary disease;
    - there is no safety evidence in obesity, diabetes or mobility limitation.
  - Guideline uptake is cautious:
    - ESC 2024 lists "dynamic or isometric resistance training 2–3 times per week" alongside ≥150 min/week moderate aerobic activity [ESC24-HT located; PCR24 checked secondary].
    - The EAPC/ESC consensus says isometric training may be first-line mainly for people with normal BP plus risk factors, and that the evidence is limited [EAPC22 checked].
    - A 2024 BJSM editorial says evidence for IET as *treatment* of hypertension is "limited and conflicting" [HP24 checked, partial].
- The 2025 AHA/ACC BP guideline (replaces the 2017 guideline) exists. I could not read its exercise section [AHA25-HT located].

### (b) Proposed table entries

**1a. High BP, controlled** (user says a clinician has told them it is controlled)

| Control | Value |
|---|---|
| Clearance | Not needed if controlled. Optional prompt for people >65 starting vigorous work (AAESS09 suggests medical evaluation for older adults with grade 1+ hypertension before starting). |
| Max zone / HIIT | Z3 / Yes, after ≥4 weeks of regular Z1–Z2 training (EP) |
| Min RIR / failure | 2 / No (EP: reps near failure provoke breath-holding) |
| Impact | Allowed |
| Tags to avoid | `breath_hold_max` (1–3RM tests, grinding max reps) |
| Joint limits | none |
| Extra warm-up / cool-down | 0 / **+5 min cool-down** [AHA24-RT] |
| Positions | none required (an optional caution on prolonged `head_down` is EP with low evidence) |
| Prompts | "Breathe steadily: breathe out as you lift, in as you lower. Don't hold your breath." · "Finish with an easy cool-down and stand up slowly." · Optional: "Wall-sits and grip holds are fine. Keep breathing and stop well before shaking." |
| Stop signs (specific) | sudden severe headache; blurred or changed vision; confusion or weakness on one side (these are stroke signs, so treat as an emergency); plus the universal red flags |

**1b. High BP, not controlled / not sure / new diagnosis not yet reviewed**

| Control | Value |
|---|---|
| Clearance | "Suggest a doctor's OK" now; **required before Z3, HIIT or heavy lifting** |
| Max zone / HIIT | Z2 / No |
| Min RIR / failure | 3 / No |
| Impact | Low–moderate allowed |
| Tags to avoid | `breath_hold_max`, `isometric_heavy` (including long wall-sits) until controlled [AAESS09, EDW24] |
| Extra warm-up / cool-down | +5 / +5 |
| Prompts | as in 1a, plus "Because your BP may not be under control yet, we're keeping things moderate. Check with your doctor before harder sessions." |
| Optional "today's reading" gate | *If* the PO adds an optional BP check-in (see regulatory caution below): user-entered resting ≥180 systolic or ≥110 diastolic → no session today, suggest contacting their doctor promptly [AAESS09]; 160–179 / 100–109 → force 1b limits for the day [ESC20 >160 test-postponement logic, applied by analogy: EP]. |

### (c) Confidence

- Exercise lowers BP: **High Evidence** [PES19, EDW23].
- Avoid Valsalva / heavy isometric holds when uncontrolled: **Moderate Evidence / Expert Practice** (consensus, physiological rationale).
- IET as a BP *intervention*: **Moderate** for the BP effect (GRADE moderate per EDW24) and **Limited** for safety.
- Specific "wait" thresholds: **Expert Practice**. Bodies disagree (160, 180/110, 200/110), and most are written for exercise *testing* or competitive sport.
- RIR / zone numbers: **Expert Practice**.

### (d) Wording cautions

- Do not say "this programme lowers your blood pressure" or "isometric protocol for hypertension". That is a treatment claim (see section 15).
- Do not interpret BP readings ("your BP is high/dangerous"). If an optional reading gate is built, phrase the outcome as "Let's skip hard training today. If readings like this continue, talk to your doctor." Do not label values as abnormal. Note that FDA 2026 cautions against wellness products referencing diagnostic thresholds (section 15).
- No medication timing advice. "Cool down and stand up slowly" is acceptable; "because of your BP tablets" is not.

---

## 4. Type 2 diabetes

### (a) Guidance

- ACSM 2022 consensus [KAN22 checked: full text via PDXScholar]:
  - **Aerobic:** 150–300 min/week moderate (40–59 % HRR, RPE 11–12) or 75–150 min vigorous (60–89 %, RPE 14–17). Train 3–7 days/week with **no more than 2 consecutive days without activity**.
  - **Resistance:** 2–3 days/week, **never on consecutive days**. Use 8–10 exercises, 1–3 sets of 10–15 reps, at 50–69 % 1RM (moderate) to 70–85 % 1RM (vigorous). Progress resistance first, then sets, then frequency.
  - **Flexibility and balance:** at least 2–3 days/week.
  - **Breaking up sitting** helps postprandial glucose.
- **HIIT** in KAN22 is "a potentially time-efficient modality". However, "adverse events have been reported in 34% of studies" in a meta-analysis, mostly musculoskeletal, and chronic intense training can cause transient post-exercise hyperglycaemia. Progress gradually [KAN22 checked].
- **Clearance** [KAN22 checked]:
  - Low-to-moderate activity such as brisk walking needs no evaluation "unless symptoms of CVD or microvascular complications are present".
  - "In adults who are currently sedentary, medical clearance is recommended before participation in moderate- to high-intensity PA."
  - Clearance is advised before vigorous work for people with CVD signs, longer diabetes duration, older age or complications.
  - This is consistent with ACSM 2015 screening [RIE15 checked].
- **Low blood sugar** (insulin or "insulin secretagogues" such as sulfonylureas; possibly meglitinides) [KAN22 checked]:
  - It is "important to carry rapid-acting carbohydrate sources during PA", with glucagon available for those prone to severe lows.
  - Supplement carbohydrate or reduce insulin "as needed". This is a clinician-guided action; **the app must not translate it into numbers.**
  - Late-onset lows are possible after long or intense sessions.
- **High glucose** [KAN22 checked]: do not start with glucose >250 mg/dL if moderate or high ketones are present; take caution >300 mg/dL without ketones and "only begin if feeling well". SGLT2-inhibitor users can develop DKA at near-normal glucose. *These are clinical-value rules; see (d).*
- **Complications** [KAN22 checked, Table 5]:
  - **Retinopathy:** exercise is "contraindicated for anyone with unstable or untreated proliferative retinopathy", recent laser (panretinal photocoagulation) or other recent eye surgery. With severe or unstable proliferative retinopathy, avoid vigorous, high-intensity breath-holding activities and overhead lifting, and avoid head-lowering or head-jarring activities. Without a measured HRmax, use RPE 10–12. Consult an ophthalmologist.
  - **Peripheral neuropathy:** limit activities that risk foot trauma (prolonged hiking, jogging, uneven surfaces). Non-weight-bearing options may suit better. No water exercise with unhealed plantar ulcers. Check feet daily, wear well-fitting shoes and dry socks, and avoid activities demanding excessive balance.
  - **Autonomic neuropathy:** higher risk of lows, abnormal BP responses and impaired thermoregulation. Use RPE rather than heart rate. It is an indication for stress testing at any age (Table 3).
  - **Kidney disease:** avoid large BP spikes and breath-holding.
- Hydrate and avoid the hottest part of the day [KAN22 via GuidelineCentral, checked secondary].
- ADA *Standards of Care in Diabetes—2026*, Section 5 (physical activity) is very likely the newest ADA text. **I could not open it** (PMC blocked). Treat as **unverified**; check before release.

### (b) Proposed table entries

**2. Type 2 diabetes (base entry)**

| Control | Value |
|---|---|
| Clearance | Before vigorous / HIIT. The existing ACSM-2015 screen already routes inactive users with metabolic disease to clearance before moderate work [RIE15, KAN22]. |
| Max zone / HIIT | Z3 / Yes after clearance and ≥4–6 weeks of regular training (EP, given KAN22's adverse-event note) |
| Min RIR / failure | 2 / No (EP) |
| Impact | Allowed (unless 2n applies) |
| Scheduling | `max_consecutive_rest_days = 2`; resistance never on consecutive days [KAN22] |
| Extra warm-up | +5 (EP) |
| Prompts | "Short walks after meals and breaking up long sitting help." · **Low-sugar prompt, shown only if the user ticks "I take insulin or a diabetes medicine that can cause low blood sugar":** "Carry a fast-acting sugar snack when you train, and know the signs of a low (shaky, sweaty, confused, unusually hungry). Lows can happen during or several hours after exercise. Your diabetes care team can tell you how to plan around training." · "Drink water before, during and after, and avoid the hottest part of the day." · "If you feel unwell or your diabetes plan says not to exercise right now, skip today." |
| Stop signs | shaky, sweaty, confused, blurred vision or sudden weakness (possible low) → stop and follow your low-sugar plan; unusual thirst, nausea, vomiting or abdominal pain → stop and seek advice; plus chest discomfort or unusual breathlessness (ESC 2020 flags these specifically for people with diabetes) [ESC20 checked] |

**2r. Modifier: diabetic eye disease (retinopathy)**

- Clearance: **Always**. Prompt: "Ask your eye doctor what's safe."
- If the user says the eye disease is unstable or recently treated by laser or surgery: **no programme until cleared** [KAN22].
- Max zone Z2. If the user reports "severe", cap at RPE ≤12 (about Z1–low Z2). HIIT: No. Min RIR 3. Failure: No.
- Impact: No (no jumping or jarring).
- Avoid tags: `breath_hold_max`, `head_down`, `jumping`, overhead heavy pressing (`overhead` with load above light).
- Stop signs: sudden floaters, flashes, a "curtain", or loss of vision → stop and seek urgent eye care.

**2n. Modifier: nerve damage / numbness in feet (peripheral neuropathy)**

- Impact: No. Avoid `jumping`, `uneven_surface_running` and `high_fall_risk`. Prefer bike, rower, seated and machine work.
- Ankle/foot joint limit ≤2 (EP).
- Prompts: daily foot check, well-fitting shoes, dry socks. "No swimming or water workouts if you have an open sore on your foot."
- Stop sign: a new blister, wound or redness on the foot → pause weight-bearing work and get it checked.

**2a. Modifier: autonomic neuropathy** (user-reported, for example "diabetes affects my heart rate or BP")

- Clearance: Always. Max zone Z2 **by RPE only** (hide HR-zone targets). HIIT: No. Min RIR 3.
- Extra warm-up +10 and cool-down +10.
- Prompts: "Change position slowly." Heat and hydration caution.

### (c) Confidence

- Aerobic, resistance and frequency targets: **High Evidence** [KAN22].
- HIIT allowed after a base period: **Moderate Evidence** (benefit shown; musculoskeletal adverse events common).
- Hypoglycaemia carbohydrate advice: **Moderate Evidence / consensus**.
- Retinopathy, neuropathy and autonomic precautions: **Expert Practice / consensus** (KAN22 does not grade them; they rest on physiological rationale and case data).
- The RIR numbers and the "≥4–6 weeks before HIIT" rule: **Expert Practice**.

### (d) Wording cautions

- Never suggest carb grams, insulin or tablet changes, or glucose targets. The KAN22 glucose and ketone thresholds (>250 mg/dL with ketones; >300 caution) are **clinical-value decision rules**. If the app asks for a glucose reading and gates the session on it, it is processing patient data to drive a health decision, which is the core of the device definitions (section 15). Recommendation: **do not encode glucose or ketone thresholds.** Use "If your diabetes plan says not to exercise right now, skip today" instead.
- Asking "Do you take a medicine that can cause low blood sugar?" is fine. It tailors safety tips and does not advise on the medicine.
- Avoid "manage", "control" or "improve your diabetes/HbA1c". Use "stay active safely with diabetes".

---

## 5. Type 1 diabetes (brief)

### (a) Guidance

Source: Riddell 2017 consensus [RID17 checked, accepted manuscript].

- **Pre-exercise glucose bands (mmol/L):**

  | Band | Consensus action |
  |---|---|
  | <5 | Take 10–20 g glucose and delay exercise until >5 |
  | 5–6.9 | Take 10 g before aerobic exercise; anaerobic exercise or HIIT can start |
  | 7–10 | Target range |
  | 10.1–15 | Exercise can start; anaerobic work may raise glucose |
  | >15 | Check ketones; light exercise only if ketones ≤1.4; avoid if ≥1.5 |

- **Recent severe low:** severe hypoglycaemia (≤2.8 mmol/L or needing assistance) in the previous 24 h contraindicates exercise.
- **Exercise type:** aerobic exercise usually lowers glucose. Sprints, weightlifting and HIIT typically raise it. Doing resistance work *before* aerobic work blunts the drop.
- **Late lows:** hypoglycaemia risk stays elevated for ≥24 h, with the highest nocturnal risk after afternoon exercise. Avoid unsafe solo settings (for example swimming alone).
- Newer related documents (ISPAD 2022 exercise chapter; EASD/ISPAD 2020 CGM-during-exercise statement) exist but were **not opened** → **unverified**.

### (b) Proposed table entry

| Control | Value |
|---|---|
| Clearance | Before vigorous (ACSM metabolic-disease rule) [RIE15] |
| Max zone / HIIT | Z3 / Yes (HIIT and resistance are not riskier for lows; they may raise glucose) |
| Min RIR / failure | 2 / No (app default) |
| Impact | Allowed |
| Extra warm-up | +5 |
| Session ordering | Offer "strength first, cardio after" as the default order (RID17: blunts glucose drop). This is a training-order choice, not a medical instruction. |
| Prompts | "Check your glucose before, during and after training as your care plan advises." · "Carry fast-acting sugar." · "Lows can happen up to a day later, especially overnight after afternoon/evening sessions." · "Avoid training alone in risky settings." · Optional check-in: "Have you had a severe low in the last 24 hours?" → if yes: "Your diabetes guidelines advise skipping exercise today." |
| Stop signs | symptoms of a low; feeling unwell with nausea or vomiting (possible ketones) → stop and follow your care plan |

### (c) Confidence

- The safety concepts (lows during and after exercise, carry carbohydrate, late lows): **Moderate Evidence** (consensus).
- Glucose bands: **Expert Practice / consensus**. Do not encode them (see below).

### (d) Wording cautions

- **Do not encode RID17's glucose bands, carbohydrate grams or insulin-reduction percentages.** Doing so would be dose/therapy advice. That is regulated territory, and the MHRA's own example of a device is software that calculates insulin from carbohydrate intake [COV16 checked secondary].
- The "severe low in 24 h" check-in is a borderline safety gate. Keep it optional and neutrally worded.

---

## 6. Known heart condition (coronary disease, heart attack/stent, heart failure, arrhythmia)

### (a) Guidance

- **ACSM 2015 screening** [RIE15 checked; algorithm via IDEA Fit, checked secondary]:
  - Not exercising regularly + known CV disease (asymptomatic): "Medical clearance for exercise is recommended", then "light- to moderate-intensity exercise".
  - Exercising regularly + known disease (asymptomatic): continue moderate; get clearance before vigorous.
  - Any signs or symptoms: stop and seek clearance.
  - Symptoms listed: chest/neck/arm/jaw discomfort, breathlessness at rest or mild exertion, dizziness or fainting, unusual fatigue.
- **ESC 2020 sports cardiology** [ESC20 checked; EMJ checked secondary]:
  - Chronic coronary syndrome: risk-stratify with a maximal exercise test or functional imaging. People with normal tests and preserved LV function "may be considered as low risk". Leisure exercise gives "greater control of effort" than competitive sport.
  - Heart failure: start exercise "only in clinically stable patients", with reassessment when intensity increases.
  - The evidence base is "rather low" (expert opinion).
- **ESC 2024 AF:** "A tailored exercise program" is recommended for paroxysmal or persistent AF to improve fitness and reduce recurrence [ESC24-AF checked secondary via ACC].
- **2023 AHA/ACC chronic coronary disease guideline:** exercise is recommended for all patients with CCD without contraindications, and cardiac rehab is recommended for eligible patients [CCD23 checked secondary via ACC].
- **AHA 2023/24 resistance-training statement** [AHA24-RT checked]:
  - RT combined with aerobic training is safe in clinically stable HF.
  - RT at even 20 % 1RM improves strength after an acute coronary event.
  - Start at 40–60 % 1RM; lower loads with higher reps for clinical populations.
  - Implanted pacemaker/ICD: consult a physician before upper-body RT.
  - Hypertrophic cardiomyopathy: advised to avoid RT (older statements allowed low-intensity machines).
  - After sternotomy: unweighted, pain-free upper-limb work, arms close to the body.
  - Warning signs (dizziness, excessive breathlessness, chest pain or pressure, palpitations) "require immediate medical evaluation". Stop RT until cleared.
- **Isometric training** is strongly contraindicated with thoracic aortic disease or connective-tissue disorders, and needs care in coronary disease [EDW24 checked].
- I could not read the 2021 ESC and 2022 AHA/ACC/HFSA HF guideline exercise recommendations → **unverified**.

### (b) Proposed table entry

**4. Known heart condition**

| Control | Before user attests clearance | After clearance (unlock only the attested scope) |
|---|---|---|
| Clearance | **Always**. Suggest cardiac rehab if recently diagnosed or after an event/procedure. | n/a |
| Max zone | Z2 (talk-test pace, RPE ≤13) | Z2, or Z3 if "cleared for vigorous" |
| HIIT | No | Only if explicitly "cleared for intervals" |
| Min RIR / failure | 3 / No | 2 / No |
| Impact | Low only | As cleared |
| Avoid tags | `breath_hold_max`, `isometric_heavy` | `breath_hold_max` |
| Extra warm-up / cool-down | +10 / +10 | +10 / +10 |
| Prompts | "Your heart condition means we start gently until your doctor or cardiac rehab team says you can do more." · "Use effort (how hard it feels) rather than heart-rate targets if you have an irregular heartbeat or take heart-rate-slowing medicine." (EP; avoid naming drugs) | same |
| Stop signs | chest pain/pressure/tightness; unusual breathlessness; dizziness or light-headedness; palpitations or a racing/irregular heartbeat; unusual fatigue → **stop; if not settling within a few minutes, call emergency services**; do not resume until cleared [AHA24-RT, RIE15] | same |

**Sub-flags (user-selectable):**
- **Heart failure:** only if the user says it is stable; Z2 cap until cleared [ESC20].
- **Pacemaker/ICD:** "suggest a doctor's OK before upper-body weights" [AHA24-RT].
- **Recent heart surgery through the breastbone:** no loaded `overhead`, no heavy pushing or pulling until the surgical team clears it [AHA24-RT].
- **Aortic disease, Marfan or other connective-tissue disorder, or cardiomyopathy:** clearance always; avoid `isometric_heavy` and `breath_hold_max` permanently unless cleared [EDW24, AHA24-RT].

### (c) Confidence

- Exercise and cardiac rehab are beneficial in stable CAD, HF and AF: **High Evidence** for rehab/exercise in CAD/HF (from guideline class statements; the classes themselves were not read this session) and **Moderate** for AF.
- Clearance and conservative start for a consumer app: **Expert Practice / consensus** [RIE15, ESC20].
- Exact caps: **Expert Practice**.

### (d) Wording cautions

- Never present the app as cardiac rehabilitation or "safe for your heart condition".
- Do not adjust based on heart-rate or ECG data from wearables as a cardiac assessment.
- The stop-sign → emergency instruction is a generic safety message and acceptable.

---

## 7. Asthma (exercise-induced bronchoconstriction, EIB)

### (a) Guidance

- **ATS 2013 EIB guideline** [ATS13 checked]:
  - Pre-exercise **interval or combination warm-up** for all patients with EIB: *strong recommendation, moderate-quality evidence*. Continuous low- or high-intensity warm-ups did not reach significance.
  - A device or mask that warms and humidifies air in **cold weather**: weak recommendation, low-quality evidence.
  - Pre-exercise SABA for all with EIB: strong, high-quality evidence. This is a medicine; the app must not advise it.
  - Environmental associations: cold dry air (ice rinks, Nordic skiing), pool trichloramines, ozone, allergens and particulates.
- **AAAAI 2022 work group** [AAAAI22 checked]:
  - Running, cycling, swimming, HIIT and walking "were shown to be safe and to improve asthma outcomes".
  - Moderate-to-vigorous activity is ideal.
  - Use warm-up and cool-down. Extreme weather and poor air quality are triggers. Pool chemicals need caution.
  - Unless baseline control is poor, exercise intolerance should not limit activity.
  - Add exercise to the asthma action plan.
- **GINA 2026 report** exists (released 2026) [GINA26 located]. I could not read its physical-activity/EIB section, so no GINA-specific claims are made here.

### (b) Proposed table entry

| Control | Value |
|---|---|
| Clearance | Not needed if controlled. If not controlled (frequent symptoms, recent attack, recent oral steroid course or emergency visit): "Get your asthma reviewed before hard training" and apply Z2 / no HIIT until the user confirms it is controlled (EP). |
| Max zone / HIIT | Z3 / Yes when controlled [AAAAI22] |
| Min RIR / failure | app default |
| Impact | Allowed |
| Extra warm-up | **+10 min, interval-style** (for example 6–8 short efforts with easy recovery) [ATS13: interval/combination warm-up; the exact format is EP] |
| Environment prompts | Cold, dry air: "cover your mouth and nose with a scarf or mask, or train indoors" [ATS13 weak]. High pollen or poor air quality days: suggest indoor training (EP). Swimming pools: "if pool air bothers your breathing, choose another session" [ATS13, AAAAI22]. |
| Prompts | "Follow your asthma action plan for exercise and keep your reliever with you." · "Cool down gradually." |
| Stop signs | wheeze, chest tightness or cough that doesn't settle with rest and your usual plan; can't speak in full sentences; blue or grey lips → emergency |

### (c) Confidence

- Warm-up: **Moderate Evidence** (strong recommendation, moderate-quality evidence).
- Mask in cold air: **Limited Evidence**.
- Exercise is safe and beneficial: **Moderate Evidence** (AAAAI review; not a GRADE guideline).
- Environmental avoidance: **Expert Practice**.

### (d) Wording cautions

- Do not tell users to take an inhaler before exercise, or how much. That is medication advice. "Follow your asthma action plan" is acceptable because it defers to the user's own clinician-issued plan.

---

## 8. Knee or hip osteoarthritis

### (a) Guidance

- **OARSI 2019** [OARSI19 checked]:
  - Knee OA core treatment: structured land-based exercise (strengthening, cardio and/or balance/neuromuscular) and mind-body exercise (Tai Chi, yoga), with or without weight management. These are "effective and safe for all patients with Knee OA, regardless of comorbidity".
  - Hip OA core treatment: structured land-based exercise.
  - Aquatic exercise: conditionally recommended for the knee.
- **ACR/AF 2019 (published 2020)** [ACR20 checked]:
  - Exercise is **strongly recommended** for knee and hip OA. Weight loss (if overweight), self-management and Tai Chi are strong. Balance exercise is conditional.
  - No hierarchy among exercise types; prescription specifics are insufficient to recommend.
  - Supervised programmes work better.
  - "There is no uniformly accepted level of pain at which a patient should or should not exercise."
- **NICE NG226 (2022)** [NG226 checked]:
  - 1.3.1: offer therapeutic exercise tailored to needs (strengthening and general aerobic fitness).
  - 1.3.3: advise that regular exercise "will be beneficial for their joints" even if it initially causes pain or discomfort, and that joint pain "may increase when they start therapeutic exercise".
  - 1.3.5: any weight loss helps; 10 % is likely better than 5 %.
- **EULAR 2018:** physical activity is "integral part of standard care" in OA. 4 principles and 10 recommendations [EULAR18 checked: abstract only; the individual recommendations were not read].
- **Pain-monitoring rule (GLA:D programme):** "Pain up to five was 'acceptable' during and after the exercise session", and pain should return to "pain as usual" by the next morning; otherwise reduce the level [GLAD12 checked] (0–10 VAS). The 2017 GLA:D description does not restate it [GLAD17 checked].

### (b) Proposed table entry (one entry per joint: "knee OA", "hip OA")

| Control | Value |
|---|---|
| Clearance | Not needed |
| Max zone / HIIT | Z3 / Yes, on low-impact modes (bike, rower, elliptical, pool) (EP) |
| Min RIR / failure | 2 / No (EP) |
| Impact | Low by default. `impact_unlock_rule`: after 4 weeks with the pain rule satisfied, allow low-volume impact if the user opts in (EP). |
| Joint limits | Affected joint ≤2 at start → ≤3 after 2 consecutive weeks meeting the pain rule (EP) |
| Tags | `jumping` avoided at start; `deep_knee_flexion` (knee OA) / deep hip flexion (hip OA) **not banned, but range limited to comfortable depth** (EP; no guideline bans them) |
| Pain gate rule | During exercise ≤5/10 is acceptable; by next morning back to usual → continue or progress; >5/10 or still worse next morning → reduce load or range for that joint [GLAD12] |
| Extra warm-up | +5 (EP) |
| Prompts | "Some extra joint discomfort when you start exercising is common and expected; it usually settles as you get stronger." [NG226 1.3.3] · "Regular strengthening and cardio are among the most recommended things for people with joint arthritis." (general statement) |
| Stop signs | a hot, very swollen joint; the joint locking or giving way; sudden severe pain; pain still worse than usual the next day (reduce load, and if it recurs, suggest a health professional) |

### (c) Confidence

- Exercise for knee/hip OA: **High Evidence** (OARSI core; ACR strong; NICE).
- Pain ≤5/10 with a next-day rule: **Expert Practice** (programme protocol, widely used; ACR says no accepted threshold).
- Impact restriction and joint numbers: **Expert Practice**. Guidelines do not grade impact; ACR does not discuss it.

### (d) Wording cautions

- **This is the most "treatment-like" entry.** OARSI and NICE call exercise a *core treatment* for OA. An app that markets "OA exercise therapy" or "reduces arthritis pain" makes a treatment/alleviation claim. Frame it as "training with a sore or arthritic knee: joint-friendly adjustments", not as therapy (section 15).
- Do not tell users they have OA from symptoms. They self-select a condition they have been told about.

---

## 9. Low back pain

### (a) Guidance

- **NICE NG59** [NG59 checked: recs 1.2.1–1.2.6]:
  - 1.2.1: advice and information that includes "encouragement to continue with normal activities".
  - 1.2.2: consider a group exercise programme; choose the type of exercise based on needs, preferences and capabilities.
  - 1.2.3: do not offer belts or corsets. 1.2.4–1.2.6: no orthotics, rocker-sole shoes or traction.
  - **Update 29 July 2026:** recs 1.2.13–1.2.14 (psychological components) withdrawn; the exercise-centred approach is unchanged [NG59-2026 checked secondary, Physitrack; confirm on nice.org.uk].
- **WHO 2023 chronic primary LBP guideline:** structured exercise therapy or programmes (conditional recommendation, low certainty); structured education and advice (conditional, very low certainty); lumbar braces, belts or supports not recommended for routine care [WHO23 checked secondary via GuidelineCentral].
- **NHS (reviewed 5 Mar 2026)** [NHS-BP checked]:
  - Self-care: "stay active and try to continue with your daily activities". Stop a stretch if pain worsens. Avoid long bed rest.
  - See a GP if pain does not improve after a few weeks, is worse at night, comes with weight loss, a lump or a change in back shape, or is between the shoulder blades.
  - **999 / A&E** for: pain, tingling, weakness or numbness in both legs; loss of feeling around genitals or anus; bladder or bowel changes; new sexual dysfunction; chest pain; pain after a serious accident.
- No specific exercise type has been shown superior (NICE: choose by preference). *Recent Cochrane reviews (for example Hayden 2021) were not opened → unverified.*

### (b) Proposed table entry

| Control | Value |
|---|---|
| Clearance | Not needed. The red-flag screen gates instead. |
| Max zone / HIIT | Z3 / Yes as tolerated |
| Min RIR / failure | 2 on `spinal_loading` exercises (EP) / No |
| Impact | Allowed as tolerated |
| Flare mode (user taps "my back is flaring") | spine joint limit ≤2; avoid heavy `spinal_loading` and loaded `spinal_flexion`; keep walking, cycling and gentle mobility (EP). Exit flare mode after 3 sessions without pain flare-up the next day (EP). |
| Extra warm-up | +5 (EP) |
| Prompts | "Keeping active helps back pain recover; long rest in bed usually doesn't." [NHS-BP, NG59] · "Pick exercise you enjoy and can do regularly." [NG59 1.2.2] · "Mild discomfort is OK; ease off any move that makes pain sharply worse." |
| Do not | Recommend back belts or braces for back pain [NG59 1.2.3, WHO23]. A performance lifting belt for heavy squats is a separate topic; keep it out of the LBP entry. |
| Stop signs (show prominently) | **Emergency:** numbness or tingling around the genitals or bottom; new difficulty peeing or loss of bowel control; weakness or numbness in both legs; chest pain; back pain after a serious accident. **See a doctor soon:** pain worse at night or not easing with rest; unexplained weight loss; feeling feverish or unwell; pain between the shoulder blades; not improving after a few weeks [NHS-BP]. |

### (c) Confidence

- Stay active and exercise for LBP: **Moderate Evidence** (WHO conditional/low; NICE).
- No belts: **Limited Evidence** (WHO very low certainty; NICE "do not offer").
- Flare-mode limits: **Expert Practice**.
- Red flags: **Expert Practice / clinical consensus** (NHS public guidance).

### (d) Wording cautions

- Do not "triage" causes of back pain. Present the red-flag list as "get help now if…", with no inference about what it means.
- Avoid "fix your back" or "treats sciatica".

---

## 10. Osteoporosis / osteopenia

### (a) Guidance

- **ROS "Strong, Steady and Straight" consensus (2022)** [ROS22 checked]:
  - **Strong:** progressive resistance 2–3 days/week at the "maximum that can be lifted 8–12 times", building to 3 sets, covering all muscle groups including the back.
  - **Impact:** for most people without vertebral or multiple fractures, moderate impact (jogging, hopping, low-level jumping) on most days with "at least 50 impacts per session". With vertebral or multiple low-trauma fractures, impact "up to brisk walking" (150 min/week), plus individualised physiotherapy at least at the start.
  - **Steady:** balance plus strength ≥2×/week. For people who already fall, highly challenging, supervised balance work (3 h/week, ≥4 months).
  - **Straight:** back-extensor work (3–5 reps, 3–5 s holds, ≥2×/week). Avoid or modify "sustained, repeated or end-range flexion" and excessive spinal curving under load. Use a hip hinge with a straight upper back.
  - Overall, "the benefits, in general, outweigh the risks". Do not restrict activity based on BMD alone.
- **ESSA 2017 (Beck et al.):** bone responds to impact and high-intensity progressive resistance training. "Loaded spine flexion is not recommended." Impact may need modification with OA or frailty [ESSA17 checked: abstract]. The specific intensity numbers (for example ≥80–85 % 1RM in supervised programmes) were **not read → unverified**.
- **IOF guidance:**
  - Aerobic exercise and progressive resistance are safe.
  - High-risk people should avoid trunk flexion exercise and "powerful twisting movements of the trunk", and avoid forward bending while carrying objects.
  - People with osteoporosis or previous fractures should consult a professional before starting [IOF checked].
- *BHOF (US) Clinician's Guide 2022 and Osteoporosis Canada "Too Fit To Fracture" were not opened → unverified.*

### (b) Proposed table entries

**8a. Osteoporosis / osteopenia (no spine fracture)**

| Control | Value |
|---|---|
| Clearance | Suggested, not required. "If you've had a fracture, check with your doctor or physio before starting." [IOF] |
| Max zone / HIIT | Z3 / Yes (EP) |
| Min RIR / failure | 1–2 (ROS's "max you can lift 8–12 times" implies RIR about 0–2; I suggest 1–2 as a safety margin, EP) / No |
| Impact | **Encouraged:** moderate impact, ≥50 impacts per session, most days, if no OA or frailty limits [ROS22, ESSA17] |
| Tags to avoid | loaded `spinal_flexion` (crunches/sit-ups with load, loaded forward bends, toe-touches under load); `loaded_spinal_rotation` (powerful twisting) [ROS22, ESSA17, IOF] |
| Required inclusions | balance work ≥2×/week; back-extensor work ≥2×/week [ROS22] |
| Extra warm-up | +5 (EP) |
| Positions | avoid sustained, end-range or repeated flexion |
| Prompts | "Bend from the hips (hip hinge) with a straight back when lifting." · "Strength, impact and balance exercise are recommended for bone health." |
| Stop signs | sudden sharp back pain during or after a lift or bend (possible fracture) → stop and get it checked |

**8b. Modifier: spine fracture or more than one low-trauma fracture**

- Clearance: strongly suggested (physio input at the start) [ROS22].
- Impact: **No** (up to brisk walking only). Avoid `jumping`, all loaded `spinal_flexion`, `loaded_spinal_rotation` and `high_fall_risk`.
- Spine joint limit ≤2 (EP). Min RIR 3 (EP). HIIT: No (EP).

### (c) Confidence

- Resistance plus impact for bone: **Moderate Evidence** (RCTs; consensus).
- Balance training reduces falls: **High Evidence** (ROS cites fall/fracture reductions).
- Avoiding loaded flexion and twisting: **Expert Practice** (consistent consensus; biomechanical rationale).
- RIR and zones: **Expert Practice**.

### (d) Wording cautions

- Do not compute fracture risk or interpret DEXA or T-scores. Do not say "builds bone density" as a promise. "Recommended for bone health" is a general statement.

---

## 11. Pregnancy

### (a) Guidance

- **2019 Canadian guideline (Mottola et al.)** [CAN19 checked]:
  - **Activity:** "All women without contraindication should be physically active throughout pregnancy". At least 150 min/week of moderate activity over ≥3 days. Combine aerobic and resistance work. Pelvic floor training may be done daily (weak). If light-headed, nauseous or unwell lying flat on the back, change position and avoid supine exercise (weak).
  - **Absolute contraindications:** ruptured membranes; premature labour; unexplained persistent vaginal bleeding; placenta praevia after 28 weeks; pre-eclampsia; incompetent cervix; intrauterine growth restriction; high-order multiples; uncontrolled type 1 diabetes, hypertension or thyroid disease; other serious cardiovascular, respiratory or systemic disorders.
  - **Relative contraindications** (discuss with the obstetric provider): recurrent pregnancy loss; gestational hypertension; previous spontaneous preterm birth; mild or moderate CV or respiratory disease; symptomatic anaemia; malnutrition; eating disorder; twins after 28 weeks; other significant conditions.
  - **Stop and seek care:** persistent excessive breathlessness not resolving with rest; severe chest pain; regular painful contractions; vaginal bleeding; persistent fluid leakage; persistent dizziness or faintness not resolving with rest.
  - **Avoid:** scuba; activities with contact or fall risk (riding, downhill skiing, ice hockey, gymnastics, **Olympic lifts**); non-stationary cycling; altitude >2500 m if not living at altitude; excessive heat or humidity, including hot yoga.
- **ACOG Committee Opinion 804 (2020)** [ACOG20 located; summary checked secondary via OPQIC]:
  - Encourage exercise. Women who habitually did vigorous aerobic activity before pregnancy can generally continue during pregnancy and postpartum.
  - Without complications, activity is "safe and desirable".
  - ACOG's specific RPE, supine and warning-sign tables **were not read → unverified**. I found no newer ACOG document; check acog.org for reaffirmation status.

### (b) Proposed table entry

| Control | Value |
|---|---|
| Clearance | **Always:** "Talk with your pregnancy care provider about your exercise plan." If the user ticks any absolute contraindication item (show the CAN19 list in plain words) → **no training programme**; show only "please follow your provider's advice". Relative items → require the user to attest "my provider has OK'd exercise". |
| Max zone / HIIT | Z2 (talk test: can hold a conversation) / No. Z3 is allowed only if the user was regularly doing vigorous training before pregnancy *and* attests the provider's OK [ACOG20 summary]. HIIT off (EP). |
| Min RIR / failure | 3 / No (EP; avoids breath-holding) |
| Impact | Allowed only if the user was already doing it before pregnancy and it feels comfortable; auto-suggest reducing if pelvic pressure, leaking or pain occur (EP) |
| Tags to avoid | `breath_hold_max`, `high_fall_risk`, `contact`, Olympic lifts [CAN19], `supine_lying` (EP default: substitute incline or side-lying versions from about 20 weeks, and *any time* the user reports dizziness or nausea lying flat [CAN19 weak]), `prone_lying` later in pregnancy (EP) |
| Extra warm-up | +5 (EP) |
| Environment | heat/humidity check and no hot rooms; hydration [CAN19]. Altitude >2500 m and scuba notes are informational only [CAN19]. |
| Prompts | "Aim for regular moderate activity most days." · "Pelvic floor exercises can be done daily; a pelvic health physio can teach the technique." [CAN19] · "If you feel dizzy or unwell lying on your back, switch position." |
| Stop signs | CAN19 list above → stop and contact your provider or maternity unit |
| Phase field | `phase_by_weeks`: optional. Changes defaults (for example supine substitution from ~20 weeks: EP). |

### (c) Confidence

- Activity is safe and beneficial without contraindications: **High Evidence** (systematic-review-based guideline).
- Contraindication and stop lists: **Expert Practice / consensus**.
- Supine guidance: **Limited Evidence** (weak recommendation).
- Avoidance list: **Expert Practice**.
- HIIT-off and RIR numbers: **Expert Practice**.

### (d) Wording cautions

- Do not assess pregnancy complications. Present the contraindication list as "if your provider has told you any of these apply, don't use the training plan".
- Avoid prescriptive weekly targets presented as medical advice. Phrase them as general guidelines.
- No nutrition or weight-gain advice (out of scope by design).

---

## 12. After giving birth (first 12 months, brief)

### (a) Guidance

- **2025 Canadian postpartum guideline (Davenport et al., BJSM)** [CAN25 checked]:
  - Be active without contraindications (strong).
  - Accumulate ≥120 min/week of MVPA over ≥4 days, aerobic plus resistance (strong).
  - **Daily pelvic floor muscle training (strong, high certainty)**, ideally with instruction from a pelvic floor physio.
  - Start or return to MVPA within the first 12 weeks (strong, for mental health).
  - Begin with light walking and PFMT. Progress to MVPA once incisions or tears have healed and lochia does not increase with activity (conditional).
  - Progression should be "individualised, gradual and symptom based" (conditional).
  - Box 1 relative contraindications include severe abdominal pain, non-menstrual vaginal bleeding, caesarean symptoms worsening with MVPA, exertional chest pain, dizziness, calf pain or swelling, and breathlessness at rest.
  - A "Get Active Questionnaire for Postpartum" is the pre-screen [CAN25; editorial checked].
- **UK return-to-running guideline (Goom, Donnelly, Brockwell 2019)** [GOOM19 checked]:
  - "Return to running is not advisable prior to 3 months postnatal". Aim for 3–6 months.
  - Low-impact timeline: weeks 0–2 walking, pelvic floor and core; 2–4 add squats, lunges, bridges; 4–6 static bike or cross-trainer; 6–12 power walking, later swimming or spin.
  - **Load and impact tests** to pass without pain, heaviness, dragging or leakage: walk 30 min; single-leg balance 10 s; single-leg squat 10 per side; jog on the spot 1 min; forward bounds ×10; hop in place 10 per leg; single-leg "running man" 10 per side.
  - Strength targets of about 20 reps (single-leg calf raise, single-leg bridge, single-leg sit-to-stand, side-lying abduction).
  - Pelvic health assessment offered from 6 weeks. Refer for incontinence, heaviness or dragging, pelvic or low-back pain, a noticeable midline gap or doming, or bleeding beyond 8 weeks.
  - Abdominal fascia has regained only 51–59 % of its strength at 6 weeks.
  - Mostly **expert consensus**.

### (b) Proposed table entry (phased)

| Phase | Clearance | Max zone | HIIT | Min RIR | Impact | Notes |
|---|---|---|---|---|---|---|
| 0–6 wk, or until healed and bleeding not increasing with activity | Suggest the routine postnatal check; pelvic health assessment from 6 wk [GOOM19] | Z1 | No | 4 (light only) | No | walking + daily PFMT [CAN25] |
| 6–12 wk | Attest "healed; no pelvic floor symptoms" | Z2 | No | 3 | No | low-impact cardio, progressive strength; core work without doming (EP) |
| ≥12 wk | Impact unlocked only after the user self-records passing the GOOM19 tests | Z3 | Yes after impact unlocked (EP) | 2 | Yes, progressive | running build-up |

- Tags to avoid early: `jumping`, running, `breath_hold_max`.
- Extra warm-up: +5.
- Prompts: daily pelvic floor exercises; "If you notice leaking, heaviness/dragging, or a bulge or dome along your tummy during exercise, ease off that move and consider seeing a pelvic health physio" [GOOM19]; breastfeeding: feed or express before training [GOOM19].
- Stop signs: heavy or increasing bleeding; severe abdominal pain; worsening caesarean wound pain; calf pain or swelling; chest pain; dizziness; breathlessness at rest → stop and seek care [CAN25].

### (c) Confidence

- MVPA ≥120 min/week and PFMT: **High/Moderate Evidence** (strong recommendations).
- The 3-month no-running rule and the impact tests: **Expert Practice** (GOOM19 Level 4 consensus).
- Phase boundaries and RIR: **Expert Practice**.

### (d) Wording cautions

- Do not diagnose prolapse or diastasis. Phrase it as "symptoms worth getting checked".
- Do not advise on lochia or wound healing beyond "once healed and your provider is happy".

---

## 13. Obesity (brief)

### (a) Guidance

- **Cut-offs:**
  - WHO general: ≥30 / ≥35 (standard; not re-checked).
  - **Asian populations:** a WHO expert consultation kept the international cut-offs but added public-health action points at **23, 27.5, 32.5 and 37.5 kg/m²**. Many Asian people have high diabetes/CVD risk below 25 [WHO04 checked].
  - **India 2025 revised definition** (India Obesity Commission) [IND25 checked]:
    - Generalised obesity at BMI >23.
    - Stage 2 requires BMI >23 plus raised waist (≥90 cm men / ≥80 cm women, or waist-to-height ratio >0.5) plus a functional limitation or comorbidity.
    - BMI grades: I 23–24.9, II 25–27.5, III 27.6–32.4, IV ≥32.5.
- **Exercise:**
  - 150–250 min/week of moderate activity prevents weight gain; >250 min/week is associated with clinically significant weight loss. Resistance training does not enhance weight loss but improves fat-free mass and risk [ACSM09 checked: abstract].
  - EASO: exercise yields modest weight loss but other benefits remain. Endurance training is best for visceral/liver fat; strength training is best for strength. Increase volume gradually and tailor to capacity and risks [EASO21 checked secondary].
  - IND25: ≥60 min/day of activity, resistance training ≥3 days/week, individualised gradual progression, and a pre-exercise evaluation [IND25 checked].
- **Heat, joints and impact:** I found no guideline-graded impact rule for obesity → impact and joint limits are **Expert Practice**. Heat and hydration caution is general practice (also in KAN22 for T2D).

### (b) Proposed table entries

**Trigger logic.** User-selected, or computed from height and weight if the PO wants that. Use the higher-risk cut-offs if the user identifies as South Asian:
- 11a: ≥30 (South Asian ≥27.5)
- 11b: ≥35 (South Asian ≥32.5)

These South Asian numbers are WHO action points. Using them as *exercise-caution* triggers is EP. *Do not use India's >23 obesity definition to trigger restrictions*: it would put most Indian users into conservative mode without any evidence that exercise limits are needed at that level.

| Control | 11a | 11b |
|---|---|---|
| Clearance | Not needed for BMI alone (not an ACSM 2015 trigger) [RIE15] | Suggested (IND25 advises pre-exercise evaluation; EP for the cut-point) |
| Max zone / HIIT | Z3 / Yes on low-impact modes after a 4-week base | Z2 for 6–8 weeks → Z3 / HIIT after base, bike or rower only (EP) |
| Min RIR / failure | 2 / No | 2 / No |
| Impact | Allowed, low volume (EP) | Low-impact only at start; unlock by `impact_unlock_rule` (EP) |
| Joint limits | knee ≤3 | knee ≤2 → 3 (EP) |
| Extra warm-up | +5 | +5 |
| Positions | – | offer alternatives to floor-to-stand transitions and long `supine_lying` if uncomfortable (EP) |
| Prompts | "Build up gradually; fitness gains count even when the scale doesn't move." [EASO21 gist] · heat/hydration · "Choose supportive shoes; low-impact cardio is easier on joints." | same |
| Stop signs | universal red flags; signs of heat illness (dizziness, nausea, headache, confusion) → stop, cool down, hydrate | same |

### (c) Confidence

- Activity volume for weight: **Moderate Evidence** [ACSM09].
- Fitness and metabolic benefit regardless of weight change: **Moderate–High** [EASO21].
- Impact, joint and zone caps: **Expert Practice**.
- South Asian cut-offs as caution triggers: **Expert Practice**, built on WHO04 / IND25.

### (d) Wording cautions

- Do not give calorie or diet advice. IND25's 500 kcal/day deficit is out of scope by design.
- Avoid "treat obesity". Use weight-neutral, non-stigmatising language.

---

## 14. Optional conditions (time-limited; lower verification)

### 12*. Previous stroke

- **Guidance** [AHA14-ST checked]:
  - Graded exercise testing with ECG monitoring is recommended before a programme. If that is not feasible, start at lower intensity rather than delaying.
  - Aerobic 40–70 % HRR (RPE 11–14), 3–5 d/week, 20–60 min (or 10–15 min bouts), with 5–10 min warm-up and cool-down.
  - Resistance 1–3 sets × 10–15 reps, 8–10 exercises, 50–80 % 1RM, 2–3 d/week. Balance work 2–3 d/week.
  - Falls occurred in 13–25 % of trial participants. Use handrails or seated options with hemiparesis or balance problems.
- **Entry:** clearance always; Z2; no HIIT; RIR 3; low impact; avoid `high_fall_risk` and unsupported single-leg work; +10 warm-up and cool-down.
- **Stop signs:** new face droop, arm weakness or speech difficulty (FAST) → emergency.
- **Confidence:** Moderate Evidence (AHA statement); caps are EP.

### 13*. COPD

- GOLD 2026 lists exercise training, pulmonary rehabilitation and tele-rehabilitation among non-drug strategies [GOLD26 checked secondary, US Pharmacist]. I did not read GOLD's or ATS/ERS's dosing → **unverified**.
- **Entry:** clearance always ("ask about pulmonary rehab"); intensity by breathlessness (Z2); no HIIT by default; +10 warm-up.
- **Stop signs:** breathlessness much worse than usual or not settling; chest pain; blue lips.
- **Confidence:** Limited (not adequately verified).

### 14*. Cancer survivors

- ACSM 2019 roundtable: exercise training and testing are generally safe. Specific doses of aerobic, resistance or combined training improve anxiety, depression, fatigue, physical function and HRQoL. Evidence on neuropathy and cognition is uncertain [CAM19 checked: abstract only].
- The commonly quoted doses (about 30 min moderate aerobic 3×/week plus resistance 2×/week) and the special cautions (bone metastases, lymphoedema, ostomy, neuropathy, during chemotherapy or radiotherapy) are **unverified** (not in the abstract read).
- **Entry:** clearance suggested; always if in active treatment, with bone metastases or lymphoedema; Z2–Z3; HIIT not by default; RIR 2; impact allowed unless bone metastases.
- **Confidence:** Moderate for benefit; details unverified.

---

## 15. Regulatory boundary: when does condition-tailored exercise software become a medical device?

*Factual and brief. Not legal advice. The PO should take regulatory advice before a public release in any of these markets.*

### United States (FDA)

- **General Wellness: Policy for Low Risk Devices**, final guidance re-issued **6 Jan 2026**, supersedes 2019 [FDA26 located (fda.gov permission withdrawn); content checked secondary: King & Spalding, Loeb & Loeb, Faegre Drinker].
- Two kinds of wellness intended use:
  - (1) general health, such as fitness, weight or sleep;
  - (2) healthy-lifestyle claims about reducing the risk or impact of certain chronic diseases where that lifestyle role is well understood.
- The product must also be low-risk and non-invasive.
- **Excluded:** claims to diagnose, treat, mitigate, cure or prevent disease; claims or outputs that "prompt or guide specific clinical action or medical management"; acting as a substitute for an authorised device; clinical-looking values unless validated.
- "See a professional" notifications are acceptable only if they do not name diseases or diagnostic thresholds (stated for sensor products).
- *FDA's separate "Policy for Device Software Functions and Mobile Medical Applications" was not opened → unverified.*

### European Union (MDR 2017/745)

- Software is a device when the manufacturer intends a medical purpose. Purposes include diagnosis, prevention, monitoring, prediction, prognosis, treatment or alleviation of disease (MDR Art. 2(1)) [MDR17 located (EUR-Lex text not extractable); content checked secondary: Mason Hayes & Curran (mhc.ie), Taylor Wessing].
- Recital 19 states that software intended for lifestyle and well-being purposes is not a medical device [**unverified**, from memory].
- **Rule 11 (Annex VIII)** [checked secondary: OpenRegulatory, Taylor Wessing]:
  - Software that provides information used to take decisions with diagnostic or therapeutic purposes is **class IIa** by default.
  - It rises to IIb for serious deterioration or surgery, and to III for death or irreversible deterioration.
  - Software that monitors physiological processes is IIa (IIb for vital parameters with immediate danger).
  - All other device software is class I.
- **MDCG 2019-11 rev.1 (17 June 2025):** intended purpose is decisive. The revision adds Rule 11 and Annex XVI examples, including wellness/lifestyle borderline examples [MDCG19-11 located; checked secondary: Emergo, OpenRegulatory]. The same software can be a device or not "depending on what you say it is for".

### United Kingdom (MHRA, UK MDR 2002)

- The guidance *Medical device stand-alone software including apps* was last updated **1 July 2023** [MHRA23 checked: landing page]. Its content via Covington's summary of the 2016 edition [COV16 checked secondary]:
  - Fitness and lifestyle apps (for example heart-rate monitoring for fitness) are "in general not medical devices".
  - Apps that recommend seeking advice *based on user-entered data* are likely devices.
  - An insulin-from-carbohydrate calculator is a device.
  - Disclaimers ("not a medical device", "for information only") do not avoid regulation if the intended use meets the definition.
- **Crafting an intended purpose for SaMD (22 Mar 2023)** [MHRA-IP23 checked]:
  - Intended purpose is judged objectively from labelling, IFU, promotional material and technical documentation.
  - It includes the "clinical condition that is to be diagnosed, prevented, monitored, treated".
  - It warns of "function creep" into device claims.

### India (CDSCO, Medical Device Rules 2017)

- CDSCO **finalised medical device software guidance (reported 30 July 2026)** [CDSCO26 checked secondary: Emergo]:
  - Medical device software is "any software intended for a medical purpose", including SaMD and some mobile apps.
  - **General wellness apps are outside scope.**
  - Standalone software is classed A–D under the 2017 Rules, by the seriousness of the condition and the significance of the information to healthcare decisions.
  - Device software needs licences (test, manufacture, import).

### What this means for the condition table: wellness-side boundaries (synthesis, Expert Practice)

**Keep (wellness side):**
- General fitness training with **conservative safety limits** when a user says they have a condition.
- Generic safety prompts.
- Generic "check with your doctor before X" and "stop and get help if Y".
- Deferring to the user's own clinician or plan ("follow your asthma action plan").
- Category-2 style statements such as "regular activity, as part of a healthy lifestyle, may help people living with type 2 diabetes".

**Avoid (pushes toward device):**
1. Claims that the plan treats, manages, relieves or reduces a disease or marker: "lowers BP", "OA pain-relief programme", "controls blood sugar", "rehab after heart attack".
2. Reading clinical values (BP, glucose, ketones, SpO₂, ECG/HRV) and applying **thresholds** to decide what the user may do, or labelling values abnormal.
3. Any dose, medication, carbohydrate or insulin advice.
4. Interpreting symptoms into a likely cause (triage or diagnosis).
5. Personalised "you should see a doctor because your data shows…" outputs (MHRA reading).
6. Marketing, app-store text or onboarding that names target patient groups ("for hypertensive patients").

**Borderline items to review with a regulatory adviser:**
- (a) the optional BP-reading gate in 1b;
- (b) the T1D "severe low in 24 h" check-in;
- (c) the OA pain-rule auto-progression (pain-driven load adjustment for a named disease could be read as alleviation);
- (d) the condition list itself, if marketed as a selling point ("adapts to your medical conditions").

**Data protection (not a device question, but release-relevant):** health conditions are special-category or sensitive personal data under GDPR Art. 9, UK GDPR and India's DPDP Act 2023 [**unverified**, not checked this session].

---

## 16. Source list (full citations and verification labels)

**Screening / cross-cutting**
- [RIE15] Riebe D, Franklin BA, Thompson PD, Garber CE, Whitfield GP, Magal M, Pescatello LS. Updating ACSM's recommendations for exercise preparticipation health screening. *Med Sci Sports Exerc.* 2015;47(11):2473–2479. doi:10.1249/MSS.0000000000000664. **checked** (citation and abstract: digitalcommons.uri.edu/kinesiology_facpubs/180); algorithm branches **checked (secondary)**: ideafit.com "Getting to the heart of pre-exercise screening".
- [GETP12] American College of Sports Medicine; Ozemek C (ed.). *ACSM's Guidelines for Exercise Testing and Prescription*, 12th ed. Wolters Kluwer/LWW; 2025. ISBN 9781975219215. **located** (AbeBooks listing); content not read. An exam-prep summary attributing "defer if resting ≥200/110" to ACSM was seen but is not authoritative and is not used.

**Hypertension**
- [PES19] Pescatello LS, Buchner DM, Jakicic JM, Powell KE, Kraus WE, Bloodgood B, et al.; 2018 PAGAC. Physical Activity to Prevent and Treat Hypertension: A Systematic Review. *Med Sci Sports Exerc.* 2019;51(6):1314–1323. doi:10.1249/MSS.0000000000001943. **checked** (abstract, scholars.duke.edu/publication/1388020).
- [ACSM-HT] Pescatello LS. "Exercise and hypertension" (ACSM web article). https://acsm.org/exercise-hypertension. **checked**.
- [ESC20] Pelliccia A, Sharma S, Gati S, et al. 2020 ESC Guidelines on sports cardiology and exercise in patients with cardiovascular disease. *Eur Heart J.* 2021;42(1):17–96. doi:10.1093/eurheartj/ehaa605. **checked** (academic.oup.com; HTN and CCS sections; HF/AF sections not reached). EMJ congress review (Malik A. *EMJ Cardiol.* 2020;8[1]:23–25) **checked (secondary)**.
- [ESC24-HT] McEvoy JW, McCarthy CP, Bruno RM, et al. 2024 ESC Guidelines for the management of elevated blood pressure and hypertension. *Eur Heart J.* 2024;45(38):3912–4018. doi:10.1093/eurheartj/ehae178. **located** (exercise section not reached).
- [PCR24] Lauder L, Mahfoud F. A review of the 2024 ESC Guidelines on elevated blood pressure and hypertension. PCRonline, 13 Nov 2024. https://pcronline.com/Cases-resources-images/Tools-and-Practice/The-Essentials/Hypertension/A-review-of-the-2024-ESC-Guidelines-on-elevated-blood-pressure-and-hypertension. **checked (secondary)**.
- [EAPC22] Hanssen H, et al. Personalised exercise prescription in the prevention and treatment of arterial hypertension: a Consensus Document from the EAPC and the ESC Council on Hypertension. *Eur J Prev Cardiol.* 2022;29(1):205–215 (volume and pages from the journal URL). **checked** (author manuscript, ora.ox.ac.uk/objects/uuid:302593d4-…); DOI **unverified**.
- [AHA25-HT] Jones DW, Ferdinand KC, Taler SJ, et al. 2025 AHA/ACC/AANP/AAPA/ABC/ACCP/ACPM/AGS/AMA/ASPC/NMA/PCNA/SGIM Guideline for the Prevention, Detection, Evaluation and Management of High Blood Pressure in Adults. *J Am Coll Cardiol.* 2025;86(18):1567–1678. doi:10.1016/j.jacc.2025.05.007. **located** (citation via iro.uiowa.edu; exercise content not read).
- [AHA13] Fletcher GF, Ades PA, Kligfield P, et al. Exercise Standards for Testing and Training: A Scientific Statement From the American Heart Association. *Circulation.* 2013;128 (pages 873–934 **unverified**). doi:10.1161/CIR.0b013e31829b5b44. **checked** (bumc.bu.edu PDF).
- [AAESS09] Sharman JE, Stowasser M. Australian Association for Exercise and Sports Science position statement on exercise and hypertension. *J Sci Med Sport.* 2009;12:252–257. **checked** (sma.org.au PDF); DOI **unverified**.
- [ESSA19-HT] Sharman JE, Smart NA, Coombes JS, Stowasser M. Exercise and sport science australia position stand update on exercise and hypertension. *J Hum Hypertens.* 2019;33(12):837–843. doi:10.1038/s41371-019-0266-z. **located** (abstract only).
- [EDW23] Edwards JJ, et al. (repository lists Edwards J, Wiles J, O'Driscoll J, Deenmamode AHP, Griffiths M, Arnold O, Cooper NJ; published author order **unverified**). Exercise training and resting blood pressure: a large-scale pairwise and network meta-analysis of randomised controlled trials. *Br J Sports Med.* 2023;57(20):1317– (end page unverified). doi:10.1136/bjsports-2022-106503. **checked** (bjsm.bmj.com; citation via pure.canterbury.ac.uk).
- [EDW24] Edwards JJ, Coleman DA, Ritti-Dias RM, Farah BQ, Stensel DJ, Lucas SJE, et al. Isometric Exercise Training and Arterial Hypertension: An Updated Review. *Sports Med.* 2024. doi:10.1007/s40279-024-02036-x. **checked**.
- [HP24] Hanssen H, Pescatello LS. Is isometric exercise training the best FIT for exercise prescription in the prevention and treatment of arterial hypertension? *Br J Sports Med.* 2024;58(4):231– . **checked** (partial).

**Cardiac / resistance training**
- [AHA24-RT] Paluch AE, Boyer WR, Franklin BA, et al. Resistance Exercise Training in Individuals With and Without Cardiovascular Disease: 2023 Update: A Scientific Statement From the AHA. *Circulation.* 2024;149:e217–e231. doi:10.1161/CIR.0000000000001189. **checked** (full-text PDF); ACC "Ten points" also **checked (secondary)**.
- [ESC24-AF] Van Gelder IC, Rienstra M, Bunting KV, et al. 2024 ESC Guidelines for the management of atrial fibrillation. *Eur Heart J.* 2024. doi:10.1093/eurheartj/ehae176. **checked (secondary)** (acc.org Ten points, 17 Sep 2024).
- [CCD23] Virani SS, Newby LK, Arnold SV, et al. 2023 AHA/ACC/ACCP/ASPC/NLA/PCNA Guideline for the Management of Patients With Chronic Coronary Disease. *J Am Coll Cardiol.* 2023. doi:10.1016/j.jacc.2023.04.003. **checked (secondary)** (acc.org Ten points).
- 2021 ESC HF and 2022 AHA/ACC/HFSA HF guidelines: **unverified** (not opened).

**Diabetes**
- [KAN22] Kanaley JA, Colberg SR, Corcoran MH, Malin SK, Rodriguez NR, Crespo CJ, Kirwan JP, Zierath JR. Exercise/Physical Activity in Individuals with Type 2 Diabetes: A Consensus Statement from the American College of Sports Medicine. *Med Sci Sports Exerc.* 2022;54(2):353–368. doi:10.1249/MSS.0000000000002800. **checked** (full text: pdxscholar.library.pdx.edu/sph_facpub/479; also GuidelineCentral summary).
- [RID17] Riddell MC, Gallen IW, Smart CE, et al. Exercise management in type 1 diabetes: a consensus statement. *Lancet Diabetes Endocrinol.* 2017;5(5):377–390. doi:10.1016/S2213-8587(17)30014-1. **checked** (accepted manuscript, discovery.dundee.ac.uk).
- ADA *Standards of Care in Diabetes—2026*, Section 5: **unverified** (not opened).
- ISPAD 2022 exercise chapter; Moser et al. 2020 EASD/ISPAD CGM statement: **unverified** (seen in search results only).

**Asthma**
- [ATS13] Parsons JP, Hallstrand TS, Mastronarde JG, et al.; ATS Subcommittee on EIB. An Official American Thoracic Society Clinical Practice Guideline: Exercise-induced Bronchoconstriction. *Am J Respir Crit Care Med.* 2013;187(9):1016–1027. doi:10.1164/rccm.201303-0437ST. **checked** (thoracic.org PDF).
- [AAAAI22] Nyenhuis SM, Kahwash B, Cooke A, Gregory KL, Greiwe J, Nanda A. Recommendations for Physical Activity in Asthma: A Work Group Report of the AAAAI Sports, Exercise, and Fitness Committee. *J Allergy Clin Immunol Pract.* 2022;10(2):433–443. doi:10.1016/j.jaip.2021.10.056. **checked**.
- [GINA26] Global Initiative for Asthma. 2026 GINA Strategy Report (Global Strategy for Asthma Management and Prevention). https://ginasthma.org/2026-gina-strategy-report/. **located** (exercise/EIB section not reached).

**Osteoarthritis**
- [OARSI19] Bannuru RR, Osani MC, Vaysbrot EE, et al. OARSI guidelines for the non-surgical management of knee, hip, and polyarticular osteoarthritis. *Osteoarthritis Cartilage.* 2019;27:1578–1589. doi:10.1016/j.joca.2019.06.011. **checked** (ESCEO-hosted PDF).
- [ACR20] Kolasinski SL, Neogi T, Hochberg MC, et al. 2019 American College of Rheumatology/Arthritis Foundation Guideline for the Management of Osteoarthritis of the Hand, Hip, and Knee. *Arthritis Rheumatol.* 2020;72(2):220–233. doi:10.1002/art.41142. **checked** (Northwestern-hosted PDF). (Also published in *Arthritis Care Res* 2020;72(2):149–162: **unverified**.)
- [EULAR18] Rausch Osthoff A-K, Niedermann K, Braun J, et al. 2018 EULAR recommendations for physical activity in people with inflammatory arthritis and osteoarthritis. *Ann Rheum Dis.* 2018;77(9):1251–1260. doi:10.1136/annrheumdis-2018-213585. **checked** (abstract only, eprints.soton.ac.uk/421741).
- [NG226] National Institute for Health and Care Excellence. Osteoarthritis in over 16s: diagnosis and management (NG226). 2022. https://www.nice.org.uk/guidance/ng226. **checked** (recs 1.3.1–1.3.5); publication date **unverified** (not displayed).
- [GLAD12] Skou ST, Odgaard A, Rasmussen JO, Roos EM. Group education and exercise is feasible in knee and hip osteoarthritis. *Dan Med J.* 2012;59(12):A4554. **checked**.
- [GLAD17] Skou ST, Roos EM. Good Life with osteoArthritis in Denmark (GLA:D™). *BMC Musculoskelet Disord.* 2017;18:72. doi:10.1186/s12891-017-1439-y. **checked**.

**Low back pain**
- [NG59] NICE. Low back pain and sciatica in over 16s: assessment and management (NG59). 2016; updated (latest reported 29 Jul 2026). https://www.nice.org.uk/guidance/ng59. **checked** (recs 1.2.1–1.2.6); 2026 update **checked (secondary)** (Physitrack "NICE low back pain guideline update 2026").
- [WHO23] World Health Organization. WHO guideline for non-surgical management of chronic primary low back pain in adults in primary and community care settings. Geneva: WHO; 2023 (7 Dec 2023). **checked (secondary)** (guidelinecentral.com/guideline/3352095); ISBN **unverified**.
- [NHS-BP] NHS. Back pain. https://www.nhs.uk/conditions/back-pain/ (reviewed 5 Mar 2026). **checked**.

**Osteoporosis**
- [ROS22] Brooke-Wavell K, Skelton DA, Barker KL, Clark EM, De Biase S, Arnold S, Paskins Z, Robinson KR, Lewis RM, Tobias JH, Ward KA, Whitney J, Leyland S. Strong, steady and straight: UK consensus statement on physical activity and exercise for osteoporosis. *Br J Sports Med.* 2022;56(15):837–846. doi:10.1136/bjsports-2021-104634. **checked** (bjsm.bmj.com; Loughborough and Warwick repositories). Warwick lists the pages as 929–944; the BJSM URL and Loughborough give 837.
- [ESSA17] Beck BR, Daly RM, Singh MAF, Taaffe DR. Exercise and Sports Science Australia (ESSA) position statement on exercise prescription for the prevention and management of osteoporosis. *J Sci Med Sport.* 2017;20(5):438–445. doi:10.1016/j.jsams.2016.10.001. **checked** (abstract, ro.ecu.edu.au); the issue number "(5)" is **unverified**.
- [IOF] International Osteoporosis Foundation. Exercise for individuals with osteoporosis. https://www.osteoporosis.foundation/health-professionals/prevention/exercise/exercise-individuals-with-osteoporosis. **checked** (undated).

**Pregnancy / postpartum**
- [CAN19] Mottola MF, Davenport MH, Ruchat S-M, et al. 2019 Canadian guideline for physical activity throughout pregnancy. *J Obstet Gynaecol Can.* 2018;40(11):1549–1559. doi:10.1016/j.jogc.2018.07.001; co-published *Br J Sports Med.* 2018;52(21):1339– . **checked** (bjsm.bmj.com/content/52/21/1339); BJSM DOI **unverified**.
- [ACOG20] American College of Obstetricians and Gynecologists. Physical Activity and Exercise During Pregnancy and the Postpartum Period. Committee Opinion No. 804. *Obstet Gynecol.* 2020;135(4):e178–e188 (pages **unverified**). doi:10.1097/AOG.0000000000003772. **located** (OPQIC summary **checked (secondary)**; acog.org and LWW failed to load).
- [CAN25] Davenport MH, Ruchat S-M, Jaramillo Garcia A, Ali MU, Forte M, Beamish N, Fleming K, Adamo KB, Brunet-Pagé É, Chari R, Lane KN, Mottola MF, Neil-Sztramko SE. 2025 Canadian guideline for physical activity, sedentary behaviour and sleep throughout the first year post partum. *Br J Sports Med.* 2025;59(8):515– . **checked** (bjsm.bmj.com/content/59/8/515); DOI **not seen**. Editorial: Davenport MH, *Br J Sports Med* 2025;59(8):513, **checked**.
- [GOOM19] Goom T, Donnelly G, Brockwell E. Returning to running postnatal – guidelines for medical, health and fitness professionals managing this population. March 2019. https://athleticsni.org/download/files/Returning_to_running_postnatal_guideline_for_medical_health_and_fitness_professionals_managing_this_population.05.pdf. **checked**.

**Obesity**
- [WHO04] WHO Expert Consultation. Appropriate body-mass index for Asian populations and its implications for policy and intervention strategies. *Lancet.* 2004;363(9403):157–163. doi:10.1016/S0140-6736(03)15268-3. **checked** (abstract, research.monash.edu).
- [IND25] Misra A, Vikram NK, Ghosh A, Ranjan P, Gulati S; India Obesity Commission. Revised definition of obesity in Asian Indians living in India. *Diabetes Metab Syndr.* 2025;19:102989. doi:10.1016/j.dsx.2024.102989. **checked** (cmcendovellore.org PDF).
- [ACSM09] Donnelly JE, Blair SN, Jakicic JM, et al. ACSM Position Stand. Appropriate physical activity intervention strategies for weight loss and prevention of weight regain for adults. *Med Sci Sports Exerc.* 2009;41(2):459–471. doi:10.1249/MSS.0b013e3181949333. **checked** (abstract, read.qxmd.com); author list and pages **unverified**.
- [EASO21] Oppert J-M, et al. Exercise training in the management of overweight and obesity in adults: synthesis of the evidence and recommendations from the EASO Physical Activity Working Group. *Obes Rev.* 2021 (supplement; volume and DOI **unverified**). **checked (secondary)** (easo.org news page).

**Optional conditions**
- [AHA14-ST] Billinger SA, Arena R, Bernhardt J, et al. Physical activity and exercise recommendations for stroke survivors: a statement for healthcare professionals from the AHA/ASA. *Stroke.* 2014;45:2532–2553. doi:10.1161/STR.0000000000000022. **checked** (muhc.ca PDF).
- [CAM19] Campbell KL, Winters-Stone KM, Wiskemann J, et al. Exercise Guidelines for Cancer Survivors: Consensus Statement from International Multidisciplinary Roundtable. *Med Sci Sports Exerc.* 2019;51(11):2375–2390. doi:10.1249/MSS.0000000000002116. **checked** (abstract, experts.nau.edu).
- [GOLD26] Global Initiative for Chronic Obstructive Lung Disease. Global Strategy for the Diagnosis, Management, and Prevention of COPD: 2026 Report. **checked (secondary)** (U.S. Pharmacist, 1 Dec 2025).

**Regulatory**
- [FDA26] U.S. Food and Drug Administration. General Wellness: Policy for Low Risk Devices. Final guidance, 6 Jan 2026. https://www.fda.gov/regulatory-information/search-fda-guidance-documents/general-wellness-policy-low-risk-devices. **located** (fetch permission withdrawn); content **checked (secondary)** (King & Spalding; Loeb & Loeb; Faegre Drinker, Jan 2026).
- [MDR17] Regulation (EU) 2017/745 on medical devices (5 Apr 2017). https://eur-lex.europa.eu/eli/reg/2017/745/oj/eng. **located**. Rule 11 **checked (secondary)**; recital 19 wording **unverified**.
- [MDCG19-11] MDCG 2019-11 rev.1. Qualification and classification of software – Regulation (EU) 2017/745 and 2017/746. June 2025. **located**; content **checked (secondary)** (Emergo by UL, 20 Jun 2025; OpenRegulatory).
- [MHRA23] MHRA. Medical devices: software applications (apps) (guidance landing page; PDF "Medical device stand-alone software including apps"). Last updated 1 Jul 2023. https://www.gov.uk/government/publications/medical-devices-software-applications-apps. **checked** (landing page); PDF content via [COV16].
- [COV16] Covington & Burling. MHRA seeks to clarify whether an app is a regulated medical device. Oct 2016. **checked (secondary)**.
- [MHRA-IP23] MHRA. Crafting an intended purpose in the context of Software as a Medical Device (SaMD). 22 Mar 2023. https://www.gov.uk/government/publications/crafting-an-intended-purpose-in-the-context-of-software-as-a-medical-device-samd. **checked**.
- [CDSCO26] CDSCO guidance on Medical Device Software (final), reported 30 Jul 2026. **checked (secondary)** (emergobyul.com "India CDSCO finalizes guidance on medical device software"); primary CDSCO document **not opened**.
- India Medical Device Rules, 2017 (classes A–D): **checked (secondary)** via CDSCO26.

---

## 17. Gaps and next verification steps (priority order)

1. **ADA Standards of Care 2026, Section 5** (newest diabetes exercise text). Not opened.
2. **ACOG CO 804** full text: intensity (RPE), supine timing, warning-sign table, and current reaffirmation status. Not opened.
3. **2025 AHA/ACC BP guideline** exercise section and **ESC 2024** Recommendation Table 15 (class/level for isometric). Not reached.
4. **GINA 2026** EIB section. Not reached.
5. **FDA 2026 General Wellness** primary text and **MDCG 2019-11 rev.1** primary text. Read them to confirm the secondary summaries, especially wellness examples involving exercise and chronic disease.
6. **Beck 2017** dosing table and **BHOF 2022** for osteoporosis intensity numbers.
7. HF guideline class statements (ESC 2021/2023, AHA/ACC/HFSA 2022); Campbell 2019 dose and safety tables; GOLD 2026 rehab section.
8. Before release: a regulatory adviser to review the four borderline features listed in section 15, plus app-store and onboarding copy.
