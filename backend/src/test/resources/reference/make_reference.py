"""
Regenerates python_reference.json: outputs of contract/status_engine.py that the Java port must reproduce exactly.
Run only if rules.json, seed.json or status_engine.py change:   python3 make_reference.py   (needs numpy)

Cases: every seeded astronaut in all six habitats on day 14, on earlier days in DEEP_SPACECRAFT (inside and just
after the anchor window), plus synthetic series for paths the seed never reaches (gaps, confirmed Z-RED, single spike,
absolute lows, cabin CO2 RED, ration RED).
"""
import copy, json, sys
from pathlib import Path

CONTRACT = Path(__file__).resolve().parents[5] / "contract"
sys.path.insert(0, str(CONTRACT))
from status_engine import RULES, to_assessment  # noqa: E402

seed = {}
for r in json.loads((CONTRACT / "seed.json").read_text()):
    seed.setdefault(r["astronaut_id"], []).append(r)

cases = []
for a, rs in seed.items():
    for env in RULES["environments"]:
        cases.append(dict(name=f"{a} day 14 {env}", astronaut_id=a, day=14, env_id=env))
    for day in (1, 5, 7, 8, 9, 11, 12, 13):
        cases.append(dict(name=f"{a} day {day} DEEP_SPACECRAFT", astronaut_id=a, day=day, env_id="DEEP_SPACECRAFT"))
for c in cases:
    c["expected"] = to_assessment(seed[c["astronaut_id"]][:c["day"]], c["env_id"])


def synthetic(name, base, env, edit):
    rs = copy.deepcopy(seed[base])
    edit(rs)
    cases.append(dict(name=name, env_id=env, records=rs, expected=to_assessment(rs, env)))


def gaps(rs):
    rs[2]["vitals"]["hr_rest_bpm"] = None
    rs[9]["vitals"]["hr_rest_bpm"] = None
    rs[13]["vitals"]["temp_c"] = None
    rs[11]["pvt"] = None
    del rs[12]["vitals"]["sleep_h"]


def confirmed_red(rs):
    rs[12]["pvt"]["median_rt_ms"] = 320.0
    rs[13]["pvt"]["median_rt_ms"] = 322.0


def single_spike(rs):
    rs[13]["pvt"]["median_rt_ms"] = 320.0


def habitat_and_ration(rs):
    for r in rs[-3:]:
        r["compliance"]["ration_intake_pct"] = 50
    rs[13]["habitat"]["cabin_co2_mmHg"] = 4.6
    rs[13]["compliance"]["morning_vitals_logged"] = False


def absolute_lows(rs):
    rs[13]["vitals"].update(glucose_mg_dl=62.0, sbp_mmHg=95.0, temp_c=38.0, sleep_h=3.5, mood_1_10=2)


synthetic("synthetic: gaps in the series", "ast_nova", "DEEP_SPACECRAFT", gaps)
synthetic("synthetic: Z-RED confirmed on two readings", "ast_nova", "DEEP_SPACECRAFT", confirmed_red)
synthetic("synthetic: single spike held at YELLOW", "ast_nova", "DEEP_SPACECRAFT", single_spike)
synthetic("synthetic: cabin CO2 RED and ration RED", "ast_nova", "PLANETARY_BASE_MOON", habitat_and_ration)
synthetic("synthetic: absolute low limits", "ast_kai", "SPACE_STATION", absolute_lows)

out = Path(__file__).with_name("python_reference.json")
out.write_text("[\n" + ",\n".join(json.dumps(c, separators=(",", ":")) for c in cases) + "\n]\n")
print(len(cases), "cases ->", out.name, f"({out.stat().st_size // 1024} KB)")
