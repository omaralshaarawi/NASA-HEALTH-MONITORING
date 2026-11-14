import { ChangeDetectionStrategy, Component, input } from '@angular/core';

// Page heading: small caption, title, and an optional line of text passed as content.
@Component({
  selector: 'app-section-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <div class="eyebrow">{{ eyebrow() }}</div>
    <h1 class="display m-0 mt-2 text-3xl leading-tight text-ink sm:text-4xl">{{ heading() }}</h1>
    <div class="mt-3 max-w-3xl text-sm leading-6 text-ink-dim"><ng-content /></div>
  `,
})
export class SectionHeaderComponent {
  readonly eyebrow = input.required<string>();
  readonly heading = input.required<string>();
}
