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

import org.apache.rocketmq.studio.provider.apache.AdminClient;
import org.apache.rocketmq.studio.provider.apache.MetadataProvider;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.util.StringUtils;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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
        if (!StringUtils.hasText(instanceId) && StringUtils.hasText(clusterId)) {
            // legacy cluster-scoped read kept for AI tool handlers
            return metadataProvider.listTopics(
                    normalizeFilter(clusterId), normalizeFilter(type), normalizeFilter(search));
        }
        return resolve(instanceId).listTopics(instanceId, normalizeFilter(type), normalizeFilter(search));
    }


    public TopicVO createTopic(TopicVO topic) {
        requireTopic(topic);
        return resolve(topic.getInstanceId()).createTopic(topic.getInstanceId(), topic);
    }


    public TopicVO updateTopic(TopicVO topic) {
        requireTopic(topic);
        return resolve(topic.getInstanceId()).updateTopic(topic.getInstanceId(), topic);
    }


    public void deleteTopic(String name) {
        deleteTopic(null, name);
    }

    public void deleteTopic(String instanceId, String name) {
        resolve(instanceId).deleteTopic(instanceId, name);
    }


    public List<BrokerRouteVO> getTopicRoutes(String name) {
        return getTopicRoutes(null, name);
    }

    public List<BrokerRouteVO> getTopicRoutes(String instanceId, String name) {
        String topicName = requireName(name, "topic name");
        if (resolve(instanceId).vendor() != InstanceVendor.APACHE) {
            // broker routing does not apply to serverless cloud instances
            return List.of();
        }
        return metadataProvider.getTopicRoutes(instanceId, topicName);
    }


    public List<TopicConsumerVO> getTopicConsumers(String name) {
        return getTopicConsumers(null, name);
    }

    public List<TopicConsumerVO> getTopicConsumers(String instanceId, String name) {
        String topicName = requireName(name, "topic name");
        return resolve(instanceId).getTopicConsumers(instanceId, topicName);
    }

    public TopicConsumerPageVO getTopicConsumersPage(String instanceId, String name, int page, int pageSize) {
        String topicName = requireName(name, "topic name");
        if (page < 1) {
            throw new BusinessException(400, "page must be greater than zero");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "pageSize must be between 1 and 100");
        }
        return resolve(instanceId).getTopicConsumersPage(instanceId, topicName, page, pageSize);
    }


    public SendMessageVO sendMessage(SendMessageDTO request) {
        requireSendMessageRequest(request);
        if (resolve(request.getInstanceId()).vendor() != InstanceVendor.APACHE) {
            throw new BusinessException(501, "Sending messages is not supported for cloud instances");
        }
        return adminClient.sendMessage(request);
    }

    // ── ConsumerGroupVO ───────────────────────────────────────────────


    public List<ConsumerGroupVO> listConsumerGroups(String clusterId, String search) {
        return listConsumerGroups(null, clusterId, search);
    }

    public List<ConsumerGroupVO> listConsumerGroups(String instanceId, String clusterId, String search) {
        if (!StringUtils.hasText(instanceId) && StringUtils.hasText(clusterId)) {
            return metadataProvider.listConsumerGroups(normalizeFilter(clusterId), normalizeFilter(search));
        }
        return resolve(instanceId).listConsumerGroups(instanceId, normalizeFilter(search));
    }


    public ConsumerGroupVO getConsumerGroup(String name) {
        return getConsumerGroup(null, name);
    }

    public ConsumerGroupVO getConsumerGroup(String instanceId, String name) {
        String groupName = requireName(name, "consumer group name");
        if (resolve(instanceId).vendor() != InstanceVendor.APACHE) {
            throw new BusinessException(501, "Consumer group detail is not supported for cloud instances");
        }
        return adminClient.getConsumerGroup(instanceId, groupName);
    }


    public List<QueueProgressVO> getGroupProgress(String name) {
        return getGroupProgress(null, name);
    }

    public List<QueueProgressVO> getGroupProgress(String instanceId, String name) {
        String groupName = requireName(name, "consumer group name");
        return resolve(instanceId).getGroupProgress(instanceId, groupName);
    }


    public List<SubscriptionEntryVO> getGroupSubscriptions(String name) {
        return getGroupSubscriptions(null, name);
    }

    public List<SubscriptionEntryVO> getGroupSubscriptions(String instanceId, String name) {
        String groupName = requireName(name, "consumer group name");
        return resolve(instanceId).getGroupSubscriptions(instanceId, groupName);
    }


    public ConsumerGroupVO createConsumerGroup(ConsumerGroupVO group) {
        String instanceId = group == null ? null : group.getInstanceId();
        return resolve(instanceId).createConsumerGroup(instanceId, group);
    }


    public void deleteConsumerGroup(String name) {
        deleteConsumerGroup(null, name);
    }

    public void deleteConsumerGroup(String instanceId, String name) {
        resolve(instanceId).deleteConsumerGroup(instanceId, name);
    }


    public void resetOffset(String name, long timestamp, String topic) {
        resetOffset(null, name, timestamp, topic);
    }

    public void resetOffset(String instanceId, String name, long timestamp, String topic) {
        resolve(instanceId).resetOffset(instanceId, name, timestamp, topic);
    }


    /**
     * Every metadata operation goes through the provider registry; a blank instance id keeps the
     * legacy global behavior by defaulting to the Apache provider.
     */
    private InstanceProvider resolve(String instanceId) {
        return providerRegistry.byInstanceId(instanceId)
                .orElseGet(() -> providerRegistry.forVendor(InstanceVendor.APACHE));
    }

    private String normalizeFilter(String value) {
        return !StringUtils.hasText(value) ? null : value.trim();
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

    private String requireName(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, fieldName + " is required");
        }
        return value.trim();
    }
}
