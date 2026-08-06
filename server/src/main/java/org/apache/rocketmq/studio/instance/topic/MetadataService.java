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
package org.apache.rocketmq.studio.instance.topic;

import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataService {

    private final MetadataProvider metadataProvider;
    private final AdminClient adminClient;
    private final InstanceProviderRegistry providerRegistry;

    // ── TopicVO ───────────────────────────────────────────────────────


    public List<TopicVO> listTopics(String clusterId, String type, String search) {
        return listTopics(null, clusterId, type, search);
    }

    public List<TopicVO> listTopics(String instanceId, String clusterId, String type, String search) {
        return providerFor(instanceId)
                .map(provider -> provider.listTopics(instanceId, normalizeFilter(type), normalizeFilter(search)))
                .orElseGet(() -> metadataProvider.listTopics(
                        normalizeFilter(clusterId),
                        normalizeFilter(type),
                        normalizeFilter(search)));
    }


    public TopicVO createTopic(TopicVO topic) {
        requireTopic(topic);
        return providerFor(topic.getInstanceId())
                .map(provider -> provider.createTopic(topic.getInstanceId(), topic))
                .orElseGet(() -> adminClient.createTopic(topic));
    }


    public TopicVO updateTopic(TopicVO topic) {
        requireTopic(topic);
        return providerFor(topic.getInstanceId())
                .map(provider -> provider.updateTopic(topic.getInstanceId(), topic))
                .orElseGet(() -> adminClient.updateTopic(topic));
    }


    public void deleteTopic(String name) {
        deleteTopic(null, name);
    }

    public void deleteTopic(String instanceId, String name) {
        Optional<InstanceProvider> provider = providerFor(instanceId);
        if (provider.isPresent()) {
            provider.get().deleteTopic(instanceId, name);
        } else {
            adminClient.deleteTopic(instanceId, name);
        }
    }


    public List<BrokerRouteVO> getTopicRoutes(String name) {
        return getTopicRoutes(null, name);
    }

    public List<BrokerRouteVO> getTopicRoutes(String instanceId, String name) {
        Optional<InstanceProvider> provider = providerFor(instanceId);
        if (provider.isPresent() && provider.get().vendor() != InstanceVendor.APACHE) {
            // broker routing does not apply to serverless cloud instances
            return List.of();
        }
        return metadataProvider.getTopicRoutes(name);
    }


    public List<TopicConsumerVO> getTopicConsumers(String name) {
        return getTopicConsumers(null, name);
    }

    public List<TopicConsumerVO> getTopicConsumers(String instanceId, String name) {
        return providerFor(instanceId)
                .map(provider -> provider.getTopicConsumers(instanceId, name))
                .orElseGet(() -> metadataProvider.getTopicConsumers(name));
    }


    public SendMessageVO sendMessage(SendMessageDTO request) {
        requireSendMessageRequest(request);
        providerFor(request.getInstanceId()).ifPresent(provider -> {
            if (provider.vendor() != InstanceVendor.APACHE) {
                throw new BusinessException(501, "Sending messages is not supported for cloud instances");
            }
        });
        return adminClient.sendMessage(request);
    }

    // ── ConsumerGroupVO ───────────────────────────────────────────────


    public List<ConsumerGroupVO> listConsumerGroups(String clusterId, String search) {
        return listConsumerGroups(null, clusterId, search);
    }

    public List<ConsumerGroupVO> listConsumerGroups(String instanceId, String clusterId, String search) {
        return providerFor(instanceId)
                .map(provider -> provider.listConsumerGroups(instanceId, normalizeFilter(search)))
                .orElseGet(() -> metadataProvider.listConsumerGroups(
                        normalizeFilter(clusterId), normalizeFilter(search)));
    }


    public ConsumerGroupVO getConsumerGroup(String name) {
        return getConsumerGroup(null, name);
    }

    public ConsumerGroupVO getConsumerGroup(String instanceId, String name) {
        providerFor(instanceId).ifPresent(provider -> {
            if (provider.vendor() != InstanceVendor.APACHE) {
                throw new BusinessException(501, "Consumer group detail is not supported for cloud instances");
            }
        });
        return adminClient.getConsumerGroup(name);
    }


    public List<QueueProgressVO> getGroupProgress(String name) {
        return getGroupProgress(null, name);
    }

    public List<QueueProgressVO> getGroupProgress(String instanceId, String name) {
        return providerFor(instanceId)
                .map(provider -> provider.getGroupProgress(instanceId, name))
                .orElseGet(() -> metadataProvider.getGroupProgress(name));
    }


    public List<SubscriptionEntryVO> getGroupSubscriptions(String name) {
        return getGroupSubscriptions(null, name);
    }

    public List<SubscriptionEntryVO> getGroupSubscriptions(String instanceId, String name) {
        return providerFor(instanceId)
                .map(provider -> provider.getGroupSubscriptions(instanceId, name))
                .orElseGet(() -> metadataProvider.getGroupSubscriptions(name));
    }


    public ConsumerGroupVO createConsumerGroup(ConsumerGroupVO group) {
        return providerFor(group == null ? null : group.getInstanceId())
                .map(provider -> provider.createConsumerGroup(group.getInstanceId(), group))
                .orElseGet(() -> adminClient.createConsumerGroup(group));
    }


    public void deleteConsumerGroup(String name) {
        deleteConsumerGroup(null, name);
    }

    public void deleteConsumerGroup(String instanceId, String name) {
        Optional<InstanceProvider> provider = providerFor(instanceId);
        if (provider.isPresent()) {
            provider.get().deleteConsumerGroup(instanceId, name);
        } else {
            adminClient.deleteConsumerGroup(name);
        }
    }


    public void resetOffset(String name, long timestamp, String topic) {
        resetOffset(null, name, timestamp, topic);
    }

    public void resetOffset(String instanceId, String name, long timestamp, String topic) {
        Optional<InstanceProvider> provider = providerFor(instanceId);
        if (provider.isPresent()) {
            provider.get().resetOffset(instanceId, name, timestamp, topic);
        } else {
            adminClient.resetOffset(name, timestamp, topic);
        }
    }

    // ── NamespaceVO ───────────────────────────────────────────────────


    public List<NamespaceVO> listNamespaces() {
        throw new BusinessException(501, "Namespace discovery is not implemented by the current metadata provider");
    }

    private Optional<InstanceProvider> providerFor(String instanceId) {
        return providerRegistry.byInstanceId(instanceId);
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requireTopic(TopicVO topic) {
        if (topic == null) {
            throw new BusinessException(400, "Topic request is required");
        }
    }

    private void requireSendMessageRequest(SendMessageDTO request) {
        if (request == null) {
            throw new BusinessException(400, "Topic send message request is required");
        }
    }
}
