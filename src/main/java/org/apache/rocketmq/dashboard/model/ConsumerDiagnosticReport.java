/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * ( المساحة "License"); you may not use this file except in compliance with
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

public class ConsumerDiagnosticReport {

    private String groupName;
    private String topicName;
    private long totalDiff;
    private double totalTps;
    private String accumulationSkewLevel; // NORMAL, MODERATE, CRITICAL
    private double clientSkewVariance;
    private List<BottleneckQueueInfo> bottleneckQueues = new ArrayList<>();
    private List<ClientConsumeStatus> clientStatuses = new ArrayList<>();
    private List<String> diagnosticSuggestions = new ArrayList<>();

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public long getTotalDiff() {
        return totalDiff;
    }

    public void setTotalDiff(long totalDiff) {
        this.totalDiff = totalDiff;
    }

    public double getTotalTps() {
        return totalTps;
    }

    public void setTotalTps(double totalTps) {
        this.totalTps = totalTps;
    }

    public String getAccumulationSkewLevel() {
        return accumulationSkewLevel;
    }

    public void setAccumulationSkewLevel(String accumulationSkewLevel) {
        this.accumulationSkewLevel = accumulationSkewLevel;
    }

    public double getClientSkewVariance() {
        return clientSkewVariance;
    }

    public void setClientSkewVariance(double clientSkewVariance) {
        this.clientSkewVariance = clientSkewVariance;
    }

    public List<BottleneckQueueInfo> getBottleneckQueues() {
        return bottleneckQueues;
    }

    public void setBottleneckQueues(List<BottleneckQueueInfo> bottleneckQueues) {
        this.bottleneckQueues = bottleneckQueues;
    }

    public List<ClientConsumeStatus> getClientStatuses() {
        return clientStatuses;
    }

    public void setClientStatuses(List<ClientConsumeStatus> clientStatuses) {
        this.clientStatuses = clientStatuses;
    }

    public List<String> getDiagnosticSuggestions() {
        return diagnosticSuggestions;
    }

    public void setDiagnosticSuggestions(List<String> diagnosticSuggestions) {
        this.diagnosticSuggestions = diagnosticSuggestions;
    }

    public static class BottleneckQueueInfo {
        private String brokerName;
        private int queueId;
        private long brokerOffset;
        private long consumerOffset;
        private long diff;
        private String clientAddr;

        public BottleneckQueueInfo() {}

        public BottleneckQueueInfo(String brokerName, int queueId, long brokerOffset, long consumerOffset, long diff, String clientAddr) {
            this.brokerName = brokerName;
            this.queueId = queueId;
            this.brokerOffset = brokerOffset;
            this.consumerOffset = consumerOffset;
            this.diff = diff;
            this.clientAddr = clientAddr;
        }

        public String getBrokerName() {
            return brokerName;
        }

        public void setBrokerName(String brokerName) {
            this.brokerName = brokerName;
        }

        public int getQueueId() {
            return queueId;
        }

        public void setQueueId(int queueId) {
            this.queueId = queueId;
        }

        public long getBrokerOffset() {
            return brokerOffset;
        }

        public void setBrokerOffset(long brokerOffset) {
            this.brokerOffset = brokerOffset;
        }

        public long getConsumerOffset() {
            return consumerOffset;
        }

        public void setConsumerOffset(long consumerOffset) {
            this.consumerOffset = consumerOffset;
        }

        public long getDiff() {
            return diff;
        }

        public void setDiff(long diff) {
            this.diff = diff;
        }

        public String getClientAddr() {
            return clientAddr;
        }

        public void setClientAddr(String clientAddr) {
            this.clientAddr = clientAddr;
        }
    }

    public static class ClientConsumeStatus {
        private String clientId;
        private String clientAddr;
        private int assignedQueueCount;
        private long totalClientDiff;
        private double clientTps;

        public ClientConsumeStatus() {}

        public ClientConsumeStatus(String clientId, String clientAddr, int assignedQueueCount, long totalClientDiff, double clientTps) {
            this.clientId = clientId;
            this.clientAddr = clientAddr;
            this.assignedQueueCount = assignedQueueCount;
            this.totalClientDiff = totalClientDiff;
            this.clientTps = clientTps;
        }

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

        public int getAssignedQueueCount() {
            return assignedQueueCount;
        }

        public void setAssignedQueueCount(int assignedQueueCount) {
            this.assignedQueueCount = assignedQueueCount;
        }

        public long getTotalClientDiff() {
            return totalClientDiff;
        }

        public void setTotalClientDiff(long totalClientDiff) {
            this.totalClientDiff = totalClientDiff;
        }

        public double getClientTps() {
            return clientTps;
        }

        public void setClientTps(double clientTps) {
            this.clientTps = clientTps;
        }
    }
}
