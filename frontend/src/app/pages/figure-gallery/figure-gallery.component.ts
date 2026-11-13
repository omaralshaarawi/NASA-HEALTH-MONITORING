import { ChangeDetectionStrategy, Component } from '@angular/core';

import { ExerciseFigureComponent } from '../../components/planner/exercise-figure/exercise-figure.component';
import { FIGURES } from '../../components/planner/exercise-figure/figures';
import { exerciseTitle } from '../../components/planner/planner-labels';

// Dev page: every illustration side by side, to check the drawings. Open /dev/figures.
@Component({
  selector: 'app-figure-gallery',
  imports: [ExerciseFigureComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto grid max-w-[84rem] grid-cols-2 gap-4 p-6 lg:grid-cols-3">
      @for (id of ids; track id) {
        <div class="panel p-4">
          <div class="display mb-3 text-lg text-ink">{{ title(id) }}</div>
          <app-exercise-figure [exerciseId]="id" />
        </div>
      }
    </div>
  `,
})
export class FigureGalleryComponent {
  protected readonly ids = Object.keys(FIGURES).filter((id) => id !== 'resistive_vigorous');
  protected readonly title = exerciseTitle;
}
