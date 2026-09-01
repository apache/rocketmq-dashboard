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
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertState;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertStateMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MybatisPlusAlertStateRepository implements AlertStateRepository {
    private final RmqAlertStateMapper mapper;

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
                .status(parseStatus(entity.getStatus())).consecutiveHits(entity.getConsecutiveHits() == null ? 0 : entity.getConsecutiveHits())
                .currentValue(entity.getCurrentValue()).lastNotifiedAt(entity.getLastNotifiedAt()).nextReminderAt(next).build();
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
        return new AlertRuleState(parseStatus(entity.getStatus()),
                entity.getConsecutiveHits() == null ? 0 : entity.getConsecutiveHits(), entity.getCurrentValue(),
                toInstant(entity.getFirstPendingAt()), toInstant(entity.getFiredAt()),
                toInstant(entity.getLastNotifiedAt()), toInstant(entity.getResolvedAt()));
    }

    /**
     * A legacy or partially written status must not abort the collection cycle, so unknown values
     * fall back to the idle state and the next sample re-advances the machine from there.
     */
    private static AlertStateStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return AlertStateStatus.OK;
        }
        try {
            return AlertStateStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return AlertStateStatus.OK;
        }
    }

    private static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
