import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import {
  AssessmentResponse,
  CountermeasurePlan,
  DailyMeasurementAndCompliance,
  EnvironmentProfile,
} from '../models/contract';
import { environment } from '../../environments/environment';

export const API_BASE = environment.apiBase;

// where the backend got the plan from (X-Plan-Source header); MOCK means the backend was not reachable
export type PlanSource = 'LIVE' | 'SAVED' | 'OFFLINE' | 'MOCK';

export interface GeneratedPlan {
  plan: CountermeasurePlan;
  source: PlanSource;
}

export interface AstronautSummary {
  astronaut_id: string;
  display_name: string;
  scenario: string;
  latest_mission_day: number;
  environment_id: string;
  overall: AssessmentResponse['overall'];
  triage_recommended: boolean;
}

export type TriageAnswer = 'yes' | 'no' | 'done';

export interface TriageProtocol {
  protocol_id: string;
  version: string;
  scenario: string;
  review_status: string;
  start: string;
  steps: Array<{
    id: string;
    type: string;
    script: string;
    checklist_label: string;
    next?: Record<string, string>;
    terminal?: boolean;
  }>;
}

export interface TriageIncident {
  schema_version: string;
  incident_id: string;
  started_at_iso: string;
  scenario: string;
  patient_id: string;
  reporter_id: string;
  environment: {
    id: string;
    label: string;
    gravity_g: number;
    one_way_delay_s: number;
    comm_mode: 'LIVE' | 'AUTONOMOUS';
  };
  patient_snapshot: {
    mission_day: number;
    overall: AssessmentResponse['overall'];
    red_indicators: Array<{ id: string; value: number | null; unit: string; reasons: string[] }>;
    latest_vitals: DailyMeasurementAndCompliance['vitals'] | null;
    recent_attachments: NonNullable<DailyMeasurementAndCompliance['attachments']>;
  };
  protocol: { protocol_id: string; version: string };
  state: {
    current_step_id: string;
    step_log: Array<{ step_id: string; at_iso: string; answer?: string | null }>;
    status: string;
  };
  agent_rules: string[];
  store_and_forward?: {
    packet_id: string;
    created_at_iso: string;
    delivery: string;
    estimated_earth_receipt_iso: string;
    summary: string;
    attachments: NonNullable<DailyMeasurementAndCompliance['attachments']>;
  } | null;
  disclaimer: string;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  astronauts(): Observable<AstronautSummary[]> {
    return this.http.get<AstronautSummary[]>(`${API_BASE}/api/astronauts`);
  }

  environments(): Observable<EnvironmentProfile[]> {
    return this.http.get<EnvironmentProfile[]>(`${API_BASE}/api/environments`);
  }

  assessment(
    astronautId: string,
    options: { day?: number; envId?: string; distanceKm?: number } = {},
  ): Observable<AssessmentResponse> {
    let params = new HttpParams();
    if (options.day !== undefined) params = params.set('day', options.day);
    if (options.envId) params = params.set('envId', options.envId);
    if (options.distanceKm !== undefined) params = params.set('distanceKm', options.distanceKm);
    return this.http.get<AssessmentResponse>(`${API_BASE}/api/assessments/${astronautId}`, { params });
  }

  postMeasurement(measurement: DailyMeasurementAndCompliance): Observable<AssessmentResponse> {
    return this.http.post<AssessmentResponse>(`${API_BASE}/api/measurements`, measurement);
  }

  reset(): Observable<{ seed_records: number }> {
    return this.http.post<{ seed_records: number }>(`${API_BASE}/api/reset`, {});
  }

  triageProtocol(): Observable<TriageProtocol> {
    return this.http.get<TriageProtocol>(`${API_BASE}/api/triage/protocol`);
  }

  startTriage(request: {
    patient_id: string;
    reporter_id?: string;
    environment_id?: string;
    distance_km?: number;
    mission_day?: number;
  }): Observable<TriageIncident> {
    return this.http.post<TriageIncident>(`${API_BASE}/api/triage/start`, request);
  }

  triageStep(request: { incident_id: string; answer: TriageAnswer; step_id: string }): Observable<TriageIncident> {
    return this.http.post<TriageIncident>(`${API_BASE}/api/triage/step`, request);
  }

  endTriage(request: { incident_id: string; reason?: string }): Observable<TriageIncident> {
    return this.http.post<TriageIncident>(`${API_BASE}/api/triage/end`, request);
  }

  triageIncident(incidentId: string): Observable<TriageIncident> {
    return this.http.get<TriageIncident>(`${API_BASE}/api/triage/${incidentId}`);
  }

  // fresh = true asks the LLM again instead of reusing a saved plan (free tier: 20 calls a day)
  generatePlan(assessment: AssessmentResponse, fresh = false): Observable<GeneratedPlan> {
    return this.http
      .post<CountermeasurePlan>(`${API_BASE}/api/plans/generate`, assessment, {
        params: fresh ? { fresh: 'true' } : {},
        observe: 'response',
      })
      .pipe(
        map((res) => ({
          plan: res.body as CountermeasurePlan,
          source: (res.headers.get('X-Plan-Source') as PlanSource | null) ?? 'SAVED',
        })),
      );
  }
}
