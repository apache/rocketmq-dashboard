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
package com.rocketmq.studio.ops.metrics;

import com.rocketmq.studio.common.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;

    @GetMapping("/overview")
    public Result<MetricsOverviewVO> getMetricsOverview() {
        return Result.ok(metricsService.getMetricsOverview());
    }

    @GetMapping("/brokers/{brokerId}")
    public Result<BrokerMetricsVO> getBrokerMetrics(@PathVariable String brokerId) {
        return Result.ok(metricsService.getBrokerMetrics(brokerId));
    }

    @GetMapping("/topics/{topicName}")
    public Result<TopicMetricsVO> getTopicMetrics(@PathVariable String topicName) {
        return Result.ok(metricsService.getTopicMetrics(topicName));
    }

    @GetMapping("/consumer-groups/{groupName}")
    public Result<ConsumerGroupMetricsVO> getConsumerGroupMetrics(@PathVariable String groupName) {
        return Result.ok(metricsService.getConsumerGroupMetrics(groupName));
    }
}