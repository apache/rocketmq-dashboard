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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicLifecycleAuditReportVO {

    private String instanceId;
    private int totalTopicsAudited;
    private int activeTopicCount;
    private int dormantTopicCount;
    private int zombieTopicCount;
    private int orphanTopicCount;
    private LocalDateTime auditTimestamp;
    @Builder.Default
    private List<TopicLifecycleAssessmentVO> topicAssessments = new ArrayList<>();
    @Builder.Default
    private List<String> governanceSuggestions = new ArrayList<>();
    @Builder.Default
    private List<DecommissionCandidateVO> decommissionCandidates = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicLifecycleAssessmentVO {
        private String topicName;
        private String lifecyclePhase; // ACTIVE, DORMANT, ZOMBIE, ORPHAN
        private double currentTps;
        private long totalMessages;
        private int subscriberCount;
        private boolean hasActiveConsumers;
        private String riskLevel; // SAFE_TO_CLEANUP, CANNOT_DELETE, OBSERVATION_REQUIRED
        private String recommendation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DecommissionCandidateVO {
        private String topicName;
        private String reason;
        private long estimatedQueueSlotsFreed;
        private String safetyPrerequisite;
    }
}
