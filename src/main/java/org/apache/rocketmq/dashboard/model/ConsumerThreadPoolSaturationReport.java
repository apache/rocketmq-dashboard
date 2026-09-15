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

public class ConsumerThreadPoolSaturationReport {
    private String consumerGroup;
    private String clientId;
    private int totalClients;
    private double overallSaturationPercent;
    private String healthStatus;
    private List<ClientThreadPoolStat> clientThreadPoolStats = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<String> optimizationRecommendations = new ArrayList<>();

    public static class ClientThreadPoolStat {
        private String clientId;
        private String clientAddr;
        private int corePoolSize;
        private int maximumPoolSize;
        private int activeThreadCount;
        private int queueDepth;
        private int queueCapacity;
        private double poolUtilizationPercent;
        private int blockedThreads;
        private boolean isSaturated;

        public ClientThreadPoolStat() {
        }

        public ClientThreadPoolStat(String clientId, String clientAddr, int corePoolSize, int maximumPoolSize,
            int activeThreadCount, int queueDepth, int queueCapacity, double poolUtilizationPercent,
            int blockedThreads, boolean isSaturated) {
            this.clientId = clientId;
            this.clientAddr = clientAddr;
            this.corePoolSize = corePoolSize;
            this.maximumPoolSize = maximumPoolSize;
            this.activeThreadCount = activeThreadCount;
            this.queueDepth = queueDepth;
            this.queueCapacity = queueCapacity;
            this.poolUtilizationPercent = poolUtilizationPercent;
            this.blockedThreads = blockedThreads;
            this.isSaturated = isSaturated;
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

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public int getActiveThreadCount() {
            return activeThreadCount;
        }

        public void setActiveThreadCount(int activeThreadCount) {
            this.activeThreadCount = activeThreadCount;
        }

        public int getQueueDepth() {
            return queueDepth;
        }

        public void setQueueDepth(int queueDepth) {
            this.queueDepth = queueDepth;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public double getPoolUtilizationPercent() {
            return poolUtilizationPercent;
        }

        public void setPoolUtilizationPercent(double poolUtilizationPercent) {
            this.poolUtilizationPercent = poolUtilizationPercent;
        }

        public int getBlockedThreads() {
            return blockedThreads;
        }

        public void setBlockedThreads(int blockedThreads) {
            this.blockedThreads = blockedThreads;
        }

        public boolean isSaturated() {
            return isSaturated;
        }

        public void setSaturated(boolean saturated) {
            isSaturated = saturated;
        }
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public int getTotalClients() {
        return totalClients;
    }

    public void setTotalClients(int totalClients) {
        this.totalClients = totalClients;
    }

    public double getOverallSaturationPercent() {
        return overallSaturationPercent;
    }

    public void setOverallSaturationPercent(double overallSaturationPercent) {
        this.overallSaturationPercent = overallSaturationPercent;
    }

    public String getHealthStatus() {
        return healthStatus;
    }

    public void setHealthStatus(String healthStatus) {
        this.healthStatus = healthStatus;
    }

    public List<ClientThreadPoolStat> getClientThreadPoolStats() {
        return clientThreadPoolStats;
    }

    public void setClientThreadPoolStats(List<ClientThreadPoolStat> clientThreadPoolStats) {
        this.clientThreadPoolStats = clientThreadPoolStats;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public List<String> getOptimizationRecommendations() {
        return optimizationRecommendations;
    }

    public void setOptimizationRecommendations(List<String> optimizationRecommendations) {
        this.optimizationRecommendations = optimizationRecommendations;
    }
}
