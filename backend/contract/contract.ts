/**
 * HOUR-1 SHARED CONTRACT v1.0.0
 * Astronaut self-assessment tool, NASA Space Apps 2026 (Health Monitoring Software).
 *
 * Source of truth for numbers:   rules.json  (thresholds, floors, environment presets, resource constants)
 *                                exercise_menu.json (the only exercises the LLM may pick)
 * Reference implementation:      status_engine.py, resources.py, seed.json
 *
 * Rules of the road
 *  - Statuses, Z-scores, slopes, delays and resource numbers are computed by CODE. The LLM only narrates
 *    and picks exercises from the menu. Never let the LLM produce a number that ends up in a report.
 *  - All data is SIMULATED. Every response carries DISCLAIMER.
 */

export const SCHEMA_VERSION = "1.0.0";
export const DISCLAIMER =
  "Prototype decision-support tool using simulated data. Not a medical device and not a diagnosis. Follow flight-surgeon and onboard medical procedures.";

// ------------------------------------------------------------------ enums
export type Status = "GREEN" | "YELLOW" | "RED";

/** The brief's four environments, split by celestial body where gravity/radiation/delay differ. */
export type EnvironmentType = "SPACE_STATION" | "DEEP_SPACECRAFT" | "PLANETARY_BASE" | "SURFACE_ROVER";
export type Body = "EARTH_ORBIT" | "DEEP_SPACE" | "MOON" | "MARS";
export type EnvironmentId =
  | "SPACE_STATION"
  | "DEEP_SPACECRAFT"
  | "PLANETARY_BASE_MOON"
  | "PLANETARY_BASE_MARS"
  | "SURFACE_ROVER_MOON"
  | "SURFACE_ROVER_MARS";

export type RegionId = "HEAD_NEURO" | "CHEST_CARDIO" | "SPINE_BONE" | "SYSTEMIC_IMMUNE_METABOLIC";

export type IndicatorId =
  | "hr_rest" | "sbp" | "dbp" | "glucose" | "temp" | "sleep" | "mood" | "pvt"   // anchor-baseline indicators
  | "cabin_co2" | "radiation"                                                    // habitat telemetry
  | "resistive" | "ration"                                                       // daily compliance
  | "immune_risk";                                                               // composite

export type DataSource = "SIMULATED" | "MANUAL" | "SENSOR";

// ------------------------------------------------------------------ 1. EnvironmentProfile
/** Loaded from rules.json "environments", then enriched with live values. */
export interface EnvironmentProfile {
  id: EnvironmentId;
  label: string;
  environment: EnvironmentType;
  body: Body;
  /** 0 | 0.16 | 0.38 */
  gravity_g: number;
  /** Current Earth distance. Fixed for LEO/Moon; slider 54.6e6..401e6 km for Mars (one-way 3.0..22.3 min). */
  distance_km: number;
  /** Present only when distance is variable (Mars). */
  distance_range_km?: { min: number; max: number };
  /** COMPUTED: distance_km / 299792.458. Never typed in. */
  one_way_delay_s: number;
  /** "Near-real-time relay" for LEO, otherwise "Light-time delay". Show the number only when not LEO. */
  comm_label: string;
  /** AUTONOMOUS when one_way_delay_s > 5 (rules.json constants.autonomous_delay_s). Drives the emergency button. */
  comm_mode: "LIVE" | "AUTONOMOUS";
  /** Background rate for this place; the radiation rule compares telemetry against it (ratio >= 1.5 yellow, >= 3 red). */
  radiation_expected_mSv_day: number;
  /** Current passive telemetry (preset default, overridable to simulate a solar particle event). */
  radiation_mSv_day: number;
  cabin_co2_mmHg: number;
  /** Daily resistive-exercise target. 60 in microgravity, 30 in partial gravity. */
  resistive_target_min: number;
}

// ------------------------------------------------------------------ 2. DailyMeasurementAndCompliance (INPUT)
export interface DailyMeasurementAndCompliance {
  astronaut_id: string;
  /** 1-based mission day. Days 1-7 form the personal anchor window. */
  mission_day: number;
  /** ISO date, e.g. "2026-11-14" */
  date_iso?: string;
  environment_id: EnvironmentId;
  source: DataSource;

  /** All optional: a missing value is "not measured today", never zero. */
  vitals: {
    hr_rest_bpm?: number | null;
    sbp_mmHg?: number | null;
    dbp_mmHg?: number | null;
    glucose_mg_dl?: number | null;   // fasting
    temp_c?: number | null;          // tympanic
    sleep_h?: number | null;
    mood_1_10?: number | null;       // integer 1..10, higher is better
  };

  /** Live 30 s browser psychomotor vigilance test. null when not done today. */
  pvt?: {
    median_rt_ms: number;
    lapses_gt500ms: number;
    n_trials: number;
    source: "BROWSER_30S";
  } | null;

  /** Passive habitat telemetry. */
  habitat: {
    radiation_mSv_day: number;
    cabin_co2_mmHg: number;
  };

  /** Daily routine card. */
  compliance: {
    morning_vitals_logged: boolean;
    /** Minutes of resistive exercise (ARED on the station; any resistive device elsewhere). Missing log counts as 0. */
    ared_resistive_minutes: number;
    /** 0..100+ of planned ration eaten. Missing log counts as 0. */
    ration_intake_pct: number;
  };

  /** Store-and-forward only. No AI image interpretation anywhere in this app. */
  attachments?: Array<{
    kind: "OCULAR_ULTRASOUND" | "CARDIAC_ULTRASOUND" | "ECG";
    ref: string;                 // file id or URL
    captured_at_iso: string;
    note?: string;
    simulated: true;
  }>;
}

// ------------------------------------------------------------------ 3. AssessmentResponse (OUTPUT)
export interface IndicatorAssessment {
  id: IndicatorId;
  label: string;
  unit: string;
  region: RegionId;
  value: number | null;
  status: Status;
  /** Human-readable, deterministic reasons, e.g. "Z=2.9 vs own normal 61.2". Show these to the user as-is. */
  reasons: string[];
  /** Personal anchor from mission days 1-7. null until 5+ anchor readings exist and day > 7. */
  anchor: { mean: number; sd_raw: number; sd_eff: number; n: number } | null;
  /** (today - anchor mean) / sd_eff, signed so positive = worse. sd_eff = max(sd_raw, floor_sd). */
  z: number | null;
  /** 7-day OLS slope in the indicator's unit per day. */
  slope_per_day: number | null;
  /** slope * 6 / sd_eff, positive = worse. */
  drift_z: number | null;
  /** Severity of the absolute-limit test alone: 0 none, 1 yellow, 2 red. Triage fires only on 2. */
  abs_level: 0 | 1 | 2;
  /** True when a Z-only RED was held at YELLOW pending a second reading. UI: "Re-measure to confirm". */
  remeasure: boolean;
  /** Last up to 14 values, oldest first, null for gaps. For sparklines. */
  trend: Array<number | null>;
  /** radiation only: value / expected rate. */
  ratio?: number;
  /** resistive / ration only. */
  consecutive_low_days?: number;
}

export interface RegionAssessment {
  id: RegionId;
  /** Worst status among its member indicators. Colours the SVG body region. */
  status: Status;
  /** Indicator ids that set the status (empty when GREEN). Highlight these in the popover. */
  drivers: IndicatorId[];
  members: IndicatorId[];
}

export interface AssessmentResponse {
  schema_version: typeof SCHEMA_VERSION;
  astronaut_id: string;
  mission_day: number;
  generated_at_iso: string;
  environment: EnvironmentProfile;

  /** Worst region status. */
  overall: Status;
  indicators: IndicatorAssessment[];
  regions: Record<RegionId, RegionAssessment>;

  compliance: {
    /** Consecutive days with vitals logged AND resistive >= 50% target AND ration >= 80%. */
    streak_days: number;
    bone_low_days: number;
    nutrition_low_days: number;
  };

  /** Named flags for the UI chips. SYSTEMIC_IMMUNE is the composite immune_risk rule. */
  flags: Array<"SYSTEMIC_IMMUNE" | "SPINE_BONE" | "INADEQUATE_NUTRITION" | "REMEASURE_NEEDED">;

  comms: {
    one_way_delay_s: number;
    mode: "LIVE" | "AUTONOMOUS";
    /** Label for the big button: LIVE -> "Call flight surgeon", AUTONOMOUS -> "Autonomous triage + queue packet". */
    emergency_action: "CALL_FLIGHT_SURGEON" | "AUTONOMOUS_TRIAGE_STORE_AND_FORWARD";
  };

  /** True when any indicator breaches an absolute RED limit. Makes the emergency button prominent. Never auto-dials. */
  triage_recommended: boolean;
  thresholds_status: "PROTOTYPE";
  disclaimer: typeof DISCLAIMER;
}

// ------------------------------------------------------------------ 4. CountermeasurePlan
/**
 * What the LLM returns (strict JSON schema, see countermeasure_plan_draft.schema.json).
 * It chooses from exercise_menu.json and writes words. It does NOT write numbers other than minutes and kcal_adjustment,
 * and the server validates both against limits.
 */
export interface CountermeasurePlanDraft {
  sessions: Array<{
    exercise_id: string;            // must exist in exercise_menu.json and be allowed at this gravity
    minutes: number;                // integer, 5..90
    target_system: RegionId;
    instructions: string[];         // 1..4 short steps, plain language
    rationale: string;              // why, referencing the triggering indicators in plain words
  }>;
  nutrition: {
    kcal_adjustment: number;        // integer, server clamps to -300..+600
    focus: string[];                // e.g. ["protein", "calcium and vitamin D sources", "hydration"]
    notes: string;
  };
  escalate_to_flight_surgeon: boolean;
  escalation_reason: string;
  /** <= 120 words, plain English, must not contain numbers that are not in the assessment. */
  explanation: string;
}

/** Computed by resources.py from exercise_menu.json and rules.json resource_math. */
export interface ResourceBlock {
  assumptions: {
    mass_kg: number;
    rest_met: number;               // 1.0, subtracted because resting energy is already in the ration
    water_ml_per_kcal: number;      // 1.15, team estimate
    co2_g_per_kcal: number;         // 0.34, derived from respiratory quotient 0.85
    formula: "extra_kcal = (MET - rest_met) * mass_kg * hours";
  };
  sessions: Array<{
    exercise_id: string;
    minutes: number;
    met: number;
    gross_kcal: number;
    extra_kcal: number;
    extra_water_ml: number;
    extra_co2_g: number;
  }>;
  totals: { extra_kcal: number; extra_water_ml: number; extra_co2_g: number };
  label: "decision-support estimate";
}

export interface CountermeasurePlan {
  schema_version: typeof SCHEMA_VERSION;
  plan_id: string;
  astronaut_id: string;
  generated_for_mission_day: number;
  generated_at_iso: string;
  environment_id: EnvironmentId;
  /** Which assessment items triggered this plan. Copied from AssessmentResponse, not written by the LLM. */
  triggers: Array<{ indicator: IndicatorId; status: Status; reasons: string[] }>;
  draft: CountermeasurePlanDraft;
  resources: ResourceBlock;
  llm: { model: string; input_hash: string };
  disclaimer: typeof DISCLAIMER;
}

// ------------------------------------------------------------------ 5. Emergency triage
/** Scripted protocol the voice agent walks through. The agent speaks `script` verbatim and only follows `next`. */
export interface TriageStep {
  id: string;
  type: "SAY" | "ASK_YES_NO" | "CONFIRM_DONE" | "SYSTEM";
  /** Verbatim text for the voice agent and the on-screen checklist. */
  script: string;
  checklist_label: string;
  /** ASK_YES_NO uses yes/no. SAY, CONFIRM_DONE and SYSTEM use done. Terminal steps have none. */
  next?: { yes?: string; no?: string; done?: string };
  terminal?: boolean;
}

export interface TriageProtocol {
  protocol_id: string;
  version: string;
  scenario: "CHEST_PAIN_COLLAPSE";
  review_status: "PLACEHOLDER_REQUIRES_CLINICIAN_REVIEW";
  start: string;
  steps: TriageStep[];
}

export interface EmergencyTriagePayload {
  schema_version: typeof SCHEMA_VERSION;
  incident_id: string;
  started_at_iso: string;
  scenario: "CHEST_PAIN_COLLAPSE";
  /** Patient and the crew member talking to the agent (may be the same person). */
  patient_id: string;
  reporter_id: string;

  environment: Pick<EnvironmentProfile, "id" | "label" | "gravity_g" | "one_way_delay_s" | "comm_mode">;

  /** Snapshot injected into the agent's context by the n8n webhook. Values come from the database, never from the LLM. */
  patient_snapshot: {
    mission_day: number;
    overall: Status;
    red_indicators: Array<{ id: IndicatorId; value: number | null; unit: string; reasons: string[] }>;
    latest_vitals: DailyMeasurementAndCompliance["vitals"];
    recent_attachments: NonNullable<DailyMeasurementAndCompliance["attachments"]>;
  };

  protocol: { protocol_id: string; version: string };
  state: {
    current_step_id: string;
    step_log: Array<{ step_id: string; at_iso: string; answer?: "yes" | "no" | "done" }>;
    status: "IN_PROGRESS" | "ENDED";
  };

  /** Hard rules injected into the voice agent's system prompt. */
  agent_rules: string[];

  /** Built at the end of the protocol; queued when comm_mode is AUTONOMOUS, sent when LIVE. */
  store_and_forward?: {
    packet_id: string;
    created_at_iso: string;
    delivery: "QUEUED_FOR_UPLINK" | "SENT_LIVE";
    estimated_earth_receipt_iso: string;     // created_at + one_way_delay_s
    summary: string;
    attachments: NonNullable<DailyMeasurementAndCompliance["attachments"]>;
  };

  disclaimer: typeof DISCLAIMER;
}
