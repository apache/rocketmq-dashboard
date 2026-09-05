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
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.cluster.metrics.MetricSnapshotRepository;
import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NativeAlertEvaluationService}, the per-evaluation transaction that
 * aggregates a native sample, advances the alert state, persists the lifecycle event, and
 * enqueues notifications (subject to cluster-incident suppression).
 */
@ExtendWith(MockitoExtension.class)
class NativeAlertEvaluationServiceTest {

    @Mock
    private AlertRuleEvaluator evaluator;

    @Mock
    private AlertStateMachine stateMachine;

    @Mock
    private AlertStateRepository stateRepository;

    @Mock
    private MetricSnapshotRepository snapshotRepository;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private NotificationOutboxService notificationOutboxService;

    @Mock
    private AlertNotificationSuppressionService notificationSuppressionService;

    @InjectMocks
    private NativeAlertEvaluationService service;

    private static final Instant COLLECTED_AT = Instant.parse("2026-07-01T10:00:00Z");
    private static final AlertEvaluationResult MATCH = new AlertEvaluationResult(
            true, true, 90.0, MetricAvailability.AVAILABLE);
    private static final AlertEvaluationResult NO_MATCH = new AlertEvaluationResult(
            false, false, 90.0, MetricAvailability.AVAILABLE);

    private static MetricSample availableSample(double value) {
        return new MetricSample("cpu", AlertDomain.BUSINESS, "instance-1", "cluster-1",
                Map.of("brokerName", "broker-1"), value, MetricAvailability.AVAILABLE, COLLECTED_AT);
    }

    private static AlertRuleVO rule(String severity, AlertDomain domain) {
        return AlertRuleVO.builder()
                .id(1L)
                .domain(domain)
                .name("CPU High")
                .severity(severity)
                .duration("1m")
                .reminderInterval("1m")
                .consecutiveSamples(1)
                .windowSeconds(0)
                .aggregation("LAST")
                .build();
    }

    private AlertRuleState firingState() {
        return new AlertRuleState(AlertStateStatus.FIRING, 1, 90.0,
                COLLECTED_AT, COLLECTED_AT, null, null);
    }

    /** Runs the full firing flow with the shared mocks and returns the persisted event. */
    private SystemAlertVO runFiring(String severity) {
        when(evaluator.evaluate(any(AlertRuleVO.class), any(MetricSample.class))).thenReturn(MATCH);
        when(stateRepository.find(any(AlertStateKey.class))).thenReturn(Optional.empty());
        when(stateRepository.save(any(AlertStateKey.class), any(AlertRuleState.class))).thenReturn(true);
        when(stateMachine.advance(any(), any(), anyInt(), any(Duration.class),
                any(Duration.class), any(Instant.class)))
                .thenReturn(new AlertStateUpdate(firingState(), AlertStateTransition.FIRING));
        when(alertRepository.saveAlert(any(SystemAlertVO.class))).thenAnswer(invocation -> {
            SystemAlertVO event = invocation.getArgument(0);
            event.setId(42L);
            return event;
        });
        when(notificationSuppressionService.findSuppressingClusterAlert(any(SystemAlertVO.class)))
                .thenReturn(Optional.empty());

        service.evaluate(rule(severity, AlertDomain.BUSINESS), availableSample(90.0));

        ArgumentCaptor<SystemAlertVO> event = ArgumentCaptor.forClass(SystemAlertVO.class);
        verify(alertRepository, atLeastOnce()).saveAlert(event.capture());
        return event.getValue();
    }

    @Test
    void returnsWithoutSideEffectsWhenTheRuleDoesNotMatch() {
        when(evaluator.evaluate(any(AlertRuleVO.class), any(MetricSample.class))).thenReturn(NO_MATCH);

        service.evaluate(rule("critical", AlertDomain.BUSINESS), availableSample(90.0));

        verify(evaluator).evaluate(eq(rule("critical", AlertDomain.BUSINESS)), any(MetricSample.class));
        verifyNoInteractions(stateMachine, stateRepository, snapshotRepository,
                alertRepository, notificationOutboxService, notificationSuppressionService);
    }

    @Test
    void aggregatesTheSampleWindowPerRuleAggregation() {
        for (String aggregation : new String[] {"MAX", "MIN", "AVG", "SUM", "LAST", "max", null}) {
            MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);
            AlertRuleEvaluator ruleEvaluator = mock(AlertRuleEvaluator.class);
            MetricSample original = availableSample(90.0);
            List<MetricSample> window = List.of(
                    sampleValue(1.0), sampleValue(5.0), sampleValue(3.0));
            when(snapshots.findRecent(any(MetricSample.class), any(Instant.class))).thenReturn(window);
            when(ruleEvaluator.evaluate(any(AlertRuleVO.class), any(MetricSample.class)))
                    .thenReturn(NO_MATCH);
            NativeAlertEvaluationService service = new NativeAlertEvaluationService(ruleEvaluator,
                    mock(AlertStateMachine.class), mock(AlertStateRepository.class), snapshots,
                    mock(AlertRepository.class), mock(NotificationOutboxService.class),
                    mock(AlertNotificationSuppressionService.class));
            AlertRuleVO rule = rule("critical", AlertDomain.BUSINESS);
            rule.setWindowSeconds(30);
            rule.setAggregation(aggregation);

            service.evaluate(rule, original);

            ArgumentCaptor<MetricSample> aggregated = ArgumentCaptor.forClass(MetricSample.class);
            ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
            verify(snapshots).findRecent(eq(original), since.capture());
            assertThat(since.getValue()).isEqualTo(COLLECTED_AT.minus(Duration.ofSeconds(30)));
            verify(ruleEvaluator).evaluate(eq(rule), aggregated.capture());
            double expected = switch (aggregation == null ? "LAST" : aggregation.toUpperCase()) {
                case "MAX" -> 5.0;
                case "MIN" -> 1.0;
                case "AVG" -> 3.0;
                case "SUM" -> 9.0;
                default -> 3.0;
            };
            assertThat(aggregated.getValue().value()).isEqualTo(expected);
        }
    }

    private static MetricSample sampleValue(double value) {
        return new MetricSample("cpu", AlertDomain.BUSINESS, "instance-1", "cluster-1",
                Map.of(), value, MetricAvailability.AVAILABLE, COLLECTED_AT);
    }

    @Test
    void skipsWindowLookupWhenUnavailableOrWindowless() {
        MetricSample unavailable = new MetricSample("cpu", AlertDomain.BUSINESS, "instance-1",
                "cluster-1", Map.of(), null, MetricAvailability.UNAVAILABLE, COLLECTED_AT);
        when(evaluator.evaluate(any(AlertRuleVO.class), any(MetricSample.class))).thenReturn(NO_MATCH);
        AlertRuleVO windowless = rule("critical", AlertDomain.BUSINESS);

        service.evaluate(windowless, availableSample(90.0));
        service.evaluate(windowless, unavailable);

        verifyNoInteractions(snapshotRepository);
        verify(evaluator, org.mockito.Mockito.times(2))
                .evaluate(any(AlertRuleVO.class), any(MetricSample.class));
    }

    @Test
    void persistsAFiringEventWithSeverityLevelMapping() {
        SystemAlertVO event = runFiring("critical");

        assertThat(event.getLevel()).isEqualTo(AlertLevel.error);
        assertThat(event.getTitle()).isEqualTo("CPU High");
        assertThat(event.getDescription()).isEqualTo("FIRING cpu on instance-1");
        assertThat(event.getTransition()).isEqualTo("FIRING");
        assertThat(event.getInstanceId()).isEqualTo("instance-1");
        assertThat(event.getCurrentValue()).isEqualTo(90.0);
        assertThat(event.getDomain()).isEqualTo(AlertDomain.BUSINESS);
        assertThat(event.getRuleId()).isEqualTo(1L);
        assertThat(event.getFingerprint()).isEqualTo(AlertFingerprint.of(
                1L, "instance-1", Map.of("brokerName", "broker-1")));
        assertThat(event.getTime()).isEqualTo(LocalDateTime.ofInstant(COLLECTED_AT, ZoneOffset.UTC));
        assertThat(event.getLabels()).containsEntry("brokerName", "broker-1");
        assertThat(event.getId()).isEqualTo(42L);
        assertThat(event.isNotificationSuppressed()).isFalse();

        verify(alertRepository).markRuleTriggered(1L,
                LocalDateTime.ofInstant(COLLECTED_AT, ZoneOffset.UTC).toString());
        verify(notificationOutboxService).enqueue(event, rule("critical", AlertDomain.BUSINESS),
                Map.of("brokerName", "broker-1"));
    }

    @Test
    void mapsWarningAndUnknownSeverities() {
        assertThat(runFiring("warning").getLevel()).isEqualTo(AlertLevel.warning);
        assertThat(runFiring("high").getLevel()).isEqualTo(AlertLevel.info);
    }

    @Test
    void suppressesBusinessAlertsWhileAClusterIncidentIsActive() {
        when(evaluator.evaluate(any(AlertRuleVO.class), any(MetricSample.class))).thenReturn(MATCH);
        when(stateRepository.find(any(AlertStateKey.class))).thenReturn(Optional.empty());
        when(stateRepository.save(any(AlertStateKey.class), any(AlertRuleState.class))).thenReturn(true);
        when(stateMachine.advance(any(), any(), anyInt(), any(Duration.class),
                any(Duration.class), any(Instant.class)))
                .thenReturn(new AlertStateUpdate(firingState(), AlertStateTransition.FIRING));
        when(alertRepository.saveAlert(any(SystemAlertVO.class))).thenAnswer(invocation -> {
            SystemAlertVO event = invocation.getArgument(0);
            event.setId(42L);
            return event;
        });
        SystemAlertVO incident = SystemAlertVO.builder()
                .id(99L)
                .title("Cluster down")
                .domain(AlertDomain.CLUSTER)
                .build();
        when(notificationSuppressionService.findSuppressingClusterAlert(any(SystemAlertVO.class)))
                .thenReturn(Optional.of(incident));

        service.evaluate(rule("critical", AlertDomain.BUSINESS), availableSample(90.0));

        ArgumentCaptor<SystemAlertVO> event = ArgumentCaptor.forClass(SystemAlertVO.class);
        verify(alertRepository).saveAlert(event.capture());
        assertThat(event.getValue().isNotificationSuppressed()).isTrue();
        assertThat(event.getValue().getSuppressionCauseAlertId()).isEqualTo(99L);
        assertThat(event.getValue().getSuppressionReason())
                .contains("Cluster down");
        verify(notificationOutboxService, never()).enqueue(any(), any(), any());
    }

    @Test
    void neverSuppressesClusterDomainAlerts() {
        when(evaluator.evaluate(any(AlertRuleVO.class), any(MetricSample.class))).thenReturn(MATCH);
        when(stateRepository.find(any(AlertStateKey.class))).thenReturn(Optional.empty());
        when(stateRepository.save(any(AlertStateKey.class), any(AlertRuleState.class))).thenReturn(true);
        when(stateMachine.advance(any(), any(), anyInt(), any(Duration.class),
                any(Duration.class), any(Instant.class)))
                .thenReturn(new AlertStateUpdate(firingState(), AlertStateTransition.FIRING));
        when(alertRepository.saveAlert(any(SystemAlertVO.class))).thenAnswer(invocation -> {
            SystemAlertVO event = invocation.getArgument(0);
            event.setId(42L);
            return event;
        });
        MetricSample clusterSample = new MetricSample("broker_down", AlertDomain.CLUSTER, "instance-1",
                "cluster-1", Map.of("brokerName", "broker-1"), 1.0, MetricAvailability.AVAILABLE,
                COLLECTED_AT);

        service.evaluate(rule("critical", AlertDomain.CLUSTER), clusterSample);

        verifyNoInteractions(notificationSuppressionService);
        verify(notificationOutboxService).enqueue(any(SystemAlertVO.class),
                eq(rule("critical", AlertDomain.CLUSTER)), eq(Map.of("brokerName", "broker-1")));
    }

    @Test
    void skipsPersistenceForNonEmittingTransitions() {
        when(evaluator.evaluate(any(AlertRuleVO.class), any(MetricSample.class))).thenReturn(MATCH);
        when(stateRepository.find(any(AlertStateKey.class))).thenReturn(Optional.empty());
        when(stateRepository.save(any(AlertStateKey.class), any(AlertRuleState.class))).thenReturn(true);
        when(stateMachine.advance(any(), any(), anyInt(), any(Duration.class),
                any(Duration.class), any(Instant.class)))
                .thenReturn(new AlertStateUpdate(firingState(), AlertStateTransition.PENDING));

        service.evaluate(rule("critical", AlertDomain.BUSINESS), availableSample(90.0));

        verify(stateRepository).save(any(AlertStateKey.class), any(AlertRuleState.class));
        verify(alertRepository, never()).saveAlert(any(SystemAlertVO.class));
        verify(notificationOutboxService, never()).enqueue(any(), any(), any());
    }
}
