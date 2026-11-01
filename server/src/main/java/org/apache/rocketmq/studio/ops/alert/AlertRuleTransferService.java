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

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertRuleTransferService {
    private static final int MAX_RULES_PER_IMPORT = 200;

    private final AlertService alertService;
    private final NativeAlertMetricCatalogService metricCatalogService;

    public AlertRuleTransferDTO exportRules(AlertDomain domain) {
        AlertRuleTransferDTO transfer = new AlertRuleTransferDTO();
        transfer.setVersion(AlertRuleTransferDTO.VERSION);
        transfer.setDomain(domain);
        transfer.setRules(alertService.listRules(domain).stream().map(this::toRequest).toList());
        return transfer;
    }

    @Transactional
    public List<AlertRuleVO> importRules(AlertDomain domain, AlertRuleTransferDTO transfer) {
        validateEnvelope(domain, transfer);
        List<AlertRuleVO> candidates = transfer.getRules().stream().map(request -> {
            AlertRuleVO candidate = request.toAlertRuleVO();
            candidate.setId(null);
            candidate.setDomain(domain);
            if (candidate.getMetric() != null) {
                candidate.setMetric(candidate.getMetric().trim());
            }
            metricCatalogService.validate(candidate);
            return candidate;
        }).toList();
        return candidates.stream().map(candidate -> alertService.createRule(domain, candidate)).toList();
    }

    private static void validateEnvelope(AlertDomain domain, AlertRuleTransferDTO transfer) {
        if (transfer == null) {
            throw new BusinessException(400, "Alert rule import document is required");
        }
        if (!Integer.valueOf(AlertRuleTransferDTO.VERSION).equals(transfer.getVersion())) {
            throw new BusinessException(400, "Unsupported alert rule import version");
        }
        if (transfer.getDomain() != domain) {
            throw new BusinessException(400, "Alert rule import domain does not match this page");
        }
        if (transfer.getRules() == null || transfer.getRules().isEmpty()
                || transfer.getRules().size() > MAX_RULES_PER_IMPORT) {
            throw new BusinessException(400, "Alert rule import must contain between 1 and "
                    + MAX_RULES_PER_IMPORT + " rules");
        }
    }

    private AlertRuleRequestDTO toRequest(AlertRuleVO rule) {
        AlertRuleRequestDTO request = new AlertRuleRequestDTO();
        request.setName(rule.getName());
        request.setMetric(rule.getMetric());
        request.setOperator(rule.getOperator());
        request.setThreshold(rule.getThreshold());
        request.setThresholdUnit(rule.getThresholdUnit());
        request.setDuration(rule.getDuration());
        request.setAggregation(rule.getAggregation());
        request.setWindowSeconds(rule.getWindowSeconds());
        request.setChannels(rule.getChannels());
        request.setEnabled(rule.isEnabled());
        request.setDescription(rule.getDescription());
        request.setBrokerName(rule.getBrokerName());
        request.setClusterName(rule.getClusterName());
        request.setSeverity(rule.getSeverity());
        request.setInstanceId(rule.getInstanceId());
        request.setConsumerGroup(rule.getConsumerGroup());
        request.setTopic(rule.getTopic());
        request.setConsecutiveSamples(rule.getConsecutiveSamples());
        request.setReminderInterval(rule.getReminderInterval());
        request.setNotificationTemplate(rule.getNotificationTemplate());
        return request;
    }
}
