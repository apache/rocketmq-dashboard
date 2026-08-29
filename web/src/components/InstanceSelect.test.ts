// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it } from 'vitest';
import { matchesInstanceOption } from './InstanceSelect';

describe('InstanceSelect filtering', () => {
  it('normalizes padded and compatibility-form search input', () => {
    expect(matchesInstanceOption('  PROD  ', 'RocketMQ prod cluster')).toBe(true);
    expect(matchesInstanceOption('ＲＭＱ', 'rmq-instance')).toBe(true);
  });
});
