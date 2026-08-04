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

import type { AlertRuleAssetInfo } from '../api/alertRuleAssets';

export interface MockAlertRuleAsset extends AlertRuleAssetInfo {
  yaml: string;
}

const yaml = (name: string, group: string, alert: string, expr: string, severity: string) =>
  `groups:\n  - name: ${group}\n    rules:\n      - alert: ${alert}\n        expr: ${expr}\n        for: 5m\n        labels:\n          severity: ${severity}\n          team: broker\n        annotations:\n          summary: "${alert}"\n          description: "Bundled alert rule ${name}"\n`;

export const mockAlertRuleAssets: MockAlertRuleAsset[] = [
  {
    name: 'rocketmq-broker-down',
    group: 'rocketmq-broker.rules',
    ruleCount: 1,
    severities: ['critical'],
    yaml: yaml(
      'rocketmq-broker-down',
      'rocketmq-broker.rules',
      'RocketMQBrokerDown',
      'up{job=~".*rocketmq.*broker.*"} == 0',
      'critical',
    ),
  },
  {
    name: 'rocketmq-broker-disk-high',
    group: 'rocketmq-broker.rules',
    ruleCount: 1,
    severities: ['critical'],
    yaml: yaml(
      'rocketmq-broker-disk-high',
      'rocketmq-broker.rules',
      'RocketMQBrokerDiskHigh',
      'rocketmq_disk_use_ratio > 85',
      'critical',
    ),
  },
  {
    name: 'rocketmq-consumer-lag-high',
    group: 'rocketmq-consumer.rules',
    ruleCount: 1,
    severities: ['warning'],
    yaml: yaml(
      'rocketmq-consumer-lag-high',
      'rocketmq-consumer.rules',
      'RocketMQConsumerLagHigh',
      'rocketmq_consumer_lag_messages > 100000',
      'warning',
    ),
  },
  {
    name: 'rocketmq-consumer-lag-critical',
    group: 'rocketmq-consumer.rules',
    ruleCount: 1,
    severities: ['critical'],
    yaml: yaml(
      'rocketmq-consumer-lag-critical',
      'rocketmq-consumer.rules',
      'RocketMQConsumerLagCritical',
      'rocketmq_consumer_lag_messages > 1000000',
      'critical',
    ),
  },
  {
    name: 'rocketmq-producer-latency-high',
    group: 'rocketmq-client.rules',
    ruleCount: 1,
    severities: ['warning'],
    yaml: yaml(
      'rocketmq-producer-latency-high',
      'rocketmq-client.rules',
      'RocketMQProducerSendLatencyHigh',
      'rocketmq_producer_send_to_back_rt > 1000',
      'warning',
    ),
  },
  {
    name: 'rocketmq-producer-failure',
    group: 'rocketmq-client.rules',
    ruleCount: 1,
    severities: ['critical'],
    yaml: yaml(
      'rocketmq-producer-failure',
      'rocketmq-client.rules',
      'RocketMQProducerSendFailure',
      'rate(rocketmq_producer_send_failure_count[5m]) > 0',
      'critical',
    ),
  },
  {
    name: 'rocketmq-topic-in-drop',
    group: 'rocketmq-topic.rules',
    ruleCount: 1,
    severities: ['info'],
    yaml: yaml(
      'rocketmq-topic-in-drop',
      'rocketmq-topic.rules',
      'RocketMQTopicMessageInDrop',
      'rate(rocketmq_messages_in_total[10m]) == 0',
      'info',
    ),
  },
  {
    name: 'rocketmq-topic-accumulation',
    group: 'rocketmq-topic.rules',
    ruleCount: 1,
    severities: ['warning'],
    yaml: yaml(
      'rocketmq-topic-accumulation',
      'rocketmq-topic.rules',
      'RocketMQTopicAccumulation',
      'rocketmq_dispatch_behind_bytes > 1073741824',
      'warning',
    ),
  },
  {
    name: 'rocketmq-client-connection-drop',
    group: 'rocketmq-client.rules',
    ruleCount: 1,
    severities: ['warning'],
    yaml: yaml(
      'rocketmq-client-connection-drop',
      'rocketmq-client.rules',
      'RocketMQClientConnectionDrop',
      'changes(rocketmq_producer_count[5m]) < -5',
      'warning',
    ),
  },
  {
    name: 'rocketmq-proxy-down',
    group: 'rocketmq-proxy.rules',
    ruleCount: 1,
    severities: ['critical'],
    yaml: yaml(
      'rocketmq-proxy-down',
      'rocketmq-proxy.rules',
      'RocketMQProxyDown',
      'up{job=~".*rocketmq.*proxy.*"} == 0',
      'critical',
    ),
  },
  {
    name: 'rocketmq-exception-rate',
    group: 'rocketmq-errors.rules',
    ruleCount: 1,
    severities: ['critical'],
    yaml: yaml(
      'rocketmq-exception-rate',
      'rocketmq-errors.rules',
      'RocketMQBrokerExceptions',
      'rate(rocketmq_broker_exception_count[5m]) > 0',
      'critical',
    ),
  },
  {
    name: 'rocketmq-dlq-resend-high',
    group: 'rocketmq-errors.rules',
    ruleCount: 1,
    severities: ['warning'],
    yaml: yaml(
      'rocketmq-dlq-resend-high',
      'rocketmq-errors.rules',
      'RocketMQDLQResendHigh',
      'rate(rocketmq_dlq_resend_count[5m]) > 10',
      'warning',
    ),
  },
  {
    name: 'rocketmq-threadpool-reject',
    group: 'rocketmq-broker.rules',
    ruleCount: 1,
    severities: ['critical'],
    yaml: yaml(
      'rocketmq-threadpool-reject',
      'rocketmq-broker.rules',
      'RocketMQThreadPoolReject',
      'increase(rocketmq_threadpool_reject_count[5m]) > 0',
      'critical',
    ),
  },
  {
    name: 'rocketmq-jvm-gc-cpu-high',
    group: 'rocketmq-broker.rules',
    ruleCount: 1,
    severities: ['warning'],
    yaml: yaml(
      'rocketmq-jvm-gc-cpu-high',
      'rocketmq-broker.rules',
      'RocketMQJVMCpuHigh',
      'rate(jvm_gc_pause_seconds_count[5m]) * avg(rate(jvm_gc_pause_seconds_sum[5m])) > 0.3',
      'warning',
    ),
  },
];
