import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./pages/dashboard-page/dashboard-page.component').then((m) => m.DashboardPageComponent),
  },
  {
    path: 'triage',
    loadComponent: () => import('./pages/triage-page/triage-page.component').then((m) => m.TriagePageComponent),
  },
  {
    path: 'planner',
    loadComponent: () => import('./pages/planner-page/planner-page.component').then((m) => m.PlannerPageComponent),
  },
  {
    path: 'dev/figures',
    loadComponent: () => import('./pages/figure-gallery/figure-gallery.component').then((m) => m.FigureGalleryComponent),
  },
  { path: '**', redirectTo: '' },
];
