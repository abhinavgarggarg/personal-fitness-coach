# Research Update 1.1: lose fat, keep muscle (30+), health conditions, steps and your own starting numbers

Date: 9 October 2026 · Rule Registry 1.0.1 → proposed 1.1.0 · Change requests CR-001, CR-002, CR-003, CR-005 (`docs/change_requests/README.md`) · Status: **awaiting your sign-off**

## In one paragraph

You asked for weight loss for people over 30 as the main purpose of the app, with the balance of strength training, cardio and HIIT changing with age, and with no food features. You also asked for a health-condition picker, a step counter, and your own starting weights. I reviewed the research for all of these: 49 sources on fat loss, age and steps, and 57 on health conditions and medical-device rules. Each source is labelled checked, located or unverified. The evidence supports your direction:
- Steady cardio (mostly walking and easy machine work) does most of the fat loss.
- Strength training protects muscle, and muscle matters more each decade.
- HIIT saves time but burns no more fat.

What changes with age is the hard work. Tempo cardio and intervals shrink and become joint-friendly, light-and-fast "power" work is added from 50, and balance work grows. Strength stays at 3 days a week at every age. This becomes 9 new rules, 4 small changes to existing rules and a 21-entry health-condition table, all waiting for your approval.

## What I need from you

1. **Approve Registry 1.1.0 and the health-condition table v1.0 draft** (details below). Reply "APPROVED", or tell me what to change. Approval lets me build these rules. Rules that rest on sources I could only read through summaries are marked "approved — verify source", and I check those sources before Phase 4 testing (see Limits).
2. **One product decision: cardio machines (MOD-001).** Today, treadmills, stationary bikes and stair machines are never used, because that was your personal equipment list. For a weight-loss app for people over 30, incline treadmill walking and bikes are the most common joint-friendly cardio. I recommend making these a per-person choice: your profile keeps today's exclusions, and other users can turn them on. Treadmill running stays off by default for the fat-loss goal because of impact.
3. **Schedule.** Applying these rules to the engine adds one part before the data layer, so Phase 3 becomes 7 parts:
   - Part 3: engine changes for 1.1
   - Part 4: data layer
   - Parts 5–7: screens, coach, integration

## What the evidence says

### Fat loss from exercise, with no diet

| Finding | Source | Confidence |
|---|---|---|
| Each extra 30 min a week of aerobic exercise gives about −0.5 kg and −0.6 cm of waist (116 trials, adults with overweight, mean age 46) | Jayedi 2024, JAMA Netw Open | Moderate (waist: High) |
| About 150 min a week gives about −2.8 kg and about −2 percentage points of body fat. About 300 min a week gives about −4.2 kg and −4 to −5 cm of waist. | Jayedi 2024 | Moderate (waist: High) |
| On average, expect about 2–3 kg from exercise alone, at 150–200 min a week | EASO 2021 (Oppert) | High |
| No trial shows that exercise alone prevents weight regain. Figures of 200–300 min a week for keeping weight off are expert opinion. | Bellicha 2021; EASO 2021 | Moderate |
| HIIT is time-efficient but not better than steady cardio for fat loss | Viana 2019; Sanca-Valeriano 2023; ACSM 2024 consensus | High–Moderate |
| Strength training alone: about −1 kg fat and +0.8 kg muscle. It does not reduce weight much by itself. | Lopez 2022 (114 trials) | High |
| When weight falls, strength training protects muscle. In older adults it prevented about 94% of muscle loss (+0.82 kg). | Sardeli 2018; Villareal 2017 | High |
| Strength plus cardio gives the best result: cardio for fat, strength for muscle, and the largest drop in waist | Willis 2012; Villareal 2017 | Moderate |

### Age and muscle (your point)

- **You are right that muscle matters more with age, but one popular number is wrong.** The often-quoted "3–8% of muscle lost per decade after 30" is not supported by the best review. That review (Mitchell 2012) finds about 4–5% per decade in men and about 4% in women, starting anywhere between the late 20s and 60, and speeding up after 60–70.
- **Strength falls 2–5 times faster than muscle size,** and loss of strength predicts disability more strongly than loss of muscle. So strength and power work should not drop with age.
- **WHO 2020:** the adult targets are the same at every age (150–300 min of moderate activity and 2+ strength days a week). From 65, people should add balance-and-strength work on at least 3 days a week.
- **HIIT in older people** is feasible: twice a week for 5 years at ages 70–77 in the Generation 100 study. The safety data, however, come from supervised settings, and the European obesity body advises supervision for HIIT with obesity. So the app adds a longer base period first, low-impact machines only, and normal screening.
- **Women around menopause:** fat increases and muscle starts to fall even when weight stays stable (SWAN study). Keeping 2–3 strength days matters, and a stable weight with a shrinking waist is real progress.

### Steps

- **Health benefit:** it levels off at about 8,000–10,000 steps a day under 60 and 6,000–8,000 from 60 (Paluch 2022, 15 cohorts). A 2025 Lancet review finds most benefits by about 5,000–7,000 a day (read through summaries only).
- **Weight:** steps alone barely move it, about 0.05 kg a week (Richardson 2008). Their value is health, and the extra easy minutes they add.
- **Step goals:** having one helps people walk more (Bravata 2007), and goals that adapt to the person may work better than a fixed 10,000 (Adams 2013, one small trial). No guideline says how fast to raise the goal, so the weekly increase is my judgement.

### Measuring progress without food tracking

- **Weighing:** daily and weekly weighing work equally well (Madigan 2015). The app shows a 7-day average and judges only 4-week trends.
- **Waist** becomes the main body measure, because strength training can add muscle while fat falls, so the scale can stall.
- **Indian reference lines:** a waist of 90 cm or more for men and 80 cm or more for women (India Obesity Commission 2025, IDF), shown as information only.

## How the mix changes with age (your question)

These are the targets for the fat-loss goal. The planner fits them into the days and minutes you have, and walking outside the gym supplies most of the easy cardio.

| | 30–39 | 40–49 | 50–59 | 60–64 | 65+ |
|---|---|---|---|---|---|
| **Strength days per week** | 3 (min 2) | 3 (min 2) | 3 (min 2), one with light-and-fast power work | 3 (min 2), power + balance | 3 combined balance-and-strength days |
| **Easy cardio (Z1), min/week, walks included** | 150–200 | 150–200 | 150–200 | 150–200 | 150–200 |
| **Tempo cardio (Z2), min/week** | 20–40 | 20–40 | 15–30 | 0–20 | 0–15 |
| **Interval (HIIT) sessions per week** | 2 (max 3*) | 1 (max 3*) | 1 (max 2) | 0, offered after 6 weeks (max 2) | 0, offered after 8 weeks if you were already active (max 2) |
| **Interval style** | Long/medium intervals; jumping only if BMI under 30 and no leg pain | Low-impact preferred | Low-impact only, no sprints | Low-impact only | Low-impact only |
| **Balance work** | — | — | 10 min/week | 20 min/week | 30 min/week (3 days) |
| **Weekly total (moderate-equivalent minutes)** | 200–300 | 200–300 | 200–300 | 180–250 | 150–250 |
| **Daily step target** | 9,000 | 9,000 | 8,000 | 7,500 | 7,000 |

- **\* A 3rd interval session** is allowed only under the existing HIIT-001 conditions: intermediate or advanced, a full-readiness day, no fatigue signal, in a conditioning block.
- **Low-impact machines:** rower, SkiErg, elliptical and sled. If you approve decision 2, bikes and incline walking are added.
- **Confidence:**
  - High Evidence: the floors (2+ strength days, 150 minutes, balance at 65+) and the case for steady cardio and strength.
  - Moderate Evidence: the step targets (observational studies).
  - Expert Practice: the exact age cut-points, the tempo and HIIT taper, and the base periods. No trial has tested age-banded mixes.

## The new rules, in plain words

| Rule | What it does | Confidence |
|---|---|---|
| **FL-001 Default goal** | For people 30+, "Lose fat, keep muscle" is the default and other goals stay available. The app explains what to expect without diet changes (about 2–3 kg and 2–5 cm of waist over a few months) and never promises faster. HIIT is never sold as a fat burner. No food features. Anyone can turn weight features off (no weight or waist tracking, no expectations, no rate check-in). With pregnancy, or cancer in active treatment, the fat-loss goal isn't offered; after giving birth it can be chosen from 12 weeks. | Moderate (waist High) |
| **FL-002 Weekly activity** | Moderate-equivalent minutes per week by age: 200–300 below 60, 180–250 at 60–64, 150–250 at 65+, never below 150. Grows at most 15% a week (12% at 65+). | Moderate |
| **FL-003 Mix by age** | The table above | Expert Practice (floors High) |
| **FL-004 Fat-loss year** | Calibrate → Foundation → Build → Conditioning → Strength → Build II → Conditioning II → Athletic (low-impact) → Consolidation → Review. Every block keeps 2–3 strength days. | Expert Practice |
| **FL-005 Progress** | Waist every 2–4 weeks (3 readings averaged) and weight as a 7-day average, judged over 4 weeks. Strength trend shown. If weight drops faster than 1 kg a week for 3 weeks, a neutral check-in suggests seeing a doctor if you're not dieting or feel unwell. | Moderate |
| **STEP-001 Step target** | Starts from your own first week. Rises 10–15% a week: at least 250 steps, at most 1,000 below 50 (750 at 50–59, 500 at 60–64, 400 at 65+), and only if you met it on 5 of 7 days. Holds at 3–4 days and drops by one increment after 2 weak weeks. Never rises in a lighter week or with leg pain. | Moderate (targets) / Expert Practice (pace) |
| **STEP-002 Walks count once** | Only brisk walks of 10+ minutes at 100+ steps a minute count as easy cardio; other steps count toward the step target only | Moderate (pace) / Expert Practice (10-minute rule) |
| **CAL-002 Your own numbers** | You can enter a recent set or your best lift. The app estimates your max, starts at 90% of what that supports, and reduces it if you haven't done the lift for 2+ weeks. Your first session confirms it. | Expert Practice |
| **SAF-010 Health conditions** | The picker and the table below | Moderate (caps Expert Practice) |

## Health-condition table (draft v1.0)

You tick any that apply. With several, the strictest setting of each control wins, using a fixed order for every control, and phases and unlocks are worked out first. Each condition asks "Has a doctor or nurse told you this is under control?", and "not sure" counts as no. A doctor's OK is something you confirm in the app, and it unlocks only what you were cleared for:
- **"Before vigorous":** until you confirm an OK for vigorous exercise, the plan stays easy–moderate with no intervals.
- **"Always":** until you confirm an OK, the existing conservative mode applies.

The app never reads blood pressure or sugar numbers, never names medicines or doses, and never claims to treat a condition.

| Condition | Doctor's OK | Hardest effort | Intervals | Main limits |
|---|---|---|---|---|
| High blood pressure, controlled | Not needed | Intervals | After 4 weeks | No breath-holding max lifts; extra 5-min cool-down |
| High blood pressure, not controlled / not sure | Before vigorous | Easy–moderate until cleared | Only if cleared for intervals | No breath-holding or long hard holds; 3 reps in reserve |
| Type 2 diabetes | Before vigorous | Easy–moderate until cleared, then intervals | If cleared for intervals, after 6 weeks | Never more than 2 days in a row without activity; strength never on back-to-back days; low-sugar tips only if you take a diabetes medicine that can cause lows |
| Diabetes (type 1 or 2) + eye disease | Always | Easy–moderate | No | No jumping, head-down positions, breath-holding or heavy overhead work |
| Diabetes + numb feet | Not needed | Intervals on the rower (bike if decision 2) | After 4 weeks | No jumping or uneven running; daily foot check |
| Diabetes + affects heart rate/BP | Always | Easy–moderate | No | Effort by feel; +10 min warm-up and cool-down |
| Type 1 diabetes | Before vigorous | Easy–moderate until cleared, then intervals | If cleared for intervals, after 4 weeks | Strength before cardio; check glucose as your care plan says |
| Heart condition | Always | Easy–moderate until cleared | Only if cleared for intervals | No breath-holding or long hard holds; +10 min warm-up and cool-down; extra limits for pacemaker, recent chest surgery, aortic disease |
| Asthma | Not needed if controlled | Intervals | If controlled | +10 min interval-style warm-up; cold-air and pollen tips |
| Knee or hip arthritis | Not needed | Intervals on low-impact machines | After base | Joint load starts at 2 of 4. Pain up to 5/10 during exercise is fine if it's back to usual next morning; otherwise ease that joint. |
| Low back pain | Not needed | Intervals | As tolerated | "Flare mode" lightens spine loading; emergency signs listed |
| Osteoporosis / osteopenia | Suggested | Intervals | Yes | A short bone-loading block (50+ low landings, ≤5 min) most days, which doesn't count as the week's 1 impact session; no loaded bending or twisting; balance and back work twice a week |
| + spine fracture | Always (physio) | Tempo | No | Impact limited to brisk walking |
| Pregnancy | Always | Easy–moderate (tempo if you trained hard before and your provider agrees) | No | Training plan blocked if your provider has told you a listed complication applies; no lying on the back from about 20 weeks (or any time it makes you dizzy); no fall-risk or Olympic lifts; weight-loss features off |
| After giving birth (≤12 months) | Suggested | Easy until 12 weeks, then intervals after impact checks | After impact checks | No impact before 12 weeks and a 7-step impact check; daily pelvic-floor work |
| Obesity (BMI 30+, or 27.5+ South Asian) | Not needed | Intervals on low-impact machines | After 4 weeks | Low-volume impact; heat tips |
| Severe obesity (BMI 35+, or 32.5+ South Asian) | Suggested | Easy–moderate for 8 weeks, then intervals | After 8 weeks, rower (bike if decision 2) | No impact at start; easier floor transitions |
| Stroke (optional) | Always | Easy–moderate; tempo once cleared | No | No fall-risk moves |
| COPD (optional) | Always | Easy–moderate | No | Effort by breathing |
| Cancer, during or after (optional) | Suggested (always in treatment) | Tempo | No | No impact if cancer has spread to bone (pending source check); weight-loss features off during treatment |

**Notes:**
- "Easy–moderate" means the app's easy zone, where you can still talk in full sentences. The app's tempo zone counts as vigorous, so "moderate only" guidance maps to the easy zone.
- **Confidence:** the benefit of exercise is High Evidence for type 2 diabetes, arthritis, blood pressure and pregnancy. Almost every exact cap is an Expert Practice translation of guideline language into the app's controls.
- **Stroke, COPD and cancer** rest on fewer checked sources. They start conservative and always suggest a doctor's OK.
- **Anyone can turn weight features off** (FL-001). This protects people for whom a focus on weight isn't helpful, without asking about it.
- **Full table:** `rules/proposed/health_conditions_v1.0_draft.json`, with each entry's stop signs, prompts and sources.

**For a public release:**
- Condition-tailored exercise software can become a regulated medical device in India (CDSCO software guidance, July 2026), the EU (MDR Rule 11), the UK (MHRA) and the US (FDA general-wellness policy, January 2026). What decides it is the intended purpose and the claims made.
- The table stays on the wellness side: safety limits, generic tips, and "check with your doctor". It reads no clinical numbers and makes no treatment claims.
- **Before any launch, a clinician and a regulatory adviser must review the table and the app-store wording.** Four borderline ideas are deliberately not built:
  - a blood-pressure reading gate
  - a "severe low in the last 24 hours" check
  - selling the app as "adapts to your medical conditions"

  The arthritis pain rule (ease off a joint when pain stays above usual, unlock more load when it doesn't) is built as a training adjustment, never marketed as therapy, and flagged for that review.

## Changes to existing rules

| Rule | Change | Why |
|---|---|---|
| CON-006 1.0.0 → 1.1.0 | In strength blocks, prescribed gym cardio stays at 150 min/week or less, but for the fat-loss goal brisk walks are outside that cap | Fat-loss targets of 200–300 min/week would otherwise clash with it. Walking interferes very little with strength gains. |
| CON-004 1.0.0 → 1.1.0 | The osteoporosis entry's short bone-loading block (≤5 min of low landings) is not one of the week's impact sessions | The UK osteoporosis consensus advises moderate impact most days; the 1-a-week cap was written for jumping conditioning |
| PH-001 1.0.0 → 1.1.0 | Easy minutes include the brisk walks of STEP-002; nothing else from steps is added | Steps are never counted twice |
| DATA-001 1.0.0 → 1.1.0 | Optional data adds daily steps (this phone's own sensor), health conditions and clearance, and your starting weights and records. Waist is already covered as a body measurement. | CR-001, CR-002, CR-005. All of it stays on the phone, goes into the encrypted backup and can be deleted. Steps from other apps stay excluded. |
| MOD-001 (your decision 2) | Excluded machines become a per-person choice | See "What I need from you" |

## Impact analysis (change control F7)

- **Rules:** 140 → 149 rules, with 4 changed (5 with decision 2). The training rules for other goals are unchanged; the CON-004, PH-001 and DATA-001 changes (and MOD-001, if approved) apply to everyone. Every current test (346) must still pass, and each new rule gets at least 2 tests, named by rule ID as before.
- **Workouts:**
  - New users get the fat-loss programme by default. That means 3 strength days when they train 3+ days, more easy cardio (mostly walking), and fewer intervals as age rises: low-impact only from 50, and offered rather than planned from 60.
  - Users with conditions get stricter limits.
  - Users who pick other goals see no change.
- **Engine work (new Part 3):**
  - fat-loss year and age-band mix in the planner
  - step target and brisk-walk counting
  - progress trends (weight, waist, rate check-in)
  - condition profiles and the "strictest wins" merge feeding the safety validator
  - new exercise tags (breath-holding max, heavy holds, loaded twisting, head-down, lying on back or front, fall risk, contact, Olympic lifts)
  - starting from your own numbers
  - low-impact power exercises for dumbbell-only and bodyweight gyms, and "any of" equipment (D-063)
  - the simulation gets one fat-loss user per age band, and the property tests get random condition combinations
- **Data (Part 4):** new tables for conditions and clearance, steps, waist and weight entries, and starting numbers.
- **Permissions:** step counting adds the "physical activity" permission, asked only when you turn steps on. There is still no internet permission. The CI permission check is updated.
- **Screens (later parts):** condition picker, "doctor's OK" scope, step card, waist and weight entry with trends, and the "enter your numbers" screen.
- **Library 1.1.0:** new tags on existing exercises, a few new low-impact power and balance exercises, and pose data for the illustrations (D-061).

## Evidence versus judgement

- **Straight from evidence:**
  - the 150–300 minute range and its dose-response
  - strength 2+ days, and balance plus strength 3 days from 65
  - steady cardio as the fat-loss driver; HIIT not superior
  - strength protecting muscle
  - step plateaus by age
  - weighing frequency
  - most condition cautions: breath-holding with high blood pressure, eye disease with diabetes, pregnancy contraindications, osteoporosis loaded bending, arthritis exercise as core care
- **Judgement (Expert Practice), labelled in the registry:**
  - age cut-points
  - tempo and HIIT tapers and base periods
  - step increase pace
  - the 1 kg/week check-in
  - nearly every numeric cap in the condition table
  - the CAL-002 reductions

## Limits and open items

1. **Sources not read in full.** Some sources could only be read through summaries, because PubMed, the Lancet, JAMA, ACOG and FDA blocked or timed out. They are labelled "located" and listed in the delta file. The rules that rest on them are marked "approved — verify source" and checked before Phase 4 testing:
   - Donnelly 2009's dose bands
   - WHO 2020
   - Ding 2025
   - ACOG 804
   - GINA 2026
   - the FDA and EU texts
   - Campbell 2019's cancer cautions

   The ADA Standards 2026 were not opened at all (unverified).
2. **Phone step counters** are less accurate than the research devices behind the step studies, and a phone counts only when it is carried. Whether the phone can measure walking pace offline (for STEP-002) is a Part 3 engineering check. If it can't, you log the walk yourself.
3. **Waist site.** WHO measures midway between the rib and hip bone, while the Indian consensus measures just above the hip bone, and readings differ. The app uses the WHO midpoint and always says so. The Indian lines are shown as information only.
4. **HIIT without supervision** in older or heavier users is protected by gates (screening, base period, low-impact only). Those gates reduce risk, but they are not proof of safety.
5. **Protein** strongly affects muscle retention, but food is out of scope. The app will not imply it can make up for that.

## Files

- `rules/proposed/registry_v1.1.0_proposed_delta.json`: the 9 new rules, 4 changes, the MOD-001 question and 57 new sources, in registry format (status "proposed")
- `rules/proposed/health_conditions_v1.0_draft.json`: the 21-entry condition table
- `docs/research_update_1_1/evidence_A_fat_loss_age_mix.md` and `evidence_B_health_conditions.md`: the full evidence reviews, with every source labelled
- `tools/proposed_build_registry_delta.py` and `tools/proposed_build_conditions.py`: the scripts that write the two JSON files

```
================================================================================
RESEARCH UPDATE 1.1 SIGN-OFF
Please review the proposed rules (Registry 1.1.0), the health-condition table
v1.0 draft and decision 2 (cardio machines). Reply 'APPROVED' to apply them in
Phase 3 Part 3, or give feedback / adjustments.
================================================================================
```
