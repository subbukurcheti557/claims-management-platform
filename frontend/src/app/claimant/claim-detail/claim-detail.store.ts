import { Injectable, computed, inject, signal } from '@angular/core';
import { ClaimDetail, ClaimStatus, ClaimsApi, TimelineEntry, errorMessage } from '@icmp/shared';
import { forkJoin } from 'rxjs';

/** Statuses in which the claimant can still add evidence (mirrors claims-service): until the decision. */
const EVIDENCE_OPEN: readonly ClaimStatus[] = ['SUBMITTED', 'UNDER_REVIEW', 'INFO_REQUESTED', 'ASSESSED'];

/** One claim as the claimant sees it, plus what they can do: answer a question, add evidence, withdraw. */
@Injectable()
export class ClaimDetailStore {
  private readonly api = inject(ClaimsApi);

  readonly claim = signal<ClaimDetail | null>(null);
  readonly timeline = signal<TimelineEntry[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  /** Id of the info request being answered, or 'withdraw', while a command runs. */
  readonly busy = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);

  readonly openRequests = computed(() => this.claim()?.infoRequests.filter((r) => r.status === 'OPEN') ?? []);
  readonly answeredRequests = computed(() => this.claim()?.infoRequests.filter((r) => r.status !== 'OPEN') ?? []);
  readonly canAddDocuments = computed(() => {
    const status = this.claim()?.status;
    return !!status && EVIDENCE_OPEN.includes(status);
  });
  readonly canWithdraw = computed(() => this.claim()?.allowedActions.includes('WITHDRAW') ?? false);

  load(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({ claim: this.api.get(id), timeline: this.api.timeline(id) }).subscribe({
      next: ({ claim, timeline }) => {
        this.claim.set(claim);
        this.timeline.set(timeline);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  respond(requestId: string, response: string, done?: () => void): void {
    const claim = this.claim();
    if (!claim) return;
    this.run(requestId, () => this.api.respond(claim.id, requestId, response), done);
  }

  withdraw(reason: string, done?: () => void): void {
    const claim = this.claim();
    if (!claim) return;
    this.run('withdraw', () => this.api.withdraw(claim.id, reason || undefined), done);
  }

  private run(key: string, command: () => ReturnType<ClaimsApi['get']>, done?: () => void): void {
    this.busy.set(key);
    this.actionError.set(null);
    command().subscribe({
      next: (updated) => {
        this.claim.set(updated);
        this.busy.set(null);
        // the server is the source of truth: refresh the history after every change
        this.api.timeline(updated.id).subscribe((t) => this.timeline.set(t));
        done?.();
      },
      error: (err: unknown) => {
        this.actionError.set(errorMessage(err));
        this.busy.set(null);
      },
    });
  }
}
