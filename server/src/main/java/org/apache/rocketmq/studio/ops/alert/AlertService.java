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
import org.apache.rocketmq.studio.audit.OperationAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private static final Set<String> VALID_OPERATORS = Set.of(">", ">=", "<", "<=", "==", "!=");
    private static final Pattern METRIC_NAME_PATTERN = Pattern.compile("^[a-zA-Z_:][a-zA-Z0-9_:]*$");
    private static final Pattern DURATION_PATTERN = Pattern.compile("^\\d+(ms|s|m|h|d|w|y)$");

    private final AlertRepository alertRepository;
    private final AlertRuleAssetService alertRuleAssetService;
    private final OperationAuditService operationAuditService;


    public List<AlertRuleVO> listRules() {
        log.info("Listing all alert rules");
        return alertRepository.findAllRules();
    }

    public String exportPrometheusRulesYaml() {
        List<AlertRuleVO> rules = alertRepository.findAllRules().stream()
                .filter(AlertRuleVO::isEnabled)
                .toList();
        List<PrometheusAlertRule> prometheusRules = rules.isEmpty()
                ? defaultPrometheusRules()
                : rules.stream().map(this::toPrometheusRule).toList();

        StringBuilder yaml = new StringBuilder();
        yaml.append("groups:\n");
        int index = 1;
        // Prometheus requires each group name to be unique, so rules sharing a group must be
        // emitted under a single "  - name:" block instead of one block per rule.
        Map<String, List<PrometheusAlertRule>> rulesByGroup = new LinkedHashMap<>();
        for (PrometheusAlertRule rule : prometheusRules) {
            rulesByGroup.computeIfAbsent(rule.group(), key -> new ArrayList<>()).add(rule);
        }
        for (Map.Entry<String, List<PrometheusAlertRule>> group : rulesByGroup.entrySet()) {
            yaml.append("  - name: ").append(group.getKey()).append('\n');
            yaml.append("    rules:\n");
            Set<String> usedAlertNames = new HashSet<>();
            for (PrometheusAlertRule rule : group.getValue()) {
                String uniqueAlertName = ensureUniqueAlertName(rule.alert(), usedAlertNames);
                yaml.append("      # Rule ").append(index++).append(": ").append(uniqueAlertName).append('\n');
                yaml.append("      - alert: ").append(uniqueAlertName).append('\n');
                yaml.append("        expr: ").append(rule.expr()).append('\n');
                yaml.append("        for: ").append(rule.duration()).append('\n');
                yaml.append("        labels:\n");
                yaml.append("          severity: ").append(rule.severity()).append('\n');
                yaml.append("          team: ").append(rule.team()).append('\n');
                yaml.append("        annotations:\n");
                yaml.append("          summary: \"").append(escapeDoubleQuotedValue(rule.summary())).append("\"\n");
                yaml.append("          description: \"").append(escapeDoubleQuotedValue(rule.description()))
                        .append("\"\n");
            }
        }
        return yaml.toString();
    }


    public AlertRuleVO createRule(AlertRuleVO rule) {
        if (rule == null) {
            throw new BusinessException(400, "Alert rule request is required");
        }
        log.info("Creating alert rule: {}", rule.getName());
        rule.setId(UUID.randomUUID().toString());
        AlertRuleVO saved = alertRepository.saveRule(rule);
        auditRule("CREATE_ALERT_RULE", saved, null);
        return saved;
    }


    public AlertRuleVO updateRule(AlertRuleVO rule) {
        if (rule == null) {
            throw new BusinessException(400, "Alert rule request is required");
        }
        String id = rule.getId();
        log.info("Updating alert rule: {}", id);
        validateRuleId(id);
        if (!alertRepository.replaceRule(rule)) {
            throw ruleNotFound(id);
        }
        auditRule("UPDATE_ALERT_RULE", rule, null);
        return rule;
    }


    public AlertRuleVO toggleRule(String id, boolean enabled) {
        log.info("Toggling alert rule id={}, enabled={}", id, enabled);
        validateRuleId(id);
        AlertRuleVO rule = alertRepository.findRuleById(id)
                .orElseThrow(() -> ruleNotFound(id));
        rule.setEnabled(enabled);
        AlertRuleVO saved = alertRepository.saveRule(rule);
        auditRule("TOGGLE_ALERT_RULE", saved, "enabled=" + enabled);
        return saved;
    }


    public void deleteRule(String id) {
        log.info("Deleting alert rule id={}", id);
        validateRuleId(id);
        if (!alertRepository.deleteRule(id)) {
            throw ruleNotFound(id);
        }
        recordAudit("DELETE_ALERT_RULE", "ALERT_RULE", id, null, null);
    }

    public AlertRuleBulkResultVO bulkToggleRules(List<String> ids, boolean enabled) {
        List<String> normalizedIds = normalizeBulkIds(ids);
        Map<String, AlertRuleVO> rulesById = new LinkedHashMap<>();
        for (AlertRuleVO rule : alertRepository.findRulesByIds(normalizedIds)) {
            rulesById.put(rule.getId(), rule);
        }
        List<String> succeeded = new ArrayList<>();
        Map<String, String> failures = new LinkedHashMap<>();
        List<AlertRuleVO> updated = new ArrayList<>();
        for (String id : normalizedIds) {
            AlertRuleVO rule = rulesById.get(id);
            if (rule == null) {
                failures.put(id, "Alert rule not found");
                continue;
            }
            try {
                rule.setEnabled(enabled);
                // replaceRule performs an existence-checked update; a rule deleted between the
                // snapshot load and this call is reported as a failure instead of being recreated.
                if (!alertRepository.replaceRule(rule)) {
                    failures.put(id, "Alert rule not found");
                    continue;
                }
                auditRule("TOGGLE_ALERT_RULE", rule, "enabled=" + enabled + ", bulk=true");
                succeeded.add(id);
                updated.add(rule);
            } catch (RuntimeException failure) {
                failures.put(id, failure.getMessage() == null ? "Update failed" : failure.getMessage());
            }
        }
        return AlertRuleBulkResultVO.builder()
                .succeededIds(succeeded).failures(failures).updatedRules(updated).build();
    }

    public AlertRuleBulkResultVO bulkDeleteRules(List<String> ids) {
        List<String> normalizedIds = normalizeBulkIds(ids);
        List<String> succeeded = new ArrayList<>();
        Map<String, String> failures = new LinkedHashMap<>();
        for (String id : normalizedIds) {
            try {
                if (!alertRepository.deleteRule(id)) {
                    failures.put(id, "Alert rule not found");
                    continue;
                }
                recordAudit("DELETE_ALERT_RULE", "ALERT_RULE", id, null, "bulk=true");
                succeeded.add(id);
            } catch (RuntimeException failure) {
                failures.put(id, failure.getMessage() == null ? "Delete failed" : failure.getMessage());
            }
        }
        return AlertRuleBulkResultVO.builder()
                .succeededIds(succeeded).failures(failures).updatedRules(List.of()).build();
    }

    private List<String> normalizeBulkIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "ids are required");
        }
        Set<String> seen = new HashSet<>();
        List<String> normalized = new ArrayList<>();
        for (String id : ids) {
            validateRuleId(id);
            String trimmed = id.trim();
            if (seen.add(trimmed)) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }


    public List<SystemAlertVO> listAlerts(String level) {
        log.info("Listing system alerts, level={}", level);
        return alertRepository.findAlerts(level);
    }


    public SystemAlertVO acknowledgeAlert(String id) {
        log.info("Acknowledging system alert id={}", id);
        if (id == null || id.isBlank()) {
            throw new BusinessException(400, "System alert ID is required");
        }
        SystemAlertVO alert = alertRepository.findAlertById(id)
                .orElseThrow(() -> new org.apache.rocketmq.studio.common.exception.BusinessException(404,
                        "System alert not found: " + id));
        alert.setAcknowledged(true);
        if (!alertRepository.acknowledgeAlert(alert)) {
            throw new BusinessException(404, "System alert not found: " + id);
        }
        recordAudit("ACKNOWLEDGE_SYSTEM_ALERT", "SYSTEM_ALERT", alert.getId(), null,
                "acknowledged=true");
        return alert;
    }


    public int clearAcknowledged() {
        log.info("Clearing acknowledged system alerts");
        int deleted = alertRepository.deleteAcknowledgedAlerts();
        recordAudit("CLEAR_ACKNOWLEDGED_SYSTEM_ALERTS", "SYSTEM_ALERT", null, null,
                "deleted=" + deleted);
        return deleted;
    }

    private List<PrometheusAlertRule> defaultPrometheusRules() {
        return alertRuleAssetService.loadDefaultRules();
    }

    private PrometheusAlertRule toPrometheusRule(AlertRuleVO rule) {
        String team = inferTeam(rule.getMetric());
        return new PrometheusAlertRule(
                groupName(team),
                alertName(rule),
                expression(rule),
                duration(rule),
                severity(rule),
                team,
                summary(rule),
                description(rule));
    }

    private String groupName(String team) {
        if ("client".equals(team)) {
            return "rocketmq-client.rules";
        }
        if ("consumer".equals(team)) {
            return "rocketmq-consumer.rules";
        }
        if ("topic".equals(team)) {
            return "rocketmq-topic.rules";
        }
        return "rocketmq-broker.rules";
    }

    private String ensureUniqueAlertName(String baseName, Set<String> usedAlertNames) {
        String uniqueName = baseName;
        int suffix = 2;
        while (!usedAlertNames.add(uniqueName)) {
            uniqueName = baseName + "_" + suffix++;
        }
        return uniqueName;
    }

    private String alertName(AlertRuleVO rule) {
        String alertName = hasText(rule.getName()) ? rule.getName().replaceAll("[^A-Za-z0-9_]", "") : "";
        if (alertName.isEmpty()) {
            return "RocketMQAlert";
        }
        // Prometheus alert names must start with [a-zA-Z_], not a digit
        if (Character.isDigit(alertName.charAt(0))) {
            alertName = "A_" + alertName;
        }
        return alertName;
    }

    private String expression(AlertRuleVO rule) {
        String metric = validateMetric(rule.getMetric());
        String operator = validateOperator(rule.getOperator());
        return metric + labelSelector(rule) + " " + operator + " " + formatThreshold(rule.getThreshold());
    }

    private String validateMetric(String metric) {
        String normalized = hasText(metric) ? metric.trim() : "rocketmq_consumer_lag_messages";
        return METRIC_NAME_PATTERN.matcher(normalized).matches() ? normalized : "rocketmq_consumer_lag_messages";
    }

    private String validateOperator(String operator) {
        String normalized = hasText(operator) ? operator.trim() : ">";
        return VALID_OPERATORS.contains(normalized) ? normalized : ">";
    }

    private String labelSelector(AlertRuleVO rule) {
        StringBuilder selector = new StringBuilder();
        appendLabel(selector, "cluster", rule.getClusterName());
        appendLabel(selector, "broker", rule.getBrokerName());
        return selector.isEmpty() ? "" : "{" + selector + "}";
    }

    private void appendLabel(StringBuilder selector, String label, String value) {
        if (!hasText(value) || "*".equals(value.trim())) {
            return;
        }
        if (!selector.isEmpty()) {
            selector.append(',');
        }
        selector.append(label).append("=\"").append(escapeDoubleQuotedValue(value.trim())).append('"');
    }

    private void auditRule(String operation, AlertRuleVO rule, String detail) {
        String auditDetail = detail == null ? "name=" + rule.getName() : detail;
        recordAudit(operation, "ALERT_RULE", rule.getId(), null,
                auditDetail);
    }

    private String severity(AlertRuleVO rule) {
        String severity = rule.getSeverity();
        if (hasText(severity)) {
            String normalized = severity.trim().toLowerCase(Locale.ROOT);
            if ("critical".equals(normalized) || "warning".equals(normalized) || "info".equals(normalized)) {
                return normalized;
            }
        }
        return "warning";
    }

    private String formatThreshold(double threshold) {
        if (!Double.isFinite(threshold)) {
            return "0";
        }
        if (threshold == Math.rint(threshold)) {
            return Long.toString((long) threshold);
        }
        return Double.toString(threshold);
    }

    private String duration(AlertRuleVO rule) {
        String dur = hasText(rule.getDuration()) ? rule.getDuration().trim() : "5m";
        return DURATION_PATTERN.matcher(dur).matches() ? dur : "5m";
    }

    private String inferTeam(String metric) {
        if (!hasText(metric)) {
            return "broker";
        }
        String normalizedMetric = metric.toLowerCase(Locale.ROOT);
        if (normalizedMetric.contains("replication") || normalizedMetric.contains("fall_behind")
                || normalizedMetric.contains("slave")) {
            return "broker";
        }
        if (normalizedMetric.contains("consumer") || normalizedMetric.contains("lag")) {
            return "consumer";
        }
        if (normalizedMetric.contains("producer") || normalizedMetric.contains("client")) {
            return "client";
        }
        if (normalizedMetric.contains("topic") || normalizedMetric.contains("messages_in")
                || normalizedMetric.contains("messages_out")) {
            return "topic";
        }
        return "broker";
    }

    private String summary(AlertRuleVO rule) {
        String description = rule.getDescription();
        if (hasText(description) && description.contains(" - ")) {
            String candidate = description.substring(0, description.indexOf(" - "));
            if (hasText(candidate)) {
                return candidate;
            }
        }
        return hasText(rule.getName()) ? rule.getName() : "RocketMQ alert";
    }

    private String description(AlertRuleVO rule) {
        String description = rule.getDescription();
        if (hasText(description) && description.contains(" - ")) {
            return description.substring(description.indexOf(" - ") + 3);
        }
        return hasText(description) ? description : "RocketMQ alert condition matched.";
    }

    private String escapeDoubleQuotedValue(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void validateRuleId(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException(400, "Alert rule ID is required");
        }
    }

    private BusinessException ruleNotFound(String id) {
        return new BusinessException(404, "Alert rule not found: " + id);
    }

    private void recordAudit(String operation, String resourceType, String resourceName,
                             String clusterId, String detail) {
        try {
            operationAuditService.record(operation, resourceType, resourceName, clusterId, detail, "SUCCESS", null);
        } catch (Exception auditFailure) {
            log.warn("Failed to record audit operation={} resource={}: {}", operation, resourceName,
                    auditFailure.getMessage());
        }
    }

}
