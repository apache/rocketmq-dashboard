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
package org.apache.rocketmq.studio.ops.ai.tool.contract.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.instance.group.ConsumerInstanceVO;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientInstance(
        String clientId,
        String protocol,
        String address,
        List<String> subscribedTopics,
        String lastHeartbeat,
        Map<String, Long> topicLag) {

    public static ClientInstance from(ConsumerInstanceVO instance) {
        return new ClientInstance(
                instance.getClientId(),
                instance.getProtocol() != null ? instance.getProtocol().name() : "UNKNOWN",
                instance.getAddress(),
                Objects.requireNonNullElseGet(instance.getSubscribedTopics(), List::of),
                instance.getLastHeartbeat() == null ? null : instance.getLastHeartbeat().toString(),
                Objects.requireNonNullElseGet(instance.getTopicLag(), Map::of));
    }
}
