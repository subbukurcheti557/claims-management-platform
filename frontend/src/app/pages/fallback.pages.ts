import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { AuthStore } from '@icmp/shared';

@Component({
  selector: 'app-not-found',
  imports: [MatButtonModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="icmp-page fallback">
      <span class="material-symbols-outlined" aria-hidden="true">travel_explore</span>
      <h1>This page does not exist</h1>
      <p class="icmp-muted">Check the address, or go back to your home page.</p>
      <a mat-flat-button [routerLink]="auth.homeUrl()">Go to home</a>
    </div>
  `,
  styleUrl: './fallback.scss',
})
export class NotFoundPage {
  protected readonly auth = inject(AuthStore);
}
