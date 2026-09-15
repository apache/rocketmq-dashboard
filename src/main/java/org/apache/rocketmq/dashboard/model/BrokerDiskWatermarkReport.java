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

public class BrokerDiskWatermarkReport {
    private String clusterName;
    private int totalBrokers;
    private double highestDiskUsagePercent;
    private String riskLevel;
    private List<BrokerDiskDetail> brokerDisks = new ArrayList<>();
    private List<String> criticalAlerts = new ArrayList<>();
    private List<String> capacityRecommendations = new ArrayList<>();

    public static class BrokerDiskDetail {
        private String brokerName;
        private String brokerAddr;
        private String mountPath;
        private long totalSpaceBytes;
        private long freeSpaceBytes;
        private double usedPercent;
        private double growthVelocityMbPerHour;
        private double hoursUntilExhaustion;
        private int fileReservedTimeHours;
        private double cleanWatermarkPercent;
        private String status;

        public BrokerDiskDetail() {
        }

        public BrokerDiskDetail(String brokerName, String brokerAddr, String mountPath, long totalSpaceBytes,
            long freeSpaceBytes, double usedPercent, double growthVelocityMbPerHour, double hoursUntilExhaustion,
            int fileReservedTimeHours, double cleanWatermarkPercent, String status) {
            this.brokerName = brokerName;
            this.brokerAddr = brokerAddr;
            this.mountPath = mountPath;
            this.totalSpaceBytes = totalSpaceBytes;
            this.freeSpaceBytes = freeSpaceBytes;
            this.usedPercent = usedPercent;
            this.growthVelocityMbPerHour = growthVelocityMbPerHour;
            this.hoursUntilExhaustion = hoursUntilExhaustion;
            this.fileReservedTimeHours = fileReservedTimeHours;
            this.cleanWatermarkPercent = cleanWatermarkPercent;
            this.status = status;
        }

        public String getBrokerName() {
            return brokerName;
        }

        public void setBrokerName(String brokerName) {
            this.brokerName = brokerName;
        }

        public String getBrokerAddr() {
            return brokerAddr;
        }

        public void setBrokerAddr(String brokerAddr) {
            this.brokerAddr = brokerAddr;
        }

        public String getMountPath() {
            return mountPath;
        }

        public void setMountPath(String mountPath) {
            this.mountPath = mountPath;
        }

        public long getTotalSpaceBytes() {
            return totalSpaceBytes;
        }

        public void setTotalSpaceBytes(long totalSpaceBytes) {
            this.totalSpaceBytes = totalSpaceBytes;
        }

        public long getFreeSpaceBytes() {
            return freeSpaceBytes;
        }

        public void setFreeSpaceBytes(long freeSpaceBytes) {
            this.freeSpaceBytes = freeSpaceBytes;
        }

        public double getUsedPercent() {
            return usedPercent;
        }

        public void setUsedPercent(double usedPercent) {
            this.usedPercent = usedPercent;
        }

        public double getGrowthVelocityMbPerHour() {
            return growthVelocityMbPerHour;
        }

        public void setGrowthVelocityMbPerHour(double growthVelocityMbPerHour) {
            this.growthVelocityMbPerHour = growthVelocityMbPerHour;
        }

        public double getHoursUntilExhaustion() {
            return hoursUntilExhaustion;
        }

        public void setHoursUntilExhaustion(double hoursUntilExhaustion) {
            this.hoursUntilExhaustion = hoursUntilExhaustion;
        }

        public int getFileReservedTimeHours() {
            return fileReservedTimeHours;
        }

        public void setFileReservedTimeHours(int fileReservedTimeHours) {
            this.fileReservedTimeHours = fileReservedTimeHours;
        }

        public double getCleanWatermarkPercent() {
            return cleanWatermarkPercent;
        }

        public void setCleanWatermarkPercent(double cleanWatermarkPercent) {
            this.cleanWatermarkPercent = cleanWatermarkPercent;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public int getTotalBrokers() {
        return totalBrokers;
    }

    public void setTotalBrokers(int totalBrokers) {
        this.totalBrokers = totalBrokers;
    }

    public double getHighestDiskUsagePercent() {
        return highestDiskUsagePercent;
    }

    public void setHighestDiskUsagePercent(double highestDiskUsagePercent) {
        this.highestDiskUsagePercent = highestDiskUsagePercent;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public List<BrokerDiskDetail> getBrokerDisks() {
        return brokerDisks;
    }

    public void setBrokerDisks(List<BrokerDiskDetail> brokerDisks) {
        this.brokerDisks = brokerDisks;
    }

    public List<String> getCriticalAlerts() {
        return criticalAlerts;
    }

    public void setCriticalAlerts(List<String> criticalAlerts) {
        this.criticalAlerts = criticalAlerts;
    }

    public List<String> getCapacityRecommendations() {
        return capacityRecommendations;
    }

    public void setCapacityRecommendations(List<String> capacityRecommendations) {
        this.capacityRecommendations = capacityRecommendations;
    }
}
