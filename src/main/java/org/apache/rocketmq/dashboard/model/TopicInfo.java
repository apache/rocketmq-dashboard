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
import java.util.Map;

public class TopicInfo {

    private String topicName;

    private TopicType topicType;

    private Integer readQueueNums;

    private Integer writeQueueNums;

    private Integer perm;

    private Boolean orderTopic;

    private Date createTime;

    private Date updateTime;

    private String topicStatus;

    private String clusterName;

    private Map<String, String> attributes;

    private Long fifoTimeoutSeconds;

    private Long liteTopicTTL;

    /**
     * LiteTopic - Session ID
     */
    private String sessionId;

    private String autoCreatePattern;

    private String delayLevel;

    private String transactionServerAddr;

    private Long transactionTimeoutSeconds;

    public String getDisplayName() {
        return topicName;
    }

    public boolean isLiteTopic() {
        return TopicType.LITE.equals(topicType);
    }

    public boolean isOrderTopic() {
        return Boolean.TRUE.equals(orderTopic) || TopicType.FIFO.equals(topicType);
    }

    public boolean isDelayTopic() {
        return TopicType.DELAY.equals(topicType);
    }

    public boolean isTransactionTopic() {
        return TopicType.TRANSACTION.equals(topicType);
    }
    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public TopicType getTopicType() {
        return topicType;
    }

    public void setTopicType(TopicType topicType) {
        this.topicType = topicType;
    }

    public Integer getReadQueueNums() {
        return readQueueNums;
    }

    public void setReadQueueNums(Integer readQueueNums) {
        this.readQueueNums = readQueueNums;
    }

    public Integer getWriteQueueNums() {
        return writeQueueNums;
    }

    public void setWriteQueueNums(Integer writeQueueNums) {
        this.writeQueueNums = writeQueueNums;
    }

    public Integer getPerm() {
        return perm;
    }

    public void setPerm(Integer perm) {
        this.perm = perm;
    }

    public Boolean getOrderTopic() {
        return orderTopic;
    }

    public void setOrderTopic(Boolean orderTopic) {
        this.orderTopic = orderTopic;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getTopicStatus() {
        return topicStatus;
    }

    public void setTopicStatus(String topicStatus) {
        this.topicStatus = topicStatus;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public Long getFifoTimeoutSeconds() {
        return fifoTimeoutSeconds;
    }

    public void setFifoTimeoutSeconds(Long fifoTimeoutSeconds) {
        this.fifoTimeoutSeconds = fifoTimeoutSeconds;
    }

    public Long getLiteTopicTTL() {
        return liteTopicTTL;
    }

    public void setLiteTopicTTL(Long liteTopicTTL) {
        this.liteTopicTTL = liteTopicTTL;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAutoCreatePattern() {
        return autoCreatePattern;
    }

    public void setAutoCreatePattern(String autoCreatePattern) {
        this.autoCreatePattern = autoCreatePattern;
    }

    public String getDelayLevel() {
        return delayLevel;
    }

    public void setDelayLevel(String delayLevel) {
        this.delayLevel = delayLevel;
    }

    public String getTransactionServerAddr() {
        return transactionServerAddr;
    }

    public void setTransactionServerAddr(String transactionServerAddr) {
        this.transactionServerAddr = transactionServerAddr;
    }

    public Long getTransactionTimeoutSeconds() {
        return transactionTimeoutSeconds;
    }

    public void setTransactionTimeoutSeconds(Long transactionTimeoutSeconds) {
        this.transactionTimeoutSeconds = transactionTimeoutSeconds;
    }

}