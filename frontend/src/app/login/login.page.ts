import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { AuthStore, errorMessage } from '@icmp/shared';

interface DemoAccount {
  email: string;
  name: string;
  role: string;
}

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatProgressSpinnerModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.page.html',
  styleUrl: './login.page.scss',
})
export class LoginPage {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  /** Set to 1 by the interceptor when a session expired. */
  readonly expired = input<string>();

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected readonly demoAccounts: DemoAccount[] = [
    { email: 'alice@claimant.test', name: 'Alice Tan', role: 'Policyholder · SG' },
    { email: 'bob@claimant.test', name: 'Bob Wong', role: 'Policyholder · HK' },
    { email: 'olivia@staff.test', name: 'Olivia Lim', role: 'Claims officer' },
    { email: 'omar@staff.test', name: 'Omar Rahman', role: 'Claims officer' },
    { email: 'maya@staff.test', name: 'Maya Chen', role: 'Claims manager' },
  ];
  protected readonly demoPassword = 'Password1!';

  protected useDemo(account: DemoAccount): void {
    this.form.setValue({ email: account.email, password: this.demoPassword });
    this.submit();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => {
        this.submitting.set(false);
        void this.router.navigateByUrl(this.auth.homeUrl());
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.error.set(errorMessage(err));
      },
    });
  }
}
