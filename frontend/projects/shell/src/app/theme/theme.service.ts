import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

export type ThemeChoice = 'system' | 'light' | 'dark';
const KEY = 'icmp.theme';

/** Light / dark / follow-the-OS. The choice is a per-browser convenience kept in localStorage. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  readonly choice = signal<ThemeChoice>(ThemeService.read());

  apply(choice: ThemeChoice = this.choice()): void {
    const root = this.document.documentElement;
    root.classList.remove('theme-light', 'theme-dark');
    if (choice !== 'system') {
      root.classList.add(`theme-${choice}`);
    }
    this.choice.set(choice);
    try {
      localStorage.setItem(KEY, choice);
    } catch {
      // storage unavailable
    }
  }

  /** Cycles system -> light -> dark -> system. */
  cycle(): void {
    const next: Record<ThemeChoice, ThemeChoice> = { system: 'light', light: 'dark', dark: 'system' };
    this.apply(next[this.choice()]);
  }

  private static read(): ThemeChoice {
    try {
      const value = localStorage.getItem(KEY);
      return value === 'light' || value === 'dark' ? value : 'system';
    } catch {
      return 'system';
    }
  }
}
