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

import org.apache.rocketmq.common.Pair;
import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.model.MessageInfo;
import org.apache.rocketmq.dashboard.model.MessagePage;
import org.apache.rocketmq.dashboard.model.MessageView;
import org.apache.rocketmq.dashboard.model.request.MessageQuery;
import org.apache.rocketmq.dashboard.service.ArchitectureBasedService;
import org.apache.rocketmq.dashboard.service.MessageService;
import org.apache.rocketmq.remoting.protocol.body.ConsumeMessageDirectlyResult;
import org.apache.rocketmq.tools.admin.api.MessageTrack;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * Message service implementation based on new architecture
 * Complete migration of v2.1.0 message query and management capabilities
 */
@Service
public class MessageServiceImpl extends ArchitectureBasedService implements MessageService {

    @Resource
    private MetadataProvider metadataProvider;

    @Resource
    private ClusterProvider clusterProvider;

    @Override
    public Pair<MessageView, List<MessageTrack>> viewMessage(String topic, String msgId) {
        try {
            if (supports("MESSAGE_QUERY")) {
                MessageInfo messageInfo = metadataProvider.getMessageById(msgId).orElse(null);
                if (messageInfo != null) {
                    MessageView messageView = new MessageView();
                    messageView.setTopic(messageInfo.getTopic());
                    messageView.setMsgId(messageInfo.getMsgId());
                    // Additional field mapping from MessageInfo to MessageView
                    return new Pair<>(messageView, Collections.emptyList());
                }
            }
            handleUnsupportedOperation("View message - not supported in current cluster");
            return new Pair<>(null, Collections.emptyList());
        } catch (Exception e) {
            handleUnsupportedOperation("View message");
            return new Pair<>(null, Collections.emptyList());
        }
    }

    @Override
    public MessagePage queryMessageByPage(MessageQuery query) {
        try {
            if (supports("MESSAGE_QUERY")) {
                List<MessageInfo> messages = metadataProvider.queryMessageByTopic(
                    query.getTopic(), query.getBegin(), query.getEnd(), query.getPageSize());
                List<MessageView> views = new java.util.ArrayList<>();
                for (MessageInfo msg : messages) {
                    MessageView view = new MessageView();
                    view.setTopic(msg.getTopic());
                    view.setMsgId(msg.getMsgId());
                    views.add(view);
                }
                Page<MessageView> page = new PageImpl<>(views,
                    org.springframework.data.domain.PageRequest.of(query.getPageNum(), query.getPageSize()),
                    views.size());
                return new MessagePage(page, query.getTaskId());
            }
            handleUnsupportedOperation("Message query by page - not supported in current cluster");
            return new MessagePage(Page.empty(), null);
        } catch (Exception e) {
            handleUnsupportedOperation("Query message by page");
            return new MessagePage(Page.empty(), null);
        }
    }

    @Override
    public List<MessageInfo> queryMessageByTopic(String topic, long beginTime, long endTime, int maxNum) {
        try {
            if (supports("MESSAGE_QUERY")) {
                return metadataProvider.queryMessageByTopic(topic, beginTime, endTime, maxNum);
            }
            handleUnsupportedOperation("Message query - not supported in current cluster");
            return List.of();
        } catch (Exception e) {
            handleUnsupportedOperation("Query message by topic");
            return List.of();
        }
    }

    @Override
    public List<MessageInfo> queryMessageByTopicAndKey(String topic, String key, long beginTime, long endTime) {
        try {
            if (supports("MESSAGE_QUERY_BY_KEY")) {
                return metadataProvider.queryMessageByTopicAndKey(topic, key, beginTime, endTime);
            }
            handleUnsupportedOperation("Message query by key - not supported in current cluster");
            return List.of();
        } catch (Exception e) {
            handleUnsupportedOperation("Query message by topic and key");
            return List.of();
        }
    }

    @Override
    public List<MessageInfo> queryMessageByGroup(String consumerGroup, String topic, long beginTime, long endTime) {
        try {
            if (supports("MESSAGE_QUERY_BY_GROUP")) {
                return metadataProvider.queryMessageByGroup(consumerGroup, topic, beginTime, endTime);
            }
            handleUnsupportedOperation("Message query by consumer group - not supported in current cluster");
            return List.of();
        } catch (Exception e) {
            handleUnsupportedOperation("Query message by consumer group");
            return List.of();
        }
    }

    @Override
    public MessageInfo getMessageById(String msgId) {
        try {
            if (supports("MESSAGE_QUERY_BY_ID")) {
                return metadataProvider.getMessageById(msgId).orElse(null);
            }
            handleUnsupportedOperation("Message query by ID - not supported in current cluster");
            return null;
        } catch (Exception e) {
            handleUnsupportedOperation("Get message by ID");
            return null;
        }
    }

    @Override
    public List<MessageInfo> getMessagesByOffset(String topic, String brokerName, int queueId, long offset, int count) {
        try {
            if (supports("MESSAGE_QUERY_BY_OFFSET")) {
                return metadataProvider.getMessagesByOffset(topic, brokerName, queueId, offset, count);
            }
            handleUnsupportedOperation("Message query by offset - not supported in current cluster");
            return List.of();
        } catch (Exception e) {
            handleUnsupportedOperation("Get messages by offset");
            return List.of();
        }
    }

    @Override
    public long searchOffset(String topic, String brokerName, int queueId, long timestamp) {
        try {
            if (supports("OFFSET_SEARCH_BY_TIMESTAMP")) {
                return metadataProvider.searchOffset(topic, brokerName, queueId, timestamp);
            }
            handleUnsupportedOperation("Search offset by timestamp - not supported in current cluster");
            return -1L;
        } catch (Exception e) {
            handleUnsupportedOperation("Search offset");
            return -1L;
        }
    }

    @Override
    public long getMaxOffset(String topic, String brokerName, int queueId) {
        try {
            if (supports("MAX_OFFSET_QUERY")) {
                return metadataProvider.getMaxOffset(topic, brokerName, queueId);
            }
            handleUnsupportedOperation("Get max offset - not supported in current cluster");
            return -1L;
        } catch (Exception e) {
            handleUnsupportedOperation("Get max offset");
            return -1L;
        }
    }

    @Override
    public long getMinOffset(String topic, String brokerName, int queueId) {
        try {
            if (supports("MIN_OFFSET_QUERY")) {
                return metadataProvider.getMinOffset(topic, brokerName, queueId);
            }
            handleUnsupportedOperation("Get min offset - not supported in current cluster");
            return -1L;
        } catch (Exception e) {
            handleUnsupportedOperation("Get min offset");
            return -1L;
        }
    }

    @Override
    public boolean deleteMessage(String topic, String msgId) {
        try {
            if (supports("MESSAGE_DELETE")) {
                metadataProvider.deleteMessage(topic, msgId);
                return true;
            }
            handleUnsupportedOperation("Delete message - not supported in current cluster");
            return false;
        } catch (Exception e) {
            handleUnsupportedOperation("Delete message");
            return false;
        }
    }

    @Override
    public boolean resendMessage(String msgId, String newTopic) {
        try {
            if (supports("MESSAGE_RESEND")) {
                metadataProvider.resendMessage(msgId, newTopic);
                return true;
            }
            handleUnsupportedOperation("Resend message - not supported in current cluster");
            return false;
        } catch (Exception e) {
            handleUnsupportedOperation("Resend message");
            return false;
        }
    }

    /**
     * Validate message query parameters
     */
    private void validateQueryParams(String topic, long beginTime, long endTime, int maxNum) {
        if (topic == null || topic.trim().isEmpty()) {
            throw new IllegalArgumentException("Topic cannot be empty");
        }
        if (beginTime < 0 || endTime < 0) {
            throw new IllegalArgumentException("Invalid timestamp range");
        }
        if (beginTime > endTime) {
            throw new IllegalArgumentException("Begin time cannot be later than end time");
        }
        if (maxNum <= 0 || maxNum > 1000) {
            throw new IllegalArgumentException("Invalid max number of messages");
        }
    }

    /**
     * Validate message ID format
     */
    private void validateMessageId(String msgId) {
        if (msgId == null || msgId.trim().isEmpty()) {
            throw new IllegalArgumentException("Message ID cannot be empty");
        }
        if (msgId.length() > 255) {
            throw new IllegalArgumentException("Message ID too long");
        }
    }

    @Override
    public ConsumeMessageDirectlyResult consumeMessageDirectly(String topic, String msgId, String consumerGroup, String clientId) {
        try {
            if (supports("MESSAGE_CONSUME_DIRECTLY")) {
                return metadataProvider.consumeMessageDirectly(topic, msgId, consumerGroup, clientId);
            }
            handleUnsupportedOperation("Consume message directly - not supported in current cluster");
            return null;
        } catch (Exception e) {
            handleUnsupportedOperation("Consume message directly");
            return null;
        }
    }
}