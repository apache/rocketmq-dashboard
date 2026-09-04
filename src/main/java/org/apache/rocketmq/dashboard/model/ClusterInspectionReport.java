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

public class ClusterInspectionReport {

    private int totalScore = 100;
    private String overallHealthStatus; // HEALTHY, WARNING, CRITICAL
    private int inspectedBrokerCount;
    private int inspectedTopicCount;
    private List<InspectionIssue> detectedIssues = new ArrayList<>();
    private List<BrokerSyncStatus> masterSlaveSyncStatuses = new ArrayList<>();

    public int getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
    }

    public String getOverallHealthStatus() {
        return overallHealthStatus;
    }

    public void setOverallHealthStatus(String overallHealthStatus) {
        this.overallHealthStatus = overallHealthStatus;
    }

    public int getInspectedBrokerCount() {
        return inspectedBrokerCount;
    }

    public void setInspectedBrokerCount(int inspectedBrokerCount) {
        this.inspectedBrokerCount = inspectedBrokerCount;
    }

    public int getInspectedTopicCount() {
        return inspectedTopicCount;
    }

    public void setInspectedTopicCount(int inspectedTopicCount) {
        this.inspectedTopicCount = inspectedTopicCount;
    }

    public List<InspectionIssue> getDetectedIssues() {
        return detectedIssues;
    }

    public void setDetectedIssues(List<InspectionIssue> detectedIssues) {
        this.detectedIssues = detectedIssues;
    }

    public List<BrokerSyncStatus> getMasterSlaveSyncStatuses() {
        return masterSlaveSyncStatuses;
    }

    public void setMasterSlaveSyncStatuses(List<BrokerSyncStatus> masterSlaveSyncStatuses) {
        this.masterSlaveSyncStatuses = masterSlaveSyncStatuses;
    }

    public static class InspectionIssue {
        private String category; // REPLICATION_LAG, DISK_HAZARD, TOPIC_SKEW, ROUTE_DRIFT
        private String severity; // INFO, WARNING, HIGH, CRITICAL
        private String targetResource;
        private String description;
        private String remediationSuggestion;

        public InspectionIssue() {}

        public InspectionIssue(String category, String severity, String targetResource, String description, String remediationSuggestion) {
            this.category = category;
            this.severity = severity;
            this.targetResource = targetResource;
            this.description = description;
            this.remediationSuggestion = remediationSuggestion;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public String getTargetResource() {
            return targetResource;
        }

        public void setTargetResource(String targetResource) {
            this.targetResource = targetResource;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getRemediationSuggestion() {
            return remediationSuggestion;
        }

        public void setRemediationSuggestion(String remediationSuggestion) {
            this.remediationSuggestion = remediationSuggestion;
        }
    }

    public static class BrokerSyncStatus {
        private String clusterName;
        private String brokerName;
        private String masterAddr;
        private String slaveAddr;
        private long commitLogOffsetGap;
        private boolean isSynchronized;

        public BrokerSyncStatus() {}

        public BrokerSyncStatus(String clusterName, String brokerName, String masterAddr, String slaveAddr, long commitLogOffsetGap, boolean isSynchronized) {
            this.clusterName = clusterName;
            this.brokerName = brokerName;
            this.masterAddr = masterAddr;
            this.slaveAddr = slaveAddr;
            this.commitLogOffsetGap = commitLogOffsetGap;
            this.isSynchronized = isSynchronized;
        }

        public String getClusterName() {
            return clusterName;
        }

        public void setClusterName(String clusterName) {
            this.clusterName = clusterName;
        }

        public String getBrokerName() {
            return brokerName;
        }

        public void setBrokerName(String brokerName) {
            this.brokerName = brokerName;
        }

        public String getMasterAddr() {
            return masterAddr;
        }

        public void setMasterAddr(String masterAddr) {
            this.masterAddr = masterAddr;
        }

        public String getSlaveAddr() {
            return slaveAddr;
        }

        public void setSlaveAddr(String slaveAddr) {
            this.slaveAddr = slaveAddr;
        }

        public long getCommitLogOffsetGap() {
            return commitLogOffsetGap;
        }

        public void setCommitLogOffsetGap(long commitLogOffsetGap) {
            this.commitLogOffsetGap = commitLogOffsetGap;
        }

        public boolean isSynchronized() {
            return isSynchronized;
        }

        public void setSynchronized(boolean aSynchronized) {
            isSynchronized = aSynchronized;
        }
    }
}
