"""Generate engine/src/main/kotlin/com/personalfitnesscoach/engine/safety/GeneratedConditions.kt from the
health-condition table rules/health_conditions_v1.0.json (SAF-010, decision D-058).

Every entry becomes a typed ConditionEntry (see ConditionModel.kt). The script fails on any key, zone, tag,
joint or merge-order value it does not know, so a table change can never be silently ignored by the engine.

    python3 -I tools/gen_conditions_kotlin.py          # validate + write
    python3 -I tools/gen_conditions_kotlin.py --check  # CI: fail if invalid or stale
"""
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "rules" / "health_conditions_v1.0.json"
LIB = ROOT / "library" / "v1" / "library.json"
REG = ROOT / "rules" / "rule_registry_v1.0.json"
OUT = ROOT / "engine/src/main/kotlin/com/personalfitnesscoach/engine/safety/GeneratedConditions.kt"

ZONES = {"Z1", "Z2", "Z3"}
JOINTS = {"SHOULDER", "ELBOW", "WRIST", "SPINE", "HIP", "KNEE", "ANKLE"}
SCOPES = {"light_moderate", "vigorous", "intervals"}
KNOWN = {"id", "name", "group", "parent", "optional", "clearance", "clearance_prompt_now", "clearance_text", "clearance_always_if",
         "max_zone", "hiit", "hiit_base_weeks", "hiit_needs_scope", "hiit_low_impact_only", "hiit_modalities", "failure_allowed",
         "impact", "impact_max", "impact_none_if", "impact_unlock", "min_rir", "min_rir_applies_to_tags", "avoid_tags",
         "avoid_tags_at_start", "avoid_tags_early", "avoid_tags_from_week", "avoid_supine_any_time_if", "range_limited_tags",
         "joint_limits", "joint_limit_unlock", "extra_warmup_min", "extra_cooldown_min", "max_zone_after_weeks",
         "max_zone_after_clearance", "max_zone_if_previously_vigorous_and_ok", "after_clearance", "ask_control_status",
         "if_not_controlled", "scheduling", "phases", "impact_checks", "impact_dose", "required_work", "effort_by", "default_order",
         "warmup_style", "sub_flags", "flare_mode", "pain_rule", "block_if", "attest_if", "asks", "prompts", "stop_signs",
         "environment", "positions_note", "never_recommend", "goal_effects", "goal_effects_if", "not_encoded",
         "pending_verification", "sources", "confidence"}

# ConditionEntry constructor parameters, in order (ConditionModel.kt); the generator emits named arguments.
FIELDS = ['id', 'name', 'group', 'parent', 'optional', 'clearance', 'clearancePromptNow', 'clearanceText', 'clearanceAlwaysIf', 'maxZone', 'hiit', 'hiitBaseWeeks', 'hiitNeedsScope', 'hiitLowImpactOnly', 'hiitModalities', 'failureAllowed', 'impact', 'impactMax', 'impactNoneIf', 'impactUnlockAfterWeeks', 'impactUnlockOptIn', 'impactUnlockNeedsPainRule', 'minRir', 'minRirTags', 'avoidTags', 'avoidTagsAtStart', 'avoidTagsEarly', 'avoidTagsFromWeek', 'avoidSupineAnyTimeIf', 'rangeLimitedTags', 'jointLimits', 'jointLimitUnlock', 'jointLimitUnlockAfterWeeks', 'jointLimitUnlockNeedsPainRule', 'extraWarmupMin', 'extraCooldownMin', 'maxZoneAfterWeeks', 'maxZoneAfterWeeksZone', 'maxZoneAfterClearance', 'maxZoneIfPreviouslyVigorousAndOk', 'afterClearance', 'askControlStatus', 'ifNotControlledMaxZone', 'ifNotControlledHiit', 'ifNotControlledPrompt', 'maxConsecutiveInactiveDays', 'strengthOnConsecutiveDays', 'phases', 'impactChecks', 'boneLoading', 'boneLoadingText', 'boneLoadingImpactsMin', 'balancePerWeek', 'backExtensorPerWeek', 'effortBy', 'defaultOrder', 'warmupStyle', 'subFlags', 'flareJointLimits', 'flareAvoidTags', 'flareKeep', 'flareExitSessions', 'painRuleDuringMax', 'blockIf', 'attestIf', 'asks', 'prompts', 'stopSigns', 'environment', 'positionsNote', 'neverRecommend', 'goalEffects', 'goalEffectsIf', 'notEncoded', 'pendingVerification', 'sources', 'confidence']

errors = []


def err(m):
    errors.append(m)


def kstr(s):
    return "null" if s is None else json.dumps(s, ensure_ascii=False).replace("$", "\\$")


def klist(xs):
    return "emptyList()" if not xs else "listOf(" + ", ".join(kstr(x) for x in xs) + ")"


def kset(xs):
    return "emptySet()" if not xs else "setOf(" + ", ".join(kstr(x) for x in xs) + ")"


def kzone(z):
    return "null" if z is None else f"Zone.{z}"


def kjoints(m):
    return "emptyMap()" if not m else "mapOf(" + ", ".join(f"Joint.{j} to {v}" for j, v in m.items()) + ")"


def snake(s):
    return re.sub(r"[^a-z0-9]+", "_", s.lower()).strip("_")


def main():
    raw = SRC.read_bytes()
    table = json.loads(raw)
    tags = set(json.loads(LIB.read_text(encoding="utf-8"))["tags"])
    reg = json.loads(REG.read_text(encoding="utf-8"))
    matrix = set(next(r for r in reg["rules"] if r["rule_id"] == "MOD-002")["parameters"]["matrix"])
    orders = table["merge_orders"]
    ids = [e["id"] for e in table["entries"]]
    if len(set(ids)) != len(ids):
        err("duplicate entry ids")

    out = []
    for e in table["entries"]:
        o = e["id"]
        for k in set(e) - KNOWN:
            err(f"{o}: unknown key {k} - teach the generator and the engine what it means first")

        def zone(v, key):
            if v is not None and v not in ZONES:
                err(f"{o}: bad zone {v} in {key}")
            return v

        def tagset(vs, key):
            for t in vs or []:
                if t not in tags:
                    err(f"{o}: unknown library tag {t} in {key}")
            return vs or []

        def joints(m, key):
            m = {j: v for j, v in (m or {}).items() if j in JOINTS or not err(f"{o}: unknown joint {j} in {key}")}
            for j, v in m.items():
                if not isinstance(v, int) or not 0 <= v <= 4:
                    err(f"{o}: bad joint limit {j}={v}")
            return m

        if e["clearance"] not in orders["clearance"]:
            err(f"{o}: clearance {e['clearance']} has no merge order")
        by_phase = e["max_zone"] == "by_phase"
        if not by_phase:
            zone(e["max_zone"], "max_zone")
            if e["hiit"] not in orders["hiit"]:
                err(f"{o}: hiit {e['hiit']} has no merge order")
            if e["impact"] not in orders["impact"] and e["impact"] != "none_at_start":
                err(f"{o}: impact {e['impact']} has no merge order")
        elif not e.get("phases"):
            err(f"{o}: by_phase without phases")
        hm = e.get("hiit_modalities")
        for m in hm or []:
            if m not in matrix:
                err(f"{o}: hiit modality {m} is not in the MOD-002 matrix")
        if e.get("hiit_needs_scope") not in (None, *SCOPES):
            err(f"{o}: bad hiit_needs_scope")
        iu = e.get("impact_unlock") or {}
        for k in set(iu) - {"after_weeks", "if", "opt_in"}:
            err(f"{o}: unknown impact_unlock key {k}")
        if iu.get("if") not in (None, "pain rule met"):
            err(f"{o}: impact_unlock 'if' must be 'pain rule met'")
        ju = dict(e.get("joint_limit_unlock") or {})
        ju_weeks = ju.pop("after_weeks", None)
        ju_pain = ju.pop("after_weeks_meeting_pain_rule", None)
        ju = joints(ju, "joint_limit_unlock")
        mr = e.get("min_rir")
        min_rir = None if mr in (None, "by_phase") else mr
        if min_rir is not None and not isinstance(min_rir, int):
            err(f"{o}: bad min_rir {mr}")
        mzw = e.get("max_zone_after_weeks")
        ac = []
        for scope, v in (e.get("after_clearance") or {}).items():
            if scope not in SCOPES:
                err(f"{o}: after_clearance scope {scope}")
            for k in set(v) - {"max_zone", "min_rir", "hiit"}:
                err(f"{o}: after_clearance key {k}")
            ac.append(f"AfterClearance({kstr(scope)}, {kzone(zone(v.get('max_zone'), 'after_clearance'))}, "
                      f"{v.get('min_rir') if v.get('min_rir') is not None else 'null'}, {kstr(v.get('hiit'))})")
        inc = e.get("if_not_controlled") or {}
        sch = e.get("scheduling") or {}
        for k in set(sch) - {"max_consecutive_inactive_days", "strength_on_consecutive_days"}:
            err(f"{o}: scheduling key {k}")
        phases = []
        for ph in e.get("phases") or []:
            for k in set(ph) - {"weeks", "until", "max_zone", "hiit", "min_rir", "impact", "focus", "attest", "impact_unlock"}:
                err(f"{o}: phase key {k}")
            if ph["impact"] not in ("none", "progressive") and ph["impact"] not in orders["impact"]:
                err(f"{o}: phase impact {ph['impact']}")
            if ph["hiit"] not in orders["hiit"] and ph["hiit"] != "after_impact_unlocked":
                err(f"{o}: phase hiit {ph['hiit']}")
            phases.append(f"ConditionPhase({ph['weeks'][0]}, {ph['weeks'][1]}, {kzone(zone(ph['max_zone'], 'phase'))}, {kstr(ph['hiit'])}, "
                          f"{ph.get('min_rir') if ph.get('min_rir') is not None else 'null'}, {kstr(ph['impact'])}, {kstr(ph.get('attest'))}, "
                          f"{kstr(ph.get('until'))}, {kstr(ph.get('impact_unlock'))}, {klist(ph.get('focus'))})")
        dose = e.get("impact_dose")
        rw = e.get("required_work") or {}
        for k in set(rw) - {"balance_per_week", "back_extensor_per_week"}:
            err(f"{o}: required_work key {k}")
        fl = e.get("flare_mode") or {}
        for k in set(fl) - {"switch", "joint_limits", "avoid_tags", "keep", "exit_after_sessions_without_next_day_flare"}:
            err(f"{o}: flare_mode key {k}")
        atw = {}
        for wk, ts in (e.get("avoid_tags_from_week") or {}).items():
            atw[int(wk)] = tagset(ts, "avoid_tags_from_week")
        pr = e.get("pain_rule") or {}
        if e.get("effort_by") not in (None, "feel_only", "feel_preferred"):
            err(f"{o}: effort_by {e.get('effort_by')}")
        if e.get("default_order") not in (None, "strength_then_cardio"):
            err(f"{o}: default_order {e.get('default_order')}")
        if e.get("parent") and e["parent"] not in ids and e["parent"] != "any_diabetes":
            err(f"{o}: parent {e['parent']} is not an entry")
        for s in e["sources"]:
            if s not in reg["sources"]:
                err(f"{o}: source {s} is not in the registry")

        args = [
            kstr(o), kstr(e["name"]), kstr(e["group"]), kstr(e.get("parent")), str(bool(e.get("optional"))).lower(),
            kstr(e["clearance"]), str(bool(e.get("clearance_prompt_now"))).lower(), kstr(e.get("clearance_text")),
            klist([snake(x) for x in e.get("clearance_always_if") or []]),
            "null" if by_phase else kzone(e["max_zone"]), kstr(e["hiit"]), str(e.get("hiit_base_weeks", 0)), kstr(e.get("hiit_needs_scope")),
            str(bool(e.get("hiit_low_impact_only"))).lower(), "null" if hm is None else kset(hm), str(bool(e["failure_allowed"])).lower(),
            kstr(e["impact"]), kstr(e.get("impact_max")), klist([snake(x) for x in e.get("impact_none_if") or []]),
            str(iu["after_weeks"]) if "after_weeks" in iu else "null", str(bool(iu.get("opt_in"))).lower(), str(iu.get("if") == "pain rule met").lower(),
            "null" if min_rir is None else str(min_rir), "null" if e.get("min_rir_applies_to_tags") is None else kset(tagset(e["min_rir_applies_to_tags"], "min_rir_applies_to_tags")),
            kset(tagset(e.get("avoid_tags"), "avoid_tags")), kset(tagset(e.get("avoid_tags_at_start"), "avoid_tags_at_start")),
            kset(tagset(e.get("avoid_tags_early"), "avoid_tags_early")),
            "emptyMap()" if not atw else "mapOf(" + ", ".join(f"{w} to {kset(ts)}" for w, ts in atw.items()) + ")",
            kstr(e.get("avoid_supine_any_time_if")), kset(tagset(e.get("range_limited_tags"), "range_limited_tags")),
            kjoints(joints(e.get("joint_limits"), "joint_limits")), kjoints(ju),
            str(ju_weeks if ju_weeks is not None else ju_pain) if (ju_weeks is not None or ju_pain is not None) else "null", str(ju_pain is not None).lower(),
            str(e.get("extra_warmup_min", 0)), str(e.get("extra_cooldown_min", 0)),
            str(mzw["weeks"]) if mzw else "null", kzone(zone(mzw["zone"], "max_zone_after_weeks")) if mzw else "null",
            kzone(zone(e.get("max_zone_after_clearance"), "max_zone_after_clearance")),
            kzone(zone(e.get("max_zone_if_previously_vigorous_and_ok"), "max_zone_if_previously_vigorous_and_ok")),
            "emptyList()" if not ac else "listOf(" + ", ".join(ac) + ")",
            str(bool(e.get("ask_control_status"))).lower(), kzone(zone(inc.get("max_zone"), "if_not_controlled")), kstr(inc.get("hiit")), kstr(inc.get("prompt")),
            str(sch["max_consecutive_inactive_days"]) if "max_consecutive_inactive_days" in sch else "null",
            str(sch["strength_on_consecutive_days"]).lower() if "strength_on_consecutive_days" in sch else "null",
            "emptyList()" if not phases else "listOf(" + ", ".join(phases) + ")",
            klist(e.get("impact_checks")), str(dose is not None).lower(), kstr(dose.get("block") if dose else None),
            str(dose["impacts_per_session_min"]) if dose and "impacts_per_session_min" in dose else "null",
            str(rw.get("balance_per_week", 0)), str(rw.get("back_extensor_per_week", 0)),
            kstr(e.get("effort_by")), kstr(e.get("default_order")), kstr(e.get("warmup_style")),
            "emptyMap()" if not e.get("sub_flags") else "mapOf(" + ", ".join(f"{kstr(k)} to {kstr(v)}" for k, v in e["sub_flags"].items()) + ")",
            kjoints(joints(fl.get("joint_limits"), "flare_mode")), kset(tagset(fl.get("avoid_tags"), "flare_mode")), klist(fl.get("keep")),
            str(fl["exit_after_sessions_without_next_day_flare"]) if "exit_after_sessions_without_next_day_flare" in fl else "null",
            str(pr["during_max_0_10"]) if "during_max_0_10" in pr else "null",
            klist(e.get("block_if")), klist(e.get("attest_if")), klist(e.get("asks")), klist(e["prompts"]), klist(e["stop_signs"]),
            klist(e.get("environment")), kstr(e.get("positions_note")), klist(e.get("never_recommend")), kstr(e.get("goal_effects")),
            kstr(e.get("goal_effects_if")), klist(e.get("not_encoded")), klist(e.get("pending_verification")), klist(e["sources"]), kstr(e["confidence"]),
        ]
        if len(args) != len(FIELDS):
            err(f"{o}: generator emits {len(args)} values for {len(FIELDS)} ConditionEntry fields")
        out.append("        ConditionEntry(\n            " + ",\n            ".join(f"{n} = {v}" for n, v in zip(FIELDS, args)) + ",\n        ),")
    # Table-level checks.
    for t in table.get("new_library_tags", []) + table.get("existing_library_tags_used", []):
        if t not in tags:
            err(f"table lists library tag {t} that the library does not define")
    reg_entries = next(r for r in reg["rules"] if r["rule_id"] == "SAF-010")["parameters"]["entries"]
    if reg_entries != ids:
        err("SAF-010 entries in the registry differ from the table")
    if errors:
        for m in errors:
            print("ERROR: " + m)
        print(f"{len(errors)} condition-table error(s)")
        return 1

    sha = hashlib.sha256(raw).hexdigest()
    src = "\n".join([
        "// GENERATED by tools/gen_conditions_kotlin.py from rules/health_conditions_v1.0.json - do not edit by hand.",
        "package com.personalfitnesscoach.engine.safety\n",
        "import com.personalfitnesscoach.engine.model.Joint",
        "import com.personalfitnesscoach.engine.model.Zone\n",
        f"/** Health-condition table {table['version']} (SAF-010). {table['status']}. */",
        "object GeneratedConditions {",
        f"    const val VERSION: String = {kstr(table['version'])}",
        f"    const val SHA256: String = {kstr(sha)}",
        "    val statusQuestion: String = " + kstr(table["status_question"]),
        "    val never: List<String> = " + klist(table["never"]),
        "",
        "    val entries: List<ConditionEntry> by lazy { listOf(",
        *out,
        "    ) }",
        "}",
        "",
    ])
    if "--check" in sys.argv:
        if not OUT.exists() or OUT.read_text(encoding="utf-8") != src:
            print("ERROR: GeneratedConditions.kt is stale - run python3 -I tools/gen_conditions_kotlin.py")
            return 1
        print(f"conditions OK: {len(ids)} entries (table {table['version']})")
        return 0
    OUT.write_text(src, encoding="utf-8")
    print(f"wrote {len(ids)} condition entries (table {table['version']})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
