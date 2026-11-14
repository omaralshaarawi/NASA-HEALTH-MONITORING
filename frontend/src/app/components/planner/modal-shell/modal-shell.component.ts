import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, booleanAttribute, input, output, viewChild } from '@angular/core';

// Shared dialog frame. Uses the native <dialog>, which gives focus trapping, Esc to close
// and a backdrop for free. Render it with @if and listen to (closed).
@Component({
  selector: 'app-modal-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog
      #dialog
      class="m-auto max-h-[calc(100dvh-2rem)] overflow-hidden rounded-[var(--radius-card)] border border-line-strong bg-[#11161d] p-0 text-ink shadow-2xl backdrop:bg-void/80 backdrop:backdrop-blur-sm"
      [class]="narrow() ? 'w-[min(32rem,calc(100vw-2rem))]' : 'w-[min(72rem,calc(100vw-2rem))]'"
      [attr.aria-labelledby]="titleId"
      (close)="closed.emit()"
      (click)="onBackdrop($event)"
    >
      <div class="flex max-h-[calc(100dvh-2rem)] flex-col">
        <header class="flex items-start justify-between gap-4 border-b border-line px-5 py-4 md:px-6">
          <div class="min-w-0">
            <div class="eyebrow">{{ eyebrow() }}</div>
            <h2 [id]="titleId" class="display m-0 mt-1 text-2xl leading-7 text-ink">{{ heading() }}</h2>
          </div>
          <button type="button" class="btn shrink-0" (click)="dialog.close()">Close</button>
        </header>
        <div class="overflow-y-auto px-5 py-5 md:px-6">
          <ng-content />
        </div>
      </div>
    </dialog>
  `,
})
export class ModalShellComponent implements AfterViewInit {
  private static nextId = 0;

  readonly heading = input.required<string>();
  readonly eyebrow = input('');
  // a short question rather than a full page of content
  readonly narrow = input(false, { transform: booleanAttribute });
  readonly closed = output<void>();

  protected readonly titleId = 'modal-title-' + ModalShellComponent.nextId++;
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  ngAfterViewInit(): void {
    this.dialog().nativeElement.showModal();
  }

  close(): void {
    this.dialog().nativeElement.close();
  }

  // a click on the dialog element itself (not its content) is a click on the backdrop
  protected onBackdrop(event: MouseEvent): void {
    if (event.target === this.dialog().nativeElement) {
      this.close();
    }
  }
}
