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

import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.util.StringUtils;

/**
 * Dispatch rules for sending a message to a topic whose registered type is known. Shared by the
 * REST send path ({@code MetadataService.sendMessage}) and the AI message-send tool so a FIFO
 * topic cannot receive an un-grouped message through one surface while the other refuses it.
 */
public final class MessageSendPolicies {

    private MessageSendPolicies() {
    }

    /**
     * The registered topic type is the single source of truth; unknown topics (no registered
     * entry) keep the plain-send behavior.
     */
    public static void validateForTopicType(TopicType topicType, String messageGroup, Long deliveryTimestamp) {
        if (topicType == null) {
            return;
        }
        switch (topicType) {
            case FIFO -> {
                if (!StringUtils.hasText(messageGroup)) {
                    throw new BusinessException(400, "messageGroup is required for FIFO topic");
                }
            }
            case DELAY -> {
                if (deliveryTimestamp == null) {
                    throw new BusinessException(400, "deliveryTimestamp is required for DELAY topic");
                }
                if (deliveryTimestamp <= System.currentTimeMillis()) {
                    throw new BusinessException(400, "deliveryTimestamp must be in the future for DELAY topic");
                }
            }
            case TRANSACTION -> throw new BusinessException(400, "sending transaction messages is not supported");
            default -> {
            }
        }
    }
}
