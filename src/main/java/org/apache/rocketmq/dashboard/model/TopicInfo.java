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

import lombok.Data;
import java.util.Date;
import java.util.Map;

/**
 *
 *
 */
@Data
public class TopicInfo {

    /**
 *
     */
    private String topicName;

    /**
 *
     */
    private TopicType topicType;

    /**
 *
     */
    private Integer readQueueNums;

    private Integer writeQueueNums;

    /**
 *
     */
    private Integer perm;

    /**
 *
     */
    private Boolean orderTopic;

    /**
 *
     */
    private Date createTime;

    /**
 *
     */
    private Date updateTime;

    /**
 *
     */
    private String topicStatus;

    /**
 *
     */
    private String clusterName;

    /**
 *
     */
    private Map<String, String> attributes;

    // Removed

    /**
 *
     */
    private Long fifoTimeoutSeconds;

    // Removed

    /**
 *
     */
    private Long liteTopicTTL;

    /**
     * LiteTopic - Session ID
     */
    private String sessionId;

    /**
 *
     */
    private String autoCreatePattern;

    // Removed

    /**
 *
     */
    private String delayLevel;

    // Removed

    /**
 *
     */
    private String transactionServerAddr;

    /**
 *
     */
    private Long transactionTimeoutSeconds;

    /**
 *
     */
    public String getDisplayName() {
        return topicName;
    }

    /**
 *
     */
    public boolean isLiteTopic() {
        return TopicType.LITE.equals(topicType);
    }

    /**
 *
     */
    public boolean isOrderTopic() {
        return Boolean.TRUE.equals(orderTopic) || TopicType.FIFO.equals(topicType);
    }

    /**
 *
     */
    public boolean isDelayTopic() {
        return TopicType.DELAY.equals(topicType);
    }

    /**
 *
     */
    public boolean isTransactionTopic() {
        return TopicType.TRANSACTION.equals(topicType);
    }
}