import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-resource-chips',
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dl class="m-0 flex flex-wrap gap-x-5 gap-y-1" [class.text-base]="large()" [class.text-sm]="!large()">
      <div class="flex items-baseline gap-1.5">
        <dt class="sr-only">Extra food energy</dt>
        <dd class="m-0 num text-ink">+{{ kcal() | number }}</dd>
        <span class="unit">kcal</span>
      </div>
      <div class="flex items-baseline gap-1.5">
        <dt class="sr-only">Extra water</dt>
        <dd class="m-0 num text-ink">+{{ waterMl() | number }}</dd>
        <span class="unit">mL H₂O</span>
      </div>
      <div class="flex items-baseline gap-1.5">
        <dt class="sr-only">Extra carbon dioxide</dt>
        <dd class="m-0 num text-ink">+{{ co2G() | number }}</dd>
        <span class="unit">g CO₂</span>
      </div>
    </dl>
  `,
})
export class ResourceChipsComponent {
  readonly kcal = input.required<number>();
  readonly waterMl = input.required<number>();
  readonly co2G = input.required<number>();
  readonly large = input(false);
}
