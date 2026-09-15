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

public class BrokerColdReadReport {
    private String clusterName;
    private double overallPageCacheHitRatePercent;
    private double totalColdReadTps;
    private String diskPressureStatus;
    private List<BrokerColdReadStat> brokerStats = new ArrayList<>();
    private List<ColdConsumerGroupDetail> topColdConsumerGroups = new ArrayList<>();
    private List<String> cacheTuningSuggestions = new ArrayList<>();

    public static class BrokerColdReadStat {
        private String brokerName;
        private String brokerAddr;
        private double pageCacheHitRatePercent;
        private double totalReadTps;
        private double coldReadTps;
        private double physicalDiskReadMbSec;
        private boolean isHighDiskPressure;

        public BrokerColdReadStat() {
        }

        public BrokerColdReadStat(String brokerName, String brokerAddr, double pageCacheHitRatePercent,
            double totalReadTps, double coldReadTps, double physicalDiskReadMbSec, boolean isHighDiskPressure) {
            this.brokerName = brokerName;
            this.brokerAddr = brokerAddr;
            this.pageCacheHitRatePercent = pageCacheHitRatePercent;
            this.totalReadTps = totalReadTps;
            this.coldReadTps = coldReadTps;
            this.physicalDiskReadMbSec = physicalDiskReadMbSec;
            this.isHighDiskPressure = isHighDiskPressure;
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

        public double getPageCacheHitRatePercent() {
            return pageCacheHitRatePercent;
        }

        public void setPageCacheHitRatePercent(double pageCacheHitRatePercent) {
            this.pageCacheHitRatePercent = pageCacheHitRatePercent;
        }

        public double getTotalReadTps() {
            return totalReadTps;
        }

        public void setTotalReadTps(double totalReadTps) {
            this.totalReadTps = totalReadTps;
        }

        public double getColdReadTps() {
            return coldReadTps;
        }

        public void setColdReadTps(double coldReadTps) {
            this.coldReadTps = coldReadTps;
        }

        public double getPhysicalDiskReadMbSec() {
            return physicalDiskReadMbSec;
        }

        public void setPhysicalDiskReadMbSec(double physicalDiskReadMbSec) {
            this.physicalDiskReadMbSec = physicalDiskReadMbSec;
        }

        public boolean isHighDiskPressure() {
            return isHighDiskPressure;
        }

        public void setHighDiskPressure(boolean highDiskPressure) {
            isHighDiskPressure = highDiskPressure;
        }
    }

    public static class ColdConsumerGroupDetail {
        private String consumerGroup;
        private String targetTopic;
        private long messageLag;
        private double coldReadTps;
        private long readOffsetDistance;
        private String impactLevel;

        public ColdConsumerGroupDetail() {
        }

        public ColdConsumerGroupDetail(String consumerGroup, String targetTopic, long messageLag, double coldReadTps,
            long readOffsetDistance, String impactLevel) {
            this.consumerGroup = consumerGroup;
            this.targetTopic = targetTopic;
            this.messageLag = messageLag;
            this.coldReadTps = coldReadTps;
            this.readOffsetDistance = readOffsetDistance;
            this.impactLevel = impactLevel;
        }

        public String getConsumerGroup() {
            return consumerGroup;
        }

        public void setConsumerGroup(String consumerGroup) {
            this.consumerGroup = consumerGroup;
        }

        public String getTargetTopic() {
            return targetTopic;
        }

        public void setTargetTopic(String targetTopic) {
            this.targetTopic = targetTopic;
        }

        public long getMessageLag() {
            return messageLag;
        }

        public void setMessageLag(long messageLag) {
            this.messageLag = messageLag;
        }

        public double getColdReadTps() {
            return coldReadTps;
        }

        public void setColdReadTps(double coldReadTps) {
            this.coldReadTps = coldReadTps;
        }

        public long getReadOffsetDistance() {
            return readOffsetDistance;
        }

        public void setReadOffsetDistance(long readOffsetDistance) {
            this.readOffsetDistance = readOffsetDistance;
        }

        public String getImpactLevel() {
            return impactLevel;
        }

        public void setImpactLevel(String impactLevel) {
            this.impactLevel = impactLevel;
        }
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public double getOverallPageCacheHitRatePercent() {
        return overallPageCacheHitRatePercent;
    }

    public void setOverallPageCacheHitRatePercent(double overallPageCacheHitRatePercent) {
        this.overallPageCacheHitRatePercent = overallPageCacheHitRatePercent;
    }

    public double getTotalColdReadTps() {
        return totalColdReadTps;
    }

    public void setTotalColdReadTps(double totalColdReadTps) {
        this.totalColdReadTps = totalColdReadTps;
    }

    public String getDiskPressureStatus() {
        return diskPressureStatus;
    }

    public void setDiskPressureStatus(String diskPressureStatus) {
        this.diskPressureStatus = diskPressureStatus;
    }

    public List<BrokerColdReadStat> getBrokerStats() {
        return brokerStats;
    }

    public void setBrokerStats(List<BrokerColdReadStat> brokerStats) {
        this.brokerStats = brokerStats;
    }

    public List<ColdConsumerGroupDetail> getTopColdConsumerGroups() {
        return topColdConsumerGroups;
    }

    public void setTopColdConsumerGroups(List<ColdConsumerGroupDetail> topColdConsumerGroups) {
        this.topColdConsumerGroups = topColdConsumerGroups;
    }

    public List<String> getCacheTuningSuggestions() {
        return cacheTuningSuggestions;
    }

    public void setCacheTuningSuggestions(List<String> cacheTuningSuggestions) {
        this.cacheTuningSuggestions = cacheTuningSuggestions;
    }
}
