import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { ClaimStatus, Priority } from '../models/api.models';
import { PRIORITY_META, STATUS_META } from './status';

/** Claim status as icon + text + colour (never colour alone, for accessibility). */
@Component({
  selector: 'icmp-status-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="chip" [attr.data-tone]="meta().tone" [attr.aria-label]="'Status: ' + meta().label">
      <span class="material-symbols-outlined" aria-hidden="true">{{ meta().icon }}</span>
      {{ meta().label }}
    </span>
  `,
  styleUrl: './chip.scss',
})
export class StatusChipComponent {
  readonly status = input.required<ClaimStatus>();
  protected readonly meta = computed(() => STATUS_META[this.status()]);
}

@Component({
  selector: 'icmp-priority-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="chip" [attr.data-tone]="meta().tone" [attr.aria-label]="'Priority: ' + meta().label">
      <span class="material-symbols-outlined" aria-hidden="true">{{ meta().icon }}</span>
      {{ meta().label }}
    </span>
  `,
  styleUrl: './chip.scss',
})
export class PriorityChipComponent {
  readonly priority = input.required<Priority>();
  protected readonly meta = computed(() => PRIORITY_META[this.priority()]);
}
