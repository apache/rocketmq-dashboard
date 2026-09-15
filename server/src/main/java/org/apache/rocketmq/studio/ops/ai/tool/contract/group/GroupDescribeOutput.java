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
import org.apache.rocketmq.studio.instance.group.ConsumerInstanceVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.client.ClientInstance;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupDescribeOutput(
        String cluster,
        String group,
        String namespace,
        String instanceId,
        SubscriptionMode subscriptionMode,
        ConsumeType consumeType,
        int onlineInstances,
        long totalLag,
        List<String> subscribedTopics,
        String subscriptionDataType,
        String deliveryOrderType,
        Integer retryMaxTimes,
        int delaySeconds,
        List<Subscription> subscriptions,
        List<ClientInstance> instances,
        Health health,
        List<GroupListItem> configurations) {

    public static GroupDescribeOutput from(
            ConsumerGroupVO source,
            String cluster,
            String requestedGroup,
            List<SubscriptionEntryVO> subscriptionEntries,
            Health health) {
        return from(source, cluster, requestedGroup, subscriptionEntries, health, List.of(source));
    }

    public static GroupDescribeOutput from(ConsumerGroupVO source, String cluster, String requestedGroup,
                                            List<SubscriptionEntryVO> subscriptionEntries, Health health,
                                            List<ConsumerGroupVO> configurations) {
        List<ConsumerInstanceVO> sourceInstances = source.getInstances();
        List<ClientInstance> clients = sourceInstances == null
                ? List.of()
                : sourceInstances.stream()
                        .filter(Objects::nonNull)
                        .map(ClientInstance::from)
                        .toList();
        List<String> subscribedTopics = source.getSubscribedTopics();
        List<SubscriptionEntryVO> entries = subscriptionEntries == null
                ? List.of()
                : subscriptionEntries;
        return new GroupDescribeOutput(
                cluster,
                StringUtils.hasText(source.getName()) ? source.getName() : requestedGroup,
                source.getNamespace(),
                source.getInstanceId(),
                source.getSubscriptionMode(),
                source.getConsumeType(),
                source.getOnlineInstances(),
                source.getTotalLag(),
                subscribedTopics == null ? List.of() : subscribedTopics,
                source.getSubscriptionDataType(),
                source.getDeliveryOrderType(),
                configurations.size() == 1 ? source.getRetryMaxTimes() : null,
                source.getDelaySeconds(),
                entries.stream()
                        .filter(Objects::nonNull)
                        .map(Subscription::from)
                        .toList(),
                clients,
                health,
                configurations.stream().map(GroupListItem::from).toList());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Subscription(
            String topic,
            String expression,
            String type,
            String filterMode,
            String consistency) {

        static Subscription from(SubscriptionEntryVO source) {
            return new Subscription(
                    source.getTopic(),
                    source.getExpression(),
                    source.getType(),
                    source.getFilterMode(),
                    source.getConsistency());
        }
    }

    public record Health(String status, List<String> reasons) {
    }
}
