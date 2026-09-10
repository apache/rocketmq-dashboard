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
import { attachThresholdUnit, normalizeDuration, normalizeMetric } from './alertRulePayload';

describe('alert rule payload helpers', () => {
  it('maps legacy Chinese metric labels to backend metric keys', () => {
    expect(normalizeMetric('磁盘使用率')).toBe('rocketmq_disk_use_ratio');
    expect(normalizeMetric('消费堆积量')).toBe('rocketmq_consumer_lag_messages');
    expect(normalizeMetric('rocketmq_disk_use_ratio')).toBe('rocketmq_disk_use_ratio');
  });

  it('passes through unknown metric labels unchanged', () => {
    expect(normalizeMetric('自定义指标')).toBe('自定义指标');
    expect(normalizeMetric('')).toBe('');
  });

  it('maps legacy Chinese duration labels to PromQL durations', () => {
    expect(normalizeDuration('5分钟')).toBe('5m');
    expect(normalizeDuration('30分钟')).toBe('30m');
    expect(normalizeDuration('10m')).toBe('10m');
  });

  it('attaches the threshold unit for known metrics', () => {
    const payload = attachThresholdUnit({ metric: '磁盘使用率', duration: '5分钟', threshold: 80 });

    expect(payload).toMatchObject({
      metric: 'rocketmq_disk_use_ratio',
      duration: '5m',
      thresholdUnit: '%',
      threshold: 80,
    });
  });

  it('leaves the threshold unit empty for unknown metrics', () => {
    const payload = attachThresholdUnit({ metric: 'custom.metric' });

    expect(payload.thresholdUnit).toBe('');
    expect(payload.metric).toBe('custom.metric');
    expect(payload.duration).toBeUndefined();
  });
});
