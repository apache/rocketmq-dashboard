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

import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.MetricCollectionScope;
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.cluster.metrics.MetricSnapshotRepository;
import org.apache.rocketmq.studio.cluster.metrics.collectors.ApacheRocketMqBusinessMetricsCollector;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class NativeAlertProcessorTest {

    @Test
    void continuesWithLaterRulesWhenOneEvaluationFailsTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO failing = rule(1L, "local", "orders", 1);
        failing.setWindowSeconds(300);
        AlertRuleVO healthy = rule(2L, "local", "orders", 1);
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(failing, healthy));
        MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);
        when(snapshots.findRecent(any(MetricSample.class), any(Instant.class)))
                .thenThrow(new IllegalStateException("snapshot read failed"));
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.find(any(AlertStateKey.class))).thenReturn(Optional.empty());
        when(states.save(any(AlertStateKey.class), any(AlertRuleState.class))).thenReturn(true);

        NativeAlertProcessor processor = processor(service, states, snapshots, mock(AlertRepository.class),
                mock(NotificationOutboxService.class), suppression());

        assertThatCode(() -> processor.process(List.of(sample("orders")))).doesNotThrowAnyException();

        verify(states).save(org.mockito.ArgumentMatchers.argThat(key -> key.ruleId().equals(2L)),
                any(AlertRuleState.class));
    }

    @Test
    void continuesWithLaterRulesWhenAlertPersistenceFailsTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO failing = rule(1L, "local", "orders", 1);
        AlertRuleVO healthy = rule(2L, "local", "orders", 1);
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(failing, healthy));
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.find(any(AlertStateKey.class))).thenReturn(Optional.empty());
        when(states.save(any(AlertStateKey.class), any(AlertRuleState.class))).thenReturn(true);
        AlertRepository alerts = mock(AlertRepository.class);
        when(alerts.saveAlert(any(SystemAlertVO.class)))
                .thenThrow(new IllegalStateException("event insert failed"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        NotificationOutboxService outbox = mock(NotificationOutboxService.class);

        NativeAlertProcessor processor = processor(service, states, mock(MetricSnapshotRepository.class), alerts,
                outbox, suppression());

        assertThatCode(() -> processor.process(List.of(sample("orders")))).doesNotThrowAnyException();

        verify(states).save(org.mockito.ArgumentMatchers.argThat(key -> key.ruleId().equals(2L)),
                any(AlertRuleState.class));
        verify(outbox).enqueue(any(SystemAlertVO.class), org.mockito.ArgumentMatchers.same(healthy),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void doesNotSwallowErrorsTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule = rule(1L, "local", "orders", 1);
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule));
        NativeAlertEvaluationService evaluationService = mock(NativeAlertEvaluationService.class);
        org.mockito.Mockito.doThrow(new AssertionError("fatal evaluation failure"))
                .when(evaluationService).evaluate(any(AlertRuleVO.class), any(MetricSample.class));

        NativeAlertProcessor processor = new NativeAlertProcessor(service, evaluationService,
                new AlertStateMachine(), mock(AlertStateRepository.class), mock(AlertRepository.class),
                mock(NotificationOutboxService.class), suppression(), mockTxManager());

        assertThatThrownBy(() -> processor.process(List.of(sample("orders"))))
                .isInstanceOf(AssertionError.class)
                .hasMessage("fatal evaluation failure");
    }

    @Test
    void requiresInstanceScopeBeforeProcessingNativeSamplesTest() {
        AlertService service = mock(AlertService.class);
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule(null, null, 1)));
        AlertStateRepository states = mock(AlertStateRepository.class);
        AlertRepository alerts = mock(AlertRepository.class);

        processor(service, states, alerts).process(List.of(sample("orders")));

        verifyNoInteractions(states, alerts);
    }

    @Test
    void appliesConsumerGroupScopeAndConsecutiveSampleRequirementTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule = rule("local", "orders", 2);
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule));
        Map<AlertStateKey, AlertRuleState> saved = new HashMap<>();
        AlertStateRepository states = new AlertStateRepository() {
            @Override
            public Optional<AlertRuleState> find(AlertStateKey key) {
                return Optional.ofNullable(saved.get(key));
            }

            @Override
            public boolean save(AlertStateKey key, AlertRuleState state) {
                saved.put(key, state);
                return true;
            }

            @Override
            public boolean acknowledge(AlertStateKey key, Instant firedAt) {
                return false;
            }

            @Override
            public void deleteByRuleId(Long ruleId) {
            }
        };
        AlertRepository alerts = mock(AlertRepository.class);
        NativeAlertProcessor processor = processor(service, states, alerts);

        processor.process(List.of(sample("payments")));
        assertThat(saved).isEmpty();

        processor.process(List.of(sample("orders")));
        assertThat(saved.values()).singleElement().extracting(AlertRuleState::status)
                .isEqualTo(AlertStateStatus.PENDING);
        verifyNoInteractions(alerts);

        processor.process(List.of(sample("orders")));
        assertThat(saved.values()).singleElement().extracting(AlertRuleState::status)
                .isEqualTo(AlertStateStatus.FIRING);
    }

    @Test
    void loadsRulesOncePerDomainForABatchOfSamplesTest() {
        AlertService service = mock(AlertService.class);
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of());

        processor(service, mock(AlertStateRepository.class), mock(AlertRepository.class))
                .process(List.of(sample("orders"), sample("payments")));

        verify(service, times(1)).listRules(AlertDomain.BUSINESS);
    }

    @Test
    void doesNotEmitLifecycleEventsWhenAnotherEvaluatorWinsTheStateWriteTest() {
        AlertService service = mock(AlertService.class);
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule("local", "orders", 1)));
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.find(any(AlertStateKey.class))).thenReturn(Optional.empty());
        when(states.save(any(AlertStateKey.class), any(AlertRuleState.class))).thenReturn(false);
        AlertRepository alerts = mock(AlertRepository.class);

        processor(service, states, alerts).process(List.of(sample("orders")));

        verifyNoInteractions(alerts);
    }

    @Test
    void evaluatesMaxAggregationAcrossTheConfiguredSnapshotWindowTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule = AlertRuleVO.builder().id(1L).domain(AlertDomain.BUSINESS).name("Orders lag")
                .metric("consumer.lag.total").operator(">").threshold(25).enabled(true).instanceId("local")
                .consumerGroup("orders").aggregation("MAX").windowSeconds(300).build();
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule));
        MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);
        MetricSample current = sample("orders");
        when(snapshots.findRecent(any(MetricSample.class), any(Instant.class)))
                .thenReturn(List.of(sample("orders", 10D), sample("orders", 30D), current));
        Map<AlertStateKey, AlertRuleState> saved = new HashMap<>();
        AlertStateRepository states = new AlertStateRepository() {
            @Override
            public Optional<AlertRuleState> find(AlertStateKey key) {
                return Optional.empty();
            }

            @Override
            public boolean save(AlertStateKey key, AlertRuleState state) {
                saved.put(key, state);
                return true;
            }

            @Override
            public boolean acknowledge(AlertStateKey key, Instant firedAt) {
                return false;
            }

            @Override
            public void deleteByRuleId(Long ruleId) {
            }
        };

        processor(service, states, snapshots, mock(AlertRepository.class), mock(NotificationOutboxService.class),
                suppression()).process(List.of(current));

        assertThat(saved.values()).singleElement().satisfies(state -> {
            assertThat(state.status()).isEqualTo(AlertStateStatus.FIRING);
            assertThat(state.currentValue()).isEqualTo(30D);
        });
    }

    @Test
    void evaluatesSumAggregationAcrossTheConfiguredSnapshotWindowTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule = AlertRuleVO.builder().id(1L).domain(AlertDomain.BUSINESS).name("Orders lag")
                .metric("consumer.lag.total").operator(">").threshold(50).enabled(true).instanceId("local")
                .consumerGroup("orders").aggregation("SUM").windowSeconds(300).build();
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule));
        MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);
        MetricSample current = sample("orders");
        when(snapshots.findRecent(any(MetricSample.class), any(Instant.class)))
                .thenReturn(List.of(sample("orders", 10D), sample("orders", 30D), current));
        Map<AlertStateKey, AlertRuleState> saved = new HashMap<>();
        AlertStateRepository states = new AlertStateRepository() {
            @Override
            public Optional<AlertRuleState> find(AlertStateKey key) {
                return Optional.empty();
            }

            @Override
            public boolean save(AlertStateKey key, AlertRuleState state) {
                saved.put(key, state);
                return true;
            }

            @Override
            public boolean acknowledge(AlertStateKey key, Instant firedAt) {
                return false;
            }

            @Override
            public void deleteByRuleId(Long ruleId) {
            }
        };

        processor(service, states, snapshots, mock(AlertRepository.class), mock(NotificationOutboxService.class),
                suppression()).process(List.of(current));

        assertThat(saved.values()).singleElement().satisfies(state -> {
            assertThat(state.status()).isEqualTo(AlertStateStatus.FIRING);
            assertThat(state.currentValue()).isEqualTo(60D);
        });
    }

    @Test
    void evaluatesAggregationIndependentlyOfTheDefaultLocaleTest() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            AlertService service = mock(AlertService.class);
            AlertRuleVO rule = AlertRuleVO.builder().id(1L).domain(AlertDomain.BUSINESS).name("Orders lag")
                    .metric("consumer.lag.total").operator(">").threshold(5).enabled(true).instanceId("local")
                    .consumerGroup("orders").aggregation("min").windowSeconds(300).build();
            when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule));
            MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);
            MetricSample current = sample("orders", 20D);
            when(snapshots.findRecent(any(MetricSample.class), any(Instant.class)))
                    .thenReturn(List.of(sample("orders", 10D), sample("orders", 30D), current));
            AlertStateRepository states = mock(AlertStateRepository.class);
            when(states.find(any(AlertStateKey.class))).thenReturn(Optional.empty());
            when(states.save(any(AlertStateKey.class), any(AlertRuleState.class))).thenReturn(true);

            processor(service, states, snapshots, mock(AlertRepository.class), mock(NotificationOutboxService.class),
                    suppression()).process(List.of(current));

            org.mockito.ArgumentCaptor<AlertRuleState> saved = org.mockito.ArgumentCaptor.forClass(AlertRuleState.class);
            verify(states).save(any(AlertStateKey.class), saved.capture());
            assertThat(saved.getValue().currentValue()).isEqualTo(10D);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void recordsTheRuleTriggerTimeWhenEmittingAFiringEventTest() {
        AlertService service = mock(AlertService.class);
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule("local", "orders", 1)));
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.find(any(AlertStateKey.class))).thenReturn(Optional.empty());
        when(states.save(any(AlertStateKey.class), any(AlertRuleState.class))).thenReturn(true);
        AlertRepository alerts = mock(AlertRepository.class);
        when(alerts.saveAlert(any(SystemAlertVO.class))).thenAnswer(invocation -> {
            SystemAlertVO event = invocation.getArgument(0);
            event.setId(8L);
            return event;
        });

        processor(service, states, alerts).process(List.of(sample("orders")));

        verify(alerts).markRuleTriggered(org.mockito.ArgumentMatchers.eq(1L), any(String.class));
    }

    @Test
    void enqueuesNotificationForLifecycleTransitionsTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule = rule("local", "orders", 1);
        rule.setChannels(List.of("sms"));
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule));
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.find(any(AlertStateKey.class))).thenReturn(Optional.empty());
        when(states.save(any(AlertStateKey.class), any(AlertRuleState.class))).thenReturn(true);
        AlertRepository alerts = mock(AlertRepository.class);
        when(alerts.saveAlert(any(SystemAlertVO.class))).thenAnswer(invocation -> {
            SystemAlertVO event = invocation.getArgument(0);
            event.setId(9L);
            return event;
        });
        NotificationOutboxService outbox = mock(NotificationOutboxService.class);

        processor(service, states, mock(MetricSnapshotRepository.class), alerts, outbox, suppression())
                .process(List.of(sample("orders")));

        verify(outbox).enqueue(any(SystemAlertVO.class), org.mockito.ArgumentMatchers.same(rule),
                org.mockito.ArgumentMatchers.anyMap());
    }

    private static NativeAlertProcessor processor(AlertService service, AlertStateRepository states,
            MetricSnapshotRepository snapshots, AlertRepository alerts, NotificationOutboxService outbox,
            AlertNotificationSuppressionService suppression) {
        NativeAlertEvaluationService evaluationService = new NativeAlertEvaluationService(new AlertRuleEvaluator(),
                new AlertStateMachine(), states, snapshots, alerts, outbox, suppression);
        return new NativeAlertProcessor(service, evaluationService, new AlertStateMachine(), states, alerts, outbox,
                suppression, mockTxManager());
    }

    private static NativeAlertProcessor processor(AlertService service, AlertStateRepository states,
            AlertRepository alerts) {
        return processor(service, states, mock(MetricSnapshotRepository.class), alerts,
                mock(NotificationOutboxService.class), suppression());
    }

    @Test
    void suppressesBusinessFiringNotificationWhenAnActiveClusterIncidentMatchesTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule = rule("local", "orders", 1);
        rule.setChannels(List.of("dingtalk"));
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule));
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.find(any(AlertStateKey.class))).thenReturn(Optional.empty());
        when(states.save(any(AlertStateKey.class), any(AlertRuleState.class))).thenReturn(true);
        AlertRepository alerts = mock(AlertRepository.class);
        when(alerts.saveAlert(any(SystemAlertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NotificationOutboxService outbox = mock(NotificationOutboxService.class);
        AlertNotificationSuppressionService suppression = mock(AlertNotificationSuppressionService.class);
        SystemAlertVO clusterCause = SystemAlertVO.builder().id(11L).title("Broker unavailable").build();
        when(suppression.findSuppressingClusterAlert(any(SystemAlertVO.class))).thenReturn(Optional.of(clusterCause));

        processor(service, states, mock(MetricSnapshotRepository.class), alerts, outbox, suppression)
                .process(List.of(sample("orders")));

        org.mockito.ArgumentCaptor<SystemAlertVO> event = org.mockito.ArgumentCaptor.forClass(SystemAlertVO.class);
        verify(alerts).saveAlert(event.capture());
        assertThat(event.getValue().isNotificationSuppressed()).isTrue();
        assertThat(event.getValue().getSuppressionCauseAlertId()).isEqualTo(11L);
        assertThat(event.getValue().getSuppressionReason()).contains("#11").contains("Broker unavailable");
        verify(outbox, never()).enqueue(any(), any(), any());
    }

    @Test
    void doesNotSuppressResolvedBusinessNotificationTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule = rule("local", "orders", 1);
        rule.setChannels(List.of("dingtalk"));
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule));
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.find(any(AlertStateKey.class))).thenReturn(Optional.of(new AlertRuleState(AlertStateStatus.FIRING,
                1, 20D, null, Instant.now().minusSeconds(60), Instant.now().minusSeconds(30), null)));
        when(states.save(any(AlertStateKey.class), any(AlertRuleState.class))).thenReturn(true);
        AlertRepository alerts = mock(AlertRepository.class);
        when(alerts.saveAlert(any(SystemAlertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NotificationOutboxService outbox = mock(NotificationOutboxService.class);
        AlertNotificationSuppressionService suppression = mock(AlertNotificationSuppressionService.class);

        processor(service, states, mock(MetricSnapshotRepository.class), alerts, outbox, suppression)
                .process(List.of(sample("orders", 0D)));

        verify(suppression, never()).findSuppressingClusterAlert(any());
        verify(outbox).enqueue(any(), org.mockito.ArgumentMatchers.same(rule), any());
    }

    @Test
    void resolvesActiveFingerprintMissingFromSuccessfulCollectionScopeTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule = rule("local", "orders", 1);
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule));
        MetricSample oldSample = sample("orders");
        AlertStateKey oldKey = new AlertStateKey(rule.getId(),
                AlertFingerprint.of(rule.getId(), oldSample.instanceId(), oldSample.labels()));
        AlertRuleState firing = new AlertRuleState(AlertStateStatus.FIRING, 1, 20D,
                oldSample.collectedAt().minusSeconds(60), oldSample.collectedAt().minusSeconds(60),
                oldSample.collectedAt().minusSeconds(60), null);
        ActiveAlertState active = new ActiveAlertState(oldKey, firing, oldSample.instanceId(), oldSample.labels());
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.findActive(any(MetricCollectionScope.class), eq(List.of(rule)))).thenReturn(List.of(active));
        when(states.save(eq(oldKey), any(AlertRuleState.class))).thenReturn(true);
        AlertRepository alerts = mock(AlertRepository.class);
        when(alerts.saveAlert(any(SystemAlertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NotificationOutboxService outbox = mock(NotificationOutboxService.class);

        NativeAlertProcessor processor = new NativeAlertProcessor(service,
                new NativeAlertEvaluationService(new AlertRuleEvaluator(), new AlertStateMachine(), states,
                        mock(MetricSnapshotRepository.class), alerts, outbox, suppression()),
                new AlertStateMachine(), states, alerts, outbox, suppression(), mockTxManager());
        processor.processSuccessfulCollection(new MetricCollectionScope(AlertDomain.BUSINESS, "local",
                        java.util.Set.of("consumer.lag.total")), List.of());

        org.mockito.ArgumentCaptor<AlertRuleState> state = org.mockito.ArgumentCaptor.forClass(AlertRuleState.class);
        org.mockito.ArgumentCaptor<SystemAlertVO> event = org.mockito.ArgumentCaptor.forClass(SystemAlertVO.class);
        verify(states).save(eq(oldKey), state.capture());
        verify(alerts).saveAlert(event.capture());
        assertThat(state.getValue().status()).isEqualTo(AlertStateStatus.RESOLVED);
        assertThat(event.getValue().getTransition()).isEqualTo(AlertStateTransition.RESOLVED.name());
        assertThat(event.getValue().getLabels()).isEqualTo(oldSample.labels());
        verify(outbox).enqueue(any(SystemAlertVO.class), eq(rule), eq(oldSample.labels()));
    }

    @Test
    void reconcilesRemainingRulesWhenAnotherRuleHasNoMetricTest() {
        AlertService service = mock(AlertService.class);
        // A stored rule without a metric (legacy rows, and rules created through the API or the
        // JSON import, where `metric` carries no validation) is not part of any collection scope,
        // so it must be filtered out instead of aborting the reconcile pass for the whole scope.
        AlertRuleVO metricLess = AlertRuleVO.builder().id(9L).domain(AlertDomain.BUSINESS).name("No metric")
                .operator(">").threshold(10D).enabled(true).instanceId("local").consecutiveSamples(1).build();
        AlertRuleVO rule = rule("local", "orders", 1);
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(metricLess, rule));
        MetricSample oldSample = sample("orders");
        AlertStateKey oldKey = new AlertStateKey(rule.getId(),
                AlertFingerprint.of(rule.getId(), oldSample.instanceId(), oldSample.labels()));
        AlertRuleState firing = new AlertRuleState(AlertStateStatus.FIRING, 1, 20D,
                oldSample.collectedAt().minusSeconds(60), oldSample.collectedAt().minusSeconds(60),
                oldSample.collectedAt().minusSeconds(60), null);
        ActiveAlertState active = new ActiveAlertState(oldKey, firing, oldSample.instanceId(), oldSample.labels());
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.findActive(any(MetricCollectionScope.class), eq(List.of(rule)))).thenReturn(List.of(active));
        when(states.save(eq(oldKey), any(AlertRuleState.class))).thenReturn(true);
        AlertRepository alerts = mock(AlertRepository.class);
        when(alerts.saveAlert(any(SystemAlertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NotificationOutboxService outbox = mock(NotificationOutboxService.class);

        NativeAlertProcessor processor = new NativeAlertProcessor(service,
                new NativeAlertEvaluationService(new AlertRuleEvaluator(), new AlertStateMachine(), states,
                        mock(MetricSnapshotRepository.class), alerts, outbox, suppression()),
                new AlertStateMachine(), states, alerts, outbox, suppression(), mockTxManager());
        assertThatCode(() -> processor.processSuccessfulCollection(new MetricCollectionScope(AlertDomain.BUSINESS,
                "local", java.util.Set.of("consumer.lag.total")), List.of())).doesNotThrowAnyException();

        org.mockito.ArgumentCaptor<AlertRuleState> state = org.mockito.ArgumentCaptor.forClass(AlertRuleState.class);
        org.mockito.ArgumentCaptor<SystemAlertVO> event = org.mockito.ArgumentCaptor.forClass(SystemAlertVO.class);
        verify(states).save(eq(oldKey), state.capture());
        assertThat(state.getValue().status()).isEqualTo(AlertStateStatus.RESOLVED);
        // the recovery half of the same claim: without the fix the pass aborts before reaching any
        // of this, so no RESOLVED event is recorded and no recovery notification is queued
        verify(alerts).saveAlert(event.capture());
        assertThat(event.getValue().getTransition()).isEqualTo(AlertStateTransition.RESOLVED.name());
        verify(outbox).enqueue(any(SystemAlertVO.class), eq(rule), eq(oldSample.labels()));
    }

    @Test
    void resolvesActiveStateForBlankInstanceIdRuleMissingFromCollectionScopeTest() {
        AlertService service = mock(AlertService.class);
        // A blank instance_id is stored verbatim and means "every instance"; the repository side
        // already reads it through StringUtils.hasText, so this filter must not fall back to a
        // null check or such a rule can neither fire nor resolve.
        AlertRuleVO rule = rule("   ", "orders", 1);
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule));
        MetricSample oldSample = sample("orders");
        AlertStateKey oldKey = new AlertStateKey(rule.getId(),
                AlertFingerprint.of(rule.getId(), oldSample.instanceId(), oldSample.labels()));
        AlertRuleState firing = new AlertRuleState(AlertStateStatus.FIRING, 1, 20D,
                oldSample.collectedAt().minusSeconds(60), oldSample.collectedAt().minusSeconds(60),
                oldSample.collectedAt().minusSeconds(60), null);
        ActiveAlertState active = new ActiveAlertState(oldKey, firing, oldSample.instanceId(), oldSample.labels());
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.findActive(any(MetricCollectionScope.class), eq(List.of(rule)))).thenReturn(List.of(active));
        when(states.save(eq(oldKey), any(AlertRuleState.class))).thenReturn(true);
        AlertRepository alerts = mock(AlertRepository.class);
        when(alerts.saveAlert(any(SystemAlertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NotificationOutboxService outbox = mock(NotificationOutboxService.class);

        NativeAlertProcessor processor = new NativeAlertProcessor(service,
                new NativeAlertEvaluationService(new AlertRuleEvaluator(), new AlertStateMachine(), states,
                        mock(MetricSnapshotRepository.class), alerts, outbox, suppression()),
                new AlertStateMachine(), states, alerts, outbox, suppression(), mockTxManager());
        processor.processSuccessfulCollection(new MetricCollectionScope(AlertDomain.BUSINESS, "local",
                        java.util.Set.of("consumer.lag.total")), List.of());

        org.mockito.ArgumentCaptor<AlertRuleState> state = org.mockito.ArgumentCaptor.forClass(AlertRuleState.class);
        verify(states).save(eq(oldKey), state.capture());
        assertThat(state.getValue().status()).isEqualTo(AlertStateStatus.RESOLVED);
        verify(outbox).enqueue(any(SystemAlertVO.class), eq(rule), eq(oldSample.labels()));
    }

    @Test
    void keepsActiveFingerprintWhenItAppearsInSuccessfulCollectionScopeTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule = rule("local", "orders", 1);
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule));
        MetricSample current = sample("orders");
        AlertStateKey key = new AlertStateKey(rule.getId(),
                AlertFingerprint.of(rule.getId(), current.instanceId(), current.labels()));
        AlertRuleState firing = new AlertRuleState(AlertStateStatus.FIRING, 1, 20D,
                current.collectedAt().minusSeconds(60), current.collectedAt().minusSeconds(60),
                current.collectedAt().minusSeconds(60), null);
        ActiveAlertState active = new ActiveAlertState(key, firing, current.instanceId(), current.labels());
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.find(key)).thenReturn(Optional.of(firing));
        when(states.save(eq(key), any(AlertRuleState.class))).thenReturn(true);
        when(states.findActive(any(MetricCollectionScope.class), eq(List.of(rule)))).thenReturn(List.of(active));
        AlertRepository alerts = mock(AlertRepository.class);

        NativeAlertProcessor processor = new NativeAlertProcessor(service,
                new NativeAlertEvaluationService(new AlertRuleEvaluator(), new AlertStateMachine(), states,
                        mock(MetricSnapshotRepository.class), alerts, mock(NotificationOutboxService.class),
                        suppression()),
                new AlertStateMachine(), states, alerts, mock(NotificationOutboxService.class), suppression(),
                mockTxManager());
        processor.processSuccessfulCollection(new MetricCollectionScope(AlertDomain.BUSINESS, "local",
                        java.util.Set.of("consumer.lag.total")), List.of(current));

        verify(alerts, never()).saveAlert(any(SystemAlertVO.class));
    }

    @Test
    void brokerScopedUnavailableSampleKeepsActiveFingerprintTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule = AlertRuleVO.builder().id(1L).domain(AlertDomain.CLUSTER).name("Broker disk")
                .metric("broker.disk.usage_ratio").operator(">").threshold(0.8).enabled(true)
                .instanceId("local").brokerName("broker-a").consecutiveSamples(1).build();
        when(service.listRules(AlertDomain.CLUSTER)).thenReturn(List.of(rule));
        Map<String, String> labels = Map.of("brokerName", "broker-a", "brokerAddr", "broker-a:10911");
        MetricSample unavailable = new MetricSample("broker.disk.usage_ratio", AlertDomain.CLUSTER, "local",
                "cluster-a", labels, null, MetricAvailability.UNAVAILABLE, Instant.now());
        AlertStateKey key = new AlertStateKey(rule.getId(), AlertFingerprint.of(rule.getId(), "local", labels));
        AlertRuleState firing = new AlertRuleState(AlertStateStatus.FIRING, 1, 0.9D,
                unavailable.collectedAt().minusSeconds(60), unavailable.collectedAt().minusSeconds(60),
                unavailable.collectedAt().minusSeconds(60), null);
        ActiveAlertState active = new ActiveAlertState(key, firing, "local", labels);
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.find(key)).thenReturn(Optional.of(firing));
        when(states.save(eq(key), any(AlertRuleState.class))).thenReturn(true);
        when(states.findActive(any(MetricCollectionScope.class), eq(List.of(rule)))).thenReturn(List.of(active));
        AlertRepository alerts = mock(AlertRepository.class);
        NotificationOutboxService outbox = mock(NotificationOutboxService.class);

        NativeAlertProcessor processor = new NativeAlertProcessor(service,
                new NativeAlertEvaluationService(new AlertRuleEvaluator(), new AlertStateMachine(), states,
                        mock(MetricSnapshotRepository.class), alerts, outbox, suppression()),
                new AlertStateMachine(), states, alerts, outbox, suppression(), mockTxManager());
        processor.processSuccessfulCollection(new MetricCollectionScope(AlertDomain.CLUSTER, "local",
                java.util.Set.of("broker.disk.usage_ratio")), List.of(unavailable));

        verify(alerts, never()).saveAlert(any(SystemAlertVO.class));
        verify(outbox, never()).enqueue(any(), any(), any());
    }

    @Test
    void resolvesMissingMetricEvenWhenAnotherMetricSharesTheSameLabelsTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule = AlertRuleVO.builder().id(1L).domain(AlertDomain.BUSINESS).name("Orders delay")
                .metric("consumer.delay.seconds").operator(">").threshold(10).enabled(true)
                .instanceId("local").consumerGroup("orders").consecutiveSamples(1).build();
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule));
        MetricSample previousDelay = new MetricSample("consumer.delay.seconds", AlertDomain.BUSINESS, "local",
                null, Map.of("consumerGroup", "orders"), 120D, MetricAvailability.AVAILABLE, Instant.now());
        AlertStateKey key = new AlertStateKey(rule.getId(),
                AlertFingerprint.of(rule.getId(), previousDelay.instanceId(), previousDelay.labels()));
        ActiveAlertState active = new ActiveAlertState(key,
                new AlertRuleState(AlertStateStatus.FIRING, 1, 120D, previousDelay.collectedAt().minusSeconds(60),
                        previousDelay.collectedAt().minusSeconds(60), previousDelay.collectedAt().minusSeconds(60),
                        null),
                previousDelay.instanceId(), previousDelay.labels());
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.findActive(any(MetricCollectionScope.class), eq(List.of(rule)))).thenReturn(List.of(active));
        when(states.save(eq(key), any(AlertRuleState.class))).thenReturn(true);
        AlertRepository alerts = mock(AlertRepository.class);
        when(alerts.saveAlert(any(SystemAlertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NotificationOutboxService outbox = mock(NotificationOutboxService.class);

        MetricSample lagSample = new MetricSample("consumer.lag.total", AlertDomain.BUSINESS, "local",
                null, Map.of("consumerGroup", "orders"), 5D, MetricAvailability.AVAILABLE, Instant.now());

        new NativeAlertProcessor(service,
                new NativeAlertEvaluationService(new AlertRuleEvaluator(), new AlertStateMachine(), states,
                        mock(MetricSnapshotRepository.class), alerts, outbox, suppression()),
                new AlertStateMachine(), states, alerts, outbox, suppression(), mockTxManager())
                .processSuccessfulCollection(new MetricCollectionScope(AlertDomain.BUSINESS, "local",
                        java.util.Set.of("consumer.delay.seconds", "consumer.lag.total")), List.of(lagSample));

        org.mockito.ArgumentCaptor<AlertRuleState> state = org.mockito.ArgumentCaptor.forClass(AlertRuleState.class);
        org.mockito.ArgumentCaptor<SystemAlertVO> event = org.mockito.ArgumentCaptor.forClass(SystemAlertVO.class);
        verify(states).save(eq(key), state.capture());
        assertThat(state.getValue().status()).isEqualTo(AlertStateStatus.RESOLVED);
        verify(alerts).saveAlert(event.capture());
        assertThat(event.getValue().getTransition()).isEqualTo(AlertStateTransition.RESOLVED.name());
        assertThat(event.getValue().getLabels()).isEqualTo(previousDelay.labels());
        verify(outbox).enqueue(any(SystemAlertVO.class), eq(rule), eq(previousDelay.labels()));
    }

    @Test
    void doesNotResolveMissingActiveStateWhenCollectionReportsWholeScopeUnavailableTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule = rule("local", "orders", 1);
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule));
        MetricSample oldSample = sample("orders");
        AlertStateKey oldKey = new AlertStateKey(rule.getId(),
                AlertFingerprint.of(rule.getId(), oldSample.instanceId(), oldSample.labels()));
        ActiveAlertState active = new ActiveAlertState(oldKey,
                new AlertRuleState(AlertStateStatus.FIRING, 1, 20D, oldSample.collectedAt().minusSeconds(60),
                        oldSample.collectedAt().minusSeconds(60), oldSample.collectedAt().minusSeconds(60), null),
                oldSample.instanceId(), oldSample.labels());
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.findActive(any(MetricCollectionScope.class), eq(List.of(rule)))).thenReturn(List.of(active));
        AlertRepository alerts = mock(AlertRepository.class);

        NativeAlertProcessor processor = new NativeAlertProcessor(service,
                new NativeAlertEvaluationService(new AlertRuleEvaluator(), new AlertStateMachine(), states,
                        mock(MetricSnapshotRepository.class), alerts, mock(NotificationOutboxService.class),
                        suppression()),
                new AlertStateMachine(), states, alerts, mock(NotificationOutboxService.class), suppression(),
                mockTxManager());
        processor.processSuccessfulCollection(new MetricCollectionScope(AlertDomain.BUSINESS, "local",
                        java.util.Set.of("consumer.lag.total")), List.of(new MetricSample("consumer.lag.total",
                        AlertDomain.BUSINESS, "local", null, Map.of(), null, MetricAvailability.UNAVAILABLE,
                        Instant.now(), "BUSINESS_METRICS_COLLECTION_FAILED")));

        verify(states, never()).save(eq(oldKey), any(AlertRuleState.class));
        verify(alerts, never()).saveAlert(any(SystemAlertVO.class));
    }

    @Test
    void doesNotResolveTopicAlertWhenItsConsumerGroupProgressIsUnavailableTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule = rule("local", "orders", 1);
        rule.setMetric("topic.backlog.total");
        rule.setTopic("orders-topic");
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule));
        Map<String, String> labels = Map.of("consumerGroup", "orders", "topic", "orders-topic");
        AlertStateKey key = new AlertStateKey(rule.getId(), AlertFingerprint.of(rule.getId(), "local", labels));
        Instant collectedAt = Instant.now();
        AlertRuleState firing = new AlertRuleState(AlertStateStatus.FIRING, 1, 20D,
                collectedAt.minusSeconds(60), collectedAt.minusSeconds(60),
                collectedAt.minusSeconds(60), null);
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.findActive(any(MetricCollectionScope.class), eq(List.of(rule))))
                .thenReturn(List.of(new ActiveAlertState(key, firing, "local", labels)));
        AlertRepository alerts = mock(AlertRepository.class);

        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        InstanceProvider provider = mock(InstanceProvider.class);
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("orders");
        group.setClusterId("cluster-a");
        group.setTotalLag(20);
        group.setConsumeStatsAvailable(true);
        when(registry.byInstanceId("local")).thenReturn(Optional.of(provider));
        when(provider.listConsumerGroups("local", null)).thenReturn(List.of(group));
        when(provider.getGroupProgress("local", "orders"))
                .thenThrow(new IllegalStateException("broker progress unavailable"));
        ApacheRocketMqBusinessMetricsCollector collector = new ApacheRocketMqBusinessMetricsCollector(registry);
        List<MetricSample> samples = collector.collect(InstanceVO.builder().name("local")
                .vendor(InstanceVendor.APACHE).build());
        assertThat(samples).filteredOn(sample -> sample.metricKey().equals("topic.backlog.total"))
                .singleElement().satisfies(sample -> {
                    assertThat(sample.availability()).isEqualTo(MetricAvailability.UNAVAILABLE);
                    assertThat(sample.labels()).isEqualTo(Map.of("consumerGroup", "orders"));
                });
        processor(service, states, alerts).processSuccessfulCollection(
                new MetricCollectionScope(AlertDomain.BUSINESS, "local", collector.metricKeys()), samples);

        verify(states, never()).save(eq(key), any(AlertRuleState.class));
        verify(alerts, never()).saveAlert(any(SystemAlertVO.class));
    }

    @Test
    void resolvesPreservedTopicAlertAfterTheNextSuccessfulGroupCollectionTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule = rule("local", "orders", 1);
        rule.setMetric("topic.backlog.total");
        rule.setTopic("orders-topic");
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule));

        Map<String, String> labels = Map.of("consumerGroup", "orders", "topic", "orders-topic");
        AlertStateKey key = new AlertStateKey(rule.getId(), AlertFingerprint.of(rule.getId(), "local", labels));
        Instant firstCollection = Instant.parse("2026-09-01T14:00:00Z");
        AlertRuleState firing = new AlertRuleState(AlertStateStatus.FIRING, 1, 20D,
                firstCollection.minusSeconds(60), firstCollection.minusSeconds(60),
                firstCollection.minusSeconds(60), null);
        AtomicReference<AlertRuleState> currentState = new AtomicReference<>(firing);
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.findActive(any(MetricCollectionScope.class), eq(List.of(rule))))
                .thenAnswer(invocation -> currentState.get().status() == AlertStateStatus.FIRING
                        ? List.of(new ActiveAlertState(key, currentState.get(), "local", labels)) : List.of());
        when(states.save(eq(key), any(AlertRuleState.class))).thenAnswer(invocation -> {
            currentState.set(invocation.getArgument(1));
            return true;
        });
        AlertRepository alerts = mock(AlertRepository.class);
        when(alerts.saveAlert(any(SystemAlertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NotificationOutboxService outbox = mock(NotificationOutboxService.class);
        NativeAlertProcessor processor = processor(service, states, mock(MetricSnapshotRepository.class), alerts,
                outbox, suppression());
        MetricCollectionScope scope = new MetricCollectionScope(AlertDomain.BUSINESS, "local",
                java.util.Set.of("topic.backlog.total"));

        processor.processSuccessfulCollection(scope, List.of(new MetricSample("topic.backlog.total",
                AlertDomain.BUSINESS, "local", "cluster-a", Map.of("consumerGroup", "orders"), null,
                MetricAvailability.UNAVAILABLE, firstCollection, "CONSUMER_PROGRESS_UNAVAILABLE")));
        assertThat(currentState.get().status()).isEqualTo(AlertStateStatus.FIRING);
        verify(alerts, never()).saveAlert(any(SystemAlertVO.class));

        processor.processSuccessfulCollection(scope, List.of(new MetricSample("topic.backlog.total",
                AlertDomain.BUSINESS, "local", "cluster-a",
                Map.of("consumerGroup", "orders", "topic", "another-topic"), 0D,
                MetricAvailability.AVAILABLE, firstCollection.plusSeconds(30))));

        assertThat(currentState.get().status()).isEqualTo(AlertStateStatus.RESOLVED);
        verify(alerts).saveAlert(org.mockito.ArgumentMatchers.argThat(event ->
                event.getTransition().equals(AlertStateTransition.RESOLVED.name())
                        && event.getLabels().equals(labels)));
        verify(outbox).enqueue(any(SystemAlertVO.class), eq(rule), eq(labels));
    }

    @Test
    void stillResolvesMissingTopicsInOtherGroupsAfterOneGroupProgressFailsTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule = rule("local", "orders", 1);
        rule.setMetric("topic.backlog.total");
        rule.setConsumerGroup(null);
        rule.setTopic("orders-topic");
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule));
        Map<String, String> failedLabels = Map.of("consumerGroup", "orders", "topic", "orders-topic");
        Map<String, String> missingLabels = Map.of("consumerGroup", "billing", "topic", "orders-topic");
        AlertStateKey failedKey = new AlertStateKey(rule.getId(), AlertFingerprint.of(rule.getId(), "local", failedLabels));
        AlertStateKey missingKey = new AlertStateKey(rule.getId(), AlertFingerprint.of(rule.getId(), "local", missingLabels));
        Instant collectedAt = Instant.now();
        AlertRuleState firing = new AlertRuleState(AlertStateStatus.FIRING, 1, 20D,
                collectedAt.minusSeconds(60), collectedAt.minusSeconds(60),
                collectedAt.minusSeconds(60), null);
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.findActive(any(MetricCollectionScope.class), eq(List.of(rule))))
                .thenReturn(List.of(new ActiveAlertState(failedKey, firing, "local", failedLabels),
                        new ActiveAlertState(missingKey, firing, "local", missingLabels)));
        when(states.save(eq(missingKey), any(AlertRuleState.class))).thenReturn(true);
        AlertRepository alerts = mock(AlertRepository.class);
        when(alerts.saveAlert(any(SystemAlertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MetricSample unavailable = new MetricSample("topic.backlog.total", AlertDomain.BUSINESS,
                "local", "cluster-a", Map.of("consumerGroup", "orders"), null,
                MetricAvailability.UNAVAILABLE, collectedAt, "CONSUMER_PROGRESS_UNAVAILABLE");
        processor(service, states, alerts).processSuccessfulCollection(
                new MetricCollectionScope(AlertDomain.BUSINESS, "local", java.util.Set.of("topic.backlog.total")),
                List.of(unavailable));

        verify(states, never()).save(eq(failedKey), any(AlertRuleState.class));
        verify(states).save(eq(missingKey), any(AlertRuleState.class));
        verify(alerts).saveAlert(org.mockito.ArgumentMatchers.argThat(event ->
                event.getTransition().equals(AlertStateTransition.RESOLVED.name())
                        && event.getLabels().equals(missingLabels)));
    }

    @Test
    void singleLifecycleEmitFailureDoesNotRollBackTheBatchTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule1 = rule(1L, "local", "orders", 1);
        AlertRuleVO rule2 = rule(2L, "local", "payments", 1);
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule1, rule2));
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.save(any(AlertStateKey.class), any(AlertRuleState.class))).thenReturn(true);
        MetricSample ordersSample = sample("orders");
        MetricSample paymentsSample = sample("payments");
        ActiveAlertState active1 = new ActiveAlertState(
                new AlertStateKey(1L, AlertFingerprint.of(1L, "local", ordersSample.labels())),
                new AlertRuleState(AlertStateStatus.FIRING, 1, 20D, Instant.now().minusSeconds(60),
                        Instant.now().minusSeconds(60), Instant.now().minusSeconds(60), null),
                "local", ordersSample.labels());
        ActiveAlertState active2 = new ActiveAlertState(
                new AlertStateKey(2L, AlertFingerprint.of(2L, "local", paymentsSample.labels())),
                new AlertRuleState(AlertStateStatus.FIRING, 1, 20D, Instant.now().minusSeconds(60),
                        Instant.now().minusSeconds(60), Instant.now().minusSeconds(60), null),
                "local", paymentsSample.labels());
        when(states.findActive(any(MetricCollectionScope.class), any())).thenReturn(List.of(active1, active2));
        AlertRepository alerts = mock(AlertRepository.class);
        when(alerts.saveAlert(any(SystemAlertVO.class)))
                .thenThrow(new IllegalStateException("db write failed"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        NotificationOutboxService outbox = mock(NotificationOutboxService.class);

        NativeAlertProcessor processor = new NativeAlertProcessor(service,
                new NativeAlertEvaluationService(new AlertRuleEvaluator(), new AlertStateMachine(), states,
                        mock(MetricSnapshotRepository.class), alerts, outbox, suppression()),
                new AlertStateMachine(), states, alerts, outbox, suppression(), mockTxManager());
        assertThatCode(() -> processor.processSuccessfulCollection(
                new MetricCollectionScope(AlertDomain.BUSINESS, "local",
                        java.util.Set.of("consumer.lag.total")),
                List.of())).doesNotThrowAnyException();

        verify(outbox).enqueue(any(SystemAlertVO.class), eq(rule2), anyMap());
    }

    @Test
    void emitFailureRollsBackBothSaveAndEmitPreventingOrphanEventsTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule = rule("local", "orders", 1);
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule));
        MetricSample oldSample = sample("orders");
        AlertStateKey oldKey = new AlertStateKey(rule.getId(),
                AlertFingerprint.of(rule.getId(), oldSample.instanceId(), oldSample.labels()));
        ActiveAlertState active = new ActiveAlertState(oldKey,
                new AlertRuleState(AlertStateStatus.FIRING, 1, 20D, oldSample.collectedAt().minusSeconds(60),
                        oldSample.collectedAt().minusSeconds(60), oldSample.collectedAt().minusSeconds(60), null),
                oldSample.instanceId(), oldSample.labels());
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.findActive(any(MetricCollectionScope.class), eq(List.of(rule)))).thenReturn(List.of(active));
        when(states.save(eq(oldKey), any(AlertRuleState.class))).thenReturn(true);
        AlertRepository alerts = mock(AlertRepository.class);
        when(alerts.saveAlert(any(SystemAlertVO.class)))
                .thenThrow(new IllegalStateException("event persist failed"));
        NotificationOutboxService outbox = mock(NotificationOutboxService.class);
        PlatformTransactionManager txManager = mockTxManager();

        NativeAlertProcessor processor = new NativeAlertProcessor(service,
                new NativeAlertEvaluationService(new AlertRuleEvaluator(), new AlertStateMachine(), states,
                        mock(MetricSnapshotRepository.class), alerts, outbox, suppression()),
                new AlertStateMachine(), states, alerts, outbox, suppression(), txManager);
        assertThatCode(() -> processor.processSuccessfulCollection(
                new MetricCollectionScope(AlertDomain.BUSINESS, "local",
                        java.util.Set.of("consumer.lag.total")),
                List.of())).doesNotThrowAnyException();

        verify(states).save(eq(oldKey), any(AlertRuleState.class));
        verify(alerts).saveAlert(any(SystemAlertVO.class));
        verify(outbox, never()).enqueue(any(), any(), any());
        verify(txManager).rollback(any(TransactionStatus.class));
    }

    @Test
    void doesNotEmitResolvedEventWhenStateSaveLosesTheOptimisticRaceTest() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule = rule("local", "orders", 1);
        when(service.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule));
        MetricSample oldSample = sample("orders");
        AlertStateKey oldKey = new AlertStateKey(rule.getId(),
                AlertFingerprint.of(rule.getId(), oldSample.instanceId(), oldSample.labels()));
        ActiveAlertState active = new ActiveAlertState(oldKey,
                new AlertRuleState(AlertStateStatus.FIRING, 1, 20D, oldSample.collectedAt().minusSeconds(60),
                        oldSample.collectedAt().minusSeconds(60), oldSample.collectedAt().minusSeconds(60), null),
                oldSample.instanceId(), oldSample.labels());
        AlertStateRepository states = mock(AlertStateRepository.class);
        when(states.findActive(any(MetricCollectionScope.class), eq(List.of(rule)))).thenReturn(List.of(active));
        // a concurrent ACK already advanced the state, so this writer's save loses the
        // optimistic race and returns false
        when(states.save(eq(oldKey), any(AlertRuleState.class))).thenReturn(false);
        AlertRepository alerts = mock(AlertRepository.class);
        NotificationOutboxService outbox = mock(NotificationOutboxService.class);

        NativeAlertProcessor processor = new NativeAlertProcessor(service,
                new NativeAlertEvaluationService(new AlertRuleEvaluator(), new AlertStateMachine(), states,
                        mock(MetricSnapshotRepository.class), alerts, outbox, suppression()),
                new AlertStateMachine(), states, alerts, outbox, suppression(), mockTxManager());
        assertThatCode(() -> processor.processSuccessfulCollection(
                new MetricCollectionScope(AlertDomain.BUSINESS, "local",
                        java.util.Set.of("consumer.lag.total")),
                List.of())).doesNotThrowAnyException();

        verify(states).save(eq(oldKey), any(AlertRuleState.class));
        verify(alerts, never()).saveAlert(any(SystemAlertVO.class));
        verify(outbox, never()).enqueue(any(), any(), any());
    }

    private static PlatformTransactionManager mockTxManager() {
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(txManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        return txManager;
    }

    private static AlertNotificationSuppressionService suppression() {
        AlertNotificationSuppressionService service = mock(AlertNotificationSuppressionService.class);
        when(service.findSuppressingClusterAlert(any(SystemAlertVO.class))).thenReturn(Optional.empty());
        return service;
    }

    private static AlertRuleVO rule(String instanceId, String group, int consecutiveSamples) {
        return rule(1L, instanceId, group, consecutiveSamples);
    }

    private static AlertRuleVO rule(Long id, String instanceId, String group, int consecutiveSamples) {
        return AlertRuleVO.builder().id(id).domain(AlertDomain.BUSINESS).name("Orders lag")
                .metric("consumer.lag.total").operator(">").threshold(10).enabled(true)
                .instanceId(instanceId).consumerGroup(group).consecutiveSamples(consecutiveSamples).build();
    }

    private static MetricSample sample(String group) {
        return sample(group, 20D);
    }

    private static MetricSample sample(String group, double value) {
        return new MetricSample("consumer.lag.total", AlertDomain.BUSINESS, "local", null,
                Map.of("consumerGroup", group), value, MetricAvailability.AVAILABLE, Instant.now());
    }
}
