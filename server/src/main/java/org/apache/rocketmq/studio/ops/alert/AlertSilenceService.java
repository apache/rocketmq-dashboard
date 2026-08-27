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
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AlertSilenceService {
    private final AlertSilenceRepository repository;
    private final OperationAuditService operationAuditService;

    public List<AlertSilenceVO> list() {
        return repository.findAll();
    }

    public AlertSilenceVO create(CreateAlertSilenceDTO request) {
        if (request == null || request.getStartsAt() == null || request.getEndsAt() == null) {
            throw new BusinessException(400, "Silence start and end times are required");
        }
        if (!request.getEndsAt().toInstant().isAfter(request.getStartsAt().toInstant())) {
            throw new BusinessException(400, "Silence end time must be after start time");
        }
        if (request.getReason() != null && request.getReason().length() > 512) {
            throw new BusinessException(400, "Silence reason must not exceed 512 characters");
        }
        AlertSilenceVO silence = AlertSilenceVO.builder().domain(request.getDomain())
                .ruleId(request.getRuleId()).instanceId(trimToNull(request.getInstanceId()))
                .labels(normalizeLabels(request.getLabels()))
                .startsAt(LocalDateTime.ofInstant(request.getStartsAt().toInstant(), ZoneOffset.UTC))
                .endsAt(LocalDateTime.ofInstant(request.getEndsAt().toInstant(), ZoneOffset.UTC))
                .reason(trimToNull(request.getReason()))
                .createdBy(AuthenticatedUserContext.currentUsernameOrSystem()).build();
        AlertSilenceVO saved = repository.save(silence);
        operationAuditService.record("CREATE_ALERT_SILENCE", "ALERT_SILENCE", String.valueOf(saved.getId()),
                saved.getInstanceId(), "ruleId=" + saved.getRuleId(), "SUCCESS", null);
        return saved;
    }

    public void delete(Long id) {
        if (id == null) {
            throw new BusinessException(400, "Silence ID is required");
        }
        if (!repository.deleteById(id)) {
            throw new BusinessException(404, "Alert silence not found: " + id);
        }
        operationAuditService.record("DELETE_ALERT_SILENCE", "ALERT_SILENCE", String.valueOf(id), null, null,
                "SUCCESS", null);
    }

    public boolean isActive(AlertRuleVO rule, String instanceId, LocalDateTime now) {
        return isActive(rule, instanceId, Map.of(), now);
    }

    public boolean isActive(AlertRuleVO rule, String instanceId, Map<String, String> labels, LocalDateTime now) {
        return activeUntil(rule, instanceId, labels, now) != null;
    }

    /**
     * Returns the end of the currently matching maintenance window, or {@code null} when delivery is allowed.
     * Overlapping matching windows suppress delivery until the last one ends.
     */
    public LocalDateTime activeUntil(AlertRuleVO rule, String instanceId, Map<String, String> labels,
            LocalDateTime now) {
        AlertDomain domain = rule.getDomain() == null ? AlertDomain.BUSINESS : rule.getDomain();
        return repository.findAll().stream()
                .filter(silence -> matches(silence, rule.getId(), domain, instanceId,
                        labels == null ? Map.of() : labels, now))
                .map(AlertSilenceVO::getEndsAt).max(LocalDateTime::compareTo).orElse(null);
    }

    private static boolean matches(AlertSilenceVO silence, Long ruleId, AlertDomain domain, String instanceId,
            Map<String, String> labels, LocalDateTime now) {
        return !now.isBefore(silence.getStartsAt()) && now.isBefore(silence.getEndsAt())
                && (silence.getDomain() == null || silence.getDomain() == domain)
                && (silence.getRuleId() == null || silence.getRuleId().equals(ruleId))
                && (silence.getInstanceId() == null || silence.getInstanceId().equals(instanceId))
                && (silence.getLabels() == null || labels.entrySet().containsAll(silence.getLabels().entrySet()));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, String> normalizeLabels(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) return Map.of();
        if (labels.size() > 16) throw new BusinessException(400, "At most 16 silence labels are supported");
        Map<String, String> normalized = new LinkedHashMap<>();
        labels.forEach((key, value) -> {
            String normalizedKey = trimToNull(key);
            String normalizedValue = trimToNull(value);
            if (normalizedKey == null || normalizedValue == null || normalizedKey.length() > 128
                    || normalizedValue.length() > 512) {
                throw new BusinessException(400, "Silence labels must have non-empty bounded keys and values");
            }
            normalized.put(normalizedKey, normalizedValue);
        });
        return Map.copyOf(normalized);
    }
}
