import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { TimelineEntry } from '../models/api.models';
import { STATUS_META } from './status';

/** The audit trail of a claim, newest last. Used by both the claimant and the staff views. */
@Component({
  selector: 'icmp-timeline',
  imports: [DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (entries().length === 0) {
      <p class="empty">No history yet.</p>
    } @else {
      <ol class="timeline" aria-label="Claim history">
        @for (entry of entries(); track $index) {
          <li>
            <span class="dot material-symbols-outlined" aria-hidden="true">{{ meta[entry.toStatus].icon }}</span>
            <div class="body">
              <div class="head">
                <strong>{{ meta[entry.toStatus].label }}</strong>
                <time [attr.datetime]="entry.occurredAt">{{ entry.occurredAt | date: 'd MMM y, HH:mm' }}</time>
              </div>
              @if (entry.comment) {
                <p class="comment">{{ entry.comment }}</p>
              }
              @if (showActors() && entry.actorName) {
                <p class="actor">{{ entry.actorName }}</p>
              }
            </div>
          </li>
        }
      </ol>
    }
  `,
  styles: `
    .timeline { list-style: none; margin: 0; padding: 0; display: grid; gap: 0; }
    li { display: grid; grid-template-columns: 32px 1fr; gap: 12px; position: relative; padding-bottom: 16px; }
    li:not(:last-child)::before {
      content: ''; position: absolute; left: 15px; top: 30px; bottom: 0; width: 2px; background: var(--icmp-line);
    }
    .dot {
      width: 32px; height: 32px; border-radius: 50%; display: grid; place-items: center; font-size: 18px;
      background: var(--icmp-surface-2); color: var(--icmp-accent);
    }
    .head { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 4px 12px; }
    time, .actor { color: var(--icmp-muted); font-size: 0.8125rem; }
    .comment { margin: 2px 0 0; overflow-wrap: anywhere; }
    .actor { margin: 2px 0 0; }
    .empty { color: var(--icmp-muted); }
  `,
})
export class TimelineComponent {
  readonly entries = input.required<TimelineEntry[]>();
  readonly showActors = input(true);
  protected readonly meta = STATUS_META;
}
