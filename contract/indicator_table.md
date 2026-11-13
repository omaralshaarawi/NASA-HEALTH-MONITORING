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
