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

public class DlqAutoReplayReport {
    private String consumerGroup;
    private String dlqTopic;
    private long totalDlqMessages;
    private long replayedMessages;
    private long failedMessages;
    private double replaySuccessRate;
    private String status;
    private ReplayPolicy policy;
    private List<ReplayExecutionItem> executionHistory = new ArrayList<>();
    private List<String> auditLogs = new ArrayList<>();

    public static class ReplayPolicy {
        private String targetTopic;
        private int rateLimitPerSecond;
        private int maxBatchSize;
        private boolean stopOnFailure;
        private int retryDelaySeconds;
        private String filterTag;

        public ReplayPolicy() {
            this.rateLimitPerSecond = 50;
            this.maxBatchSize = 500;
            this.stopOnFailure = false;
            this.retryDelaySeconds = 0;
            this.filterTag = "*";
        }

        public String getTargetTopic() {
            return targetTopic;
        }

        public void setTargetTopic(String targetTopic) {
            this.targetTopic = targetTopic;
        }

        public int getRateLimitPerSecond() {
            return rateLimitPerSecond;
        }

        public void setRateLimitPerSecond(int rateLimitPerSecond) {
            this.rateLimitPerSecond = rateLimitPerSecond;
        }

        public int getMaxBatchSize() {
            return maxBatchSize;
        }

        public void setMaxBatchSize(int maxBatchSize) {
            this.maxBatchSize = maxBatchSize;
        }

        public boolean isStopOnFailure() {
            return stopOnFailure;
        }

        public void setStopOnFailure(boolean stopOnFailure) {
            this.stopOnFailure = stopOnFailure;
        }

        public int getRetryDelaySeconds() {
            return retryDelaySeconds;
        }

        public void setRetryDelaySeconds(int retryDelaySeconds) {
            this.retryDelaySeconds = retryDelaySeconds;
        }

        public String getFilterTag() {
            return filterTag;
        }

        public void setFilterTag(String filterTag) {
            this.filterTag = filterTag;
        }
    }

    public static class ReplayExecutionItem {
        private String batchId;
        private long startTime;
        private long endTime;
        private int count;
        private int successCount;
        private int failCount;
        private String operator;

        public ReplayExecutionItem() {
        }

        public ReplayExecutionItem(String batchId, long startTime, long endTime, int count, int successCount,
            int failCount, String operator) {
            this.batchId = batchId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.count = count;
            this.successCount = successCount;
            this.failCount = failCount;
            this.operator = operator;
        }

        public String getBatchId() {
            return batchId;
        }

        public void setBatchId(String batchId) {
            this.batchId = batchId;
        }

        public long getStartTime() {
            return startTime;
        }

        public void setStartTime(long startTime) {
            this.startTime = startTime;
        }

        public long getEndTime() {
            return endTime;
        }

        public void setEndTime(long endTime) {
            this.endTime = endTime;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public void setSuccessCount(int successCount) {
            this.successCount = successCount;
        }

        public int getFailCount() {
            return failCount;
        }

        public void setFailCount(int failCount) {
            this.failCount = failCount;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getDlqTopic() {
        return dlqTopic;
    }

    public void setDlqTopic(String dlqTopic) {
        this.dlqTopic = dlqTopic;
    }

    public long getTotalDlqMessages() {
        return totalDlqMessages;
    }

    public void setTotalDlqMessages(long totalDlqMessages) {
        this.totalDlqMessages = totalDlqMessages;
    }

    public long getReplayedMessages() {
        return replayedMessages;
    }

    public void setReplayedMessages(long replayedMessages) {
        this.replayedMessages = replayedMessages;
    }

    public long getFailedMessages() {
        return failedMessages;
    }

    public void setFailedMessages(long failedMessages) {
        this.failedMessages = failedMessages;
    }

    public double getReplaySuccessRate() {
        return replaySuccessRate;
    }

    public void setReplaySuccessRate(double replaySuccessRate) {
        this.replaySuccessRate = replaySuccessRate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ReplayPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(ReplayPolicy policy) {
        this.policy = policy;
    }

    public List<ReplayExecutionItem> getExecutionHistory() {
        return executionHistory;
    }

    public void setExecutionHistory(List<ReplayExecutionItem> executionHistory) {
        this.executionHistory = executionHistory;
    }

    public List<String> getAuditLogs() {
        return auditLogs;
    }

    public void setAuditLogs(List<String> auditLogs) {
        this.auditLogs = auditLogs;
    }
}
