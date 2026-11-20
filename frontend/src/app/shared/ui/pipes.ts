import { Pipe, PipeTransform } from '@angular/core';

const ZERO_DECIMAL_CURRENCIES = new Set(['IDR']);

/** Formats an amount in its own currency, e.g. "SGD 7,200.00" or "IDR 24,000,000". */
export function formatMoney(amount: number | null | undefined, currency: string): string {
  if (amount === null || amount === undefined || Number.isNaN(amount)) {
    return '—';
  }
  const digits = ZERO_DECIMAL_CURRENCIES.has(currency) ? 0 : 2;
  const number = new Intl.NumberFormat('en-SG', { minimumFractionDigits: digits, maximumFractionDigits: digits }).format(amount);
  return `${currency} ${number}`;
}

/** Short form for dashboards: "USD 1.2M", "USD 238K". */
export function formatCompact(amount: number | null | undefined, currency = 'USD'): string {
  if (amount === null || amount === undefined) return '—';
  return `${currency} ${new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 1 }).format(amount)}`;
}

/** Human relative time, e.g. "in 3 days", "2 hours ago". */
export function relativeTime(iso: string | null | undefined, now = Date.now()): string {
  if (!iso) return '—';
  const diffMs = new Date(iso).getTime() - now;
  const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
  const abs = Math.abs(diffMs);
  const minute = 60_000;
  const hour = 60 * minute;
  const day = 24 * hour;
  if (abs < hour) return rtf.format(Math.round(diffMs / minute), 'minute');
  if (abs < day) return rtf.format(Math.round(diffMs / hour), 'hour');
  return rtf.format(Math.round(diffMs / day), 'day');
}

@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(amount: number | null | undefined, currency: string): string {
    return formatMoney(amount, currency);
  }
}

@Pipe({ name: 'compactMoney' })
export class CompactMoneyPipe implements PipeTransform {
  transform(amount: number | null | undefined, currency = 'USD'): string {
    return formatCompact(amount, currency);
  }
}

@Pipe({ name: 'relativeTime' })
export class RelativeTimePipe implements PipeTransform {
  transform(iso: string | null | undefined): string {
    return relativeTime(iso);
  }
}

@Pipe({ name: 'enumLabel' })
export class EnumLabelPipe implements PipeTransform {
  /** "UNDER_REVIEW" -> "Under review" */
  transform(value: string | null | undefined): string {
    if (!value) return '';
    const text = value.toLowerCase().replace(/_/g, ' ');
    return text.charAt(0).toUpperCase() + text.slice(1);
  }
}
