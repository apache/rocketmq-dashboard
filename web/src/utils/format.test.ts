// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it } from 'vitest';
import {
  formatBytes,
  formatDate,
  formatDateTime,
  formatUtcDateTime,
  formatDelay,
  formatNumber,
  formatPercent,
  formatRelativeTime,
  formatTimeOfDay,
} from './format';

describe('formatBytes', () => {
  it('formats zero', () => {
    expect(formatBytes(0)).toBe('0 B');
  });

  it('formats negative values', () => {
    expect(formatBytes(-1536)).toBe('-1.5 KB');
  });

  it('clamps to the largest unit', () => {
    expect(formatBytes(1024 ** 5)).toBe('1.0 PB');
    const huge = formatBytes(1024 ** 9);
    expect(huge).toContain('PB');
    expect(huge).not.toContain('undefined');
  });

  it('promotes to the next unit when rounding reaches the boundary', () => {
    expect(formatBytes(1023.96)).toBe('1.0 KB');
    expect(formatBytes(1024 * 1024 - 1)).toBe('1.0 MB');
    expect(formatBytes(1024 * 1024 - 1, 0)).toBe('1 MB');
    expect(formatBytes(1024 * 1024 * 1024 - 1, 2)).toBe('1.00 GB');
    expect(formatBytes(1024 ** 6)).toBe('1024.0 PB');
  });

  it('keeps scaling past megabytes instead of capping at MB', () => {
    expect(formatBytes(5 * 1024 ** 3)).toBe('5.0 GB');
    expect(formatBytes(2 * 1024 ** 4)).toBe('2.0 TB');
  });

  it('renders whole bytes because a byte count has no fraction', () => {
    expect(formatBytes(1)).toBe('1 B');
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(512, 2)).toBe('512 B');
    expect(formatBytes(-2048)).toBe('-2.0 KB');
  });

  it('promotes a sub-kilobyte value that only rounds up at whole-byte width', () => {
    expect(formatBytes(1023.6)).toBe('1.0 KB');
  });
  it('handles non-finite input', () => {
    expect(formatBytes(Number.NaN)).toBe('-');
    expect(formatBytes(Number.POSITIVE_INFINITY)).toBe('-');
    expect(formatBytes(Number.NEGATIVE_INFINITY)).toBe('-');
  });

  it('bounds invalid precision arguments', () => {
    expect(formatBytes(1536, Number.POSITIVE_INFINITY)).toBe('1.5 KB');
    expect(formatBytes(1536, -2)).toBe('2 KB');
    expect(formatPercent(12.345, Number.NaN)).toBe('12.3%');
    expect(formatPercent(12.345, -1)).toBe('12%');
  });

  it('uses a placeholder for invalid dates and numeric values', () => {
    expect(formatDate('not-a-date')).toBe('-');
    expect(formatDateTime(new Date(Number.NaN))).toBe('-');
    expect(formatUtcDateTime('not-a-date', 'UTC')).toBe('-');
    expect(formatRelativeTime(Number.NaN, 'en', (key) => key)).toBe('-');
    expect(formatRelativeTime(Date.now(), 'en', (key) => key, Number.POSITIVE_INFINITY)).toBe('-');
    expect(formatTimeOfDay(Number.POSITIVE_INFINITY)).toBe('-');
    expect(formatNumber(Number.NaN)).toBe('-');
    expect(formatDelay(Number.POSITIVE_INFINITY, 'en')).toBe('-');
    expect(formatPercent(Number.NEGATIVE_INFINITY)).toBe('-');
  });

  it('treats timezone-less alert timestamps as UTC', () => {
    expect(formatUtcDateTime('2026-08-23T10:35:38.590731', 'America/Los_Angeles')).toBe(
      '2026-08-23 03:35:38 PDT',
    );
    expect(formatUtcDateTime('2026-08-23T10:35:38Z', 'UTC')).toBe('2026-08-23 10:35:38 UTC');
  });

  it('formats recent timestamps for compact conversation history', () => {
    const now = new Date(2026, 7, 13, 15, 30).getTime();
    const zh = (key: string, params?: Record<string, string | number>) =>
      key === 'ai.history.justNow' ? '刚刚' : `${params?.count} 分钟前`;
    const en = (key: string, params?: Record<string, string | number>) =>
      key === 'ai.history.justNow' ? 'Just now' : `${params?.count} min ago`;

    expect(formatRelativeTime(now, 'zh', zh, now)).toBe('刚刚');
    expect(formatRelativeTime(now - 5 * 60_000, 'zh', zh, now)).toBe('5 分钟前');
    expect(formatRelativeTime(now - 5 * 60_000, 'en', en, now)).toBe('5 min ago');
    expect(formatRelativeTime(now - 2 * 60 * 60_000, 'zh', zh, now)).toBe('13:30');
    expect(formatTimeOfDay(now)).toBe('15:30');
  });
});
