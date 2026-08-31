import { describe, expect, it } from 'vitest';

import { toToken } from './localeToken';

describe('toToken', () => {
  it('lowercases fixed tokens with the root locale', () => {
    expect(toToken('CRITICAL')).toBe('critical');
    expect(toToken('SUCCESS')).toBe('success');
    expect(toToken('PARTIAL')).toBe('partial');
    expect(toToken('global')).toBe('global');
  });

  it('treats null and undefined as the empty token', () => {
    expect(toToken(null)).toBe('');
    expect(toToken(undefined)).toBe('');
    expect(toToken('')).toBe('');
  });

  it('does not mutate the input contract for mixed-case values', () => {
    expect(toToken('MixedCase')).toBe('mixedcase');
  });
});
