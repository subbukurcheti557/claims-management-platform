import { Injectable, computed, inject, signal } from '@angular/core';
import { ClaimsApi, Exposure, Market, Workload, errorMessage } from '@icmp/shared';
import { forkJoin } from 'rxjs';

/** Manager dashboard state: liability exposure and team workload, optionally for one market. */
@Injectable()
export class DashboardStore {
  private readonly api = inject(ClaimsApi);

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
