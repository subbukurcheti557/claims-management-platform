import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { AppNotification, NotificationApi } from '@icmp/shared';

/**
 * Header-bell state. Claim events are processed asynchronously (Kafka -> notification-service), so the
 * browser catches up by polling the unread count every 30 s and whenever the tab regains focus.
 */
@Injectable({ providedIn: 'root' })
export class NotificationStore {
  static readonly POLL_MS = 30_000;

  private readonly api = inject(NotificationApi);
  private timer: ReturnType<typeof setInterval> | null = null;
  private readonly onFocus = () => this.refreshCount();

  readonly unread = signal(0);
  readonly items = signal<AppNotification[]>([]);
  readonly loading = signal(false);
  readonly hasUnread = computed(() => this.unread() > 0);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stop());
  }

  start(): void {
    if (this.timer) return;
    this.refreshCount();
    this.timer = setInterval(() => this.refreshCount(), NotificationStore.POLL_MS);
    window.addEventListener('focus', this.onFocus);
  }

  stop(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
    window.removeEventListener('focus', this.onFocus);
    this.unread.set(0);
    this.items.set([]);
  }

  refreshCount(): void {
    this.api.unreadCount().subscribe({ next: (r) => this.unread.set(r.count), error: () => undefined });
  }

  loadList(): void {
    this.loading.set(true);
    this.api.list(false, 15).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  markRead(item: AppNotification): void {
    if (item.read) return;
    this.api.markRead(item.id).subscribe({
      next: (updated) => {
        this.items.update((list) => list.map((n) => (n.id === updated.id ? updated : n)));
        this.unread.update((n) => Math.max(0, n - 1));
      },
    });
  }

  markAllRead(): void {
    this.api.markAllRead().subscribe({
      next: () => {
        this.items.update((list) => list.map((n) => ({ ...n, read: true })));
        this.unread.set(0);
      },
    });
  }
}
