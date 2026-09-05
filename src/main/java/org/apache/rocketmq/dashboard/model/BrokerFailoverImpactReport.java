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

public class BrokerFailoverImpactReport {

    private String targetBrokerName;
    private String clusterName;
    private long simulationTime;
    private int totalClusterBrokers;
    private int totalClusterTopics;
    private int impactedTopicCount;
    private int totalLossTopicCount; // Topics that become completely unreachable
    private int degradedTopicCount; // Topics with reduced queue capacity
    private double availabilityScore; // 0.0 - 100.0
    private String hazardLevel; // LOW, MEDIUM, CRITICAL
    private String actionPlan;
    private List<ImpactedTopicDetail> impactedTopics = new ArrayList<>();

    public static class ImpactedTopicDetail {
        private String topic;
        private int originalQueueCount;
        private int lostQueueCount;
        private int remainingQueueCount;
        private double capacityLossRatio;
        private boolean isCompleteLoss;
        private String riskExplanation;

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public int getOriginalQueueCount() {
            return originalQueueCount;
        }

        public void setOriginalQueueCount(int originalQueueCount) {
            this.originalQueueCount = originalQueueCount;
        }

        public int getLostQueueCount() {
            return lostQueueCount;
        }

        public void setLostQueueCount(int lostQueueCount) {
            this.lostQueueCount = lostQueueCount;
        }

        public int getRemainingQueueCount() {
            return remainingQueueCount;
        }

        public void setRemainingQueueCount(int remainingQueueCount) {
            this.remainingQueueCount = remainingQueueCount;
        }

        public double getCapacityLossRatio() {
            return capacityLossRatio;
        }

        public void setCapacityLossRatio(double capacityLossRatio) {
            this.capacityLossRatio = capacityLossRatio;
        }

        public boolean isCompleteLoss() {
            return isCompleteLoss;
        }

        public void setCompleteLoss(boolean completeLoss) {
            isCompleteLoss = completeLoss;
        }

        public String getRiskExplanation() {
            return riskExplanation;
        }

        public void setRiskExplanation(String riskExplanation) {
            this.riskExplanation = riskExplanation;
        }
    }

    public String getTargetBrokerName() {
        return targetBrokerName;
    }

    public void setTargetBrokerName(String targetBrokerName) {
        this.targetBrokerName = targetBrokerName;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public long getSimulationTime() {
        return simulationTime;
    }

    public void setSimulationTime(long simulationTime) {
        this.simulationTime = simulationTime;
    }

    public int getTotalClusterBrokers() {
        return totalClusterBrokers;
    }

    public void setTotalClusterBrokers(int totalClusterBrokers) {
        this.totalClusterBrokers = totalClusterBrokers;
    }

    public int getTotalClusterTopics() {
        return totalClusterTopics;
    }

    public void setTotalClusterTopics(int totalClusterTopics) {
        this.totalClusterTopics = totalClusterTopics;
    }

    public int getImpactedTopicCount() {
        return impactedTopicCount;
    }

    public void setImpactedTopicCount(int impactedTopicCount) {
        this.impactedTopicCount = impactedTopicCount;
    }

    public int getTotalLossTopicCount() {
        return totalLossTopicCount;
    }

    public void setTotalLossTopicCount(int totalLossTopicCount) {
        this.totalLossTopicCount = totalLossTopicCount;
    }

    public int getDegradedTopicCount() {
        return degradedTopicCount;
    }

    public void setDegradedTopicCount(int degradedTopicCount) {
        this.degradedTopicCount = degradedTopicCount;
    }

    public double getAvailabilityScore() {
        return availabilityScore;
    }

    public void setAvailabilityScore(double availabilityScore) {
        this.availabilityScore = availabilityScore;
    }

    public String getHazardLevel() {
        return hazardLevel;
    }

    public void setHazardLevel(String hazardLevel) {
        this.hazardLevel = hazardLevel;
    }

    public String getActionPlan() {
        return actionPlan;
    }

    public void setActionPlan(String actionPlan) {
        this.actionPlan = actionPlan;
    }

    public List<ImpactedTopicDetail> getImpactedTopics() {
        return impactedTopics;
    }

    public void setImpactedTopics(List<ImpactedTopicDetail> impactedTopics) {
        this.impactedTopics = impactedTopics;
    }
}
