"""Deterministic 14-day seed for 3 fictional astronauts in DEEP_SPACECRAFT. Output: seed.json (DailyMeasurementAndCompliance[])."""
import json
import numpy as np

ENV = "DEEP_SPACECRAFT"
DAYS = 14

def base_day(rng, a, d):
    # noise SDs are deliberately small (below the floors) so the nominal astronaut stays quiet
    return {
        "astronaut_id": a, "mission_day": d, "environment_id": ENV, "source": "SIMULATED",
        "vitals": {
            "hr_rest_bpm": round(61 + rng.normal(0, 1.0), 1),
            "sbp_mmHg": round(118 + rng.normal(0, 2.5), 0),
            "dbp_mmHg": round(76 + rng.normal(0, 2.0), 0),
            "glucose_mg_dl": round(90 + rng.normal(0, 3.0), 0),
            "temp_c": round(36.8 + rng.normal(0, 0.08), 2),
            "sleep_h": round(7.0 + rng.normal(0, 0.35), 1),
            "mood_1_10": int(np.clip(round(7.5 + rng.normal(0, 0.5)), 1, 10)),
        },
        "pvt": {"median_rt_ms": round(250 + rng.normal(0, 8), 0), "lapses_gt500ms": 0, "n_trials": 20, "source": "BROWSER_30S"},
        "habitat": {"radiation_mSv_day": round(1.4 + rng.normal(0, 0.05), 2), "cabin_co2_mmHg": round(2.7 + rng.normal(0, 0.1), 1)},
        "compliance": {"morning_vitals_logged": True, "ared_resistive_minutes": int(60 + rng.integers(-3, 4)), "ration_intake_pct": int(100 - rng.integers(0, 4))},
        "attachments": [],
    }

def build():
    rng = np.random.default_rng(2026)
    out = {"ast_nova": [], "ast_kai": [], "ast_lin": []}
    for d in range(1, DAYS + 1):
        out["ast_nova"].append(base_day(rng, "ast_nova", d))                     # nominal
        k = base_day(rng, "ast_kai", d)                                          # drifting from day 8
        if d >= 8:
            t = d - 7
            k["vitals"]["hr_rest_bpm"] = round(62 + 1.1 * t + rng.normal(0, 0.4), 1)
            k["vitals"]["sleep_h"] = round(7.0 - 0.30 * t + rng.normal(0, 0.1), 1)
            k["vitals"]["mood_1_10"] = int(max(2, round(7.5 - 0.35 * t)))
            k["vitals"]["temp_c"] = round(36.8 + 0.05 * t + rng.normal(0, 0.03), 2)
            k["pvt"]["median_rt_ms"] = round(250 + 8 * t + rng.normal(0, 4), 0)
        if d >= 11:
            k["compliance"]["ared_resistive_minutes"] = int(rng.integers(10, 25))
        if d >= 12:
            k["compliance"]["ration_intake_pct"] = int(rng.integers(66, 76))
        if d >= 13:                                                              # Solar Particle Event (solar flare) alert, about 2.2x the 1.4 mSv/day preset
            k["habitat"]["radiation_mSv_day"] = round(3.1 + rng.normal(0, 0.1), 2)
        out["ast_kai"].append(k)
        l = base_day(rng, "ast_lin", d)                                          # acute event on day 14
        if d == 14:
            l["vitals"].update(hr_rest_bpm=118.0, sbp_mmHg=168.0, dbp_mmHg=104.0, sleep_h=6.2, mood_1_10=5)
            l["pvt"] = None
            l["attachments"] = []
        out["ast_lin"].append(l)
    return out

if __name__ == "__main__":
    s = build()
    flat = [r for rs in s.values() for r in rs]
    json.dump(flat, open("seed.json", "w"), indent=1)
    print("seed.json:", len(flat), "records")
