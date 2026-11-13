# Hour-1 shared contract v1.0.0

Prototype, simulated data, not a medical device. Thresholds are team-chosen prototypes, not clinically validated.
Hazard context comes from the official NASA HRP "Hazards of Human Spaceflight" videos and pages (team extraction; see "Sources and what is simulated").

## Who reads what
| Track | Read |
|---|---|
| All | `contract.ts` (types), `rules.json` (numbers) |
| Backend | `status_engine.py` (reference; port to your stack and diff against `example_assessment_*.json`), `resources.py`, `seed.py` / `seed.json`, `schemas/*.schema.json` |
| Frontend | `contract.ts`, `example_assessment_ast_{nova,kai,lin}.json` (green / yellow / red demo states) |
| LLM planner | `schemas/CountermeasurePlanDraft.schema.json` (structured output), `exercise_menu.json`, `resources.validate_draft()` |
| Voice / n8n | `triage_protocol.json`, `EmergencyTriagePayload` in `contract.ts`, `validate_protocol.py` |

## Rule (per indicator, worst of three tests wins)
1. **Absolute limit**: works from day 1.
2. **Anchor Z**: Z = (today - mean of mission days 1-7) / max(SD of days 1-7, floor SD). Positive = worse. A Z-only RED needs the previous reading at Z >= 2.5 too, otherwise it is held at YELLOW with "re-measure".
3. **Drift**: 7-day OLS slope x 6 / sd_eff >= 2.5, and only if today's Z >= 1. YELLOW only.

Region colour = worst member. Overall = worst region. The emergency button is made prominent only on an **absolute** RED.

## Indicator and rule table (generated from rules.json)
| Indicator | Body region | Hazard (RIDGE) | Unit | Floor SD | YELLOW | RED |
|---|---|---|---|---|---|---|
| Resting heart rate | CHEST_CARDIO | G, R | bpm | 3 | Z >= 2.5 or drift >= 2.5 | Z >= 3.0 on 2 readings; > 100 |
| Systolic BP | CHEST_CARDIO | G | mmHg | 5 | Z >= 2.5 or drift >= 2.5; >= 135 or <= 100 | Z >= 3.0 on 2 readings; > 160 or < 90 |
| Diastolic BP | CHEST_CARDIO | G | mmHg | 4 | Z >= 2.5 or drift >= 2.5; >= 90 | Z >= 3.0 on 2 readings; > 100 |
| Fasting glucose | SYSTEMIC_IMMUNE_METABOLIC | I | mg/dL | 6 | Z >= 2.5 or drift >= 2.5; >= 105 or <= 70 | Z >= 3.0 on 2 readings; > 140 or < 65 |
| Core temperature | SYSTEMIC_IMMUNE_METABOLIC | E | C | 0.2 | Z >= 2.5 or drift >= 2.5; >= 37.6 | Z >= 3.0 on 2 readings; > 38.3 or < 35.5 |
| Sleep duration | HEAD_NEURO | I | h | 1 | Z >= 2.5 or drift >= 2.5; <= 5.5 | Z >= 3.0 on 2 readings; < 4 |
| Mood (1-10) | HEAD_NEURO | I | score | 1 | Z >= 2.5 or drift >= 2.5; <= 4 | Z >= 3.0 on 2 readings; < 3 |
| PVT reaction time | HEAD_NEURO | G, R, I | ms | 20 | Z >= 2.5 or drift >= 2.5; >= 350 | Z >= 3.0 on 2 readings; > 450 |
| Cabin CO2 | HEAD_NEURO | E | mmHg | - | >= 3 | > 4.5 |
| Radiation dose rate | SYSTEMIC_IMMUNE_METABOLIC | R | mSv/day | - | >= 1.5x expected (possible Solar Particle Event) | >= 3.0x expected |
| Resistive exercise | SPINE_BONE | G | min | - | < 50% of target for 3+ days (30 min in 0g) | never (chronic; capped at YELLOW) |
| Ration intake | SYSTEMIC_IMMUNE_METABOLIC | I, R | % | - | < 80% for 2+ days | < 60% for 3+ days |
| Immune risk (composite) | SYSTEMIC_IMMUNE_METABOLIC | E, R | flag | - | radiation >= 1.5x expected AND (temp or sleep Z >= 1.5 or yellow) | never (temp's own limit) |

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

| Environment | Expected mSv/day (preset) | 180-day dose | Chest X-ray equivalent (1 mSv = ~10) | Gravity | One-way delay |
|---|---|---|---|---|---|
| SPACE_STATION | 0.4 | 72 mSv | ~720 | 0 g | near-real-time |
| DEEP_SPACECRAFT | 1.4 | 252 mSv | ~2,520 | 0 g | 12.5 min (slider 3.0-22.3) |
| PLANETARY_BASE_MOON | 1.4 | 252 mSv | ~2,520 | 0.16 g | 1.28 s |
| PLANETARY_BASE_MARS | 1 | 180 mSv | ~1,800 | 0.38 g | 12.5 min (slider 3.0-22.3) |
| SURFACE_ROVER_MOON | 1.4 | 252 mSv | ~2,520 | 0.16 g | 1.28 s |
| SURFACE_ROVER_MARS | 1 | 180 mSv | ~1,800 | 0.38 g | 12.5 min (slider 3.0-22.3) |

Delay = distance_km / 299792. NASA quotes "up to about 20 minutes" one way for Mars; the app computes the real range, 3.0 to 22.3 minutes. The emergency button reads "Autonomous triage + queue packet" when delay is over 5 s.

## Countermeasure menu (exercise_menu.json)
| exercise_id | Name (NASA HRP countermeasure) | Kind | MET (assumption) | Region | Needs flight surgeon |
|---|---|---|---|---|---|
| resistive_moderate | ARED resistive loading, moderate (or bands) | EXERCISE | 3.5 | SPINE_BONE | no |
| resistive_vigorous | ARED resistive loading, vigorous | EXERCISE | 6 | SPINE_BONE | no |
| cycle_ergometer | Cycle ergometer (cardio), moderate | EXERCISE | 6.8 | CHEST_CARDIO | no |
| treadmill_harness | Treadmill with harness, brisk walk or jog | EXERCISE | 7 | CHEST_CARDIO | no |
| zero_t2_ergometer | Zero T2 treadmill-free compact ergometer and resistive session | EXERCISE | 5 | SPINE_BONE | no |
| walk_surface | Walking on the surface (partial gravity) | EXERCISE | 3.5 | CHEST_CARDIO | no |
| stretch_mobility | Stretching and mobility | EXERCISE | 2.3 | SPINE_BONE | no |
| thigh_cuff_bcomplex | Veno-constrictive thigh cuff and B-complex protocol (vision / SANS) | PROTOCOL | 1 | HEAD_NEURO | yes |
| compression_fluid_loading | Lower-body compression and fluid loading (before landing or surface work) | PROTOCOL | 1 | CHEST_CARDIO | yes |
| circadian_led_journaling | Circadian LED lighting and crew journaling session | PROTOCOL | 1.3 | HEAD_NEURO | no |
| microbial_swab_air_check | Habitat microbial surface swab and air check | PROTOCOL | 2 | SYSTEMIC_IMMUNE_METABOLIC | no |

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
