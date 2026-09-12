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
package org.apache.rocketmq.studio.ops.ai.tool.contract.group;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupListItem(
        String name,
        String namespace,
        String clusterId,
        SubscriptionMode subscriptionMode,
        ConsumeType consumeType,
        int retryMaxTimes,
        int onlineInstances,
        long totalLag,
        List<String> subscribedTopics) {

    public static GroupListItem from(ConsumerGroupVO group) {
        return new GroupListItem(
                group.getName(),
                group.getNamespace(),
                group.getClusterId(),
                group.getSubscriptionMode(),
                group.getConsumeType(),
                group.getRetryMaxTimes(),
                group.getOnlineInstances(),
                group.getTotalLag(),
                group.getSubscribedTopics() != null ? group.getSubscribedTopics() : List.of());
    }
}
