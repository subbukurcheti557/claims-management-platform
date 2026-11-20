import { DatePipe, KeyValuePipe, PercentPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import {
  AuthStore,
  CompactMoneyPipe,
  EnumLabelPipe,
  LoadStateComponent,
  MARKETS,
  MARKET_NAMES,
  MoneyPipe,
  PageHeaderComponent,
} from '@icmp/shared';

import { DashboardStore } from './dashboard.store';

@Component({
  selector: 'staff-dashboard',
  imports: [
    CompactMoneyPipe,
    DatePipe,
    KeyValuePipe,
    EnumLabelPipe,
    LoadStateComponent,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MoneyPipe,
    PageHeaderComponent,
    PercentPipe,
    RouterLink,
  ],
  providers: [DashboardStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
})
export class DashboardPage implements OnInit {
  protected readonly store = inject(DashboardStore);
  protected readonly auth = inject(AuthStore);
  protected readonly markets = MARKETS;
  protected readonly marketNames = MARKET_NAMES;

  ngOnInit(): void {
    this.store.load();
    this.store.startAutoRefresh();
  }

  protected pct(value: number, max: number): string {
    return value <= 0 ? "0%" : `${Math.max(2, Math.round((value / max) * 100))}%`;
  }
}
