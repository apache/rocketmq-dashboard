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
import org.apache.rocketmq.studio.cluster.metrics.ClusterMetricsCollector;
import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.cluster.metrics.MetricSnapshotRepository;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class NativeAlertRuleTestServiceTest {
    private final MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);

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
                new AlertRuleEvaluator(), snapshots).test(rule);

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
                new AlertRuleEvaluator(), snapshots).test(rule);

        assertThat(result.samples()).singleElement().satisfies(sample -> {
            assertThat(sample.currentValue()).isEqualTo(20);
            assertThat(sample.conditionMet()).isTrue();
        });
    }

    @Test
    void normalizesTheMetricKeyBeforePolicyAndSampleFilteringTest() {
        InstanceRepository instances = mock(InstanceRepository.class);
        BusinessMetricsCollector collector = mock(BusinessMetricsCollector.class);
        InstanceVO instance = InstanceVO.builder().name("local").build();
        when(instances.findByIdentifier("local")).thenReturn(Optional.of(instance));
        when(collector.supports(instance)).thenReturn(true);
        when(collector.collect(instance)).thenReturn(List.of(sample("orders", 20)));
        AlertRuleVO rule = AlertRuleVO.builder().domain(AlertDomain.BUSINESS)
                .metric("  consumer.lag.total  ")
                .instanceId(" local ")
                .operator(" > ")
                .threshold(10)
                .build();

        AlertRuleTestResultVO result = new NativeAlertRuleTestService(instances, List.of(), List.of(collector),
                new AlertRuleEvaluator(), snapshots).test(rule);

        assertThat(result.samples()).singleElement().satisfies(sample -> {
            assertThat(sample.currentValue()).isEqualTo(20);
            assertThat(sample.conditionMet()).isTrue();
        });
    }

    @Test
    void keepsResultsFromHealthyCollectorsWhenAnotherCollectorFailsTest() {
        InstanceRepository instances = mock(InstanceRepository.class);
        BusinessMetricsCollector failing = mock(BusinessMetricsCollector.class);
        BusinessMetricsCollector healthy = mock(BusinessMetricsCollector.class);
        InstanceVO instance = InstanceVO.builder().name("local").build();
        when(instances.findByIdentifier("local")).thenReturn(Optional.of(instance));
        when(failing.supports(instance)).thenReturn(true);
        when(failing.collect(instance)).thenThrow(new IllegalStateException("provider unavailable"));
        when(healthy.supports(instance)).thenReturn(true);
        when(healthy.collect(instance)).thenReturn(List.of(sample("orders", 20)));
        AlertRuleVO rule = AlertRuleVO.builder().domain(AlertDomain.BUSINESS).metric("consumer.lag.total")
                .instanceId("local").consumerGroup("orders").operator(">").threshold(10).build();

        AlertRuleTestResultVO result = new NativeAlertRuleTestService(instances, List.of(),
                List.of(failing, healthy), new AlertRuleEvaluator(), snapshots).test(rule);

        assertThat(result.samples()).singleElement()
                .satisfies(sample -> assertThat(sample.currentValue()).isEqualTo(20));
    }

    @Test
    void rejectsARuleWithoutMetricInsteadOfFailingWithANullPointerExceptionTest() {
        InstanceRepository instances = mock(InstanceRepository.class);
        BusinessMetricsCollector collector = mock(BusinessMetricsCollector.class);
        InstanceVO instance = InstanceVO.builder().name("local").build();
        when(instances.findByIdentifier("local")).thenReturn(Optional.of(instance));
        when(collector.supports(instance)).thenReturn(true);
        when(collector.collect(instance)).thenReturn(List.of(sample("orders", 20)));
        AlertRuleVO rule = AlertRuleVO.builder().domain(AlertDomain.BUSINESS).instanceId("local")
                .operator(">").threshold(10).build();

        assertThatThrownBy(() -> new NativeAlertRuleTestService(instances, List.of(), List.of(collector),
                new AlertRuleEvaluator(), snapshots).test(rule))
                .isInstanceOf(BusinessException.class)
                .hasMessage("metric is required")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
    }

    @ParameterizedTest
    @CsvSource(value = {"AVG, 336.6666666667, true", "MAX, 500, true", "MIN, 10, false", "SUM, 1010, true",
        "LAST, 10, false", "NULL, 10, false", "avg, 336.6666666667, true"}, nullValues = "NULL")
    void aggregatesTheStoredWindowBeforeEvaluatingARuleTest(String aggregation, double expectedValue,
            boolean expectedCondition) {
        MetricSample current = sample("orders", 10);
        when(snapshots.findRecent(current, current.collectedAt().minusSeconds(300)))
                .thenReturn(List.of(sample("orders", 500, current.collectedAt().minusSeconds(120)),
                        sample("orders", 500, current.collectedAt().minusSeconds(60))));
        AlertRuleVO rule = AlertRuleVO.builder().domain(AlertDomain.BUSINESS).metric("consumer.lag.total")
                .instanceId("local").consumerGroup("orders").operator(">").threshold(100)
                .aggregation(aggregation).windowSeconds(300).enabled(false).build();

        AlertRuleTestResultVO result = testBusinessRule(rule, current, sample("payments", 1000),
                sample("consumer.delay.seconds", "orders", 1000));

        assertThat(result.samples()).singleElement().satisfies(sample -> {
            assertThat(sample.labels()).containsEntry("consumerGroup", "orders");
            assertThat(sample.availability()).isEqualTo("AVAILABLE");
            assertThat(sample.currentValue()).isCloseTo(expectedValue, within(0.000001));
            assertThat(sample.conditionMet()).isEqualTo(expectedCondition);
        });
        verify(snapshots).findRecent(current, current.collectedAt().minusSeconds(300));
        verifyNoMoreInteractions(snapshots);
    }

    @Test
    void aggregatesClusterMetricWindowsBeforeEvaluatingARuleTest() {
        InstanceRepository instances = mock(InstanceRepository.class);
        ClusterMetricsCollector collector = mock(ClusterMetricsCollector.class);
        InstanceVO instance = InstanceVO.builder().name("local").build();
        when(instances.findByIdentifier("local")).thenReturn(Optional.of(instance));
        when(collector.supports(instance)).thenReturn(true);
        MetricSample current = new MetricSample("broker.disk.usage_ratio", AlertDomain.CLUSTER, "local", "cluster-a",
                Map.of("brokerName", "broker-a"), 0.1, MetricAvailability.AVAILABLE, Instant.now());
        MetricSample previous = new MetricSample(current.metricKey(), current.domain(), current.instanceId(),
                current.clusterId(), current.labels(), 0.9, MetricAvailability.AVAILABLE,
                current.collectedAt().minusSeconds(60));
        when(collector.collect(instance)).thenReturn(List.of(current));
        when(snapshots.findRecent(current, current.collectedAt().minusSeconds(300)))
                .thenReturn(List.of(previous));
        AlertRuleVO rule = AlertRuleVO.builder().domain(AlertDomain.CLUSTER).metric(current.metricKey())
                .instanceId("local").brokerName("broker-a").operator(">").threshold(0.8)
                .aggregation("MAX").windowSeconds(300).build();

        AlertRuleTestResultVO result = new NativeAlertRuleTestService(instances, List.of(collector), List.of(),
                new AlertRuleEvaluator(), snapshots).test(rule);

        assertThat(result.samples()).singleElement().satisfies(sample -> {
            assertThat(sample.currentValue()).isEqualTo(0.9);
            assertThat(sample.conditionMet()).isTrue();
        });
        verify(snapshots).findRecent(current, current.collectedAt().minusSeconds(300));
        verifyNoMoreInteractions(snapshots);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void evaluatesCurrentSampleWithoutReadingSnapshotsWhenWindowIsDisabledTest(int windowSeconds) {
        AlertRuleVO rule = AlertRuleVO.builder().metric("consumer.lag.total").instanceId("local")
                .operator(">").threshold(100).aggregation("AVG").windowSeconds(windowSeconds).build();

        AlertRuleTestResultVO result = testBusinessRule(rule, sample("orders", 10));

        assertThat(result.samples()).singleElement().satisfies(sample -> {
            assertThat(sample.currentValue()).isEqualTo(10);
            assertThat(sample.conditionMet()).isFalse();
        });
        verifyNoInteractions(snapshots);
    }

    @Test
    void fallsBackToCurrentSampleWhenStoredWindowIsEmptyTest() {
        MetricSample current = sample("orders", 10);
        when(snapshots.findRecent(current, current.collectedAt().minusSeconds(300))).thenReturn(List.of());
        AlertRuleVO rule = AlertRuleVO.builder().metric("consumer.lag.total").instanceId("local")
                .operator(">").threshold(5).aggregation("AVG").windowSeconds(300).build();

        AlertRuleTestResultVO result = testBusinessRule(rule, current);

        assertThat(result.samples()).singleElement().satisfies(sample -> {
            assertThat(sample.currentValue()).isEqualTo(10);
            assertThat(sample.conditionMet()).isTrue();
        });
        verify(snapshots).findRecent(current, current.collectedAt().minusSeconds(300));
        verifyNoMoreInteractions(snapshots);
    }

    @ParameterizedTest
    @EnumSource(value = MetricAvailability.class, names = {"UNAVAILABLE", "UNSUPPORTED", "STALE"})
    void preservesUnavailableSampleInsteadOfReplacingItWithHistoricalValuesTest(MetricAvailability availability) {
        InstanceRepository instances = mock(InstanceRepository.class);
        ClusterMetricsCollector collector = mock(ClusterMetricsCollector.class);
        InstanceVO instance = InstanceVO.builder().name("local").build();
        when(instances.findByIdentifier("local")).thenReturn(Optional.of(instance));
        when(collector.supports(instance)).thenReturn(true);
        MetricSample unavailable = new MetricSample("broker.availability", AlertDomain.CLUSTER, "local", null,
                Map.of("brokerName", "broker-a"), null, availability, Instant.now(), "collection failed");
        when(collector.collect(instance)).thenReturn(List.of(unavailable));
        AlertRuleVO rule = AlertRuleVO.builder().domain(AlertDomain.CLUSTER).metric(unavailable.metricKey())
                .instanceId("local").operator("UNAVAILABLE").aggregation("AVG").windowSeconds(300).build();

        AlertRuleTestResultVO result = new NativeAlertRuleTestService(instances, List.of(collector), List.of(),
                new AlertRuleEvaluator(), snapshots).test(rule);

        assertThat(result.samples()).singleElement().satisfies(sample -> {
            assertThat(sample.availability()).isEqualTo(availability.name());
            assertThat(sample.currentValue()).isNull();
            assertThat(sample.conditionMet()).isEqualTo(availability == MetricAvailability.UNAVAILABLE);
            assertThat(sample.unavailableReason()).isEqualTo("collection failed");
        });
        verifyNoInteractions(snapshots);
    }

    private AlertRuleTestResultVO testBusinessRule(AlertRuleVO rule, MetricSample... samples) {
        InstanceRepository instances = mock(InstanceRepository.class);
        BusinessMetricsCollector collector = mock(BusinessMetricsCollector.class);
        InstanceVO instance = InstanceVO.builder().name("local").build();
        when(instances.findByIdentifier("local")).thenReturn(Optional.of(instance));
        when(collector.supports(instance)).thenReturn(true);
        when(collector.collect(instance)).thenReturn(List.of(samples));
        return new NativeAlertRuleTestService(instances, List.of(), List.of(collector),
                new AlertRuleEvaluator(), snapshots).test(rule);
    }

    private static MetricSample sample(String group, double value) {
        return sample("consumer.lag.total", group, value);
    }

    private static MetricSample sample(String group, double value, Instant collectedAt) {
        return new MetricSample("consumer.lag.total", AlertDomain.BUSINESS, "local", null,
                Map.of("consumerGroup", group), value, MetricAvailability.AVAILABLE, collectedAt);
    }

    private static MetricSample sample(String metric, String group, double value) {
        return new MetricSample(metric, AlertDomain.BUSINESS, "local", null,
                Map.of("consumerGroup", group), value, MetricAvailability.AVAILABLE, Instant.now());
    }
}
