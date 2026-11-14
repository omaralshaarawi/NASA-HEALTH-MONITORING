"""
Simulated Closed-Loop ECLSS Estimates for a countermeasure plan. The LLM never produces these numbers.

Context (NASA HRP Hostile/Closed Environments video, as transcribed by the team; verify wording before quoting):
  "Close the loop - Recycling all possible resources, including an astronaut's carbon dioxide and urine,
   into tomorrow's Air and Water."
Formulas (rules.json "resource_math"): extra_kcal = (MET - 1) * mass_kg * hours; water = 1.15 mL/kcal; CO2 = 0.34 g/kcal.
These are decision-support estimates, not life-support design values.
"""
import json
import re
from pathlib import Path

_HERE = Path(__file__).parent
_R = json.loads((_HERE / "rules.json").read_text())["resource_math"]
_MENU_FILE = json.loads((_HERE / "exercise_menu.json").read_text())
_MENU = {i["exercise_id"]: i for i in _MENU_FILE["items"]}
_REGIONS = {"HEAD_NEURO", "CHEST_CARDIO", "SPINE_BONE", "SYSTEMIC_IMMUNE_METABOLIC"}


def session_impact(exercise_id, minutes, mass_kg):
    met = _MENU[exercise_id]["met"]
    hours = minutes / 60
    gross = met * _R["kcal_per_met_kg_h"] * mass_kg * hours
    extra = max(met - _R["rest_met"], 0) * _R["kcal_per_met_kg_h"] * mass_kg * hours   # resting part is already in the ration
    return dict(met=met, gross_kcal=round(gross), extra_kcal=round(extra),
                extra_water_ml=round(extra * _R["water_ml_per_kcal"]), extra_co2_g=round(extra * _R["co2_g_per_kcal"]))


def build_resource_block(sessions, mass_kg):
    """sessions: [{exercise_id, minutes}] -> ResourceBlock (contract.ts)."""
    rows = []
    for s in sessions:
        rows.append(dict(exercise_id=s["exercise_id"], minutes=s["minutes"], **session_impact(s["exercise_id"], s["minutes"], mass_kg)))
    return dict(
        assumptions=dict(mass_kg=mass_kg, rest_met=_R["rest_met"], water_ml_per_kcal=_R["water_ml_per_kcal"],
                         co2_g_per_kcal=_R["co2_g_per_kcal"], formula="extra_kcal = (MET - rest_met) * mass_kg * hours"),
        sessions=rows,
        totals=dict(extra_kcal=sum(r["extra_kcal"] for r in rows), extra_water_ml=sum(r["extra_water_ml"] for r in rows),
                    extra_co2_g=sum(r["extra_co2_g"] for r in rows)),
        label="decision-support estimate")   # literal fixed by contract.ts; present it in the UI as "Simulated closed-loop ECLSS estimate"


_DOSE = re.compile(r"\b\d+(\.\d+)?\s?(mg|mcg|ug|iu)\b|\b(tablet|pill|capsule|inject\w*)\b", re.I)


def validate_draft(draft, gravity_g):
    """Server-side guard for the LLM's CountermeasurePlanDraft. Returns a list of problems (empty = OK)."""
    p = []
    needs_direction = False
    for i, s in enumerate(draft.get("sessions", [])):
        item = _MENU.get(s.get("exercise_id"))
        if item is None:
            p.append(f"sessions[{i}]: unknown exercise_id {s.get('exercise_id')!r}"); continue
        m = s.get("minutes")
        if not isinstance(m, int) or not (_MENU_FILE["min_minutes"] <= m <= _MENU_FILE["max_minutes_per_session"]):
            p.append(f"sessions[{i}]: minutes {m!r} outside {_MENU_FILE['min_minutes']}-{_MENU_FILE['max_minutes_per_session']}")
        if "gravity_max_g" in item and gravity_g > item["gravity_max_g"]:
            p.append(f"sessions[{i}]: {s['exercise_id']} not allowed above {item['gravity_max_g']} g")
        if "gravity_min_g" in item and gravity_g < item["gravity_min_g"]:
            p.append(f"sessions[{i}]: {s['exercise_id']} needs at least {item['gravity_min_g']} g")
        if s.get("target_system") not in _REGIONS:
            p.append(f"sessions[{i}]: bad target_system {s.get('target_system')!r}")
        needs_direction |= item["requires_flight_surgeon_direction"]
        for t in s.get("instructions", []) + [s.get("rationale", "")]:
            if _DOSE.search(t): p.append(f"sessions[{i}]: dosing language in {t[:50]!r}")
    if needs_direction and not draft.get("escalate_to_flight_surgeon"):
        p.append("a PROTOCOL needing flight-surgeon direction was chosen but escalate_to_flight_surgeon is false")
    n = draft.get("nutrition", {})
    if not isinstance(n.get("kcal_adjustment"), int) or not (-300 <= n["kcal_adjustment"] <= 600):
        p.append(f"nutrition.kcal_adjustment {n.get('kcal_adjustment')!r} outside -300..600")
    for t in n.get("focus", []) + [n.get("notes", ""), draft.get("explanation", "")]:
        if _DOSE.search(t): p.append(f"dosing language in {t[:50]!r}")
    if len(draft.get("explanation", "").split()) > 120:
        p.append("explanation longer than 120 words")
    return p


if __name__ == "__main__":
    s = session_impact("resistive_vigorous", 30, 80)
    print(s)
    assert s == dict(met=6.0, gross_kcal=240, extra_kcal=200, extra_water_ml=230, extra_co2_g=68), s
    print("old 0.85 g/kcal would give", round(200 * 0.85), "g CO2 for the same session")
    print("2800 kcal/day ->", round(2800 * _R["co2_g_per_kcal"]), "g CO2/day")
    rb = build_resource_block([dict(exercise_id="resistive_vigorous", minutes=30), dict(exercise_id="circadian_led_journaling", minutes=20)], 80)
    print("block totals:", rb["totals"]); assert rb["totals"]["extra_kcal"] == 200 + 8, rb["totals"]

    good = dict(sessions=[dict(exercise_id="resistive_vigorous", minutes=30, target_system="SPINE_BONE", instructions=["Warm up", "Use the restraints"], rationale="Resistive time was low for 4 days")],
                nutrition=dict(kcal_adjustment=200, focus=["protein"], notes="Eat the full ration"), escalate_to_flight_surgeon=False, escalation_reason="", explanation="Short plan.")
    cases = {
        "good draft": (good, 0.0, True),
        "unknown exercise": ({**good, "sessions": [{**good["sessions"][0], "exercise_id": "bench_press"}]}, 0.0, False),
        "ARED on Mars (0.38 g)": (good, 0.38, True),
        "ARED above 0.5 g": (good, 1.0, False),
        "minutes 200": ({**good, "sessions": [{**good["sessions"][0], "minutes": 200}]}, 0.0, False),
        "thigh cuff without escalation": ({**good, "sessions": [{**good["sessions"][0], "exercise_id": "thigh_cuff_bcomplex", "target_system": "HEAD_NEURO"}]}, 0.0, False),
        "thigh cuff with escalation": ({**good, "escalate_to_flight_surgeon": True, "sessions": [{**good["sessions"][0], "exercise_id": "thigh_cuff_bcomplex", "target_system": "HEAD_NEURO"}]}, 0.0, True),
        "dosing language": ({**good, "sessions": [{**good["sessions"][0], "instructions": ["Take 500 mg of B-complex"]}]}, 0.0, False),
        "kcal +900": ({**good, "nutrition": {**good["nutrition"], "kcal_adjustment": 900}}, 0.0, False),
    }
    bad = 0
    for name, (d, g, ok) in cases.items():
        probs = validate_draft(d, g)
        res = (not probs) == ok
        bad += not res
        print(("PASS " if res else "FAIL ") + f"{name}: {'ok' if not probs else probs[0][:90]}")
    raise SystemExit(bad)
