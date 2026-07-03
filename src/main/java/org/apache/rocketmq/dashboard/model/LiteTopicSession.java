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
import java.util.Set;

/**
 *
 *
 */
@Data
public class LiteTopicSession {

    /**
     * Session ID
     */
    private String sessionId;

    /**
 *
     */
    private String clientId;

    /**
 *
     */
    private String clientAddress;

    /**
 *
     */
    private Set<String> liteTopics;

    /**
 *
     */
    private String parentTopic;

    /**
     * Consumer Group
     */
    private String consumerGroup;

    /**
 *
     */
    private Date createTime;

    /**
 *
     */
    private Date lastActiveTime;

    /**
 *
     */
    private Long ttl;

    /**
 *
     */
    private Long ttlRemaining;

    /**
 *
     */
    private String status;

    /**
 *
     */
    private Long totalMessages;

    /**
 *
     */
    private Long consumedMessages;

    /**
 *
     */
    private Long pendingMessages;

    /**
 *
     */
    private Double consumptionRate;

    /**
 *
     */
    private PopConsumeProgress popProgress;

    /**
 *
     */
    private Integer liteTopicCreationCount;

    /**
 *
     */
    public boolean hasActiveConsumption() {
        return "ACTIVE".equals(status) && consumptionRate != null && consumptionRate > 0;
    }

    /**
 *
     */
    public boolean isExpired() {
        return "EXPIRED".equals(status) || (ttlRemaining != null && ttlRemaining <= 0);
    }

    /**
 *
     */
    public double getConsumptionProgress() {
        if (totalMessages == null || totalMessages == 0) {
            return 0.0;
        }
        return (double) consumedMessages / totalMessages * 100.0;
    }

    @Data
    public static class PopConsumeProgress {
        private Integer ackTimeoutSeconds;
        private Integer maxReconsumeTimes;
        private Integer totalPopInFlightCount;
        private Integer lastPopTime;
        private Integer popCheckpoint;
        private Integer totalPopCount;
    }
}