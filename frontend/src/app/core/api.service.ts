import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { AssessmentResponse, CountermeasurePlan } from '../models/contract';

export const API_BASE = 'http://localhost:8080';

// where the backend got the plan from (X-Plan-Source header); MOCK means the backend was not reachable
export type PlanSource = 'LIVE' | 'SAVED' | 'OFFLINE' | 'MOCK';

export interface GeneratedPlan {
  plan: CountermeasurePlan;
  source: PlanSource;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  assessment(astronautId: string): Observable<AssessmentResponse> {
    return this.http.get<AssessmentResponse>(`${API_BASE}/api/assessments/${astronautId}`);
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
