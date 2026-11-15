import { Injectable, signal } from '@angular/core';

import { AssessmentResponse, CountermeasurePlan } from '../models/contract';

export interface ClosedDay {
  day: number;
  closedAtIso: string;
  assessment: AssessmentResponse;
  plan: CountermeasurePlan | null;
}

const SAVED_LOG = 'crew-health.log';

@Injectable({ providedIn: 'root' })
export class DailyLogService {
  private readonly days = signal<Record<string, ClosedDay[]>>(readSaved());

  // newest first
  of(crewId: string): ClosedDay[] {
    return [...(this.days()[crewId] ?? [])].sort((a, b) => b.day - a.day);
  }

  nextDay(crewId: string): number | null {
    const last = this.of(crewId)[0];
    return last ? last.day + 1 : null;
  }

  close(assessment: AssessmentResponse, plan: CountermeasurePlan | null): void {
    const entry: ClosedDay = {
      day: assessment.mission_day,
      closedAtIso: new Date().toISOString(),
      assessment,
      plan,
    };
    const id = assessment.astronaut_id;
    this.days.update((all) => ({
      ...all,
      [id]: [...(all[id] ?? []).filter((saved) => saved.day !== entry.day), entry],
    }));
    this.save();
  }

  clear(): void {
    this.days.set({});
    this.save();
  }

  private save(): void {
    try {
      localStorage.setItem(SAVED_LOG, JSON.stringify(this.days()));
    } catch {
      // storage blocked or full
    }
  }
}

function readSaved(): Record<string, ClosedDay[]> {
  try {
    return JSON.parse(localStorage.getItem(SAVED_LOG) ?? '{}') as Record<string, ClosedDay[]>;
  } catch {
    return {};
  }
}
