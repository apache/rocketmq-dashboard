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
import java.util.Set;

/**
 *
 */
@Data
public class ConsumerGroupInfo {

    /**
 *
     */
    private String consumerGroupName;

    /**
 *
     */
    private String namespace;

    /**
 *
     */
    private String consumeMode;

    /**
 *
     */
    private Boolean consumeMessageOrderly;

    /**
 *
     */
    private Boolean consumeBroadcastEnable;

    /**
 *
     */
    private Boolean consumeFromMinEnable;

    /**
 *
     */
    private Integer retryQueueNums;

    /**
 *
     */
    private Integer retryMaxTimes;

    /**
 *
     */
    private Integer consumeTimeoutMinute;

    /**
 *
     */
    private String clusterName;

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
    private String status;

    /**
 *
     */
    private Set<String> subscribedTopics;

    /**
 *
     */
    private Integer onlineClientCount;

    /**
 *
     */
    private String liteBindTopic;

    /**
 *
     */
    private Map<String, String> attributes;

    /**
 *
     */
    private Integer groupSysFlag;

    /**
 *
     */
    public String getDisplayName() {
        if (namespace != null && !namespace.isEmpty() && !"DEFAULT".equals(namespace)) {
            return namespace + "/" + consumerGroupName;
        }
        return consumerGroupName;
    }

    /**
 *
     */
    public boolean isPopConsumer() {
        return "POP".equalsIgnoreCase(consumeMode);
    }

    /**
 *
     */
    public boolean isOrderlyConsume() {
        return Boolean.TRUE.equals(consumeMessageOrderly);
    }

    /**
 *
     */
    public boolean isBroadcastConsume() {
        return Boolean.TRUE.equals(consumeBroadcastEnable);
    }
}