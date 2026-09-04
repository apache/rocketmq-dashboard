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
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertSilence;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertSilenceMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Repository
@RequiredArgsConstructor
public class MybatisPlusAlertSilenceRepository implements AlertSilenceRepository {
    private final RmqAlertSilenceMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public AlertSilenceVO save(AlertSilenceVO silence) {
        RmqAlertSilence entity = toEntity(silence);
        mapper.insert(entity);
        silence.setId(entity.getId());
        return silence;
    }

    @Override
    public AlertSilenceVO update(AlertSilenceVO silence) {
        RmqAlertSilence entity = toEntity(silence);
        entity.setId(silence.getId());
        if (mapper.updateById(entity) <= 0) {
            throw new IllegalStateException("Unable to update alert silence: " + silence.getId());
        }
        return silence;
    }

    @Override
    public AlertSilenceVO findById(Long id) {
        RmqAlertSilence entity = mapper.selectById(id);
        return entity == null ? null : toVo(entity);
    }

    @Override
    public List<AlertSilenceVO> findAll() {
        return mapper.selectList(new QueryWrapper<RmqAlertSilence>()
                        .orderByDesc("ends_at").orderByDesc("id"))
                .stream().map(this::toVo).toList();
    }

    @Override
    public PageResult<AlertSilenceVO> findPage(int page, int pageSize) {
        IPage<RmqAlertSilence> mapperPage = mapper.selectPage(new Page<>(page, pageSize),
                new QueryWrapper<RmqAlertSilence>().orderByDesc("ends_at").orderByDesc("id"));
        return PageResult.of(mapperPage.getRecords().stream().map(this::toVo).toList(), mapperPage.getTotal(),
                (int) mapperPage.getCurrent(), (int) mapperPage.getSize());
    }

    @Override
    public List<AlertSilenceVO> findActiveCandidates(AlertDomain domain, Long ruleId, String instanceId,
            LocalDateTime now) {
        QueryWrapper<RmqAlertSilence> query = new QueryWrapper<RmqAlertSilence>()
                .and(schedule -> schedule
                        .nested(once -> once
                                .and(type -> type.isNull("recurrence").or()
                                        .eq("recurrence", AlertSilenceRecurrence.ONCE.name()))
                                .le("starts_at", now).gt("ends_at", now))
                        .or(recurring -> recurring
                                .in("recurrence", AlertSilenceRecurrence.DAILY.name(),
                                        AlertSilenceRecurrence.WEEKLY.name())
                                .le("starts_at", now).gt("recurrence_until", now)))
                .and(scope -> scope.isNull("domain").or().eq("domain", domain.name()));
        if (ruleId == null) {
            query.isNull("rule_id");
        } else {
            query.and(scope -> scope.isNull("rule_id").or().eq("rule_id", ruleId));
        }
        if (instanceId == null) {
            query.isNull("instance_id");
        } else {
            query.and(scope -> scope.isNull("instance_id").or().eq("instance_id", instanceId));
        }
        query.orderByDesc("ends_at").orderByDesc("id");
        return mapper.selectList(query).stream().map(this::toVo).toList();
    }

    @Override
    public boolean deleteById(Long id) {
        return mapper.deleteById(id) > 0;
    }

    private RmqAlertSilence toEntity(AlertSilenceVO silence) {
        RmqAlertSilence entity = new RmqAlertSilence();
        entity.setDomain(silence.getDomain() == null ? null : silence.getDomain().name());
        entity.setRuleId(silence.getRuleId());
        entity.setInstanceId(silence.getInstanceId());
        entity.setLabelsJson(writeLabels(silence.getLabels()));
        entity.setStartsAt(silence.getStartsAt());
        entity.setEndsAt(silence.getEndsAt());
        entity.setRecurrence((silence.getRecurrence() == null ? AlertSilenceRecurrence.ONCE
                : silence.getRecurrence()).name());
        entity.setTimeZone(silence.getTimeZone());
        entity.setRecurrenceDaysJson(writeDays(silence.getRecurrenceDays()));
        entity.setRecurrenceUntil(silence.getRecurrenceUntil());
        entity.setReason(silence.getReason());
        entity.setCreatedBy(silence.getCreatedBy());
        return entity;
    }

    private AlertSilenceVO toVo(RmqAlertSilence entity) {
        return AlertSilenceVO.builder().id(entity.getId())
                .domain(entity.getDomain() == null ? null : AlertDomain.valueOf(entity.getDomain()))
                .ruleId(entity.getRuleId()).instanceId(entity.getInstanceId())
                .labels(readLabels(entity.getLabelsJson()))
                .startsAt(entity.getStartsAt()).endsAt(entity.getEndsAt())
                .recurrence(entity.getRecurrence() == null ? AlertSilenceRecurrence.ONCE
                        : AlertSilenceRecurrence.valueOf(entity.getRecurrence()))
                .timeZone(entity.getTimeZone()).recurrenceDays(readDays(entity.getRecurrenceDaysJson()))
                .recurrenceUntil(entity.getRecurrenceUntil())
                .reason(entity.getReason()).createdBy(entity.getCreatedBy()).build();
    }

    private String writeLabels(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(new TreeMap<>(labels));
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to serialize alert silence labels", error);
        }
    }

    private Map<String, String> readLabels(String labelsJson) {
        if (labelsJson == null || labelsJson.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(labelsJson, new TypeReference<>() { });
        } catch (Exception error) {
            throw new IllegalStateException("Unable to read alert silence labels", error);
        }
    }

    private String writeDays(Set<Integer> days) {
        if (days == null || days.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(new TreeSet<>(days));
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to serialize alert silence weekdays", error);
        }
    }

    private Set<Integer> readDays(String daysJson) {
        if (daysJson == null || daysJson.isBlank()) return Set.of();
        try {
            return Set.copyOf(objectMapper.readValue(daysJson, new TypeReference<Set<Integer>>() { }));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to read alert silence weekdays", error);
        }
    }
}
