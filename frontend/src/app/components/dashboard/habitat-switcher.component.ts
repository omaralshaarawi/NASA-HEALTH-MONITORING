import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { EnvironmentProfile } from '../../models/contract';

// Pick a habitat, and for Mars the Earth distance. Every value shown comes from the backend's
// environment list; the assessment is recomputed by the backend for the choice.
@Component({
  selector: 'app-habitat-switcher',
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <div class="grid gap-2 sm:grid-cols-2" role="radiogroup" aria-label="Habitat">
      @for (environment of environments(); track environment.id) {
        <button
          type="button"
          role="radio"
          class="rounded-xl border px-3.5 py-2.5 text-left transition-colors"
          [class]="environment.id === selectedEnvironmentId() ? 'border-signal bg-white/[0.07]' : 'border-line hover:border-line-strong'"
          [attr.aria-checked]="environment.id === selectedEnvironmentId()"
          [disabled]="disabled()"
          (click)="environmentSelected.emit(environment)"
        >
          <span class="block text-sm text-ink">{{ environment.label }}</span>
          <span class="eyebrow">{{ environment.gravity_g }} g · {{ environment.comm_mode }}</span>
        </button>
      }
    </div>

    @if (selectedEnvironment()?.distance_range_km; as range) {
      <label class="mt-5 block">
        <span class="flex justify-between gap-3 text-sm text-ink-dim">
          <span>Earth distance</span>
          <span class="num text-ink">{{ distanceKm() | number: '1.0-0' }} km</span>
        </span>
        <input
          class="mt-2 w-full accent-[var(--color-signal)]"
          type="range"
          [min]="range.min"
          [max]="range.max"
          step="1000000"
          [value]="distanceKm()"
          [disabled]="disabled()"
          (change)="distanceChanged($event)"
        />
        <span class="mt-1 flex justify-between gap-3 text-xs text-ink-mute">
          <span class="num">{{ range.min | number: '1.0-0' }} km</span>
          <span class="num">{{ range.max | number: '1.0-0' }} km</span>
        </span>
      </label>
    }
  `,
})
export class HabitatSwitcherComponent {
  readonly environments = input.required<EnvironmentProfile[]>();
  readonly selectedEnvironmentId = input.required<string>();
  readonly distanceKm = input.required<number>();
  readonly disabled = input(false);
  readonly environmentSelected = output<EnvironmentProfile>();
  readonly distanceSelected = output<number>();

  protected selectedEnvironment(): EnvironmentProfile | undefined {
    return this.environments().find((environment) => environment.id === this.selectedEnvironmentId());
  }

  protected distanceChanged(event: Event): void {
    this.distanceSelected.emit(Number((event.target as HTMLInputElement).value));
  }
}
