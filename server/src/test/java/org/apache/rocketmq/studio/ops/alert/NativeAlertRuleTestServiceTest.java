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
package org.apache.rocketmq.studio.ops.alert;

import org.apache.rocketmq.studio.cluster.metrics.BusinessMetricsCollector;
import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NativeAlertRuleTestServiceTest {

    @Test
    void evaluatesOnlyTheSelectedConsumerGroupWithoutPersistingEvenWhenTheRuleIsNotYetEnabledTest() {
        InstanceRepository instances = mock(InstanceRepository.class);
        BusinessMetricsCollector collector = mock(BusinessMetricsCollector.class);
        InstanceVO instance = InstanceVO.builder().name("local").build();
        when(instances.findByIdentifier("local")).thenReturn(Optional.of(instance));
        when(collector.supports(instance)).thenReturn(true);
        when(collector.collect(instance)).thenReturn(List.of(sample("orders", 20), sample("payments", 5)));
        AlertRuleVO rule = AlertRuleVO.builder().domain(AlertDomain.BUSINESS).metric("consumer.lag.total")
                .instanceId("local").consumerGroup("orders").operator(">").threshold(10).enabled(false).build();

        AlertRuleTestResultVO result = new NativeAlertRuleTestService(instances, List.of(), List.of(collector),
                new AlertRuleEvaluator()).test(rule);

        assertThat(result.samples()).singleElement().satisfies(sample -> {
            assertThat(sample.labels()).containsEntry("consumerGroup", "orders");
            assertThat(sample.currentValue()).isEqualTo(20);
            assertThat(sample.conditionMet()).isTrue();
            assertThat(sample.unavailableReason()).isNull();
        });
    }

    @Test
    void excludesSamplesForOtherMetricsFromTheTestResultTest() {
        InstanceRepository instances = mock(InstanceRepository.class);
        BusinessMetricsCollector collector = mock(BusinessMetricsCollector.class);
        InstanceVO instance = InstanceVO.builder().name("local").build();
        when(instances.findByIdentifier("local")).thenReturn(Optional.of(instance));
        when(collector.supports(instance)).thenReturn(true);
        when(collector.collect(instance)).thenReturn(List.of(
                sample("consumer.lag.total", "orders", 20),
                sample("consumer.delay.seconds", "orders", 5)));
        AlertRuleVO rule = AlertRuleVO.builder().domain(AlertDomain.BUSINESS).metric("consumer.lag.total")
                .instanceId("local").consumerGroup("orders").operator(">").threshold(10).build();

        AlertRuleTestResultVO result = new NativeAlertRuleTestService(instances, List.of(), List.of(collector),
                new AlertRuleEvaluator()).test(rule);

        assertThat(result.samples()).singleElement().satisfies(sample -> {
            assertThat(sample.currentValue()).isEqualTo(20);
            assertThat(sample.conditionMet()).isTrue();
        });
    }

    private static MetricSample sample(String group, double value) {
        return sample("consumer.lag.total", group, value);
    }

    private static MetricSample sample(String metric, String group, double value) {
        return new MetricSample(metric, AlertDomain.BUSINESS, "local", null,
                Map.of("consumerGroup", group), value, MetricAvailability.AVAILABLE, Instant.now());
    }
}
