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
package com.rocketmq.studio.ops.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class InMemoryAlertRepository implements AlertRepository {

    private final Map<String, AlertRuleVO> rules = new ConcurrentHashMap<>();
    private final Map<String, SystemAlertVO> alerts = new ConcurrentHashMap<>();

    @Override
    public List<AlertRuleVO> findAllRules() {
        return new ArrayList<>(rules.values());
    }

    @Override
    public AlertRuleVO saveRule(AlertRuleVO rule) {
        rules.put(rule.getId(), rule);
        log.debug("Saved alert rule id={}", rule.getId());
        return rule;
    }

    @Override
    public void deleteRule(String id) {
        rules.remove(id);
        log.debug("Deleted alert rule id={}", id);
    }

    @Override
    public List<SystemAlertVO> findAlerts(String level) {
        return alerts.values().stream()
                .filter(a -> level == null || level.equalsIgnoreCase(a.getLevel().name()))
                .collect(Collectors.toList());
    }

    @Override
    public SystemAlertVO saveAlert(SystemAlertVO alert) {
        alerts.put(alert.getId(), alert);
        log.debug("Saved system alert id={}", alert.getId());
        return alert;
    }

    @Override
    public int deleteAcknowledgedAlerts() {
        List<String> toRemove = alerts.values().stream()
                .filter(SystemAlertVO::isAcknowledged)
                .map(SystemAlertVO::getId)
                .collect(Collectors.toList());
        toRemove.forEach(alerts::remove);
        log.debug("Cleared {} acknowledged system alerts", toRemove.size());
        return toRemove.size();
    }
}
