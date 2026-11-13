import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { EnvironmentProfile } from '../../models/contract';

@Component({
  selector: 'app-habitat-switcher',
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="panel p-4" aria-labelledby="habitat-heading">
      <div class="flex items-start justify-between gap-3">
        <div>
          <div class="eyebrow">Mission context</div>
          <h2 id="habitat-heading" class="display m-0 mt-1 text-xl text-ink">Habitat · distance · day</h2>
        </div>
        <span class="chip num">DAY {{ day() }}</span>
      </div>
      <div class="mt-4 grid gap-2 sm:grid-cols-2">
        @for (environment of environments(); track environment.id) {
          <button type="button" class="rounded border px-3 py-2 text-left transition-colors"
                  [disabled]="disabled()" [class.border-signal]="environment.id === selectedEnvironmentId()"
                  [class.bg-raised]="environment.id === selectedEnvironmentId()" [class.border-line]="environment.id !== selectedEnvironmentId()"
                  (click)="environmentSelected.emit(environment)">
            <span class="display block text-base text-ink">{{ environment.label }}</span>
            <span class="eyebrow">{{ environment.gravity_g }} g · {{ environment.comm_label }}</span>
          </button>
        }
      </div>
      @if (selectedEnvironment(); as selected) {
        @if (selected.distance_range_km; as range) {
          <label class="mt-5 block">
            <span class="flex justify-between gap-3 text-sm text-ink-dim">
              <span>Earth distance</span><span class="num">{{ distanceKm() | number:'1.0-0' }} km</span>
            </span>
            <input class="mt-2 w-full accent-signal" type="range" [min]="range.min" [max]="range.max" step="1000000"
                   [value]="distanceKm()" [disabled]="disabled()" (change)="distanceChanged($event)" />
          </label>
        }
      }
      <label class="mt-5 block">
        <span class="flex justify-between gap-3 text-sm text-ink-dim">
          <span>Mission day</span><span class="num">DAY {{ day() }}</span>
        </span>
        <input class="mt-2 w-full accent-signal" type="range" min="1" [max]="maxDay()" step="1" [value]="day()"
               [disabled]="disabled()" (change)="dayChanged($event)" />
      </label>
    </section>
  `,
})
export class HabitatSwitcherComponent {
  readonly environments = input.required<EnvironmentProfile[]>();
  readonly selectedEnvironmentId = input.required<string>();
  readonly distanceKm = input.required<number>();
  readonly day = input.required<number>();
  readonly maxDay = input(14);
  readonly disabled = input(false);
  readonly environmentSelected = output<EnvironmentProfile>();
  readonly distanceSelected = output<number>();
  readonly daySelected = output<number>();

  protected selectedEnvironment(): EnvironmentProfile | undefined {
    return this.environments().find((environment) => environment.id === this.selectedEnvironmentId());
  }

  protected distanceChanged(event: Event): void {
    this.distanceSelected.emit(Number((event.target as HTMLInputElement).value));
  }

  protected dayChanged(event: Event): void {
    this.daySelected.emit(Number((event.target as HTMLInputElement).value));
  }
}