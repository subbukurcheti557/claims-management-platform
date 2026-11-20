import { Injectable, computed, inject, signal } from '@angular/core';
import {
  AuthStore,
  ClaimDetail,
  ClaimNote,
  ClaimsApi,
  Liability,
  RejectionReason,
  StaffApi,
  StaffMember,
  TimelineEntry,
  errorMessage,
  isConflict,
} from '@icmp/shared';
import { Observable, forkJoin } from 'rxjs';

export type WorkspaceAction = 'assign' | 'requestInfo' | 'assess' | 'approve' | 'settle' | 'reject' | 'note';

/**
 * State for one claim in the staff workspace. Every command sends the version the officer is looking at;
 * if someone else changed the claim in the meantime the server answers 409 and the store reloads.
 */
@Injectable()
export class WorkspaceStore {
  private readonly api = inject(ClaimsApi);
  private readonly staffApi = inject(StaffApi);
  private readonly auth = inject(AuthStore);

  readonly claim = signal<ClaimDetail | null>(null);
  readonly timeline = signal<TimelineEntry[]>([]);
  readonly notes = signal<ClaimNote[]>([]);
  readonly staff = signal<StaffMember[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly busy = signal<WorkspaceAction | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  /** UI hint only: the assigned officer or any manager may act. The backend enforces the same rule. */
  readonly canHandle = computed(() => {
    const c = this.claim();
    return !!c && (this.auth.isManager() || c.assignedOfficerId === this.auth.user()?.id);
  });
  readonly isMine = computed(() => this.claim()?.assignedOfficerId === this.auth.user()?.id);

  load(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({ claim: this.api.get(id), timeline: this.api.timeline(id), notes: this.api.notes(id) }).subscribe({
      next: ({ claim, timeline, notes }) => {
        this.claim.set(claim);
        this.timeline.set(timeline);
        this.notes.set(notes);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
    if (this.auth.isManager() && this.staff().length === 0) {
      this.staffApi.list().subscribe({ next: (list) => this.staff.set(list), error: () => this.staff.set([]) });
    }
  }

  assign(officerId?: string): void {
    this.command('assign', (c) => this.api.assign(c.id, c.version, officerId), 'Claim assigned.');
  }

  requestInfo(question: string, done?: () => void): void {
    this.command('requestInfo', (c) => this.api.requestInfo(c.id, c.version, question), 'Question sent to the claimant.', done);
  }

  assess(body: { liability: Liability; recommendedAmount: number; reserveAmount: number; findings: string }, done?: () => void): void {
    this.command('assess', (c) => this.api.assess(c.id, c.version, body), 'Assessment recorded.', done);
  }

  approve(amount: number, done?: () => void): void {
    this.command('approve', (c) => this.api.approve(c.id, c.version, amount), 'Claim approved. The claimant has been notified.', done);
  }

  settle(reference: string, done?: () => void): void {
    this.command('settle', (c) => this.api.settle(c.id, c.version, reference), 'Payment recorded and the claim is closed.', done);
  }

  reject(reason: RejectionReason, explanation: string, done?: () => void): void {
    this.command('reject', (c) => this.api.reject(c.id, c.version, reason, explanation), 'Claim rejected. The claimant has been notified.', done);
  }

  addNote(body: string, done?: () => void): void {
    const c = this.claim();
    if (!c) return;
    this.busy.set('note');
    this.api.addNote(c.id, body).subscribe({
      next: (note) => {
        this.notes.update((list) => [note, ...list]);
        this.claim.update((cl) => (cl ? { ...cl, noteCount: (cl.noteCount ?? 0) + 1 } : cl));
        this.busy.set(null);
        done?.();
      },
      error: (err: unknown) => {
        this.actionError.set(errorMessage(err));
        this.busy.set(null);
      },
    });
  }

  dismissNotice(): void {
    this.notice.set(null);
  }

  private command(
    action: WorkspaceAction,
    call: (claim: ClaimDetail) => Observable<ClaimDetail>,
    success: string,
    done?: () => void,
  ): void {
    const c = this.claim();
    if (!c) return;
    this.busy.set(action);
    this.actionError.set(null);
    this.notice.set(null);
    call(c).subscribe({
      next: (updated) => {
        this.claim.set(updated);
        this.busy.set(null);
        this.notice.set(success);
        this.api.timeline(updated.id).subscribe((t) => this.timeline.set(t));
        done?.();
      },
      error: (err: unknown) => {
        this.busy.set(null);
        if (isConflict(err)) {
          this.actionError.set(`${errorMessage(err)} The latest version has been loaded.`);
          this.load(c.id);
        } else {
          this.actionError.set(errorMessage(err));
        }
      },
    });
  }
}
