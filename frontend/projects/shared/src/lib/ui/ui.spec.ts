import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

import { errorMessage, isConflict } from '../errors/problem';
import { formatMoney, relativeTime } from './pipes';
import { progressIndex } from './status';
import { PriorityChipComponent, StatusChipComponent } from './status-chip.component';

describe('formatting', () => {
  it('formats money in the claim currency', () => {
    expect(formatMoney(7200, 'SGD')).toBe('SGD 7,200.00');
    expect(formatMoney(24000000, 'IDR')).toBe('IDR 24,000,000');
    expect(formatMoney(undefined, 'SGD')).toBe('—');
  });

  it('describes time relative to now', () => {
    const now = Date.parse('2026-11-15T10:00:00Z');
    expect(relativeTime('2026-11-18T10:00:00Z', now)).toBe('in 3 days');
    expect(relativeTime('2026-11-15T08:00:00Z', now)).toBe('2 hours ago');
  });

  it('maps statuses onto the claimant progress tracker', () => {
    expect(progressIndex('SUBMITTED')).toBe(0);
    expect(progressIndex('INFO_REQUESTED')).toBe(1);
    expect(progressIndex('REJECTED')).toBe(3);
    expect(progressIndex('SETTLED')).toBe(4);
    expect(progressIndex('WITHDRAWN')).toBe(-1);
  });
});

describe('errorMessage', () => {
  it('prefers field errors, then the problem detail', () => {
    const fields = new HttpErrorResponse({
      status: 400,
      error: { title: 'Validation failed', errors: [{ field: 'estimatedLoss', message: 'must be greater than 0' }] },
    });
    expect(errorMessage(fields)).toBe('estimatedLoss: must be greater than 0');
    const detail = new HttpErrorResponse({ status: 422, error: { detail: 'Incident date is outside the policy period.' } });
    expect(errorMessage(detail)).toBe('Incident date is outside the policy period.');
  });

  it('explains network and server failures in plain words', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toContain('could not be reached');
    expect(errorMessage(new HttpErrorResponse({ status: 503 }))).toContain('try again');
    expect(isConflict(new HttpErrorResponse({ status: 409 }))).toBe(true);
  });
});

describe('chips', () => {
  it('shows status as icon and text, not colour alone', async () => {
    const fixture = TestBed.createComponent(StatusChipComponent);
    fixture.componentRef.setInput('status', 'INFO_REQUESTED');
    await fixture.whenStable();
    const chip: HTMLElement = fixture.nativeElement.querySelector('.chip');
    expect(chip.textContent).toContain('Information needed');
    expect(chip.getAttribute('aria-label')).toBe('Status: Information needed');
    expect(chip.dataset['tone']).toBe('warning');
  });

  it('renders priority', async () => {
    const fixture = TestBed.createComponent(PriorityChipComponent);
    fixture.componentRef.setInput('priority', 'HIGH');
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('High');
  });
});
