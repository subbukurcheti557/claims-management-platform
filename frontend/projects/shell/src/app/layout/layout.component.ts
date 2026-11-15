import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject } from '@angular/core';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatMenuModule } from '@angular/material/menu';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AppNotification, AuthStore } from '@icmp/shared';

import { NotificationStore } from '../notifications/notification.store';
import { ThemeService } from '../theme/theme.service';

interface NavLink {
  path: string;
  label: string;
  icon: string;
  exact?: boolean;
}

/** Shell chrome: top bar, role-based navigation, notification bell, theme toggle and user menu. */
@Component({
  selector: 'app-layout',
  imports: [
    DatePipe,
    MatBadgeModule,
    MatButtonModule,
    MatDividerModule,
    MatMenuModule,
    MatToolbarModule,
    MatTooltipModule,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.scss',
})
export class LayoutComponent implements OnInit, OnDestroy {
  protected readonly auth = inject(AuthStore);
  protected readonly notifications = inject(NotificationStore);
  protected readonly theme = inject(ThemeService);
  private readonly router = inject(Router);

  protected readonly links = computed<NavLink[]>(() => {
    if (this.auth.isClaimant()) {
      return [
        { path: '/claimant', label: 'My claims', icon: 'folder_open', exact: true },
        { path: '/claimant/new', label: 'Report an incident', icon: 'add_circle' },
      ];
    }
    if (this.auth.isStaff()) {
      return [
        { path: '/staff/queue', label: 'Work queue', icon: 'inbox' },
        { path: '/staff/dashboard', label: this.auth.isManager() ? 'Exposure & team' : 'Team dashboard', icon: 'monitoring' },
      ];
    }
    return [];
  });

  protected readonly roleLabel = computed(() => {
    switch (this.auth.role()) {
      case 'CLAIMANT':
        return 'Policyholder';
      case 'CLAIMS_OFFICER':
        return 'Claims officer';
      case 'CLAIMS_MANAGER':
        return 'Claims manager';
      default:
        return '';
    }
  });

  protected readonly themeIcon = computed(() =>
    ({ system: 'contrast', light: 'light_mode', dark: 'dark_mode' })[this.theme.choice()],
  );

  ngOnInit(): void {
    if (!this.auth.checkExpiry()) {
      void this.router.navigate(['/login']);
      return;
    }
    this.notifications.start();
  }

  ngOnDestroy(): void {
    this.notifications.stop();
  }

  protected open(item: AppNotification): void {
    this.notifications.markRead(item);
    const base = this.auth.isStaff() ? '/staff/claims' : '/claimant';
    void this.router.navigate([base, item.claimId]);
  }

  protected logout(): void {
    this.notifications.stop();
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}
