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

public class TopicCrossClusterSyncReport {
    private String topic;
    private String sourceCluster;
    private String targetCluster;
    private boolean isConfigurationInSync;
    private int totalDiscrepancies;
    private String syncStatus;
    private List<TopicAttributeDiff> attributeDiffs = new ArrayList<>();
    private List<String> syncActionLog = new ArrayList<>();

    public static class TopicAttributeDiff {
        private String attributeName;
        private String sourceValue;
        private String targetValue;
        private boolean isDrifted;
        private String remediationRecommendation;

        public TopicAttributeDiff() {
        }

        public TopicAttributeDiff(String attributeName, String sourceValue, String targetValue, boolean isDrifted,
            String remediationRecommendation) {
            this.attributeName = attributeName;
            this.sourceValue = sourceValue;
            this.targetValue = targetValue;
            this.isDrifted = isDrifted;
            this.remediationRecommendation = remediationRecommendation;
        }

        public String getAttributeName() {
            return attributeName;
        }

        public void setAttributeName(String attributeName) {
            this.attributeName = attributeName;
        }

        public String getSourceValue() {
            return sourceValue;
        }

        public void setSourceValue(String sourceValue) {
            this.sourceValue = sourceValue;
        }

        public String getTargetValue() {
            return targetValue;
        }

        public void setTargetValue(String targetValue) {
            this.targetValue = targetValue;
        }

        public boolean isDrifted() {
            return isDrifted;
        }

        public void setDrifted(boolean drifted) {
            isDrifted = drifted;
        }

        public String getRemediationRecommendation() {
            return remediationRecommendation;
        }

        public void setRemediationRecommendation(String remediationRecommendation) {
            this.remediationRecommendation = remediationRecommendation;
        }
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getSourceCluster() {
        return sourceCluster;
    }

    public void setSourceCluster(String sourceCluster) {
        this.sourceCluster = sourceCluster;
    }

    public String getTargetCluster() {
        return targetCluster;
    }

    public void setTargetCluster(String targetCluster) {
        this.targetCluster = targetCluster;
    }

    public boolean isConfigurationInSync() {
        return isConfigurationInSync;
    }

    public void setConfigurationInSync(boolean configurationInSync) {
        isConfigurationInSync = configurationInSync;
    }

    public int getTotalDiscrepancies() {
        return totalDiscrepancies;
    }

    public void setTotalDiscrepancies(int totalDiscrepancies) {
        this.totalDiscrepancies = totalDiscrepancies;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public List<TopicAttributeDiff> getAttributeDiffs() {
        return attributeDiffs;
    }

    public void setAttributeDiffs(List<TopicAttributeDiff> attributeDiffs) {
        this.attributeDiffs = attributeDiffs;
    }

    public List<String> getSyncActionLog() {
        return syncActionLog;
    }

    public void setSyncActionLog(List<String> syncActionLog) {
        this.syncActionLog = syncActionLog;
    }
}
