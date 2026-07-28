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


public class LiteTopicQuota {

    private Integer maxTopicCount;

    private Integer currentTopicCount;

    private Integer maxSessionCount;

    private Integer currentSessionCount;

    private Long defaultTTL;

    private Long maxTTL;

    private Double currentCreationRate;

    private Double maxCreationRate;

    public double getUsageRate() {
        if (maxTopicCount == null || maxTopicCount == 0) {
            return 0.0;
        }
        return (double) currentTopicCount / maxTopicCount;
    }

    public double getSessionUsageRate() {
        if (maxSessionCount == null || maxSessionCount == 0) {
            return 0.0;
        }
        return (double) currentSessionCount / maxSessionCount;
    }

    public boolean isNearQuotaLimit(double threshold) {
        return getUsageRate() >= threshold;
    }

    public boolean isQuotaExceeded() {
        return currentTopicCount >= maxTopicCount;
    }

    public Integer getRemainingQuota() {
        if (maxTopicCount == null) {
            return 0;
        }
        return Math.max(0, maxTopicCount - currentTopicCount);
    }
    public Integer getMaxTopicCount() {
        return maxTopicCount;
    }

    public void setMaxTopicCount(Integer maxTopicCount) {
        this.maxTopicCount = maxTopicCount;
    }

    public Integer getCurrentTopicCount() {
        return currentTopicCount;
    }

    public void setCurrentTopicCount(Integer currentTopicCount) {
        this.currentTopicCount = currentTopicCount;
    }

    public Integer getMaxSessionCount() {
        return maxSessionCount;
    }

    public void setMaxSessionCount(Integer maxSessionCount) {
        this.maxSessionCount = maxSessionCount;
    }

    public Integer getCurrentSessionCount() {
        return currentSessionCount;
    }

    public void setCurrentSessionCount(Integer currentSessionCount) {
        this.currentSessionCount = currentSessionCount;
    }

    public Long getDefaultTTL() {
        return defaultTTL;
    }

    public void setDefaultTTL(Long defaultTTL) {
        this.defaultTTL = defaultTTL;
    }

    public Long getMaxTTL() {
        return maxTTL;
    }

    public void setMaxTTL(Long maxTTL) {
        this.maxTTL = maxTTL;
    }

    public Double getCurrentCreationRate() {
        return currentCreationRate;
    }

    public void setCurrentCreationRate(Double currentCreationRate) {
        this.currentCreationRate = currentCreationRate;
    }

    public Double getMaxCreationRate() {
        return maxCreationRate;
    }

    public void setMaxCreationRate(Double maxCreationRate) {
        this.maxCreationRate = maxCreationRate;
    }

}