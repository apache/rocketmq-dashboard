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
import org.apache.rocketmq.studio.persistence.entity.RmqAlertSilence;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertSilenceMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

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
    public List<AlertSilenceVO> findAll() {
        return mapper.selectList(new QueryWrapper<RmqAlertSilence>()
                        .orderByDesc("ends_at").orderByDesc("id"))
                .stream().map(this::toVo).toList();
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
        entity.setReason(silence.getReason());
        entity.setCreatedBy(silence.getCreatedBy());
        return entity;
    }

    private AlertSilenceVO toVo(RmqAlertSilence entity) {
        return AlertSilenceVO.builder().id(entity.getId())
                .domain(parseDomain(entity.getDomain()))
                .ruleId(entity.getRuleId()).instanceId(entity.getInstanceId())
                .labels(readLabels(entity.getLabelsJson()))
                .startsAt(entity.getStartsAt()).endsAt(entity.getEndsAt())
                .reason(entity.getReason()).createdBy(entity.getCreatedBy()).build();
    }

    /**
     * A legacy or manually edited domain must not break silence matching, so unknown values fall
     * back to the business domain like {@code rmq_alert_rule} rows without a recognized domain.
     */
    private static AlertDomain parseDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        try {
            return AlertDomain.valueOf(domain.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return AlertDomain.BUSINESS;
        }
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
}
