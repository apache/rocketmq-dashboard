// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it } from 'vitest';
import { formatBytes, formatRelativeTime, formatTimeOfDay } from './format';

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

  it('handles non-finite input', () => {
    expect(formatBytes(Number.NaN)).toBe('-');
    expect(formatBytes(Number.POSITIVE_INFINITY)).toBe('-');
    expect(formatBytes(Number.NEGATIVE_INFINITY)).toBe('-');
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
