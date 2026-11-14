import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { FIGURES, Point } from './figures';

// Inline SVG pictogram for one countermeasure. No images or video, so it is instant and prints well.
@Component({
  selector: 'app-exercise-figure',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './exercise-figure.component.html',
  styles: `
    :host { display: block; }
    .body { stroke: #dfe8f2; }
    .body-far { stroke: #64788f; }
    .gear { stroke: #4f6682; fill: none; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
    .gear-fill { fill: #16212f; stroke: #4f6682; stroke-width: 2; stroke-linejoin: round; }
    .accent { stroke: var(--color-signal); fill: none; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
    .motion { stroke: var(--color-signal); fill: none; stroke-width: 1.5; stroke-dasharray: 3 4; stroke-linecap: round; }
    .grid { stroke: #1a2636; stroke-width: 1; }
    .tag { fill: #060a10; stroke: var(--color-signal); stroke-width: 1.25; }
    .tag-text { fill: var(--color-signal); font: 500 9px "IBM Plex Mono", monospace; text-anchor: middle; dominant-baseline: central; }
  `,
})
export class ExerciseFigureComponent {
  readonly exerciseId = input.required<string>();

  protected readonly figure = computed(() => FIGURES[this.exerciseId()] ?? null);

  protected line(...points: Point[]): string {
    return points.map((p) => p.join(',')).join(' ');
  }
}
