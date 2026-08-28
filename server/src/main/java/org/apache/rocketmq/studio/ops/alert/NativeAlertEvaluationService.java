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

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.cluster.metrics.MetricSnapshotRepository;
import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Evaluates one rule against one native sample in an independent transaction. A failure rolls
 * back this evaluation's state, event, and outbox changes without invalidating other evaluations
 * from the same collection batch.
 */
@Service
@RequiredArgsConstructor
public class NativeAlertEvaluationService {
    private final AlertRuleEvaluator evaluator;
    private final AlertStateMachine stateMachine;
    private final AlertStateRepository stateRepository;
    private final MetricSnapshotRepository snapshotRepository;
    private final AlertRepository alertRepository;
    private final NotificationOutboxService notificationOutboxService;
    private final AlertNotificationSuppressionService notificationSuppressionService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void evaluate(AlertRuleVO rule, MetricSample sample) {
        MetricSample evaluatedSample = aggregate(rule, sample);
        AlertEvaluationResult evaluation = evaluator.evaluate(rule, evaluatedSample);
        if (!evaluation.matches()) {
            return;
        }

        AlertStateKey key = new AlertStateKey(rule.getId(),
                AlertFingerprint.of(rule.getId(), sample.instanceId(), sample.labels()));
        AlertStateUpdate update = stateMachine.advance(stateRepository.find(key).orElse(null), evaluation,
                Math.max(1, rule.getConsecutiveSamples()), AlertRuleDuration.parse(rule.getDuration()),
                AlertRuleDuration.parse(rule.getReminderInterval()), sample.collectedAt());
        if (!stateRepository.save(key, update.state()) || !emitsLifecycleEvent(update.transition())) {
            return;
        }

        LocalDateTime eventTime = LocalDateTime.ofInstant(sample.collectedAt(), ZoneOffset.UTC);
        SystemAlertVO event = SystemAlertVO.builder()
                .level(level(rule.getSeverity()))
                .title(rule.getName())
                .description(update.transition() + " " + sample.metricKey() + " on " + sample.instanceId())
                .time(eventTime)
                .acknowledged(false)
                .domain(sample.domain())
                .ruleId(rule.getId())
                .fingerprint(key.fingerprint())
                .transition(update.transition().name())
                .instanceId(sample.instanceId())
                .currentValue(update.state().currentValue())
                .labels(Map.copyOf(new TreeMap<>(sample.labels())))
                .build();
        applyNotificationSuppression(event, sample.domain(), update.transition());

        SystemAlertVO savedEvent = alertRepository.saveAlert(event);
        if (savedEvent != null) {
            event = savedEvent;
        }
        if (update.transition() == AlertStateTransition.FIRING) {
            alertRepository.markRuleTriggered(rule.getId(), eventTime.toString());
        }
        if (!event.isNotificationSuppressed()) {
            notificationOutboxService.enqueue(event, rule, sample.labels());
        }
    }

    private MetricSample aggregate(AlertRuleVO rule, MetricSample sample) {
        if (sample.availability() != MetricAvailability.AVAILABLE || rule.getWindowSeconds() <= 0) {
            return sample;
        }
        List<MetricSample> window = snapshotRepository.findRecent(sample,
                sample.collectedAt().minus(Duration.ofSeconds(rule.getWindowSeconds())));
        if (window.isEmpty()) {
            return sample;
        }
        double value = switch (rule.getAggregation() == null ? "LAST" : rule.getAggregation().toUpperCase(Locale.ROOT)) {
            case "MAX" -> window.stream().mapToDouble(item -> item.value()).max().orElse(sample.value());
            case "MIN" -> window.stream().mapToDouble(item -> item.value()).min().orElse(sample.value());
            case "AVG" -> window.stream().mapToDouble(item -> item.value()).average().orElse(sample.value());
            case "SUM" -> window.stream().mapToDouble(item -> item.value()).sum();
            default -> window.get(window.size() - 1).value();
        };
        return new MetricSample(sample.metricKey(), sample.domain(), sample.instanceId(), sample.clusterId(),
                sample.labels(), value, MetricAvailability.AVAILABLE, sample.collectedAt());
    }

    private void applyNotificationSuppression(SystemAlertVO event, AlertDomain domain,
            AlertStateTransition transition) {
        if (!shouldSuppress(domain, transition)) {
            return;
        }
        Optional<SystemAlertVO> cause = notificationSuppressionService.findSuppressingClusterAlert(event);
        if (cause.isEmpty()) {
            return;
        }
        SystemAlertVO suppressingAlert = cause.get();
        event.setNotificationSuppressed(true);
        event.setSuppressionCauseAlertId(suppressingAlert.getId());
        event.setSuppressionReason("Suppressed by active cluster incident #" + suppressingAlert.getId()
                + ": " + suppressingAlert.getTitle());
    }

    private static boolean emitsLifecycleEvent(AlertStateTransition transition) {
        return transition == AlertStateTransition.FIRING
                || transition == AlertStateTransition.REMINDER
                || transition == AlertStateTransition.RESOLVED;
    }

    private static boolean shouldSuppress(AlertDomain domain, AlertStateTransition transition) {
        return domain == AlertDomain.BUSINESS
                && (transition == AlertStateTransition.FIRING || transition == AlertStateTransition.REMINDER);
    }

    private static AlertLevel level(String severity) {
        if ("critical".equalsIgnoreCase(severity)) {
            return AlertLevel.error;
        }
        if ("warning".equalsIgnoreCase(severity)) {
            return AlertLevel.warning;
        }
        return AlertLevel.info;
    }
}
