import { Routes } from '@angular/router';

const placeholder = () => import('./pages/placeholder-page/placeholder-page.component').then((m) => m.PlaceholderPageComponent);

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'home' },
  {
    path: 'home',
    title: 'Home',
    data: { hero: true },
    loadComponent: () => import('./pages/home-page/home-page.component').then((m) => m.HomePageComponent),
  },
  { path: 'health', title: 'Health', data: { eyebrow: 'Body dashboard' }, loadComponent: placeholder },
  { path: 'analysis', pathMatch: 'full', redirectTo: 'analysis/readings' },
  {
    path: 'analysis/readings',
    title: "Today's Readings & Triggers",
    data: { eyebrow: 'Daily analysis' },
    loadComponent: placeholder,
  },
  {
    path: 'analysis/plan',
    title: 'Training & Nutrition Plan',
    data: { eyebrow: 'Daily analysis' },
    loadComponent: placeholder,
  },
  { path: 'mission', title: 'Mission', data: { eyebrow: 'Mission context' }, loadComponent: placeholder },
  { path: 'report', title: 'Daily Report', data: { eyebrow: 'Flight surgeon report' }, loadComponent: placeholder },
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
