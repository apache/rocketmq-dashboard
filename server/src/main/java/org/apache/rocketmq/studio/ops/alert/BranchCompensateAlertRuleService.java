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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BranchCompensateAlertRuleService {

    private final BranchCompensateAlertRuleRepository branchCompensateAlertRuleRepository;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<BranchCompensateAlertRuleVO> listRules() {
        log.info("Listing all branch compensate alert rules");
        return branchCompensateAlertRuleRepository.findAllRules();
    }

    public BranchCompensateAlertRuleVO createRule(BranchCompensateAlertRuleVO rule) {
        log.info("Creating branch compensate alert rule: {}", rule.getName());
        rule.setId(UUID.randomUUID().toString());
        String now = LocalDateTime.now().format(FORMATTER);
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        return branchCompensateAlertRuleRepository.saveRule(rule);
    }

    public BranchCompensateAlertRuleVO updateRule(BranchCompensateAlertRuleVO rule) {
        log.info("Updating branch compensate alert rule: {}", rule.getId());
        BranchCompensateAlertRuleVO existing = branchCompensateAlertRuleRepository.findRuleById(rule.getId());
        if (existing == null) {
            throw new BusinessException(404, "Branch compensate alert rule not found: " + rule.getId());
        }
        rule.setCreatedAt(existing.getCreatedAt());
        rule.setUpdatedAt(LocalDateTime.now().format(FORMATTER));
        return branchCompensateAlertRuleRepository.saveRule(rule);
    }

    public BranchCompensateAlertRuleVO toggleRule(String id, boolean enabled) {
        log.info("Toggling branch compensate alert rule id={}, enabled={}", id, enabled);
        BranchCompensateAlertRuleVO rule = branchCompensateAlertRuleRepository.findRuleById(id);
        if (rule == null) {
            throw new BusinessException(404, "Branch compensate alert rule not found: " + id);
        }
        rule.setEnabled(enabled);
        rule.setUpdatedAt(LocalDateTime.now().format(FORMATTER));
        return branchCompensateAlertRuleRepository.saveRule(rule);
    }

    public void deleteRule(String id) {
        log.info("Deleting branch compensate alert rule id={}", id);
        branchCompensateAlertRuleRepository.deleteRule(id);
    }
}