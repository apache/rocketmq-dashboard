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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProducerLatencyReport {
    private String topic;
    private String producerGroup;
    private long totalSamples;
    private double p50LatencyMs;
    private double p95LatencyMs;
    private double p99LatencyMs;
    private double maxLatencyMs;
    private double avgLatencyMs;
    private double timeoutRatePercent;
    private String healthStatus;
    private List<BrokerLatencyStat> brokerLatencyStats = new ArrayList<>();
    private List<LatencyBucket> latencyHistogram = new ArrayList<>();
    private List<String> diagnosticSuggestions = new ArrayList<>();
    private Map<String, Long> errorTypeCounts = new HashMap<>();

    public static class BrokerLatencyStat {
        private String brokerName;
        private String brokerAddr;
        private long sendCount;
        private double avgLatencyMs;
        private double p95LatencyMs;
        private double timeoutCount;
        private boolean isSlowBroker;

        public BrokerLatencyStat() {
        }

        public BrokerLatencyStat(String brokerName, String brokerAddr, long sendCount, double avgLatencyMs,
            double p95LatencyMs, double timeoutCount, boolean isSlowBroker) {
            this.brokerName = brokerName;
            this.brokerAddr = brokerAddr;
            this.sendCount = sendCount;
            this.avgLatencyMs = avgLatencyMs;
            this.p95LatencyMs = p95LatencyMs;
            this.timeoutCount = timeoutCount;
            this.isSlowBroker = isSlowBroker;
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

        public long getSendCount() {
            return sendCount;
        }

        public void setSendCount(long sendCount) {
            this.sendCount = sendCount;
        }

        public double getAvgLatencyMs() {
            return avgLatencyMs;
        }

        public void setAvgLatencyMs(double avgLatencyMs) {
            this.avgLatencyMs = avgLatencyMs;
        }

        public double getP95LatencyMs() {
            return p95LatencyMs;
        }

        public void setP95LatencyMs(double p95LatencyMs) {
            this.p95LatencyMs = p95LatencyMs;
        }

        public double getTimeoutCount() {
            return timeoutCount;
        }

        public void setTimeoutCount(double timeoutCount) {
            this.timeoutCount = timeoutCount;
        }

        public boolean isSlowBroker() {
            return isSlowBroker;
        }

        public void setSlowBroker(boolean slowBroker) {
            isSlowBroker = slowBroker;
        }
    }

    public static class LatencyBucket {
        private String rangeLabel;
        private long count;
        private double percentage;

        public LatencyBucket() {
        }

        public LatencyBucket(String rangeLabel, long count, double percentage) {
            this.rangeLabel = rangeLabel;
            this.count = count;
            this.percentage = percentage;
        }

        public String getRangeLabel() {
            return rangeLabel;
        }

        public void setRangeLabel(String rangeLabel) {
            this.rangeLabel = rangeLabel;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }

        public double getPercentage() {
            return percentage;
        }

        public void setPercentage(double percentage) {
            this.percentage = percentage;
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

    public long getTotalSamples() {
        return totalSamples;
    }

    public void setTotalSamples(long totalSamples) {
        this.totalSamples = totalSamples;
    }

    public double getP50LatencyMs() {
        return p50LatencyMs;
    }

    public void setP50LatencyMs(double p50LatencyMs) {
        this.p50LatencyMs = p50LatencyMs;
    }

    public double getP95LatencyMs() {
        return p95LatencyMs;
    }

    public void setP95LatencyMs(double p95LatencyMs) {
        this.p95LatencyMs = p95LatencyMs;
    }

    public double getP99LatencyMs() {
        return p99LatencyMs;
    }

    public void setP99LatencyMs(double p99LatencyMs) {
        this.p99LatencyMs = p99LatencyMs;
    }

    public double getMaxLatencyMs() {
        return maxLatencyMs;
    }

    public void setMaxLatencyMs(double maxLatencyMs) {
        this.maxLatencyMs = maxLatencyMs;
    }

    public double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public void setAvgLatencyMs(double avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
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

    public List<BrokerLatencyStat> getBrokerLatencyStats() {
        return brokerLatencyStats;
    }

    public void setBrokerLatencyStats(List<BrokerLatencyStat> brokerLatencyStats) {
        this.brokerLatencyStats = brokerLatencyStats;
    }

    public List<LatencyBucket> getLatencyHistogram() {
        return latencyHistogram;
    }

    public void setLatencyHistogram(List<LatencyBucket> latencyHistogram) {
        this.latencyHistogram = latencyHistogram;
    }

    public List<String> getDiagnosticSuggestions() {
        return diagnosticSuggestions;
    }

    public void setDiagnosticSuggestions(List<String> diagnosticSuggestions) {
        this.diagnosticSuggestions = diagnosticSuggestions;
    }

    public Map<String, Long> getErrorTypeCounts() {
        return errorTypeCounts;
    }

    public void setErrorTypeCounts(Map<String, Long> errorTypeCounts) {
        this.errorTypeCounts = errorTypeCounts;
    }
}
