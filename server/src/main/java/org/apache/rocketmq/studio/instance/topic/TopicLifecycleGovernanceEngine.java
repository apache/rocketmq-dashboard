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

import org.apache.rocketmq.studio.common.util.SystemTopicFilter;
import org.apache.rocketmq.studio.instance.topic.TopicLifecycleAuditReportVO.DecommissionCandidateVO;
import org.apache.rocketmq.studio.instance.topic.TopicLifecycleAuditReportVO.TopicLifecycleAssessmentVO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class TopicLifecycleGovernanceEngine {

    public static final double ACTIVE_TPS_THRESHOLD = 0.05;

    public TopicLifecycleAuditReportVO auditLifecycle(String instanceId, List<TopicVO> topics) {
        TopicLifecycleAuditReportVO report = TopicLifecycleAuditReportVO.builder()
                .instanceId(instanceId)
                .auditTimestamp(LocalDateTime.now())
                .topicAssessments(new ArrayList<>())
                .governanceSuggestions(new ArrayList<>())
                .decommissionCandidates(new ArrayList<>())
                .build();

        if (topics == null || topics.isEmpty()) {
            report.setTotalTopicsAudited(0);
            report.getGovernanceSuggestions().add("No user topics found for lifecycle governance audit.");
            return report;
        }

        int activeCount = 0;
        int dormantCount = 0;
        int zombieCount = 0;
        int orphanCount = 0;

        for (TopicVO topic : topics) {
            String name = topic.getName();
            if (SystemTopicFilter.isSystem(name)) {
                continue; // Skip system topics from deletion governance
            }

            double tps = topic.getTps();
            long msgCount = topic.getMessageCount();
            int subCount = topic.getConsumerGroupCount();
            int writeQueues = topic.getWriteQueues() > 0 ? topic.getWriteQueues() : 8;

            String phase;
            String risk;
            String recommendation;

            if (tps > ACTIVE_TPS_THRESHOLD) {
                phase = "ACTIVE";
                risk = "CANNOT_DELETE";
                recommendation = "Topic has ongoing message throughput. Healthy active state; deletion strictly prohibited.";
                activeCount++;
            } else if (subCount == 0 && msgCount == 0) {
                phase = "ZOMBIE";
                risk = "SAFE_TO_CLEANUP";
                recommendation = "Topic has 0 messages, 0 throughput, and 0 subscriber consumer groups. Safe for immediate decommissioning.";
                zombieCount++;
                report.getDecommissionCandidates().add(DecommissionCandidateVO.builder()
                        .topicName(name)
                        .reason("Zero traffic, zero subscribers, and zero accumulated messages.")
                        .estimatedQueueSlotsFreed((long) writeQueues)
                        .safetyPrerequisite("Safe for deletion; no active consumer group or producer bound.")
                        .build());
            } else if (subCount == 0 && msgCount > 0) {
                phase = "ORPHAN";
                risk = "OBSERVATION_REQUIRED";
                recommendation = "Topic contains stored messages but has no active consumer subscribers. Verify if data is archived or abandoned before deletion.";
                orphanCount++;
                report.getDecommissionCandidates().add(DecommissionCandidateVO.builder()
                        .topicName(name)
                        .reason("Orphan topic with messages but zero consumer subscribers.")
                        .estimatedQueueSlotsFreed((long) writeQueues)
                        .safetyPrerequisite("Verify message retention policy and export backlogged messages before decommission.")
                        .build());
            } else {
                // subCount > 0, but TPS <= ACTIVE_TPS_THRESHOLD
                phase = "DORMANT";
                risk = "OBSERVATION_REQUIRED";
                recommendation = "Topic has registered subscribers but near-zero throughput. Check upstream publishing services and subscriber heartbeats.";
                dormantCount++;
            }

            report.getTopicAssessments().add(TopicLifecycleAssessmentVO.builder()
                    .topicName(name)
                    .lifecyclePhase(phase)
                    .currentTps(tps)
                    .totalMessages(msgCount)
                    .subscriberCount(subCount)
                    .hasActiveConsumers(subCount > 0)
                    .riskLevel(risk)
                    .recommendation(recommendation)
                    .build());
        }

        report.setTotalTopicsAudited(report.getTopicAssessments().size());
        report.setActiveTopicCount(activeCount);
        report.setDormantTopicCount(dormantCount);
        report.setZombieTopicCount(zombieCount);
        report.setOrphanTopicCount(orphanCount);

        if (zombieCount > 0) {
            report.getGovernanceSuggestions().add(String.format(
                    "Detected %d ZOMBIE topic(s) (0 traffic, 0 subscribers, 0 backlog). Decommission these topics to free up queue slots and broker memory.",
                    zombieCount));
            report.getGovernanceSuggestions().add("Execute administrative cleanup during scheduled maintenance windows.");
        }
        if (orphanCount > 0) {
            report.getGovernanceSuggestions().add(String.format(
                    "Detected %d ORPHAN topic(s) (stored messages but 0 subscribers). Check for abandoned topics or unmonitored backlogs.",
                    orphanCount));
            report.getGovernanceSuggestions().add("Inspect CommitLog expiration policies to confirm whether orphan messages should be archived.");
        }
        if (dormantCount > 0) {
            report.getGovernanceSuggestions().add(String.format(
                    "Detected %d DORMANT topic(s) (subscribers registered, but near-zero ingest). Verify producer heartbeats.",
                    dormantCount));
        }
        if (zombieCount == 0 && orphanCount == 0 && dormantCount == 0) {
            report.getGovernanceSuggestions().add("Cluster topic lifecycle is completely healthy: all topics actively publish or consume messages.");
        }

        return report;
    }
}
