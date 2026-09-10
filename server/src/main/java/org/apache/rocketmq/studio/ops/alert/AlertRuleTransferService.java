/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AlertRuleTransferService {
    private static final int MAX_RULES_PER_IMPORT = 200;

    private final AlertService alertService;
    private final NativeAlertMetricCatalogService metricCatalogService;
    private final Validator validator;

    public AlertRuleTransferDTO exportRules(AlertDomain domain) {
        AlertRuleTransferDTO transfer = new AlertRuleTransferDTO();
        transfer.setVersion(AlertRuleTransferDTO.VERSION);
        transfer.setDomain(domain);
        transfer.setRules(alertService.listRules(domain).stream().map(this::toRequest).toList());
        return transfer;
    }

    public AlertRuleImportPreviewVO previewRules(AlertDomain domain, AlertRuleTransferDTO transfer) {
        return buildPlan(domain, transfer).preview();
    }

    @Transactional
    public AlertRuleImportResultVO applyRules(AlertDomain domain, AlertRuleImportApplyDTO request) {
        if (request == null || request.getTransfer() == null) {
            throw new BusinessException(400, "Alert rule import request is required");
        }
        if (request.getStrategy() == null) {
            throw new BusinessException(400, "Alert rule import conflict strategy is required");
        }
        ImportPlan plan = buildPlan(domain, request.getTransfer());
        if (plan.invalidCount() > 0) {
            throw new BusinessException(400,
                    "Alert rule import contains invalid rows; preview and correct the document before applying");
        }
        if (request.getStrategy() == AlertRuleImportConflictStrategy.FAIL && plan.duplicateCount() > 0) {
            throw new BusinessException(409,
                    "Alert rule import contains duplicate evaluation conditions");
        }

        List<AlertRuleVO> changed = new ArrayList<>();
        int created = 0;
        int replaced = 0;
        int skipped = 0;
        for (ImportPlanItem item : plan.items()) {
            if (item.status() == AlertRuleImportPreviewItemVO.Status.NEW) {
                changed.add(alertService.createRule(domain, item.candidate()));
                created++;
                continue;
            }
            if (request.getStrategy() == AlertRuleImportConflictStrategy.SKIP) {
                skipped++;
                continue;
            }
            AlertRuleVO replacement = item.candidate();
            replacement.setId(item.existing().getId());
            changed.add(alertService.updateRule(domain, replacement));
            replaced++;
        }
        return new AlertRuleImportResultVO(request.getStrategy(), created, replaced, skipped,
                List.copyOf(changed));
    }

    @Transactional
    public List<AlertRuleVO> importRules(AlertDomain domain, AlertRuleTransferDTO transfer) {
        AlertRuleImportApplyDTO request = new AlertRuleImportApplyDTO();
        request.setTransfer(transfer);
        request.setStrategy(AlertRuleImportConflictStrategy.FAIL);
        return applyRules(domain, request).changedRules();
    }

    private ImportPlan buildPlan(AlertDomain domain, AlertRuleTransferDTO transfer) {
        validateEnvelope(domain, transfer);
        Map<String, AlertRuleVO> existingByFingerprint = new LinkedHashMap<>();
        List<AlertRuleVO> existingRules = alertService.listRules(domain);
        if (existingRules != null) {
            for (AlertRuleVO existing : existingRules) {
                existingByFingerprint.putIfAbsent(AlertRuleSemanticFingerprint.of(existing), existing);
            }
        }

        Map<String, Integer> importedFingerprintRows = new HashMap<>();
        List<ImportPlanItem> items = new ArrayList<>();
        for (int index = 0; index < transfer.getRules().size(); index++) {
            int rowNumber = index + 1;
            AlertRuleRequestDTO rule = transfer.getRules().get(index);
            String validationError = validateRequest(rule);
            if (validationError != null) {
                items.add(ImportPlanItem.invalid(rowNumber, rule, validationError));
                continue;
            }

            AlertRuleVO candidate = rule.toAlertRuleVO();
            candidate.setId(null);
            candidate.setDomain(domain);
            candidate.setName(candidate.getName().trim());
            try {
                NativeAlertRulePolicy.validate(candidate);
                metricCatalogService.validate(candidate);
            } catch (BusinessException error) {
                items.add(ImportPlanItem.invalid(rowNumber, rule, error.getMessage()));
                continue;
            }

            String fingerprint = AlertRuleSemanticFingerprint.of(candidate);
            Integer firstRow = importedFingerprintRows.putIfAbsent(fingerprint, rowNumber);
            if (firstRow != null) {
                items.add(ImportPlanItem.invalid(rowNumber, rule,
                        "Duplicate evaluation conditions within import document; matches row " + firstRow));
                continue;
            }
            AlertRuleVO existing = existingByFingerprint.get(fingerprint);
            items.add(existing == null
                    ? ImportPlanItem.fresh(rowNumber, candidate)
                    : ImportPlanItem.duplicate(rowNumber, candidate, existing));
        }
        return new ImportPlan(List.copyOf(items));
    }

    private String validateRequest(AlertRuleRequestDTO request) {
        if (request == null) {
            return "rule: must not be null";
        }
        Set<ConstraintViolation<AlertRuleRequestDTO>> violations = validator.validate(request);
        if (violations.isEmpty()) {
            return null;
        }
        return violations.stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .reduce((left, right) -> left + "; " + right)
                .orElse("Invalid alert rule");
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

    private record ImportPlan(List<ImportPlanItem> items) {
        private int count(AlertRuleImportPreviewItemVO.Status status) {
            return Math.toIntExact(items.stream().filter(item -> item.status() == status).count());
        }

        private int invalidCount() {
            return count(AlertRuleImportPreviewItemVO.Status.INVALID);
        }

        private int duplicateCount() {
            return count(AlertRuleImportPreviewItemVO.Status.DUPLICATE);
        }

        private AlertRuleImportPreviewVO preview() {
            return new AlertRuleImportPreviewVO(items.size(),
                    count(AlertRuleImportPreviewItemVO.Status.NEW), duplicateCount(), invalidCount(),
                    items.stream().map(ImportPlanItem::preview).toList());
        }
    }

    private record ImportPlanItem(
            int rowNumber,
            AlertRuleImportPreviewItemVO.Status status,
            AlertRuleVO candidate,
            AlertRuleVO existing,
            String error) {

        private static ImportPlanItem fresh(int rowNumber, AlertRuleVO candidate) {
            return new ImportPlanItem(rowNumber, AlertRuleImportPreviewItemVO.Status.NEW,
                    candidate, null, null);
        }

        private static ImportPlanItem duplicate(int rowNumber, AlertRuleVO candidate, AlertRuleVO existing) {
            return new ImportPlanItem(rowNumber, AlertRuleImportPreviewItemVO.Status.DUPLICATE,
                    candidate, existing, null);
        }

        private static ImportPlanItem invalid(int rowNumber, AlertRuleRequestDTO request, String error) {
            AlertRuleVO candidate = request == null ? null : request.toAlertRuleVO();
            return new ImportPlanItem(rowNumber, AlertRuleImportPreviewItemVO.Status.INVALID,
                    candidate, null, error);
        }

        private AlertRuleImportPreviewItemVO preview() {
            return new AlertRuleImportPreviewItemVO(rowNumber, status,
                    candidate == null ? null : candidate.getName(),
                    candidate == null ? null : candidate.getMetric(),
                    existing == null ? null : existing.getId(),
                    existing == null ? null : existing.getName(), error);
        }
    }
}
