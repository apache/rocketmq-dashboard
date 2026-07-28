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
package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.exception.ServiceException;
import org.apache.rocketmq.dashboard.model.AlertRuleVO;
import org.apache.rocketmq.dashboard.service.AlertRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link AlertRuleService}.
 * Migrated from the standalone studio server module so that the main
 * dashboard application serves the {@code /api/alert/rules} endpoints
 * expected by the frontend.
 */
@Service
public class AlertRuleServiceImpl implements AlertRuleService {

    private static final Logger log = LoggerFactory.getLogger(AlertRuleServiceImpl.class);

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<String, AlertRuleVO> rules = new ConcurrentHashMap<>();

    @Override
    public List<AlertRuleVO> listRules() {
        return new ArrayList<>(rules.values());
    }

    @Override
    public AlertRuleVO createRule(AlertRuleVO rule) {
        log.info("Creating alert rule: {}", rule.getAlert());
        rule.setId(UUID.randomUUID().toString());
        String now = LocalDateTime.now().format(FORMATTER);
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        rules.put(rule.getId(), rule);
        return rule;
    }

    @Override
    public AlertRuleVO updateRule(AlertRuleVO rule) {
        log.info("Updating alert rule: {}", rule.getId());
        AlertRuleVO existing = rules.get(rule.getId());
        if (existing == null) {
            throw new ServiceException(404, "Alert rule not found: " + rule.getId());
        }
        rule.setCreatedAt(existing.getCreatedAt());
        rule.setUpdatedAt(LocalDateTime.now().format(FORMATTER));
        rules.put(rule.getId(), rule);
        return rule;
    }

    @Override
    public AlertRuleVO toggleRule(String id, boolean enabled) {
        log.info("Toggling alert rule id={}, enabled={}", id, enabled);
        AlertRuleVO rule = rules.get(id);
        if (rule == null) {
            throw new ServiceException(404, "Alert rule not found: " + id);
        }
        rule.setEnabled(enabled);
        rule.setUpdatedAt(LocalDateTime.now().format(FORMATTER));
        return rule;
    }

    @Override
    public void deleteRule(String id) {
        log.info("Deleting alert rule id={}", id);
        rules.remove(id);
    }
}
