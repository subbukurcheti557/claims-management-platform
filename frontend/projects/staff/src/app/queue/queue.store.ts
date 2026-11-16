import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import {
  ClaimStatus,
  ClaimSummary,
  ClaimType,
  ClaimsApi,
  Market,
  OfficerWorkload,
  Priority,
  QueueScope,
  errorMessage,
  isConflict,
} from '@icmp/shared';

export interface QueueFilters {
  status?: ClaimStatus;
  market?: Market;
  priority?: Priority;
  claimType?: ClaimType;
  overdueOnly: boolean;
  q: string;
}

const EMPTY_FILTERS: QueueFilters = { overdueOnly: false, q: '' };
const SCOPE_KEY = 'icmp.queue.scope';

/**
 * Work-queue state: scope tab, filters, paging and the "pick up" command. New claims arrive while the page is open,
 * so the queue refreshes itself every 30 s and when the browser tab regains focus (quietly, without the loading bar).
 */
@Injectable()
export class QueueStore {
  static readonly REFRESH_MS = 30_000;

  private readonly api = inject(ClaimsApi);
  private timer: ReturnType<typeof setInterval> | null = null;
  private readonly onFocus = () => this.refresh();

  readonly scope = signal<QueueScope>(QueueStore.readScope());
  readonly filters = signal<QueueFilters>(EMPTY_FILTERS);
  readonly page = signal(0);
  readonly pageSize = signal(20);
  readonly sort = signal('slaDueAt,asc');

  readonly rows = signal<ClaimSummary[]>([]);
  readonly total = signal(0);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly pickingUp = signal<string | null>(null);
  readonly myStats = signal<OfficerWorkload | null>(null);
  /** Claims waiting to be picked up, shown on the "To pick up" tab whichever tab is open. */
  readonly waiting = signal<number | null>(null);
  readonly lastUpdated = signal<Date | null>(null);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopAutoRefresh());
  }

  startAutoRefresh(): void {
    if (this.timer) return;
    this.timer = setInterval(() => this.refresh(), QueueStore.REFRESH_MS);
    window.addEventListener('focus', this.onFocus);
  }

  stopAutoRefresh(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
    window.removeEventListener('focus', this.onFocus);
  }

  /** Reloads the current view, the waiting count and my figures without showing the loading bar. */
  refresh(): void {
    this.load(true);
    this.loadMyStats();
  }

  readonly hasFilters = computed(() => {
    const f = this.filters();
    return !!(f.status || f.market || f.priority || f.claimType || f.overdueOnly || f.q);
  });

  load(quiet = false): void {
    if (!quiet) this.loading.set(true);
    this.error.set(null);
    this.loadWaiting();
    const f = this.filters();
    this.api
      .queue({
        scope: this.scope(),
        status: f.status,
        market: f.market,
        priority: f.priority,
        claimType: f.claimType,
        overdueOnly: f.overdueOnly,
        q: f.q.trim() || undefined,
        page: this.page(),
        size: this.pageSize(),
        sort: this.sort(),
      })
      .subscribe({
        next: (page) => {
          this.rows.set(page.content);
          this.total.set(page.totalElements);
          this.loading.set(false);
          this.lastUpdated.set(new Date());
        },
        error: (err: unknown) => {
          this.error.set(errorMessage(err));
          this.loading.set(false);
        },
      });
  }

  private loadWaiting(): void {
    this.api.queue({ scope: 'UNASSIGNED', overdueOnly: false, page: 0, size: 1 }).subscribe({
      next: (page) => this.waiting.set(page.totalElements),
      error: () => this.waiting.set(null),
    });
  }

  loadMyStats(): void {
    this.api.myStats().subscribe({ next: (s) => this.myStats.set(s), error: () => this.myStats.set(null) });
  }

  setScope(scope: QueueScope): void {
    this.scope.set(scope);
    this.page.set(0);
    try {
      localStorage.setItem(SCOPE_KEY, scope);
    } catch {
      // storage unavailable
    }
    this.load();
  }

  setFilters(patch: Partial<QueueFilters>): void {
    this.filters.update((f) => ({ ...f, ...patch }));
    this.page.set(0);
    this.load();
  }

  clearFilters(): void {
    this.filters.set(EMPTY_FILTERS);
    this.page.set(0);
    this.load();
  }

  setPage(page: number, size: number): void {
    this.page.set(page);
    this.pageSize.set(size);
    this.load();
  }

  setSort(active: string, direction: 'asc' | 'desc' | ''): void {
    this.sort.set(direction ? `${active},${direction}` : 'slaDueAt,asc');
    this.load();
  }

  /** Optimistic locking: sends the version the officer saw; a colleague who was faster wins with 409. */
  pickUp(row: ClaimSummary, onDone: (claimId: string) => void): void {
    this.pickingUp.set(row.id);
    this.notice.set(null);
    this.api.assign(row.id, row.version).subscribe({
      next: (claim) => {
        this.pickingUp.set(null);
        onDone(claim.id);
      },
      error: (err: unknown) => {
        this.pickingUp.set(null);
        this.notice.set(
          isConflict(err) ? `${row.claimNumber} was just picked up by a colleague. The queue has been refreshed.` : errorMessage(err),
        );
        this.load();
      },
    });
  }

  private static readScope(): QueueScope {
    try {
      const value = localStorage.getItem(SCOPE_KEY);
      return value === 'MINE' || value === 'TEAM' || value === 'ALL' ? value : 'UNASSIGNED';
    } catch {
      return 'UNASSIGNED';
    }
  }
}
