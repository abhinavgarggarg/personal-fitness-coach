"""Validate the exercise library (library/v1/*.json) and generate Kotlin from it (decision D-046).

Writes two files under engine/src/main/kotlin/com/personalfitnesscoach/engine/library/:
  GeneratedLibrary.kt      exercises, drills, equipment and coverage gaps the engine reasons with
  GeneratedLibraryText.kt  setup instructions, cues, mistakes and safety notes (original text, D7)

Each JSON file becomes its own Kotlin function so no method grows past the JVM 64 KB limit.
Validation fails (exit 1) on: unknown keys or enum values, duplicate IDs, unknown equipment
or tags, broken ladder links, a class that its equipment cannot provide, missing text, a
failure-safe free-weight barbell lift, a missing safety tag (SAF-010), a bad "any of"
equipment group (D-063), and any movement pattern with no exercise for a class unless the
gap is justified in library.json (and no stale justifications).

    python3 -I tools/gen_library_kotlin.py           # validate + write
    python3 -I tools/gen_library_kotlin.py --check   # CI: fail if invalid or stale
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LIB = ROOT / "library" / "v1"
OUT_DIR = ROOT / "engine/src/main/kotlin/com/personalfitnesscoach/engine/library"
EXERCISE_FILES = ["squat", "hinge", "lunge", "push", "pull", "core", "isolation"]

PATTERNS = ["SQUAT", "HINGE", "LUNGE", "HORIZONTAL_PUSH", "HORIZONTAL_PULL", "VERTICAL_PUSH", "VERTICAL_PULL",
            "LOADED_CARRY", "ROTATION", "ANTI_EXTENSION", "ANTI_ROTATION", "ANTI_LATERAL_FLEXION", "ISOLATION"]
LIFTING = PATTERNS[:7]
CORE_PATTERNS = PATTERNS[9:12]
MUSCLES = ["CHEST", "LATS", "UPPER_BACK", "FRONT_DELTS", "SIDE_DELTS", "REAR_DELTS", "BICEPS", "TRICEPS",
           "QUADS", "HAMSTRINGS", "GLUTES", "CALVES", "ADDUCTORS", "CORE", "FOREARMS"]
JOINTS = ["SHOULDER", "ELBOW", "WRIST", "SPINE", "HIP", "KNEE", "ANKLE"]
COSTS = ["ISOLATION_OR_CORE", "MACHINE_OR_CABLE_COMPOUND", "FREE_WEIGHT_COMPOUND", "HEAVY_BILATERAL"]
LOADS = ["BARBELL", "DUMBBELL", "STACK", "KETTLEBELL", "BODYWEIGHT", "TIME", "DISTANCE"]
OBJECTIVES = ["STRENGTH", "HYPERTROPHY", "ENDURANCE", "POWER", "CONDITIONING", "CORE", "MOBILITY", "GPP"]
UNITS = ["REPS", "SECONDS", "METRES"]
COVERAGE_CLASSES = ["BARBELL", "DUMBBELL", "KETTLEBELL", "CABLE", "MACHINE", "BODYWEIGHT"]
DRILL_KINDS = ["ACTIVATE", "MOBILISE", "BALANCE", "STRETCH", "BREATHING"]
REGIONS = ["HIPS", "ANKLES", "KNEES", "SPINE", "UPPER_BACK", "SHOULDERS", "WRISTS", "CHEST", "LATS", "QUADS",
           "HAMSTRINGS", "GLUTES", "CALVES", "ADDUCTORS", "HIP_FLEXORS", "WHOLE_BODY"]
MODALITIES = ["ROWER", "SKIERG", "ELLIPTICAL", "SLED", "BATTLE_ROPES", "KETTLEBELL", "CARRIES", "MEDBALL",
              "BODYWEIGHT_CIRCUIT", "JUMP_ROPE",
              # MOD-001 2.0.0: per-person choices (decision 2, Research Update 1.1)
              "TREADMILL_WALK", "STATIONARY_BIKE", "AIR_BIKE", "STAIR_MACHINE", "TREADMILL_RUN"]
MOD_UNITS = ["MINUTES", "METRES", "WATTS", "CALORIES", "REPS"]
# Equipment that fixes you to one spot in the gym (EQ-002 station kind).
FIXED_EXTRA = {"rack", "bench", "incline_bench", "pullup_bar", "dip_station", "landmine", "plyo_box"}
BAR_FOR = {"trap_bar": "trap_bar", "ez_bar": "ez_bar", "landmine": "landmine", "barbell": "barbell"}

EX_KEYS = {"id", "name", "aliases", "pattern", "pattern2", "class", "primary", "secondary", "equipment", "load", "cost",
           "difficulty", "skill", "joints", "fatigue", "impact", "objectives", "reps", "max_reps", "failure_safe", "e1rm",
           "unilateral", "tempo", "setup", "station", "tags", "family", "rung", "progression", "regression", "unit",
           "power", "assisted", "bar", "one_sided", "user_add_only", "shallow", "not_overhead", "text",
           # Library 1.1.0: "any of" equipment groups (D-063) and power drills that fill power slots only (D-057).
           "equipment_any", "power_only"}
DRILL_KEYS = {"id", "name", "kind", "regions", "prepares", "unit", "amount", "per_side", "equipment", "joints", "tags", "text"}
MOVE_KEYS = {"id", "name", "jumping", "joints", "tags", "text"}
REQUIRED = ["id", "name", "pattern", "class", "primary", "equipment", "load", "cost", "text"]
TEXT_KEYS = {"setup", "cues", "mistakes", "safety"}

errors = []


def err(msg):
    errors.append(msg)


def kstr(s):
    return json.dumps(s, ensure_ascii=False).replace("$", "\\$")


def kset(enum, values):
    return "emptySet()" if not values else "setOf(" + ", ".join(f"{enum}.{v}" for v in values) + ")"


def kstrset(values):
    return "emptySet()" if not values else "setOf(" + ", ".join(kstr(v) for v in values) + ")"


def kstrlist(values):
    return "emptyList()" if not values else "listOf(" + ", ".join(kstr(v) for v in values) + ")"


def load_json(name):
    path = LIB / f"{name}.json"
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as e:  # noqa: BLE001 - report any parse problem as a validation error
        err(f"{path.name}: cannot parse ({e})")
        return None


def default_objectives(ex):
    p = ex["pattern"]
    if p in CORE_PATTERNS or p == "ROTATION":
        objs = ["CORE"]
    elif p == "LOADED_CARRY":
        objs = ["GPP", "CORE"]
    elif p == "ISOLATION":
        objs = ["HYPERTROPHY"]
    elif ex.get("load") in ("BODYWEIGHT", "TIME") and ex.get("cost") == "ISOLATION_OR_CORE":
        objs = ["HYPERTROPHY", "ENDURANCE"]
    else:
        objs = ["STRENGTH", "HYPERTROPHY"]
    if ex.get("power") and "POWER" not in objs:
        objs = ["POWER"] + objs
    return objs


def validate_text(owner, text, need_mistakes=True):
    if not isinstance(text, dict):
        err(f"{owner}: text missing")
        return
    extra = set(text) - TEXT_KEYS
    if extra:
        err(f"{owner}: unknown text keys {sorted(extra)}")
    if not str(text.get("setup", "")).strip():
        err(f"{owner}: text.setup is empty")
    if not text.get("cues") or not all(str(c).strip() for c in text["cues"]):
        err(f"{owner}: text.cues must list at least one cue")
    if need_mistakes and (not text.get("mistakes") or not all(str(c).strip() for c in text["mistakes"])):
        err(f"{owner}: text.mistakes must list at least one mistake")


def main():
    meta = load_json("library")
    if meta is None:
        return finish()
    defaults = meta["defaults"]
    equipment = meta["equipment"]
    tags = meta["tags"]
    classes = meta["classes"]
    gaps = meta.get("coverage_gaps", {})

    exercises, sources = [], {}
    for f in EXERCISE_FILES:
        data = load_json(f) or []
        for ex in data:
            ex["_file"] = f
            exercises.append(ex)
    ids = {}
    for ex in exercises:
        owner = f"{ex.get('_file')}:{ex.get('id', '?')}"
        for k in REQUIRED:
            if k not in ex:
                err(f"{owner}: missing '{k}'")
        unknown = set(ex) - EX_KEYS - {"_file"}
        if unknown:
            err(f"{owner}: unknown keys {sorted(unknown)}")
        eid = ex.get("id", "")
        if not re.fullmatch(r"[a-z0-9]+(-[a-z0-9]+)*", eid):
            err(f"{owner}: id must be lower-case kebab-case")
        if eid in ids:
            err(f"{owner}: duplicate id (also in {ids[eid]})")
        ids[eid] = ex["_file"]
    if errors:
        return finish()

    for ex in exercises:
        owner = f"{ex['_file']}:{ex['id']}"
        for k, v in defaults.items():
            ex.setdefault(k, v)
        ex.setdefault("aliases", [])
        ex.setdefault("secondary", [])
        ex.setdefault("joints", {})
        ex.setdefault("tags", [])
        if ex["pattern"] not in PATTERNS:
            err(f"{owner}: unknown pattern {ex['pattern']}")
        if ex.get("pattern2") is not None and (ex["pattern2"] not in PATTERNS or ex["pattern2"] == ex["pattern"]):
            err(f"{owner}: bad pattern2 {ex['pattern2']}")
        if ex["class"] not in classes:
            err(f"{owner}: unknown class {ex['class']}")
        for m in ex["primary"] + ex["secondary"]:
            if m not in MUSCLES:
                err(f"{owner}: unknown muscle {m}")
        if not ex["primary"]:
            err(f"{owner}: needs at least one primary muscle")
        if set(ex["primary"]) & set(ex["secondary"]):
            err(f"{owner}: a muscle is both primary and secondary")
        for j, v in ex["joints"].items():
            if j not in JOINTS or not isinstance(v, int) or not 0 <= v <= 4:
                err(f"{owner}: bad joint stress {j}={v}")
        for e in ex["equipment"]:
            if e not in equipment:
                err(f"{owner}: unknown equipment {e}")
        ex.setdefault("equipment_any", [])
        if not isinstance(ex["equipment_any"], list):
            err(f"{owner}: equipment_any must be a list of groups")
            ex["equipment_any"] = []
        for g in ex["equipment_any"]:
            if not isinstance(g, list) or len(g) < 2 or len(set(g)) != len(g):
                err(f"{owner}: each equipment_any group lists 2+ different items")
                continue
            for e in g:
                if e not in equipment:
                    err(f"{owner}: unknown equipment {e} in equipment_any")
                if e in ex["equipment"]:
                    err(f"{owner}: {e} is both required and in an equipment_any group")
        for t in ex["tags"]:
            if t not in tags:
                err(f"{owner}: unknown tag {t}")
        if ex["load"] not in LOADS:
            err(f"{owner}: unknown load type {ex['load']}")
        if ex["cost"] not in COSTS:
            err(f"{owner}: unknown cost class {ex['cost']}")
        if ex["unit"] not in UNITS:
            err(f"{owner}: unknown unit {ex['unit']}")
        for k in ("difficulty", "skill", "fatigue"):
            if not 1 <= ex[k] <= 5:
                err(f"{owner}: {k} must be 1-5")
        if not 0 <= ex["impact"] <= 3:
            err(f"{owner}: impact must be 0-3")
        objs = ex.get("objectives") or default_objectives(ex)
        ex["objectives"] = objs
        for o in objs:
            if o not in OBJECTIVES:
                err(f"{owner}: unknown objective {o}")
        lo, hi = ex["reps"]
        if not 1 <= lo <= hi:
            err(f"{owner}: bad rep range {ex['reps']}")
        ex.setdefault("max_reps", hi)
        if ex["max_reps"] < hi:
            err(f"{owner}: max_reps below the top of the range")
        if ex.get("e1rm") and ex["load"] not in ("BARBELL", "DUMBBELL", "STACK", "KETTLEBELL"):
            err(f"{owner}: e1rm tracking needs an external load")
        if ex.get("e1rm") and ex.get("assisted"):
            err(f"{owner}: assisted exercises cannot track e1RM")
        if ex.get("failure_safe") and ex["load"] == "BARBELL":
            err(f"{owner}: free-weight barbell lifts are never failure-safe (INT-003)")
        if ex.get("failure_safe") and ex["pattern"] == "LOADED_CARRY":
            err(f"{owner}: carries are never failure-safe (INT-003)")
        if ex["load"] == "BARBELL":
            bar = ex.get("bar") or next((BAR_FOR[e] for e in ("landmine", "trap_bar", "ez_bar", "barbell") if e in ex["equipment"]), None)
            if ex.get("one_sided"):
                bar = "landmine"
            if bar is None:
                err(f"{owner}: barbell load needs barbell, ez_bar, trap_bar or landmine equipment")
            ex["bar"] = bar
        elif ex.get("bar"):
            err(f"{owner}: 'bar' only applies to barbell loads")
        if ex["unit"] != "REPS" and ex.get("e1rm"):
            err(f"{owner}: e1rm needs a rep-based unit")
        # Class must be something the listed equipment can provide.
        cls = ex["class"]
        eq_classes = {equipment[e]["class"] for e in ex["equipment"] + [x for g in ex["equipment_any"] for x in g] if e in equipment}
        if cls in ("BARBELL", "DUMBBELL", "KETTLEBELL", "CABLE", "MACHINE", "BAND") and cls not in eq_classes:
            err(f"{owner}: class {cls} but no {cls} equipment listed")
        if cls == "BODYWEIGHT" and eq_classes & {"DUMBBELL", "KETTLEBELL", "CABLE", "MACHINE"} and ex["load"] != "BODYWEIGHT":
            err(f"{owner}: bodyweight class with an external load")
        def is_fixed(e):
            return e in FIXED_EXTRA or (e in equipment and equipment[e]["class"] in ("CABLE", "MACHINE"))
        # A group whose options all fix you to one spot (box or bench) counts as a fixed station, named by its first option.
        fixed = [e for e in ex["equipment"] if is_fixed(e)] + [g[0] for g in ex["equipment_any"] if all(is_fixed(e) for e in g)]
        st = ex.get("station")
        if st is None:
            st = fixed[0] if fixed else (ex["equipment"][0] if ex["equipment"] else "floor")
        if st != "floor" and st not in equipment:
            err(f"{owner}: station '{st}' is not floor or an equipment id")
        if fixed and st not in fixed:
            err(f"{owner}: station '{st}' should be one of its fixed items {fixed}")
        ex["station"] = st
        ex["_station_kind"] = "FLOOR" if not fixed else "SINGLE_STATION"
        for k in ("progression", "regression"):
            if ex.get(k) is not None and ex[k] not in ids:
                err(f"{owner}: {k} '{ex[k]}' does not exist")
            if ex.get(k) == ex["id"]:
                err(f"{owner}: {k} points to itself")
        if (ex.get("family") is None) != (ex.get("rung") is None):
            err(f"{owner}: family and rung go together")
        # Safety tags that must not go missing (review finding 5): blocked tags and joint limits rely on them.
        if ex["pattern"] in ("SQUAT", "LUNGE") and "deep_knee_flexion" not in ex["tags"] and not ex.get("shallow"):
            err(f"{owner}: squat/lunge pattern needs the deep_knee_flexion tag (or \"shallow\": true)")
        if ex["pattern"] in ("VERTICAL_PUSH", "VERTICAL_PULL") and "overhead" not in ex["tags"] and not ex.get("not_overhead"):
            err(f"{owner}: vertical push/pull needs the overhead tag (or \"not_overhead\": true)")
        if ex["load"] == "BARBELL" and ex.get("bar") == "barbell" and ex["pattern"] in ("SQUAT", "LUNGE", "VERTICAL_PUSH") \
                and "spinal_loading" not in ex["tags"] and not ex.get("not_overhead"):
            err(f"{owner}: standing barbell squat/lunge/press needs the spinal_loading tag")
        if ex["pattern"] == "LOADED_CARRY" and any(ex["joints"].get(j, 0) < 1 for j in ("KNEE", "HIP", "ANKLE")):
            err(f"{owner}: carries load the knee, hip and ankle (joint stress ≥ 1 each)")
        if ex.get("failure_safe") and "overhead" in ex["tags"] and ex["load"] in ("DUMBBELL", "KETTLEBELL", "BARBELL"):
            err(f"{owner}: a free weight held overhead is never failure-safe (INT-003)")
        # Library 1.1.0 (SAF-010): condition profiles avoid these tags, so they must not go missing either.
        if ex["cost"] == "HEAVY_BILATERAL" and "breath_hold_max" not in ex["tags"]:
            err(f"{owner}: heavy bilateral lifts need the breath_hold_max tag")
        if ex["pattern"] == "ROTATION" and "loaded_spinal_rotation" not in ex["tags"]:
            err(f"{owner}: rotation-pattern exercises need the loaded_spinal_rotation tag")
        if ex["pattern"] == "VERTICAL_PUSH" and "overhead" in ex["tags"] and ex["load"] in ("BARBELL", "DUMBBELL", "KETTLEBELL", "STACK") \
                and "overhead_heavy" not in ex["tags"]:
            err(f"{owner}: a loaded overhead press needs the overhead_heavy tag")
        if "unsupported_single_leg" in ex["tags"] and "high_fall_risk" not in ex["tags"]:
            err(f"{owner}: unsupported single-leg work is also high_fall_risk")
        if ex.get("power") and ex["load"] == "BODYWEIGHT":
            ex["power_only"] = True  # jumps and throws never fill lifting slots (D-057)
        if ex.get("power_only") and not ex.get("power"):
            err(f"{owner}: power_only needs power")
        validate_text(owner, ex["text"])

    by_id = {ex["id"]: ex for ex in exercises}
    # Ladder sanity: a family's progression stays in the family and climbs; regressions descend.
    for ex in exercises:
        if ex.get("family"):
            for k, sign in (("progression", 1), ("regression", -1)):
                other = by_id.get(ex.get(k)) if ex.get(k) else None
                if other and other.get("family") == ex["family"] and (other["rung"] - ex["rung"]) * sign <= 0:
                    err(f"{ex['id']}: {k} {other['id']} does not move {'up' if sign > 0 else 'down'} the {ex['family']} ladder")
    families = {}
    for ex in exercises:
        if ex.get("family"):
            families.setdefault(ex["family"], []).append(ex["rung"])
    for fam, rungs in families.items():
        if sorted(set(rungs)) != list(range(1, max(rungs) + 1)):
            err(f"family {fam}: rungs {sorted(rungs)} must run 1..n without holes")
    # A progression edge never loops back to the exercise.
    for ex in exercises:
        seen, cur = set(), ex["id"]
        while cur is not None and cur not in seen:
            seen.add(cur)
            cur = by_id[cur].get("progression")
        if cur is not None:
            err(f"{ex['id']}: progression chain loops at {cur}")

    # Coverage: every non-isolation pattern has an exercise per class unless the gap is justified.
    have = {}
    for ex in exercises:
        for p in (ex["pattern"], ex.get("pattern2")):
            if p:
                have.setdefault(p, set()).add(ex["class"])
    for p in PATTERNS[:12]:
        for c in COVERAGE_CLASSES:
            justified = c in gaps.get(p, {})
            if c in have.get(p, set()) and justified:
                err(f"coverage gap {p}/{c} is documented but the library covers it - remove the justification")
            if c not in have.get(p, set()) and not justified:
                err(f"coverage: no {c} exercise for {p} and no justification in library.json")
    for p, m in gaps.items():
        if p not in PATTERNS:
            err(f"coverage_gaps: unknown pattern {p}")
        for c, why in m.items():
            if c not in COVERAGE_CLASSES or not why.strip():
                err(f"coverage_gaps: bad entry {p}/{c}")

    drills = load_json("drills") or []
    drill_ids = set()
    for d in drills:
        owner = f"drills:{d.get('id', '?')}"
        if set(d) - DRILL_KEYS:
            err(f"{owner}: unknown keys {sorted(set(d) - DRILL_KEYS)}")
        if d.get("id") in drill_ids or d.get("id") in ids:
            err(f"{owner}: duplicate id")
        for t in d.get("tags", []):
            if t not in tags:
                err(f"{owner}: unknown tag {t}")
        drill_ids.add(d.get("id"))
        if d.get("kind") not in DRILL_KINDS:
            err(f"{owner}: unknown kind {d.get('kind')}")
        for r in d.get("regions", []):
            if r not in REGIONS:
                err(f"{owner}: unknown region {r}")
        for p in d.get("prepares", []):
            if p not in PATTERNS:
                err(f"{owner}: unknown pattern {p}")
        for j, v in d.get("joints", {}).items():
            if j not in JOINTS or not isinstance(v, int) or not 0 <= v <= 4:
                err(f"{owner}: bad joint stress {j}={v}")
        if "joints" not in d:
            err(f"{owner}: drills need a joints map (0-4 per loaded joint; {{}} for none) so pain limits apply")
        if d.get("unit") not in ("REPS", "SECONDS"):
            err(f"{owner}: unit must be REPS or SECONDS")
        for e in d.get("equipment", []):
            if e not in equipment:
                err(f"{owner}: unknown equipment {e}")
        validate_text(owner, d.get("text"), need_mistakes=False)
    for p in LIFTING:
        if not any(p in d.get("prepares", []) for d in drills if d.get("kind") in ("ACTIVATE", "MOBILISE")):
            err(f"drills: nothing prepares {p} (MOB-001)")
    for kind in ("BALANCE", "STRETCH", "BREATHING"):
        if not any(d.get("kind") == kind for d in drills):
            err(f"drills: no {kind} drill")

    modalities = load_json("modalities") or []
    seen_mod, move_ids = set(), set()
    for m in modalities:
        owner = f"modalities:{m.get('modality', '?')}"
        if m.get("modality") not in MODALITIES or m.get("modality") in seen_mod:
            err(f"{owner}: unknown or duplicate modality")
        seen_mod.add(m.get("modality"))
        for e in m.get("equipment", []) + m.get("alt_equipment", []):
            if e not in equipment:
                err(f"{owner}: unknown equipment {e}")
        for u in m.get("units", []):
            if u not in MOD_UNITS:
                err(f"{owner}: unknown unit {u}")
        validate_text(owner, m.get("text"))
        # Circuit moves (EQ-003: away-from-gym conditioning defaults to the no-jump moves).
        for mv in m.get("moves", []):
            mo = f"{owner}:{mv.get('id', '?')}"
            if set(mv) - MOVE_KEYS:
                err(f"{mo}: unknown keys {sorted(set(mv) - MOVE_KEYS)}")
            if not re.fullmatch(r"move-[a-z0-9]+(-[a-z0-9]+)*", mv.get("id", "")):
                err(f"{mo}: move ids are kebab-case starting with move-")
            if mv.get("id") in ids or mv.get("id") in drill_ids or mv.get("id") in move_ids:
                err(f"{mo}: duplicate id")
            move_ids.add(mv.get("id"))
            if not isinstance(mv.get("jumping"), bool):
                err(f"{mo}: jumping must be true or false")
            elif mv["jumping"] != ("jumping" in mv.get("tags", [])):
                err(f"{mo}: jumping moves, and only they, carry the jumping tag")
            for t in mv.get("tags", []):
                if t not in tags:
                    err(f"{mo}: unknown tag {t}")
            for j, v in mv.get("joints", {}).items():
                if j not in JOINTS or not isinstance(v, int) or not 0 <= v <= 4:
                    err(f"{mo}: bad joint stress {j}={v}")
            validate_text(mo, mv.get("text"), need_mistakes=False)
        if m.get("modality") == "BODYWEIGHT_CIRCUIT" and sum(1 for mv in m.get("moves", []) if mv.get("jumping") is False) < 6:
            err(f"{owner}: needs at least 6 no-jump moves (EQ-003)")
    for m in MODALITIES:
        if m not in seen_mod:
            err(f"modalities: {m} has no entry")

    if errors:
        return finish()
    return write_or_check(meta, exercises, drills, modalities)


def exercise_kt(ex):
    joints = ", ".join(f"Joint.{j} to {v}" for j, v in ex["joints"].items())
    args = [
        f"id = {kstr(ex['id'])}", f"name = {kstr(ex['name'])}", f"pattern = Pattern.{ex['pattern']}",
        f"primary = {kset('Muscle', ex['primary'])}", f"secondary = {kset('Muscle', ex['secondary'])}",
        f"equipment = {kstrset(ex['equipment'])}", f"loadType = LoadType.{ex['load']}", f"costClass = CostClass.{ex['cost']}",
        f"difficulty = {ex['difficulty']}", f"skill = {ex['skill']}",
        f"jointStress = {'mapOf(' + joints + ')' if joints else 'emptyMap()'}",
        f"fatigueSystemic = {ex['fatigue']}", f"impact = {ex['impact']}",
        f"objectives = {kset('Objective', ex['objectives'])}",
        f"defaultRepRange = {ex['reps'][0]}..{ex['reps'][1]}", f"maxExtendedReps = {ex['max_reps']}",
        f"failureSafe = {str(bool(ex.get('failure_safe'))).lower()}", f"trackE1rm = {str(bool(ex.get('e1rm'))).lower()}",
        f"unilateral = {str(bool(ex.get('unilateral'))).lower()}", f"tempoSecPerRep = {float(ex['tempo'])!r}",
        f"setupSec = {ex['setup']}", f"station = Station.{ex['_station_kind']}", f"limitationTags = {kstrset(ex['tags'])}",
        f"aliases = {kstrlist(ex['aliases'])}",
        f"secondaryPattern = {'Pattern.' + ex['pattern2'] if ex.get('pattern2') else 'null'}",
        f"equipmentClass = EquipmentClass.{ex['class']}", f"stationKey = {kstr(ex['station'])}",
        f"family = {kstr(ex['family']) if ex.get('family') else 'null'}", f"rung = {ex.get('rung') or 0}",
        f"progressionId = {kstr(ex['progression']) if ex.get('progression') else 'null'}",
        f"regressionId = {kstr(ex['regression']) if ex.get('regression') else 'null'}",
        f"unit = DoseUnit.{ex['unit']}", f"powerCapable = {str(bool(ex.get('power'))).lower()}",
        f"assisted = {str(bool(ex.get('assisted'))).lower()}", f"bar = {kstr(ex['bar']) if ex.get('bar') else 'null'}",
        f"userAddOnly = {str(bool(ex.get('user_add_only'))).lower()}",
        "equipmentAnyOf = " + ("emptyList()" if not ex["equipment_any"] else "listOf(" + ", ".join(kstrset(g) for g in ex["equipment_any"]) + ")"),
        f"powerOnly = {str(bool(ex.get('power_only'))).lower()}",
    ]
    return "        Exercise(\n            " + ",\n            ".join(args) + ",\n        ),"


def text_kt(key, t):
    return (f"        {kstr(key)} to ExerciseText({kstr(t['setup'])}, {kstrlist(t['cues'])}, "
            f"{kstrlist(t.get('mistakes', []))}, {kstr(t['safety']) if t.get('safety') else 'null'}),")


HEADER = "// GENERATED by tools/gen_library_kotlin.py from library/v1/*.json - do not edit by hand.\n"


def write_or_check(meta, exercises, drills, modalities):
    lib = [HEADER, "package com.personalfitnesscoach.engine.library\n",
           "import com.personalfitnesscoach.engine.model.*\n",
           "/** The exercise library, version " + meta["library_version"] + " (D-046). */",
           "object GeneratedLibrary {",
           f"    const val VERSION: String = {kstr(meta['library_version'])}",
           f"    const val LICENSE: String = {kstr(meta['license'])}", ""]
    lib.append("    val equipment: Map<String, EquipmentInfo> = mapOf(")
    for k, v in meta["equipment"].items():
        lib.append(f"        {kstr(k)} to EquipmentInfo({kstr(k)}, {kstr(v['name'])}, EquipmentClass.{v['class']}),")
    lib.append("    )\n")
    lib.append("    val tags: Map<String, String> = mapOf(")
    for k, v in meta["tags"].items():
        lib.append(f"        {kstr(k)} to {kstr(v)},")
    lib.append("    )\n")
    lib.append("    /** Pattern/class combinations with no exercise, and why (checked by the generator and the tests). */")
    lib.append("    val coverageGaps: Map<Pattern, Map<EquipmentClass, String>> = mapOf(")
    for p, m in meta.get("coverage_gaps", {}).items():
        inner = ", ".join(f"EquipmentClass.{c} to {kstr(w)}" for c, w in m.items())
        lib.append(f"        Pattern.{p} to mapOf({inner}),")
    lib.append("    )\n")
    lib.append("    val exercises: List<Exercise> by lazy { " + " + ".join(f"{f}()" for f in EXERCISE_FILES) + " }\n")
    for f in EXERCISE_FILES:
        lib.append(f"    private fun {f}(): List<Exercise> = listOf(")
        lib.extend(exercise_kt(ex) for ex in exercises if ex["_file"] == f)
        lib.append("    )\n")
    lib.append("    val drills: List<Drill> = listOf(")
    for d in drills:
        joints = ", ".join(f"Joint.{j} to {v}" for j, v in d.get("joints", {}).items())
        lib.append(f"        Drill({kstr(d['id'])}, {kstr(d['name'])}, DrillKind.{d['kind']}, {kset('Region', d.get('regions', []))}, "
                   f"{kset('Pattern', d.get('prepares', []))}, DoseUnit.{d['unit']}, {d['amount']}, {str(bool(d.get('per_side'))).lower()}, "
                   f"{kstrset(d.get('equipment', []))}, {'mapOf(' + joints + ')' if joints else 'emptyMap()'}, {kstrset(d.get('tags', []))}),")
    lib.append("    )\n")
    lib.append("    val modalities: List<ModalityInfo> = listOf(")
    for m in modalities:
        units = ", ".join(f"ConditioningUnit.{u}" for u in m.get("units", []))
        moves = []
        for mv in m.get("moves", []):
            mj = ", ".join(f"Joint.{j} to {v}" for j, v in mv.get("joints", {}).items())
            moves.append(f"CircuitMove({kstr(mv['id'])}, {kstr(mv['name'])}, {str(mv['jumping']).lower()}, "
                         f"{'mapOf(' + mj + ')' if mj else 'emptyMap()'}, {kstrset(mv.get('tags', []))})")
        lib.append(f"        ModalityInfo(Modality.{m['modality']}, {kstr(m['name'])}, {kstrset(m.get('equipment', []))}, "
                   f"{kstrset(m.get('alt_equipment', []))}, listOf({units})" + (", listOf(\n            " + ",\n            ".join(moves) + ",\n        )" if moves else "") + "),")
    lib.append("    )")
    lib.append("}")
    lib_src = "\n".join(lib) + "\n"

    txt = [HEADER, "package com.personalfitnesscoach.engine.library\n",
           "import com.personalfitnesscoach.engine.model.Modality\n",
           "/** User-facing exercise text. All wording is original (D7); licence: ORIGINAL. */",
           "object GeneratedLibraryText {",
           "    val exercises: Map<String, ExerciseText> by lazy { " + " + ".join(f"{f}()" for f in EXERCISE_FILES) + " }\n"]
    for f in EXERCISE_FILES:
        txt.append(f"    private fun {f}(): Map<String, ExerciseText> = mapOf(")
        txt.extend(text_kt(ex["id"], ex["text"]) for ex in exercises if ex["_file"] == f)
        txt.append("    )\n")
    txt.append("    val drills: Map<String, ExerciseText> by lazy { mapOf(")
    txt.extend(text_kt(d["id"], d["text"]) for d in drills)
    txt.append("    ) }\n")
    txt.append("    val modalities: Map<Modality, ExerciseText> by lazy { mapOf(")
    for m in modalities:
        t = m["text"]
        txt.append(f"        Modality.{m['modality']} to ExerciseText({kstr(t['setup'])}, {kstrlist(t['cues'])}, "
                   f"{kstrlist(t.get('mistakes', []))}, {kstr(t['safety']) if t.get('safety') else 'null'}),")
    txt.append("    ) }\n")
    txt.append("    /** Bodyweight-circuit moves (EQ-003). */")
    txt.append("    val circuitMoves: Map<String, ExerciseText> by lazy { mapOf(")
    txt.extend(text_kt(mv["id"], mv["text"]) for m in modalities for mv in m.get("moves", []))
    txt.append("    ) }")
    txt.append("}")
    txt_src = "\n".join(txt) + "\n"

    outputs = {OUT_DIR / "GeneratedLibrary.kt": lib_src, OUT_DIR / "GeneratedLibraryText.kt": txt_src}
    if "--check" in sys.argv:
        stale = [p.name for p, s in outputs.items() if not p.exists() or p.read_text(encoding="utf-8") != s]
        if stale:
            print("ERROR: generated library is stale: " + ", ".join(stale) + " - run python3 -I tools/gen_library_kotlin.py")
            return 1
        print(f"library OK: {len(exercises)} exercises, {len(drills)} drills, {len(modalities)} modalities (v{meta['library_version']})")
        return 0
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for p, s in outputs.items():
        p.write_text(s, encoding="utf-8")
    print(f"wrote {len(exercises)} exercises, {len(drills)} drills, {len(modalities)} modalities (v{meta['library_version']})")
    return 0


def finish():
    for e in errors:
        print("ERROR: " + e)
    print(f"{len(errors)} library validation error(s)")
    return 1


if __name__ == "__main__":
    sys.exit(main())
