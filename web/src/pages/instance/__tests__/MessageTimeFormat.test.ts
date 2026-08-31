import { describe, expect, it } from 'vitest';

import { formatTimeMs, storeTimeSortValue } from '../message';

describe('formatTimeMs', () => {
  it('renders a valid millisecond timestamp with the full precision suffix', () => {
    expect(formatTimeMs('2026-08-31T12:00:00.250Z')).toMatch(
      /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}$/,
    );
  });

  it('renders numeric epoch milliseconds', () => {
    expect(formatTimeMs(1756627200250)).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}$/);
  });

  it('renders unavailable values as the placeholder instead of NaN fields', () => {
    expect(formatTimeMs('')).toBe('-');
    expect(formatTimeMs(0)).toBe('-');
    expect(formatTimeMs('not-a-timestamp')).toBe('-');
  });
});

describe('storeTimeSortValue', () => {
  it('orders valid timestamps numerically', () => {
    const earlier = storeTimeSortValue('2026-08-31T11:00:00.000Z');
    const later = storeTimeSortValue('2026-08-31T12:00:00.000Z');
    expect(later - earlier).toBeGreaterThan(0);
  });

  it('passes numeric values through and pins non-finite numbers', () => {
    expect(storeTimeSortValue(1756627200250)).toBe(1756627200250);
    expect(storeTimeSortValue(Number.NaN)).toBe(0);
  });

  it('pins invalid and empty strings to the epoch so they do not break sorting', () => {
    expect(storeTimeSortValue('')).toBe(0);
    expect(storeTimeSortValue('garbage')).toBe(0);
    expect(
      storeTimeSortValue('garbage') - storeTimeSortValue('2026-08-31T12:00:00.000Z'),
    ).toBeLessThan(0);
  });
});
