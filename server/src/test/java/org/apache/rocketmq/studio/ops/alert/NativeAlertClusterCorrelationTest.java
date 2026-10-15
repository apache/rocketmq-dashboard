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

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.MetricSnapshotRepository;
import org.apache.rocketmq.studio.cluster.metrics.collectors.ApacheRocketMqBusinessMetricsCollector;
import org.apache.rocketmq.studio.cluster.metrics.collectors.ApacheRocketMqClusterMetricsCollector;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NativeAlertClusterCorrelationTest {
    @ParameterizedTest
    @CsvSource(value = {"cluster-a, cluster-a", "NULL, legacy", "'  ', legacy"}, nullValues = "NULL")
    void enrichesEventLabelsWithoutChangingTheExistingFingerprintTest(String clusterId, String eventCluster) {
        Map<String, String> identityLabels = Map.of("consumerGroup", "orders", "clusterId", "legacy",
                "clusterName", "friendly-name");
        MetricSample sample = new MetricSample("consumer.lag.total", AlertDomain.BUSINESS, "shared-nameserver",
                clusterId, identityLabels, 100D, MetricAvailability.AVAILABLE, Instant.now());
        AlertRuleVO rule = AlertRuleVO.builder().id(2L).name("Orders backlog").domain(AlertDomain.BUSINESS)
                .metric("consumer.lag.total").operator(">").threshold(10).instanceId(sample.instanceId())
                .consumerGroup("orders").enabled(true).consecutiveSamples(1).build();
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.find(any())).thenReturn(Optional.empty());
        when(states.save(any(), any())).thenReturn(true);
        AlertRepository alerts = mock(AlertRepository.class);
        when(alerts.saveAlert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        NotificationOutboxService outbox = mock(NotificationOutboxService.class);
        AlertNotificationSuppressionService suppression = mock(AlertNotificationSuppressionService.class);
        when(suppression.findSuppressingClusterAlert(any())).thenReturn(Optional.empty());

        new NativeAlertEvaluationService(new AlertRuleEvaluator(), new AlertStateMachine(), states,
                mock(MetricSnapshotRepository.class), alerts, outbox, suppression).evaluate(rule, sample);

        org.mockito.ArgumentCaptor<SystemAlertVO> event = org.mockito.ArgumentCaptor.forClass(SystemAlertVO.class);
        verify(alerts).saveAlert(event.capture());
        assertThat(event.getValue().getLabels()).containsEntry("clusterId", eventCluster)
                .containsEntry("clusterName", "friendly-name");
        String originalFingerprint = AlertFingerprint.of(rule.getId(), sample.instanceId(), identityLabels);
        assertThat(event.getValue().getFingerprint()).isEqualTo(originalFingerprint);
        verify(states).save(eq(new AlertStateKey(rule.getId(), originalFingerprint)), any());
        verify(outbox).enqueue(eq(event.getValue()), eq(rule), eq(event.getValue().getLabels()));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "cluster-a, cluster-b, false",
        "cluster-a, cluster-a, true",
        "cluster-a, NULL, true",
        "NULL, cluster-b, true",
        "NULL, NULL, true"
    }, nullValues = "NULL")
    void correlatesRealCollectorEventsWithinTheirKnownClusterTest(String brokerCluster, String consumerCluster,
            boolean expectedSuppression) throws Exception {
        InstanceVO instance = InstanceVO.builder().name("shared-nameserver").endpoint("localhost:9876")
                .vendor(InstanceVendor.APACHE).build();
        RuntimeAdminClientResolver resolver = mock(RuntimeAdminClientResolver.class);
        MQAdminExt admin = mock(MQAdminExt.class);
        ClusterInfo topology = new ClusterInfo();
        topology.setBrokerAddrTable(Map.of(
                "broker-a", new BrokerData(brokerCluster, "broker-a", new HashMap<>(Map.of(0L, "broker-a:10911"))),
                "broker-b", new BrokerData("cluster-b", "broker-b", new HashMap<>(Map.of(0L, "broker-b:10911")))));
        when(admin.examineBrokerClusterInfo()).thenReturn(topology);
        when(admin.fetchBrokerRuntimeStats("broker-a:10911")).thenThrow(new IllegalStateException("offline"));
        KVTable healthy = new KVTable();
        healthy.setTable(new HashMap<>());
        when(admin.fetchBrokerRuntimeStats("broker-b:10911")).thenReturn(healthy);
        when(resolver.execute(eq(instance), any(MqAdminExtFactory.AdminAction.class))).thenAnswer(invocation ->
                invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1).apply(admin));
        List<MetricSample> clusterSamples = new ApacheRocketMqClusterMetricsCollector(resolver).collect(instance);
        assertThat(clusterSamples).filteredOn(sample -> "broker.availability".equals(sample.metricKey())
                        && "broker-a".equals(sample.labels().get("brokerName")))
                .singleElement().satisfies(sample -> assertThat(sample.clusterId()).isEqualTo(brokerCluster));

        InstanceProvider provider = mock(InstanceProvider.class);
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("orders");
        group.setClusterId(consumerCluster);
        group.setConsumeStatsAvailable(true);
        group.setTotalLag(100);
        when(registry.byInstanceId(instance.getName())).thenReturn(Optional.of(provider));
        when(provider.listConsumerGroups(instance.getName(), null)).thenReturn(List.of(group));
        when(provider.getGroupProgress(instance.getName(), "orders")).thenReturn(List.of());
        List<MetricSample> businessSamples = new ApacheRocketMqBusinessMetricsCollector(registry).collect(instance);
        assertThat(businessSamples).filteredOn(sample -> "consumer.lag.total".equals(sample.metricKey()))
                .singleElement().satisfies(sample -> assertThat(sample.clusterId()).isEqualTo(consumerCluster));

        AlertRuleVO clusterRule = AlertRuleVO.builder().id(1L).name("Broker offline").domain(AlertDomain.CLUSTER)
                .metric("broker.availability").operator("UNAVAILABLE").instanceId(instance.getName())
                .brokerName("broker-a").enabled(true).consecutiveSamples(1).build();
        AlertRuleVO businessRule = AlertRuleVO.builder().id(2L).name("Orders backlog").domain(AlertDomain.BUSINESS)
                .metric("consumer.lag.total").operator(">").threshold(10).instanceId(instance.getName())
                .consumerGroup("orders").enabled(true).consecutiveSamples(1).build();
        AlertService service = mock(AlertService.class);
        when(service.listRules(AlertDomain.CLUSTER)).thenReturn(List.of(clusterRule));
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(businessRule));
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.find(any())).thenReturn(Optional.empty());
        when(states.save(any(), any())).thenReturn(true);
        List<SystemAlertVO> emitted = new ArrayList<>();
        AlertRepository alerts = mock(AlertRepository.class);
        when(alerts.saveAlert(any())).thenAnswer(invocation -> {
            SystemAlertVO event = invocation.getArgument(0);
            event.setId((long) emitted.size() + 1);
            emitted.add(event);
            return event;
        });
        when(alerts.findAlertsPage(any())).thenAnswer(invocation -> {
            List<SystemAlertVO> candidates = emitted.stream().filter(event -> event.getDomain() == AlertDomain.CLUSTER)
                    .toList();
            return PageResult.of(candidates, candidates.size(), 1, 100);
        });
        NotificationOutboxService outbox = mock(NotificationOutboxService.class);
        AlertNotificationSuppressionService suppression = new AlertNotificationSuppressionService(alerts);
        AlertStateMachine stateMachine = new AlertStateMachine();
        NativeAlertProcessor processor = new NativeAlertProcessor(service,
                new NativeAlertEvaluationService(new AlertRuleEvaluator(), stateMachine, states,
                        mock(MetricSnapshotRepository.class), alerts, outbox, suppression),
                stateMachine, states, alerts, outbox, suppression, mock(PlatformTransactionManager.class));

        processor.process(clusterSamples);
        processor.process(businessSamples);

        assertThat(emitted).hasSize(2);
        SystemAlertVO businessEvent = emitted.get(1);
        assertThat(businessEvent.getDomain()).isEqualTo(AlertDomain.BUSINESS);
        assertThat(businessEvent.isNotificationSuppressed()).isEqualTo(expectedSuppression);
        if (expectedSuppression) {
            assertThat(businessEvent.getSuppressionCauseAlertId()).isEqualTo(emitted.get(0).getId());
            verify(outbox, never()).enqueue(eq(businessEvent), eq(businessRule), any());
        } else {
            assertThat(businessEvent.getSuppressionCauseAlertId()).isNull();
            verify(outbox).enqueue(eq(businessEvent), eq(businessRule), any());
        }
    }
}
