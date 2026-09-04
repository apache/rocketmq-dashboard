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
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class AlertSilenceService {
    private static final int MAX_PAGE_SIZE = 100;

    private final AlertSilenceRepository repository;
    private final OperationAuditService operationAuditService;

    public List<AlertSilenceVO> list() {
        return repository.findAll();
    }

    public PageResult<AlertSilenceVO> listPage(int page, int pageSize) {
        validatePagination(page, pageSize);
        return repository.findPage(page, pageSize);
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
        RecurrenceConfiguration recurrence = validateRecurrence(request);
        AlertSilenceVO silence = AlertSilenceVO.builder().domain(request.getDomain())
                .ruleId(request.getRuleId()).instanceId(trimToNull(request.getInstanceId()))
                .labels(normalizeLabels(request.getLabels()))
                .startsAt(LocalDateTime.ofInstant(request.getStartsAt().toInstant(), ZoneOffset.UTC))
                .endsAt(LocalDateTime.ofInstant(request.getEndsAt().toInstant(), ZoneOffset.UTC))
                .recurrence(recurrence.type()).timeZone(recurrence.timeZone())
                .recurrenceDays(recurrence.days()).recurrenceUntil(recurrence.until())
                .reason(trimToNull(request.getReason()))
                .createdBy(AuthenticatedUserContext.currentUsernameOrSystem()).build();
        AlertSilenceVO saved = repository.save(silence);
        operationAuditService.record("CREATE_ALERT_SILENCE", "ALERT_SILENCE", String.valueOf(saved.getId()),
                saved.getInstanceId(), "ruleId=" + saved.getRuleId() + ", recurrence=" + saved.getRecurrence(),
                "SUCCESS", null);
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
        return repository.findActiveCandidates(domain, rule.getId(), instanceId, now).stream()
                .filter(silence -> matchesScope(silence, rule.getId(), domain, instanceId,
                        labels == null ? Map.of() : labels))
                .map(silence -> AlertSilenceSchedule.activeUntil(silence, now))
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo).orElse(null);
    }

    private static void validatePagination(int page, int pageSize) {
        if (page < 1) {
            throw new BusinessException(400, "page must be positive");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(400, "pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private static boolean matchesScope(AlertSilenceVO silence, Long ruleId, AlertDomain domain, String instanceId,
            Map<String, String> labels) {
        return (silence.getDomain() == null || silence.getDomain() == domain)
                && (silence.getRuleId() == null || silence.getRuleId().equals(ruleId))
                && (silence.getInstanceId() == null || silence.getInstanceId().equals(instanceId))
                && (silence.getLabels() == null || labels.entrySet().containsAll(silence.getLabels().entrySet()));
    }

    private static RecurrenceConfiguration validateRecurrence(CreateAlertSilenceDTO request) {
        AlertSilenceRecurrence recurrence = request.getRecurrence() == null
                ? AlertSilenceRecurrence.ONCE : request.getRecurrence();
        if (recurrence == AlertSilenceRecurrence.ONCE) {
            return new RecurrenceConfiguration(recurrence, null, Set.of(), null);
        }

        String timeZone = trimToNull(request.getTimeZone());
        if (timeZone == null) {
            throw new BusinessException(400, "Time zone is required for recurring silences");
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(timeZone);
        } catch (DateTimeException error) {
            throw new BusinessException(400, "Unknown silence time zone: " + timeZone);
        }
        if (request.getRecurrenceUntil() == null) {
            throw new BusinessException(400, "Recurrence end time is required for recurring silences");
        }
        if (request.getRecurrenceUntil().toInstant().isBefore(request.getEndsAt().toInstant())) {
            throw new BusinessException(400, "Recurrence end time must not be before the first window ends");
        }

        Duration wallDuration = Duration.between(
                request.getStartsAt().toInstant().atZone(zone).toLocalDateTime(),
                request.getEndsAt().toInstant().atZone(zone).toLocalDateTime());
        Duration maximumDuration = recurrence == AlertSilenceRecurrence.DAILY
                ? Duration.ofDays(1) : Duration.ofDays(7);
        if (wallDuration.isNegative() || wallDuration.isZero() || wallDuration.compareTo(maximumDuration) > 0) {
            throw new BusinessException(400, recurrence == AlertSilenceRecurrence.DAILY
                    ? "Daily silence windows must not exceed 24 hours"
                    : "Weekly silence windows must not exceed 7 days");
        }

        Set<Integer> days = normalizeRecurrenceDays(recurrence, request.getRecurrenceDays());
        return new RecurrenceConfiguration(recurrence, zone.getId(), days,
                LocalDateTime.ofInstant(request.getRecurrenceUntil().toInstant(), ZoneOffset.UTC));
    }

    private static Set<Integer> normalizeRecurrenceDays(AlertSilenceRecurrence recurrence, Set<Integer> days) {
        if (recurrence == AlertSilenceRecurrence.DAILY) {
            return Set.of();
        }
        if (days == null || days.isEmpty()) {
            throw new BusinessException(400, "At least one weekday is required for weekly silences");
        }
        if (days.stream().anyMatch(day -> day == null || day < 1 || day > 7)) {
            throw new BusinessException(400, "Silence weekdays must use ISO values from 1 to 7");
        }
        TreeSet<Integer> normalized = new TreeSet<>(days);
        return Set.copyOf(normalized);
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

    private record RecurrenceConfiguration(AlertSilenceRecurrence type, String timeZone, Set<Integer> days,
            LocalDateTime until) {
    }
}
