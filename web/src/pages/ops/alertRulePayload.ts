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

export const legacyMetricValues: Record<string, string> = {
  磁盘使用率: 'rocketmq_disk_use_ratio',
  消费堆积量: 'rocketmq_consumer_lag_messages',
  'TPS 异常': 'rocketmq_tps',
  'Broker 离线': 'rocketmq_broker_offline',
  'Proxy 连接数': 'rocketmq_proxy_connections',
};

const legacyDurationValues: Record<string, string> = {
  '1分钟': '1m',
  '5分钟': '5m',
  '15分钟': '15m',
  '30分钟': '30m',
};

export const thresholdUnits: Record<string, string> = {
  rocketmq_disk_use_ratio: '%',
  rocketmq_consumer_lag_messages: '条',
  rocketmq_tps: 'TPS',
  rocketmq_broker_offline: '个',
  rocketmq_proxy_connections: '个',
};

export function normalizeMetric(metric: string): string {
  return legacyMetricValues[metric] ?? metric;
}

export function normalizeDuration(duration: string): string {
  return legacyDurationValues[duration] ?? duration;
}

export function attachThresholdUnit<T extends { metric: string; duration?: string }>(
  values: T,
): Omit<T, 'metric' | 'duration'> & {
  metric: string;
  duration?: string;
  thresholdUnit: string;
} {
  const metric = normalizeMetric(values.metric);
  return {
    ...values,
    metric,
    ...(values.duration === undefined ? {} : { duration: normalizeDuration(values.duration) }),
    thresholdUnit: thresholdUnits[metric] ?? '',
  };
}
