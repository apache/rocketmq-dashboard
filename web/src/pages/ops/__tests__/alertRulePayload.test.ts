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

describe('alert rule payload normalization', () => {
  it('maps legacy Chinese metric labels to Prometheus metric names', () => {
    expect(normalizeMetric('磁盘使用率')).toBe('rocketmq_disk_use_ratio');
    expect(normalizeMetric('rocketmq_tps')).toBe('rocketmq_tps');
  });

  it('maps legacy Chinese duration labels to Prometheus durations', () => {
    expect(normalizeDuration('1分钟')).toBe('1m');
    expect(normalizeDuration('30分钟')).toBe('30m');
    expect(normalizeDuration('45分钟')).toBe('45分钟');
  });

  it('attaches the metric threshold unit after normalization', () => {
    expect(attachThresholdUnit({ metric: '磁盘使用率', duration: '1分钟', threshold: 80 })).toEqual(
      {
        metric: 'rocketmq_disk_use_ratio',
        duration: '1m',
        threshold: 80,
        thresholdUnit: '%',
      },
    );
    expect(thresholdUnits['rocketmq_consumer_lag_messages']).toBe('条');
  });
});
