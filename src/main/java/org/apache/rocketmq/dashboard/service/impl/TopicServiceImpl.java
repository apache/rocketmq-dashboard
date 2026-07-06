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
package org.apache.rocketmq.dashboard.service.impl;

import jakarta.annotation.Resource;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.request.TopicConfigInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.rocketmq.dashboard.model.TopicInfo;
import org.apache.rocketmq.dashboard.model.TopicType;
import org.apache.rocketmq.dashboard.service.ArchitectureBasedService;
import org.apache.rocketmq.dashboard.service.TopicService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Topic service implementation based on new architecture
 * Complete migration of v2.1.0 Topic management capabilities
 */
@Service
public class TopicServiceImpl extends ArchitectureBasedService implements TopicService {

    private static final Logger log = LoggerFactory.getLogger(TopicServiceImpl.class);

    @Resource
    private org.apache.rocketmq.tools.admin.MQAdminExt mqAdminExt;

    @Override
    public ClusterCapability getClusterCapability() {
        return super.getClusterCapability();
    }

    @Override
    public List<String> getTopicList() {
        try {
            return metadataProvider.listTopics(Optional.of(getDefaultNamespace()))
                .stream()
                .map(TopicInfo::getTopicName)
                .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get topic list", e);
        }
    }

    @Override
    public TopicInfo getTopicInfo(String topic) {
        try {
            return metadataProvider.getTopic(topic, Optional.of(getDefaultNamespace()))
                .orElse(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get topic info for: " + topic, e);
        }
    }

    @Override
    public boolean createTopic(String topic, int readQueueNums, int writeQueueNums, int perm) {
        return createTopicWithType(topic, readQueueNums, writeQueueNums, perm, TopicType.NORMAL);
    }

    @Override
    public boolean createTopicWithType(String topic, int readQueueNums, int writeQueueNums, int perm, TopicType topicType) {
        try {
            // Validate topic type support
            if (topicType != TopicType.NORMAL && !clusterCapability.getSupportedTopicTypes().contains(topicType)) {
                throw new IllegalArgumentException("Topic type " + topicType + " is not supported in current cluster");
            }

            TopicInfo topicInfo = new TopicInfo();
            topicInfo.setTopicName(topic);
            topicInfo.setNamespace(getDefaultNamespace());
            topicInfo.setTopicType(topicType);
            topicInfo.setReadQueueNums(readQueueNums);
            topicInfo.setWriteQueueNums(writeQueueNums);
            topicInfo.setPerm(perm);
            topicInfo.setOrderTopic(TopicType.FIFO.equals(topicType));

            // Type-specific configuration
            switch (topicType) {
                case FIFO:
                    topicInfo.getAttributes().put("FIFO_TIMEOUT", "3600");
                    break;
                case DELAY:
                    topicInfo.getAttributes().put("DELAY_LEVEL", "1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h");
                    break;
                case TRANSACTION:
                    topicInfo.getAttributes().put("TRANSACTION_TIMEOUT", "60");
                    break;
                case LITE:
                    if (!supportsLiteTopic()) {
                        handleUnsupportedOperation("LiteTopic creation");
                    }
                    topicInfo.getAttributes().put("LITE_TTL", "3600000"); // 1 hour default TTL
                    break;
            }

            metadataProvider.createTopic(topicInfo);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create topic: " + topic, e);
        }
    }

    @Override
    public boolean updateTopic(String topic, int readQueueNums, int writeQueueNums, int perm) {
        try {
            TopicInfo topicInfo = new TopicInfo();
            topicInfo.setTopicName(topic);
            topicInfo.setNamespace(getDefaultNamespace());
            topicInfo.setReadQueueNums(readQueueNums);
            topicInfo.setWriteQueueNums(writeQueueNums);
            topicInfo.setPerm(perm);

            metadataProvider.updateTopic(topicInfo);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to update topic: " + topic, e);
        }
    }

    @Override
    public void createOrUpdate(TopicConfigInfo topicConfigInfo) {
        try {
            TopicInfo topicInfo = new TopicInfo();
            topicInfo.setTopicName(topicConfigInfo.getTopicName());
            topicInfo.setNamespace(getDefaultNamespace());
            topicInfo.setReadQueueNums(topicConfigInfo.getReadQueueNums());
            topicInfo.setWriteQueueNums(topicConfigInfo.getWriteQueueNums());
            topicInfo.setPerm(topicConfigInfo.getPerm());
            topicInfo.setOrderTopic(topicConfigInfo.isOrder());

            if (topicConfigInfo.getMessageType() != null) {
                try {
                    topicInfo.setTopicType(TopicType.valueOf(topicConfigInfo.getMessageType()));
                } catch (IllegalArgumentException e) {
                    topicInfo.setTopicType(TopicType.NORMAL);
                }
            } else  {
                topicInfo.setTopicType(TopicType.NORMAL);
            }

            if (metadataProvider.getTopic(topicConfigInfo.getTopicName(), Optional.of(getDefaultNamespace())).isPresent()) {
                metadataProvider.updateTopic(topicInfo);
            } else {
                metadataProvider.createTopic(topicInfo);
            }
         } catch (Exception e) {
            throw new RuntimeException("Failed to create or update topic: " + topicConfigInfo.getTopicName(), e);
        }
    }

    @Override
    public boolean deleteTopic(String topic) {
        try {
            metadataProvider.deleteTopic(topic, Optional.of(getDefaultNamespace()));
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete topic: " + topic, e);
        }
    }

    @Override
    public List<String> getTopicClusterList(String topic) {
        if (isV4Architecture()) {
            try {
                return getClusterTopology().getBrokerNodes()
                    .stream()
                    .map(node -> node.getNodeName())
                    .distinct()
                    .toList();
            } catch (Exception e) {
                log.warn("Failed to get cluster topology: {}", e.getMessage());
                return List.of();
            }
        }
        // V5 architecture can get from Namespace
        return List.of("default-cluster");
    }

    @Override
    public boolean isTopicExist(String topic) {
        try {
            return metadataProvider.getTopic(topic, Optional.of(getDefaultNamespace())).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public TopicInfo getTopicWithNamespace(String topic, String namespace) {
        try {
            return metadataProvider.getTopic(topic, Optional.ofNullable(namespace))
                .orElse(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get topic info for: " + topic, e);
        }
    }

    @Override
    public List<TopicInfo> getAllTopicList() {
        try {
            return metadataProvider.listTopics(Optional.of(getDefaultNamespace()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to get all topic list", e);
        }
    }

    @Override
    public List<String> getTopicsByCluster(String clusterName) {
        try {
            // Simplified implementation, return all topics
            return metadataProvider.listTopics(Optional.of(getDefaultNamespace()))
                .stream()
                .map(TopicInfo::getTopicName)
                .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get topics by cluster: " + clusterName, e);
        }
    }

    @Override
    public List<TopicType> getSupportedTopicTypes() {
        return clusterCapability.getSupportedTopicTypes().stream().toList();
    }

    /**
     * Check if topic is system topic
     */
    private boolean isSystemTopic(String topic) {
        return topic.startsWith("%") || topic.startsWith("RMQ_SYS") || topic.startsWith("SELF_TEST");
    }
}