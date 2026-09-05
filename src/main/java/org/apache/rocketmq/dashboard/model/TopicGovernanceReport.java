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

public class TopicGovernanceReport {

    private int totalTopicsCount;
    private int zombieTopicsCount;
    private int orphanTopicsCount;
    private int oversizedQueueTopicsCount;
    private List<TopicGovernanceItem> governanceItems = new ArrayList<>();

    public int getTotalTopicsCount() {
        return totalTopicsCount;
    }

    public void setTotalTopicsCount(int totalTopicsCount) {
        this.totalTopicsCount = totalTopicsCount;
    }

    public int getZombieTopicsCount() {
        return zombieTopicsCount;
    }

    public void setZombieTopicsCount(int zombieTopicsCount) {
        this.zombieTopicsCount = zombieTopicsCount;
    }

    public int getOrphanTopicsCount() {
        return orphanTopicsCount;
    }

    public void setOrphanTopicsCount(int orphanTopicsCount) {
        this.orphanTopicsCount = orphanTopicsCount;
    }

    public int getOversizedQueueTopicsCount() {
        return oversizedQueueTopicsCount;
    }

    public void setOversizedQueueTopicsCount(int oversizedQueueTopicsCount) {
        this.oversizedQueueTopicsCount = oversizedQueueTopicsCount;
    }

    public List<TopicGovernanceItem> getGovernanceItems() {
        return governanceItems;
    }

    public void setGovernanceItems(List<TopicGovernanceItem> governanceItems) {
        this.governanceItems = governanceItems;
    }

    public static class TopicGovernanceItem {
        private String topicName;
        private String anomalyType; // ZOMBIE_TOPIC, ORPHAN_TOPIC, OVERSIZED_QUEUES
        private int readQueueNums;
        private int writeQueueNums;
        private int consumerGroupCount;
        private long inTps;
        private String riskReason;
        private String suggestedAction;

        public TopicGovernanceItem() {}

        public TopicGovernanceItem(String topicName, String anomalyType, int readQueueNums, int writeQueueNums, int consumerGroupCount, long inTps, String riskReason, String suggestedAction) {
            this.topicName = topicName;
            this.anomalyType = anomalyType;
            this.readQueueNums = readQueueNums;
            this.writeQueueNums = writeQueueNums;
            this.consumerGroupCount = consumerGroupCount;
            this.inTps = inTps;
            this.riskReason = riskReason;
            this.suggestedAction = suggestedAction;
        }

        public String getTopicName() {
            return topicName;
        }

        public void setTopicName(String topicName) {
            this.topicName = topicName;
        }

        public String getAnomalyType() {
            return anomalyType;
        }

        public void setAnomalyType(String anomalyType) {
            this.anomalyType = anomalyType;
        }

        public int getReadQueueNums() {
            return readQueueNums;
        }

        public void setReadQueueNums(int readQueueNums) {
            this.readQueueNums = readQueueNums;
        }

        public int getWriteQueueNums() {
            return writeQueueNums;
        }

        public void setWriteQueueNums(int writeQueueNums) {
            this.writeQueueNums = writeQueueNums;
        }

        public int getConsumerGroupCount() {
            return consumerGroupCount;
        }

        public void setConsumerGroupCount(int consumerGroupCount) {
            this.consumerGroupCount = consumerGroupCount;
        }

        public long getInTps() {
            return inTps;
        }

        public void setInTps(long inTps) {
            this.inTps = inTps;
        }

        public String getRiskReason() {
            return riskReason;
        }

        public void setRiskReason(String riskReason) {
            this.riskReason = riskReason;
        }

        public String getSuggestedAction() {
            return suggestedAction;
        }

        public void setSuggestedAction(String suggestedAction) {
            this.suggestedAction = suggestedAction;
        }
    }
}
