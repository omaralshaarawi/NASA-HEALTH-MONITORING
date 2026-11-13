"""Writes resources_reference.json: outputs of contract/resources.py that the Java port must reproduce.
Run from anywhere: python3 make_resources_reference.py"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).parent
CONTRACT = HERE.parents[4] / "contract"
sys.path.insert(0, str(CONTRACT))
import resources  # noqa: E402

menu = json.loads((CONTRACT / "exercise_menu.json").read_text())
ids = [i["exercise_id"] for i in menu["items"]]

impacts = []
for ex in ids:
    for minutes in [5, 10, 15, 17, 30, 45, 60, 90]:
        for mass in [50, 55.5, 60, 70, 80, 92.3]:
            impacts.append(dict(exercise_id=ex, minutes=minutes, mass_kg=mass,
                                expected=resources.session_impact(ex, minutes, mass)))

block_inputs = [
    ([dict(exercise_id="resistive_vigorous", minutes=30), dict(exercise_id="circadian_led_journaling", minutes=20)], 80),
    ([dict(exercise_id="zero_t2_ergometer", minutes=45), dict(exercise_id="cycle_ergometer", minutes=30),
      dict(exercise_id="stretch_mobility", minutes=10)], 72.5),
    ([dict(exercise_id="thigh_cuff_bcomplex", minutes=20)], 64),
    ([], 80),
]
blocks = [dict(sessions=s, mass_kg=m, expected=resources.build_resource_block(s, m)) for s, m in block_inputs]

good = dict(
    sessions=[dict(exercise_id="resistive_vigorous", minutes=30, target_system="SPINE_BONE",
                   instructions=["Warm up", "Use the restraints"], rationale="Resistive time was low for 4 days")],
    nutrition=dict(kcal_adjustment=200, focus=["protein"], notes="Eat the full ration"),
    escalate_to_flight_surgeon=False, escalation_reason="", explanation="Short plan.")
s0 = good["sessions"][0]


def with_session(**kw):
    return {**good, "sessions": [{**s0, **kw}]}


drafts = {
    "good draft": (good, 0.0),
    "unknown exercise": (with_session(exercise_id="bench_press"), 0.0),
    "ARED on Mars": (good, 0.38),
    "ARED above 0.5 g": (good, 1.0),
    "minutes 200": (with_session(minutes=200), 0.0),
    "minutes 30.5": (with_session(minutes=30.5), 0.0),
    "minutes missing": ({**good, "sessions": [{k: v for k, v in s0.items() if k != "minutes"}]}, 0.0),
    "minutes as text": (with_session(minutes="30"), 0.0),
    "thigh cuff without escalation": (with_session(exercise_id="thigh_cuff_bcomplex", target_system="HEAD_NEURO"), 0.0),
    "thigh cuff with escalation": ({**with_session(exercise_id="thigh_cuff_bcomplex", target_system="HEAD_NEURO"),
                                    "escalate_to_flight_surgeon": True}, 0.0),
    "walk on the surface in 0 g": (with_session(exercise_id="walk_surface", target_system="CHEST_CARDIO"), 0.0),
    "walk on the Moon": (with_session(exercise_id="walk_surface", target_system="CHEST_CARDIO"), 0.16),
    "bad target system": (with_session(target_system="LEGS"), 0.0),
    "target system missing": ({**good, "sessions": [{k: v for k, v in s0.items() if k != "target_system"}]}, 0.0),
    "dosing in instructions": (with_session(instructions=["Take 500 mg of B-complex"]), 0.0),
    "dosing in rationale": (with_session(rationale="One tablet helps with sleep"), 0.0),
    "dosing in notes": ({**good, "nutrition": {**good["nutrition"], "notes": "Add 2.5 mcg of vitamin D"}}, 0.0),
    "dosing in focus": ({**good, "nutrition": {**good["nutrition"], "focus": ["protein", "iron capsule"]}}, 0.0),
    "dosing in explanation": ({**good, "explanation": "An injection is not needed."}, 0.0),
    "long dosing text": (with_session(instructions=["It's a long sentence that keeps going before it says take 10 IU daily"]), 0.0),
    "kcal +900": ({**good, "nutrition": {**good["nutrition"], "kcal_adjustment": 900}}, 0.0),
    "kcal 250.5": ({**good, "nutrition": {**good["nutrition"], "kcal_adjustment": 250.5}}, 0.0),
    "kcal missing": ({**good, "nutrition": dict(focus=[], notes="")}, 0.0),
    "nutrition missing": ({k: v for k, v in good.items() if k != "nutrition"}, 0.0),
    "121 words": ({**good, "explanation": " ".join(["word"] * 121)}, 0.0),
    "120 words": ({**good, "explanation": " ".join(["word"] * 120)}, 0.0),
    "several problems": ({**good, "sessions": [{**s0, "minutes": 3, "target_system": None},
                                              {**s0, "exercise_id": "yoga"},
                                              {**s0, "exercise_id": "compression_fluid_loading"}],
                          "nutrition": {**good["nutrition"], "kcal_adjustment": -400}}, 0.0),
    "empty draft": ({}, 0.0),
}
validations = [dict(name=n, draft=d, gravity_g=g, expected=resources.validate_draft(d, g)) for n, (d, g) in drafts.items()]

out = dict(session_impacts=impacts, resource_blocks=blocks, validations=validations)
(HERE / "resources_reference.json").write_text(json.dumps(out, indent=1) + "\n")
print(len(impacts), "session impacts,", len(blocks), "blocks,", len(validations), "validations")
