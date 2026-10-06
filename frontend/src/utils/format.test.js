import { describe, expect, it } from 'vitest';
import { formatDate, formatDateTime, formatMoney, formatTime, statusLabel } from './format.js';

describe('format', () => {
  it('formats API local date-times without shifting the time zone', () => {
    expect(formatTime('2026-10-07T09:05:00')).toBe('09:05');
    expect(formatDate('2026-10-07T09:05:00')).toBe('Wed 7 Oct');
    expect(formatDateTime('2026-10-07T23:59')).toBe('Wed 7 Oct, 23:59');
  });

  it('returns empty strings for missing or malformed values', () => {
    expect(formatTime(null)).toBe('');
    expect(formatDate('not a date')).toBe('');
  });

  it('formats rupees with Indian grouping and drops .00', () => {
    expect(formatMoney(600)).toBe('₹600');
    expect(formatMoney('33.34')).toBe('₹33.34');
    expect(formatMoney(150000)).toBe('₹1,50,000');
    expect(formatMoney(null)).toBe('');
  });

  it('labels ride statuses for people', () => {
    expect(statusLabel('STARTED')).toBe('On the way');
    expect(statusLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW');
  });
});
