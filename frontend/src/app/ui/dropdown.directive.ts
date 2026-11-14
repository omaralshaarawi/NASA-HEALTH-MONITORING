import { Directive, ElementRef, inject, signal } from '@angular/core';

// Open / close state and keyboard handling for a button that reveals a list:
// the Daily Analysis submenu and the astronaut switcher. Items are marked with data-item.
@Directive({
  selector: '[appDropdown]',
  exportAs: 'dropdown',
  host: {
    '(document:click)': 'closeIfOutside($event)',
    '(keydown)': 'onKey($event)',
  },
})
export class DropdownDirective {
  private readonly host: HTMLElement = inject(ElementRef).nativeElement;

  readonly open = signal(false);

  toggle(): void {
    this.open.update((open) => !open);
  }

  close(): void {
    this.open.set(false);
  }

  protected closeIfOutside(event: Event): void {
    if (this.open() && !this.host.contains(event.target as Node)) this.close();
  }

  protected onKey(event: KeyboardEvent): void {
    if (!this.open()) return;
    if (event.key === 'Escape') {
      this.close();
      this.host.querySelector<HTMLElement>('[aria-expanded]')?.focus();
      return;
    }
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
    const items = Array.from(this.host.querySelectorAll<HTMLElement>('[data-item]'));
    if (!items.length) return;
    event.preventDefault();
    const at = items.indexOf(document.activeElement as HTMLElement);
    const step = event.key === 'ArrowDown' ? 1 : -1;
    items[(at + step + items.length) % items.length].focus();
  }
}
