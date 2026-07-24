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
package com.rocketmq.studio.ops.ai.tool;

import com.rocketmq.studio.instance.group.ConsumerGroupVO;
import com.rocketmq.studio.instance.topic.MetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ConsumerGroupListToolHandler implements ToolHandler {

    private static final String NAME = "rmq.group.list";

    private final MetadataService metadataService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Object execute(Map<String, Object> input) {
        String clusterId = (String) input.get("cluster");
        String search = (String) input.get("search");
        return metadataService.listConsumerGroups(clusterId, search).stream()
                .map(ConsumerGroupListToolHandler::safeProjection)
                .toList();
    }

    private static Map<String, Object> safeProjection(ConsumerGroupVO group) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", require(group.getName(), "name"));
        result.put("namespace", blankIfNull(group.getNamespace()));
        result.put("clusterId", blankIfNull(group.getClusterId()));
        result.put("subscriptionMode", requiredEnumName(
                group.getSubscriptionMode(), "subscriptionMode", group.getName()));
        result.put("consumeType", requiredEnumName(
                group.getConsumeType(), "consumeType", group.getName()));
        result.put("onlineInstances", group.getOnlineInstances());
        result.put("totalLag", group.getTotalLag());
        result.put("subscribedTopics", copyList(group.getSubscribedTopics()));
        result.put("retryMaxTimes", group.getRetryMaxTimes());
        return result;
    }

    private static String requiredEnumName(Enum<?> value, String field, String groupName) {
        if (value == null) {
            throw new IllegalStateException("Consumer group " + field
                    + " is unavailable: " + groupName);
        }
        return value.name();
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Consumer group " + field + " is unavailable");
        }
        return value;
    }

    private static List<String> copyList(List<String> value) {
        return value == null ? List.of() : List.copyOf(value);
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
