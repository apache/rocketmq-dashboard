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
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregated consumer group detail (decision 11): the former describe output plus the
 * progress and clients blocks of the deleted consume_progress/clients tools. The optional
 * topic filter applies to both blocks; without online consumers they stay empty, never null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupDetailOutput(
        String instanceId,
        String group,
        SubscriptionMode subscriptionMode,
        ConsumeType consumeType,
        /** Confirmed online clients, or -1 when the connection inventory is unavailable. */
        int onlineInstances,
        long totalLag,
        List<String> subscribedTopics,
        String subscriptionDataType,
        String deliveryOrderType,
        Integer retryMaxTimes,
        int delaySeconds,
        List<Subscription> subscriptions,
        List<Instance> instances,
        Health health,
        List<GroupListItem> configurations,
        Progress progress,
        Clients clients) {

    public static GroupDetailOutput from(
            ConsumerGroupVO source,
            String instanceId,
            String requestedGroup,
            List<SubscriptionEntryVO> subscriptionEntries,
            Health health,
            List<ConsumerGroupVO> configurations,
            List<QueueProgressVO> progress,
            String topicFilter) {
        List<ConsumerInstanceVO> sourceInstances = source.getInstances() == null
                ? List.<ConsumerInstanceVO>of()
                : source.getInstances().stream().filter(Objects::nonNull).toList();
        List<String> subscribedTopics = source.getSubscribedTopics();
        List<SubscriptionEntryVO> entries = subscriptionEntries == null
                ? List.of()
                : subscriptionEntries;
        return new GroupDetailOutput(
                instanceId,
                StringUtils.hasText(source.getName()) ? source.getName() : requestedGroup,
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
                sourceInstances.stream()
                        .map(Instance::from)
                        .toList(),
                health,
                configurations.stream().map(GroupListItem::from).toList(),
                Progress.from(progress, topicFilter),
                Clients.from(sourceInstances, topicFilter));
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Instance(
            String clientId,
            String protocol,
            String address,
            List<String> subscribedTopics,
            String lastHeartbeat,
            Map<String, Long> topicLag) {

        static Instance from(ConsumerInstanceVO source) {
            return new Instance(
                    source.getClientId(),
                    source.getProtocol() != null ? source.getProtocol().name() : "UNKNOWN",
                    source.getAddress(),
                    Objects.requireNonNullElseGet(source.getSubscribedTopics(), List::of),
                    source.getLastHeartbeat() == null ? null : source.getLastHeartbeat().toString(),
                    Objects.requireNonNullElseGet(source.getTopicLag(), Map::of));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Progress(
            long totalLag,
            List<QueueProgress> queues) {

        static Progress from(List<QueueProgressVO> progress, String topicFilter) {
            List<QueueProgressVO> source = progress == null ? List.of() : progress;
            if (StringUtils.hasText(topicFilter)) {
                source = source.stream()
                        .filter(queue -> queue != null && topicFilter.equals(queue.getTopic()))
                        .toList();
            }
            List<QueueProgress> queues = source.stream()
                    .filter(Objects::nonNull)
                    .map(QueueProgress::from)
                    .toList();
            long totalLag = queues.stream().anyMatch(queue -> queue.lag() == -1L)
                    ? -1L
                    : queues.stream().mapToLong(QueueProgress::lag).sum();
            return new Progress(totalLag, queues);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QueueProgress(
            String broker,
            int queueId,
            long brokerOffset,
            long consumerOffset,
            long lag) {

        static QueueProgress from(QueueProgressVO source) {
            return new QueueProgress(
                    source.getBroker(),
                    source.getQueueId(),
                    source.getBrokerOffset(),
                    source.getConsumerOffset(),
                    source.getDiffTotal());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Clients(
            int totalClients,
            List<Client> clients) {

        static Clients from(List<ConsumerInstanceVO> instances, String topicFilter) {
            List<Client> clients = instances.stream()
                    .filter(instance -> matchesTopic(instance, topicFilter))
                    .map(Client::from)
                    .toList();
            return new Clients(clients.size(), clients);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Client(
            String clientId,
            String protocol,
            String address,
            String language,
            String version,
            Boolean active,
            List<String> subscribedTopics,
            String lastHeartbeat,
            Map<String, Long> topicLag) {

        static Client from(ConsumerInstanceVO source) {
            return new Client(
                    source.getClientId(),
                    source.getProtocol() != null ? source.getProtocol().name() : "UNKNOWN",
                    source.getAddress(),
                    null,
                    null,
                    null,
                    Objects.requireNonNullElseGet(source.getSubscribedTopics(), List::of),
                    source.getLastHeartbeat() == null ? null : source.getLastHeartbeat().toString(),
                    Objects.requireNonNullElseGet(source.getTopicLag(), Map::of));
        }
    }

    static boolean matchesTopic(ConsumerInstanceVO instance, String topicFilter) {
        if (!StringUtils.hasText(topicFilter)) {
            return true;
        }
        List<String> subscribedTopics = instance.getSubscribedTopics();
        if (subscribedTopics != null && subscribedTopics.contains(topicFilter)) {
            return true;
        }
        return instance.getTopicLag() != null && instance.getTopicLag().containsKey(topicFilter);
    }
}
