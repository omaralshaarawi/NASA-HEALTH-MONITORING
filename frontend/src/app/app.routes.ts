import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'home' },
  {
    path: 'home',
    title: 'Home',
    data: { hero: true },
    loadComponent: () => import('./pages/home-page/home-page.component').then((m) => m.HomePageComponent),
  },
  {
    path: 'health',
    title: 'Health',
    loadComponent: () => import('./pages/health-page/health-page.component').then((m) => m.HealthPageComponent),
  },
  { path: 'analysis', pathMatch: 'full', redirectTo: 'analysis/readings' },
  {
    path: 'analysis/readings',
    title: "Today's Readings & Triggers",
    loadComponent: () =>
      import('./pages/analysis-readings-page/analysis-readings-page.component').then((m) => m.AnalysisReadingsPageComponent),
  },
  {
    path: 'analysis/plan',
    title: 'Training & Nutrition Plan',
    loadComponent: () =>
      import('./pages/analysis-plan-page/analysis-plan-page.component').then((m) => m.AnalysisPlanPageComponent),
  },
  {
    path: 'mission',
    title: 'Mission',
    loadComponent: () => import('./pages/mission-page/mission-page.component').then((m) => m.MissionPageComponent),
  },
  {
    path: 'report',
    title: 'Daily Report',
    loadComponent: () => import('./pages/report-page/report-page.component').then((m) => m.ReportPageComponent),
  },
  {
    path: 'triage',
    title: 'Emergency triage',
    loadComponent: () => import('./pages/triage-page/triage-page.component').then((m) => m.TriagePageComponent),
  },
  {
    path: 'dev/figures',
    loadComponent: () => import('./pages/figure-gallery/figure-gallery.component').then((m) => m.FigureGalleryComponent),
  },
  // old addresses
  { path: 'planner', redirectTo: 'analysis/plan' },
  { path: '**', redirectTo: 'home' },
];
