import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';

import { AssessmentResponse } from '../../../models/contract';
import { ModalShellComponent } from '../modal-shell/modal-shell.component';
import { Attachment, SCAN_GUIDES, ScanKind } from './scan-guides';

// Guided probe / electrode placement plus a simulated capture that is attached to today's record.
// Store-and-forward only: nothing in this app looks at or interprets the image.
@Component({
  selector: 'app-ultrasound-attachment-modal',
  imports: [ModalShellComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ultrasound-attachment-modal.component.html',
  styles: `
    .skin { fill: #16212f; stroke: #7d8ea3; stroke-width: 2; stroke-linejoin: round; }
    .hint { fill: none; stroke: #4f6682; stroke-width: 1.5; stroke-linecap: round; }
    .target { fill: none; stroke: var(--color-signal); stroke-width: 1.5; stroke-dasharray: 3 4; }
    .probe { fill: #dfe8f2; stroke: #060a10; stroke-width: 1.5; stroke-linejoin: round; }
    .cable { fill: none; stroke: #7d8ea3; stroke-width: 2; stroke-linecap: round; }
    .gel { fill: var(--color-signal); opacity: 0.35; }
    .tag { fill: #060a10; stroke: var(--color-signal); stroke-width: 1.25; }
    .tag-text { fill: var(--color-signal); font: 500 10px "IBM Plex Mono", monospace; text-anchor: middle; dominant-baseline: central; }
    .electrode { fill: var(--color-signal); stroke: #060a10; stroke-width: 1.5; }
  `,
})
export class UltrasoundAttachmentModalComponent {
  readonly assessment = input.required<AssessmentResponse>();
  readonly initialKind = input<ScanKind>('OCULAR_ULTRASOUND');
  readonly attached = output<Attachment>();
  readonly closed = output<void>();

  protected readonly guides = SCAN_GUIDES;
  private readonly chosenKind = signal<ScanKind | null>(null);
  protected readonly kind = computed(() => this.chosenKind() ?? this.initialKind());
  protected readonly guide = computed(() => SCAN_GUIDES.find((g) => g.kind === this.kind()) ?? SCAN_GUIDES[0]);

  // the simulated frame on screen: a seed for the noise, and when it was taken
  protected readonly capture = signal<{ seed: number; atIso: string } | null>(null);
  protected readonly note = signal('');
  protected readonly justAttached = signal<Attachment | null>(null);

  protected readonly autonomous = computed(() => this.assessment().comms.mode === 'AUTONOMOUS');
  protected readonly ecgTrace = computed(() => ecgPath(this.capture()?.seed ?? 1));

  protected choose(kind: ScanKind): void {
    this.chosenKind.set(kind);
    this.capture.set(null);
    this.justAttached.set(null);
  }

  protected takeCapture(): void {
    this.capture.set({ seed: Math.floor(Math.random() * 900) + 1, atIso: new Date().toISOString() });
    this.justAttached.set(null);
  }

  protected attach(): void {
    const capture = this.capture();
    if (!capture) {
      return;
    }
    const note = this.note().trim();
    const attachment: Attachment = {
      kind: this.kind(),
      ref: `sim://${this.kind().toLowerCase()}/${this.assessment().astronaut_id}/${capture.atIso}`,
      captured_at_iso: capture.atIso,
      ...(note ? { note } : {}),
      simulated: true,
    };
    this.attached.emit(attachment);
    this.justAttached.set(attachment);
    this.capture.set(null);
    this.note.set('');
  }
}

// A made-up heartbeat trace for the simulated strip: flat line, small P wave, sharp QRS, rounded T.
function ecgPath(seed: number): string {
  const beat = 64 + (seed % 5) * 4;
  let d = 'M0 60';
  for (let x = 0; x < 320; x += beat) {
    d += ` L${x + 10} 60 Q${x + 15} 52 ${x + 20} 60 L${x + 26} 60 L${x + 29} 66 L${x + 33} 22 L${x + 37} 74 L${x + 40} 60`;
    d += ` L${x + 46} 60 Q${x + 54} 44 ${x + 62} 60`;
  }
  return d + ' L320 60';
}
