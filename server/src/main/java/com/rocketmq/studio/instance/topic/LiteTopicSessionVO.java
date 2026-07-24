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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
public class LiteTopicSessionVO {
    private String sessionId;
    private String clientId;
    private String clientAddress;
    private String parentTopic;
    private String consumerGroup;
    private Long createTime;
    private Long lastActiveTime;
    private Long ttl;
    private Long ttlRemaining;
    private String status;
    private Long totalMessages;
    private Long consumedMessages;
    private Long pendingMessages;
    private Integer popProgress;
    private Integer liteTopicCreationCount;
    private List<SessionLiteTopic> liteTopics;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionLiteTopic {
        private String topicName;
        private String status;
        private Long ttlRemaining;
    }
}
