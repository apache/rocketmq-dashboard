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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.cluster.metrics.MetricCollectionScope;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertState;
import org.apache.rocketmq.studio.persistence.entity.RmqSystemAlert;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertStateMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqSystemAlertMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MybatisPlusAlertStateRepository implements AlertStateRepository {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RmqAlertStateMapper mapper;
    private final RmqSystemAlertMapper alertMapper;

    @Override
    public Optional<AlertRuleState> find(AlertStateKey key) {
        return Optional.ofNullable(mapper.selectOne(new QueryWrapper<RmqAlertState>()
                .eq("rule_id", key.ruleId()).eq("fingerprint", key.fingerprint()).last("LIMIT 1")))
                .map(MybatisPlusAlertStateRepository::toState);
    }

    @Override
    public boolean save(AlertStateKey key, AlertRuleState state) {
        RmqAlertState entity = mapper.selectOne(new QueryWrapper<RmqAlertState>()
                .eq("rule_id", key.ruleId()).eq("fingerprint", key.fingerprint()).last("LIMIT 1"));
        if (entity == null) {
            entity = new RmqAlertState();
            entity.setRuleId(key.ruleId());
            entity.setFingerprint(key.fingerprint());
            entity.setVersion(0);
            apply(entity, state);
            try {
                return mapper.insert(entity) == 1;
            } catch (DuplicateKeyException ignored) {
                return false;
            }
        } else {
            int version = entity.getVersion() == null ? 0 : entity.getVersion();
            apply(entity, state);
            // A concurrent acknowledgement wins over this sample. The next collection cycle reloads the state.
            return mapper.updateIfVersion(entity, version) == 1;
        }
    }

    @Override
    public boolean acknowledge(AlertStateKey key, Instant firedAt) {
        if (firedAt == null) {
            return false;
        }
        return mapper.acknowledgeFiring(key.ruleId(), key.fingerprint(), toLocal(firedAt),
                LocalDateTime.now(ZoneOffset.UTC)) > 0;
    }

    @Override
    public void deleteByRuleId(Long ruleId) {
        if (ruleId != null) {
            mapper.delete(new QueryWrapper<RmqAlertState>().eq("rule_id", ruleId));
        }
    }

    @Override
    public List<ActiveAlertState> findActive(MetricCollectionScope scope, List<AlertRuleVO> rules) {
        if (scope == null || rules == null || rules.isEmpty()) {
            return List.of();
        }
        Set<String> metricKeys = scope.metricKeys();
        Map<Long, AlertRuleVO> scopedRules = rules.stream()
                .filter(rule -> rule.getId() != null)
                .filter(AlertRuleVO::isEnabled)
                .filter(rule -> ruleDomain(rule) == scope.domain())
                .filter(rule -> metricKeys.contains(rule.getMetric()))
                .filter(rule -> !StringUtils.hasText(rule.getInstanceId())
                        || scope.instanceId().equals(rule.getInstanceId()))
                .collect(Collectors.toMap(AlertRuleVO::getId, rule -> rule, (left, right) -> left));
        if (scopedRules.isEmpty()) {
            return List.of();
        }
        List<RmqAlertState> activeStates = mapper.selectList(new QueryWrapper<RmqAlertState>()
                        .in("rule_id", scopedRules.keySet())
                        .in("status", List.of(AlertStateStatus.FIRING.name(), AlertStateStatus.ACKED.name())));
        Map<AlertStateKey, RmqSystemAlert> latestAlerts = findLatestAlerts(scope, activeStates);
        return activeStates
                .stream()
                .map(state -> toActiveState(state, latestAlerts.get(new AlertStateKey(state.getRuleId(),
                        state.getFingerprint()))))
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public List<AlertRuleRuntimeVO> findRuntimeByRuleIds(List<AlertRuleVO> rules) {
        Map<Long, AlertRuleVO> byId = rules.stream().filter(rule -> rule.getId() != null)
                .collect(Collectors.toMap(AlertRuleVO::getId, rule -> rule));
        if (byId.isEmpty()) return List.of();
        return mapper.selectList(new QueryWrapper<RmqAlertState>().in("rule_id", byId.keySet())).stream()
                .map(state -> toRuntime(state, byId.get(state.getRuleId()))).toList();
    }

    private static AlertRuleRuntimeVO toRuntime(RmqAlertState entity, AlertRuleVO rule) {
        LocalDateTime next = entity.getLastNotifiedAt() == null || rule == null ? null
                : entity.getLastNotifiedAt().plus(AlertRuleDuration.parse(rule.getReminderInterval()));
        return AlertRuleRuntimeVO.builder().ruleId(entity.getRuleId()).fingerprint(entity.getFingerprint())
                .status(AlertStateStatus.valueOf(entity.getStatus())).consecutiveHits(entity.getConsecutiveHits() == null ? 0 : entity.getConsecutiveHits())
                .currentValue(entity.getCurrentValue()).lastNotifiedAt(entity.getLastNotifiedAt()).nextReminderAt(next).build();
    }

    private Map<AlertStateKey, RmqSystemAlert> findLatestAlerts(MetricCollectionScope scope,
            List<RmqAlertState> activeStates) {
        if (activeStates.isEmpty()) {
            return Map.of();
        }
        Set<Long> ruleIds = activeStates.stream().map(RmqAlertState::getRuleId).collect(Collectors.toSet());
        Set<String> fingerprints = activeStates.stream().map(RmqAlertState::getFingerprint).collect(Collectors.toSet());
        Set<AlertStateKey> activeKeys = activeStates.stream()
                .map(state -> new AlertStateKey(state.getRuleId(), state.getFingerprint()))
                .collect(Collectors.toCollection(HashSet::new));
        return alertMapper.selectList(new QueryWrapper<RmqSystemAlert>()
                        .in("rule_id", ruleIds)
                        .in("fingerprint", fingerprints)
                        .eq("domain", scope.domain().name())
                        .eq("instance_id", scope.instanceId())
                        .orderByDesc("time", "id"))
                .stream()
                .filter(alert -> activeKeys.contains(new AlertStateKey(alert.getRuleId(), alert.getFingerprint())))
                .collect(Collectors.toMap(alert -> new AlertStateKey(alert.getRuleId(), alert.getFingerprint()),
                        alert -> alert, (latest, ignored) -> latest));
    }

    private Optional<ActiveAlertState> toActiveState(RmqAlertState state, RmqSystemAlert alert) {
        if (alert == null) {
            return Optional.empty();
        }
        return Optional.of(new ActiveAlertState(new AlertStateKey(state.getRuleId(), state.getFingerprint()),
                toState(state), alert.getInstanceId(), readLabels(alert.getLabelsJson())));
    }

    private static AlertDomain ruleDomain(AlertRuleVO rule) {
        return rule.getDomain() == null ? AlertDomain.BUSINESS : rule.getDomain();
    }

    private static void apply(RmqAlertState entity, AlertRuleState state) {
        entity.setStatus(state.status().name());
        entity.setConsecutiveHits(state.consecutiveHits());
        entity.setCurrentValue(state.currentValue());
        entity.setFirstPendingAt(toLocal(state.firstPendingAt()));
        entity.setFiredAt(toLocal(state.firedAt()));
        entity.setLastNotifiedAt(toLocal(state.lastNotifiedAt()));
        entity.setResolvedAt(toLocal(state.resolvedAt()));
        entity.setGmtModified(LocalDateTime.now(ZoneOffset.UTC));
    }

    private static AlertRuleState toState(RmqAlertState entity) {
        return new AlertRuleState(AlertStateStatus.valueOf(entity.getStatus()),
                entity.getConsecutiveHits() == null ? 0 : entity.getConsecutiveHits(), entity.getCurrentValue(),
                toInstant(entity.getFirstPendingAt()), toInstant(entity.getFiredAt()),
                toInstant(entity.getLastNotifiedAt()), toInstant(entity.getResolvedAt()));
    }

    private static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static Map<String, String> readLabels(String labelsJson) {
        if (!StringUtils.hasText(labelsJson)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(labelsJson, new TypeReference<>() { });
        } catch (Exception error) {
            throw new IllegalStateException("Unable to read alert labels", error);
        }
    }
}
