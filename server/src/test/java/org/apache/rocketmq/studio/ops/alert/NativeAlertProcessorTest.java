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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
                mock(NotificationOutboxService.class), suppression());

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
                suppression);
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

        new NativeAlertProcessor(service,
                new NativeAlertEvaluationService(new AlertRuleEvaluator(), new AlertStateMachine(), states,
                        mock(MetricSnapshotRepository.class), alerts, outbox, suppression()),
                new AlertStateMachine(), states, alerts, outbox, suppression())
                .processSuccessfulCollection(new MetricCollectionScope(AlertDomain.BUSINESS, "local",
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

        new NativeAlertProcessor(service,
                new NativeAlertEvaluationService(new AlertRuleEvaluator(), new AlertStateMachine(), states,
                        mock(MetricSnapshotRepository.class), alerts, mock(NotificationOutboxService.class),
                        suppression()),
                new AlertStateMachine(), states, alerts, mock(NotificationOutboxService.class), suppression())
                .processSuccessfulCollection(new MetricCollectionScope(AlertDomain.BUSINESS, "local",
                        java.util.Set.of("consumer.lag.total")), List.of(current));

        verify(alerts, never()).saveAlert(any(SystemAlertVO.class));
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
                new AlertStateMachine(), states, alerts, outbox, suppression())
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

        new NativeAlertProcessor(service,
                new NativeAlertEvaluationService(new AlertRuleEvaluator(), new AlertStateMachine(), states,
                        mock(MetricSnapshotRepository.class), alerts, mock(NotificationOutboxService.class),
                        suppression()),
                new AlertStateMachine(), states, alerts, mock(NotificationOutboxService.class), suppression())
                .processSuccessfulCollection(new MetricCollectionScope(AlertDomain.BUSINESS, "local",
                        java.util.Set.of("consumer.lag.total")), List.of(new MetricSample("consumer.lag.total",
                        AlertDomain.BUSINESS, "local", null, Map.of(), null, MetricAvailability.UNAVAILABLE,
                        Instant.now(), "BUSINESS_METRICS_COLLECTION_FAILED")));

        verify(states, never()).save(eq(oldKey), any(AlertRuleState.class));
        verify(alerts, never()).saveAlert(any(SystemAlertVO.class));
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
