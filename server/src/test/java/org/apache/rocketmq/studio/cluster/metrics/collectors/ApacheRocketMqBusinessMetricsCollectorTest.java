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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApacheRocketMqBusinessMetricsCollectorTest {

    @Test
    void collectsOneLagSampleForEachConsumerGroupTest() {
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        InstanceProvider provider = mock(InstanceProvider.class);
        ConsumerGroupVO orders = group("orders", "cluster-a", 42);
        ConsumerGroupVO payments = group("payments", "cluster-a", 0);
        when(registry.byInstanceId("local")).thenReturn(Optional.of(provider));
        when(provider.listConsumerGroups("local", null)).thenReturn(List.of(orders, payments));
        when(provider.getGroupProgress("local", "orders")).thenReturn(List.of(QueueProgressVO.builder()
                .topic("orders-topic").diffTotal(17).build(), QueueProgressVO.builder()
                .topic("orders-topic").diffTotal(42).build()));
        when(provider.getGroupProgress("local", "payments")).thenReturn(List.of(QueueProgressVO.builder()
                .topic("payments-topic").diffTotal(0).build()));

        List<MetricSample> samples = new ApacheRocketMqBusinessMetricsCollector(registry).collect(apacheInstance());

        assertThat(samples).hasSize(8).allSatisfy(sample -> {
            assertThat(sample.availability()).isEqualTo(MetricAvailability.AVAILABLE);
        });
        assertThat(samples).filteredOn(sample -> sample.metricKey().equals(
                ApacheRocketMqBusinessMetricsCollector.CONSUMER_LAG_TOTAL)
                && "orders".equals(sample.labels().get("consumerGroup")))
                .singleElement().satisfies(sample -> assertThat(sample.value()).isEqualTo(42D));
        assertThat(samples).filteredOn(sample -> sample.metricKey().equals(
                ApacheRocketMqBusinessMetricsCollector.CONSUMER_LAG_MAX_QUEUE)
                && "orders".equals(sample.labels().get("consumerGroup"))).singleElement()
                .satisfies(sample -> assertThat(sample.value()).isEqualTo(42D));
        assertThat(samples).filteredOn(sample -> sample.metricKey().equals(
                ApacheRocketMqBusinessMetricsCollector.CONSUMER_DELAY_SECONDS)
                && "orders".equals(sample.labels().get("consumerGroup"))).singleElement()
                .satisfies(sample -> assertThat(sample.value()).isEqualTo(30D));
        assertThat(samples).filteredOn(sample -> sample.metricKey().equals(
                ApacheRocketMqBusinessMetricsCollector.TOPIC_BACKLOG_TOTAL)
                && "orders-topic".equals(sample.labels().get("topic"))).singleElement()
                .satisfies(sample -> assertThat(sample.value()).isEqualTo(59D));
    }

    @Test
    void skipsUnsupportedVendorTest() {
        InstanceVO instance = apacheInstance();
        instance.setVendor(InstanceVendor.ALIYUN);

        assertThat(new ApacheRocketMqBusinessMetricsCollector(mock(InstanceProviderRegistry.class)).collect(instance))
                .isEmpty();
    }

    @Test
    void supportsLegacyInstancesWithoutAnExplicitVendorTest() {
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        InstanceProvider provider = mock(InstanceProvider.class);
        when(registry.byInstanceId("local")).thenReturn(Optional.of(provider));
        when(provider.listConsumerGroups("local", null)).thenReturn(List.of());
        InstanceVO legacyInstance = apacheInstance();
        legacyInstance.setVendor(null);

        assertThat(new ApacheRocketMqBusinessMetricsCollector(registry).collect(legacyInstance)).isEmpty();
        verify(provider).listConsumerGroups("local", null);
    }

    @Test
    void reportsUnavailableWhenProviderFailsTest() {
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        when(registry.byInstanceId("local")).thenThrow(new IllegalStateException("offline"));

        assertThat(new ApacheRocketMqBusinessMetricsCollector(registry).collect(apacheInstance())).hasSize(4)
                .allSatisfy(sample -> {
                    assertThat(sample.availability()).isEqualTo(MetricAvailability.UNAVAILABLE);
                    assertThat(sample.value()).isNull();
                    assertThat(sample.unavailableReason()).isEqualTo("BUSINESS_METRICS_COLLECTION_FAILED");
                });
    }

    @Test
    void doesNotTurnMissingGroupStatsIntoZeroLagTest() {
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        InstanceProvider provider = mock(InstanceProvider.class);
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("orders");
        group.setClusterId("cluster-a");
        when(registry.byInstanceId("local")).thenReturn(Optional.of(provider));
        when(provider.listConsumerGroups("local", null)).thenReturn(List.of(group));

        List<MetricSample> samples = new ApacheRocketMqBusinessMetricsCollector(registry).collect(apacheInstance());

        assertThat(samples).hasSize(4).allSatisfy(sample -> {
            assertThat(sample.availability()).isEqualTo(MetricAvailability.UNAVAILABLE);
            assertThat(sample.value()).isNull();
            assertThat(sample.labels()).containsEntry("consumerGroup", "orders");
            assertThat(sample.unavailableReason()).isEqualTo("CONSUMER_STATS_UNAVAILABLE");
        });
    }

    private static ConsumerGroupVO group(String name, String clusterId, long lag) {
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName(name);
        group.setClusterId(clusterId);
        group.setTotalLag(lag);
        group.setConsumeStatsAvailable(true);
        group.setConsumptionTimestampAvailable(true);
        group.setDelaySeconds(30);
        return group;
    }

    private static InstanceVO apacheInstance() {
        return InstanceVO.builder().name("local").endpoint("localhost:9876").vendor(InstanceVendor.APACHE).build();
    }

    @Test
    void supportsApacheAndVendorLessInstancesWithAName() {
        ApacheRocketMqBusinessMetricsCollector collector =
                new ApacheRocketMqBusinessMetricsCollector(mock(InstanceProviderRegistry.class));

        assertThat(collector.supports(apacheInstance())).isTrue();
        assertThat(collector.supports(InstanceVO.builder().name("local").endpoint("localhost:9876").build()))
                .isTrue();
        assertThat(collector.supports(InstanceVO.builder().name("cloud").endpoint("x")
                .vendor(InstanceVendor.TENCENT).build())).isFalse();
        assertThat(collector.supports(InstanceVO.builder().vendor(InstanceVendor.APACHE).build())).isFalse();
        assertThat(collector.supports(null)).isFalse();
    }

    @Test
    void exposesTheBusinessMetricKeys() {
        assertThat(new ApacheRocketMqBusinessMetricsCollector(mock(InstanceProviderRegistry.class)).metricKeys())
                .containsExactlyInAnyOrder("consumer.lag.total", "consumer.lag.max_queue",
                        "consumer.delay.seconds", "topic.backlog.total");
    }
}
