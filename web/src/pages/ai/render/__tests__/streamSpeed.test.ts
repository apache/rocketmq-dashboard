/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { describe, expect, it } from 'vitest';
import { createStreamSpeedTracker, estimateTokens } from '../streamSpeed';

describe('estimateTokens', () => {
  it('counts one token per CJK character', () => {
    expect(estimateTokens('当前有哪些实例')).toBe(7);
  });

  it('counts four latin characters per token', () => {
    expect(estimateTokens('hello world!')).toBe(3);
  });

  it('mixes both scripts', () => {
    // 2 CJK tokens + 8 latin chars / 4 = 2 tokens
    expect(estimateTokens('实例abcd1234')).toBe(4);
  });

  it('returns zero for an empty delta', () => {
    expect(estimateTokens('')).toBe(0);
  });
});

describe('createStreamSpeedTracker', () => {
  it('reports nothing before any delta arrives', () => {
    const tracker = createStreamSpeedTracker();
    expect(tracker.tokensPerSecond()).toBeNull();
    expect(tracker.tokens()).toBe(0);
  });

  it('reports nothing while the sample window is shorter than two seconds', () => {
    const tracker = createStreamSpeedTracker();
    tracker.recordDelta('aaaa', 1000);
    tracker.recordDelta('aaaa', 2500);
    expect(tracker.tokensPerSecond()).toBeNull();
  });

  it('estimates tokens per second over the streaming window', () => {
    const tracker = createStreamSpeedTracker();
    // 1 token at t=0, another 1 at t=4s -> 2 tokens / 4s = 0.5 token/s.
    tracker.recordDelta('aaaa', 0);
    tracker.recordDelta('aaaa', 4000);
    expect(tracker.tokens()).toBe(2);
    expect(tracker.tokensPerSecond()).toBeCloseTo(0.5, 5);
  });

  it('includes tool round-trip gaps in the window', () => {
    const tracker = createStreamSpeedTracker();
    tracker.recordDelta('aaaa', 0);
    // A 10s tool call between deltas slows the reported speed down, by design.
    tracker.recordDelta('aaaa', 10000);
    expect(tracker.tokensPerSecond()).toBeCloseTo(0.2, 5);
  });
});
