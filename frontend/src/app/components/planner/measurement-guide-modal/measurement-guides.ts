import { IndicatorId, Status } from '../../../models/contract';
import rules from '../../../models/rules.json';

// Text for the three guide cards. Background facts follow NASA Human Research Program (HRP)
// public material as collected by the team: check the exact wording before quoting it in the pitch.
// Limits are NOT written here; they are read from rules.json below.

export interface MeasurementGuide {
  instrument: string;
  how: string[];
  why: string[];
  // one headline fact for the "why" card
  fact?: { value: string; caption: string };
  act: string[];
  hazard: string;
}

const BLOOD_PRESSURE: MeasurementGuide = {
  instrument: 'Automatic upper-arm cuff',
  how: [
    'Stop moving and rest for a few minutes. Strap in loosely so you are not holding yourself in place with your muscles.',
    'Wrap the cuff on the bare upper arm, level with the heart.',
    'Let the arm float relaxed against your side. A tensed or raised arm changes the reading.',
    'Stay silent and still while the cuff inflates. Take a second reading a minute later and log that one.',
  ],
  why: [
    'Without gravity, body fluid moves from the legs towards the chest and head (the cephalad fluid shift). The body responds by reducing blood volume.',
    'Over months the heart and blood vessels adapt to having no gravity to work against. Back in a gravity field, on the Moon, Mars or Earth, blood pressure can drop on standing and cause fainting.',
  ],
  fact: { value: 'Head-ward', caption: 'fluid shift in the first days of weightlessness' },
  act: [
    'YELLOW: re-measure after resting, and check today\'s sleep, hydration and workload.',
    'High reading with chest pain, severe headache or breathlessness: start the emergency triage checklist.',
    'Low reading with dizziness: stop work, rest with a crewmate nearby and report to the flight surgeon.',
  ],
  hazard: 'Gravity fields',
};

export const GUIDES: Record<IndicatorId, MeasurementGuide> = {
  hr_rest: {
    instrument: 'Fingertip pulse oximeter or chest strap',
    how: [
      'Measure soon after waking, before exercise, food or a demanding task.',
      'Stop moving for a few minutes. In 0 g there is no "sitting still", so strap in loosely and let your arms float.',
      'Clip the sensor on a warm finger and wait until the number has been steady for about half a minute.',
      'Log the steady value, not the first number that appears.',
    ],
    why: [
      'Body fluid shifts towards the chest and head in weightlessness, the heart fills differently and blood volume falls. With no gravity to pump against, the heart and vessels gradually decondition.',
      'Resting heart rate is an early warning. It rises with infection, dehydration, short sleep and stress, often before you feel anything.',
    ],
    fact: { value: 'Early sign', caption: 'rises before symptoms when the body is under load' },
    act: [
      'YELLOW: re-measure after a short rest. Look at sleep, temperature and hydration for a cause.',
      'RED, or a fast pulse with chest pain, breathlessness or faintness: start the emergency triage checklist.',
      'A steady climb over several days is worth reporting even if each day looks acceptable.',
    ],
    hazard: 'Gravity fields',
  },

  sbp: BLOOD_PRESSURE,
  dbp: BLOOD_PRESSURE,

  glucose: {
    instrument: 'Finger-stick glucose meter (fasting)',
    how: [
      'Measure before the first meal of the day.',
      'Clean and dry the fingertip, then use the lancet on the side of the finger.',
      'In 0 g the blood forms a small sphere that stays on the skin. Bring the test strip to the drop; do not shake your hand.',
      'Put the lancet and strip straight into the sharps container so nothing floats away.',
    ],
    why: [
      'Muscles that are not loaded take up less sugar, so the body handles glucose less efficiently during long periods without weight-bearing.',
      'Shifted sleep, stress hormones and a changed diet push in the same direction.',
    ],
    fact: { value: 'Unloaded muscle', caption: 'uses less glucose, so levels are harder to control' },
    act: [
      'Low reading with shaking, sweating or confusion: eat fast carbohydrate from the ration, re-check, and tell a crewmate.',
      'Repeated high fasting readings: report to the flight surgeon and keep up the resistive exercise.',
      'One odd value: re-measure with a fresh strip before acting on it.',
    ],
    hazard: 'Gravity fields · Hostile and closed environments',
  },

  temp: {
    instrument: 'Ear (tympanic) thermometer',
    how: [
      'Measure at the same time each day, and not straight after exercise.',
      'Fit a clean probe cover. Gently pull the ear up and back to straighten the canal.',
      'Seat the probe snugly, press the button and hold still until the beep.',
      'Use the same ear every day so readings are comparable.',
    ],
    why: [
      'Warm air does not rise without gravity and sweat does not run off, so the body loses heat less easily.',
      'Spaceflight also changes the immune system. Stress hormones, radiation and poor sleep weaken defences, and dormant viruses can become active again. A rise in temperature may be the first sign of infection.',
    ],
    fact: { value: 'Dormant viruses', caption: 'can reactivate when immunity is altered in flight' },
    act: [
      'YELLOW: rest, drink, and re-measure later the same day.',
      'RED, or fever with other symptoms: follow the onboard medical checklist and report to the flight surgeon.',
      'Clean shared surfaces and equipment; in a closed habitat an infection spreads quickly.',
    ],
    hazard: 'Hostile and closed environments',
  },

  sleep: {
    instrument: 'Wrist actigraph and sleep log',
    how: [
      'Wear the actigraph on the wrist through the whole sleep period.',
      'On waking, log the hours you actually slept, not the hours in the sleep station.',
      'Note anything that woke you: noise, alarms, temperature, workload.',
    ],
    why: [
      'In low Earth orbit the Sun rises and sets about every 90 minutes; in deep space there is no day at all. The body clock loses its main cue.',
      'Noise, a shifting schedule and a high workload shorten sleep further. Lost sleep slows reactions and weakens immunity.',
    ],
    fact: { value: '16 sunrises', caption: 'a day on the space station' },
    act: [
      'YELLOW: use the circadian lighting schedule, dim lights and screens before the sleep period, and protect the next sleep block.',
      'Two short nights: avoid safety-critical tasks until the reaction test is back to normal.',
      'A run of short nights: report to the flight surgeon.',
    ],
    hazard: 'Isolation and confinement',
  },

  mood: {
    instrument: 'Daily self-rating, 1 to 10',
    how: [
      'Rate your mood once a day at the same time. 10 is the best you have felt on the mission.',
      'Answer for today as a whole, not the last five minutes.',
      'Be honest. The score is compared with your own first week, not with anyone else.',
    ],
    why: [
      'A small crew lives for months in a confined space, far from family, with the same faces and the same routine.',
      'The farther from Earth, the longer every message takes. Support from home becomes slower and the crew has to rely on itself.',
    ],
    fact: { value: 'Months', caption: 'in a small, closed habitat with the same few people' },
    act: [
      'YELLOW: use the journal, talk to a crewmate, and keep sleep and exercise on schedule.',
      'A low score for several days, or RED: tell the flight surgeon. This is expected on long missions and there is support for it.',
      'Thoughts of harming yourself or others: tell the commander now.',
    ],
    hazard: 'Isolation and confinement · Distance from Earth',
  },

  pvt: {
    instrument: '30-second reaction test in this app',
    how: [
      'Strap in and hold the tablet steady with your other hand.',
      'Watch the box. Tap the moment the counter appears.',
      'Do not guess ahead. Early taps do not count and slow reactions are logged as lapses.',
      'Take the test at the same time each day.',
    ],
    why: [
      'Reaction time slows with lost sleep, high cabin carbon dioxide and heavy workload, often before you feel tired.',
      'NASA uses a short reaction test of this kind on the space station so crews can check their own alertness.',
    ],
    fact: { value: 'Before you feel it', caption: 'reaction time shows fatigue earlier than you notice' },
    act: [
      'YELLOW: take a rest break and repeat the test before any safety-critical task.',
      'RED: hand over or postpone critical tasks such as piloting, robotics or a spacewalk.',
      'Slow for several days: check sleep and cabin CO₂, and report it.',
    ],
    hazard: 'Isolation and confinement',
  },

  cabin_co2: {
    instrument: 'Habitat CO₂ sensor (automatic)',
    how: [
      'The habitat reports this value by itself. You do not measure it.',
      'If you have a headache, check the reading for the module you are in.',
      'A personal monitor near the face shows what you are actually breathing.',
    ],
    why: [
      'Air does not mix by itself without gravity. Where ventilation is weak, exhaled carbon dioxide collects around the head.',
      'Raised CO₂ brings headaches, poor concentration and worse sleep.',
    ],
    act: [
      'YELLOW: move to a well-ventilated spot and check that the fans and vents are clear.',
      'RED: follow the habitat atmosphere procedure and check the CO₂ removal system.',
    ],
    hazard: 'Hostile and closed environments',
  },

  radiation: {
    instrument: 'Habitat dosimeter (automatic)',
    how: [
      'The dosimeter reports the dose rate by itself.',
      'The app compares it with the rate expected for this habitat, not with a fixed number.',
      'Wear your personal dosimeter whenever the procedure says so.',
    ],
    why: [
      'Beyond the protection of Earth\'s magnetic field, crews are exposed to galactic cosmic rays all the time and to solar particle events during solar storms.',
      'Radiation raises long-term cancer risk and, in high doses, affects the nervous system, the heart and the immune system.',
    ],
    act: [
      'YELLOW: a solar particle event is possible. Limit time in thinly shielded areas.',
      'RED: move to the best-shielded part of the habitat and follow the radiation procedure.',
    ],
    hazard: 'Space radiation',
  },

  resistive: {
    instrument: 'Exercise log (minutes)',
    how: [
      'Log the minutes of resistive exercise actually done today.',
      'A day with no log counts as zero.',
    ],
    why: [
      'Bones that carry no weight lose mineral, and muscles shrink with them. Loading the skeleton is the main protection.',
    ],
    fact: { value: '1 to 1.5%', caption: 'of bone mineral lost per month in weight-bearing bones if nothing is done' },
    act: [
      'YELLOW: do the planned resistive session today and do not skip the next one.',
      'If equipment or schedule is the reason, tell the flight surgeon so the plan can change.',
    ],
    hazard: 'Gravity fields',
  },

  ration: {
    instrument: 'Meal log (% of ration eaten)',
    how: [
      'Log how much of today\'s planned ration you ate.',
      'A day with no log counts as zero.',
    ],
    why: [
      'Appetite often drops in flight and food tastes different when the head is congested by the fluid shift.',
      'Eating too little speeds up bone and muscle loss and weakens the immune system.',
    ],
    act: [
      'YELLOW: finish every planned meal today and keep drinking.',
      'RED: several days of very low intake need a flight surgeon review.',
    ],
    hazard: 'Hostile and closed environments',
  },

  immune_risk: {
    instrument: 'Computed from radiation, temperature and sleep',
    how: [
      'Nothing to measure. The app raises this flag when radiation is elevated and temperature or sleep is also off baseline.',
    ],
    why: [
      'Stress hormones, radiation and poor sleep all alter the immune system, and the effects add up.',
      'In a sealed habitat microbes are shared by the whole crew and can change their behaviour in flight.',
    ],
    fact: { value: 'Dormant viruses', caption: 'can reactivate when immunity is altered in flight' },
    act: [
      'Protect sleep, eat the full ration and keep up hygiene.',
      'Run the habitat surface swab and air check.',
      'Watch temperature closely and report new symptoms.',
    ],
    hazard: 'Hostile and closed environments · Space radiation',
  },
};

export interface Limit {
  status: Exclude<Status, 'GREEN'>;
  text: string;
}

interface IndicatorRule {
  id: string;
  unit: string;
  kind: string;
  abs?: Record<string, number>;
}

// The limits the engine applies to this indicator, in plain words, straight from rules.json.
export function limitsFor(id: IndicatorId): Limit[] {
  const rule = (rules.indicators as IndicatorRule[]).find((r) => r.id === id);
  if (!rule) {
    return [];
  }
  const g = rules.global;
  const abs = rule.abs ?? {};
  const unit = rule.unit === 'C' ? '°C' : rule.unit;
  const limits: Limit[] = [];

  if (abs['yellow_hi'] !== undefined) limits.push({ status: 'YELLOW', text: `${abs['yellow_hi']} ${unit} or higher` });
  if (abs['yellow_lo'] !== undefined) limits.push({ status: 'YELLOW', text: `${abs['yellow_lo']} ${unit} or lower` });
  if (rule.kind === 'anchor') {
    limits.push({
      status: 'YELLOW',
      text: `${g.z_yellow} standard deviations away from your own days 1-${g.anchor_days} baseline, or a steady ${g.drift_window_days}-day drift`,
    });
  }
  if (abs['red_hi'] !== undefined) limits.push({ status: 'RED', text: `above ${abs['red_hi']} ${unit}` });
  if (abs['red_lo'] !== undefined) limits.push({ status: 'RED', text: `below ${abs['red_lo']} ${unit}` });
  if (rule.kind === 'anchor') {
    limits.push({ status: 'RED', text: `${g.z_red} standard deviations from baseline on two readings in a row` });
  }

  if (id === 'radiation') {
    limits.push({ status: 'YELLOW', text: `${rules.radiation.ratio_yellow}x the rate expected for this habitat` });
    limits.push({ status: 'RED', text: `${rules.radiation.ratio_red}x the expected rate` });
  }
  if (id === 'resistive') {
    const r = rules.compliance.resistive;
    limits.push({
      status: 'YELLOW',
      text: `under ${r.low_fraction_of_target * 100}% of the daily target for ${r.low_days_yellow} days in a row`,
    });
  }
  if (id === 'ration') {
    const r = rules.compliance.ration;
    limits.push({ status: 'YELLOW', text: `under ${r.low_pct}% for ${r.low_days_yellow} days in a row` });
    limits.push({ status: 'RED', text: `under ${r.very_low_pct}% for ${r.very_low_days_red} days in a row` });
  }
  if (id === 'immune_risk') {
    const c = rules.composites.immune_risk;
    limits.push({
      status: 'YELLOW',
      text: `radiation at ${c.radiation_ratio_min}x expected or more, together with temperature or sleep off baseline`,
    });
  }
  return limits;
}
