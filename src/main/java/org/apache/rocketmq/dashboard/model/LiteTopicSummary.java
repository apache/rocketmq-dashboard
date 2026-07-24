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
import java.util.List;

/**
 *
 *
 */
@Data
public class LiteTopicSummary {

    /**
 *
     */
    private String topicPattern;

    /**
 *
     */
    private Integer topicCount;

    /**
 *
     */
    private List<String> sessionIds;

    /**
 *
     */
    private Date earliestCreateTime;

    /**
 *
     */
    private Date lastActiveTime;

    /**
 *
     */
    private Long averageTTL;

    /**
 *
     */
    private Long minTTL;

    /**
 *
     */
    private Long maxTTL;

    /**
 *
     */
    private Integer consumerCount;

    /**
 *
     */
    private Long totalBacklog;

    /**
 *
     */
    private boolean active;

    /**
 *
     */
    private java.util.Map<String, Object> attributes;

    /**
 *
     */
    public String getTTLStatus() {
        if (lastActiveTime == null) {
            return "UNKNOWN";
        }

        long now = System.currentTimeMillis();
        long elapsed = now - lastActiveTime.getTime();

        if (averageTTL != null && elapsed > averageTTL * 0.8) {
            return "EXPIRING_SOON";
        } else if (averageTTL != null && elapsed > averageTTL) {
            return "EXPIRED";
        } else {
            return "ACTIVE";
        }
    }

    /**
 *
     */
    public double getConsumerDensity() {
        if (topicCount == null || topicCount == 0) {
            return 0.0;
        }
        return (double) consumerCount / topicCount;
    }

    /**
 *
     */
    public boolean isEmptyAggregation() {
        return consumerCount == 0 && (totalBacklog == null || totalBacklog == 0);
    }
}