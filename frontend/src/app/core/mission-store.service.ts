import { Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Observable, Subject, catchError, forkJoin, map, of, switchMap, tap } from 'rxjs';

import { MOCK_ASSESSMENTS, MOCK_PLANS } from '../mocks/mock-data';
import { AttachmentRef } from '../models-local/attachments';
import {
  AssessmentResponse,
  CountermeasurePlan,
  DailyMeasurementAndCompliance,
  EnvironmentProfile,
  Status,
} from '../models/contract';
import { ApiService } from './api.service';
import { CREW, CrewMember } from './crew';
import { DailyLogService } from './daily-log.service';

const SAVED_CREW = 'crew-health.crew';

interface Habitat {
  envId: string;
  distanceKm: number;
}

// The one place pages get crew data from. Pick a crew member here and every page follows.
// Data comes from the backend; when it cannot be reached the bundled day-14 examples are shown.
@Injectable({ providedIn: 'root' })
export class MissionStore {
  private readonly api = inject(ApiService);
  private readonly log = inject(DailyLogService);
  private readonly reload = new Subject<void>();

  readonly crew = CREW;
  readonly crewId = signal(savedCrew());
  readonly member = computed<CrewMember>(() => CREW.find((m) => m.id === this.crewId()) ?? CREW[0]);

  private readonly assessments = signal<Record<string, AssessmentResponse>>({});
  private readonly plans = signal<Record<string, CountermeasurePlan>>({});
  private readonly overall = signal<Record<string, Status>>({});
  // a habitat picked on the Mission page; null means the crew member's own
  private readonly habitat = signal<Habitat | null>(null);
  // scans captured in this session; they travel to the backend with the next readings
  private readonly capturedScans = signal<Record<string, AttachmentRef[]>>({});

  // After a day is closed, the next day has no assessment until the backend has one for it.
  // Null means exactly that: nothing is filled in on this side.
  readonly pendingDay = computed(() => {
    const next = this.log.nextDay(this.crewId());
    const latest = this.assessments()[this.crewId()];
    return next !== null && latest && latest.mission_day < next ? next : null;
  });

  // null until the first answer arrives, and whenever today has no assessment yet
  readonly assessment = computed(() => (this.pendingDay() ? null : (this.assessments()[this.crewId()] ?? null)));
  readonly plan = computed(() => {
    const assessment = this.assessment();
    const plan = this.plans()[this.crewId()];
    return assessment && plan?.generated_for_mission_day === assessment.mission_day ? plan : null;
  });
  // the most recent closed day, for context while the next one is awaited
  readonly lastClosed = computed(() => this.log.of(this.crewId())[0] ?? null);
  readonly scans = computed(() => this.capturedScans()[this.crewId()] ?? []);
  // true while another habitat is being simulated on the Mission page
  readonly habitatChosen = computed(() => this.habitat() !== null);
  readonly environments = signal<EnvironmentProfile[]>([]);
  readonly loading = signal(true);
  readonly planLoading = signal(false);
  readonly offline = signal(false);

  constructor() {
    this.reload
      .pipe(
        switchMap(() => this.fetchAssessment()),
        switchMap((assessment) => this.fetchPlan(assessment)),
        takeUntilDestroyed(),
      )
      .subscribe();

    this.fetchCrewList();
    this.reload.next();
  }

  selectCrew(id: string): void {
    if (id === this.crewId() || !CREW.some((m) => m.id === id)) return;
    this.crewId.set(id);
    this.habitat.set(null);
    try {
      localStorage.setItem(SAVED_CREW, id);
    } catch {
      // private mode: the choice just will not survive a reload
    }
    this.reload.next();
  }

  selectHabitat(envId: string, distanceKm: number): void {
    this.habitat.set({ envId, distanceKm });
    this.reload.next();
  }

  // back to the crew member's own habitat
  resetHabitat(): void {
    if (!this.habitat()) return;
    this.habitat.set(null);
    this.reload.next();
  }

  refresh(): void {
    this.fetchCrewList();
    this.reload.next();
  }

  addScan(scan: AttachmentRef): void {
    const id = this.crewId();
    this.capturedScans.update((all) => ({ ...all, [id]: [...(all[id] ?? []), scan] }));
  }

  // Sends one day's readings. The answer is that day's assessment, computed by the backend.
  logReadings(measurement: DailyMeasurementAndCompliance): Observable<AssessmentResponse> {
    return this.api.postMeasurement(measurement).pipe(
      tap((assessment) => {
        this.assessments.update((all) => ({ ...all, [assessment.astronaut_id]: assessment }));
        this.refresh();
      }),
    );
  }

  // Closes today: its assessment and plan go into the log unchanged, and the next day waits for
  // readings. This is the one place to swap for a backend call when the server learns about days.
  startNextDay(): void {
    const assessment = this.assessment();
    if (!assessment) return;
    this.log.close(assessment, this.plan());
    this.capturedScans.update((all) => ({ ...all, [assessment.astronaut_id]: [] }));
  }

  // Demo reset: forgets the log and the captured scans, and asks the backend to reload its seed data.
  resetDemo(): Observable<unknown> {
    this.log.clear();
    this.capturedScans.set({});
    this.habitat.set(null);
    return this.api.reset().pipe(
      catchError(() => of(null)),
      tap(() => this.refresh()),
    );
  }

  // overall status of any crew member, for the switcher
  statusOf(id: string): Status | null {
    return this.assessments()[id]?.overall ?? this.overall()[id] ?? null;
  }

  private fetchAssessment(): Observable<AssessmentResponse> {
    const id = this.crewId();
    const habitat = this.habitat();
    this.loading.set(true);
    return this.api.assessment(id, habitat ? { envId: habitat.envId, distanceKm: habitat.distanceKm } : {}).pipe(
      tap(() => this.offline.set(false)),
      catchError(() => {
        this.offline.set(true);
        return of(MOCK_ASSESSMENTS[id]);
      }),
      tap((assessment) => {
        this.assessments.update((all) => ({ ...all, [id]: assessment }));
        this.loading.set(false);
      }),
    );
  }

  private fetchPlan(assessment: AssessmentResponse): Observable<CountermeasurePlan> {
    const id = assessment.astronaut_id;
    const keep = (plan: CountermeasurePlan) => this.plans.update((all) => ({ ...all, [id]: plan }));
    if (this.offline()) {
      keep(MOCK_PLANS[id]);
      this.planLoading.set(false);
      return EMPTY;
    }
    this.planLoading.set(true);
    return this.api.generatePlan(assessment).pipe(
      map((result) => result.plan),
      catchError(() => of(MOCK_PLANS[id])),
      tap((plan) => {
        keep(plan);
        this.planLoading.set(false);
      }),
    );
  }

  private fetchCrewList(): void {
    forkJoin({ astronauts: this.api.astronauts(), environments: this.api.environments() })
      .pipe(catchError(() => of(null)))
      .subscribe((data) => {
        if (!data) {
          this.overall.set(Object.fromEntries(CREW.map((m) => [m.id, MOCK_ASSESSMENTS[m.id].overall])));
          return;
        }
        this.overall.set(Object.fromEntries(data.astronauts.map((a) => [a.astronaut_id, a.overall])));
        this.environments.set(data.environments);
      });
  }
}

function savedCrew(): string {
  try {
    const id = localStorage.getItem(SAVED_CREW);
    if (id && CREW.some((m) => m.id === id)) return id;
  } catch {
    // no storage: fall through to the default
  }
  return CREW[0].id;
}
