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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;


    public List<AlertRuleVO> listRules() {
        log.info("Listing all alert rules");
        return alertRepository.findAllRules();
    }


    public AlertRuleVO createRule(AlertRuleVO rule) {
        log.info("Creating alert rule: {}", rule.getName());
        rule.setId(UUID.randomUUID().toString());
        return alertRepository.saveRule(rule);
    }


    public AlertRuleVO updateRule(AlertRuleVO rule) {
        log.info("Updating alert rule: {}", rule.getId());
        return alertRepository.saveRule(rule);
    }


    public AlertRuleVO toggleRule(String id, boolean enabled) {
        log.info("Toggling alert rule id={}, enabled={}", id, enabled);
        List<AlertRuleVO> rules = alertRepository.findAllRules();
        AlertRuleVO rule = rules.stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new com.rocketmq.studio.common.exception.BusinessException(404, "Alert rule not found: " + id));
        rule.setEnabled(enabled);
        return alertRepository.saveRule(rule);
    }


    public void deleteRule(String id) {
        log.info("Deleting alert rule id={}", id);
        alertRepository.deleteRule(id);
    }


    public List<SystemAlertVO> listAlerts(String level) {
        log.info("Listing system alerts, level={}", level);
        return alertRepository.findAlerts(level);
    }


    public SystemAlertVO acknowledgeAlert(String id) {
        log.info("Acknowledging system alert id={}", id);
        List<SystemAlertVO> alerts = alertRepository.findAlerts(null);
        SystemAlertVO alert = alerts.stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new com.rocketmq.studio.common.exception.BusinessException(404, "System alert not found: " + id));
        alert.setAcknowledged(true);
        return alertRepository.saveAlert(alert);
    }


    public int clearAcknowledged() {
        log.info("Clearing acknowledged system alerts");
        return alertRepository.deleteAcknowledgedAlerts();
    }
}
