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
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.cluster.metrics.MetricSnapshotRepository;
import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.MetricCollectionScope;
import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.EnumMap;
import java.util.Objects;
import java.util.stream.Collectors;

/** Applies native samples to persisted rule state and emits only lifecycle transitions. */
@Component
@RequiredArgsConstructor
public class NativeAlertProcessor {
    private final AlertService alertService;
    private final AlertRuleEvaluator evaluator;
    private final AlertStateMachine stateMachine;
    private final AlertStateRepository stateRepository;
    private final MetricSnapshotRepository snapshotRepository;
    private final AlertRepository alertRepository;
    private final NotificationOutboxService notificationOutboxService;
    private final AlertNotificationSuppressionService notificationSuppressionService;

    @Transactional
    public void process(List<MetricSample> samples) {
        processSamples(samples);
    }

    @Transactional
    public void processSuccessfulCollection(MetricCollectionScope scope, List<MetricSample> samples) {
        Objects.requireNonNull(scope, "scope is required");
        List<MetricSample> collected = samples == null ? List.of() : samples;
        processSamples(collected);
        if (containsWholeScopeFailure(scope, collected)) {
            return;
        }
        reconcileMissingActiveStates(scope, collected);
    }

    private static boolean containsWholeScopeFailure(MetricCollectionScope scope, List<MetricSample> samples) {
        return samples.stream().filter(scope::contains)
                .anyMatch(sample -> sample.availability() != MetricAvailability.AVAILABLE
                        && sample.labels().isEmpty());
    }

    private void processSamples(List<MetricSample> samples) {
        Map<AlertDomain, List<AlertRuleVO>> rulesByDomain = new EnumMap<>(AlertDomain.class);
        for (MetricSample sample : samples) {
            for (AlertRuleVO rule : rulesByDomain.computeIfAbsent(sample.domain(), alertService::listRules)) {
                if (rule.getId() == null) {
                    continue;
                }
                if (!rule.isEnabled()) {
                    continue;
                }
                if (!NativeAlertRuleScopeMatcher.matches(rule, sample)) {
                    continue;
                }
                MetricSample evaluatedSample = aggregate(rule, sample);
                AlertEvaluationResult evaluation = evaluator.evaluate(rule, evaluatedSample);
                if (!evaluation.matches()) {
                    continue;
                }
                AlertStateKey key = new AlertStateKey(rule.getId(),
                        AlertFingerprint.of(rule.getId(), sample.instanceId(), sample.labels()));
                AlertStateUpdate update = stateMachine.advance(stateRepository.find(key).orElse(null), evaluation,
                        Math.max(1, rule.getConsecutiveSamples()), AlertRuleDuration.parse(rule.getDuration()),
                        AlertRuleDuration.parse(rule.getReminderInterval()), sample.collectedAt());
                if (!stateRepository.save(key, update.state())) {
                    continue;
                }
                if (update.transition() == AlertStateTransition.FIRING || update.transition() == AlertStateTransition.REMINDER
                        || update.transition() == AlertStateTransition.RESOLVED) {
                    emitLifecycleEvent(rule, key, update, sample.domain(), sample.instanceId(), sample.metricKey(),
                            sample.labels(), sample.collectedAt());
                }
            }
        }
    }

    private void reconcileMissingActiveStates(MetricCollectionScope scope, List<MetricSample> samples) {
        List<AlertRuleVO> rules = alertService.listRules(scope.domain()).stream()
                .filter(rule -> rule.getId() != null)
                .filter(AlertRuleVO::isEnabled)
                .filter(rule -> scope.metricKeys().contains(rule.getMetric()))
                .filter(rule -> rule.getInstanceId() == null || scope.instanceId().equals(rule.getInstanceId()))
                .toList();
        if (rules.isEmpty()) {
            return;
        }
        Set<AlertStateKey> presentKeys = samples.stream()
                .filter(scope::contains)
                .flatMap(sample -> rules.stream()
                        .filter(rule -> NativeAlertRuleScopeMatcher.matches(rule, sample))
                        .map(rule -> new AlertStateKey(rule.getId(),
                                AlertFingerprint.of(rule.getId(), sample.instanceId(), sample.labels()))))
                .collect(Collectors.toSet());
        Map<Long, AlertRuleVO> byId = rules.stream().collect(Collectors.toMap(AlertRuleVO::getId, rule -> rule));
        Instant resolvedAt = samples.stream().filter(scope::contains).map(MetricSample::collectedAt).max(Instant::compareTo)
                .orElseGet(Instant::now);
        AlertEvaluationResult clear = new AlertEvaluationResult(true, false, null, MetricAvailability.AVAILABLE);
        for (ActiveAlertState active : stateRepository.findActive(scope, rules)) {
            if (presentKeys.contains(active.key())) {
                continue;
            }
            AlertRuleVO rule = byId.get(active.key().ruleId());
            if (rule == null) {
                continue;
            }
            AlertStateUpdate update = stateMachine.advance(active.state(), clear,
                    Math.max(1, rule.getConsecutiveSamples()), AlertRuleDuration.parse(rule.getDuration()),
                    AlertRuleDuration.parse(rule.getReminderInterval()), resolvedAt);
            if (update.transition() != AlertStateTransition.RESOLVED) {
                continue;
            }
            if (!stateRepository.save(active.key(), update.state())) {
                continue;
            }
            emitLifecycleEvent(rule, active.key(), update, scope.domain(), active.instanceId(), rule.getMetric(),
                    active.labels(), resolvedAt);
        }
    }

    private void emitLifecycleEvent(AlertRuleVO rule, AlertStateKey key, AlertStateUpdate update, AlertDomain domain,
            String instanceId, String metricKey, Map<String, String> labels, Instant collectedAt) {
        LocalDateTime eventTime = LocalDateTime.ofInstant(collectedAt, ZoneOffset.UTC);
        Map<String, String> eventLabels = Map.copyOf(new TreeMap<>(labels == null ? Map.of() : labels));
        SystemAlertVO event = SystemAlertVO.builder().level(level(rule.getSeverity()))
                .title(rule.getName()).description(update.transition() + " " + metricKey + " on " + instanceId)
                .time(eventTime).acknowledged(false).domain(domain).ruleId(rule.getId())
                .fingerprint(key.fingerprint()).transition(update.transition().name()).instanceId(instanceId)
                .currentValue(update.state().currentValue()).labels(eventLabels).build();
        boolean suppressNotification = shouldSuppress(domain, update.transition());
        if (suppressNotification) {
            Optional<SystemAlertVO> cause = notificationSuppressionService.findSuppressingClusterAlert(event);
            if (cause.isPresent()) {
                event.setNotificationSuppressed(true);
                event.setSuppressionCauseAlertId(cause.get().getId());
                event.setSuppressionReason("Suppressed by active cluster incident #" + cause.get().getId()
                        + ": " + cause.get().getTitle());
            }
        }
        SystemAlertVO savedEvent = alertRepository.saveAlert(event);
        if (savedEvent != null) {
            event = savedEvent;
        }
        if (update.transition() == AlertStateTransition.FIRING) {
            alertRepository.markRuleTriggered(rule.getId(), eventTime.toString());
        }
        if (!event.isNotificationSuppressed()) {
            notificationOutboxService.enqueue(event, rule, eventLabels);
        }
    }

    private static boolean shouldSuppress(AlertDomain domain, AlertStateTransition transition) {
        return domain == AlertDomain.BUSINESS
                && (transition == AlertStateTransition.FIRING || transition == AlertStateTransition.REMINDER);
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
        return new MetricSample(sample.metricKey(), sample.domain(), sample.instanceId(), sample.clusterId(), sample.labels(),
                value, MetricAvailability.AVAILABLE, sample.collectedAt());
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
