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
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private static final Set<String> VALID_OPERATORS = Set.of(">", ">=", "<", "<=", "==", "!=", "UNAVAILABLE");
    private static final Pattern METRIC_NAME_PATTERN = Pattern.compile("^[a-zA-Z_:][a-zA-Z0-9_:]*$");
    private static final Pattern DURATION_PATTERN = Pattern.compile("^\\d+(ms|s|m|h|d|w|y)$");

    private final AlertRepository alertRepository;
    private final AlertStateRepository alertStateRepository;
    private final AlertRuleAssetService alertRuleAssetService;
    private final OperationAuditService operationAuditService;


    public List<AlertRuleVO> listRules() {
        log.info("Listing all alert rules");
        return alertRepository.findAllRules();
    }

    public PageResult<AlertRuleVO> listRules(String search, Boolean enabled, int page,
                                             int pageSize) {
        validateRulePagination(page, pageSize);
        String normalizedSearch = StringUtils.hasText(search) ? search.trim() : null;
        log.info("Listing alert rules, search={}, enabled={}, page={}, pageSize={}",
                normalizedSearch, enabled, page, pageSize);
        return alertRepository.findRulePage(normalizedSearch, enabled, page, pageSize);
    }

    public List<AlertRuleVO> listRules(AlertDomain domain) {
        return listRules().stream()
                .filter(rule -> domain == resolveDomain(rule))
                .toList();
    }

    public PageResult<AlertRuleVO> listRules(AlertDomain domain, String search, Boolean enabled, int page,
            int pageSize) {
        requireDomain(domain);
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "Invalid page or pageSize");
        }
        return alertRepository.findRulesPage(new AlertRuleQuery(domain,
                hasText(search) ? search.trim() : null, enabled, page, pageSize));
    }

    public List<AlertRuleRuntimeVO> listRuleRuntime(AlertDomain domain) {
        return alertStateRepository.findRuntimeByRuleIds(listRules(domain));
    }

    public String exportPrometheusRulesYaml() {
        List<AlertRuleVO> rules = alertRepository.findAllRules().stream()
                .filter(AlertRuleVO::isEnabled)
                .filter(rule -> resolveDomain(rule) == AlertDomain.BUSINESS)
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
        if (rule.getId() != null) {
            throw new BusinessException(400, "Alert rule ID must not be provided when creating a rule");
        }
        if (!hasText(rule.getName())) {
            throw new BusinessException(400, "Alert rule name is required");
        }
        rule.setName(rule.getName().trim());
        NativeAlertRulePolicy.validate(rule);
        rejectDuplicateSemanticRule(rule, null);
        log.info("Creating alert rule: {}", rule.getName());
        AlertRuleVO saved = saveNewRule(rule);
        auditRule("CREATE_ALERT_RULE", saved, null);
        return saved;
    }

    public AlertRuleVO createRule(AlertDomain domain, AlertRuleVO rule) {
        requireDomain(domain);
        if (rule == null) {
            return createRule(null);
        }
        rule.setDomain(domain);
        return createRule(rule);
    }


    @Transactional
    public AlertRuleVO updateRule(AlertRuleVO rule) {
        if (rule == null) {
            throw new BusinessException(400, "Alert rule request is required");
        }
        if (!hasText(rule.getName())) {
            throw new BusinessException(400, "Alert rule name is required");
        }
        rule.setName(rule.getName().trim());
        Long id = rule.getId();
        log.info("Updating alert rule: {}", id);
        validateRuleId(id);
        NativeAlertRulePolicy.validate(rule);
        rejectDuplicateSemanticRule(rule, id);
        if (!replaceRuleWithoutDuplicate(rule)) {
            throw ruleNotFound(id);
        }
        alertStateRepository.deleteByRuleId(id);
        auditRule("UPDATE_ALERT_RULE", rule, null);
        return rule;
    }

    @Transactional
    public AlertRuleVO updateRule(AlertDomain domain, AlertRuleVO rule) {
        requireDomain(domain);
        validateRuleId(rule == null ? null : rule.getId());
        AlertRuleVO existing = alertRepository.findRuleById(rule.getId())
                .orElseThrow(() -> ruleNotFound(rule.getId()));
        if (resolveDomain(existing) != domain) {
            throw new BusinessException(404, "Alert rule not found: " + rule.getId());
        }
        rule.setDomain(domain);
        return updateRule(rule);
    }

    private AlertDomain resolveDomain(AlertRuleVO rule) {
        return rule.getDomain() == null ? AlertDomain.BUSINESS : rule.getDomain();
    }

    private void rejectDuplicateSemanticRule(AlertRuleVO rule, Long excludedId) {
        String fingerprint = AlertRuleSemanticFingerprint.of(rule);
        boolean duplicate = alertRepository.findAllRules().stream()
                .filter(candidate -> !Objects.equals(candidate.getId(), excludedId))
                .anyMatch(candidate -> AlertRuleSemanticFingerprint.of(candidate).equals(fingerprint));
        if (duplicate) {
            throw new BusinessException(409, "An alert rule with the same evaluation conditions already exists");
        }
    }

    private AlertRuleVO saveNewRule(AlertRuleVO rule) {
        try {
            return alertRepository.insertRule(rule);
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(409, "An alert rule with the same evaluation conditions already exists");
        }
    }

    private boolean replaceRuleWithoutDuplicate(AlertRuleVO rule) {
        try {
            return alertRepository.replaceRule(rule);
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(409, "An alert rule with the same evaluation conditions already exists");
        }
    }

    private void requireDomain(AlertDomain domain) {
        if (domain == null) {
            throw new BusinessException(400, "Alert domain is required");
        }
    }


    @Transactional
    public AlertRuleVO toggleRule(Long id, boolean enabled) {
        log.info("Toggling alert rule id={}, enabled={}", id, enabled);
        validateRuleId(id);
        AlertRuleVO rule = alertRepository.findRuleById(id)
                .orElseThrow(() -> new org.apache.rocketmq.studio.common.exception.BusinessException(404, "Alert rule not found: " + id));
        rule.setEnabled(enabled);
        if (!alertRepository.replaceRule(rule)) {
            throw ruleNotFound(id);
        }
        alertStateRepository.deleteByRuleId(id);
        auditRule("TOGGLE_ALERT_RULE", rule, "enabled=" + enabled);
        return rule;
    }

    @Transactional
    public AlertRuleVO toggleRule(AlertDomain domain, Long id, boolean enabled) {
        requireDomain(domain);
        AlertRuleVO rule = findRuleInDomain(domain, id);
        rule.setEnabled(enabled);
        if (!alertRepository.replaceRule(rule)) {
            throw ruleNotFound(id);
        }
        alertStateRepository.deleteByRuleId(id);
        auditRule("TOGGLE_ALERT_RULE", rule, "enabled=" + enabled);
        return rule;
    }


    @Transactional
    public void deleteRule(Long id) {
        log.info("Deleting alert rule id={}", id);
        validateRuleId(id);
        if (!alertRepository.deleteRule(id)) {
            throw ruleNotFound(id);
        }
        alertStateRepository.deleteByRuleId(id);
        recordAudit("DELETE_ALERT_RULE", "ALERT_RULE", String.valueOf(id), null, null);
    }

    @Transactional
    public void deleteRule(AlertDomain domain, Long id) {
        requireDomain(domain);
        findRuleInDomain(domain, id);
        if (!alertRepository.deleteRule(id)) {
            throw ruleNotFound(id);
        }
        alertStateRepository.deleteByRuleId(id);
        recordAudit("DELETE_ALERT_RULE", "ALERT_RULE", String.valueOf(id), null, null);
    }

    @Transactional
    public AlertRuleBulkResultVO bulkToggleRules(List<Long> ids, boolean enabled) {
        List<Long> normalizedIds = normalizeBulkIds(ids);
        Map<Long, AlertRuleVO> rulesById = rulesById(normalizedIds);
        List<Long> succeeded = new ArrayList<>();
        Map<Long, String> failures = new LinkedHashMap<>();
        List<AlertRuleVO> updated = new ArrayList<>();
        for (Long id : normalizedIds) {
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
                alertStateRepository.deleteByRuleId(id);
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

    @Transactional
    public AlertRuleBulkResultVO bulkToggleRules(AlertDomain domain, List<Long> ids, boolean enabled) {
        requireDomain(domain);
        return bulkUpdateRules(domain, ids, rule -> rule.setEnabled(enabled),
                "TOGGLE_ALERT_RULE", "enabled=" + enabled + ", bulk=true");
    }

    @Transactional
    public AlertRuleBulkResultVO bulkDeleteRules(List<Long> ids) {
        List<Long> normalizedIds = normalizeBulkIds(ids);
        List<Long> succeeded = new ArrayList<>();
        Map<Long, String> failures = new LinkedHashMap<>();
        for (Long id : normalizedIds) {
            try {
                if (!alertRepository.deleteRule(id)) {
                    failures.put(id, "Alert rule not found");
                    continue;
                }
                alertStateRepository.deleteByRuleId(id);
                recordAudit("DELETE_ALERT_RULE", "ALERT_RULE", String.valueOf(id), null, "bulk=true");
                succeeded.add(id);
            } catch (RuntimeException failure) {
                failures.put(id, failure.getMessage() == null ? "Delete failed" : failure.getMessage());
            }
        }
        return AlertRuleBulkResultVO.builder()
                .succeededIds(succeeded).failures(failures).updatedRules(List.of()).build();
    }

    @Transactional
    public AlertRuleBulkResultVO bulkDeleteRules(AlertDomain domain, List<Long> ids) {
        requireDomain(domain);
        List<Long> normalizedIds = normalizeBulkIds(ids);
        Map<Long, AlertRuleVO> rulesById = rulesById(normalizedIds);
        List<Long> succeeded = new ArrayList<>();
        Map<Long, String> failures = new LinkedHashMap<>();
        for (Long id : normalizedIds) {
            AlertRuleVO rule = rulesById.get(id);
            if (rule == null || resolveDomain(rule) != domain) {
                failures.put(id, "Alert rule not found");
                continue;
            }
            try {
                if (!alertRepository.deleteRule(id)) {
                    failures.put(id, "Alert rule not found");
                    continue;
                }
                alertStateRepository.deleteByRuleId(id);
                recordAudit("DELETE_ALERT_RULE", "ALERT_RULE", String.valueOf(id), null, "bulk=true");
                succeeded.add(id);
            } catch (RuntimeException failure) {
                failures.put(id, failure.getMessage() == null ? "Delete failed" : failure.getMessage());
            }
        }
        return AlertRuleBulkResultVO.builder()
                .succeededIds(succeeded).failures(failures).updatedRules(List.of()).build();
    }

    private AlertRuleBulkResultVO bulkUpdateRules(AlertDomain domain, List<Long> ids,
            java.util.function.Consumer<AlertRuleVO> update, String auditOperation, String auditDetail) {
        List<Long> normalizedIds = normalizeBulkIds(ids);
        Map<Long, AlertRuleVO> rulesById = rulesById(normalizedIds);
        List<Long> succeeded = new ArrayList<>();
        Map<Long, String> failures = new LinkedHashMap<>();
        List<AlertRuleVO> updated = new ArrayList<>();
        for (Long id : normalizedIds) {
            AlertRuleVO rule = rulesById.get(id);
            if (rule == null || resolveDomain(rule) != domain) {
                failures.put(id, "Alert rule not found");
                continue;
            }
            try {
                update.accept(rule);
                if (!alertRepository.replaceRule(rule)) {
                    failures.put(id, "Alert rule not found");
                    continue;
                }
                alertStateRepository.deleteByRuleId(id);
                auditRule(auditOperation, rule, auditDetail);
                succeeded.add(id);
                updated.add(rule);
            } catch (RuntimeException failure) {
                failures.put(id, failure.getMessage() == null ? "Update failed" : failure.getMessage());
            }
        }
        return AlertRuleBulkResultVO.builder()
                .succeededIds(succeeded).failures(failures).updatedRules(updated).build();
    }

    private AlertRuleVO findRuleInDomain(AlertDomain domain, Long id) {
        validateRuleId(id);
        return alertRepository.findRuleById(id)
                .filter(rule -> resolveDomain(rule) == domain)
                .orElseThrow(() -> ruleNotFound(id));
    }

    private Map<Long, AlertRuleVO> rulesById(List<Long> ids) {
        Map<Long, AlertRuleVO> rulesById = new LinkedHashMap<>();
        for (AlertRuleVO rule : alertRepository.findRulesByIds(ids)) {
            if (rule.getId() != null) {
                rulesById.put(rule.getId(), rule);
            }
        }
        return rulesById;
    }

    private List<Long> normalizeBulkIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "ids are required");
        }
        Set<Long> seen = new HashSet<>();
        List<Long> normalized = new ArrayList<>();
        for (Long id : ids) {
            validateRuleId(id);
            if (seen.add(id)) {
                normalized.add(id);
            }
        }
        return normalized;
    }

    private static void validateRulePagination(int page, int pageSize) {
        if (page < 1) {
            throw new BusinessException(400, "page must be greater than zero");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "pageSize must be between 1 and 100");
        }
    }


    public List<SystemAlertVO> listAlerts(String level) {
        log.info("Listing system alerts, level={}", level);
        return alertRepository.findAlerts(level);
    }

    public PageResult<SystemAlertVO> listAlerts(String level, int page, int pageSize) {
        validateAlertPagination(page, pageSize);
        String normalizedLevel = StringUtils.hasText(level) ? level.trim() : level;
        log.info("Listing system alerts, level={}, page={}, pageSize={}",
                normalizedLevel, page, pageSize);
        return alertRepository.findAlerts(normalizedLevel, page, pageSize);
    }

    public List<SystemAlertVO> listAlerts(String level, AlertDomain domain, String instanceId, String transition) {
        return listAlerts(level).stream()
                .filter(alert -> domain == null || domain == alert.getDomain())
                .filter(alert -> !hasText(instanceId) || instanceId.trim().equals(alert.getInstanceId()))
                .filter(alert -> !hasText(transition) || transition.trim().equalsIgnoreCase(alert.getTransition()))
                .toList();
    }

    public PageResult<SystemAlertVO> listAlerts(String level, AlertDomain domain, String instanceId,
            String transition, int page, int pageSize) {
        return listAlerts(level, domain, instanceId, transition, null, null, null, null, page, pageSize);
    }

    public PageResult<SystemAlertVO> listAlerts(String level, AlertDomain domain, String instanceId,
            String transition, String labelKey, String labelValue, LocalDateTime from, LocalDateTime to,
            int page, int pageSize) {
        return listAlerts(level, domain, instanceId, transition, labelKey, labelValue, from, to, page, pageSize, null);
    }

    public PageResult<SystemAlertVO> listAlerts(String level, AlertDomain domain, String instanceId,
            String transition, String labelKey, String labelValue, LocalDateTime from, LocalDateTime to,
            int page, int pageSize, Boolean notificationSuppressed) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "Invalid page or pageSize");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(400, "from must not be after to");
        }
        if (hasText(labelKey) != hasText(labelValue)) {
            throw new BusinessException(400, "labelKey and labelValue must be provided together");
        }
        return alertRepository.findAlertsPage(new SystemAlertQuery(level, domain, instanceId, transition,
                hasText(labelKey) ? labelKey.trim() : null, hasText(labelValue) ? labelValue.trim() : null,
                from, to, page, pageSize, notificationSuppressed));
    }

    public List<SystemAlertVO> findRelatedAlerts(Long id) {
        if (id == null) {
            throw new BusinessException(400, "System alert ID is required");
        }
        SystemAlertVO source = alertRepository.findAlertById(id)
                .orElseThrow(() -> new BusinessException(404, "System alert not found: " + id));
        AlertDomain relatedDomain = (source.getDomain() == null ? AlertDomain.BUSINESS : source.getDomain())
                == AlertDomain.BUSINESS
                ? AlertDomain.CLUSTER : AlertDomain.BUSINESS;
        List<SystemAlertVO> explicitCauses = source.getSuppressionCauseAlertId() == null
                ? List.of()
                : alertRepository.findAlertById(source.getSuppressionCauseAlertId())
                        .stream()
                        .filter(candidate -> candidate.getDomain() == relatedDomain)
                        .filter(candidate -> AlertCorrelationScope.matches(source, candidate))
                        .toList();
        if (!hasText(source.getInstanceId()) || source.getTime() == null) {
            return explicitCauses;
        }
        LocalDateTime from = source.getTime().minusMinutes(30);
        LocalDateTime to = source.getTime().plusMinutes(30);
        List<SystemAlertVO> windowMatches = alertRepository.findAlertsPage(new SystemAlertQuery(null, relatedDomain, source.getInstanceId(),
                        "FIRING", null, null, from, to, 1, 100))
                .getItems().stream()
                .filter(candidate -> !Objects.equals(candidate.getId(), source.getId()))
                .filter(candidate -> AlertCorrelationScope.matches(source, candidate))
                .toList();
        LinkedHashMap<Long, SystemAlertVO> related = new LinkedHashMap<>();
        explicitCauses.forEach(candidate -> related.put(candidate.getId(), candidate));
        windowMatches.forEach(candidate -> related.putIfAbsent(candidate.getId(), candidate));
        return List.copyOf(related.values());
    }


    public SystemAlertVO acknowledgeAlert(Long id) {
        log.info("Acknowledging system alert id={}", id);
        if (id == null) {
            throw new BusinessException(400, "System alert ID is required");
        }
        SystemAlertVO alert = alertRepository.findAlertById(id)
                .orElseThrow(() -> new org.apache.rocketmq.studio.common.exception.BusinessException(404,
                        "System alert not found: " + id));
        alert.setAcknowledged(true);
        alert.setAcknowledgedBy(AuthenticatedUserContext.currentUsernameOrSystem());
        alert.setAcknowledgedAt(LocalDateTime.now(ZoneOffset.UTC));
        if (!alertRepository.acknowledgeAlert(alert)) {
            throw new BusinessException(404, "System alert not found: " + id);
        }
        if ("FIRING".equalsIgnoreCase(alert.getTransition())
                && alert.getRuleId() != null && hasText(alert.getFingerprint()) && alert.getTime() != null) {
            alertStateRepository.acknowledge(new AlertStateKey(alert.getRuleId(), alert.getFingerprint()),
                    alert.getTime().toInstant(ZoneOffset.UTC));
        }
        recordAudit("ACKNOWLEDGE_SYSTEM_ALERT", "SYSTEM_ALERT", String.valueOf(alert.getId()), null,
                "acknowledged=true");
        return alert;
    }


    @Transactional
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

    private static void validateAlertPagination(int page, int pageSize) {
        if (page < 1) {
            throw new BusinessException(400, "page must be greater than 0");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "pageSize must be between 1 and 100");
        }
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
        if ("UNAVAILABLE".equals(operator)) {
            // Prometheus has no availability enum. Its compatible exporter convention is zero for
            // unavailable targets; native Studio evaluation keeps failed samples value-less.
            return metric + labelSelector(rule) + " == 0";
        }
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
        recordAudit(operation, "ALERT_RULE", String.valueOf(rule.getId()), null,
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

    private void validateRuleId(Long id) {
        if (id == null) {
            throw new BusinessException(400, "Alert rule ID is required");
        }
    }

    private BusinessException ruleNotFound(Long id) {
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
