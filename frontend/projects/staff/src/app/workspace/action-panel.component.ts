import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import {
  ClaimAction,
  ClaimDetail,
  Liability,
  MoneyPipe,
  REJECTION_REASONS,
  RejectionReason,
  StaffMember,
  formatMoney,
} from '@icmp/shared';

import { WorkspaceAction } from './workspace.store';

/** Officer authority limit in USD (mirrors claims-service configuration; the server is authoritative). */
export const OFFICER_LIMIT_USD = 25_000;

type PanelTab = 'requestInfo' | 'assess' | 'approve' | 'settle' | 'reject';

/**
 * Presentational: shows only the actions the claim's status allows (from the server's allowedActions) and
 * emits commands. It holds form state but no business state.
 */
@Component({
  selector: 'staff-action-panel',
  imports: [
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MoneyPipe,
    ReactiveFormsModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './action-panel.component.html',
  styleUrl: './action-panel.component.scss',
})
export class ActionPanelComponent {
  private readonly fb = inject(NonNullableFormBuilder);

  readonly claim = input.required<ClaimDetail>();
  readonly canHandle = input.required<boolean>();
  readonly isManager = input(false);
  readonly staff = input<StaffMember[]>([]);
  readonly busy = input<WorkspaceAction | null>(null);

  readonly assign = output<string | undefined>();
  readonly requestInfo = output<string>();
  readonly assess = output<{ liability: Liability; recommendedAmount: number; reserveAmount: number; findings: string }>();
  readonly approve = output<number>();
  readonly settle = output<string>();
  readonly reject = output<{ reason: RejectionReason; explanation: string }>();

  protected readonly reasons = REJECTION_REASONS;
  protected readonly tab = signal<PanelTab | null>(null);

  protected readonly allowed = computed(() => new Set<ClaimAction>(this.claim().allowedActions));
  protected readonly tabs = computed(() => {
    const a = this.allowed();
    const list: { value: PanelTab; label: string }[] = [];
    if (a.has('ASSESS')) list.push({ value: 'assess', label: this.claim().status === 'ASSESSED' ? 'Re-assess' : 'Assess' });
    if (a.has('APPROVE')) list.push({ value: 'approve', label: 'Approve' });
    if (a.has('SETTLE')) list.push({ value: 'settle', label: 'Record payment' });
    if (a.has('REQUEST_INFO')) list.push({ value: 'requestInfo', label: 'Ask claimant' });
    if (a.has('REJECT')) list.push({ value: 'reject', label: 'Reject' });
    return list;
  });
  protected readonly assignable = computed(() => this.staff().filter((s) => s.id !== this.claim().assignedOfficerId));

  protected readonly assignForm = this.fb.group({ officerId: [''] });
  protected readonly infoForm = this.fb.group({ question: ['', [Validators.required, Validators.minLength(5)]] });
  protected readonly assessForm = this.fb.group({
    liability: ['ACCEPTED' as Liability, Validators.required],
    recommendedAmount: [0, [Validators.required, Validators.min(0)]],
    reserveAmount: [0, [Validators.required, Validators.min(0)]],
    findings: ['', [Validators.required, Validators.minLength(10)]],
  });
  protected readonly approveForm = this.fb.group({ amount: [0, [Validators.required, Validators.min(0.01)]] });
  protected readonly settleForm = this.fb.group({ paymentReference: ['', Validators.required] });
  protected readonly rejectForm = this.fb.group({
    reason: ['' as RejectionReason | '', Validators.required],
    explanation: ['', [Validators.required, Validators.minLength(10)]],
  });

  /** USD equivalent of the approve amount, using the claim's own estimate conversion. */
  protected readonly approveUsd = signal(0);
  protected readonly overLimit = computed(() => !this.isManager() && this.approveUsd() > OFFICER_LIMIT_USD);

  constructor() {
    // When the claim changes (after a command), reset forms to sensible defaults and pick the next step.
    effect(() => {
      const c = this.claim();
      const latest = c.latestAssessment;
      const recommended = latest?.recommendedAmount ?? c.reserveAmount ?? 0;
      this.assessForm.reset({
        liability: latest?.liability ?? 'ACCEPTED',
        recommendedAmount: recommended,
        reserveAmount: latest?.reserveAmount ?? c.reserveAmount ?? 0,
        findings: '',
      });
      this.approveForm.reset({ amount: latest?.recommendedAmount ?? 0 });
      this.updateUsd(latest?.recommendedAmount ?? 0);
      this.infoForm.reset({ question: '' });
      this.settleForm.reset({ paymentReference: '' });
      this.rejectForm.reset({ reason: '', explanation: '' });
      const first = this.tabs()[0]?.value ?? null;
      this.tab.set(first);
    });
    this.approveForm.controls.amount.valueChanges.subscribe((v) => this.updateUsd(Number(v) || 0));
  }

  protected maxPayableText(): string {
    const c = this.claim();
    return formatMoney(c.maxPayable, c.currency);
  }

  protected submitAssign(): void {
    const id = this.assignForm.controls.officerId.value;
    this.assign.emit(id || undefined);
  }

  protected submitInfo(): void {
    if (this.infoForm.invalid) return this.infoForm.markAllAsTouched();
    this.requestInfo.emit(this.infoForm.controls.question.value.trim());
  }

  protected submitAssess(): void {
    if (this.assessForm.invalid) return this.assessForm.markAllAsTouched();
    const v = this.assessForm.getRawValue();
    this.assess.emit({
      liability: v.liability,
      recommendedAmount: Number(v.recommendedAmount),
      reserveAmount: Number(v.reserveAmount),
      findings: v.findings.trim(),
    });
  }

  protected onLiability(value: Liability): void {
    if (value === 'DENIED') {
      this.assessForm.patchValue({ recommendedAmount: 0, reserveAmount: 0 });
    }
  }

  protected submitApprove(): void {
    if (this.approveForm.invalid) return this.approveForm.markAllAsTouched();
    this.approve.emit(Number(this.approveForm.controls.amount.value));
  }

  protected submitSettle(): void {
    if (this.settleForm.invalid) return this.settleForm.markAllAsTouched();
    this.settle.emit(this.settleForm.controls.paymentReference.value.trim());
  }

  protected submitReject(): void {
    if (this.rejectForm.invalid) return this.rejectForm.markAllAsTouched();
    const v = this.rejectForm.getRawValue();
    this.reject.emit({ reason: v.reason as RejectionReason, explanation: v.explanation.trim() });
  }

  private updateUsd(amount: number): void {
    const c = this.claim();
    const rate = c.estimatedLossUsd && c.estimatedLoss ? c.estimatedLossUsd / c.estimatedLoss : 0;
    this.approveUsd.set(Math.round(amount * rate));
  }
}
