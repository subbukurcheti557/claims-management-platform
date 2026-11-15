import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import {
  AuthStore,
  EnumLabelPipe,
  LoadStateComponent,
  MARKET_NAMES,
  MoneyPipe,
  PriorityChipComponent,
  RelativeTimePipe,
  StatusChipComponent,
  TimelineComponent,
} from '@icmp/shared';

import { ActionPanelComponent } from './action-panel.component';
import { WorkspaceStore } from './workspace.store';

@Component({
  selector: 'staff-claim-workspace',
  imports: [
    ActionPanelComponent,
    DatePipe,
    EnumLabelPipe,
    FormsModule,
    LoadStateComponent,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MoneyPipe,
    PriorityChipComponent,
    RelativeTimePipe,
    RouterLink,
    StatusChipComponent,
    TimelineComponent,
  ],
  providers: [WorkspaceStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './claim-workspace.page.html',
  styleUrl: './claim-workspace.page.scss',
})
export class ClaimWorkspacePage {
  protected readonly store = inject(WorkspaceStore);
  protected readonly auth = inject(AuthStore);
  protected readonly marketNames = MARKET_NAMES;
  protected readonly noteText = signal('');

  readonly id = input.required<string>();

  constructor() {
    effect(() => this.store.load(this.id()));
  }

  protected saveNote(): void {
    const body = this.noteText().trim();
    if (body) this.store.addNote(body, () => this.noteText.set(''));
  }
}
