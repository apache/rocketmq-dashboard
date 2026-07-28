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
package org.apache.rocketmq.studio.model;

import java.util.Date;
import java.util.Set;

public class LiteTopicSession {

    /**
     * Session ID
     */
    private String sessionId;

    private String clientId;

    private String clientAddress;

    private Set<String> liteTopics;

    private String parentTopic;

    /**
     * Consumer Group
     */
    private String consumerGroup;

    private Date createTime;

    private Date lastActiveTime;

    private Long ttl;

    private Long ttlRemaining;

    private String status;

    private Long totalMessages;

    private Long consumedMessages;

    private Long pendingMessages;

    private Double consumptionRate;

    private PopConsumeProgress popProgress;

    private Integer liteTopicCreationCount;

    public boolean hasActiveConsumption() {
        return "ACTIVE".equals(status) && consumptionRate != null && consumptionRate > 0;
    }

    public boolean isExpired() {
        return "EXPIRED".equals(status) || ttlRemaining != null && ttlRemaining <= 0;
    }

    public double getConsumptionProgress() {
        if (totalMessages == null || totalMessages == 0) {
            return 0.0;
        }
        return (double) consumedMessages / totalMessages * 100.0;
    }

    public static class PopConsumeProgress {
        private Integer ackTimeoutSeconds;
        private Integer maxReconsumeTimes;
        private Integer totalPopInFlightCount;
        private Integer lastPopTime;
        private Integer popCheckpoint;
        private Integer totalPopCount;
    }
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientAddress() {
        return clientAddress;
    }

    public void setClientAddress(String clientAddress) {
        this.clientAddress = clientAddress;
    }

    public Set<String> getLiteTopics() {
        return liteTopics;
    }

    public void setLiteTopics(Set<String> liteTopics) {
        this.liteTopics = liteTopics;
    }

    public String getParentTopic() {
        return parentTopic;
    }

    public void setParentTopic(String parentTopic) {
        this.parentTopic = parentTopic;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(Date lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    public Long getTtl() {
        return ttl;
    }

    public void setTtl(Long ttl) {
        this.ttl = ttl;
    }

    public Long getTtlRemaining() {
        return ttlRemaining;
    }

    public void setTtlRemaining(Long ttlRemaining) {
        this.ttlRemaining = ttlRemaining;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTotalMessages() {
        return totalMessages;
    }

    public void setTotalMessages(Long totalMessages) {
        this.totalMessages = totalMessages;
    }

    public Long getConsumedMessages() {
        return consumedMessages;
    }

    public void setConsumedMessages(Long consumedMessages) {
        this.consumedMessages = consumedMessages;
    }

    public Long getPendingMessages() {
        return pendingMessages;
    }

    public void setPendingMessages(Long pendingMessages) {
        this.pendingMessages = pendingMessages;
    }

    public Double getConsumptionRate() {
        return consumptionRate;
    }

    public void setConsumptionRate(Double consumptionRate) {
        this.consumptionRate = consumptionRate;
    }

    public PopConsumeProgress getPopProgress() {
        return popProgress;
    }

    public void setPopProgress(PopConsumeProgress popProgress) {
        this.popProgress = popProgress;
    }

    public Integer getLiteTopicCreationCount() {
        return liteTopicCreationCount;
    }

    public void setLiteTopicCreationCount(Integer liteTopicCreationCount) {
        this.liteTopicCreationCount = liteTopicCreationCount;
    }

}