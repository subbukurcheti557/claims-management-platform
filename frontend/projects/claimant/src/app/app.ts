import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthStore } from '@icmp/shared';

/**
 * Standalone frame for developing this micro-frontend on its own server. Sessions are per origin, so
 * outside the shell there is no signed-in user; the normal entry point is the shell on port 4200.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!auth.isAuthenticated()) {
      <div class="icmp-page">
        <div class="icmp-banner" data-tone="warning" role="status">
          <span class="material-symbols-outlined" aria-hidden="true">info</span>
          <div>
            <strong>Claimant micro-frontend (standalone mode)</strong>
            <p>This area is normally loaded inside the shell. Open <a href="http://localhost:4200">localhost:4200</a> and sign in.</p>
          </div>
        </div>
      </div>
    }
    <router-outlet />
  `,
})
export class App {
  protected readonly auth = inject(AuthStore);
}
