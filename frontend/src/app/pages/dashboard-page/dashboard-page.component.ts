import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, of, Subscription } from 'rxjs';

import { BodyMapComponent } from '../../components/dashboard/body-map.component';
import { HabitatSwitcherComponent } from '../../components/dashboard/habitat-switcher.component';
import { IndicatorListComponent } from '../../components/dashboard/indicator-list.component';
import { MeasurementFormModalComponent } from '../../components/dashboard/measurement-form-modal.component';
import { TopBarComponent } from '../../components/dashboard/top-bar.component';
import { MeasurementGuideModalComponent } from '../../components/planner/measurement-guide-modal/measurement-guide-modal.component';
import { CREW } from '../../core/crew';
import { ApiService, AstronautSummary } from '../../core/api.service';
import { MOCK_ASSESSMENTS } from '../../mocks/mock-data';
import { AssessmentResponse, DailyMeasurementAndCompliance, EnvironmentProfile, IndicatorId, RegionId } from '../../models/contract';
import { statusClass } from '../../components/planner/planner-labels';

@Component({
  selector: 'app-dashboard-page',
  imports: [
    BodyMapComponent,
    HabitatSwitcherComponent,
    IndicatorListComponent,
    MeasurementFormModalComponent,
    MeasurementGuideModalComponent,
    DecimalPipe,
    RouterLink,
    TopBarComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard-page.component.html',
})
export class DashboardPageComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private assessmentSubscription: Subscription | null = null;
  private openLogAfterLoad = false;

  protected readonly crew = CREW;
  protected readonly statusClass = statusClass;
  protected readonly selectedId = signal('ast_nova');
  protected readonly summaries = signal<AstronautSummary[]>([]);
  protected readonly environments = signal<EnvironmentProfile[]>([]);
  protected readonly assessments = signal<Record<string, AssessmentResponse>>({});
  protected readonly selectedEnvironmentId = signal('SPACE_STATION');
  protected readonly distanceKm = signal(400);
  protected readonly day = signal(14);
  protected readonly maxDay = computed(() => this.summaries().length
    ? Math.max(1, ...this.summaries().map((summary) => summary.latest_mission_day))
    : 14);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly offline = signal(false);
  protected readonly measurementOpen = signal(false);
  protected readonly guideFor = signal<IndicatorId | null>(null);
  protected readonly onlyRegion = signal<RegionId | null>(null);
  protected readonly measurementError = signal('');
  protected readonly notice = signal('');

  protected readonly assessment = computed(() => this.assessments()[this.selectedId()] ?? MOCK_ASSESSMENTS[this.selectedId()]);
  protected readonly selectedCrew = computed(() => this.crew.find((member) => member.id === this.selectedId()) ?? this.crew[0]);
  protected readonly selectedEnvironment = computed(
    () => this.environments().find((environment) => environment.id === this.selectedEnvironmentId()) ?? this.assessment().environment,
  );
  protected readonly summary = computed(() => this.summaries().find((entry) => entry.astronaut_id === this.selectedId()));
  protected readonly nextMissionDay = computed(() => Math.max(
    this.assessment().mission_day,
    this.summary()?.latest_mission_day ?? this.assessment().mission_day,
  ) + 1);

  constructor() {
    const query = this.route.snapshot.queryParamMap;
    const crewId = query.get('crew');
    if (crewId && this.crew.some((member) => member.id === crewId)) this.selectedId.set(crewId);
    const day = Number(query.get('day'));
    if (Number.isFinite(day) && day >= 1) this.day.set(day);
    const distance = Number(query.get('distanceKm'));
    if (Number.isFinite(distance) && distance > 0) this.distanceKm.set(distance);
    const envId = query.get('env');
    if (envId) this.selectedEnvironmentId.set(envId);
    this.openLogAfterLoad = query.get('log') === '1';
    this.loadMetadata();
  }

  protected selectCrew(id: string): void {
    if (this.offline() || id === this.selectedId()) return;
    this.selectedId.set(id);
    this.onlyRegion.set(null);
    this.loadAssessments();
  }

  protected selectEnvironment(environment: EnvironmentProfile): void {
    if (this.offline()) return;
    this.selectedEnvironmentId.set(environment.id);
    this.distanceKm.set(environment.distance_km);
    this.loadAssessments();
  }

  protected selectDistance(distance: number): void {
    if (this.offline()) return;
    this.distanceKm.set(distance);
    this.loadAssessments();
  }

  protected selectDay(day: number): void {
    if (this.offline()) return;
    this.day.set(day);
    this.loadAssessments();
  }

  protected showRegion(region: RegionId): void {
    this.onlyRegion.set(this.onlyRegion() === region ? null : region);
  }

  protected openTriage(): void {
    const assessment = this.assessment();
    void this.router.navigate(['/triage'], {
      queryParams: {
        patient: assessment.astronaut_id,
        env: assessment.environment.id,
        distanceKm: assessment.environment.distance_km,
        day: assessment.mission_day,
      },
    });
  }

  protected openMeasurement(): void {
    this.measurementError.set('');
    this.measurementOpen.set(true);
  }

  protected submitMeasurement(measurement: DailyMeasurementAndCompliance): void {
    if (this.offline()) return;
    this.saving.set(true);
    this.measurementError.set('');
    this.api.postMeasurement(measurement).pipe(
      catchError((error: unknown) => {
        this.measurementError.set(this.errorMessage(error, 'The backend could not save this measurement.'));
        this.saving.set(false);
        return of(null);
      }),
    ).subscribe((response) => {
      if (!response) return;
      this.assessments.update((all) => ({ ...all, [response.astronaut_id]: response }));
      this.day.set(response.mission_day);
      this.summaries.update((all) => all.map((summary) => summary.astronaut_id === response.astronaut_id
        ? { ...summary, latest_mission_day: response.mission_day, overall: response.overall, triage_recommended: response.triage_recommended }
        : summary));
      this.measurementOpen.set(false);
      this.saving.set(false);
      this.notice.set(`Mission day ${response.mission_day} measurement saved.`);
      this.loadAssessments();
    });
  }

  protected resetDemo(): void {
    if (this.offline()) return;
    this.api.reset().pipe(
      catchError((error: unknown) => {
        this.notice.set(this.errorMessage(error, 'Could not reset demo data.'));
        return of(null);
      }),
    ).subscribe((result) => {
      if (!result) return;
      this.notice.set(`Demo records reset to ${result.seed_records} seeded measurements.`);
      this.loadMetadata();
    });
  }

  protected retry(): void {
    this.offline.set(false);
    this.loading.set(true);
    this.loadMetadata();
  }

  protected get emergencyLabel(): string {
    return this.assessment().comms.emergency_action === 'CALL_FLIGHT_SURGEON'
      ? 'Call flight surgeon'
      : 'Autonomous triage + queue packet';
  }

  protected hasVitals(): boolean {
    const vitals = new Set(['hr_rest', 'sbp', 'dbp', 'glucose', 'temp', 'sleep', 'mood']);
    return this.assessment().indicators.some((indicator) => vitals.has(indicator.id) && indicator.value !== null);
  }

  private loadMetadata(): void {
    this.assessmentSubscription?.unsubscribe();
    forkJoin({ astronauts: this.api.astronauts(), environments: this.api.environments() }).pipe(
      catchError(() => {
        this.useOfflineData();
        return of(null);
      }),
    ).subscribe((data) => {
      if (!data) return;
      this.summaries.set(data.astronauts);
      this.environments.set(data.environments);
      this.offline.set(false);
      const selectedSummary = data.astronauts.find((entry) => entry.astronaut_id === this.selectedId());
      const envFromUrl = this.route.snapshot.queryParamMap.get('env');
      const envId = data.environments.some((environment) => environment.id === envFromUrl)
        ? envFromUrl!
        : selectedSummary?.environment_id ?? data.environments[0]?.id;
      if (envId) {
        this.selectedEnvironmentId.set(envId);
        const environment = data.environments.find((entry) => entry.id === envId);
        if (environment && !this.route.snapshot.queryParamMap.has('distanceKm')) this.distanceKm.set(environment.distance_km);
      }
      const max = Math.max(1, ...data.astronauts.map((entry) => entry.latest_mission_day));
      this.day.set(Math.min(this.day(), max));
      this.loadAssessments();
    });
  }

  private loadAssessments(): void {
    if (this.offline() || this.environments().length === 0) {
      this.useOfflineData();
      return;
    }
    this.assessmentSubscription?.unsubscribe();
    this.loading.set(true);
    let requestFailed = false;
    const requests = this.crew.map((member) => {
      const summary = this.summaries().find((entry) => entry.astronaut_id === member.id);
      const requestedDay = Math.min(this.day(), summary?.latest_mission_day ?? this.day());
      return this.api.assessment(member.id, {
        day: requestedDay,
        envId: this.selectedEnvironmentId(),
        distanceKm: this.distanceKm(),
      }).pipe(catchError(() => {
        requestFailed = true;
        return of(MOCK_ASSESSMENTS[member.id]);
      }));
    });
    this.assessmentSubscription = forkJoin(requests).subscribe((results) => {
      const data = Object.fromEntries(this.crew.map((member, index) => [member.id, results[index]]));
      this.assessments.set(data);
      this.offline.set(requestFailed);
      this.loading.set(false);
      if (this.openLogAfterLoad && !requestFailed) {
        this.openLogAfterLoad = false;
        this.openMeasurement();
      }
      if (requestFailed) this.notice.set('Some assessment requests failed; bundled day-14 examples are shown.');
    });
  }

  private useOfflineData(): void {
    this.assessmentSubscription?.unsubscribe();
    this.offline.set(true);
    this.loading.set(false);
    this.assessments.set(MOCK_ASSESSMENTS);
    const profile = MOCK_ASSESSMENTS[this.selectedId()].environment;
    this.environments.set([profile]);
    this.selectedEnvironmentId.set(profile.id);
    this.distanceKm.set(profile.distance_km);
    this.notice.set('Backend offline · bundled day-14 data is read-only. Reconnect and retry to enable controls.');
  }

  private errorMessage(error: unknown, fallback: string): string {
    if (typeof error === 'object' && error !== null && 'error' in error) {
      const body = (error as { error?: { message?: string; error?: string } }).error;
      return body?.message ?? body?.error ?? fallback;
    }
    return fallback;
  }
}