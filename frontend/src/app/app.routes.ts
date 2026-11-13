import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'planner' },
  {
    path: 'planner',
    loadComponent: () => import('./pages/planner-page/planner-page.component').then((m) => m.PlannerPageComponent),
  },
  {
    path: 'dev/figures',
    loadComponent: () => import('./pages/figure-gallery/figure-gallery.component').then((m) => m.FigureGalleryComponent),
  },
];
