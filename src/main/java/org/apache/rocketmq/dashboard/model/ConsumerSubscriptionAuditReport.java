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
import java.util.Map;

public class ConsumerSubscriptionAuditReport {

    private String consumerGroup;
    private long auditTime;
    private int totalClients;
    private boolean isConsistent;
    private int conflictItemCount;
    private String auditStatus; // CONSISTENT, INCONSISTENT_SUBSCRIPTIONS, CONFLICT_EXPRESSIONS
    private String recommendation;
    private List<SubscriptionConflictItem> conflictItems = new ArrayList<>();
    private List<ClientSubscriptionSummary> clientSummaries = new ArrayList<>();

    public static class SubscriptionConflictItem {
        private String topic;
        private String conflictType; // TOPIC_MISSING, SUB_EXPRESSION_MISMATCH, FILTER_CLASS_MISMATCH
        private String description;
        private Map<String, String> clientExpressions; // clientId -> subString

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getConflictType() {
            return conflictType;
        }

        public void setConflictType(String conflictType) {
            this.conflictType = conflictType;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, String> getClientExpressions() {
            return clientExpressions;
        }

        public void setClientExpressions(Map<String, String> clientExpressions) {
            this.clientExpressions = clientExpressions;
        }
    }

    public static class ClientSubscriptionSummary {
        private String clientId;
        private String clientAddr;
        private String language;
        private String version;
        private List<String> subscribedTopics = new ArrayList<>();

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientAddr() {
            return clientAddr;
        }

        public void setClientAddr(String clientAddr) {
            this.clientAddr = clientAddr;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public List<String> getSubscribedTopics() {
            return subscribedTopics;
        }

        public void setSubscribedTopics(List<String> subscribedTopics) {
            this.subscribedTopics = subscribedTopics;
        }
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public long getAuditTime() {
        return auditTime;
    }

    public void setAuditTime(long auditTime) {
        this.auditTime = auditTime;
    }

    public int getTotalClients() {
        return totalClients;
    }

    public void setTotalClients(int totalClients) {
        this.totalClients = totalClients;
    }

    public boolean isConsistent() {
        return isConsistent;
    }

    public void setConsistent(boolean consistent) {
        isConsistent = consistent;
    }

    public int getConflictItemCount() {
        return conflictItemCount;
    }

    public void setConflictItemCount(int conflictItemCount) {
        this.conflictItemCount = conflictItemCount;
    }

    public String getAuditStatus() {
        return auditStatus;
    }

    public void setAuditStatus(String auditStatus) {
        this.auditStatus = auditStatus;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public List<SubscriptionConflictItem> getConflictItems() {
        return conflictItems;
    }

    public void setConflictItems(List<SubscriptionConflictItem> conflictItems) {
        this.conflictItems = conflictItems;
    }

    public List<ClientSubscriptionSummary> getClientSummaries() {
        return clientSummaries;
    }

    public void setClientSummaries(List<ClientSubscriptionSummary> clientSummaries) {
        this.clientSummaries = clientSummaries;
    }
}
