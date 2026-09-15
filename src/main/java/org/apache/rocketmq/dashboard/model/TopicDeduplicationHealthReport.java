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

public class TopicDeduplicationHealthReport {
    private String topic;
    private long sampledMessageCount;
    private long uniqueKeyCount;
    private long duplicateKeyCount;
    private double duplicateRatioPercent;
    private String idempotencyHealthScore;
    private List<DuplicateKeyDetail> topDuplicateKeys = new ArrayList<>();
    private List<DuplicateTimeWindowBucket> timeWindowDistribution = new ArrayList<>();
    private List<String> idempotencyRecommendations = new ArrayList<>();

    public static class DuplicateKeyDetail {
        private String messageKey;
        private int occurrences;
        private long firstSeenTime;
        private long lastSeenTime;
        private long minIntervalMs;
        private String sampleMsgIds;

        public DuplicateKeyDetail() {
        }

        public DuplicateKeyDetail(String messageKey, int occurrences, long firstSeenTime, long lastSeenTime,
            long minIntervalMs, String sampleMsgIds) {
            this.messageKey = messageKey;
            this.occurrences = occurrences;
            this.firstSeenTime = firstSeenTime;
            this.lastSeenTime = lastSeenTime;
            this.minIntervalMs = minIntervalMs;
            this.sampleMsgIds = sampleMsgIds;
        }

        public String getMessageKey() {
            return messageKey;
        }

        public void setMessageKey(String messageKey) {
            this.messageKey = messageKey;
        }

        public int getOccurrences() {
            return occurrences;
        }

        public void setOccurrences(int occurrences) {
            this.occurrences = occurrences;
        }

        public long getFirstSeenTime() {
            return firstSeenTime;
        }

        public void setFirstSeenTime(long firstSeenTime) {
            this.firstSeenTime = firstSeenTime;
        }

        public long getLastSeenTime() {
            return lastSeenTime;
        }

        public void setLastSeenTime(long lastSeenTime) {
            this.lastSeenTime = lastSeenTime;
        }

        public long getMinIntervalMs() {
            return minIntervalMs;
        }

        public void setMinIntervalMs(long minIntervalMs) {
            this.minIntervalMs = minIntervalMs;
        }

        public String getSampleMsgIds() {
            return sampleMsgIds;
        }

        public void setSampleMsgIds(String sampleMsgIds) {
            this.sampleMsgIds = sampleMsgIds;
        }
    }

    public static class DuplicateTimeWindowBucket {
        private String intervalLabel;
        private long duplicateCount;
        private double percentage;

        public DuplicateTimeWindowBucket() {
        }

        public DuplicateTimeWindowBucket(String intervalLabel, long duplicateCount, double percentage) {
            this.intervalLabel = intervalLabel;
            this.duplicateCount = duplicateCount;
            this.percentage = percentage;
        }

        public String getIntervalLabel() {
            return intervalLabel;
        }

        public void setIntervalLabel(String intervalLabel) {
            this.intervalLabel = intervalLabel;
        }

        public long getDuplicateCount() {
            return duplicateCount;
        }

        public void setDuplicateCount(long duplicateCount) {
            this.duplicateCount = duplicateCount;
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

    public long getSampledMessageCount() {
        return sampledMessageCount;
    }

    public void setSampledMessageCount(long sampledMessageCount) {
        this.sampledMessageCount = sampledMessageCount;
    }

    public long getUniqueKeyCount() {
        return uniqueKeyCount;
    }

    public void setUniqueKeyCount(long uniqueKeyCount) {
        this.uniqueKeyCount = uniqueKeyCount;
    }

    public long getDuplicateKeyCount() {
        return duplicateKeyCount;
    }

    public void setDuplicateKeyCount(long duplicateKeyCount) {
        this.duplicateKeyCount = duplicateKeyCount;
    }

    public double getDuplicateRatioPercent() {
        return duplicateRatioPercent;
    }

    public void setDuplicateRatioPercent(double duplicateRatioPercent) {
        this.duplicateRatioPercent = duplicateRatioPercent;
    }

    public String getIdempotencyHealthScore() {
        return idempotencyHealthScore;
    }

    public void setIdempotencyHealthScore(String idempotencyHealthScore) {
        this.idempotencyHealthScore = idempotencyHealthScore;
    }

    public List<DuplicateKeyDetail> getTopDuplicateKeys() {
        return topDuplicateKeys;
    }

    public void setTopDuplicateKeys(List<DuplicateKeyDetail> topDuplicateKeys) {
        this.topDuplicateKeys = topDuplicateKeys;
    }

    public List<DuplicateTimeWindowBucket> getTimeWindowDistribution() {
        return timeWindowDistribution;
    }

    public void setTimeWindowDistribution(List<DuplicateTimeWindowBucket> timeWindowDistribution) {
        this.timeWindowDistribution = timeWindowDistribution;
    }

    public List<String> getIdempotencyRecommendations() {
        return idempotencyRecommendations;
    }

    public void setIdempotencyRecommendations(List<String> idempotencyRecommendations) {
        this.idempotencyRecommendations = idempotencyRecommendations;
    }
}
