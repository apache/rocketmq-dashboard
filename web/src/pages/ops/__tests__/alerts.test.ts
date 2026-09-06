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
import { attachThresholdUnit, normalizeDuration, normalizeMetric } from '../alertRulePayload';

describe('attachThresholdUnit', () => {
  it('derives the threshold unit from the selected metric', () => {
    expect(attachThresholdUnit({ metric: 'rocketmq_broker_offline', threshold: 1 })).toEqual({
      metric: 'rocketmq_broker_offline',
      threshold: 1,
      thresholdUnit: '个',
    });
  });

  it('overwrites stale units when a metric changes', () => {
    expect(
      attachThresholdUnit({
        metric: 'rocketmq_consumer_lag_messages',
        threshold: 100,
        thresholdUnit: '%',
      }),
    ).toEqual({
      metric: 'rocketmq_consumer_lag_messages',
      threshold: 100,
      thresholdUnit: '条',
    });
  });

  it('normalizes legacy display values before submitting them to the backend', () => {
    expect(attachThresholdUnit({ metric: '磁盘使用率', duration: '5分钟', threshold: 85 })).toEqual(
      {
        metric: 'rocketmq_disk_use_ratio',
        duration: '5m',
        threshold: 85,
        thresholdUnit: '%',
      },
    );
  });

  it('keeps an unknown metric and resolves its unit to an empty string', () => {
    expect(attachThresholdUnit({ metric: 'rocketmq_custom_metric', threshold: 7 })).toEqual({
      metric: 'rocketmq_custom_metric',
      threshold: 7,
      thresholdUnit: '',
    });
  });

  it('attaches the unit for every known metric family', () => {
    expect(attachThresholdUnit({ metric: 'rocketmq_disk_use_ratio', threshold: 80 }).thresholdUnit).toBe('%');
    expect(attachThresholdUnit({ metric: 'rocketmq_tps', threshold: 10 }).thresholdUnit).toBe('TPS');
    expect(
      attachThresholdUnit({ metric: 'rocketmq_proxy_connections', threshold: 3 }).thresholdUnit,
    ).toBe('个');
  });

  it('normalizes a legacy metric but leaves a legacy duration untouched', () => {
    expect(
      attachThresholdUnit({ metric: 'TPS 异常', duration: '10分钟', threshold: 50 }),
    ).toEqual({
      metric: 'rocketmq_tps',
      duration: '10分钟',
      threshold: 50,
      thresholdUnit: 'TPS',
    });
  });

  it('normalizes every known legacy duration label', () => {
    expect(attachThresholdUnit({ metric: '消费堆积量', duration: '1分钟', threshold: 1 })).toEqual({
      metric: 'rocketmq_consumer_lag_messages',
      duration: '1m',
      threshold: 1,
      thresholdUnit: '条',
    });
    expect(attachThresholdUnit({ metric: '消费堆积量', duration: '15分钟', threshold: 1 })).toEqual({
      metric: 'rocketmq_consumer_lag_messages',
      duration: '15m',
      threshold: 1,
      thresholdUnit: '条',
    });
    expect(attachThresholdUnit({ metric: '消费堆积量', duration: '30分钟', threshold: 1 })).toEqual({
      metric: 'rocketmq_consumer_lag_messages',
      duration: '30m',
      threshold: 1,
      thresholdUnit: '条',
    });
  });

  it('omits the duration field when the payload carries none', () => {
    const payload = attachThresholdUnit({ metric: 'rocketmq_broker_offline', threshold: 2 });
    expect(payload).not.toHaveProperty('duration');
  });
});

describe('normalizeMetric and normalizeDuration', () => {
  it('passes non-legacy values through unchanged', () => {
    expect(normalizeMetric('rocketmq_unknown_metric')).toBe('rocketmq_unknown_metric');
    expect(normalizeDuration('9分钟')).toBe('9分钟');
    expect(normalizeDuration('')).toBe('');
  });

  it('maps every legacy metric label to its backend key', () => {
    expect(normalizeMetric('消费堆积量')).toBe('rocketmq_consumer_lag_messages');
    expect(normalizeMetric('磁盘使用率')).toBe('rocketmq_disk_use_ratio');
    expect(normalizeMetric('Broker 离线')).toBe('rocketmq_broker_offline');
    expect(normalizeMetric('Proxy 连接数')).toBe('rocketmq_proxy_connections');
  });
});
