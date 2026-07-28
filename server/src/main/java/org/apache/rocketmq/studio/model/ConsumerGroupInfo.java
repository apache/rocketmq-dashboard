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
import java.util.Set;

public class ConsumerGroupInfo {

    private String consumerGroupName;

    private String consumeMode;

    private Boolean consumeMessageOrderly;

    private Boolean consumeBroadcastEnable;

    private Boolean consumeFromMinEnable;

    private Integer retryQueueNums;

    private Integer retryMaxTimes;

    private Integer consumeTimeoutMinute;

    private String clusterName;

    private Date createTime;

    private Date updateTime;

    private String status;

    private Set<String> subscribedTopics;

    private Integer onlineClientCount;

    private String liteBindTopic;

    private Map<String, String> attributes;

    private Integer groupSysFlag;

    public String getDisplayName() {
        return consumerGroupName;
    }

    public boolean isPopConsumer() {
        return "POP".equalsIgnoreCase(consumeMode);
    }

    public boolean isOrderlyConsume() {
        return Boolean.TRUE.equals(consumeMessageOrderly);
    }

    public boolean isBroadcastConsume() {
        return Boolean.TRUE.equals(consumeBroadcastEnable);
    }
    public String getConsumerGroupName() {
        return consumerGroupName;
    }

    public void setConsumerGroupName(String consumerGroupName) {
        this.consumerGroupName = consumerGroupName;
    }

    public String getConsumeMode() {
        return consumeMode;
    }

    public void setConsumeMode(String consumeMode) {
        this.consumeMode = consumeMode;
    }

    public Boolean getConsumeMessageOrderly() {
        return consumeMessageOrderly;
    }

    public void setConsumeMessageOrderly(Boolean consumeMessageOrderly) {
        this.consumeMessageOrderly = consumeMessageOrderly;
    }

    public Boolean getConsumeBroadcastEnable() {
        return consumeBroadcastEnable;
    }

    public void setConsumeBroadcastEnable(Boolean consumeBroadcastEnable) {
        this.consumeBroadcastEnable = consumeBroadcastEnable;
    }

    public Boolean getConsumeFromMinEnable() {
        return consumeFromMinEnable;
    }

    public void setConsumeFromMinEnable(Boolean consumeFromMinEnable) {
        this.consumeFromMinEnable = consumeFromMinEnable;
    }

    public Integer getRetryQueueNums() {
        return retryQueueNums;
    }

    public void setRetryQueueNums(Integer retryQueueNums) {
        this.retryQueueNums = retryQueueNums;
    }

    public Integer getRetryMaxTimes() {
        return retryMaxTimes;
    }

    public void setRetryMaxTimes(Integer retryMaxTimes) {
        this.retryMaxTimes = retryMaxTimes;
    }

    public Integer getConsumeTimeoutMinute() {
        return consumeTimeoutMinute;
    }

    public void setConsumeTimeoutMinute(Integer consumeTimeoutMinute) {
        this.consumeTimeoutMinute = consumeTimeoutMinute;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Set<String> getSubscribedTopics() {
        return subscribedTopics;
    }

    public void setSubscribedTopics(Set<String> subscribedTopics) {
        this.subscribedTopics = subscribedTopics;
    }

    public Integer getOnlineClientCount() {
        return onlineClientCount;
    }

    public void setOnlineClientCount(Integer onlineClientCount) {
        this.onlineClientCount = onlineClientCount;
    }

    public String getLiteBindTopic() {
        return liteBindTopic;
    }

    public void setLiteBindTopic(String liteBindTopic) {
        this.liteBindTopic = liteBindTopic;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public Integer getGroupSysFlag() {
        return groupSysFlag;
    }

    public void setGroupSysFlag(Integer groupSysFlag) {
        this.groupSysFlag = groupSysFlag;
    }

}