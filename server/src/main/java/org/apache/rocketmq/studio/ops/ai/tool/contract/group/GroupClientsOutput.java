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
package org.apache.rocketmq.studio.ops.ai.tool.contract.group;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.ConsumerInstanceVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.client.ClientInstance;
import org.springframework.util.StringUtils;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupClientsOutput(
        String cluster,
        String group,
        String topic,
        int totalClients,
        List<ClientInstance> clients) {

    public static GroupClientsOutput from(
            ConsumerGroupVO group,
            String cluster,
            String requestedGroup,
            String topic) {
        List<ClientInstance> clients = group.getInstances() == null ? List.of() : group.getInstances().stream()
                .filter(java.util.Objects::nonNull)
                .filter(instance -> matchesTopic(instance, topic))
                .map(ClientInstance::from)
                .toList();
        return new GroupClientsOutput(
                cluster,
                StringUtils.hasText(group.getName()) ? group.getName() : requestedGroup,
                StringUtils.hasText(topic) ? topic : null,
                clients.size(),
                clients);
    }

    static boolean matchesTopic(ConsumerInstanceVO instance, String topic) {
        if (!StringUtils.hasText(topic)) {
            return true;
        }
        List<String> subscribedTopics = instance.getSubscribedTopics();
        if (subscribedTopics != null && subscribedTopics.contains(topic)) {
            return true;
        }
        return instance.getTopicLag() != null && instance.getTopicLag().containsKey(topic);
    }
}
