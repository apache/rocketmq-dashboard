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
package org.apache.rocketmq.studio.ops.ai.tool.contract.litetopic;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.instance.topic.LiteTopicQuotaVO;

/** LiteTopic quota watermark. Every field is optional: brokers may report only part of it. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LiteTopicQuotaOutput(
        Integer currentTopicCount,
        Integer maxTopicCount,
        Integer currentSessionCount,
        Integer maxSessionCount,
        Integer currentCreationRate,
        Integer maxCreationRate,
        Double usageRate,
        Double sessionUsageRate,
        Long defaultTTL,
        Long maxTTL,
        Integer remainingQuota,
        Double consumerDensity) {

    public static LiteTopicQuotaOutput from(LiteTopicQuotaVO vo) {
        return new LiteTopicQuotaOutput(
                vo.getCurrentTopicCount(),
                vo.getMaxTopicCount(),
                vo.getCurrentSessionCount(),
                vo.getMaxSessionCount(),
                vo.getCurrentCreationRate(),
                vo.getMaxCreationRate(),
                vo.getUsageRate(),
                vo.getSessionUsageRate(),
                vo.getDefaultTTL(),
                vo.getMaxTTL(),
                vo.getRemainingQuota(),
                vo.getConsumerDensity());
    }
}
