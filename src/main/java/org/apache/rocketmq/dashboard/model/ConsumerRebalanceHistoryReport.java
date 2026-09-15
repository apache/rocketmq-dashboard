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

package org.apache.rocketmq.dashboard.model;

import java.util.ArrayList;
import java.util.List;

public class ConsumerRebalanceHistoryReport {
    private String consumerGroup;
    private int totalRebalanceEvents;
    private int flappingScore;
    private String stabilityLevel;
    private List<RebalanceEventItem> rebalanceEvents = new ArrayList<>();
    private List<FlappingQueueDetail> flappingQueues = new ArrayList<>();
    private List<String> churnClients = new ArrayList<>();
    private List<String> stabilityRecommendations = new ArrayList<>();

    public static class RebalanceEventItem {
        private String eventId;
        private long timestamp;
        private String triggerReason;
        private int clientCountBefore;
        private int clientCountAfter;
        private int reassignedQueueCount;
        private String impactedTopic;

        public RebalanceEventItem() {
        }

        public RebalanceEventItem(String eventId, long timestamp, String triggerReason, int clientCountBefore,
            int clientCountAfter, int reassignedQueueCount, String impactedTopic) {
            this.eventId = eventId;
            this.timestamp = timestamp;
            this.triggerReason = triggerReason;
            this.clientCountBefore = clientCountBefore;
            this.clientCountAfter = clientCountAfter;
            this.reassignedQueueCount = reassignedQueueCount;
            this.impactedTopic = impactedTopic;
        }

        public String getEventId() {
            return eventId;
        }

        public void setEventId(String eventId) {
            this.eventId = eventId;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public String getTriggerReason() {
            return triggerReason;
        }

        public void setTriggerReason(String triggerReason) {
            this.triggerReason = triggerReason;
        }

        public int getClientCountBefore() {
            return clientCountBefore;
        }

        public void setClientCountBefore(int clientCountBefore) {
            this.clientCountBefore = clientCountBefore;
        }

        public int getClientCountAfter() {
            return clientCountAfter;
        }

        public void setClientCountAfter(int clientCountAfter) {
            this.clientCountAfter = clientCountAfter;
        }

        public int getReassignedQueueCount() {
            return reassignedQueueCount;
        }

        public void setReassignedQueueCount(int reassignedQueueCount) {
            this.reassignedQueueCount = reassignedQueueCount;
        }

        public String getImpactedTopic() {
            return impactedTopic;
        }

        public void setImpactedTopic(String impactedTopic) {
            this.impactedTopic = impactedTopic;
        }
    }

    public static class FlappingQueueDetail {
        private String topic;
        private int queueId;
        private String brokerName;
        private int reassignmentCount;
        private String lastAssignedClientId;

        public FlappingQueueDetail() {
        }

        public FlappingQueueDetail(String topic, int queueId, String brokerName, int reassignmentCount,
            String lastAssignedClientId) {
            this.topic = topic;
            this.queueId = queueId;
            this.brokerName = brokerName;
            this.reassignmentCount = reassignmentCount;
            this.lastAssignedClientId = lastAssignedClientId;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public int getQueueId() {
            return queueId;
        }

        public void setQueueId(int queueId) {
            this.queueId = queueId;
        }

        public String getBrokerName() {
            return brokerName;
        }

        public void setBrokerName(String brokerName) {
            this.brokerName = brokerName;
        }

        public int getReassignmentCount() {
            return reassignmentCount;
        }

        public void setReassignmentCount(int reassignmentCount) {
            this.reassignmentCount = reassignmentCount;
        }

        public String getLastAssignedClientId() {
            return lastAssignedClientId;
        }

        public void setLastAssignedClientId(String lastAssignedClientId) {
            this.lastAssignedClientId = lastAssignedClientId;
        }
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public int getTotalRebalanceEvents() {
        return totalRebalanceEvents;
    }

    public void setTotalRebalanceEvents(int totalRebalanceEvents) {
        this.totalRebalanceEvents = totalRebalanceEvents;
    }

    public int getFlappingScore() {
        return flappingScore;
    }

    public void setFlappingScore(int flappingScore) {
        this.flappingScore = flappingScore;
    }

    public String getStabilityLevel() {
        return stabilityLevel;
    }

    public void setStabilityLevel(String stabilityLevel) {
        this.stabilityLevel = stabilityLevel;
    }

    public List<RebalanceEventItem> getRebalanceEvents() {
        return rebalanceEvents;
    }

    public void setRebalanceEvents(List<RebalanceEventItem> rebalanceEvents) {
        this.rebalanceEvents = rebalanceEvents;
    }

    public List<FlappingQueueDetail> getFlappingQueues() {
        return flappingQueues;
    }

    public void setFlappingQueues(List<FlappingQueueDetail> flappingQueues) {
        this.flappingQueues = flappingQueues;
    }

    public List<String> getChurnClients() {
        return churnClients;
    }

    public void setChurnClients(List<String> churnClients) {
        this.churnClients = churnClients;
    }

    public List<String> getStabilityRecommendations() {
        return stabilityRecommendations;
    }

    public void setStabilityRecommendations(List<String> stabilityRecommendations) {
        this.stabilityRecommendations = stabilityRecommendations;
    }
}
