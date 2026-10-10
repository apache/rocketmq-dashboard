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
package org.apache.rocketmq.studio.cluster.metrics.collectors;

import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.apache.rocketmq.studio.ops.alert.AlertDomain;
import org.apache.rocketmq.studio.ops.alert.AlertRuleEvaluator;
import org.apache.rocketmq.studio.ops.alert.AlertRuleVO;

import java.util.List;
import java.time.Instant;
import org.apache.rocketmq.studio.ops.alert.AlertRuleState;
import org.apache.rocketmq.studio.ops.alert.AlertStateMachine;
import org.apache.rocketmq.studio.ops.alert.AlertStateStatus;
import org.apache.rocketmq.studio.ops.alert.AlertStateTransition;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloudRocketMqBusinessMetricsCollectorTest {
    @Test
    void collectsAggregateLagWithoutInventingQueueMaximumTest() {
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        InstanceProvider provider = mock(InstanceProvider.class);
        InstanceVO instance = InstanceVO.builder().name("aliyun").vendor(InstanceVendor.ALIYUN).build();
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("orders");
        group.setClusterId("cloud-a");
        when(registry.byInstanceId("aliyun")).thenReturn(Optional.of(provider));
        when(provider.listConsumerGroups("aliyun", null)).thenReturn(List.of(group));
        when(provider.getGroupProgress("aliyun", "orders")).thenReturn(List.of(
                QueueProgressVO.builder().topic("orders-topic").broker("topic:orders-topic")
                        .brokerOffset(-1).consumerOffset(-1).diffTotal(12).build(), QueueProgressVO.builder()
                .topic("payments-topic").broker("topic:payments-topic")
                        .brokerOffset(-1).consumerOffset(-1).diffTotal(30).build()));

        List<MetricSample> samples = new CloudRocketMqBusinessMetricsCollector(registry).collect(instance);

        assertThat(samples).hasSize(4);
        assertThat(samples).filteredOn(sample -> !sample.metricKey().equals("consumer.lag.max_queue"))
                .allSatisfy(sample -> assertThat(sample.availability()).isEqualTo(MetricAvailability.AVAILABLE));
        assertThat(samples).filteredOn(sample -> sample.metricKey().equals("consumer.lag.total"))
                .singleElement().extracting(MetricSample::value).isEqualTo(42D);
        assertThat(samples).filteredOn(sample -> sample.metricKey().equals("consumer.lag.max_queue"))
                .singleElement().satisfies(sample -> {
                    assertThat(sample.availability()).isEqualTo(MetricAvailability.UNSUPPORTED);
                    assertThat(sample.value()).isNull();
                    assertThat(sample.clusterId()).isEqualTo("cloud-a");
                    assertThat(sample.unavailableReason()).isEqualTo("CLOUD_QUEUE_LAG_UNSUPPORTED");
                });
        assertThat(samples).filteredOn(sample -> sample.metricKey().equals("topic.backlog.total"))
                .extracting(MetricSample::value).containsExactlyInAnyOrder(12D, 30D);
    }

    @ParameterizedTest
    @EnumSource(value = InstanceVendor.class, names = {"ALIYUN", "TENCENT"})
    void doesNotPublishTopicTotalsAsQueueMaximumTest(InstanceVendor vendor) {
        QueueProgressVO aggregate = QueueProgressVO.builder().topic("orders-topic")
                .broker("topic:orders-topic").queueId(0)
                .brokerOffset(QueueProgressVO.UNKNOWN_OFFSET).consumerOffset(QueueProgressVO.UNKNOWN_OFFSET)
                .diffTotal(80).build();
        List<MetricSample> samples = collectCloudProgress(vendor, List.of(aggregate));
        MetricSample maximum = samples.stream().filter(sample -> sample.metricKey().equals("consumer.lag.max_queue"))
                .findFirst().orElseThrow();
        AlertRuleVO rule = AlertRuleVO.builder().domain(AlertDomain.BUSINESS)
                .metric("consumer.lag.max_queue").operator(">").threshold(50).build();

        // Two queues of 40 each have topic lag 80, but neither queue breaches 50.
        assertThat(new AlertRuleEvaluator().evaluate(rule, maximum).conditionMet()).isFalse();
        assertThat(maximum.availability()).isEqualTo(MetricAvailability.UNSUPPORTED);
        assertThat(maximum.value()).isNull();
        assertThat(maximum.labels()).containsEntry("consumerGroup", "orders");
        assertThat(samples).filteredOn(sample -> sample.metricKey().equals("consumer.lag.total"))
                .singleElement().satisfies(sample -> {
                    assertThat(sample.value()).isEqualTo(80D);
                    assertThat(sample.availability()).isEqualTo(MetricAvailability.AVAILABLE);
                });
        assertThat(samples).filteredOn(sample -> sample.metricKey().equals("topic.backlog.total"))
                .singleElement().extracting(MetricSample::value).isEqualTo(80D);
    }

    @Test
    void doesNotPublishAliyunTotalFallbackAsQueueMaximumTest() {
        QueueProgressVO aggregate = QueueProgressVO.builder().broker("total").queueId(0)
                .brokerOffset(QueueProgressVO.UNKNOWN_OFFSET).consumerOffset(QueueProgressVO.UNKNOWN_OFFSET)
                .diffTotal(80).build();
        List<MetricSample> samples = collectCloudProgress(InstanceVendor.ALIYUN, List.of(aggregate));

        assertThat(samples).filteredOn(sample -> sample.metricKey().equals("consumer.lag.max_queue"))
                .singleElement().satisfies(sample -> {
                    assertThat(sample.availability()).isEqualTo(MetricAvailability.UNSUPPORTED);
                    assertThat(sample.value()).isNull();
                });
        assertThat(samples).filteredOn(sample -> sample.metricKey().equals("consumer.lag.total"))
                .singleElement().extracting(MetricSample::value).isEqualTo(80D);
        assertThat(samples).noneMatch(sample -> sample.metricKey().equals("topic.backlog.total"));
    }

    @Test
    void unsupportedQueueMaximumDoesNotClearAnActiveAlertTest() {
        List<MetricSample> samples = collectCloudProgress(InstanceVendor.TENCENT, List.of(
                QueueProgressVO.builder().topic("orders-topic").broker("topic:orders-topic")
                        .brokerOffset(QueueProgressVO.UNKNOWN_OFFSET).consumerOffset(QueueProgressVO.UNKNOWN_OFFSET)
                        .diffTotal(0).build()));
        MetricSample maximum = samples.stream().filter(sample -> sample.metricKey().equals("consumer.lag.max_queue"))
                .findFirst().orElseThrow();
        AlertRuleVO rule = AlertRuleVO.builder().domain(AlertDomain.BUSINESS)
                .metric("consumer.lag.max_queue").operator(">").threshold(50).build();
        Instant previously = maximum.collectedAt().minusSeconds(60);
        AlertRuleState firing = new AlertRuleState(AlertStateStatus.FIRING, 1, 80D,
                previously, previously, previously, null);

        var update = new AlertStateMachine().advance(firing,
                new AlertRuleEvaluator().evaluate(rule, maximum), 1, maximum.collectedAt());

        assertThat(update.transition()).isEqualTo(AlertStateTransition.NONE);
        assertThat(update.state().status()).isEqualTo(AlertStateStatus.FIRING);
    }

    @Test
    void emptyProgressStillDoesNotProvideAQueueMaximumTest() {
        List<MetricSample> samples = collectCloudProgress(InstanceVendor.ALIYUN, List.of());

        assertThat(samples).filteredOn(sample -> sample.metricKey().equals("consumer.lag.max_queue"))
                .singleElement().satisfies(sample -> {
                    assertThat(sample.availability()).isEqualTo(MetricAvailability.UNSUPPORTED);
                    assertThat(sample.value()).isNull();
                });
        assertThat(samples).filteredOn(sample -> sample.metricKey().equals("consumer.lag.total"))
                .singleElement().extracting(MetricSample::value).isEqualTo(0D);
    }

    @Test
    void failedGroupProgressRemainsUnavailableTest() {
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        InstanceProvider provider = mock(InstanceProvider.class);
        InstanceVO instance = InstanceVO.builder().name("cloud").vendor(InstanceVendor.TENCENT).build();
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("orders");
        when(registry.byInstanceId("cloud")).thenReturn(Optional.of(provider));
        when(provider.listConsumerGroups("cloud", null)).thenReturn(List.of(group));
        when(provider.getGroupProgress("cloud", "orders")).thenThrow(new IllegalStateException("unavailable"));

        assertThat(new CloudRocketMqBusinessMetricsCollector(registry).collect(instance))
                .hasSize(3).allSatisfy(sample -> {
                    assertThat(sample.availability()).isEqualTo(MetricAvailability.UNAVAILABLE);
                    assertThat(sample.value()).isNull();
                    assertThat(sample.labels()).containsEntry("consumerGroup", "orders");
                });
    }

    private static List<MetricSample> collectCloudProgress(InstanceVendor vendor, List<QueueProgressVO> progress) {
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        InstanceProvider provider = mock(InstanceProvider.class);
        InstanceVO instance = InstanceVO.builder().name("cloud").vendor(vendor).build();
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("orders");
        group.setClusterId("cloud-a");
        when(registry.byInstanceId("cloud")).thenReturn(Optional.of(provider));
        when(provider.listConsumerGroups("cloud", null)).thenReturn(List.of(group));
        when(provider.getGroupProgress("cloud", "orders")).thenReturn(progress);
        return new CloudRocketMqBusinessMetricsCollector(registry).collect(instance);
    }

    @Test
    void skipsApacheInstancesHandledByTheApacheCollectorTest() {
        InstanceVO instance = InstanceVO.builder().name("local").vendor(InstanceVendor.APACHE).build();

        assertThat(new CloudRocketMqBusinessMetricsCollector(mock(InstanceProviderRegistry.class)).collect(instance))
                .isEmpty();
    }
}
