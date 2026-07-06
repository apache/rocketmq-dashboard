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
package org.apache.rocketmq.dashboard.service;

import org.apache.rocketmq.dashboard.model.MessageInfo;
import org.apache.rocketmq.dashboard.model.MessagePage;
import org.apache.rocketmq.dashboard.model.MessageView;
import org.apache.rocketmq.dashboard.model.request.MessageQuery;
import org.apache.rocketmq.remoting.protocol.body.ConsumeMessageDirectlyResult;
import org.apache.rocketmq.tools.admin.api.MessageTrack;

import org.apache.rocketmq.common.Pair;
import java.util.List;

/**
 * Message query and management service interface
 */
public interface MessageService {

    /**
     * View message detail by topic and msgId
     */
    Pair<MessageView, List<MessageTrack>> viewMessage(String topic, String msgId);

    /**
     * Query messages by topic with pagination
     */
    MessagePage queryMessageByPage(MessageQuery query);

    /**
     * Query messages by topic
     */
    List<MessageInfo> queryMessageByTopic(String topic, long beginTime, long endTime, int maxNum);

    /**
     * Query messages by topic (simplified, default maxNum)
     */
    default List<MessageInfo> queryMessageByTopic(String topic, long beginTime, long endTime) {
        return queryMessageByTopic(topic, beginTime, endTime, 64);
    }

    /**
     * Query messages by topic and message key
     */
    List<MessageInfo> queryMessageByTopicAndKey(String topic, String key, long beginTime, long endTime);

    /**
     * Query messages by topic and key (simplified, uses default time range)
     */
    default List<MessageInfo> queryMessageByTopicAndKey(String topic, String key) {
        return queryMessageByTopicAndKey(topic, key, System.currentTimeMillis() - 7 * 24 * 3600 * 1000L, System.currentTimeMillis());
    }

    /**
     * Query messages by consumer group
     */
    List<MessageInfo> queryMessageByGroup(String consumerGroup, String topic, long beginTime, long endTime);

    /**
     * Get message by ID
     */
    MessageInfo getMessageById(String msgId);

    /**
     * Get messages by offset
     */
    List<MessageInfo> getMessagesByOffset(String topic, String brokerName, int queueId, long offset, int count);

    /**
     * Search offset by timestamp
     */
    long searchOffset(String topic, String brokerName, int queueId, long timestamp);

    /**
     * Get maximum offset
     */
    long getMaxOffset(String topic, String brokerName, int queueId);

    /**
     * Get minimum offset
     */
    long getMinOffset(String topic, String brokerName, int queueId);

    /**
     * Delete message
     */
    boolean deleteMessage(String topic, String msgId);

    /**
     * Resend message to new topic
     */
    boolean resendMessage(String msgId, String newTopic);

    /**
     * Consume message directly for testing
     */
    ConsumeMessageDirectlyResult consumeMessageDirectly(String topic, String msgId, String consumerGroup, String clientId);
}