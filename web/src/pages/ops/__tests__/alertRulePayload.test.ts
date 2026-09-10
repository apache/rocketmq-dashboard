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
import { normalizeDuration, normalizeMetric, thresholdUnits } from '../alertRulePayload';

describe('alert rule payload normalization', () => {
  it('maps every legacy Chinese metric label to its Prometheus metric name', () => {
    expect(normalizeMetric('磁盘使用率')).toBe('rocketmq_disk_use_ratio');
    expect(normalizeMetric('消费堆积量')).toBe('rocketmq_consumer_lag_messages');
    expect(normalizeMetric('TPS 异常')).toBe('rocketmq_tps');
    expect(normalizeMetric('Broker 离线')).toBe('rocketmq_broker_offline');
    expect(normalizeMetric('Proxy 连接数')).toBe('rocketmq_proxy_connections');
  });

  it('passes unknown metric names through unchanged', () => {
    expect(normalizeMetric('rocketmq_tps')).toBe('rocketmq_tps');
    expect(normalizeMetric('custom.metric')).toBe('custom.metric');
  });

  it('maps known Chinese duration labels and passes unknown ones through unchanged', () => {
    expect(normalizeDuration('1分钟')).toBe('1m');
    expect(normalizeDuration('30分钟')).toBe('30m');
    expect(normalizeDuration('45分钟')).toBe('45分钟');
  });

  it('exports threshold units for all known metrics', () => {
    expect(thresholdUnits).toEqual({
      rocketmq_disk_use_ratio: '%',
      rocketmq_consumer_lag_messages: '条',
      rocketmq_tps: 'TPS',
      rocketmq_broker_offline: '个',
      rocketmq_proxy_connections: '个',
    });
  });
});
