"""Deterministic habitat resource impact of a countermeasure plan. The LLM never produces these numbers."""
import json
from pathlib import Path
_R = json.loads((Path(__file__).parent / "rules.json").read_text())["resource_math"]
_MENU = {i["exercise_id"]: i for i in json.loads((Path(__file__).parent / "exercise_menu.json").read_text())["items"]}

def session_impact(exercise_id, minutes, mass_kg):
    met = _MENU[exercise_id]["met"]
    hours = minutes / 60
    gross = met * _R["kcal_per_met_kg_h"] * mass_kg * hours
    extra = max(met - _R["rest_met"], 0) * _R["kcal_per_met_kg_h"] * mass_kg * hours   # resting part is already in the ration
    return dict(met=met, gross_kcal=round(gross), extra_kcal=round(extra),
                extra_water_ml=round(extra * _R["water_ml_per_kcal"]), extra_co2_g=round(extra * _R["co2_g_per_kcal"]))

if __name__ == "__main__":
    s = session_impact("resistive_vigorous", 30, 80)
    print(s)
    assert s == dict(met=6.0, gross_kcal=240, extra_kcal=200, extra_water_ml=230, extra_co2_g=68), s
    # sanity: the old constant overstated CO2 by 2.5x
    print("old 0.85 g/kcal would give", round(200 * 0.85), "g CO2 for the same session")
    # sanity: whole-crew-day CO2 from a 2,800 kcal day should be near the ~1 kg/day astronauts exhale
    print("2800 kcal/day ->", round(2800 * _R["co2_g_per_kcal"]), "g CO2/day")
