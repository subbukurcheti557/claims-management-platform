import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import {
  EnumLabelPipe,
  LoadStateComponent,
  MoneyPipe,
  PROGRESS_STEPS,
  STATUS_META,
  StatusChipComponent,
  TimelineComponent,
  progressIndex,
} from '@icmp/shared';

import { ClaimDetailStore } from './claim-detail.store';

@Component({
  selector: 'claimant-claim-detail',
  imports: [
    DatePipe,
    EnumLabelPipe,
    FormsModule,
    LoadStateComponent,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MoneyPipe,
    RouterLink,
    StatusChipComponent,
    TimelineComponent,
  ],
  providers: [ClaimDetailStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './claim-detail.page.html',
  styleUrl: './claim-detail.page.scss',
})
export class ClaimDetailPage {
  protected readonly store = inject(ClaimDetailStore);

  /** Route parameter (component input binding). */
  readonly id = input.required<string>();
  /** Set right after FNOL. */
  readonly submitted = input<string>();

  protected readonly steps = PROGRESS_STEPS;
  protected readonly meta = STATUS_META;
  protected readonly answers = signal<Record<string, string>>({});
  protected readonly confirmWithdraw = signal(false);
  protected readonly withdrawReason = signal('');

  protected readonly current = computed(() => {
    const c = this.store.claim();
    return c ? progressIndex(c.status) : 0;
  });
  protected readonly decisionLabel = computed(() => (this.store.claim()?.status === 'REJECTED' ? 'Rejected' : 'Decision'));

  constructor() {
    effect(() => this.store.load(this.id()));
  }

  protected setAnswer(requestId: string, value: string): void {
    this.answers.update((a) => ({ ...a, [requestId]: value }));
  }

  protected sendAnswer(requestId: string): void {
    const text = (this.answers()[requestId] ?? '').trim();
    if (!text) return;
    this.store.respond(requestId, text, () => this.setAnswer(requestId, ''));
  }

  protected withdraw(): void {
    this.store.withdraw(this.withdrawReason().trim(), () => this.confirmWithdraw.set(false));
  }
}
