/**
 * TypeScript view of the backend REST contract (each service's OpenAPI spec is the source of truth;
 * see contracts/openapi). Money is a number in the claim's local currency unless the name ends in Usd.
 */

export type Role = 'CLAIMANT' | 'CLAIMS_OFFICER' | 'CLAIMS_MANAGER';
export type ClaimType = 'MOTOR' | 'PROPERTY';
export type Market = 'SG' | 'HK' | 'AU' | 'MY' | 'TH' | 'ID';
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH';
export type Liability = 'ACCEPTED' | 'PARTIAL' | 'DENIED';
export type ClaimStatus =
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'INFO_REQUESTED'
  | 'ASSESSED'
  | 'APPROVED'
  | 'SETTLED'
  | 'REJECTED'
  | 'WITHDRAWN';
export type RejectionReason =
  | 'NOT_COVERED'
  | 'POLICY_EXCLUSION'
  | 'INSUFFICIENT_EVIDENCE'
  | 'LATE_NOTIFICATION'
  | 'FRAUD_SUSPECTED'
  | 'OTHER';
export type ClaimAction =
  | 'ASSIGN'
  | 'REASSIGN'
  | 'REQUEST_INFO'
  | 'ASSESS'
  | 'REJECT'
  | 'APPROVE'
  | 'SETTLE'
  | 'RESPOND'
  | 'WITHDRAW';
export type QueueScope = 'UNASSIGNED' | 'MINE' | 'TEAM' | 'ALL';

export const MARKETS: readonly Market[] = ['SG', 'HK', 'AU', 'MY', 'TH', 'ID'];
export const MARKET_NAMES: Readonly<Record<Market, string>> = {
  SG: 'Singapore',
  HK: 'Hong Kong',
  AU: 'Australia',
  MY: 'Malaysia',
  TH: 'Thailand',
  ID: 'Indonesia',
};
export const OPEN_STATUSES: readonly ClaimStatus[] = [
  'SUBMITTED',
  'UNDER_REVIEW',
  'INFO_REQUESTED',
  'ASSESSED',
  'APPROVED',
];
export const REJECTION_REASONS: readonly { code: RejectionReason; label: string }[] = [
  { code: 'NOT_COVERED', label: 'Not covered by the policy' },
  { code: 'POLICY_EXCLUSION', label: 'Policy exclusion applies' },
  { code: 'INSUFFICIENT_EVIDENCE', label: 'Insufficient evidence' },
  { code: 'LATE_NOTIFICATION', label: 'Reported too late' },
  { code: 'FRAUD_SUSPECTED', label: 'Could not be verified' },
  { code: 'OTHER', label: 'Other' },
];

export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  market: Market;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: UserProfile;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Policy {
  policyNumber: string;
  holderName: string;
  product: ClaimType;
  market: Market;
  currency: string;
  insuredItem: string;
  sumInsured: number;
  deductible: number;
  startDate: string;
  endDate: string;
  status: 'ACTIVE' | 'LAPSED' | 'CANCELLED';
  paidToDate: number;
  remainingSumInsured: number;
}

export interface ClaimSummary {
  id: string;
  claimNumber: string;
  policyNumber: string;
  claimType: ClaimType;
  market: Market;
  currency: string;
  status: ClaimStatus;
  incidentDate: string;
  estimatedLoss: number;
  submittedAt: string;
  updatedAt: string;
  assignedOfficerName?: string;
  openInfoRequests: number;
  version: number;
  // staff only
  claimantName?: string;
  priority?: Priority;
  reserveAmount?: number;
  slaDueAt?: string;
  overdue?: boolean;
  assignedOfficerId?: string;
  /** Claim number of an already paid claim for the same policy and incident date. */
  possibleDuplicateOf?: string;
}

export interface InfoRequest {
  id: string;
  question: string;
  response?: string;
  status: 'OPEN' | 'ANSWERED' | 'CANCELLED';
  requestedByName?: string;
  requestedAt: string;
  respondedAt?: string;
}

export interface Assessment {
  id: string;
  liability: Liability;
  recommendedAmount: number;
  reserveAmount: number;
  findings: string;
  assessorName: string;
  assessedAt: string;
}

export interface ClaimDetail {
  id: string;
  claimNumber: string;
  policyNumber: string;
  claimType: ClaimType;
  market: Market;
  currency: string;
  status: ClaimStatus;
  allowedActions: ClaimAction[];
  incidentDate: string;
  incidentLocation: string;
  description: string;
  injuryReported: boolean;
  estimatedLoss: number;
  insuredItem?: string;
  assignedOfficerName?: string;
  approvedAmount?: number;
  paidAmount?: number;
  paymentReference?: string;
  rejectionReasonCode?: RejectionReason;
  rejectionReason?: string;
  decisionNote?: string;
  submittedAt: string;
  updatedAt: string;
  closedAt?: string;
  version: number;
  infoRequests: InfoRequest[];
  // staff only
  claimantId?: string;
  claimantName?: string;
  assignedOfficerId?: string;
  priority?: Priority;
  slaDueAt?: string;
  overdue?: boolean;
  reserveAmount?: number;
  sumInsured?: number;
  deductible?: number;
  maxPayable?: number;
  estimatedLossUsd?: number;
  latestAssessment?: Assessment;
  assessments?: Assessment[];
  noteCount?: number;
  /** Claim number of an already paid claim for the same policy and incident date. */
  possibleDuplicateOf?: string;
}

export interface TimelineEntry {
  occurredAt: string;
  fromStatus?: ClaimStatus;
  toStatus: ClaimStatus;
  actorName?: string;
  actorRole: Role;
  comment?: string;
}

export interface ClaimNote {
  id: string;
  body: string;
  authorName: string;
  authorId: string;
  createdAt: string;
}

export interface NewClaimRequest {
  policyNumber: string;
  claimType: ClaimType;
  incidentDate: string;
  incidentLocation: string;
  description: string;
  injuryReported: boolean;
  estimatedLoss: number;
}

export interface AppNotification {
  id: string;
  eventType: string;
  category: 'INFO' | 'ACTION_REQUIRED' | 'DECISION';
  claimId: string;
  claimNumber: string;
  title: string;
  message: string;
  createdAt: string;
  readAt?: string;
  read: boolean;
}

export interface StaffMember {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  market: Market;
}

export interface QueueQuery {
  scope: QueueScope;
  status?: ClaimStatus;
  market?: Market;
  priority?: Priority;
  claimType?: ClaimType;
  overdueOnly?: boolean;
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface ExposureTotals {
  openClaims: number;
  overdueClaims: number;
  settledClaims: number;
  outstandingUsd: number;
  paidUsd: number;
  incurredUsd: number;
}

export interface MarketExposure {
  market: Market;
  currency: string;
  openClaims: number;
  outstanding: number;
  outstandingUsd: number;
  paid: number;
  paidUsd: number;
  incurredUsd: number;
}

export interface Exposure {
  asOf: string;
  reportingCurrency: 'USD';
  totals: ExposureTotals;
  byMarket: MarketExposure[];
  byClaimType: { claimType: ClaimType; openClaims: number; outstandingUsd: number; paidUsd: number }[];
  byStatus: { status: ClaimStatus; claims: number; outstandingUsd: number }[];
  ageing: { bucket: string; openClaims: number; outstandingUsd: number }[];
  fxRatesToUsd: Record<string, number>;
}

export interface OfficerWorkload {
  officerId: string;
  officerName: string;
  openClaims: number;
  openByStatus: Partial<Record<ClaimStatus, number>>;
  overdueClaims: number;
  highPriorityOpen: number;
  closedLast30Days: number;
  averageDaysToClose?: number;
  outstandingUsd: number;
}

export interface Workload {
  asOf: string;
  unassignedClaims: number;
  unassignedOverdue: number;
  officers: OfficerWorkload[];
}

/** RFC 7807 problem detail returned by every service. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  correlationId?: string;
  errors?: { field: string; message: string }[];
}

// ---------- supporting documents (accident photos, reports)

export type DocumentCategory = 'DAMAGE_PHOTO' | 'POLICE_REPORT' | 'REPAIR_ESTIMATE' | 'MEDICAL_REPORT' | 'OTHER';

export const DOCUMENT_CATEGORIES: readonly { code: DocumentCategory; label: string; imagesOnly: boolean }[] = [
  { code: 'DAMAGE_PHOTO', label: 'Damage photo', imagesOnly: true },
  { code: 'POLICE_REPORT', label: 'Police report', imagesOnly: false },
  { code: 'REPAIR_ESTIMATE', label: 'Repair estimate', imagesOnly: false },
  { code: 'MEDICAL_REPORT', label: 'Medical report', imagesOnly: false },
  { code: 'OTHER', label: 'Other document', imagesOnly: false },
];

/** Mirrors claims-service: JPEG/PNG/WebP photos and PDFs, at most 10 MB each (the server checks the real content). */
export const DOCUMENT_LIMITS = {
  maxBytes: 10 * 1024 * 1024,
  imageTypes: ['image/jpeg', 'image/png', 'image/webp'] as readonly string[],
  allTypes: ['image/jpeg', 'image/png', 'image/webp', 'application/pdf'] as readonly string[],
} as const;

export interface ClaimDocument {
  id: string;
  category: DocumentCategory;
  categoryLabel: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedByName: string;
  uploadedByRole: Role;
  uploadedAt: string;
  /** Relative to the gateway; fetch with the bearer token. */
  contentUrl: string;
  /** Staff only. */
  sha256?: string;
}
