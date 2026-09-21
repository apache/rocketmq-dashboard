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
package org.apache.rocketmq.studio.ops.ai.tool.contract.litetopic;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.instance.topic.LiteTopicSessionVO;

import java.util.List;

/**
 * One LiteTopic session with its per-topic entries. Fields the broker cannot report stay
 * absent rather than being invented.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LiteTopicSessionOutput(
        String sessionId,
        String clientId,
        String clientAddress,
        String parentTopic,
        String consumerGroup,
        Long createTime,
        Long lastActiveTime,
        Long ttl,
        Long ttlRemaining,
        String status,
        Long totalMessages,
        Long consumedMessages,
        Long pendingMessages,
        Integer popProgress,
        Integer liteTopicCreationCount,
        List<Entry> liteTopics) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Entry(String topicName, String status, Long ttlRemaining) {
    }

    public static LiteTopicSessionOutput from(LiteTopicSessionVO vo) {
        return new LiteTopicSessionOutput(
                vo.getSessionId(),
                vo.getClientId(),
                vo.getClientAddress(),
                vo.getParentTopic(),
                vo.getConsumerGroup(),
                vo.getCreateTime(),
                vo.getLastActiveTime(),
                vo.getTtl(),
                vo.getTtlRemaining(),
                vo.getStatus(),
                vo.getTotalMessages(),
                vo.getConsumedMessages(),
                vo.getPendingMessages(),
                vo.getPopProgress(),
                vo.getLiteTopicCreationCount(),
                vo.getLiteTopics() == null
                        ? null
                        : vo.getLiteTopics().stream()
                                .map(topic -> new Entry(
                                        topic.getTopicName(), topic.getStatus(), topic.getTtlRemaining()))
                                .toList());
    }
}
