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
import {
  attachThresholdUnit,
  normalizeDuration,
  normalizeMetric,
  thresholdUnits,
} from '../alertRulePayload';

/**
 * The unit the rule form displays is resolved while the metric is picked (the native catalog
 * supplies `messages`/`seconds`, the ratio metrics carry `%`), and the payload builder used to
 * re-derive it from a map that only knows the five built-in metric names. Every native rule was
 * therefore saved with an empty unit: the list rendered "> 12000" and a notification template's
 * `${thresholdUnit}` expanded to nothing.
 */
describe('attachThresholdUnit', () => {
  it('keeps the unit the form resolved for a native metric', () => {
    expect(
      attachThresholdUnit({
        metric: 'consumer.lag.total',
        duration: '5m',
        thresholdUnit: 'messages',
      }),
    ).toEqual({ metric: 'consumer.lag.total', duration: '5m', thresholdUnit: 'messages' });

    expect(
      attachThresholdUnit({
        metric: 'consumer.delay.seconds',
        duration: '5m',
        thresholdUnit: 'seconds',
      }).thresholdUnit,
    ).toBe('seconds');
  });

  it('keeps a ratio metric percentage', () => {
    expect(
      attachThresholdUnit({ metric: 'broker.disk.usage_ratio', thresholdUnit: '%' }).thresholdUnit,
    ).toBe('%');
  });

  it('falls back to the built-in map when the form carried no unit', () => {
    expect(attachThresholdUnit({ metric: '消费堆积量' }).thresholdUnit).toBe('条');
    expect(attachThresholdUnit({ metric: 'rocketmq_disk_use_ratio' }).thresholdUnit).toBe('%');
  });

  it('still normalizes a legacy metric name and duration', () => {
    const payload = attachThresholdUnit({ metric: '消费堆积量', duration: '5分钟' });

    expect(payload.metric).toBe('rocketmq_consumer_lag_messages');
    expect(payload.duration).toBe('5m');
    expect(payload.thresholdUnit).toBe('条');
  });
});

describe('normalizers', () => {
  it('pass through values they do not know', () => {
    expect(normalizeMetric('consumer.lag.total')).toBe('consumer.lag.total');
    expect(normalizeDuration('1h')).toBe('1h');
    expect(Object.keys(thresholdUnits)).toHaveLength(5);
  });
});
