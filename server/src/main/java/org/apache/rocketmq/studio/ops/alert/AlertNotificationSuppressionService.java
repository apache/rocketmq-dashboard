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
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Finds active cluster incidents that make a business notification redundant. */
@Service
@RequiredArgsConstructor
public class AlertNotificationSuppressionService {
    private static final Duration CORRELATION_WINDOW = Duration.ofMinutes(30);
    private static final int CANDIDATE_PAGE_SIZE = 100;

    private final AlertRepository alertRepository;

    public Optional<SystemAlertVO> findSuppressingClusterAlert(SystemAlertVO event) {
        if (event.getDomain() != AlertDomain.BUSINESS || event.getTime() == null) {
            return Optional.empty();
        }
        LocalDateTime windowStart = event.getTime().minus(CORRELATION_WINDOW);
        Map<String, SystemAlertVO> latestByIncident = new HashMap<>();
        int page = 1;
        long fetched = 0;
        while (true) {
            PageResult<SystemAlertVO> result = alertRepository.findAlertsPage(new SystemAlertQuery(
                    null, AlertDomain.CLUSTER, event.getInstanceId(), null, null, null,
                    windowStart, event.getTime(), page, CANDIDATE_PAGE_SIZE));
            List<SystemAlertVO> candidates = result.getItems();
            for (SystemAlertVO candidate : candidates) {
                if (!AlertCorrelationScope.matches(event, candidate)) {
                    continue;
                }
                String incident = candidate.getFingerprint() == null
                        ? legacyIncidentKey(candidate)
                        : candidate.getFingerprint();
                latestByIncident.merge(incident, candidate, (left, right) -> later(left, right) ? left : right);
            }
            fetched += candidates.size();
            if (candidates.isEmpty() || fetched >= result.getTotal()) {
                break;
            }
            page++;
        }
        return latestByIncident.values().stream()
                .filter(candidate -> "FIRING".equalsIgnoreCase(candidate.getTransition()))
                .max(Comparator.comparing(SystemAlertVO::getTime, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private static String legacyIncidentKey(SystemAlertVO alert) {
        Map<String, String> identityLabels = new LinkedHashMap<>();
        if (alert.getLabels() != null) {
            identityLabels.putAll(alert.getLabels());
        }
        long ruleId = alert.getRuleId() == null ? 0L : alert.getRuleId();
        if (alert.getRuleId() == null && alert.getTitle() != null) {
            identityLabels.put("__legacy_title", alert.getTitle());
        }
        return "legacy:" + AlertFingerprint.of(ruleId, alert.getInstanceId(), identityLabels);
    }

    private static boolean later(SystemAlertVO left, SystemAlertVO right) {
        if (left.getTime() == null) {
            return false;
        }
        return right.getTime() == null || !left.getTime().isBefore(right.getTime());
    }
}
