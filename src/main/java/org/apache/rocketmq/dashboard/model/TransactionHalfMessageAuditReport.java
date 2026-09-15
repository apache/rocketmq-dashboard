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

public class TransactionHalfMessageAuditReport {
    private String topic;
    private String producerGroup;
    private long totalPendingHalfMessages;
    private long severeHangingMessages;
    private double timeoutRatePercent;
    private String healthStatus;
    private List<HangingHalfMessageDetail> pendingHalfMessages = new ArrayList<>();
    private List<String> auditLogs = new ArrayList<>();
    private List<String> resolutionRecommendations = new ArrayList<>();

    public static class HangingHalfMessageDetail {
        private String msgId;
        private String transactionId;
        private String topic;
        private String producerGroup;
        private long preparedTime;
        private long hangingDurationMinutes;
        private int checkCount;
        private int maxCheckRetries;
        private String state;

        public HangingHalfMessageDetail() {
        }

        public HangingHalfMessageDetail(String msgId, String transactionId, String topic, String producerGroup,
            long preparedTime, long hangingDurationMinutes, int checkCount, int maxCheckRetries, String state) {
            this.msgId = msgId;
            this.transactionId = transactionId;
            this.topic = topic;
            this.producerGroup = producerGroup;
            this.preparedTime = preparedTime;
            this.hangingDurationMinutes = hangingDurationMinutes;
            this.checkCount = checkCount;
            this.maxCheckRetries = maxCheckRetries;
            this.state = state;
        }

        public String getMsgId() {
            return msgId;
        }

        public void setMsgId(String msgId) {
            this.msgId = msgId;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public void setTransactionId(String transactionId) {
            this.transactionId = transactionId;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getProducerGroup() {
            return producerGroup;
        }

        public void setProducerGroup(String producerGroup) {
            this.producerGroup = producerGroup;
        }

        public long getPreparedTime() {
            return preparedTime;
        }

        public void setPreparedTime(long preparedTime) {
            this.preparedTime = preparedTime;
        }

        public long getHangingDurationMinutes() {
            return hangingDurationMinutes;
        }

        public void setHangingDurationMinutes(long hangingDurationMinutes) {
            this.hangingDurationMinutes = hangingDurationMinutes;
        }

        public int getCheckCount() {
            return checkCount;
        }

        public void setCheckCount(int checkCount) {
            this.checkCount = checkCount;
        }

        public int getMaxCheckRetries() {
            return maxCheckRetries;
        }

        public void setMaxCheckRetries(int maxCheckRetries) {
            this.maxCheckRetries = maxCheckRetries;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public long getTotalPendingHalfMessages() {
        return totalPendingHalfMessages;
    }

    public void setTotalPendingHalfMessages(long totalPendingHalfMessages) {
        this.totalPendingHalfMessages = totalPendingHalfMessages;
    }

    public long getSevereHangingMessages() {
        return severeHangingMessages;
    }

    public void setSevereHangingMessages(long severeHangingMessages) {
        this.severeHangingMessages = severeHangingMessages;
    }

    public double getTimeoutRatePercent() {
        return timeoutRatePercent;
    }

    public void setTimeoutRatePercent(double timeoutRatePercent) {
        this.timeoutRatePercent = timeoutRatePercent;
    }

    public String getHealthStatus() {
        return healthStatus;
    }

    public void setHealthStatus(String healthStatus) {
        this.healthStatus = healthStatus;
    }

    public List<HangingHalfMessageDetail> getPendingHalfMessages() {
        return pendingHalfMessages;
    }

    public void setPendingHalfMessages(List<HangingHalfMessageDetail> pendingHalfMessages) {
        this.pendingHalfMessages = pendingHalfMessages;
    }

    public List<String> getAuditLogs() {
        return auditLogs;
    }

    public void setAuditLogs(List<String> auditLogs) {
        this.auditLogs = auditLogs;
    }

    public List<String> getResolutionRecommendations() {
        return resolutionRecommendations;
    }

    public void setResolutionRecommendations(List<String> resolutionRecommendations) {
        this.resolutionRecommendations = resolutionRecommendations;
    }
}
