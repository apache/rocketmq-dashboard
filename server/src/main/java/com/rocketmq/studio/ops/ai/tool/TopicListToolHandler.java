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

import com.rocketmq.studio.instance.topic.MetadataService;
import com.rocketmq.studio.instance.topic.TopicVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TopicListToolHandler implements ToolHandler {

    private static final String NAME = "rmq.topic.list";

    private final MetadataService metadataService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Object execute(Map<String, Object> input) {
        String clusterId = (String) input.get("cluster");
        String type = (String) input.get("type");
        String search = (String) input.get("search");
        return metadataService.listTopics(clusterId, type, search).stream()
                .map(TopicListToolHandler::safeProjection)
                .toList();
    }

    private static Map<String, Object> safeProjection(TopicVO topic) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", require(topic.getName(), "name"));
        result.put("namespace", blankIfNull(topic.getNamespace()));
        result.put("clusterId", blankIfNull(topic.getClusterId()));
        result.put("type", requiredEnumName(topic.getType(), "type", topic.getName()));
        result.put("writeQueues", topic.getWriteQueues());
        result.put("readQueues", topic.getReadQueues());
        result.put("perm", requiredEnumName(topic.getPerm(), "perm", topic.getName()));
        result.put("messageCount", topic.getMessageCount());
        result.put("tps", topic.getTps());
        result.put("consumerGroupCount", topic.getConsumerGroupCount());
        return result;
    }

    private static String requiredEnumName(Enum<?> value, String field, String topicName) {
        if (value == null) {
            throw new IllegalStateException("Topic " + field + " is unavailable: " + topicName);
        }
        return value.name();
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Topic " + field + " is unavailable");
        }
        return value;
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
