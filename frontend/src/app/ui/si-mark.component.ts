import { ChangeDetectionStrategy, Component, input } from '@angular/core';

// sparkle watermark for AI-generated images (Space Apps rules)
@Component({
  selector: 'app-si-mark',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { role: 'img', '[attr.aria-label]': 'label()', '[attr.title]': 'label()' },
  styles: `
    :host {
      display: block;
      width: 1.375rem;
      height: 1.375rem;
    }
    svg {
      display: block;
      width: 100%;
      height: 100%;
      fill: rgb(255 255 255 / 0.82);
      filter: drop-shadow(0 1px 2px rgb(0 0 0 / 0.7));
    }
  `,
  template: `
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 0C12 6.6 17.4 12 24 12C17.4 12 12 17.4 12 24C12 17.4 6.6 12 0 12C6.6 12 12 6.6 12 0Z" />
    </svg>
  `,
})
export class SiMarkComponent {
  readonly label = input('SI-generated image');
}
