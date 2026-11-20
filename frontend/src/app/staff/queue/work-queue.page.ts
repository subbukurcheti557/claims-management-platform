import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  AuthStore,
  ClaimStatus,
  EnumLabelPipe,
  LoadStateComponent,
  MARKETS,
  MARKET_NAMES,
  MoneyPipe,
  PageHeaderComponent,
  PriorityChipComponent,
  QueueScope,
  RelativeTimePipe,
  STATUS_META,
  StatusChipComponent,
} from '@icmp/shared';

import { QueueStore } from './queue.store';

@Component({
  selector: 'staff-work-queue',
  imports: [
    DatePipe,
    EnumLabelPipe,
    FormsModule,
    LoadStateComponent,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatSortModule,
    MatTableModule,
    MatTabsModule,
    MoneyPipe,
    PageHeaderComponent,
    PriorityChipComponent,
    RelativeTimePipe,
    RouterLink,
    StatusChipComponent,
  ],
  providers: [QueueStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './work-queue.page.html',
  styleUrl: './work-queue.page.scss',
})
export class WorkQueuePage implements OnInit {
  protected readonly store = inject(QueueStore);
  protected readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly scopes: { value: QueueScope; label: string; icon: string }[] = [
    { value: 'UNASSIGNED', label: 'To pick up', icon: 'move_to_inbox' },
    { value: 'MINE', label: 'My claims', icon: 'person' },
    { value: 'TEAM', label: 'All open', icon: 'groups' },
    { value: 'ALL', label: 'Search all', icon: 'search' },
  ];
  protected readonly markets = MARKETS;
  protected readonly marketNames = MARKET_NAMES;
  protected readonly statuses = Object.keys(STATUS_META) as ClaimStatus[];
  protected readonly columns = ['claimNumber', 'claimant', 'market', 'status', 'priority', 'reserve', 'slaDueAt', 'officer', 'actions'];

  ngOnInit(): void {
    this.store.load();
    this.store.loadMyStats();
    this.store.startAutoRefresh();
  }

  protected scopeIndex(): number {
    return this.scopes.findIndex((s) => s.value === this.store.scope());
  }

  protected onTab(index: number): void {
    this.store.setScope(this.scopes[index].value);
  }

  protected onPage(e: PageEvent): void {
    this.store.setPage(e.pageIndex, e.pageSize);
  }

  protected onSort(e: Sort): void {
    this.store.setSort(e.active, e.direction);
  }

  protected open(id: string): void {
    void this.router.navigate(['..', 'claims', id], { relativeTo: this.route });
  }

  protected pickUp(event: Event, row: Parameters<QueueStore['pickUp']>[0]): void {
    event.stopPropagation();
    this.store.pickUp(row, (id) => this.open(id));
  }
}
