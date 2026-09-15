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
package org.apache.rocketmq.studio.ops.ai.tool.handler.topic;

import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.topic.SendMessageDTO;
import org.apache.rocketmq.studio.instance.topic.SendMessageVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicSendInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicSendOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TopicSendToolHandler extends MutationToolHandler<TopicSendInput, TopicSendOutput> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "send a message to topic '%s' in cluster '%s'.",
            List.of("Publishes one message to the target topic on the reachable brokers."),
            List.of("The message body and properties are delivered as supplied; verify the topic and tag before confirming."));

    private final MetadataService metadataService;

    public TopicSendToolHandler(MetadataService metadataService) {
        super(TopicSendInput.class);
        this.metadataService = metadataService;
    }

    @Override
    public String name() {
        return "rmq.topic.send";
    }

    @Override
    public ToolPlan preview(TopicSendInput input, ToolExecutionContext context) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("topic", input.topic());
        after.put("tag", input.tag());
        after.put("key", input.key());
        after.put("bodySize", input.body() == null ? 0 : input.body().length());
        after.put("properties", input.properties());
        return PLAN_DESCRIPTION.builder(input.topic(), context.cluster())
                .after(after)
                .build();
    }

    @Override
    public TopicSendOutput execute(TopicSendInput input, ToolExecutionContext context) {
        SendMessageDTO request = SendMessageDTO.builder()
                .instanceId(context.cluster())
                .topic(input.topic())
                .tag(input.tag())
                .key(input.key())
                .body(input.body())
                .properties(parseProperties(input.properties()))
                .build();
        SendMessageVO sent = metadataService.sendMessage(request);
        return TopicSendOutput.from(sent);
    }

    private static Map<String, String> parseProperties(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (Exception exception) {
            throw ToolError.MESSAGE_PROPERTIES_INVALID.exception();
        }
    }
}
