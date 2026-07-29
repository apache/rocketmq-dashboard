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
package org.apache.rocketmq.studio.ops.metrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MetricsProvider metricsProvider;

    public MetricsOverviewVO getMetricsOverview() {
        log.debug("Fetching metrics overview");
        return metricsProvider.getMetricsOverview();
    }

    public BrokerMetricsVO getBrokerMetrics(String brokerId) {
        log.debug("Fetching broker metrics for: {}", brokerId);
        return metricsProvider.getBrokerMetrics(brokerId);
    }

    public TopicMetricsVO getTopicMetrics(String topicName) {
        log.debug("Fetching topic metrics for: {}", topicName);
        return metricsProvider.getTopicMetrics(topicName);
    }

    public ConsumerGroupMetricsVO getConsumerGroupMetrics(String groupName) {
        log.debug("Fetching consumer group metrics for: {}", groupName);
        return metricsProvider.getConsumerGroupMetrics(groupName);
    }
}