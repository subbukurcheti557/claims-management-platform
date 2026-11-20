import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { ClaimsApi, Exposure, Market, Workload, errorMessage } from '@icmp/shared';
import { forkJoin } from 'rxjs';

/**
 * Manager dashboard state: liability exposure and team workload, optionally for one market. Refreshes every 30 s and
 * when the browser tab regains focus, so new claims and decisions show up without a reload.
 */
@Injectable()
export class DashboardStore {
  static readonly REFRESH_MS = 30_000;

  private readonly api = inject(ClaimsApi);
  private timer: ReturnType<typeof setInterval> | null = null;
  private readonly onFocus = () => this.load();

  readonly lastUpdated = signal<Date | null>(null);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopAutoRefresh());
  }

  startAutoRefresh(): void {
    if (this.timer) return;
    this.timer = setInterval(() => this.load(), DashboardStore.REFRESH_MS);
    window.addEventListener('focus', this.onFocus);
  }

  stopAutoRefresh(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
    window.removeEventListener('focus', this.onFocus);
  }

  readonly market = signal<Market | undefined>(undefined);
  readonly exposure = signal<Exposure | null>(null);
  readonly workload = signal<Workload | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** Largest market outstanding, used to scale the bars. */
  readonly maxMarketOutstanding = computed(() =>
    Math.max(1, ...(this.exposure()?.byMarket.map((m) => m.outstandingUsd + m.paidUsd) ?? [1])),
  );
  readonly maxAgeing = computed(() => Math.max(1, ...(this.exposure()?.ageing.map((a) => a.outstandingUsd) ?? [1])));
  readonly openStatuses = computed(() => this.exposure()?.byStatus.filter((s) => s.outstandingUsd > 0 || s.claims > 0) ?? []);
  readonly maxOfficerOpen = computed(() => Math.max(1, ...(this.workload()?.officers.map((o) => o.openClaims) ?? [1])));

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({ exposure: this.api.exposure(this.market()), workload: this.api.workload() }).subscribe({
      next: ({ exposure, workload }) => {
        this.exposure.set(exposure);
        this.workload.set(workload);
        this.loading.set(false);
        this.lastUpdated.set(new Date());
      },
      error: (err: unknown) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  setMarket(market: Market | undefined): void {
    this.market.set(market);
    this.load();
  }
}
