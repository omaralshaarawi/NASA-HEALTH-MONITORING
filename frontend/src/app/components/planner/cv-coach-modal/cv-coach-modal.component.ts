import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { Subscription, catchError, forkJoin, interval, map, of, switchMap } from 'rxjs';

import { CvCoachService } from '../../../core/cv-coach.service';
import { MissionStore } from '../../../core/mission-store.service';
import { CvExercise, CvSyncResult, CvTelemetry } from '../../../models-local/cv-coach';
import { ModalShellComponent } from '../modal-shell/modal-shell.component';
import { REGION_LABELS } from '../planner-labels';

const POLL_MS = 400;
// this many failed telemetry calls in a row (about 2 s) and the coach counts as gone
const MISSES_BEFORE_OFFLINE = 5;

// Camera coach for one training session: the live pose-tracking feed from cv-service, the live
// counters, a form check, and a way to add the workout to today's record. The webcam is only in
// use while this dialog is open.
@Component({
  selector: 'app-cv-coach-modal',
  imports: [ModalShellComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cv-coach-modal.component.html',
  styleUrl: './cv-coach-modal.component.css',
})
export class CvCoachModalComponent implements OnInit {
  private readonly coach = inject(CvCoachService);
  protected readonly store = inject(MissionStore);

  // the plan's exercise id; the service matches it to a camera profile
  readonly exerciseId = input.required<string>();
  readonly closed = output<void>();

  protected readonly regionLabels = REGION_LABELS;
  protected readonly phase = signal<'connecting' | 'online' | 'offline'>('connecting');
  protected readonly problem = signal('');
  protected readonly exercises = signal<CvExercise[]>([]);
  protected readonly telemetry = signal<CvTelemetry | null>(null);
  protected readonly feedUrl = signal<string | null>(null);
  protected readonly feedProblem = signal(false);
  protected readonly switching = signal(false);

  protected readonly minutes = signal('');
  protected readonly syncing = signal(false);
  protected readonly synced = signal<CvSyncResult | null>(null);
  protected readonly syncProblem = signal('');

  private readonly feed = viewChild<ElementRef<HTMLImageElement>>('feed');
  private polling: Subscription | null = null;
  private misses = 0;
  // the camera stream this dialog opened, until it is stopped
  private stream: string | null = null;

  protected readonly holdMode = computed(() => this.telemetry()?.cv_tracking_mode === 'POSTURE_HOLD');
  protected readonly minutesOverride = computed(() => {
    const value = this.minutes().trim();
    return value === '' ? null : Math.max(0, Math.round(Number(value)));
  });
  protected readonly canSync = computed(() => {
    const t = this.telemetry();
    if (!t || !this.store.assessment() || this.syncing() || this.phase() !== 'online') return false;
    const override = this.minutesOverride();
    return override !== null ? Number.isFinite(override) && override > 0 : t.reps > 0;
  });

  constructor() {
    // closing the tab skips the normal clean-up, so the camera is stopped from here too
    const onPageHide = () => {
      if (this.stream) this.coach.stopOnUnload(this.stream);
    };
    window.addEventListener('pagehide', onPageHide);
    inject(DestroyRef).onDestroy(() => {
      window.removeEventListener('pagehide', onPageHide);
      this.disconnect();
    });
  }

  ngOnInit(): void {
    this.connect();
  }

  protected connect(): void {
    this.phase.set('connecting');
    this.problem.set('');
    this.feedProblem.set(false);
    forkJoin({ list: this.coach.exercises(), selected: this.coach.select(this.exerciseId()) }).subscribe({
      next: ({ list, selected }) => {
        this.exercises.set(list);
        this.telemetry.set(selected);
        this.phase.set('online');
        this.stream = this.coach.newStream();
        this.feedUrl.set(this.coach.feedUrl(this.stream));
        this.startPolling();
      },
      error: (error: Error) => this.goOffline(error.message),
    });
  }

  protected choose(event: Event): void {
    const id = (event.target as HTMLSelectElement).value;
    this.switching.set(true);
    this.synced.set(null);
    this.coach.select(id).subscribe({
      next: (selected) => {
        this.telemetry.set(selected);
        this.switching.set(false);
      },
      error: (error: Error) => {
        this.problem.set(error.message);
        this.switching.set(false);
      },
    });
  }

  protected setMinutes(event: Event): void {
    this.minutes.set((event.target as HTMLInputElement).value);
  }

  protected sync(): void {
    const assessment = this.store.assessment();
    if (!assessment || !this.canSync()) return;
    this.syncing.set(true);
    this.synced.set(null);
    this.syncProblem.set('');
    this.coach
      .sync({ astronaut_id: assessment.astronaut_id, day: assessment.mission_day, minutes_override: this.minutesOverride() })
      .subscribe({
        next: (result) => {
          this.syncing.set(false);
          this.synced.set(result);
          // the body map, compliance and every page follow the new assessment
          this.store.refresh();
        },
        error: (error: Error) => {
          this.syncing.set(false);
          this.syncProblem.set(error.message);
        },
      });
  }

  protected feedFailed(): void {
    this.feedProblem.set(true);
  }

  private startPolling(): void {
    this.polling?.unsubscribe();
    this.misses = 0;
    this.polling = interval(POLL_MS)
      .pipe(
        switchMap(() =>
          this.coach.telemetry().pipe(
            map((t): CvTelemetry | null => t),
            catchError(() => of(null)),
          ),
        ),
      )
      .subscribe((t) => {
        if (t) {
          this.misses = 0;
          this.telemetry.set(t);
        } else if (++this.misses >= MISSES_BEFORE_OFFLINE) {
          this.goOffline('The camera coach stopped answering. Check that cv-service is still running.');
        }
      });
  }

  private goOffline(message: string): void {
    this.disconnect();
    this.problem.set(message);
    this.phase.set('offline');
  }

  // Stops polling, drops the video stream and tells cv-service to release the webcam at once,
  // so the camera is free as soon as the dialog closes and opens again next time.
  private disconnect(): void {
    this.polling?.unsubscribe();
    this.polling = null;
    this.feed()?.nativeElement.removeAttribute('src');
    this.feedUrl.set(null);
    if (this.stream) {
      this.coach.stop(this.stream);
      this.stream = null;
    }
  }
}
