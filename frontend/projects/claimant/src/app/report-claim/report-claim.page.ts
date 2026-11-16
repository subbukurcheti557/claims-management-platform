import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { MatStepperModule } from '@angular/material/stepper';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  ClaimsApi,
  EnumLabelPipe,
  checkDocumentFile,
  formatBytes,
  LoadStateComponent,
  MoneyPipe,
  PageHeaderComponent,
  Policy,
  PolicyApi,
  errorMessage,
} from '@icmp/shared';
import { catchError, concatMap, from, map, of, startWith, toArray } from 'rxjs';

/** Photos attached while reporting; more can be added on the claim page afterwards. */
const MAX_FNOL_PHOTOS = 10;

/** Incident date must be today or earlier. */
function notInFuture(control: { value: string }) {
  return control.value && control.value > new Date().toISOString().slice(0, 10) ? { future: true } : null;
}

/** First notice of loss: pick a policy, describe what happened, estimate the loss, review and submit. */
@Component({
  selector: 'claimant-report-claim',
  imports: [
    DatePipe,
    EnumLabelPipe,
    LoadStateComponent,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatRadioModule,
    MatStepperModule,
    MoneyPipe,
    PageHeaderComponent,
    ReactiveFormsModule,
    RouterLink,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './report-claim.page.html',
  styleUrl: './report-claim.page.scss',
})
export class ReportClaimPage implements OnInit {
  private readonly policyApi = inject(PolicyApi);
  private readonly claimsApi = inject(ClaimsApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly today = new Date().toISOString().slice(0, 10);
  protected readonly policies = signal<Policy[]>([]);
  protected readonly loadingPolicies = signal(true);
  protected readonly policyError = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);
  protected readonly photos = signal<File[]>([]);
  protected readonly photoProblems = signal<string[]>([]);
  protected readonly uploadingPhotos = signal(false);
  protected readonly maxPhotos = MAX_FNOL_PHOTOS;

  protected readonly policyForm = this.fb.group({ policyNumber: ['', Validators.required] });
  protected readonly incidentForm = this.fb.group({
    incidentDate: ['', [Validators.required, notInFuture]],
    incidentLocation: ['', [Validators.required, Validators.maxLength(300)]],
    description: ['', [Validators.required, Validators.minLength(20), Validators.maxLength(4000)]],
    injuryReported: [false],
  });
  protected readonly lossForm = this.fb.group({
    estimatedLoss: [null as number | null, [Validators.required, Validators.min(0.01)]],
  });

  private readonly selectedNumber = toSignal(
    this.policyForm.controls.policyNumber.valueChanges.pipe(startWith(this.policyForm.controls.policyNumber.value)),
    { initialValue: '' },
  );
  protected readonly selected = computed(() => this.policies().find((p) => p.policyNumber === this.selectedNumber()));
  protected readonly activePolicies = computed(() => this.policies().filter((p) => p.status === 'ACTIVE'));
  protected readonly inactivePolicies = computed(() => this.policies().filter((p) => p.status !== 'ACTIVE'));

  ngOnInit(): void {
    this.loadPolicies();
  }

  protected loadPolicies(): void {
    this.loadingPolicies.set(true);
    this.policyError.set(null);
    this.policyApi.mine().subscribe({
      next: (list) => {
        this.policies.set(list);
        this.loadingPolicies.set(false);
        const active = list.filter((p) => p.status === 'ACTIVE');
        if (active.length === 1) this.policyForm.controls.policyNumber.setValue(active[0].policyNumber);
      },
      error: (err: unknown) => {
        this.policyError.set(errorMessage(err));
        this.loadingPolicies.set(false);
      },
    });
  }

  protected addPhotos(event: Event): void {
    const input = event.target as HTMLInputElement;
    const chosen = Array.from(input.files ?? []);
    input.value = '';
    const problems: string[] = [];
    const accepted: File[] = [];
    for (const file of chosen) {
      const problem = checkDocumentFile(file, 'DAMAGE_PHOTO');
      if (problem) {
        problems.push(problem);
      } else if (this.photos().length + accepted.length >= MAX_FNOL_PHOTOS) {
        problems.push(`${file.name} was not added: attach up to ${MAX_FNOL_PHOTOS} photos here, and add more later.`);
      } else {
        accepted.push(file);
      }
    }
    this.photos.update((list) => [...list, ...accepted]);
    this.photoProblems.set(problems);
  }

  protected removePhoto(index: number): void {
    this.photos.update((list) => list.filter((_, i) => i !== index));
  }

  protected size(bytes: number): string {
    return formatBytes(bytes);
  }

  protected submit(): void {
    const policy = this.selected();
    if (!policy || this.incidentForm.invalid || this.lossForm.invalid) {
      this.incidentForm.markAllAsTouched();
      this.lossForm.markAllAsTouched();
      return;
    }
    const incident = this.incidentForm.getRawValue();
    this.submitting.set(true);
    this.submitError.set(null);
    this.claimsApi
      .submit({
        policyNumber: policy.policyNumber,
        claimType: policy.product,
        incidentDate: incident.incidentDate,
        incidentLocation: incident.incidentLocation.trim(),
        description: incident.description.trim(),
        injuryReported: incident.injuryReported,
        estimatedLoss: Number(this.lossForm.getRawValue().estimatedLoss),
      })
      .subscribe({
        next: (claim) => this.uploadPhotosThenOpen(claim.id),
        error: (err: unknown) => {
          this.submitting.set(false);
          this.submitError.set(errorMessage(err));
        },
      });
  }

  /**
   * The claim must exist before files can be attached to it, so photos are uploaded right after it is created.
   * A failed photo does not undo the claim: the claim page says how many failed so they can be added again.
   */
  private uploadPhotosThenOpen(claimId: string): void {
    const open = (failed: number) => {
      this.submitting.set(false);
      this.uploadingPhotos.set(false);
      const queryParams = failed > 0 ? { submitted: 1, photosFailed: failed } : { submitted: 1 };
      void this.router.navigate(['..', claimId], { relativeTo: this.route, queryParams });
    };
    const photos = this.photos();
    if (photos.length === 0) {
      open(0);
      return;
    }
    this.uploadingPhotos.set(true);
    from(photos)
      .pipe(
        concatMap((file) =>
          this.claimsApi.uploadDocument(claimId, 'DAMAGE_PHOTO', file).pipe(
            map(() => true),
            catchError(() => of(false)),
          ),
        ),
        toArray(),
      )
      .subscribe((results) => open(results.filter((ok) => !ok).length));
  }
}
