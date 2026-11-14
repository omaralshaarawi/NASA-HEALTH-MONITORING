import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { EMPTY, Observable, TimeoutError, catchError, throwError, timeout } from 'rxjs';

import { environment } from '../../environments/environment';
import { CvExercise, CvSyncRequest, CvSyncResult, CvTelemetry } from '../models-local/cv-coach';

const QUICK_MS = 3000;
const SYNC_MS = 15_000;

// The camera coach runs on this machine (cv-service, port 5001). Every call fails with a plain
// sentence that can be shown as it is, so a stopped service never breaks the page.
@Injectable({ providedIn: 'root' })
export class CvCoachService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.cvServiceUrl;

  // a fresh id for each time the camera is opened, so stopping one never cuts off a newer one
  newStream(): string {
    return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
  }

  // the live MJPEG stream for that id
  feedUrl(stream: string): string {
    return `${this.base}/video_feed?stream=${encodeURIComponent(stream)}`;
  }

  // Tells cv-service to release the webcam now. Nothing to report if it is already gone.
  stop(stream: string): void {
    this.http
      .post(`${this.base}/stop`, null, { params: { stream } })
      .pipe(
        timeout(QUICK_MS),
        catchError(() => EMPTY),
      )
      .subscribe();
  }

  // the same, for when the tab is closing and a normal request may not get out
  stopOnUnload(stream: string): void {
    navigator.sendBeacon?.(`${this.base}/stop?stream=${encodeURIComponent(stream)}`);
  }

  exercises(): Observable<CvExercise[]> {
    return this.call(this.http.get<CvExercise[]>(`${this.base}/exercises`), QUICK_MS);
  }

  select(exerciseId: string): Observable<CvTelemetry> {
    return this.call(this.http.post<CvTelemetry>(`${this.base}/select`, { exercise_id: exerciseId }), QUICK_MS);
  }

  telemetry(): Observable<CvTelemetry> {
    return this.call(this.http.get<CvTelemetry>(`${this.base}/telemetry`), QUICK_MS);
  }

  sync(request: CvSyncRequest): Observable<CvSyncResult> {
    return this.call(this.http.post<CvSyncResult>(`${this.base}/sync-to-backend`, request), SYNC_MS);
  }

  private call<T>(request: Observable<T>, ms: number): Observable<T> {
    return request.pipe(
      timeout(ms),
      catchError((error: unknown) => throwError(() => new Error(cvErrorMessage(error, this.base)))),
    );
  }
}

export function cvErrorMessage(error: unknown, base: string): string {
  if (error instanceof TimeoutError) return 'The camera coach is not answering. Check that cv-service is still running.';
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) return `The camera coach is offline. Start cv-service on ${base} and try again.`;
    const detail = (error.error as { detail?: unknown } | null)?.detail;
    if (typeof detail === 'string') return detail;
  }
  return 'The camera coach could not do that. Try again.';
}
