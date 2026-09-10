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
  it('formats positive byte counts into the nearest unit', () => {
    expect(formatBytes(512)).toBe('512.0 B');
    expect(formatBytes(1024)).toBe('1.0 KB');
    expect(formatBytes(1536)).toBe('1.5 KB');
    expect(formatBytes(1048576)).toBe('1.0 MB');
  });

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

  it('renders a same-day timestamp before noon with a leading zero hour', () => {
    expect(formatTimeOfDay(new Date(2026, 7, 13, 8, 5).getTime())).toBe('08:05');
    expect(formatTimeOfDay(new Date(2026, 7, 13, 0, 0).getTime())).toBe('00:00');
  });

  it('falls back to month and day for a timestamp on a previous calendar date', () => {
    const zh = (key: string, params?: Record<string, string | number>) =>
      key === 'ai.history.justNow' ? '刚刚' : `${params?.count} 分钟前`;
    const en = (key: string, params?: Record<string, string | number>) =>
      key === 'ai.history.justNow' ? 'Just now' : `${params?.count} min ago`;
    const yesterday = new Date(2026, 7, 13, 23, 45).getTime();
    const now = new Date(2026, 7, 14, 9, 0).getTime();

    expect(formatRelativeTime(yesterday, 'zh', zh, now)).toBe('8月13日');
    expect(formatRelativeTime(yesterday, 'en', en, now)).toBe('Aug 13');
  });

  it('formats full and date-only values in local time', () => {
    expect(formatDateTime(new Date(2026, 7, 23, 10, 35, 38))).toBe('2026-08-23 10:35:38');
    expect(formatDateTime(new Date(2026, 0, 1, 0, 0, 0))).toBe('2026-01-01 00:00:00');
    expect(formatDate(new Date(2026, 0, 5))).toBe('2026-01-05');
    expect(formatDate('2026-12-31T00:00:00')).toBe('2026-12-31');
  });

  it('formats delay durations in both languages', () => {
    expect(formatDelay(82500, 'zh')).toBe('22小时55分钟');
    expect(formatDelay(82500, 'en')).toBe('22h 55m');
    expect(formatDelay(90061, 'en')).toBe('1d 1h 1m');
    expect(formatDelay(45, 'zh')).toBe('45秒');
    expect(formatDelay(3630, 'zh')).toBe('1小时30秒');
    expect(formatDelay(0, 'en')).toBe('0s');
    expect(formatDelay(-3, 'zh')).toBe('0秒');
  });

  it('formats numbers with thousands separators', () => {
    expect(formatNumber(0)).toBe('0');
    expect(formatNumber(1234567)).toBe('1,234,567');
    expect(formatNumber(3.5)).toBe('3.5');
  });

  it('honors an explicit offset when formatting UTC date time', () => {
    expect(formatUtcDateTime('2026-08-23T10:35:38+02:00', 'UTC')).toBe(
      '2026-08-23 08:35:38 UTC',
    );
    expect(formatUtcDateTime('2026-08-23T10:35:38', 'UTC')).toBe('2026-08-23 10:35:38 UTC');
  });

  it('formats percentage values with the default precision', () => {
    expect(formatPercent(0)).toBe('0.0%');
    expect(formatPercent(12.345)).toBe('12.3%');
    expect(formatPercent(100)).toBe('100.0%');
  });
});
