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

package org.apache.rocketmq.studio.instance.topic;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.model.LiteTopicQuota;
import org.apache.rocketmq.studio.model.LiteTopicSession;
import org.apache.rocketmq.studio.model.LiteTopicSummary;
import org.apache.rocketmq.studio.provider.LiteTopicProvider;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * Read/write facade for the LiteTopic console page.
 *
 * <p>All broker interaction is delegated to the {@link LiteTopicProvider}; this class validates
 * request input and maps the provider's domain models onto the REST view objects the console
 * consumes. TTLs are exchanged with the console in milliseconds (matching the UI contract) while
 * the broker stores them in minutes — the provider performs that conversion.
 */
@Service
@RequiredArgsConstructor
public class LiteTopicService {

    private final LiteTopicProvider liteTopicProvider;

    public List<LiteTopicItemVO> listLiteTopics(String pattern, String namespace) {
        return liteTopicProvider.listLiteTopics(pattern, namespace).stream()
                .map(this::toItemVO)
                .toList();
    }

    public LiteTopicSessionVO getSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BusinessException(400, "sessionId is required");
        }
        return toSessionVO(liteTopicProvider.getSession(sessionId));
    }

    public void extendTTL(String topicPattern, Long newTTL) {
        if (topicPattern == null || topicPattern.isBlank()) {
            throw new BusinessException(400, "topicPattern is required");
        }
        if (newTTL == null || newTTL <= 0) {
            throw new BusinessException(400, "newTTL must be positive");
        }
        liteTopicProvider.extendTTL(topicPattern.trim(), newTTL);
    }

    public LiteTopicQuotaVO getQuota(String namespace) {
        return toQuotaVO(liteTopicProvider.getQuota(namespace));
    }

    public LiteTopicCapabilityVO getCapability() {
        return new LiteTopicCapabilityVO(liteTopicProvider.isSupported());
    }

    // ─── Mapping ──────────────────────────────────────────────────────

    private LiteTopicItemVO toItemVO(LiteTopicSummary summary) {
        return LiteTopicItemVO.builder()
                .topicPattern(summary.getTopicPattern())
                .namespace(summary.getNamespace())
                .topicCount(summary.getTopicCount())
                .consumerCount(summary.getConsumerCount())
                .totalBacklog(summary.getTotalBacklog())
                .averageTTL(summary.getAverageTTL())
                .ttlStatus(summary.getTTLStatus())
                .lastActiveTime(epochMillis(summary.getLastActiveTime()))
                .sessionIds(summary.getSessionIds())
                .build();
    }

    private LiteTopicSessionVO toSessionVO(LiteTopicSession session) {
        return LiteTopicSessionVO.builder()
                .sessionId(session.getSessionId())
                .clientId(session.getClientId())
                .clientAddress(session.getClientAddress())
                .parentTopic(session.getParentTopic())
                .consumerGroup(session.getConsumerGroup())
                .createTime(epochMillis(session.getCreateTime()))
                .lastActiveTime(epochMillis(session.getLastActiveTime()))
                .ttl(session.getTtl())
                .ttlRemaining(session.getTtlRemaining())
                .status(session.getStatus())
                .totalMessages(session.getTotalMessages())
                .consumedMessages(session.getConsumedMessages())
                .pendingMessages(session.getPendingMessages())
                .liteTopicCreationCount(session.getLiteTopicCreationCount())
                .liteTopics(toLiteTopicRows(session))
                .build();
    }

    /**
     * A lite topic shares its parent topic's TTL policy, so every entry inherits the session's
     * status and remaining TTL.
     */
    private List<LiteTopicSessionVO.SessionLiteTopic> toLiteTopicRows(LiteTopicSession session) {
        if (session.getLiteTopics() == null) {
            return List.of();
        }
        return session.getLiteTopics().stream()
                .map(name -> new LiteTopicSessionVO.SessionLiteTopic(
                        name, session.getStatus(), session.getTtlRemaining()))
                .toList();
    }

    private LiteTopicQuotaVO toQuotaVO(LiteTopicQuota quota) {
        return LiteTopicQuotaVO.builder()
                .currentTopicCount(quota.getCurrentTopicCount())
                .maxTopicCount(quota.getMaxTopicCount())
                .currentSessionCount(quota.getCurrentSessionCount())
                .maxSessionCount(quota.getMaxSessionCount())
                .currentCreationRate(toInt(quota.getCurrentCreationRate()))
                .maxCreationRate(toInt(quota.getMaxCreationRate()))
                .usageRate(quota.getUsageRate())
                .sessionUsageRate(quota.getSessionUsageRate())
                .defaultTTL(quota.getDefaultTTL())
                .maxTTL(quota.getMaxTTL())
                .remainingQuota(quota.getRemainingQuota())
                .consumerDensity(consumerDensity(quota))
                .build();
    }

    /** Sessions per lite topic, i.e. how many independent consumers share the parent topic. */
    private Double consumerDensity(LiteTopicQuota quota) {
        Integer topics = quota.getCurrentTopicCount();
        Integer sessions = quota.getCurrentSessionCount();
        if (topics == null || topics <= 0 || sessions == null) {
            return null;
        }
        return (double) sessions / topics;
    }

    private static Long epochMillis(Date value) {
        return value == null ? null : value.getTime();
    }

    private static Integer toInt(Double value) {
        return value == null ? null : value.intValue();
    }
}
