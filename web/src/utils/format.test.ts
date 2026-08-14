// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it } from 'vitest';
import { formatBytes } from './format';

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
});
