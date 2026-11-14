import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

import { MissionStore } from '../core/mission-store.service';

// Shown in place of a page's content while there is no assessment to display.
@Component({
  selector: 'app-awaiting-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <section class="panel mt-6 p-6" role="status">
      @if (store.pendingDay(); as day) {
        <div class="display text-lg text-ink">Awaiting day {{ day }} readings</div>
        <p class="m-0 mt-2 text-sm leading-6 text-ink-dim">{{ hint() }}</p>
        <ng-content />
      } @else {
        <div class="display text-lg text-ink">{{ store.loading() ? 'Loading crew data…' : "Awaiting today's readings" }}</div>
        @if (!store.loading()) {
          <p class="m-0 mt-2 text-sm leading-6 text-ink-dim">{{ hint() }}</p>
        }
      }
    </section>
  `,
})
export class AwaitingPanelComponent {
  protected readonly store = inject(MissionStore);

  readonly hint = input("There is no assessment for today yet. It appears here once today's readings have been logged on the Health page.");
}
