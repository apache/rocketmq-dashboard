import { describe, expect, it } from 'vitest';

import { formatThresholdCondition } from '../alerts';
import type { AlertRule } from '../../../api/alerts';

function ratioRule(threshold: number): AlertRule {
  return {
    id: 1,
    name: 'disk',
    metric: 'broker.disk.usage_ratio',
    operator: '>',
    threshold,
    thresholdUnit: null,
    enabled: true,
  } as AlertRule;
}

describe('formatThresholdCondition legacy ratio rendering', () => {
  it('renders a binary-inexact ratio without float residue', () => {
    // 0.29 * 100 === 28.999999999999996 in IEEE-754 double arithmetic.
    expect(formatThresholdCondition(ratioRule(0.29))).toBe('> 29%');
  });

  it('renders further binary-inexact ratios exactly', () => {
    // 0.07 * 100 === 7.000000000000001; 0.57 * 100 === 56.99999999999999.
    expect(formatThresholdCondition(ratioRule(0.07))).toBe('> 7%');
    expect(formatThresholdCondition(ratioRule(0.57))).toBe('> 57%');
    expect(formatThresholdCondition(ratioRule(0.58))).toBe('> 58%');
  });

  it('keeps rendering exactly-representable ratios (control)', () => {
    expect(formatThresholdCondition(ratioRule(0.85))).toBe('> 85%');
  });

  it('rounds non-integer percentages to a stable precision', () => {
    // A ratio threshold of 0.295 is 29.5% — one decimal place, no residue.
    expect(formatThresholdCondition(ratioRule(0.295))).toBe('> 29.5%');
  });
});
