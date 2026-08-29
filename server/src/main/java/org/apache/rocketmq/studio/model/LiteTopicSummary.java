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

import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
public class LiteTopicSummary {

    private String topicPattern;

    private Integer topicCount;

    private List<String> sessionIds;

    private Date earliestCreateTime;

    private Date lastActiveTime;

    private Long averageTTL;

    private Long minTTL;

    private Long maxTTL;

    private Integer consumerCount;

    private Long totalBacklog;

    private boolean active;

    private java.util.Map<String, Object> attributes;

    public String getTTLStatus() {
        if (lastActiveTime == null) {
            return "UNKNOWN";
        }

        if (averageTTL == null || averageTTL <= 0) {
            return "ACTIVE";
        }
        long now = System.currentTimeMillis();
        if (lastActiveTime.getTime() >= now) {
            return "ACTIVE";
        }
        long elapsed;
        try {
            elapsed = Math.subtractExact(now, lastActiveTime.getTime());
        } catch (ArithmeticException exception) {
            return "EXPIRED";
        }

        // EXPIRED must be checked first: elapsed > averageTTL implies elapsed > averageTTL * 0.8,
        // so ordering it second would make EXPIRED unreachable.
        if (elapsed > averageTTL) {
            return "EXPIRED";
        } else if (elapsed > averageTTL * 0.8) {
            return "EXPIRING_SOON";
        } else {
            return "ACTIVE";
        }
    }

    public double getConsumerDensity() {
        if (topicCount == null || topicCount <= 0 || consumerCount == null || consumerCount <= 0) {
            return 0.0;
        }
        return (double) consumerCount / topicCount;
    }

    public boolean isEmptyAggregation() {
        return (consumerCount == null || consumerCount == 0)
                && (totalBacklog == null || totalBacklog == 0);
    }
}
