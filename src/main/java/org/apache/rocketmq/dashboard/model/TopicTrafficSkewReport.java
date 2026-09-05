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

public class TopicTrafficSkewReport {

    private String topic;
    private long inspectTime;
    private int totalQueues;
    private long totalMessages;
    private double standardDeviation;
    private double giniCoefficient;
    private String skewLevel; // BALANCED, SLIGHT_SKEW, SEVERE_SKEW
    private String suggestion;
    private List<QueueSkewDetail> queueDetails = new ArrayList<>();

    public static class QueueSkewDetail {
        private String brokerName;
        private int queueId;
        private long minOffset;
        private long maxOffset;
        private long messageCount;
        private double ratioPercent;
        private boolean isHotspot;

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

        public long getMinOffset() {
            return minOffset;
        }

        public void setMinOffset(long minOffset) {
            this.minOffset = minOffset;
        }

        public long getMaxOffset() {
            return maxOffset;
        }

        public void setMaxOffset(long maxOffset) {
            this.maxOffset = maxOffset;
        }

        public long getMessageCount() {
            return messageCount;
        }

        public void setMessageCount(long messageCount) {
            this.messageCount = messageCount;
        }

        public double getRatioPercent() {
            return ratioPercent;
        }

        public void setRatioPercent(double ratioPercent) {
            this.ratioPercent = ratioPercent;
        }

        public boolean isHotspot() {
            return isHotspot;
        }

        public void setHotspot(boolean hotspot) {
            isHotspot = hotspot;
        }
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public long getInspectTime() {
        return inspectTime;
    }

    public void setInspectTime(long inspectTime) {
        this.inspectTime = inspectTime;
    }

    public int getTotalQueues() {
        return totalQueues;
    }

    public void setTotalQueues(int totalQueues) {
        this.totalQueues = totalQueues;
    }

    public long getTotalMessages() {
        return totalMessages;
    }

    public void setTotalMessages(long totalMessages) {
        this.totalMessages = totalMessages;
    }

    public double getStandardDeviation() {
        return standardDeviation;
    }

    public void setStandardDeviation(double standardDeviation) {
        this.standardDeviation = standardDeviation;
    }

    public double getGiniCoefficient() {
        return giniCoefficient;
    }

    public void setGiniCoefficient(double giniCoefficient) {
        this.giniCoefficient = giniCoefficient;
    }

    public String getSkewLevel() {
        return skewLevel;
    }

    public void setSkewLevel(String skewLevel) {
        this.skewLevel = skewLevel;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public List<QueueSkewDetail> getQueueDetails() {
        return queueDetails;
    }

    public void setQueueDetails(List<QueueSkewDetail> queueDetails) {
        this.queueDetails = queueDetails;
    }
}
