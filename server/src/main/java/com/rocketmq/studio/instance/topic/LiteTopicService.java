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

package com.rocketmq.studio.instance.topic;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class LiteTopicService {

    public List<LiteTopicItemVO> listLiteTopics(String pattern, String namespace) {
        return sampleItems().stream()
                .filter(item -> matches(pattern, item.getTopicPattern()))
                .filter(item -> matches(namespace, item.getNamespace()))
                .toList();
    }

    public LiteTopicSessionVO getSession(String sessionId) {
        long now = System.currentTimeMillis();
        return LiteTopicSessionVO.builder()
                .sessionId(sessionId)
                .clientId("grpc-client-" + sessionId)
                .clientAddress("192.168.1.10:8081")
                .parentTopic("chat/{sessionId}")
                .consumerGroup("cg-chat-session")
                .createTime(now - 3_600_000L)
                .lastActiveTime(now - 30_000L)
                .ttl(3_600_000L)
                .ttlRemaining(1_800_000L)
                .status("ACTIVE")
                .totalMessages(5_000L)
                .consumedMessages(4_800L)
                .pendingMessages(200L)
                .popProgress(96)
                .liteTopicCreationCount(2)
                .liteTopics(List.of(
                        new LiteTopicSessionVO.SessionLiteTopic("chat/" + sessionId, "ACTIVE", 1_800_000L),
                        new LiteTopicSessionVO.SessionLiteTopic("agent/" + sessionId, "ACTIVE", 1_200_000L)))
                .build();
    }

    public void extendTTL(String topicPattern, Long newTTL) {
        if (topicPattern == null || topicPattern.isBlank()) {
            throw new IllegalArgumentException("topicPattern is required");
        }
        if (newTTL == null || newTTL <= 0) {
            throw new IllegalArgumentException("newTTL must be positive");
        }
    }

    public LiteTopicQuotaVO getQuota(String namespace) {
        return LiteTopicQuotaVO.builder()
                .currentTopicCount(128)
                .maxTopicCount(1_000_000)
                .currentSessionCount(32)
                .maxSessionCount(100_000)
                .currentCreationRate(12)
                .maxCreationRate(1_000)
                .usageRate(0.000128)
                .sessionUsageRate(0.00032)
                .defaultTTL(3_600_000L)
                .maxTTL(86_400_000L)
                .remainingQuota(999_872)
                .consumerDensity(0.25)
                .build();
    }

    public LiteTopicCapabilityVO getCapability() {
        return new LiteTopicCapabilityVO(true);
    }

    private List<LiteTopicItemVO> sampleItems() {
        long now = System.currentTimeMillis();
        return List.of(
                LiteTopicItemVO.builder()
                        .topicPattern("chat/{sessionId}")
                        .namespace("default")
                        .topicCount(96)
                        .consumerCount(12)
                        .totalBacklog(1_200L)
                        .averageTTL(3_600_000L)
                        .ttlStatus("ACTIVE")
                        .lastActiveTime(now - 60_000L)
                        .sessionIds(List.of("sess-001", "sess-002"))
                        .build(),
                LiteTopicItemVO.builder()
                        .topicPattern("agent/{sessionId}")
                        .namespace("ai")
                        .topicCount(32)
                        .consumerCount(4)
                        .totalBacklog(180L)
                        .averageTTL(1_800_000L)
                        .ttlStatus("EXPIRING_SOON")
                        .lastActiveTime(now - 120_000L)
                        .sessionIds(List.of("agent-001"))
                        .build());
    }

    private boolean matches(String filter, String value) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return value != null && value.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
    }
}
