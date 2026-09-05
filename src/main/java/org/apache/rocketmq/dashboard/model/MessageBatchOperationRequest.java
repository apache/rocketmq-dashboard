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

import java.util.List;
import java.util.Map;

public class MessageBatchOperationRequest {

    private String topic;
    private String targetTopic;
    private List<String> msgIds;
    private String tagFilterRegex;
    private Map<String, String> propertyRegexMap;
    private int maxConcurrency = 5;
    private long rateLimitDelayMs = 10;
    private boolean exportAsCsv = false;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getTargetTopic() {
        return targetTopic;
    }

    public void setTargetTopic(String targetTopic) {
        this.targetTopic = targetTopic;
    }

    public List<String> getMsgIds() {
        return msgIds;
    }

    public void setMsgIds(List<String> msgIds) {
        this.msgIds = msgIds;
    }

    public String getTagFilterRegex() {
        return tagFilterRegex;
    }

    public void setTagFilterRegex(String tagFilterRegex) {
        this.tagFilterRegex = tagFilterRegex;
    }

    public Map<String, String> getPropertyRegexMap() {
        return propertyRegexMap;
    }

    public void setPropertyRegexMap(Map<String, String> propertyRegexMap) {
        this.propertyRegexMap = propertyRegexMap;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public long getRateLimitDelayMs() {
        return rateLimitDelayMs;
    }

    public void setRateLimitDelayMs(long rateLimitDelayMs) {
        this.rateLimitDelayMs = rateLimitDelayMs;
    }

    public boolean isExportAsCsv() {
        return exportAsCsv;
    }

    public void setExportAsCsv(boolean exportAsCsv) {
        this.exportAsCsv = exportAsCsv;
    }
}
