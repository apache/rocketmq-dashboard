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
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertRule;
import org.apache.rocketmq.studio.persistence.entity.RmqSystemAlert;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertRuleMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertNotificationOutboxMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqSystemAlertMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * MySQL-backed alert repository for alert rules and system alert events.
 */
@RequiredArgsConstructor
@Repository
public class MybatisPlusAlertRepository implements AlertRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RmqAlertRuleMapper ruleMapper;
    private final RmqSystemAlertMapper alertMapper;
    private final RmqAlertNotificationOutboxMapper notificationOutboxMapper;

    @Override
    public List<AlertRuleVO> findAllRules() {
        return ruleMapper.selectList(new QueryWrapper<RmqAlertRule>().orderByAsc("name")).stream()
                .map(MybatisPlusAlertRepository::toRuleVO)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<AlertRuleVO> findRulePage(String search, Boolean enabled,
                                                 int page, int pageSize) {
        QueryWrapper<RmqAlertRule> query = ruleQuery(search, enabled);
        Page<RmqAlertRule> result = ruleMapper.selectPage(new Page<>(page, pageSize), query);
        List<AlertRuleVO> items = result.getRecords().stream()
                .map(MybatisPlusAlertRepository::toRuleVO)
                .toList();
        return PageResult.of(items, result.getTotal(), page, pageSize);
    }

    private QueryWrapper<RmqAlertRule> ruleQuery(String search, Boolean enabled) {
        return new QueryWrapper<RmqAlertRule>()
                .like(StringUtils.hasText(search), "name", search)
                .eq(enabled != null, "enabled", enabled)
                .orderByAsc("name", "id");
    }

    @Override
    public PageResult<AlertRuleVO> findRulesPage(AlertRuleQuery query) {
        QueryWrapper<RmqAlertRule> conditions = new QueryWrapper<RmqAlertRule>()
                .eq(query.enabled() != null, "enabled", query.enabled())
                .and(StringUtils.hasText(query.search()), wrapper -> wrapper
                        .like("name", query.search().trim())
                        .or()
                        .like("metric", query.search().trim()))
                .orderByAsc("name")
                .orderByAsc("id");
        if (query.domain() == AlertDomain.BUSINESS) {
            // Rules created before alert domains were introduced are business rules.
            conditions.and(wrapper -> wrapper.isNull("domain").or().eq("domain", AlertDomain.BUSINESS.name()));
        } else {
            conditions.eq("domain", query.domain().name());
        }
        Page<RmqAlertRule> result = ruleMapper.selectPage(new Page<>(query.page(), query.pageSize()), conditions);
        return PageResult.of(result.getRecords().stream().map(MybatisPlusAlertRepository::toRuleVO).toList(),
                result.getTotal(), query.page(), query.pageSize());
    }

    @Override
    public Optional<AlertRuleVO> findRuleById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ruleMapper.selectById(id))
                .map(MybatisPlusAlertRepository::toRuleVO);
    }

    @Override
    public List<AlertRuleVO> findRulesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ruleMapper.selectList(new QueryWrapper<RmqAlertRule>()
                        .in("id", ids))
                .stream()
                .map(MybatisPlusAlertRepository::toRuleVO)
                .toList();
    }

    @Override
    @Transactional
    public AlertRuleVO insertRule(AlertRuleVO rule) {
        RmqAlertRule entity = toRuleEntity(rule);
        ruleMapper.insert(entity);
        rule.setId(entity.getId());
        return rule;
    }

    @Override
    public boolean replaceRule(AlertRuleVO rule) {
        if (rule.getId() == null || ruleMapper.selectById(rule.getId()) == null) {
            return false;
        }
        return ruleMapper.updateById(toRuleEntity(rule)) > 0;
    }

    @Override
    public void markRuleTriggered(Long id, String triggeredAt) {
        if (id != null) {
            ruleMapper.updateLastTriggered(id, triggeredAt);
        }
    }

    @Override
    public boolean deleteRule(Long id) {
        return id != null && ruleMapper.deleteById(id) > 0;
    }

    @Override
    public List<SystemAlertVO> findAlerts(String level) {
        return alertMapper.selectList(alertQuery(level)).stream()
                .map(MybatisPlusAlertRepository::toAlertVO)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<SystemAlertVO> findAlerts(String level, int page, int pageSize) {
        Page<RmqSystemAlert> result = alertMapper.selectPage(
                new Page<>(page, pageSize), alertQuery(level));
        List<SystemAlertVO> items = result.getRecords().stream()
                .map(MybatisPlusAlertRepository::toAlertVO)
                .toList();
        return PageResult.of(items, result.getTotal(), page, pageSize);
    }

    @Override
    public Optional<SystemAlertVO> findAlertById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(alertMapper.selectById(id))
                .map(MybatisPlusAlertRepository::toAlertVO);
    }

    private QueryWrapper<RmqSystemAlert> alertQuery(String level) {
        return new QueryWrapper<RmqSystemAlert>()
                .eq(StringUtils.hasText(level), "level", normalizeLevel(level))
                .orderByDesc("time", "id");
    }

    @Override
    public PageResult<SystemAlertVO> findAlertsPage(SystemAlertQuery query) {
        QueryWrapper<RmqSystemAlert> conditions = new QueryWrapper<RmqSystemAlert>()
                .eq(StringUtils.hasText(query.level()), "level", normalizeLevel(query.level()))
                .eq(query.domain() != null, "domain", query.domain() == null ? null : query.domain().name())
                .eq(StringUtils.hasText(query.instanceId()), "instance_id", trimToNull(query.instanceId()))
                .eq(StringUtils.hasText(query.transition()), "transition", normalizeTransition(query.transition()))
                .eq(query.notificationSuppressed() != null, "notification_suppressed", query.notificationSuppressed())
                .apply(StringUtils.hasText(query.labelKey()),
                        "JSON_CONTAINS(labels_json, JSON_OBJECT({0}, {1}))", query.labelKey(), query.labelValue())
                .ge(query.from() != null, "time", query.from())
                .le(query.to() != null, "time", query.to())
                .orderByDesc("time", "id");
        Page<RmqSystemAlert> result = alertMapper.selectPage(new Page<>(query.page(), query.pageSize()), conditions);
        return PageResult.of(result.getRecords().stream().map(MybatisPlusAlertRepository::toAlertVO).toList(),
                result.getTotal(), query.page(), query.pageSize());
    }

    @Override
    public SystemAlertVO saveAlert(SystemAlertVO alert) {
        RmqSystemAlert entity = toAlertEntity(alert);
        alertMapper.insert(entity);
        alert.setId(entity.getId());
        return alert;
    }

    @Override
    public boolean acknowledgeAlert(SystemAlertVO alert) {
        return alertMapper.updateById(toAlertEntity(alert)) > 0;
    }

    @Override
    public int deleteAcknowledgedAlerts() {
        notificationOutboxMapper.deleteForAcknowledgedAlerts();
        return Math.toIntExact(alertMapper.delete(
                new QueryWrapper<RmqSystemAlert>().eq("acknowledged", true)));
    }

    // ── Mapping ────────────────────────────────────────────────────

    private static AlertRuleVO toRuleVO(RmqAlertRule entity) {
        AlertRuleVO vo = new AlertRuleVO();
        vo.setId(entity.getId());
        vo.setDomain(StringUtils.hasText(entity.getDomain()) ? parseDomain(entity.getDomain()) : null);
        vo.setName(entity.getName());
        vo.setMetric(entity.getMetric());
        vo.setOperator(entity.getOperator());
        vo.setThreshold(entity.getThreshold() == null ? 0 : entity.getThreshold());
        vo.setThresholdUnit(entity.getThresholdUnit());
        vo.setDuration(entity.getDuration());
        vo.setAggregation(StringUtils.hasText(entity.getAggregation()) ? entity.getAggregation() : "LAST");
        vo.setWindowSeconds(entity.getWindowSeconds() == null ? 0 : entity.getWindowSeconds());
        vo.setChannels(splitCsv(entity.getChannels()));
        vo.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        vo.setLastTriggered(entity.getLastTriggered());
        vo.setDescription(entity.getDescription());
        vo.setBrokerName(entity.getBrokerName());
        vo.setClusterName(entity.getClusterName());
        vo.setSeverity(entity.getSeverity());
        vo.setInstanceId(entity.getInstanceId());
        vo.setConsumerGroup(entity.getConsumerGroup());
        vo.setTopic(entity.getTopic());
        vo.setConsecutiveSamples(entity.getConsecutiveSamples() == null ? 1 : entity.getConsecutiveSamples());
        vo.setReminderInterval(StringUtils.hasText(entity.getReminderInterval()) ? entity.getReminderInterval() : "30m");
        vo.setNotificationTemplate(entity.getNotificationTemplate());
        return vo;
    }

    private static RmqAlertRule toRuleEntity(AlertRuleVO rule) {
        RmqAlertRule entity = new RmqAlertRule();
        entity.setId(rule.getId());
        entity.setDomain((rule.getDomain() == null ? AlertDomain.BUSINESS : rule.getDomain()).name());
        entity.setName(rule.getName());
        entity.setMetric(rule.getMetric());
        entity.setOperator(rule.getOperator());
        entity.setThreshold(rule.getThreshold());
        entity.setThresholdUnit(rule.getThresholdUnit());
        entity.setDuration(rule.getDuration());
        entity.setAggregation(StringUtils.hasText(rule.getAggregation()) ? rule.getAggregation().trim().toUpperCase(Locale.ROOT) : "LAST");
        entity.setWindowSeconds(Math.max(0, rule.getWindowSeconds()));
        entity.setChannels(rule.getChannels() == null
                ? null
                : String.join(",", normalizeChannels(rule.getChannels())));
        entity.setEnabled(rule.isEnabled());
        entity.setLastTriggered(rule.getLastTriggered());
        entity.setDescription(rule.getDescription());
        entity.setBrokerName(rule.getBrokerName());
        entity.setClusterName(rule.getClusterName());
        entity.setSeverity(rule.getSeverity());
        entity.setInstanceId(rule.getInstanceId());
        entity.setConsumerGroup(rule.getConsumerGroup());
        entity.setTopic(rule.getTopic());
        entity.setConsecutiveSamples(Math.max(1, rule.getConsecutiveSamples()));
        entity.setReminderInterval(StringUtils.hasText(rule.getReminderInterval()) ? rule.getReminderInterval() : "30m");
        entity.setNotificationTemplate(rule.getNotificationTemplate());
        entity.setSemanticFingerprint(AlertRuleSemanticFingerprint.of(rule));
        entity.setGmtModified(LocalDateTime.now());
        return entity;
    }

    private static AlertDomain parseDomain(String domain) {
        if (!StringUtils.hasText(domain)) {
            return AlertDomain.BUSINESS;
        }
        try {
            return AlertDomain.valueOf(domain.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return AlertDomain.BUSINESS;
        }
    }

    private static SystemAlertVO toAlertVO(RmqSystemAlert entity) {
        SystemAlertVO vo = new SystemAlertVO();
        vo.setId(entity.getId());
        vo.setLevel(parseLevel(entity.getLevel()));
        vo.setTitle(entity.getTitle());
        vo.setDescription(entity.getDescription());
        vo.setTime(entity.getTime());
        vo.setAcknowledged(Boolean.TRUE.equals(entity.getAcknowledged()));
        vo.setAcknowledgedBy(entity.getAcknowledgedBy());
        vo.setAcknowledgedAt(entity.getAcknowledgedAt());
        vo.setDomain(parseDomain(entity.getDomain()));
        vo.setRuleId(entity.getRuleId());
        vo.setFingerprint(entity.getFingerprint());
        vo.setTransition(entity.getTransition());
        vo.setInstanceId(entity.getInstanceId());
        vo.setCurrentValue(entity.getCurrentValue());
        vo.setNotificationSuppressed(Boolean.TRUE.equals(entity.getNotificationSuppressed()));
        vo.setSuppressionCauseAlertId(entity.getSuppressionCauseAlertId());
        vo.setSuppressionReason(entity.getSuppressionReason());
        vo.setLabels(readLabels(entity.getLabelsJson()));
        return vo;
    }

    private static RmqSystemAlert toAlertEntity(SystemAlertVO alert) {
        RmqSystemAlert entity = new RmqSystemAlert();
        entity.setId(alert.getId());
        entity.setLevel(alert.getLevel() == null ? null : alert.getLevel().name());
        entity.setTitle(alert.getTitle());
        entity.setDescription(alert.getDescription());
        entity.setTime(alert.getTime());
        entity.setAcknowledged(alert.isAcknowledged());
        entity.setAcknowledgedBy(alert.getAcknowledgedBy());
        entity.setAcknowledgedAt(alert.getAcknowledgedAt());
        entity.setDomain(alert.getDomain() == null ? null : alert.getDomain().name());
        entity.setRuleId(alert.getRuleId());
        entity.setFingerprint(alert.getFingerprint());
        entity.setTransition(alert.getTransition());
        entity.setInstanceId(alert.getInstanceId());
        entity.setCurrentValue(alert.getCurrentValue());
        entity.setNotificationSuppressed(alert.isNotificationSuppressed());
        entity.setSuppressionCauseAlertId(alert.getSuppressionCauseAlertId());
        entity.setSuppressionReason(alert.getSuppressionReason());
        entity.setLabelsJson(writeLabels(alert.getLabels()));
        entity.setGmtModified(LocalDateTime.now());
        return entity;
    }

    private static AlertLevel parseLevel(String level) {
        if (!StringUtils.hasText(level)) {
            return AlertLevel.info;
        }
        try {
            return AlertLevel.valueOf(level.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return AlertLevel.info;
        }
    }

    private static String normalizeLevel(String level) {
        String normalized = trimToNull(level);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeTransition(String transition) {
        String normalized = trimToNull(transition);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static List<String> splitCsv(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return normalizeChannels(Arrays.asList(value.split(",")));
    }

    private static List<String> normalizeChannels(List<String> channels) {
        return channels.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String writeLabels(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(new TreeMap<>(labels));
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to serialize alert labels", error);
        }
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
