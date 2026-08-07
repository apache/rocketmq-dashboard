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
import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertRule;
import org.apache.rocketmq.studio.persistence.entity.RmqSystemAlert;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertRuleMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqSystemAlertMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MySQL-backed alert repository for alert rules and system alert events.
 */
@RequiredArgsConstructor
@Repository
public class MybatisPlusAlertRepository implements AlertRepository {

    private final RmqAlertRuleMapper ruleMapper;
    private final RmqSystemAlertMapper alertMapper;

    @Override
    public List<AlertRuleVO> findAllRules() {
        return ruleMapper.selectList(new QueryWrapper<RmqAlertRule>().orderByAsc("name")).stream()
                .map(MybatisPlusAlertRepository::toRuleVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AlertRuleVO saveRule(AlertRuleVO rule) {
        RmqAlertRule entity = toRuleEntity(rule);
        if (entity.getId() != null && ruleMapper.selectById(entity.getId()) != null) {
            ruleMapper.updateById(entity);
        } else {
            ruleMapper.insert(entity);
        }
        return rule;
    }

    @Override
    public boolean replaceRule(AlertRuleVO rule) {
        if (ruleMapper.selectById(rule.getId()) == null) {
            return false;
        }
        ruleMapper.updateById(toRuleEntity(rule));
        return true;
    }

    @Override
    public boolean deleteRule(String id) {
        return ruleMapper.deleteById(id) > 0;
    }

    @Override
    public List<SystemAlertVO> findAlerts(String level) {
        QueryWrapper<RmqSystemAlert> query = new QueryWrapper<RmqSystemAlert>()
                .eq(StringUtils.hasText(level), "level", level == null ? null : level.toLowerCase())
                .orderByDesc("time");
        return alertMapper.selectList(query).stream()
                .map(MybatisPlusAlertRepository::toAlertVO)
                .collect(Collectors.toList());
    }

    @Override
    public SystemAlertVO saveAlert(SystemAlertVO alert) {
        RmqSystemAlert entity = toAlertEntity(alert);
        if (entity.getId() != null && alertMapper.selectById(entity.getId()) != null) {
            alertMapper.updateById(entity);
        } else {
            alertMapper.insert(entity);
        }
        return alert;
    }

    @Override
    public int deleteAcknowledgedAlerts() {
        return Math.toIntExact(alertMapper.delete(
                new QueryWrapper<RmqSystemAlert>().eq("acknowledged", true)));
    }

    // ── Mapping ────────────────────────────────────────────────────

    private static AlertRuleVO toRuleVO(RmqAlertRule entity) {
        AlertRuleVO vo = new AlertRuleVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setMetric(entity.getMetric());
        vo.setOperator(entity.getOperator());
        vo.setThreshold(entity.getThreshold() == null ? 0 : entity.getThreshold());
        vo.setThresholdUnit(entity.getThresholdUnit());
        vo.setDuration(entity.getDuration());
        vo.setChannels(splitCsv(entity.getChannels()));
        vo.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        vo.setLastTriggered(entity.getLastTriggered());
        vo.setDescription(entity.getDescription());
        vo.setBrokerName(entity.getBrokerName());
        vo.setClusterName(entity.getClusterName());
        vo.setSeverity(entity.getSeverity());
        return vo;
    }

    private static RmqAlertRule toRuleEntity(AlertRuleVO rule) {
        RmqAlertRule entity = new RmqAlertRule();
        entity.setId(rule.getId());
        entity.setName(rule.getName());
        entity.setMetric(rule.getMetric());
        entity.setOperator(rule.getOperator());
        entity.setThreshold(rule.getThreshold());
        entity.setThresholdUnit(rule.getThresholdUnit());
        entity.setDuration(rule.getDuration());
        entity.setChannels(rule.getChannels() == null ? null : String.join(",", rule.getChannels()));
        entity.setEnabled(rule.isEnabled());
        entity.setLastTriggered(rule.getLastTriggered());
        entity.setDescription(rule.getDescription());
        entity.setBrokerName(rule.getBrokerName());
        entity.setClusterName(rule.getClusterName());
        entity.setSeverity(rule.getSeverity());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private static SystemAlertVO toAlertVO(RmqSystemAlert entity) {
        SystemAlertVO vo = new SystemAlertVO();
        vo.setId(entity.getId());
        vo.setLevel(parseLevel(entity.getLevel()));
        vo.setTitle(entity.getTitle());
        vo.setDescription(entity.getDescription());
        vo.setTime(entity.getTime());
        vo.setAcknowledged(Boolean.TRUE.equals(entity.getAcknowledged()));
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
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private static AlertLevel parseLevel(String level) {
        if (!StringUtils.hasText(level)) {
            return AlertLevel.info;
        }
        try {
            return AlertLevel.valueOf(level);
        } catch (IllegalArgumentException exception) {
            return AlertLevel.info;
        }
    }

    private static List<String> splitCsv(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.toList());
    }
}
