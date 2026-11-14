import { AssessmentResponse, RegionId, Status } from '../models/contract';

// Shapes returned by cv-service (the camera coach). Not part of the shared contract.

export type CvTrackingMode = 'REP_COUNTER' | 'CADENCE_TRACKER' | 'POSTURE_HOLD';

export interface CvExercise {
  exercise_id: string;
  name: string;
  target_system: RegionId;
  cv_tracking_mode: CvTrackingMode;
  camera_orientation: string;
  setup_instruction_0g: string;
  rep_thresholds: { default_target_reps: number; default_hold_seconds: number };
}

export interface CvTelemetry {
  exercise_id: string;
  requested_id: string;
  profile_fallback: boolean;
  name: string;
  target_system: RegionId;
  cv_tracking_mode: CvTrackingMode;
  setup_instruction_0g: string;
  reps: number;
  target_reps: number;
  hold_seconds: number;
  target_hold_seconds: number;
  cadence_rpm: number;
  stage: string;
  tracked_side: 'LEFT' | 'RIGHT';
  primary_angle: number;
  visibility_ok: boolean;
  camera_active: boolean;
  // form check from the camera, not a health status
  severity: Status;
  hud_warning: string;
  active_warnings: string[];
}

export interface CvSyncRequest {
  astronaut_id: string;
  day: number;
  minutes_override: number | null;
}

export interface CvSyncResult {
  synced: boolean;
  exercise_id: string;
  reps_completed: number;
  credited_minutes: number;
  previous_ared_minutes: number;
  new_ared_minutes: number;
  updated_assessment: AssessmentResponse;
}
