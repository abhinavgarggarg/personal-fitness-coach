"""Builds rules/rule_registry_v0.1.json — the single source of truth for every
fitness rule and numeric parameter in the Personal Fitness Coach engine.

Phase 1 deliverable 38. v0.1.0 (proposed) was approved by the Product Owner on
2026-10-07; every rule is now version 1.0.0, status "approved". Later changes follow
B6/F7 change control (semantic versioning per rule).
Run:  python3 -I tools/build_registry.py
"""
import json
from pathlib import Path

TODAY = "2026-10-07"
VERSION = "1.0.0"
APPROVED_ON = "2026-10-07"  # Product Owner Phase 1 sign-off

# ---------------------------------------------------------------- sources
# verification: "checked" = abstract/publisher summary read on 2026-10-07;
# "located" = title, year and venue confirmed on 2026-10-07; author list, volume/pages and findings from prior knowledge;
# "unverified" = not located this session.
SOURCES = {
 "ACSM2026": ("Currier BS, et al. (writing group chaired by Phillips SM). American College of Sports Medicine Position Stand. Resistance training prescription for muscle function, hypertrophy, and physical performance in healthy adults: an overview of reviews. Med Sci Sports Exerc. 2026;58(4):851-872. doi:10.1249/MSS.0000000000003897", "checked", "https://doi.org/10.1249/MSS.0000000000003897"),
 "Currier2023": ("Currier BS, McLeod JC, Banfield L, et al. Resistance training prescription for muscle strength and hypertrophy in healthy adults: a systematic review and Bayesian network meta-analysis. Br J Sports Med. 2023;57(18):1211-1220. doi:10.1136/bjsports-2023-106807", "checked", "https://research-information.bris.ac.uk/en/publications/resistance-training-prescription-for-muscle-strength-and-hypertro/"),
 "Pelland2024": ("Pelland JC, Remmert JF, Robinson ZP, Hinson SR, Zourdos MC. The resistance training dose-response: meta-regressions exploring the effects of weekly volume and frequency on muscle hypertrophy and strength gain. SportRxiv preprint (version 2), 2024. NOT PEER REVIEWED.", "checked", "https://sportrxiv.org/index.php/server/preprint/view/460"),
 "Schoenfeld2017vol": ("Schoenfeld BJ, Ogborn D, Krieger JW. Dose-response relationship between weekly resistance training volume and increases in muscle mass: a systematic review and meta-analysis. J Sports Sci. 2017;35(11):1073-1082.", "located", "https://paulogentil.com/pdf/Dose-response%20relationship%20between%20weekly%20resistance%20training%20volume%20and%20increases%20in%20muscle%20mass%20-%20A%20systematic%20review%20and%20metaanalysis.pdf"),
 "Schoenfeld2016freq": ("Schoenfeld BJ, Ogborn D, Krieger JW. Effects of resistance training frequency on measures of muscle hypertrophy: a systematic review and meta-analysis. Sports Med. 2016;46(11):1689-1697.", "located", "https://pubmed.ncbi.nlm.nih.gov/27102172/"),
 "Schoenfeld2019freq": ("Schoenfeld BJ, Grgic J, Krieger J. How many times per week should a muscle be trained to maximize muscle hypertrophy? A systematic review and meta-analysis. J Sports Sci. 2019;37(11):1286-1295.", "located", "https://mennohenselmans.com/training-frequency-2018-meta-analysis-review/"),
 "Grgic2018freq": ("Grgic J, Schoenfeld BJ, Davies TB, Lazinica B, Krieger JW, Pedisic Z. Effect of resistance training frequency on gains in muscular strength: a systematic review and meta-analysis. Sports Med. 2018;48(5):1207-1220.", "located", "https://vuir.vu.edu.au/37695/"),
 "Schoenfeld2017load": ("Schoenfeld BJ, Grgic J, Ogborn D, Krieger JW. Strength and hypertrophy adaptations between low- vs. high-load resistance training: a systematic review and meta-analysis. J Strength Cond Res. 2017;31(12):3508-3523.", "located", "https://pubmed.ncbi.nlm.nih.gov/28834797/"),
 "Schoenfeld2021reps": ("Schoenfeld BJ, Grgic J, Van Every DW, Plotkin DL. Loading recommendations for muscle strength, hypertrophy, and local endurance: a re-examination of the repetition continuum. Sports. 2021;9(2):32.", "located", "https://www.ncbi.nlm.nih.gov/pmc/articles/PMC7927075/"),
 "Refalo2023": ("Refalo MC, Helms ER, Trexler ET, Hamilton DL, Fyfe JJ. Influence of resistance training proximity-to-failure on skeletal muscle hypertrophy: a systematic review with meta-analysis. Sports Med. 2023;53(3):649-665.", "located", "https://dro.deakin.edu.au/articles/journal_contribution/Influence_of_Resistance_Training_Proximity-to-Failure_on_Skeletal_Muscle_Hypertrophy_A_Systematic_Review_with_Meta-analysis/22030796"),
 "Robinson2024": ("Robinson ZP, Pelland JC, Remmert JF, Refalo MC, Jukic I, Steele J, Zourdos MC. Exploring the dose-response relationship between estimated resistance training proximity to failure, strength gain, and muscle hypertrophy: a series of meta-regressions. Sports Med. 2024;54(9):2209-2231. doi:10.1007/s40279-024-02069-2", "checked", "https://rke.abertay.ac.uk/en/publications/exploring-the-dose-response-relationship-between-estimated-resist/"),
 "Grgic2022fail": ("Grgic J, Schoenfeld BJ, Orazem J, Sabol F. Effects of resistance training performed to repetition failure or non-failure on muscular strength and hypertrophy: a systematic review and meta-analysis. J Sport Health Sci. 2022;11(2):202-211.", "located", "https://doaj.org/article/dc36ba7ccd994798964f66b93bd8f662"),
 "Zourdos2016": ("Zourdos MC, Klemp A, Dolan C, et al. Novel resistance training-specific rating of perceived exertion scale measuring repetitions in reserve. J Strength Cond Res. 2016;30(1):267-275.", "located", "https://openrepository.aut.ac.nz/items/efef3b25-6701-4fb5-bb82-55fcd2a26027/full"),
 "Helms2016": ("Helms ER, Cronin J, Storey A, Zourdos MC. Application of the repetitions in reserve-based rating of perceived exertion scale for resistance training. Strength Cond J. 2016;38(4):42-49.", "located", "https://openrepository.aut.ac.nz/items/92cefeac-7c32-44f1-bfbc-f48f4a5dd8cf/full"),
 "Halperin2022": ("Halperin I, Malleron T, Har-Nir I, et al. Accuracy in predicting repetitions to task failure in resistance exercise: a scoping review and exploratory meta-analysis. Sports Med. 2022;52(2):377-390.", "located", "https://pure.solent.ac.uk/en/publications/accuracy-in-predicting-repetitions-to-task-failure-in-resistance-/"),
 "Singer2024": ("Singer A, Wolf M, Generoso L, et al. Give it a rest: a systematic review with Bayesian meta-analysis on the effect of inter-set rest interval duration on muscle hypertrophy. Front Sports Act Living. 2024;6:1429789. doi:10.3389/fspor.2024.1429789", "checked", "https://www.frontiersin.org/articles/10.3389/fspor.2024.1429789/full"),
 "Grgic2018rest": ("Grgic J, Schoenfeld BJ, Skrepnik M, Davies TB, Mikulic P. Effects of rest interval duration in resistance training on measures of muscular strength: a systematic review. Sports Med. 2018;48(1):137-151.", "located", "https://vuir.vu.edu.au/38537/"),
 "Nunes2021": ("Nunes JP, Grgic J, Cunha PM, et al. What influence does resistance exercise order have on muscular strength gains and muscle hypertrophy? A systematic review and meta-analysis. Eur J Sport Sci. 2021;21(2):149-157.", "located", "https://vuir.vu.edu.au/44202/"),
 "Schumann2022": ("Schumann M, Feuerbacher JF, Sünkeler M, et al. Compatibility of concurrent aerobic and strength training for skeletal muscle size and function: an updated systematic review and meta-analysis. Sports Med. 2022;52(3):601-612.", "located", "https://www.ncbi.nlm.nih.gov/pmc/articles/PMC8891239/"),
 "Wilson2012": ("Wilson JM, Marin PJ, Rhea MR, Wilson SMC, Loenneke JP, Anderson JC. Concurrent training: a meta-analysis examining interference of aerobic and resistance exercises. J Strength Cond Res. 2012;26(8):2293-2307.", "located", "https://search.pedro.org.au/search-results/record-detail/34457"),
 "Murlasits2018": ("Murlasits Z, Kneffel Z, Thalib L. The physiological effects of concurrent strength and endurance training sequence: a systematic review and meta-analysis. J Sports Sci. 2018;36(11):1212-1219.", "located", "https://pubmed.ncbi.nlm.nih.gov/28783467/"),
 "Robineau2016": ("Robineau J, Babault N, Piscione J, Lacome M, Bigard AX. Specific training effects of concurrent aerobic and strength exercises depend on recovery duration. J Strength Cond Res. 2016;30(3):672-683.", "located", "https://pubmed.ncbi.nlm.nih.gov/25546450/"),
 "Sabag2018": ("Sabag A, Najafi A, Michael S, Esgin T, Halaki M, Hackett D. The compatibility of concurrent high intensity interval training and resistance training for muscular strength and hypertrophy: a systematic review and meta-analysis. J Sports Sci. 2018;36(21):2472-2483.", "located", "https://researchers.westernsydney.edu.au/en/publications/the-compatibility-of-concurrent-high-intensity-interval-training-/"),
 "Milanovic2015": ("Milanović Z, Sporiš G, Weston M. Effectiveness of high-intensity interval training (HIT) and continuous endurance training for VO2max improvements: a systematic review and meta-analysis of controlled trials. Sports Med. 2015;45(10):1469-1481.", "located", "https://research.tees.ac.uk/en/publications/effectiveness-of-high-intensity-interval-training-hit-and-continu-3/"),
 "Wen2019": ("Wen D, Utesch T, Wu J, et al. Effects of different protocols of high intensity interval training for VO2max improvements in adults: a meta-analysis of randomised controlled trials. J Sci Med Sport. 2019;22(8):941-947.", "located", "https://research.uni-luebeck.de/en/publications/effects-of-different-protocols-of-high-intensity-interval-trainin/"),
 "Buchheit2013": ("Buchheit M, Laursen PB. High-intensity interval training, solutions to the programming puzzle. Part I: cardiopulmonary emphasis. Sports Med. 2013;43(5):313-338.", "located", "https://www.iat.uni-leipzig.de/datenbanken/iks/dsv-xc/Record/4056671"),
 "Weston2014": ("Weston KS, Wisløff U, Coombes JS. High-intensity interval training in patients with lifestyle-induced cardiometabolic disease: a systematic review and meta-analysis. Br J Sports Med. 2014;48(16):1227-1234.", "located", "https://bjsm.bmj.com/content/48/16/1227"),
 "Foster2018talk": ("Foster C, Porcari JP, Ault S, et al. Exercise prescription when there is no exercise test: the talk test. Kinesiology. 2018;50(Suppl 1):33-48.", "checked", "https://hrcak.srce.hr/en/192696"),
 "Borg1982": ("Borg GA. Psychophysical bases of perceived exertion. Med Sci Sports Exerc. 1982;14(5):377-381.", "located", "https://explore.openalex.org/works/w2004008756"),
 "Foster2001": ("Foster C, Florhaug JA, Franklin J, et al. A new approach to monitoring exercise training. J Strength Cond Res. 2001;15(1):109-115.", "located", "https://sponet.de/sponet/Record/4006210"),
 "Foster1998": ("Foster C. Monitoring training in athletes with reference to overtraining syndrome. Med Sci Sports Exerc. 1998;30(7):1164-1168.", "located", "https://lida.sport-iat.de/ta/Record/4001707?lng=en"),
 "Gabbett2016": ("Gabbett TJ. The training-injury prevention paradox: should athletes be training smarter and harder? Br J Sports Med. 2016;50(5):273-280.", "located", "https://lida.sport-iat.de/ta/Record/4040728"),
 "Williams2017": ("Williams S, West S, Cross MJ, Stokes KA. Better way to determine the acute:chronic workload ratio? Br J Sports Med. 2017;51(3):209-210.", "located", "https://pubmed.ncbi.nlm.nih.gov/27650255/"),
 "Lolli2019": ("Lolli L, Batterham AM, Hawkins R, et al. Mathematical coupling causes spurious correlation within the conventional acute-to-chronic workload ratio calculations. Br J Sports Med. 2019;53(15):921-922.", "located", "https://bjsm.bmj.com/content/53/15/921"),
 "Impellizzeri2021": ("Impellizzeri FM, Woodcock S, Coutts AJ, Fanchini M, McCall A, Vigotsky AD. What role do chronic workloads play in the acute to chronic workload ratio? Time to dismiss ACWR and its underlying theory. Sports Med. 2021;51(3):581-592.", "located", "https://iris.univr.it/handle/11562/1139126"),
 "Saw2016": ("Saw AE, Main LC, Gastin PB. Monitoring the athlete training response: subjective self-reported measures trump commonly used objective measures: a systematic review. Br J Sports Med. 2016;50(5):281-289.", "checked", "https://bjsm.bmj.com/content/50/5/281"),
 "Craven2022": ("Craven J, McCartney D, Desbrow B, et al. Effects of acute sleep loss on physical performance: a systematic and meta-analytical review. Sports Med. 2022;52(11):2669-2690.", "checked", "https://nutrition-evidence.com/article/415006/effects-of-acute-sleep-loss-on-physical-performance-a-systematic-and-meta-analytical-review"),
 "Bell2023": ("Bell L, Strafford BW, Coleman M, Androulakis Korakakis P, Nolan D. Integrating deloading into strength and physique sports training programmes: an international Delphi consensus approach. Sports Med Open. 2023;9:87. doi:10.1186/s40798-023-00633-0", "checked", "https://link.springer.com/article/10.1186/s40798-023-00633-0"),
 "Coleman2024": ("Coleman M, et al. Gaining more from doing less? The effects of a one-week deload period during supervised resistance training on muscular adaptations. PeerJ. 2024;12:e16777. doi:10.7717/peerj.16777", "checked", "https://peerj.com/articles/16777"),
 "Meeusen2013": ("Meeusen R, Duclos M, Foster C, et al. Prevention, diagnosis, and treatment of the overtraining syndrome: joint consensus statement of the European College of Sport Science and the American College of Sports Medicine. Med Sci Sports Exerc. 2013;45(1):186-205.", "located", "https://hal.inrae.fr/hal-02651426"),
 "Moesgaard2022": ("Moesgaard L, Beck MM, Christiansen L, Aagaard P, Lundbye-Jensen J. Effects of periodization on strength and muscle hypertrophy in volume-equated resistance training programs: a systematic review and meta-analysis. Sports Med. 2022;52(7):1647-1666.", "located", "https://pubmed.ncbi.nlm.nih.gov/35044672/"),
 "Bosquet2013": ("Bosquet L, Berryman N, Dupuy O, et al. Effect of training cessation on muscular performance: a meta-analysis. Scand J Med Sci Sports. 2013;23(3):e140-e149. doi:10.1111/sms.12047", "located", "https://ekoizpen-zientifikoa.ehu.eus/documentos/5f1cda5129995265e44d37a4?lang=en"),
 "Bull2020": ("Bull FC, Al-Ansari SS, Biddle S, et al. World Health Organization 2020 guidelines on physical activity and sedentary behaviour. Br J Sports Med. 2020;54(24):1451-1462.", "located", "https://digibug.ugr.es/handle/10481/66573"),
 "Garber2011": ("Garber CE, Blissmer B, Deschenes MR, et al. ACSM position stand: quantity and quality of exercise for developing and maintaining cardiorespiratory, musculoskeletal, and neuromotor fitness in apparently healthy adults. Med Sci Sports Exerc. 2011;43(7):1334-1359.", "located", "https://digitalcommons.uri.edu/kinesiology_facpubs/137"),
 "Riebe2015": ("Riebe D, Franklin BA, Thompson PD, et al. Updating ACSM's recommendations for exercise preparticipation health screening. Med Sci Sports Exerc. 2015;47(11):2473-2479.", "located", "https://digitalcommons.uri.edu/kinesiology_facpubs/180"),
 "Warburton2011": ("Warburton DER, Jamnik VK, Bredin SSD, Gledhill N. The Physical Activity Readiness Questionnaire for Everyone (PAR-Q+) and electronic Physical Activity Readiness Medical Examination (ePARmed-X+). Health Fit J Can. 2011;4(2):3-23.", "located", "https://hfjc.library.ubc.ca/index.php/HFJC/article/view/103"),
 "Fragala2019": ("Fragala MS, Cadore EL, Dorgo S, et al. Resistance training for older adults: position statement from the National Strength and Conditioning Association. J Strength Cond Res. 2019;33(8):2019-2052.", "located", "https://scholarworks.utep.edu/kines_papers/60"),
 "Behm2016": ("Behm DG, Blazevich AJ, Kay AD, McHugh M. Acute effects of muscle stretching on physical performance, range of motion, and injury incidence in healthy active individuals: a systematic review. Appl Physiol Nutr Metab. 2016;41(1):1-11.", "located", "https://pure.northampton.ac.uk/en/publications/acute-effects-of-muscle-stretching-on-physical-performance-range-/"),
 "Afonso2021": ("Afonso J, Ramirez-Campillo R, Moscão J, et al. Strength training versus stretching for improving range of motion: a systematic review and meta-analysis. Healthcare (Basel). 2021;9(4):427.", "located", "https://estudogeral.uc.pt/handle/10316/104612"),
 "Fradkin2010": ("Fradkin AJ, Zazryn TR, Smoliga JM. Effects of warming-up on physical performance: a systematic review with meta-analysis. J Strength Cond Res. 2010;24(1):140-148.", "located", "https://lida.sport-iat.de/ta/Record/4017835"),
 "Jeffreys2019": ("Jeffreys I. The Warm-Up: Maximise Performance and Improve Long-Term Athletic Development. Champaign, IL: Human Kinetics; 2019. (RAMP protocol)", "located", "https://pure.southwales.ac.uk/en/publications/the-warm-up-maximise-performance-and-improve-long-term-athletic-d/"),
 "LeSuer1997": ("LeSuer DA, McCormick JH, Mayhew JL, Wasserstein RL, Arnold MD. The accuracy of prediction equations for estimating 1-RM performance in the bench press, squat, and deadlift. J Strength Cond Res. 1997;11(4):211-213.", "located", "https://researchconnect.suny.edu/en/publications/accuracy-of-prediction-equations-for-determining-one-repetition-m/"),
 "Brzycki1993": ("Brzycki M. Strength testing: predicting a one-rep max from reps-to-fatigue. J Phys Educ Recreat Dance. 1993;64(1):88-90.", "located", "https://brzycki.scholar.princeton.edu/publications/strength-testing-%E2%80%93-predicting-one-rep-max-reps-fatigue"),
 "Epley1985": ("Epley B. Poundage Chart. Boyd Epley Workout. Lincoln, NE: Body Enterprises; 1985. (origin of the Epley formula; not peer reviewed)", "unverified", ""),
 "AndroulakisKorakakis2020": ("Androulakis-Korakakis P, Fisher JP, Steele J. The minimum effective training dose required to increase 1RM strength in resistance-trained men: a systematic review and meta-analysis. Sports Med. 2020;50(4):751-765.", "located", "https://research.bond.edu.au/en/publications/the-minimum-effective-training-dose-required-for-1rm-strength-in-/"),
 "Iversen2021": ("Iversen VM, Norum M, Schoenfeld BJ, Fimland MS. No time to lift? Designing time-efficient training programs for strength and hypertrophy: a narrative review. Sports Med. 2021;51(10):2079-2095.", "located", "https://www.ncbi.nlm.nih.gov/pmc/articles/PMC8449772/"),
 "Zhang2021": ("Zhang X, Li H, Bi S, Luo Y, Cao Y, Zhang G. Auto-regulation method vs. fixed-loading method in maximum strength training for athletes: a systematic review and meta-analysis. Front Physiol. 2021;12:651112.", "checked", "https://www.frontiersin.org/articles/10.3389/fphys.2021.651112/full"),
 "Roberts2020": ("Roberts BM, Nuckols G, Krieger JW. Sex differences in resistance training: a systematic review and meta-analysis. J Strength Cond Res. 2020;34(5):1448-1460.", "located", "https://pubmed.ncbi.nlm.nih.gov/32218059/"),
 "Silbernagel2007": ("Silbernagel KG, Thomeé R, Eriksson BI, Karlsson J. Continued sports activity, using a pain-monitoring model, during rehabilitation in patients with Achilles tendinopathy: a randomized controlled study. Am J Sports Med. 2007;35(6):897-906.", "located", "https://search.pedro.org.au/search-results/record-detail/18389"),
 "Lally2010": ("Lally P, van Jaarsveld CHM, Potts HWW, Wardle J. How are habits formed: modelling habit formation in the real world. Eur J Soc Psychol. 2010;40(6):998-1009.", "located", "https://discovery.ucl.ac.uk/id/eprint/10144814/"),
 "Mazeas2022": ("Mazeas A, Duclos M, Pereira B, Chalabaev A. Evaluating the effectiveness of gamification on physical activity: systematic review and meta-analysis of randomized controlled trials. J Med Internet Res. 2022;24(1):e26779.", "located", "https://www.jmir.org/2022/1/E26779/"),
 "Kibler2006": ("Kibler WB, Press J, Sciascia A. The role of core stability in athletic function. Sports Med. 2006;36(3):189-198.", "located", "https://vivo.weill.cornell.edu/display/pubid16526831"),
 "UpperLimits2026": ("Exploring the upper limits of resistance training volume for muscle hypertrophy and strength in trained athletes (authors not recorded). J Sci Sport Exerc. 2026. doi:10.1007/s42978-026-00387-7 (located, not reviewed; not used by any rule)", "located", "https://journal.hep.com.cn/josisae/EN/10.1007/s42978-026-00387-7"),
 "Israetel2021": ("Israetel M, Hoffmann J, Smith CW. Scientific Principles of Hypertrophy Training. Renaissance Periodization; 2021. (source of MV/MEV/MAV/MRV vocabulary; book, expert practice)", "located", "https://rpstrength.com/the-scientific-principles-of-hypertrophy-training"),
}

EP, LIM, MOD, HIGH = "Expert Practice", "Limited Evidence", "Moderate Evidence", "High Evidence"
PR = "Product Rule (not an evidence claim)"

# --------------------------------------------------------------- rules
# (id, category, title, statement, parameters, confidence, evidence, uncertainty, algorithms)
R = []
def rule(rid, cat, title, statement, params, conf, ev, unc, algos):
    R.append(dict(rule_id=rid, category=cat, title=title, statement=statement,
                  parameters=params, confidence=conf, evidence=ev,
                  uncertainty=unc, affected_algorithms=algos))

# ---- Safety
rule("SAF-001","safety","Pre-participation screening and conservative mode",
 "Eight-question screen at onboarding (ACSM 2015 logic, PAR-Q+ domains, original wording). Symptoms, or known disease while inactive, recommend medical clearance and set conservative mode until the user confirms clearance.",
 {"questions":8,"rescreen_months":12,
  "conservative_mode":{"hiit_allowed":False,"zone3_allowed":False,"min_rir":3,"failure_allowed":False},
  "logic":{"symptoms_q3_q4":"clearance+conservative","disease_q1_q2_and_inactive":"clearance+conservative",
           "disease_q1_q2_and_active_no_symptoms":"moderate_only_until_clearance","limit_or_pregnancy_q5":"clinician_guidance+conservative",
           "msk_or_medication_q6_q7":"limitation_tags+note","all_no":"standard"}},
 HIGH,["Riebe2015","Warburton2011"],"Screen wording is original and untested; licence check needed before using PAR-Q+ text verbatim.",["onboarding","workout_generation","safety_validator"])
rule("SAF-002","safety","Red-flag symptoms trigger SAFETY STOP",
 "Chest pain/pressure, fainting or near-fainting, dizziness, unusual breathlessness, palpitations, sudden severe pain, or stroke-like signs end the session immediately with calm guidance; emergency number shown; resume only after user confirms resolution or medical review; first session back LIGHT.",
 {"symptoms":["chest_pain_pressure_tightness","fainting_or_near_fainting","dizziness_lightheadedness","unusual_breathlessness","palpitations_irregular_heartbeat","sudden_severe_pain","sudden_severe_headache","one_sided_weakness_numbness","confusion_or_speech_trouble"],
  "emergency_number_default":"112","first_session_after":"LIGHT","convert_to_lighter_workout":False},
 HIGH,["Riebe2015"],"Symptom list is conservative by design.",["readiness","guided_workout","safety_validator","ai_coach"])
rule("SAF-003","safety","In-session pain protocol",
 "Pain is a separate gate from readiness. Joint/tendon pain <=3/10 not worsening: continue with load -10-20% or shorter range. 4-6/10 or worsening: stop the exercise, offer substitutes with joint stress <=1 for that joint, region to conservative mode. >=7/10 or sharp, swelling, instability, locking, numbness/tingling, or after a fall: stop loading the region, end session if whole-body movement affected, suggest a professional.",
 {"continue_max_rating":3,"continue_load_reduction_pct":[10,20],"stop_exercise_min_rating":4,"stop_region_min_rating":7,
  "substitute_max_joint_stress":1,"stop_region_descriptors":["sharp","stabbing","swelling","instability","locking","numbness","tingling","after_fall_or_impact"]},
 EP,["Silbernagel2007"],"Thresholds are expert practice; rehab data (supervised) allow up to 5/10.",["guided_workout","substitution","safety_validator"])
rule("SAF-004","safety","Persistent pain and region conservative mode",
 "Same region painful in >=2 sessions or >7 days: suggest a physiotherapist/doctor; region stays conservative (joint stress <=1, RIR >=3, no failure, no jumping) until 2 consecutive pain-free sessions, then +1 stress level per week.",
 {"sessions_threshold":2,"days_threshold":7,"region_max_joint_stress":1,"region_min_rir":3,"pain_free_sessions_to_exit":2,"stress_step_up_per_week":1},
 EP,["Silbernagel2007"],"No direct evidence for the exact return sequence.",["workout_generation","substitution","safety_validator"])
rule("SAF-005","safety","Hard workload caps",
 "The engine never prescribes beyond these caps; neither the AI coach nor repeated taps can bypass them.",
 {"fractional_sets_per_muscle_week":{"beginner":12,"intermediate":16,"advanced":20},
  "direct_sets_per_muscle_session":{"beginner":6,"intermediate":8,"advanced":10},
  "working_sets_per_session":{"beginner":20,"intermediate":25,"advanced":30},
  "weekly_ssu":{"beginner":100,"intermediate":140,"advanced":180},
  "hiit_sessions_per_week":{"default":2,"conditional_max":3},
  "rir0_exercises_per_session":{"beginner_first_8_weeks":0,"otherwise":2},
  "training_days_per_week":6,
  "planned_weekly_load_increase":{"early_week_over_week_pct":20,"after_4_weeks_multiplier_of_3wk_mean":1.20},
  "intensity_rise_per_lift_per_week_pct":{"beginner":7.5,"intermediate":5,"advanced":5}},
 EP,["Pelland2024","Schoenfeld2017vol","Bell2023"],"Cap values are expert practice set below volumes tolerated in studies because conditioning is concurrent.",["all"])
rule("SAF-006","safety","User additions: warn, confirm, absolute ceiling",
 "User may deviate. Additions beyond a cap require explicit per-addition confirmation and are logged and counted. Absolute ceiling stops accidental runaway. Additions to a painful region are blocked.",
 {"confirm_beyond_cap":True,"absolute_ceiling":{"weekly_per_muscle_multiplier_of_cap":1.5,"working_sets_per_session":40,"hiit_sessions_per_week":4},"block_painful_region":True},
 EP,[],"Product rule.",["guided_workout","safety_validator"])
rule("SAF-007","safety","Illness gate",
 "Fever, chills, body aches, chest symptoms, vomiting or diarrhoea lock the day to RECOVERY (rest). Return per REG-005.",
 {"systemic_symptoms":["fever","chills","body_aches","chest_symptoms","vomiting","diarrhoea"],"tier":"RECOVERY"},
 EP,[],"Expert practice ('neck check' style).",["readiness","safety_validator"])
rule("SAF-008","safety","Final pre-render safety validator",
 "Every generated or modified session is checked: per-muscle and per-session sets, weekly SSU and planned load, HIIT count and spacing, pattern balance, pain-region exclusions, contraindications, tier compliance, screening mode, failure policy. Auto-correct and log; fallback to LIGHT; never crash; never display unvalidated.",
 {"checks":["SAF-005","VOL-005","VOL-008","HIIT-001","HIIT-004","PAT-001","PAT-002","PAT-003","SAF-003","SAF-004","RDY-004","SAF-001","INT-003","LOAD-005","MOD-001"],
  "fallback":"LIGHT_template","display_unvalidated":False},
 HIGH,[],"Engineering requirement.",["safety_validator"])
rule("SAF-009","safety","AI coach boundary",
 "Coach explains engine decisions from the Decision Log and rule IDs; no diagnosis, medication/supplement or extreme-diet advice; can only request engine actions which the engine validates; never writes sets, reps or loads; output safety-filtered; embedded instructions treated as data; offline fallback to Tier 1.",
 {"tier1_required":True,"tier2_optional":True,"can_write_parameters":False,"allowed_requests":["shorter_session","substitution","easier_tier","explain"],"prompt_injection":"treat_as_data"},
 HIGH,[],"Engineering requirement.",["ai_coach"])
rule("COACH-001","safety","Coaching voice",
 "Calm, competent, honest, brief, non-judgemental. No body-shaming, guilt, punishment exercise, extreme motivation or 'no pain, no gain'.",
 {"prohibited":["body_shaming","guilt","punishment_exercise","extreme_motivation","medical_claims"]},
 EP,[],"Product rule.",["ai_coach","ui_copy"])

# ---- Public health, frequency
rule("PH-001","public_health","WHO floor accounting",
 "Equivalent minutes = Z1 + 2 x (Z2 + Z3 work) + optional logged walking; target >=150/week; muscle-strengthening >=2 days. If the schedule cannot meet the floor in the gym, the weekly plan says so and suggests walking.",
 {"target_equivalent_minutes":150,"upper_range_minutes":300,"vigorous_multiplier":2,"strength_days_min":2},
 HIGH,["Bull2020","Garber2011"],"",["weekly_scheduling","progress_summary"])
rule("FREQ-001","frequency","Training days per week",
 "Train 2-6 days/week with at least one full rest day; default 3 when unknown (ASSUMPTION).",
 {"min_days":2,"max_days":6,"min_rest_days":1,"default_days":3},HIGH,["ACSM2026","Bull2020"],"Upper bound is expert practice.",["weekly_scheduling"])
rule("FREQ-002","frequency","Per-muscle exposures",
 "Each major muscle gets >=2 exposures/week; an exposure is >=2 fractional hard sets in one session.",
 {"min_exposures_per_week":2,"exposure_min_fractional_sets":2},MOD,["Schoenfeld2016freq","Schoenfeld2019freq","Pelland2024","ACSM2026"],"With volume equated frequency matters little for hypertrophy; it is a volume-distribution tool.",["volume_allocation","weekly_scheduling"])
rule("FREQ-003","frequency","Resistance training days",
 "Resistance training on >=2 days every week.",{"min_days":2},HIGH,["Bull2020","ACSM2026"],"",["weekly_scheduling"])
rule("FREQ-004","frequency","Main-pattern frequency in strength blocks",
 "Squat, hinge, horizontal push and horizontal pull loaded 2-3 x/week in strength blocks.",
 {"min":2,"max":3,"patterns":["squat","hinge","horizontal_push","horizontal_pull"]},MOD,["Pelland2024","Currier2023","Grgic2018freq"],"",["weekly_scheduling","periodization"])
rule("FREQ-005","frequency","Aerobic exposure distribution",
 "Aerobic work appears on >=3 days/week when training >=3 days; short post-strength blocks count.",
 {"min_days":3,"applies_when_training_days_gte":3,"min_block_minutes":10},EP,["Bull2020"],"",["weekly_scheduling"])

# ---- Volume
rule("VOL-001","volume","Hard set definition",
 "A hard set is a working set ending at RIR <=4 (RPE >=6). Warm-up sets and RIR >=5 count zero.",
 {"max_rir":4,"min_rpe":6},MOD,["Robinson2024","Refalo2023"],"Exact proximity threshold for a 'stimulating' set is uncertain.",["volume_accounting"])
rule("VOL-002","volume","Fractional set counting",
 "1.0 set to each primary muscle, 0.5 to each secondary, 0 to stabilisers.",
 {"primary":1.0,"secondary":0.5,"stabiliser":0.0},MOD,["Pelland2024"],"Preprint evidence; per-exercise credits are judgement calls.",["volume_accounting"])
rule("VOL-003","volume","Volume landmarks (fractional sets/muscle/week)",
 "Blocks start at 'start'; usual working range; the engine never plans above 'cap'. Landmark names are coaching vocabulary.",
 {"maintenance":{"beginner":4,"intermediate":4,"advanced":6},
  "block_start":{"beginner":6,"intermediate":8,"advanced":10},
  "working_range":{"beginner":[8,12],"intermediate":[10,16],"advanced":[12,20]},
  "cap":{"beginner":12,"intermediate":16,"advanced":20}},
 EP,["Israetel2021","Pelland2024","Schoenfeld2017vol","ACSM2026"],"Individual response varies widely; values for small muscles may differ.",["volume_allocation","periodization"])
rule("VOL-004","volume","Fatigue-driven volume reduction",
 "If >=2 fatigue signals (DEL-001) persist across >=2 sessions, accessory sets drop 30% until they clear.",
 {"min_signals":2,"min_sessions":2,"accessory_reduction_pct":30},EP,["Meeusen2013","Saw2016"],"",["volume_allocation","workout_generation"])
rule("VOL-005","volume","Per-session per-muscle cap",
 "Direct hard sets per muscle per session <= cap by level.",
 {"cap":{"beginner":6,"intermediate":8,"advanced":10}},LIM,["Pelland2024"],"Per-session dose ceiling poorly studied.",["volume_allocation","safety_validator"])
rule("VOL-006","volume","Weekly volume progression within a block",
 "Each week a muscle may gain sets only if its exercises held or improved and no fatigue signal is active.",
 {"step":{"beginner":[1,1],"intermediate":[1,2],"advanced":[1,2]}},EP,["Israetel2021"],"",["volume_allocation"])
rule("VOL-007","volume","Systemic stress units (planning)",
 "SSU = sum over sets C_ex x E_RIR + sum over conditioning t_min x Z_zone x J_modality. Weekly SSU <= level ceiling and (after 4 weeks) <= 1.15 x 3-week mean. Personal factor k = median(sRPE load / SSU) after 4 weeks.",
 {"C":{"isolation_or_core":0.5,"machine_or_cable_compound":0.8,"free_weight_compound_carry_sled":1.0,"heavy_bilateral_squat_hinge_ge80pct":1.5},
  "E":{"rir0":1.2,"rir1_2":1.0,"rir3_4":0.8},
  "Z_per_min":{"Z1":0.4,"Z2":0.8,"Z3_work":1.5,"interval_rest":0.2},
  "J":{"rower_skierg_elliptical":0.9,"ropes_kettlebell_sled_carries":1.0,"jump_rope_jumping_circuits":1.1},
  "weekly_ceiling":{"beginner":100,"intermediate":140,"advanced":180},"rolling_multiplier":1.15,"k_after_weeks":4},
 EP,["Foster2001"],"Novel model; coefficients are judgement and must be validated in Phase 4 simulations.",["volume_allocation","workload","safety_validator"])
rule("VOL-008","volume","Working sets per session cap",
 "Working sets per session (including core) <= cap.",
 {"cap":{"beginner":20,"intermediate":25,"advanced":30}},EP,["Iversen2021"],"",["workout_generation","safety_validator"])

# ---- Intensity
rule("INT-001","intensity","RPE-RIR mapping validity",
 "RIR = 10 - RPE used only from RPE 6 to 10 on working sets.",
 {"valid_rpe_min":6,"valid_rpe_max":10,"mapping":{"10":0,"9.5":0,"9":1,"8.5":1.5,"8":2,"7":3,"6":4}},MOD,["Zourdos2016","Helms2016","Halperin2022"],"RIR error ~1 rep; larger far from failure and in novices.",["logging","e1rm"])
rule("INT-002","intensity","RIR targets",
 "Targets by level and exercise class; week 1 of a block +1 RIR, last loading week -1 (not below floor).",
 {"beginner_first_8_weeks":{"compound":3,"isolation":2},"intermediate":{"compound":[1,3],"isolation":[0,2]},
  "advanced":{"compound":[1,2],"isolation":[0,1]},"block_week1_offset":1,"block_last_week_offset":-1,"deload_or_light_min_rir":3},
 MOD,["Robinson2024","Refalo2023","ACSM2026"],"",["workout_generation","periodization"])
rule("INT-003","intensity","Failure policy",
 "RIR 0 only on the last set of FailureSafe exercises (machine, cable, isolation dumbbell, safe bodyweight), <=2 exercises/session, FULL tier, not in deload, not beginners' first 8 weeks, never on failure-unsafe lifts.",
 {"max_exercises_per_session":2,"allowed_tiers":["FULL"],"beginner_lockout_weeks":8,
  "failure_unsafe_examples":["barbell_back_squat","barbell_bench_press","overhead_press","deadlift","loaded_carries","olympic_lifts"]},
 MOD,["Robinson2024","Grgic2022fail","ACSM2026"],"",["workout_generation","safety_validator"])
rule("INT-004","intensity","Estimated 1RM (Epley, RIR-adjusted)",
 "e1RM = load x (1 + (reps + RIR)/30); update only from sets with reps+RIR <= 12 and RIR <= 3; session value = best valid set; smoothed E <- E + 0.4 (S - E); single update capped at +5%.",
 {"formula":"epley_rir_adjusted","divisor":30,"max_reps_plus_rir":12,"max_rir":3,"smoothing_alpha":0.4,"max_single_rise_pct":5},
 MOD,["LeSuer1997","Brzycki1993","Epley1985"],"Formula accuracy falls above ~10 reps and varies by lift.",["e1rm","load_selection"])
rule("INT-005","intensity","Load selection from e1RM",
 "load = e1RM / (1 + (target reps + target RIR)/30), then rounded per PROG-003; without e1RM use CAL-001.",
 {"divisor":30},MOD,["LeSuer1997"],"",["load_selection"])
rule("INT-006","intensity","Teaching effort to beginners",
 "First 4 weeks: rep targets plus 'how many more could you have done? 0/1/2/3/4+'; RPE language introduced gradually.",
 {"weeks":4,"rir_options":["0","1","2","3","4+"]},MOD,["Halperin2022","Helms2016"],"",["logging_ui"])
rule("INT-007","intensity","In-session autoregulation",
 "After the first working set: RPE < target-1 with reps hit -> +1 increment (<=5%); RPE > target+1 or reps below range -> -5%; net change <= +/-10% per exercise per session.",
 {"easy_threshold":-1,"hard_threshold":1,"step_up_max_pct":5,"step_down_pct":5,"net_limit_pct":10},EP,["Zhang2021","Helms2016"],"",["guided_workout"])

# ---- Reps
rule("REP-001","reps","Strength range","3-6 reps at ~80-88% 1RM; 1-3 only for advanced at RPE <=8.",
 {"reps":[3,6],"pct_1rm":[80,88],"advanced_low_reps":[1,3],"advanced_low_rep_max_rpe":8},HIGH,["ACSM2026","Currier2023","Schoenfeld2021reps"],"Exact range is practice.",["workout_generation"])
rule("REP-002","reps","Hypertrophy range","Compounds 6-12, isolation 8-20 near target RIR.",
 {"compound":[6,12],"isolation":[8,20],"pct_1rm":[60,80]},HIGH,["Schoenfeld2017load","Schoenfeld2021reps"],"",["workout_generation"])
rule("REP-003","reps","Muscular endurance range","15-30 reps or 30-60 s at <60% 1RM.",{"reps":[15,30],"seconds":[30,60],"pct_1rm_max":60},MOD,["Schoenfeld2021reps"],"",["workout_generation"])
rule("REP-004","reps","Power","3-5 reps at 30-70% 1RM with maximal intent; reps x sets < 24; stop when speed visibly drops.",
 {"reps":[3,5],"pct_1rm":[30,70],"max_total_reps":23,"stop_rule":"visible_speed_drop"},MOD,["ACSM2026","Fragala2019"],"No velocity device; subjective speed-drop rule.",["workout_generation"])
rule("REP-005","reps","Core dosing","Holds 10-45 s or 6-12 controlled reps per side.",{"hold_seconds":[10,45],"reps_per_side":[6,12]},EP,["Kibler2006"],"",["workout_generation"])
rule("REP-006","reps","Conditioning units","Minutes, metres, watts, calories or reps by modality.",{"units":["minutes","metres","watts","calories","reps"]},EP,[],"",["workout_generation"])

# ---- Rest
rule("REST-001","rest","Heavy compounds",">=80% or <=6 reps: 120-300 s, default 180 s.",{"min_s":120,"max_s":300,"default_s":180},MOD,["Grgic2018rest"],"",["timers","time_budget"])
rule("REST-002","rest","Moderate compounds","6-12 reps: 90-150 s, default 120 s.",{"min_s":90,"max_s":150,"default_s":120},MOD,["Singer2024"],"",["timers","time_budget"])
rule("REST-003","rest","Isolation / accessories","60-90 s, default 75 s.",{"min_s":60,"max_s":90,"default_s":75},MOD,["Singer2024"],"",["timers","time_budget"])
rule("REST-004","rest","Non-competing supersets","45-75 s between paired exercises, default 60 s.",{"min_s":45,"max_s":75,"default_s":60},EP,["Iversen2021"],"",["timers","time_budget"])
rule("REST-005","rest","Circuits","15-45 s transitions, default 30 s.",{"min_s":15,"max_s":45,"default_s":30},EP,[],"",["timers"])
rule("REST-006","rest","Core","30-60 s, default 45 s.",{"min_s":30,"max_s":60,"default_s":45},EP,[],"",["timers"])
rule("REST-007","rest","Autoregulated rest and compression",
 "User may start next set anywhere in range; timer alerts at default; under time pressure rests shrink toward minimum, accessories first, heavy compounds last and never below minimum.",
 {"compression_order":["P5","P4","P3","P1"]},EP,["Singer2024","Grgic2018rest"],"",["timers","time_budget"])

# ---- Order
rule("ORD-001","order","Session sequence",
 "Warm-up -> power/skill -> primary compound -> secondary compound -> accessories -> core -> conditioning -> cool-down.",
 {"sequence":["warmup","power","primary_compound","secondary_compound","accessories","core","conditioning","cooldown"]},MOD,["ACSM2026","Nunes2021"],"",["workout_generation"])
rule("ORD-002","order","Order exceptions",
 "Conditioning-priority day: conditioning moves to step 3 and lower-body strength volume -30%. Weak point may precede secondary compounds. Restricted-joint mobility moves into warm-up.",
 {"conditioning_priority_lower_volume_reduction_pct":30},EP,["Murlasits2018"],"",["workout_generation"])
rule("ORD-003","order","Superset eligibility",
 "Only non-competing pairs at one station or within a few steps; never heavy main lifts at RIR <=2; never across two stations in a crowded gym.",
 {"allowed_pairs":["push+pull","upper+lower","compound+core"],"forbid_main_lift_rir_lte":2},EP,["Iversen2021"],"",["time_budget","workout_generation"])

# ---- Concurrent
rule("CON-001","concurrent","Strength first in shared sessions","Resistance before conditioning except conditioning-priority days.",{},MOD,["Murlasits2018","Schumann2022"],"",["workout_generation"])
rule("CON-002","concurrent","Same-day separation","Two hard sessions on one day separated by >=6 h, else merged strength-first.",{"min_hours":6,"hard_aerobic_min_minutes":30},LIM,["Robineau2016"],"",["weekly_scheduling"])
rule("CON-003","concurrent","HIIT vs heavy legs","No HIIT in ~24 h before heavy lower-body or power session; same day HIIT after strength.",{"hours_before_heavy_lower":24},EP,["Sabag2018","Schumann2022"],"",["weekly_scheduling","workout_generation"])
rule("CON-004","concurrent","Low-impact modality preference","Low-impact, concentric-dominant modalities first; impact work <=1/week and not the day before heavy legs.",{"impact_sessions_per_week_max":1},MOD,["Wilson2012"],"",["conditioning_selection"])
rule("CON-005","concurrent","Power protection","Power work at session start; never after HIIT the same day.",{},MOD,["Schumann2022"],"",["workout_generation"])
rule("CON-006","concurrent","Aerobic dose in strength blocks","Mostly Z1 and <=150 prescribed min/week in strength-emphasis blocks.",{"max_prescribed_minutes":150},LIM,["Wilson2012"],"",["periodization"])

# ---- Aerobic
rule("AER-001","aerobic","Aerobic intensity zones",
 "Talk test + CR10, optional %HRR: Z1 easy (full sentences, CR10 3-4, 40-59% HRR, moderate), Z2 tempo (talk not comfortable, CR10 5-6, 60-79%, vigorous), Z3 intervals (few words, CR10 7-9, >=80%, vigorous), Z4 sprint (CR10 10).",
 {"Z1":{"cr10":[3,4],"hrr_pct":[40,59],"who":"moderate"},"Z2":{"cr10":[5,6],"hrr_pct":[60,79],"who":"vigorous"},
  "Z3":{"cr10":[7,9],"hrr_pct":[80,100],"who":"vigorous"},"Z4":{"cr10":[10,10],"who":"vigorous"}},
 MOD,["Foster2018talk","Borg1982","Garber2011"],"",["conditioning","ui"])
rule("AER-002","aerobic","Weekly intensity distribution","About 75-80% of aerobic minutes in Z1; remainder Z2/Z3 incl. HIIT.",{"z1_share":[0.75,0.80]},LIM,["Buchheit2013"],"Distribution evidence comes from endurance athletes.",["weekly_scheduling"])
rule("AER-003","aerobic","Aerobic progression","Duration first (+2-5 min/session, <=+15%/week) to ~30-40 min Z1; then Z2 tempo; then intervals.",{"per_session_minutes":[2,5],"weekly_increase_pct_max":15,"z1_target_minutes":[30,40]},EP,[],"",["progression"])

# ---- HIIT
rule("HIIT-001","hiit","Weekly HIIT ceiling",
 "<=2/week; 3rd only for intermediate/advanced, FULL day, no fatigue signal or workload flag, conditioning block. >=6 min Z3 work or a circuit at CR10 >=7 for >=8 min counts as HIIT.",
 {"default_max":2,"conditional_max":3,"count_if_z3_work_minutes_gte":6,"count_if_circuit_cr10_gte":7,"circuit_minutes_gte":8},EP,["Buchheit2013","Wen2019"],"",["weekly_scheduling","safety_validator"])
rule("HIIT-002","hiit","HIIT protocol menu",
 "Protocol chosen by block and purpose, not popularity.",
 {"long":{"reps":[3,5],"work_s":[180,240],"rest_s":[120,180],"cr10":[7,8]},
  "medium":{"reps":[6,10],"work_s":[60,120],"rest_s":[60,120],"cr10":[8,8]},
  "short":{"reps":[10,20],"work_s":[15,30],"rest_s":[15,60],"cr10":[8,9],"beginner_ratio":"1:2"},
  "sprint":{"reps":[4,8],"work_s":[10,20],"rest_s":[90,180],"cr10":[10,10],"min_ratio":"1:6","eligibility":"advanced","per_week_max":1},
  "tempo_not_hiit":{"reps":[2,3],"work_s":[480,720],"rest_s":[120,180],"cr10":[5,6]}},
 MOD,["Buchheit2013","Wen2019","Milanovic2015"],"",["conditioning"])
rule("HIIT-003","hiit","HIIT prerequisites","Screen clear or cleared, calibration done, >=3 weeks Z1 base; first HIIT short 1:2 at CR10 7-8.",{"base_weeks":3,"first_protocol":"short_1_2"},EP,["Weston2014","Riebe2015"],"",["periodization","safety_validator"])
rule("HIIT-004","hiit","HIIT spacing",">=48 h preferred, 24 h minimum between HIIT sessions, plus CON-003.",{"preferred_hours":48,"min_hours":24},EP,[],"",["weekly_scheduling","safety_validator"])
rule("HIIT-005","hiit","Per-session HIIT work caps","Long <=20 min work, short <=10 min, sprint <=2.5 min; <=35 min conditioning after strength.",{"long_work_min_max":20,"short_work_min_max":10,"sprint_work_min_max":2.5,"post_strength_conditioning_min_max":35},EP,[],"",["conditioning","safety_validator"])

# ---- Modality
rule("MOD-001","modality","Excluded modalities","Treadmill running, all stationary bikes and stair machines are never prescribed; air/fan bikes and treadmill walking excluded until the Product Owner says otherwise.",
 {"excluded":["treadmill_running","stationary_bike","stair_machine"],"excluded_pending_confirmation":["air_fan_bike","treadmill_walking"]},HIGH,[],"User constraint.",["conditioning","substitution","safety_validator"])
rule("MOD-002","modality","Modality matrix and selection score",
 "Score = 0.35 stimulus fit + 0.25 (1-interference/5) x leg priority + 0.20 (1-impact/5) x joint sensitivity + 0.10 variety + 0.10 preference.",
 {"weights":{"stimulus_fit":0.35,"interference":0.25,"impact":0.20,"variety":0.10,"preference":0.10},
  "matrix":{"rower":[5,3,1,3,2,2,5,5,5],"skierg":[4,3,1,2,2,1,4,5,5],"elliptical":[4,2,1,1,1,1,5,3,3],"sled":[4,4,1,1,2,3,2,5,3],
            "battle_ropes":[3,4,1,2,2,1,1,5,2],"kettlebell":[4,3,2,3,3,3,2,5,3],"carries":[3,3,1,1,2,2,2,4,4],
            "medball":[3,2,2,2,2,1,1,4,2],"bodyweight_circuit":[3,3,2,1,2,2,2,4,3],"jump_rope":[4,2,3,3,2,2,3,4,2]},
  "matrix_columns":["cardio","local_fatigue","impact","skill","recovery_cost","interference","steady_state","intervals","measurable"]},
 EP,["Wilson2012"],"Ratings are expert judgement.",["conditioning_selection"])

# ---- Patterns
rule("PAT-001","patterns","Weekly pattern coverage",
 ">=3 days: patterns 1-7 each >=1x, squat/hinge/h-push/h-pull >=2x, carry >=1x, anti-ext/anti-rot/anti-lat >=1x, rotation >=1x per 2 weeks. 2 days: 1-7 and 10-12 weekly; carry and rotation alternate weeks.",
 {"lifting_patterns_min":1,"main_patterns_min":2,"carry_min":1,"core_categories_min":1,"rotation_per_weeks":2},EP,[],"",["weekly_scheduling","safety_validator"])
rule("PAT-002","patterns","Pull:push ratio","Weekly pulling sets / pushing sets between 1.0 and 1.5.",{"min":1.0,"max":1.5},EP,[],"",["volume_allocation","safety_validator"])
rule("PAT-003","patterns","Knee:hip ratio","Knee-dominant sets / hip-dominant sets between 0.67 and 1.5.",{"min":0.67,"max":1.5},EP,[],"",["volume_allocation","safety_validator"])
rule("PAT-004","patterns","Unilateral minimum",">=1 unilateral lower-body exercise/week (>=2 when >=4 days).",{"min":1,"min_when_4plus_days":2},EP,[],"",["weekly_scheduling"])

# ---- Bodyweight, core, mobility, warm-up
rule("BW-001","bodyweight","Bodyweight progression levers","Reps -> ROM -> tempo/pauses -> leverage -> unilateral/less assistance -> added load; regressions reverse.",
 {"order":["reps","range_of_motion","tempo_or_pause","leverage","unilateral_or_less_assistance","added_load"]},EP,[],"",["progression"])
rule("BW-002","bodyweight","Bodyweight step-up trigger","All sets at top of range at RIR >=1 for 2 sessions; assisted pull-up: -1 step after 3x8 at RIR 2.",
 {"top_of_range":{"push_up":15,"pull_up":[10,12],"squat":20},"consecutive_sessions":2,"min_rir":1,"assisted_pullup":{"sets":3,"reps":8,"rir":2}},EP,[],"",["progression"])
rule("CORE-001","core","Anti-movement emphasis","Anti-extension, anti-rotation, anti-lateral flexion and bracing; controlled rotation; no default high-rep crunches.",{"default_spinal_flexion_sets":0},EP,["Kibler2006"],"",["workout_generation"])
rule("CORE-002","core","Core progression","Lever before load: 3 x 30-45 s or 10-12/side at ~RIR 2 for 2 sessions -> next rung.",{"hold_seconds":[30,45],"reps_per_side":[10,12],"sessions":2},EP,[],"",["progression"])
rule("CORE-003","core","Core frequency and volume","2-4 sessions/week, 6-12 sets/week, <=4 sets/session.",{"sessions":[2,4],"weekly_sets":[6,12],"max_sets_per_session":4},EP,[],"",["weekly_scheduling"])
rule("MOB-001","mobility","Pre-workout dynamic mobility","3-5 min dynamic drills for the session's joints inside the warm-up.",{"minutes":[3,5]},MOD,["Behm2016","Fradkin2010"],"",["warmup"])
rule("MOB-002","mobility","Static stretching timing","Not in default warm-up; if preferred <=30 s per muscle then dynamic work. Post-session 30-60 s per chosen muscle plus 1-2 min slow breathing.",
 {"pre_max_seconds_per_muscle":30,"post_seconds_per_muscle":[30,60],"breathing_minutes":[1,2]},MOD,["Behm2016"],"Wind-down breathing is expert practice.",["warmup","cooldown"])
rule("MOB-003","mobility","Intra-workout mobility","Optional drill for the next exercise during rests if it doesn't tire the working muscle.",{},EP,[],"",["guided_workout"])
rule("MOB-004","mobility","Full range of motion default","Lift through full range by default.",{},MOD,["Afonso2021"],"",["workout_generation"])
rule("MOB-005","mobility","Off-day routine","Optional 10-20 min, 2-3x/week; 2 x 30-60 s per position.",{"minutes":[10,20],"per_week":[2,3]},EP,[],"",["weekly_scheduling"])
rule("WU-001","warmup","RAMP warm-up","Raise 3-5 min (CR10 2-3), Activate & Mobilise 3-4 min, Potentiate with ramp sets; 8-12 min total; HIIT days 5 min easy + 3 x 30 s builds.",
 {"raise_min":[3,5],"activate_mobilise_min":[3,4],"total_min":[8,12],"hiit_builds_cr10":[5,7,8]},MOD,["Fradkin2010","Jeffreys2019"],"",["warmup"])
rule("WU-002","warmup","Ramp-up sets",
 "First main lift: bar x 8-10 -> 50% x 5 -> 70% x 3 -> 85% x 1-2 (85% only if reps <=6 or >=80% e1RM). Working load <= bar+10 kg: one set at 50% x 8. Second main lift: 60% x 5 -> 80% x 2-3.",
 {"first_lift":[["bar",[8,10]],[0.50,5],[0.70,3],[0.85,[1,2]]],"heavy_step_condition":{"reps_lte":6,"or_pct_e1rm_gte":80},
  "light_threshold_kg_over_bar":10,"light_single_set":[0.50,8],"second_lift":[[0.60,5],[0.80,[2,3]]]},EP,["Jeffreys2019"],"",["warmup"])
rule("WU-003","warmup","Warm-up compression","Floor 5 min (2 min raise, 1-2 drills, 2-3 ramp sets); never removed.",{"floor_minutes":5},EP,[],"",["warmup","time_budget"])
rule("WU-004","warmup","Age-adjusted warm-up",">=50: +2-3 min raise; >=65: + balance drill.",{"age_50_extra_min":[2,3],"age_65_balance_drill":True},EP,["Fragala2019"],"",["warmup"])

# ---- Readiness
rule("RDY-001","readiness","Readiness raw score",
 "R_raw = 25 x [0.30(S-1) + 0.30(E-1) + 0.25(D-1) + 0.15(M-1)]; items 1-5, 3 = normal; all 3s = 50.",
 {"weights":{"sleep_quality":0.30,"energy":0.30,"soreness":0.25,"stress":0.15},"scale":[1,5],"multiplier":25,"max_seconds":15},EP,["Saw2016"],"Weights are judgement.",["readiness"])
rule("RDY-002","readiness","Personal baseline blend",
 "After >=14 check-ins in 28 days: z = (R_raw - mu28)/max(sd28, 8); R_pers = 50 + 15 clamp(z,-2,2); R = 0.6 R_raw + 0.4 R_pers. Floor: R_raw < 15 caps at LIGHT.",
 {"min_checkins":14,"window_days":28,"min_sd":8,"z_clamp":2,"pers_center":50,"pers_scale":15,"raw_weight":0.6,"pers_weight":0.4,"raw_floor_for_light_cap":15},EP,[],"",["readiness"])
rule("RDY-003","readiness","Readiness tiers",
 "R >= 40 FULL; 28-39 MODIFIED; 15-27 LIGHT; < 15 RECOVERY. Sleep, energy or soreness rated 1 caps at MODIFIED.",
 {"FULL_min":40,"MODIFIED_min":28,"LIGHT_min":15,"item_of_1_cap":"MODIFIED"},EP,[],"Seed 'R < 40 = low' recast on a scale where 3 = normal = 50.",["readiness"])
rule("RDY-004","readiness","Tier modifications",
 "MODIFIED: sets -30%, RIR +1, no RIR 0, main loads <=95%, HIIT -> 15-20 min steady Z1-Z2. LIGHT: sets -50%, main loads <=85% at RIR >=3, no HIIT, 10-20 min Z1, 5-10 min mobility. RECOVERY: no resistance training; optional 10-30 min Z1 + mobility.",
 {"MODIFIED":{"sets_reduction_pct":30,"rir_offset":1,"max_main_load_pct":95,"hiit_replacement_minutes":[15,20]},
  "LIGHT":{"sets_reduction_pct":50,"max_main_load_pct":85,"min_rir":3,"z1_minutes":[10,20],"mobility_minutes":[5,10]},
  "RECOVERY":{"resistance":False,"optional_z1_minutes":[10,30]}},EP,["Craven2022","Saw2016"],"",["workout_generation"])
rule("RDY-005","readiness","Sleep-hours caps","<4 h caps at LIGHT; 4 to <5 h caps at MODIFIED.",{"light_cap_below_h":4,"modified_cap_below_h":5},MOD,["Craven2022"],"Effect size is moderate evidence; the caps are judgement.",["readiness"])
rule("RDY-006","readiness","Fatigue-signal step-down",">=2 active DEL-001 signals lower the tier one step, never below LIGHT on signals alone.",{"min_signals":2,"floor":"LIGHT"},EP,["Meeusen2013"],"",["readiness"])
rule("RDY-007","readiness","User tier choice","Easier tier always allowed; harder one step up with warning; never past a safety, pain-gate or screening limit.",{"max_steps_up":1},EP,[],"Product rule.",["readiness_ui"])

# ---- Load
rule("LOAD-001","workload","Session load","Session load (AU) = session RPE (CR10) x session minutes (pauses >10 min removed).",{"pause_exclusion_min":10},MOD,["Foster2001"],"",["workload"])
rule("LOAD-002","workload","sRPE collection timing","Asked on the summary after cool-down, ideally >=10 min after last hard effort; one prompt within 24 h if skipped.",{"min_minutes_after":10,"late_prompt_hours":24},MOD,["Foster2001"],"",["summary_ui"])
rule("LOAD-003","workload","Weekly load, monotony, strain","Weekly load = 7-day sum; monotony = mean/sample SD of daily loads (rest = 0); strain = weekly load x monotony; monotony > 2.0 soft flag.",{"monotony_flag":2.0,"sd":"sample"},MOD,["Foster1998"],"",["workload"])
rule("LOAD-004","workload","EWMA acute:chronic ratio (soft flag)","Acute lambda 0.25, chronic 2/29; shown after >=28 days; > 1.5 soft flag; never described as injury risk.",{"acute_lambda":0.25,"chronic_lambda":0.0689655,"min_days":28,"flag_gt":1.5},LIM,["Williams2017","Gabbett2016","Lolli2019","Impellizzeri2021"],"Ratio has statistical flaws and weak causal support.",["workload"])
rule("LOAD-005","workload","Planned weekly load cap","Predicted weekly load (k x planned SSU) <= 1.20 x mean of last 3 weeks; before 4 weeks <= +20% week over week; deload/return ramps exempt.",{"multiplier":1.20,"early_week_over_week_pct":20,"history_weeks":3},EP,["Gabbett2016"],"",["weekly_scheduling","safety_validator"])
rule("LOAD-006","workload","Cold start","<14 days: per-session caps and calibration only; 14-27 days: week-over-week rule; >=28: all signals.",{"phase1_days":14,"phase2_days":28},EP,[],"",["workload"])

# ---- Deload
rule("DEL-001","deload","Fatigue signals",
 "F1 performance (>=2 main lifts >=3% below recent best on 2 consecutive exposures, or targets missed on >=2 lifts in 2 sessions); F2 effort creep (sRPE >= plan+1.5 in 3 of last 4); F3 readiness (7-day mean >1 SD below baseline, or raw mean <35); F4 soreness (<=2 on 3 of 5 check-ins, or same ache twice in 7 days); F5 workload (EWMA >1.5 or monotony >2.0); F6 self-report run-down twice in 7 days.",
 {"F1":{"min_lifts":2,"drop_pct":3,"consecutive_exposures":2},"F2":{"rpe_over_plan":1.5,"of_last":[3,4]},"F3":{"sd_below":1,"raw_mean_below":35},
  "F4":{"soreness_lte":2,"of_last":[3,5],"same_ache_days":7},"F5":{"ewma_gt":1.5,"monotony_gt":2.0},"F6":{"times":2,"days":7}},EP,["Meeusen2013","Saw2016"],"",["deload","readiness"])
rule("DEL-002","deload","Deload decision",
 ">=3 signals: deload next session. 2 signals for >=2 sessions: lighter week (3 MODIFIED sessions) then re-check. Block end: >=1 signal -> deload, else pivot. 10 weeks without either: offer a lighter week.",
 {"deload_now_signals":3,"lighter_week_signals":2,"lighter_week_sessions":3,"block_end_signals":1,"offer_after_weeks":10},EP,["Bell2023"],"",["deload"])
rule("DEL-003","deload","Deload prescription","5-7 days; same days; sets -50%; loads 85-90% at RIR >=3; no HIIT; Z1 only; extra mobility; not total rest by default.",
 {"days":[5,7],"sets_reduction_pct":50,"load_pct":[85,90],"min_rir":3,"hiit":False},LIM,["Bell2023","Coleman2024"],"",["deload"])
rule("DEL-004","deload","Resumption after deload","Next block at block-start volume, loads 95-100% of pre-deload, RIR +1 in week 1.",{"load_pct":[95,100],"week1_rir_offset":1},EP,[],"",["periodization"])

# ---- Periodization
rule("PER-001","periodization","Autoregulated hybrid architecture","Blocks set emphasis; within-week DUP for main patterns; session autoregulation by readiness and RIR; beginners session-to-session progression.",{},MOD,["Moesgaard2022","ACSM2026","Zhang2021"],"",["periodization"])
rule("PER-002","periodization","Block length","4-6 loading weeks (default 5) then deload or pivot per DEL-002.",{"min_weeks":4,"max_weeks":6,"default_weeks":5},EP,["Bell2023"],"",["periodization"])
rule("PER-003","periodization","12-month block sequence","Calibrate(2) -> Foundation -> Build -> Strength -> Conditioning -> Build II -> Strength II -> Power & athleticism -> Consolidation -> Review(+2 flex).",
 {"sequence":["calibrate","foundation","build","strength","conditioning","build_2","strength_2","power_athleticism","consolidation","review"],"calibrate_weeks":2,"flex_weeks":2},EP,[],"",["periodization"])
rule("PER-004","periodization","Non-maximal check-ins","Block end: e1RM trends, 10-min row/SkiErg at CR10 5, bodyweight reps at RIR 1, optional measurements; 2,000 m row opt-in for intermediate+.",{"aerobic_check_minutes":10,"aerobic_check_cr10":5},EP,[],"",["periodization","progress"])
rule("PER-005","periodization","Disruption handling","<50% sessions pauses block clock; travel -> substitution mode; holidays -> deload/pivot/maintenance; >=14 days -> REG-004; >=4 weeks restarts block.",{"pause_below_session_share":0.5,"restart_block_after_weeks":4},EP,[],"",["periodization"])
rule("PER-006","periodization","Priority weighting","#1 priority's blocks +1 week, lowest priority's -1 (min 4); #1 priority starts at block-start + 2 sets.",{"top_priority_weeks":1,"low_priority_weeks":-1,"min_block_weeks":4,"top_priority_extra_sets":2},EP,[],"Product rule.",["periodization","volume_allocation"])

# ---- Progression
rule("PROG-001","progression","Load progression","All sets at top of range, mean RPE <= target+0.5, form solid, recovery ok -> smallest available step of 2.5-5%.",{"min_step_pct":2.5,"max_step_pct":5.0,"rpe_tolerance":0.5},MOD,["ACSM2026"],"",["progressive_overload"])
rule("PROG-002","progression","Double progression","Reps before load; extend range up to +3 (max 15 compounds, 20 isolation) when next step >5%; on the jump, reps = max(bottom, floor(reps x old/new)).",{"range_extension_max":3,"compound_max_reps":15,"isolation_max_reps":20},EP,[],"",["progressive_overload"])
rule("PROG-003","progression","Rounding to real equipment","Nearest available load <= 2% above target, else highest below.",{"over_target_tolerance_pct":2},EP,[],"Product rule.",["load_selection"])
rule("PROG-004","progression","Hold or reduce","RPE target+1..+1.5 hold; > +1.5 or reps below range on >=2 sets -> -5%; twice in a row -> -10% and REG-001 review.",{"hold_band":[1,1.5],"reduce_pct":5,"second_reduce_pct":10},EP,[],"",["progressive_overload"])
rule("PROG-005","progression","Bodyweight progression","Follows BW-001 and BW-002.",{},EP,[],"",["progressive_overload"])
rule("PROG-006","progression","Conditioning progression","Steady +2-5 min/session (<=15%/week) then distance/watts at same CR10; intervals +1 repeat -> longer work -> shorter rest -> +1-2% pace; sleds/carries distance then +5-10% load.",{"steady_minutes":[2,5],"weekly_pct_max":15,"pace_pct":[1,2],"sled_load_pct":[5,10]},EP,[],"",["progressive_overload"])
rule("PROG-007","progression","Progression caps","e1RM-based intensity <=5%/week/lift (beginners 7.5%); +2 sets/muscle/week; aerobic <= +15%/week; HIIT work <= +2 min/week.",{"intensity_pct_week":{"beginner":7.5,"other":5},"sets_per_week":2,"aerobic_pct_week":15,"hiit_work_min_week":2},EP,[],"",["progressive_overload","safety_validator"])
rule("PROG-008","progression","Technique gate","'Form felt solid?' yes/unsure/no after main lifts and new exercises; unsure -> hold; no -> hold + cue; two no -> offer regression.",{"options":["yes","unsure","no"],"regress_after_no":2},EP,[],"",["progressive_overload","guided_workout"])

# ---- Regression / return
rule("REG-001","regression","Failed targets","Three failed exposures on a lift within 3 weeks -> same-pattern variation for the block, or -10% and rebuild; counts toward F1.",{"failures":3,"window_weeks":3,"reduce_pct":10},EP,[],"",["progressive_overload"])
rule("REG-002","regression","1-2 missed sessions","Plan shifts; never double up; at-risk pattern merged into next session within caps.",{},EP,[],"",["weekly_scheduling"])
rule("REG-003","regression","3 missed sessions or 7-13 days off","First session back at MODIFIED with loads -5%.",{"loads_pct":95,"tier":"MODIFIED","days":[7,13]},EP,["Bosquet2013"],"",["weekly_scheduling"])
rule("REG-004","regression","Layoff >= 14 days",
 "14-27 d: wk1 90% load/70% sets, wk2 95%/85%; 28-55 d: wk1 80%/60% then +5% load and +15% sets weekly; >=56 d: recalibrate and restart block. Age >=60 or flagged screen: next longer band.",
 {"bands":[{"days":[14,27],"weeks":[{"load":90,"sets":70},{"load":95,"sets":85}],"hiit_pause_weeks":1},
           {"days":[28,55],"week1":{"load":80,"sets":60},"weekly_step":{"load":5,"sets":15},"hiit_pause_weeks":2},
           {"days":[56,None],"action":"recalibrate_and_restart_block"}],"older_or_flagged_shift":1},
 MOD,["Bosquet2013"],"Direction moderate; exact percentages expert practice.",["return_to_training"])
rule("REG-005","regression","Return after illness","Symptom-free 24-48 h without fever medicine: LIGHT -> MODIFIED -> normal; HIIT after 2 normal sessions; illness >=7 days also applies REG-004; mild head cold without fever: user may choose LIGHT or MODIFIED.",{"symptom_free_hours":[24,48],"hiit_after_normal_sessions":2},EP,[],"",["return_to_training"])

# ---- Calibration, substitution, time, generation, scheduling
rule("CAL-001","calibration","Initial load calibration","Conservative start; after each set RIR 5+ -> +20-30%, 4 -> +10-15%, 3 -> working load, <=2 -> stop and use -5% next time; <=5 ramp sets; never above RPE 8; confirm at second exposure.",
 {"rir_5plus_increase_pct":[20,30],"rir_4_increase_pct":[10,15],"stop_rir_lte":2,"stop_reduction_pct":5,"max_ramp_sets":5,"max_rpe":8,"known_weights_start_pct":90,"sessions":[1,4]},EP,["Halperin2022"],"",["calibration"])
rule("SUB-001","substitution","Substitution hard filters","Equipment available today; no pain/limitation exceeded; not excluded; skill <= level + 1; not MOD-001.",{"skill_margin":1},HIGH,[],"Product/safety rule.",["substitution"])
rule("SUB-002","substitution","Substitution score",
 "Weighted score: pattern 0.30, objective/stimulus 0.20, primary muscle 0.15, equipment 0.10, joint stress 0.10, difficulty 0.07, fatigue parity 0.05, preference 0.03; pattern 1.0 same / 0.5 adjacent / 0 other; <0.5 pattern never auto-picked.",
 {"weights":{"pattern":0.30,"objective":0.20,"muscle":0.15,"equipment":0.10,"joint":0.10,"difficulty":0.07,"fatigue":0.05,"preference":0.03},
  "pattern_scores":{"same":1.0,"adjacent":0.5,"other":0.0},"min_pattern_for_auto":0.5},EP,[],"",["substitution"])
rule("SUB-003","substitution","Preference learning bounds","Accept/reject moves preference +/-0.05 within 0-1; never overrides pattern or safety.",{"step":0.05,"bounds":[0,1]},EP,[],"Product rule.",["substitution"])
rule("TIME-001","time","Priority tiers","P0 warm-up + cool-down (>=2 min) -> P1 primary compound -> P2 key conditioning -> P3 secondary compounds -> P4 accessories -> P5 core/mobility.",{"cooldown_min_minutes":2},EP,["Iversen2021"],"",["time_budget"])
rule("TIME-002","time","Compression order","Rests -> supersets -> P5 -> P4 -> warm-up floor -> P3 -1 set -> P2 <=40% shorter -> P1 to 2 sets; <20 min offers express session.",
 {"steps":["rest_P4_P5_to_min","supersets","P5_reduce_then_drop","P4_reduce_then_drop","warmup_to_floor","P3_minus_one_set","P2_shorten_max_40pct","P1_to_two_sets"],"express_below_minutes":20,"p2_max_cut_pct":40},EP,["Iversen2021"],"",["time_budget"])
rule("TIME-003","time","Extended sessions","Add P4 sets toward targets, Z1 minutes, core/mobility, then skill/power; within caps.",{"order":["P4_sets","z1_minutes","core_mobility","skill_power"]},EP,[],"",["time_budget"])
rule("TIME-004","time","Time model","Set time = setup + reps x tempo (3.5 s) + 10 s; + rests; + 60-90 s per station change; + 5% buffer; personal factor 0.8-1.3.",
 {"tempo_s_per_rep":3.5,"per_set_overhead_s":10,"station_change_s":[60,90],"buffer_pct":5,"personal_factor_bounds":[0.8,1.3]},EP,[],"",["time_budget"])
rule("GEN-001","generation","Workout generation pipeline","Load state -> safety/readiness gates -> slots -> exercise selection -> dose -> tier changes -> warm-up -> time fit -> safety validator -> output + Decision Log. Deterministic; ties broken by exercise ID.",
 {"steps":10,"deterministic":True,"tie_break":"exercise_id"},HIGH,[],"Engineering requirement.",["workout_generation"])
rule("SCH-001","scheduling","Weekly templates","2: FB A/B; 3: FB A/B/C (DUP); 4: Upper/Lower x2; 5: U/L/Cond/U/L; 6: U/L/Cond/U/L/easy aerobic.",
 {"2":["FB_A","FB_B"],"3":["FB_A","FB_B","FB_C"],"4":["UPPER_H","LOWER_H","UPPER_M","LOWER_M"],"5":["UPPER","LOWER","COND_CORE","UPPER","LOWER"],"6":["UPPER","LOWER","COND","UPPER","LOWER","EASY_AEROBIC_MOBILITY"]},EP,["ACSM2026","Currier2023"],"",["weekly_scheduling"])
rule("SCH-002","scheduling","Spacing","Heavy lower >=48 h apart (72 h preferred for advanced heavy squat+deadlift); <=3 consecutive hard days; HIIT per HIIT-004/CON-003.",{"heavy_lower_min_h":48,"advanced_pref_h":72,"max_consecutive_hard_days":3},EP,[],"",["weekly_scheduling"])
rule("SCH-003","scheduling","Rescheduling","Unplanned day runs next session in sequence; reduced days re-plan by priority keeping coverage and caps.",{},EP,[],"Product rule.",["weekly_scheduling"])

# ---- Experience, age, individual
rule("EXP-001","experience","Onboarding classification","Beginner <12 months consistent or not comfortable with >=2 key lifts; intermediate 12-36 months and comfortable; advanced >36 months with structure; calibration may move down, not up, in first 4 weeks.",
 {"beginner_months_lt":12,"intermediate_months":[12,36],"advanced_months_gt":36,"key_lifts":["squat","hinge","press","row"],"lock_up_weeks":4},EP,[],"",["onboarding"])
rule("EXP-002","experience","Level promotion","Beginner->intermediate when >=2 main lifts stall 3 weeks despite FULL readiness and >=80% adherence (or 9 months); intermediate->advanced after >=24 months with <0.5%/week e1RM progress over 12 weeks and >=80% adherence; >=8-week break: one level lower for 4 weeks.",
 {"stall_weeks":3,"adherence_min":0.8,"beginner_max_months":9,"advanced_min_months":24,"progress_pct_week_lt":0.5,"window_weeks":12,"break_weeks":8,"demotion_weeks":4},EP,[],"",["experience"])
rule("AGE-001","individual","Age adjustments",">=50: longer warm-up, HIIT ceiling 2, deload at 2 signals, year-round power work; >=60: longer return band; >=65: balance >=3 days/week and progression caps x0.8.",
 {"age_50":{"hiit_max":2,"deload_signals":2,"power_year_round":True},"age_60":{"return_band_shift":1},"age_65":{"balance_days":3,"progression_cap_multiplier":0.8}},HIGH,["Fragala2019","Bull2020"],"Direction high; numbers expert practice.",["all"])
rule("IND-001","individual","Sex and prior injury","Same algorithms for all; calibration start scales with bodyweight if given; prior injuries become limitation tags with conservative mode for 4 weeks.",{"injury_conservative_weeks":4},MOD,["Roberts2020"],"",["onboarding","substitution"])

# ---- Equipment, adherence, data
rule("EQ-001","equipment","Equipment matrix","No universal superiority; equipment chosen by role (barbell main lifts once technique is reliable, dumbbells for accessories and crowded gyms, machines for planned RIR 0, etc.).",{},MOD,["ACSM2026"],"",["substitution","workout_generation"])
rule("EQ-002","equipment","Crowded-gym constraints","Supersets within one station; never >2 stations at once; Occupied -> swap or reorder; prefer dumbbells at flagged peak hours.",{"max_simultaneous_stations":2},EP,[],"Product rule.",["time_budget","substitution"])
rule("ADH-001","adherence","Forgiving weekly streak","Week counts at >= planned-1 sessions (min 2, or 1 if planning 2); one freeze per 4 weeks; never resets to zero.",{"freeze_per_weeks":4},EP,["Lally2010"],"",["gamification"])
rule("ADH-002","adherence","Micro-achievements","First session, calibration, PRs, block completed, session milestones, WHO-floor weeks; never for extreme volume.",{"session_milestones":[10,25,50,100]},MOD,["Mazeas2022"],"",["gamification"])
rule("ADH-003","adherence","Stable core, rotating edges","Main lifts fixed per block; accessories rotate between blocks; favourites kept.",{},EP,[],"",["workout_generation"])
rule("ADH-004","adherence","Time-efficient option","20-30-min express session always available; habit messaging expects ~2-3 months.",{"express_minutes":[20,30],"habit_median_days":66},MOD,["Lally2010","Iversen2021"],"",["ui","time_budget"])
rule("DATA-001","data","Data taxonomy","11 required, 6 optional items; listed exclusions never collected; all data on-device.",
 {"required":["age","experience","days","session_length","equipment_inventory","screening","priorities","logged_sets","session_rpe_duration","readiness","pain_reports"],
  "optional":["sleep_hours","bodyweight","body_measurements","heart_rate","sex","exercise_preferences"],
  "never":["location","contacts","photos_camera","microphone","advertising_id","social_profiles","third_party_steps_v1","calorie_intake","vanity_metrics"]},HIGH,[],"Product rule.",["onboarding","privacy"])

# --------------------------------------------------------------- assemble
def tests_for(rid):
    return [f"TC-{rid}a", f"TC-{rid}b"]

registry = {
    "registry_version": VERSION,
    "status": "APPROVED — Product Owner Phase 1 sign-off on " + APPROVED_ON,
    "generated": TODAY,
    "confidence_scale": [HIGH, MOD, LIM, EP, PR],
    "versioning": {"MAJOR": "changes training direction, a safety boundary, or removes a rule",
                   "MINOR": "changes a parameter value or adds a rule",
                   "PATCH": "wording, evidence list or test reference only"},
    "sources": {k: {"citation": v[0], "verification": v[1], "url": v[2]} for k, v in SOURCES.items()},
    "rules": [],
}
for r in R:
    for ev in r["evidence"]:
        assert ev in SOURCES, (r["rule_id"], ev)
    registry["rules"].append({
        "rule_id": r["rule_id"], "version": VERSION, "category": r["category"],
        "title": r["title"], "statement": r["statement"], "parameters": r["parameters"],
        "confidence": r["confidence"], "evidence": r["evidence"], "uncertainty": r["uncertainty"],
        "date_introduced": TODAY, "date_reviewed": TODAY,
        "affected_algorithms": r["affected_algorithms"], "tests": tests_for(r["rule_id"]),
        "change_reason": "Initial version; approved at Phase 1 sign-off", "status": "approved",
    })

# ---- PATCH 1.0.1 (2026-10-07): ACSM 2026 full text checked; citation corrected; rest-interval nuance added.
PATCH_101 = {"note": "PATCH: ACSM 2026 full text checked (first author, volume, pages, DOI corrected); no parameter changes"}
for r in registry["rules"]:
    if "ACSM2026" in r["evidence"]:
        r["version"] = "1.0.1"; r["date_reviewed"] = "2026-10-07"; r["change_reason"] = PATCH_101["note"]
for r in registry["rules"]:
    if r["rule_id"] in ("REST-002", "REST-003", "REST-007"):
        if "ACSM2026" not in r["evidence"]:
            r["evidence"].append("ACSM2026")
        r["version"] = "1.0.1"; r["date_reviewed"] = "2026-10-07"
        r["uncertainty"] = (r["uncertainty"] + " " if r["uncertainty"] else "") + "ACSM 2026: inter-set rest did not consistently change outcomes, so defaults are practical and may be compressed under time pressure."
        r["change_reason"] = "PATCH: evidence added (ACSM 2026 on rest intervals); no parameter changes"
registry["registry_version"] = "1.0.1"
PRODUCT_RULES = ['SAF-006', 'SAF-008', 'SAF-009', 'COACH-001', 'MOD-001', 'GEN-001', 'SUB-001', 'SUB-003', 'PROG-003', 'RDY-007', 'SCH-003', 'EQ-002', 'DATA-001', 'PER-006', 'TIME-004']
for r in registry["rules"]:
    if r["rule_id"] in PRODUCT_RULES:
        r["confidence"] = PR
ids = [r["rule_id"] for r in registry["rules"]]
assert len(ids) == len(set(ids)), "duplicate rule IDs"
ROOTDIR = Path(__file__).resolve().parent.parent
# Snapshot of the registry as approved at Phase 1 (1.0.1); tools/check_phase1.py checks the Phase 1 report against it.
import copy
(ROOTDIR / "rules" / "archive").mkdir(parents=True, exist_ok=True)
(ROOTDIR / "rules" / "archive" / "rule_registry_1.0.1.json").write_text(json.dumps(registry, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

# ---- CHANGE SET 1.1.0 (2026-10-09): Research Update 1.1, approved by the Product Owner (rules/changes/registry_1.1.0.json).
CHANGE = json.loads((ROOTDIR / "rules" / "changes" / "registry_1.1.0.json").read_text(encoding="utf-8"))
APPROVED_11 = CHANGE["approved_on"]
NOTE_11 = "Research Update 1.1, approved by the Product Owner on " + APPROVED_11
BY_ID = {r["rule_id"]: r for r in registry["rules"]}

def bump(r, to=None, kind="PATCH"):
    if to is None:
        a, b, c = (int(x) for x in r["version"].split("."))
        to = f"{a}.{b}.{c + 1}" if kind == "PATCH" else f"{a}.{b + 1}.0"
    r["version"] = to
    r["date_reviewed"] = APPROVED_11

def deep_merge(dst, add):
    for k, v in add.items():
        if isinstance(v, dict) and isinstance(dst.get(k), dict):
            deep_merge(dst[k], v)
        else:
            dst[k] = v

import re as _re
for k, v in CHANGE["new_sources"].items():
    assert k not in registry["sources"], k
    m = _re.search(r"doi:\s*(10\.[^\s,;]+)", v["citation"])
    registry["sources"][k] = {"citation": v["citation"], "verification": v["verification"],
                              "url": ("https://doi.org/" + m.group(1).rstrip(".")) if m else ""}

def pending(evidence):
    return [k for k in evidence if not registry["sources"][k]["verification"].startswith("checked")]

for nr in CHANGE["new_rules"]:
    r = copy.deepcopy(nr)
    assert r["rule_id"] not in BY_ID, r["rule_id"]
    for ev in r["evidence"]:
        assert ev in registry["sources"], (r["rule_id"], ev)
    r["status"] = "approved"
    r["change_reason"] = r["change_reason"] + " — " + NOTE_11
    if pending(r["evidence"]):
        r["verification_pending"] = pending(r["evidence"])
    registry["rules"].append(r)
    BY_ID[r["rule_id"]] = r

for c in CHANGE["changed_rules"]:
    r = BY_ID[c["rule_id"]]
    assert r["version"] == c["from_version"], (c["rule_id"], r["version"], c["from_version"])
    if "statement" in c:
        r["statement"] = c["statement"]
    if "statement_add" in c:
        r["statement"] = r["statement"].rstrip() + " " + c["statement_add"]
    if "parameters_replace" in c:
        r["parameters"] = c["parameters_replace"]
    if "parameters_add" in c:
        deep_merge(r["parameters"], c["parameters_add"])
    for ev in c.get("evidence_add", []):
        assert ev in registry["sources"], (c["rule_id"], ev)
        if ev not in r["evidence"]:
            r["evidence"].append(ev)
    if c.get("confidence_note"):
        r["uncertainty"] = (r["uncertainty"] + " " if r["uncertainty"] else "") + c["confidence_note"]
    bump(r, to=c["to_version"])
    r["change_reason"] = f"{c['change']}: {c['reason']} ({NOTE_11})"
    pend = [k for k in c.get("evidence_add", []) if not registry["sources"][k]["verification"].startswith("checked")]
    if pend:
        r["verification_pending"] = pend

# Evidence and source patches (no wording or parameter changes).
def add_evidence(rid, keys, reason):
    r = BY_ID[rid]
    for k in keys:
        if k not in r["evidence"]:
            r["evidence"].append(k)
    if r["date_reviewed"] != APPROVED_11:
        bump(r)
    r["change_reason"] = f"PATCH: evidence added ({reason}; {NOTE_11})" if r["change_reason"].startswith("Initial") or r["change_reason"].startswith("PATCH") else r["change_reason"]

for r in registry["rules"]:
    if "Pelland2024" in r["evidence"]:
        r["evidence"] = ["Pelland2026" if k == "Pelland2024" else k for k in r["evidence"]]
        if r["date_reviewed"] != APPROVED_11:
            bump(r)
        r["change_reason"] = f"PATCH: peer-reviewed Pelland 2026 replaces the 2024 preprint ({NOTE_11})"
        if r["rule_id"] == "VOL-002":
            r["uncertainty"] = "Peer-reviewed meta-regression (Pelland 2026): fractional set counting fitted best. Participants' mean age was about 25, so transfer to adults 30+ is assumed."
add_evidence("INT-003", ["Hermann2025"], "single set to failure vs 2 RIR")
add_evidence("LOAD-002", ["Harkin2016", "Michie2009"], "self-monitoring")
add_evidence("ADH-001", ["Gardner2012", "Singh2024"], "a missed day does not derail habit formation")
registry["sources"]["Schoenfeld2017vol"]["verification"] = "checked"
registry["sources"]["Lally2010"]["url"] = ""
registry["sources"]["Lally2010"]["note"] = "The earlier URL opened a different paper (Gardner, Rebar & Lally 2022). Publisher DOI 10.1002/ejsp.674 not yet confirmed."
registry["registry_version"] = CHANGE["registry_version"]
registry["status"] = "APPROVED — Product Owner Phase 1 sign-off on " + APPROVED_ON + "; Research Update 1.1 sign-off on " + APPROVED_11
registry["generated"] = APPROVED_11
registry["verification_pending"] = CHANGE["verification_pending"]
ids = [r["rule_id"] for r in registry["rules"]]
assert len(ids) == len(set(ids)), "duplicate rule IDs"
assert len(ids) == CHANGE["rule_count_after"], len(ids)
for r in registry["rules"]:
    for ev in r["evidence"]:
        assert ev in registry["sources"], (r["rule_id"], ev)
out = ROOTDIR / "rules" / "rule_registry_v1.0.json"  # 1.x line; registry_version field carries the minor/patch level
out.write_text(json.dumps(registry, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print(f"wrote {out} — registry {registry['registry_version']}, {len(ids)} rules, {len(registry['sources'])} sources")
from collections import Counter
print(Counter(r["confidence"] for r in registry["rules"]))
print(Counter(v["verification"].split(" ")[0] for v in registry["sources"].values()))
