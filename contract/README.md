# Hour-1 shared contract v1.0.0

Prototype, simulated data, not a medical device. Thresholds are team-chosen prototypes, not clinically validated.

## Who reads what
| Track | Read |
|---|---|
| All | `contract.ts` (types), `rules.json` (numbers) |
| Backend | `status_engine.py` (reference; port to your stack and diff against `example_assessment_*.json`), `resources.py`, `seed.py` / `seed.json`, `schemas/*.schema.json` |
| Frontend | `contract.ts`, `example_assessment_ast_{nova,kai,lin}.json` (green / yellow / red demo states) |
| LLM planner | `schemas/CountermeasurePlanDraft.schema.json` (structured output), `exercise_menu.json` |
| Voice / n8n | `triage_protocol.json`, `EmergencyTriagePayload` in `contract.ts`, `validate_protocol.py` |

## Rule (per indicator, worst of three tests wins)
1. **Absolute limit**: works from day 1.
2. **Anchor Z**: Z = (today - mean of mission days 1-7) / max(SD of days 1-7, floor SD). Positive = worse. A Z-only RED needs the previous reading at Z >= 2.5 too, otherwise it is held at YELLOW with "re-measure".
3. **Drift**: 7-day OLS slope x 6 / sd_eff >= 2.5, and only if today's Z >= 1. YELLOW only.

Region colour = worst member. Overall = worst region. Emergency button is made prominent only on an **absolute** RED.

## Indicator and rule table (generated from rules.json)
| Indicator | Body region | Unit | Floor SD | YELLOW | RED |
|---|---|---|---|---|---|
| Resting heart rate | CHEST_CARDIO | bpm | 3 | Z >= 2.5 or drift >= 2.5 | Z >= 3.0 on 2 readings; > 100 |
| Systolic BP | CHEST_CARDIO | mmHg | 5 | Z >= 2.5 or drift >= 2.5; >= 135 or <= 100 | Z >= 3.0 on 2 readings; > 160 or < 90 |
| Diastolic BP | CHEST_CARDIO | mmHg | 4 | Z >= 2.5 or drift >= 2.5; >= 90 | Z >= 3.0 on 2 readings; > 100 |
| Fasting glucose | SYSTEMIC_IMMUNE_METABOLIC | mg/dL | 6 | Z >= 2.5 or drift >= 2.5; >= 105 or <= 70 | Z >= 3.0 on 2 readings; > 140 or < 65 |
| Core temperature | SYSTEMIC_IMMUNE_METABOLIC | C | 0.2 | Z >= 2.5 or drift >= 2.5; >= 37.6 | Z >= 3.0 on 2 readings; > 38.3 or < 35.5 |
| Sleep duration | HEAD_NEURO | h | 1 | Z >= 2.5 or drift >= 2.5; <= 5.5 | Z >= 3.0 on 2 readings; < 4 |
| Mood (1-10) | HEAD_NEURO | score | 1 | Z >= 2.5 or drift >= 2.5; <= 4 | Z >= 3.0 on 2 readings; < 3 |
| PVT reaction time | HEAD_NEURO | ms | 20 | Z >= 2.5 or drift >= 2.5; >= 350 | Z >= 3.0 on 2 readings; > 450 |
| Cabin CO2 | HEAD_NEURO | mmHg | - | >= 3 | > 4.5 |
| Radiation dose rate | SYSTEMIC_IMMUNE_METABOLIC | mSv/day | - | >= 1.5x expected | >= 3.0x expected |
| Resistive exercise | SPINE_BONE | min | - | < 50% of target for 3+ days (30 min in 0g) | never (chronic; capped at YELLOW) |
| Ration intake | SYSTEMIC_IMMUNE_METABOLIC | % | - | < 80% for 2+ days | < 60% for 3+ days |
| Immune risk (composite) | SYSTEMIC_IMMUNE_METABOLIC | flag | - | radiation >= 1.5x expected AND (temp or sleep Z >= 1.5 or yellow) | never (temp's own limit) |

Compliance rules cap at what is shown: resistive exercise never goes RED (bone loss is chronic). Streak = consecutive days with vitals logged AND resistive >= 50% of target AND ration >= 80%.

## Environments (rules.json)
SPACE_STATION, DEEP_SPACECRAFT, PLANETARY_BASE_MOON, PLANETARY_BASE_MARS, SURFACE_ROVER_MOON, SURFACE_ROVER_MARS.
Delay = distance_km / 299792.458: Moon 1.28 s, Mars 3.04 to 22.29 min one-way. Emergency button is "Autonomous triage + queue packet" when delay > 5 s.

## Resource math (resources.py)
extra_kcal = (MET - 1) x mass_kg x hours; water = 1.15 mL/kcal (team estimate); CO2 = 0.34 g/kcal (derived from respiratory quotient 0.85).
Example: 80 kg, 30 min vigorous resistive (6 MET) -> 200 kcal, 230 mL water, 68 g CO2.

## Still unverified (do not present as sourced)
- All vitals/compliance thresholds: team prototypes. Do NOT say they come from NASA-STD-3001 unless you read them there.
- Mars-surface radiation 0.64 mSv/day: from memory of MSL/RAD surface data, confirm.
- Exercise MET values: approximate Compendium figures.
- Triage scripts are generic first-aid placeholders and need clinician review. The medication step is intentionally left to the onboard checklist.
- Sourced: cabin CO2 (NASA OCHMO-TB-004), cruise radiation 1.75 mSv/day (arXiv 1503.06631), Moon 1.37 and ISS 0.73 mSv/day (Chang'e-4 LND paper).

## Verified by running
`python3 test_engine.py` (all pass), `python3 validate_protocol.py`, `python3 resources.py`, `npx -p typescript tsc -p tsconfig.json`; seed.json and engine output validate against the generated JSON Schemas.
