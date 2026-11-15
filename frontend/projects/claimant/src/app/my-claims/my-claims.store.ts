import { Injectable, computed, inject, signal } from '@angular/core';
import { ClaimStatus, ClaimSummary, ClaimsApi, OPEN_STATUSES, errorMessage } from '@icmp/shared';

export type ClaimFilter = 'ALL' | 'OPEN' | 'CLOSED';

/** State for the claimant's claim list. Components read signals and call intent methods only. */
@Injectable()
export class MyClaimsStore {
  private readonly api = inject(ClaimsApi);

  private readonly all = signal<ClaimSummary[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly filter = signal<ClaimFilter>('ALL');

  readonly claims = computed(() => {
    const f = this.filter();
    return this.all().filter((c) => f === 'ALL' || (f === 'OPEN') === OPEN_STATUSES.includes(c.status));
  });
  readonly needsAction = computed(() => this.all().filter((c) => c.status === 'INFO_REQUESTED'));
  readonly counts = computed(() => {
    const list = this.all();
    const open = list.filter((c) => OPEN_STATUSES.includes(c.status)).length;
    return { ALL: list.length, OPEN: open, CLOSED: list.length - open } satisfies Record<ClaimFilter, number>;
  });

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.mine(undefined, 0, 100).subscribe({
      next: (page) => {
        this.all.set(page.content);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  setFilter(filter: ClaimFilter): void {
    this.filter.set(filter);
  }

  static isOpen(status: ClaimStatus): boolean {
    return OPEN_STATUSES.includes(status);
  }
}
