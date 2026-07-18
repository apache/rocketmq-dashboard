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
package com.rocketmq.studio.instance.topic;

import com.rocketmq.studio.instance.group.ConsumerGroupVO;
import com.rocketmq.studio.instance.group.QueueProgressVO;
import com.rocketmq.studio.instance.group.SubscriptionEntryVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataService {

    private final MetadataProvider metadataProvider;
    private final AdminClient adminClient;

    // ── TopicVO ───────────────────────────────────────────────────────


    public List<TopicVO> listTopics(String clusterId, String type, String search) {
        return metadataProvider.listTopics(clusterId, type, search);
    }


    public TopicVO createTopic(TopicVO topic) {
        return adminClient.createTopic(topic);
    }


    public TopicVO updateTopic(TopicVO topic) {
        return adminClient.updateTopic(topic);
    }


    public void deleteTopic(String name) {
        adminClient.deleteTopic(name);
    }


    public List<BrokerRouteVO> getTopicRoutes(String name) {
        return metadataProvider.getTopicRoutes(name);
    }


    public List<TopicConsumerVO> getTopicConsumers(String name) {
        return metadataProvider.getTopicConsumers(name);
    }


    public SendMessageVO sendMessage(SendMessageDTO request) {
        return adminClient.sendMessage(request);
    }

    // ── ConsumerGroupVO ───────────────────────────────────────────────


    public List<ConsumerGroupVO> listConsumerGroups(String clusterId, String search) {
        return metadataProvider.listConsumerGroups(clusterId, search);
    }


    public ConsumerGroupVO getConsumerGroup(String name) {
        return adminClient.getConsumerGroup(name);
    }


    public List<QueueProgressVO> getGroupProgress(String name) {
        return metadataProvider.getGroupProgress(name);
    }


    public List<SubscriptionEntryVO> getGroupSubscriptions(String name) {
        return metadataProvider.getGroupSubscriptions(name);
    }


    public ConsumerGroupVO createConsumerGroup(ConsumerGroupVO group) {
        return adminClient.createConsumerGroup(group);
    }


    public void deleteConsumerGroup(String name) {
        adminClient.deleteConsumerGroup(name);
    }


    public void resetOffset(String name, long timestamp, String topic) {
        adminClient.resetOffset(name, timestamp, topic);
    }

    // ── NamespaceVO ───────────────────────────────────────────────────


    public List<NamespaceVO> listNamespaces() {
        return metadataProvider.listTopics(null, null, null).stream()
                .filter(topic -> topic.getNamespace() != null && !topic.getNamespace().isBlank())
                .map(topic -> {
                    NamespaceVO namespace = new NamespaceVO();
                    namespace.setName(topic.getNamespace());
                    namespace.setClusterId(topic.getClusterId());
                    return namespace;
                })
                .collect(Collectors.toMap(
                        namespace -> new NamespaceKey(namespace.getName(), namespace.getClusterId()),
                        Function.identity(),
                        (first, ignored) -> first))
                .values().stream()
                .sorted(Comparator.comparing(NamespaceVO::getName)
                        .thenComparing(namespace -> Objects.toString(namespace.getClusterId(), "")))
                .toList();
    }

    private record NamespaceKey(String name, String clusterId) {
    }
}
