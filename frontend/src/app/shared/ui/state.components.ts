import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';

/** Page title with an optional subtitle and an actions slot. */
@Component({
  selector: 'icmp-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-header">
      <div class="titles">
        @if (eyebrow()) {
          <span class="eyebrow">{{ eyebrow() }}</span>
        }
        <h1>{{ title() }}</h1>
        @if (subtitle()) {
          <p class="subtitle">{{ subtitle() }}</p>
        }
      </div>
      <div class="actions"><ng-content /></div>
    </header>
  `,
  styles: `
    .page-header { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 12px 24px; margin-bottom: 24px; }
    .titles { min-width: 0; }
    h1 { margin: 0; font: 600 1.75rem/1.2 var(--icmp-font-display); text-wrap: balance; }
    .eyebrow { font: 500 0.75rem var(--icmp-font-mono); letter-spacing: 0.06em; text-transform: uppercase; color: var(--icmp-muted); }
    .subtitle { margin: 6px 0 0; color: var(--icmp-muted); max-width: 70ch; }
    .actions { display: flex; flex-wrap: wrap; gap: 8px; }
  `,
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string>();
  readonly eyebrow = input<string>();
}

/** Loading bar / error with retry / empty message, so every async view handles all three states. */
@Component({
  selector: 'icmp-load-state',
  imports: [MatButtonModule, MatProgressBarModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading()) {
      <mat-progress-bar mode="indeterminate" aria-label="Loading" />
    } @else if (error()) {
      <div class="state error" role="alert">
        <span class="material-symbols-outlined" aria-hidden="true">error</span>
        <p>{{ error() }}</p>
        <button mat-stroked-button type="button" (click)="retry.emit()">Try again</button>
      </div>
    } @else if (empty()) {
      <div class="state">
        <span class="material-symbols-outlined" aria-hidden="true">{{ emptyIcon() }}</span>
        <p>{{ emptyMessage() }}</p>
        <ng-content />
      </div>
    }
  `,
  styles: `
    .state { display: grid; justify-items: center; gap: 8px; padding: 40px 16px; text-align: center; color: var(--icmp-muted); }
    .state .material-symbols-outlined { font-size: 40px; }
    .state p { margin: 0; max-width: 50ch; }
    .error { color: var(--icmp-tone-danger-fg); }
  `,
})
export class LoadStateComponent {
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly empty = input(false);
  readonly emptyMessage = input('Nothing here yet.');
  readonly emptyIcon = input('inbox');
  readonly retry = output<void>();
}
