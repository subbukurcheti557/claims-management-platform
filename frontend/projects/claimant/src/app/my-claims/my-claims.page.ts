import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { RouterLink } from '@angular/router';
import {
  EnumLabelPipe,
  LoadStateComponent,
  MoneyPipe,
  PageHeaderComponent,
  STATUS_META,
  StatusChipComponent,
} from '@icmp/shared';

import { ClaimFilter, MyClaimsStore } from './my-claims.store';

@Component({
  selector: 'claimant-my-claims',
  imports: [
    DatePipe,
    EnumLabelPipe,
    LoadStateComponent,
    MatButtonModule,
    MatButtonToggleModule,
    MoneyPipe,
    PageHeaderComponent,
    RouterLink,
    StatusChipComponent,
  ],
  providers: [MyClaimsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './my-claims.page.html',
  styleUrl: './my-claims.page.scss',
})
export class MyClaimsPage implements OnInit {
  protected readonly store = inject(MyClaimsStore);
  protected readonly meta = STATUS_META;
  protected readonly filters: { value: ClaimFilter; label: string }[] = [
    { value: 'ALL', label: 'All' },
    { value: 'OPEN', label: 'Open' },
    { value: 'CLOSED', label: 'Closed' },
  ];

  ngOnInit(): void {
    this.store.load();
  }
}
