import { ClaimStatus, Priority } from '../models/api.models';

export interface StatusMeta {
  label: string;
  /** Material Symbols icon name, so status is never conveyed by colour alone. */
  icon: string;
  tone: 'neutral' | 'info' | 'warning' | 'success' | 'danger';
  /** Plain-language explanation shown to claimants. */
  claimantHint: string;
}

export const STATUS_META: Readonly<Record<ClaimStatus, StatusMeta>> = {
  SUBMITTED: { label: 'Submitted', icon: 'inbox', tone: 'info', claimantHint: 'We have your claim and an officer will pick it up shortly.' },
  UNDER_REVIEW: { label: 'Under review', icon: 'manage_search', tone: 'info', claimantHint: 'A claims officer is reviewing your claim.' },
  INFO_REQUESTED: { label: 'Information needed', icon: 'help', tone: 'warning', claimantHint: 'Your claims officer needs more information from you.' },
  ASSESSED: { label: 'Assessed', icon: 'fact_check', tone: 'info', claimantHint: 'Your claim has been assessed and a decision is being made.' },
  APPROVED: { label: 'Approved', icon: 'thumb_up', tone: 'success', claimantHint: 'Your claim is approved. Payment is being arranged.' },
  SETTLED: { label: 'Settled', icon: 'paid', tone: 'success', claimantHint: 'Payment has been made. This claim is closed.' },
  REJECTED: { label: 'Rejected', icon: 'block', tone: 'danger', claimantHint: 'Your claim was not approved. See the explanation below.' },
  WITHDRAWN: { label: 'Withdrawn', icon: 'undo', tone: 'neutral', claimantHint: 'You withdrew this claim.' },
};

export const PRIORITY_META: Readonly<Record<Priority, { label: string; icon: string; tone: StatusMeta['tone'] }>> = {
  HIGH: { label: 'High', icon: 'priority_high', tone: 'danger' },
  MEDIUM: { label: 'Medium', icon: 'drag_handle', tone: 'warning' },
  LOW: { label: 'Low', icon: 'south', tone: 'neutral' },
};

/** The claimant-facing progress steps, in order. */
export const PROGRESS_STEPS: readonly { status: ClaimStatus; label: string }[] = [
  { status: 'SUBMITTED', label: 'Submitted' },
  { status: 'UNDER_REVIEW', label: 'In review' },
  { status: 'ASSESSED', label: 'Assessed' },
  { status: 'APPROVED', label: 'Decision' },
  { status: 'SETTLED', label: 'Paid' },
];

/** Index of the progress step a status belongs to (INFO_REQUESTED sits inside "In review"). */
export function progressIndex(status: ClaimStatus): number {
  switch (status) {
    case 'SUBMITTED':
      return 0;
    case 'UNDER_REVIEW':
    case 'INFO_REQUESTED':
      return 1;
    case 'ASSESSED':
      return 2;
    case 'APPROVED':
    case 'REJECTED':
      return 3;
    case 'SETTLED':
      return 4;
    case 'WITHDRAWN':
      return -1;
  }
}
