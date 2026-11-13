import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ModalShellComponent } from '../planner/modal-shell/modal-shell.component';
import { Attachment } from '../planner/ultrasound-attachment-modal/scan-guides';
import { UltrasoundAttachmentModalComponent } from '../planner/ultrasound-attachment-modal/ultrasound-attachment-modal.component';
import { AssessmentResponse, DailyMeasurementAndCompliance, EnvironmentProfile } from '../../models/contract';
import { PvtResult, PvtTestComponent } from './pvt-test.component';

@Component({
  selector: 'app-measurement-form-modal',
  imports: [FormsModule, ModalShellComponent, PvtTestComponent, UltrasoundAttachmentModalComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-modal-shell heading="Daily measurement" eyebrow="Manual entry · backend validated" (closed)="closed.emit()">
      <form class="space-y-5" (ngSubmit)="submit()">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <p class="m-0 text-sm leading-6 text-ink-dim">Mission day {{ nextMissionDay() }} · values not entered remain unmeasured, not zero.</p>
          <span class="chip">{{ environment().label }}</span>
        </div>

        <section>
          <h3 class="eyebrow">Vitals · optional</h3>
          <div class="mt-2 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <label class="text-sm text-ink-dim">Resting heart rate <span class="unit">bpm</span><input class="mt-1 w-full rounded border border-line bg-hull px-3 py-2 text-ink" name="hr" type="number" min="20" max="250" step="1" [(ngModel)]="vitals.hr" /></label>
            <label class="text-sm text-ink-dim">Systolic pressure <span class="unit">mmHg</span><input class="mt-1 w-full rounded border border-line bg-hull px-3 py-2 text-ink" name="sbp" type="number" min="50" max="250" step="1" [(ngModel)]="vitals.sbp" /></label>
            <label class="text-sm text-ink-dim">Diastolic pressure <span class="unit">mmHg</span><input class="mt-1 w-full rounded border border-line bg-hull px-3 py-2 text-ink" name="dbp" type="number" min="30" max="150" step="1" [(ngModel)]="vitals.dbp" /></label>
            <label class="text-sm text-ink-dim">Fasting glucose <span class="unit">mg/dL</span><input class="mt-1 w-full rounded border border-line bg-hull px-3 py-2 text-ink" name="glucose" type="number" min="20" max="500" step="1" [(ngModel)]="vitals.glucose" /></label>
            <label class="text-sm text-ink-dim">Temperature <span class="unit">°C</span><input class="mt-1 w-full rounded border border-line bg-hull px-3 py-2 text-ink" name="temp" type="number" min="30" max="43" step="0.1" [(ngModel)]="vitals.temp" /></label>
            <label class="text-sm text-ink-dim">Sleep <span class="unit">h</span><input class="mt-1 w-full rounded border border-line bg-hull px-3 py-2 text-ink" name="sleep" type="number" min="0" max="24" step="0.1" [(ngModel)]="vitals.sleep" /></label>
            <label class="text-sm text-ink-dim">Mood <span class="unit">1–10</span><input class="mt-1 w-full rounded border border-line bg-hull px-3 py-2 text-ink" name="mood" type="number" min="1" max="10" step="1" [(ngModel)]="vitals.mood" /></label>
          </div>
        </section>

        <section>
          <h3 class="eyebrow">Daily routine</h3>
          <div class="mt-2 grid gap-3 sm:grid-cols-3">
            <label class="flex items-center gap-2 text-sm text-ink-dim"><input type="checkbox" name="morning" [(ngModel)]="morningVitalsLogged" /> Morning vitals logged</label>
            <label class="text-sm text-ink-dim">Resistive exercise <span class="unit">min</span><input class="mt-1 w-full rounded border border-line bg-hull px-3 py-2 text-ink" name="exercise" type="number" min="0" max="300" step="1" [(ngModel)]="exerciseMinutes" /></label>
            <label class="text-sm text-ink-dim">Ration consumed <span class="unit">%</span><input class="mt-1 w-full rounded border border-line bg-hull px-3 py-2 text-ink" name="ration" type="number" min="0" max="200" step="1" [(ngModel)]="rationPct" /></label>
          </div>
        </section>

        <app-pvt-test (completed)="pvtResult.set($event)" />

        <section class="rounded border border-line bg-hull p-4">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h3 class="eyebrow m-0">Optional scan attachments</h3>
              <p class="m-0 mt-1 text-xs text-ink-mute">Simulated references are stored with this measurement; images are not interpreted.</p>
            </div>
            <button type="button" class="btn" (click)="scanOpen.set(true)">Capture guided scan / ECG</button>
          </div>
          @if (attachments().length) {
            <ul class="mt-3 flex list-none flex-wrap gap-2 p-0">
              @for (attachment of attachments(); track attachment.ref) {
                <li class="chip">{{ attachment.kind }} · attached</li>
              }
            </ul>
          }
        </section>

        @if (error()) {
          <div class="rounded border border-signal/50 bg-raised p-3 text-sm leading-6 text-ink" role="alert">{{ error() }}</div>
        }
        <div class="flex flex-wrap justify-end gap-3">
          <button type="button" class="btn" [disabled]="submitting()" (click)="closed.emit()">Cancel</button>
          <button type="submit" class="btn btn-primary" [disabled]="submitting()">{{ submitting() ? 'Saving…' : 'Save measurement' }}</button>
        </div>
      </form>

      @if (scanOpen()) {
        <app-ultrasound-attachment-modal [assessment]="assessment()" (attached)="addAttachment($event)" (closed)="scanOpen.set(false)" />
      }
    </app-modal-shell>
  `,
})
export class MeasurementFormModalComponent {
  readonly assessment = input.required<AssessmentResponse>();
  readonly nextMissionDay = input.required<number>();
  readonly environment = input.required<EnvironmentProfile>();
  readonly submitting = input(false);
  readonly error = input('');
  readonly closed = output<void>();
  readonly saveMeasurement = output<DailyMeasurementAndCompliance>();

  protected readonly scanOpen = signal(false);
  protected readonly attachments = signal<Attachment[]>([]);
  protected readonly pvtResult = signal<PvtResult | null>(null);
  protected morningVitalsLogged = false;
  protected exerciseMinutes = 0;
  protected rationPct = 100;
  protected vitals: {
    hr: number | null;
    sbp: number | null;
    dbp: number | null;
    glucose: number | null;
    temp: number | null;
    sleep: number | null;
    mood: number | null;
  } = { hr: null, sbp: null, dbp: null, glucose: null, temp: null, sleep: null, mood: null };

  protected addAttachment(attachment: Attachment): void {
    this.attachments.update((all) => [...all, attachment]);
  }

  protected submit(): void {
    const values = this.vitals;
    const vitals = {
      ...(this.numberOrUndefined(values.hr) === undefined ? {} : { hr_rest_bpm: this.numberOrUndefined(values.hr) }),
      ...(this.numberOrUndefined(values.sbp) === undefined ? {} : { sbp_mmHg: this.numberOrUndefined(values.sbp) }),
      ...(this.numberOrUndefined(values.dbp) === undefined ? {} : { dbp_mmHg: this.numberOrUndefined(values.dbp) }),
      ...(this.numberOrUndefined(values.glucose) === undefined ? {} : { glucose_mg_dl: this.numberOrUndefined(values.glucose) }),
      ...(this.numberOrUndefined(values.temp) === undefined ? {} : { temp_c: this.numberOrUndefined(values.temp) }),
      ...(this.numberOrUndefined(values.sleep) === undefined ? {} : { sleep_h: this.numberOrUndefined(values.sleep) }),
      ...(this.numberOrUndefined(values.mood) === undefined ? {} : { mood_1_10: this.numberOrUndefined(values.mood) }),
    };
    const env = this.environment();
    const measurement: DailyMeasurementAndCompliance = {
      astronaut_id: this.assessment().astronaut_id,
      mission_day: this.nextMissionDay(),
      date_iso: new Date().toISOString().slice(0, 10),
      environment_id: env.id,
      source: 'MANUAL',
      vitals,
      ...(this.pvtResult() ? { pvt: this.pvtResult() } : {}),
      habitat: {
        radiation_mSv_day: env.radiation_mSv_day,
        cabin_co2_mmHg: env.cabin_co2_mmHg ?? 0,
      },
      compliance: {
        morning_vitals_logged: this.morningVitalsLogged,
        ared_resistive_minutes: this.exerciseMinutes,
        ration_intake_pct: this.rationPct,
      },
      ...(this.attachments().length ? { attachments: this.attachments() } : {}),
    };
    this.saveMeasurement.emit(measurement);
  }

  private numberOrUndefined(value: number | null): number | undefined {
    return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
  }
}