"""Phase 1 checks: registry integrity, doc<->registry parameter agreement, worked-example arithmetic.
Run: python3 -I tools/check_phase1.py"""
import json, math, re, statistics
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
reg = json.loads((ROOT/"rules/rule_registry_v1.0.json").read_text())
md = (ROOT/"docs/phase1/phase1_report.md").read_text()
R = {r["rule_id"]: r for r in reg["rules"]}
P = lambda rid: R[rid]["parameters"]
fails = []
def check(name, cond):
    print(("PASS " if cond else "FAIL ") + name)
    if not cond: fails.append(name)

# --- registry integrity
check("140 unique rule ids", len(R) == 140 == len(reg["rules"]))
check("all evidence keys resolve", all(e in reg["sources"] for r in reg["rules"] for e in r["evidence"]))
check("every rule has >=2 tests", all(len(r["tests"]) >= 2 for r in reg["rules"]))
check("all rules approved 1.0.x", all(r["version"] in ("1.0.0","1.0.1") and r["status"] == "approved" for r in reg["rules"]))
check("registry version 1.0.1", reg["registry_version"] == "1.0.1")
check("SUB-002 weights sum to 1", abs(sum(P("SUB-002")["weights"].values()) - 1) < 1e-9)
check("MOD-002 weights sum to 1", abs(sum(P("MOD-002")["weights"].values()) - 1) < 1e-9)
check("RDY-001 weights sum to 1", abs(sum(P("RDY-001")["weights"].values()) - 1) < 1e-9)

# --- doc <-> registry agreement (numbers the doc states)
cap = P("VOL-003")["cap"]; start = P("VOL-003")["block_start"]
check("volume caps 12/16/20 in doc", f"{cap['beginner']} / {cap['intermediate']} / {cap['advanced']}" in md)
check("block start 6/8/10 in doc", "| Block start (≈MEV) | 6 | 8 | 10 |" in md)
check("session caps 20/25/30", "≤20 / 25 / 30 working sets" in md and P("VOL-008")["cap"] == {"beginner":20,"intermediate":25,"advanced":30})
check("per-session muscle caps 6/8/10", "≤6 / 8 / 10 direct hard sets" in md)
t = P("RDY-003"); check("readiness tiers 40/28/15", t["FULL_min"]==40 and t["MODIFIED_min"]==28 and t["LIGHT_min"]==15 and "R ≥ 40 FULL · 28–39 MODIFIED · 15–27 LIGHT · < 15 RECOVERY" in md)
check("readiness weights in formula", "0.30(S-1) + 0.30(E-1) + 0.25(D-1) + 0.15(M-1)" in md)
check("baseline blend 0.6/0.4, 15, clamp 2, sd 8", "R = 0.6\\,R_{\\text{raw}} + 0.4\\,R_{\\text{pers}}" in md and "50 + 15\\,\\mathrm{clamp}(z,-2,2)" in md and "\\max(\\sigma_{28},\\,8)" in md)
for rid, txt in [("REST-001","120–300 s | 180 s"),("REST-002","90–150 s | 120 s"),("REST-003","60–90 s | 75 s"),("REST-004","45–75 s between the paired exercises | 60 s"),("REST-005","15–45 s transitions | 30 s"),("REST-006","30–60 s | 45 s")]:
    p = P(rid); check(f"{rid} {p['min_s']}-{p['max_s']} default {p['default_s']}", txt in md)
check("HIIT ceiling 2 / 3 conditional", P("HIIT-001")["default_max"] == 2 and "≤2 HIIT sessions/week" in md)
check("LOAD-005 1.20 and +20%", P("LOAD-005")["multiplier"] == 1.2 and "≤ 1.20 × mean of the last 3 completed weeks" in md)
check("EWMA lambdas", abs(P("LOAD-004")["acute_lambda"] - 2/8) < 1e-9 and abs(P("LOAD-004")["chronic_lambda"] - 2/29) < 1e-6)
check("pain thresholds 3 / 4 / 7", P("SAF-003")["continue_max_rating"] == 3 and P("SAF-003")["stop_exercise_min_rating"] == 4 and P("SAF-003")["stop_region_min_rating"] == 7)
check("SSU ceilings 100/140/180", P("VOL-007")["weekly_ceiling"] == {"beginner":100,"intermediate":140,"advanced":180} and "weekly SSU ≤ 100 / 140 / 180" in md)
check("deload -50% sets, 85-90%", P("DEL-003")["sets_reduction_pct"] == 50 and "sets −50%; loads 85–90%" in md)

# --- worked examples
C, E = P("VOL-007")["C"], P("VOL-007")["E"]
ssu = 6*C["free_weight_compound_carry_sled"]*E["rir1_2"] + 2*C["heavy_bilateral_squat_hinge_ge80pct"]*E["rir1_2"] \
    + 4*C["machine_or_cable_compound"]*E["rir1_2"] + 6*C["isolation_or_core"]*E["rir1_2"] \
    + 3*C["isolation_or_core"]*E["rir3_4"] + 12*P("VOL-007")["Z_per_min"]["Z2"]*P("VOL-007")["J"]["rower_skierg_elliptical"]
check(f"SSU example = 25.0 ({ssu:.2f})", round(ssu,1) == 25.0)
e1 = 60*(1+(8+2)/30); load = e1/(1+(5+2)/30)
check(f"bench e1RM 80.0 ({e1:.2f}) and target 64.9 ({load:.2f})", round(e1,1)==80.0 and round(load,1)==64.9)
check("65 kg within 2% of target", 65 <= load*1.02 and 65/load-1 < 0.005)
check("squat +2.5 kg = 2.5% step", abs(2.5/100 - 0.025) < 1e-12)
check("DB jump 13 reps", math.floor(15*30/32.5) == 13 and 32.5/30-1 > 0.05)
# calibration: 30 * 1.125 = 33.75; 35 > 2% above, so 32.5
check("calibration picks 32.5", 30*1.125 == 33.75 and 35 > 33.75*1.02 and 32.5 <= 33.75)
cal_e1 = 32.5*(1+(8+3)/30); check(f"calibration e1RM 44.4 ({cal_e1:.2f})", round(cal_e1,1)==44.4)
# readiness example
w = P("RDY-001")["weights"]
rraw = 25*(w["sleep_quality"]*(2-1)+w["energy"]*(3-1)+w["soreness"]*(2-1)+w["stress"]*(3-1))
z = max(-2,min(2,(rraw-58)/max(9,8))); R_ = 0.6*rraw + 0.4*(50+15*z)
check(f"readiness example R_raw 36.25 ({rraw}) -> R 29.75 ({R_:.2f}) MODIFIED", abs(rraw-36.25)<1e-9 and abs(R_-29.75)<1e-9 and 28 <= R_ < 40)
check("all-3s = 50 -> FULL", abs(25*sum(w.values())*2 - 50) < 1e-9)
# ACWR example
week=[400,0,400,0,400,0,400]; spike=[650,0,650,0,650,500,650]
a=c=None; la, lc = 0.25, 2/29
for x in week*5+spike:
    if a is None: a=c=float(x)
    a=la*x+(1-la)*a; c=lc*x+(1-lc)*c
check(f"EWMA ratio day 42 = 1.41 ({a/c:.3f}); acute 460 ({a:.0f}) chronic 326 ({c:.0f})", round(a/c,2)==1.41 and round(a)==460 and round(c)==326)
check("weekly loads 1600 and 3100, cap 1920", sum(week)==1600 and sum(spike)==3100 and 1600*1.2==1920)
# 30-minute table sums
check("60-min plan sums to 60", 10+14+8+5+8+4+3+6+2 == 60)
check("30-min version sums to 30", 5+9+6+4+4+2 == 30)
check("P2 cut 6->4 rounds within 40%", (6-4)/6 <= 0.40)
# substitution scores
W = P("SUB-002")["weights"]
def s(d): return sum(W[k]*d[k] for k in W)
mach = s(dict(pattern=1,objective=1,muscle=1,equipment=.7,joint=1,difficulty=1,fatigue=1,preference=.5))
pull = s(dict(pattern=.5,objective=1,muscle=.7,equipment=.7,joint=1,difficulty=1,fatigue=1,preference=.5))
check(f"substitution scores 0.955 / 0.760 ({mach:.3f}/{pull:.3f})", round(mach,3)==0.955 and round(pull,3)==0.760)
# warm-up ramp for 100 kg
check("ramp 50/70/85 of 100 kg", [round(100*p) for p in (0.5,0.7,0.85)] == [50,70,85])
# registry summary counts in doc
from collections import Counter
cnt = Counter(r["confidence"] for r in reg["rules"])
check(f"doc totals 8/36/6/75/15 match registry {dict(cnt)}", "| **Total** | **140** | **8** | **36** | **6** | **75** | **15** |" in md and cnt["High Evidence"]==8 and cnt["Moderate Evidence"]==36 and cnt["Limited Evidence"]==6 and cnt["Expert Practice"]==75)
ver = Counter(v["verification"] for v in reg["sources"].values())
check(f"source counts 64 = 11 checked + 52 located + 1 unverified ({dict(ver)})", len(reg["sources"])==64 and ver["checked"]==11 and ver["located"]==52 and ver["unverified"]==1)
print(f"\n{len(fails)} failure(s)")
