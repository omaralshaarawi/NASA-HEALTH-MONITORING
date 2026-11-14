"""Regenerates indicator_table.md and README.md from rules.json and exercise_menu.json."""
import json
R = json.load(open("rules.json")); G = R["global"]; M = json.load(open("exercise_menu.json"))
C = R["constants"]["c_km_s"]
region_of = {m: r for r, ms in R["regions"].items() for m in ms}
# RIDGE = Radiation, Isolation, Distance, Gravity, hostile/closed Environments. Team mapping from the NASA HRP hazard pages.
RIDGE = {"hr_rest": "G, R", "sbp": "G", "dbp": "G", "glucose": "I", "temp": "E", "sleep": "I", "mood": "I", "pvt": "G, R, I",
         "cabin_co2": "E", "radiation": "R", "resistive": "G", "ration": "I, R", "immune_risk": "E, R"}

def lim(a, kind):
    parts = []
    if kind == "y":
        if "yellow_hi" in a: parts.append(f">= {a['yellow_hi']:g}")
        if "yellow_lo" in a: parts.append(f"<= {a['yellow_lo']:g}")
    else:
        if "red_hi" in a: parts.append(f"> {a['red_hi']:g}")
        if "red_lo" in a: parts.append(f"< {a['red_lo']:g}")
    return " or ".join(parts)

rows = []
for i in R["indicators"]:
    a, k = i["abs"], i["kind"]
    if k == "anchor":
        y = f"Z >= {G['z_yellow']} or drift >= {G['drift_yellow']}" + (f"; {lim(a,'y')}" if lim(a,'y') else "")
        r = f"Z >= {G['z_red']} on 2 readings" + (f"; {lim(a,'r')}" if lim(a,'r') else "")
        fl = f"{i['floor_sd']:g}"
    elif k == "limit_only": y, r, fl = lim(a, 'y'), lim(a, 'r'), "-"
    elif k == "ratio": y, r, fl = f">= {R['radiation']['ratio_yellow']}x expected (possible Solar Particle Event)", f">= {R['radiation']['ratio_red']}x expected", "-"
    elif i["id"] == "resistive": y, r, fl = "< 50% of target for 3+ days (30 min in 0g)", "never (chronic; capped at YELLOW)", "-"
    elif k == "compliance": y, r, fl = "< 80% for 2+ days", "< 60% for 3+ days", "-"
    else: y, r, fl = "radiation >= 1.5x expected AND (temp or sleep Z >= 1.5 or yellow)", "never (temp's own limit)", "-"
    rows.append(f"| {i['label']} | {region_of[i['id']]} | {RIDGE[i['id']]} | {i['unit']} | {fl} | {y} | {r} |")
table = ("| Indicator | Body region | Hazard (RIDGE) | Unit | Floor SD | YELLOW | RED |\n|---|---|---|---|---|---|---|\n" + "\n".join(rows))
open("indicator_table.md", "w").write(table + "\n")

# radiation / X-ray table
rad = ["| Environment | Expected mSv/day (preset) | 180-day dose | Chest X-ray equivalent (1 mSv = ~10) | Gravity | One-way delay |", "|---|---|---|---|---|---|"]
for eid, e in R["environments"].items():
    d = e["distance_km"]; dist = d.get("fixed", d.get("default")); delay = dist / C
    dl = "near-real-time" if dist < 1000 else (f"{delay:.2f} s" if delay < 60 else f"{delay/60:.1f} min (slider {d['min']/C/60:.1f}-{d['max']/C/60:.1f})")
    dose = e["radiation_expected_mSv_day"] * 180
    rad.append(f"| {eid} | {e['radiation_expected_mSv_day']:g} | {dose:.0f} mSv | ~{dose*10:,.0f} | {e['gravity_g']:g} g | {dl} |")
radtable = "\n".join(rad)

# exercise menu
menu = ["| exercise_id | Name (NASA HRP countermeasure) | Kind | MET (assumption) | Region | Needs flight surgeon |", "|---|---|---|---|---|---|"]
for it in M["items"]:
    menu.append(f"| {it['exercise_id']} | {it['label']} | {it['kind']} | {it['met']:g} | {it['target_system']} | {'yes' if it['requires_flight_surgeon_direction'] else 'no'} |")
menutable = "\n".join(menu)

readme = f"""# Hour-1 shared contract v1.0.0

Prototype, simulated data, not a medical device. Thresholds are team-chosen prototypes, not clinically validated.
Hazard context comes from the official NASA HRP "Hazards of Human Spaceflight" videos and pages (team extraction; see "Sources and what is simulated").

## Who reads what
| Track | Read |
|---|---|
| All | `contract.ts` (types), `rules.json` (numbers) |
| Backend | `status_engine.py` (reference; port to your stack and diff against `example_assessment_*.json`), `resources.py`, `seed.py` / `seed.json`, `schemas/*.schema.json` |
| Frontend | `contract.ts`, `example_assessment_ast_{{nova,kai,lin}}.json` (green / yellow / red demo states) |
| LLM planner | `schemas/CountermeasurePlanDraft.schema.json` (structured output), `exercise_menu.json`, `resources.validate_draft()` |
| Voice / n8n | `triage_protocol.json`, `EmergencyTriagePayload` in `contract.ts`, `validate_protocol.py` |

## Rule (per indicator, worst of three tests wins)
1. **Absolute limit**: works from day 1.
2. **Anchor Z**: Z = (today - mean of mission days 1-7) / max(SD of days 1-7, floor SD). Positive = worse. A Z-only RED needs the previous reading at Z >= {G['z_yellow']} too, otherwise it is held at YELLOW with "re-measure".
3. **Drift**: 7-day OLS slope x 6 / sd_eff >= {G['drift_yellow']}, and only if today's Z >= 1. YELLOW only.

Region colour = worst member. Overall = worst region. The emergency button is made prominent only on an **absolute** RED.

## Indicator and rule table (generated from rules.json)
{table}

Compliance rules cap at what is shown: resistive exercise never goes RED (bone loss is chronic). Streak = consecutive days with vitals logged AND resistive >= 50% of target AND ration >= 80%.

## Hazards of human spaceflight (RIDGE) and where the app covers them
Facts below are as extracted by the team from the NASA HRP videos and pages.

| Hazard | Official risks and facts | App features |
|---|---|---|
| **R**adiation | Dose in mSv, 1 mSv is about 10 chest X-rays; mission doses 50 to 2,000 mSv; ISS crews get about 10x Earth's dose; sources: Van Allen belt particles, Solar Particle Events (solar flares, 11-year cycle), Galactic Cosmic Rays. Symptoms: nausea, vomiting, anorexia, fatigue, CNS effects, cataracts, cardiac disease, cancer risk. Countermeasures: dosimetry alerts, storm sheltering. | `radiation` ratio rule and Solar Particle Event label; `immune_risk` composite; `ration`, `pvt`, `mood` linked to symptoms |
| **I**solation and confinement | Mood, cognition, morale and sleep decline; circadian misalignment (a Martian sol is 24 h 38 min); noisy small habitats, workload swings, limited food variety. NASA tracks lighting, movement and sleep (wrist actigraphy); LED lighting, fresh crops, crew journaling. | `sleep`, `mood`, `pvt`, `ration`; `circadian_led_journaling` |
| **D**istance from Earth | ISS about 250 miles, Moon 238,855 miles (~3 days), Mars averages about 140 million miles; delay up to ~20 min one way. No resupply or quick evacuation, so crews manage emergencies autonomously (decision support, onboard ultrasound). | delay banner (d / c), `AUTONOMOUS` emergency mode, store-and-forward packet, triage protocol, ultrasound attachments |
| **G**ravity fields | 1 g, 0 g, Mars 3/8 g. Spine and hips lose about 1 to 1.5% mineral density per month in microgravity; spine can lengthen up to ~3%; fluid shifts toward the head; vision changes (SANS) affect about 70% of ISS astronauts; heart becomes more spherical, risk of clots and arrhythmia; low BP and fainting after landing. | `resistive` rule and `SPINE_BONE` region; `hr_rest`, `sbp`, `dbp`; `pvt` (sensorimotor); ocular and cardiac ultrasound attachments; exercise menu |
| hostile/closed **E**nvironments | Six habitability factors: temperature, pressure, light, noise, air, livable space. Microbes spread between crewmates; stress, radiation and poor sleep alter immunity (rashes, infections, dormant viruses waking). Close the loop: recycle CO2 and urine into air and water. | `cabin_co2` (Air), `temp`, `immune_risk`; microbial swab item; Simulated Closed-Loop ECLSS Estimates (below) |

## Environments (rules.json), radiation presets and delays
Radiation presets are **scenario assumptions derived from NASA's 50 to 2,000 mSv mission range**, not measurements: LEO 50 to 100 mSv over about 6 months is 0.27 to 0.55 mSv/day (preset 0.4); multi-year deep-space missions of 500 to 2,000 mSv are roughly 1.0 to 1.8 mSv/day (preset 1.4 deep space and Moon, 1.0 Mars surface).

{radtable}

Delay = distance_km / {C:g}. NASA quotes "up to about 20 minutes" one way for Mars; the app computes the real range, 3.0 to 22.3 minutes. The emergency button reads "Autonomous triage + queue packet" when delay is over {R['constants']['autonomous_delay_s']} s.

## Countermeasure menu (exercise_menu.json)
{menutable}

PROTOCOL items carry no doses or device settings in this app. Where "needs flight surgeon" is yes, `resources.validate_draft()` rejects a plan unless `escalate_to_flight_surgeon` is true.

## Simulated Closed-Loop ECLSS Estimates (resources.py)
Context (NASA HRP Hostile/Closed Environments video, as transcribed by the team): "Close the loop - Recycling all possible resources, including an astronaut's carbon dioxide and urine, into tomorrow's Air and Water."
extra_kcal = (MET - 1) x mass_kg x hours; water = 1.15 mL/kcal; CO2 = 0.34 g/kcal (derived from respiratory quotient 0.85; the quotient itself is dimensionless).
Example: 80 kg, 30 min vigorous resistive (6 MET) -> 200 kcal, 230 mL water, 68 g CO2.
The `ResourceBlock.label` in `contract.ts` is the fixed literal "decision-support estimate"; show it in the UI as "Simulated closed-loop ECLSS estimate".

## Sources and what is simulated
- Hazard facts, units and countermeasure names: NASA HRP Hazards videos and the /hrp/hazards, /hrp/risks and /hrp/ongoing-hrp-research pages, **as extracted by the team. Claude has not seen those pages; re-check the exact wording of every quote and figure before the pitch.**
- Simulated or team-chosen: every vital and compliance threshold, floor SD, the per-environment radiation presets, cabin CO2 limits and defaults, MET values, water and CO2 per kcal, seed data, triage scripts.
- Do not say the thresholds come from NASA-STD-3001 unless you read them there.
- Triage scripts are generic first-aid placeholders and need clinician review. The medication step is left to the onboard checklist.

## Verified by running
`python3 test_engine.py`, `python3 validate_protocol.py`, `python3 resources.py`, `npx -p typescript tsc -p tsconfig.json`. seed.json and engine output validate against the generated JSON Schemas. `contract.ts` and `schemas/*.schema.json` are unchanged since the first commit.
"""
open("README.md", "w").write(readme)
print(radtable)
