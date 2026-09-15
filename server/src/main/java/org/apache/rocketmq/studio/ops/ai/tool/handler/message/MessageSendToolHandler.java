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
package org.apache.rocketmq.studio.ops.ai.tool.handler.message;

import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.topic.SendMessageDTO;
import org.apache.rocketmq.studio.instance.topic.SendMessageVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageSendInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageSendOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
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

/**
 * Send one message; the registered topic type drives dispatch (FIFO requires messageGroup,
 * DELAY requires a future deliveryTimestamp, TRANSACTION is rejected).
 */
@Component
public class MessageSendToolHandler extends MutationToolHandler<MessageSendInput, MessageSendOutput> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "send a message to topic '%s' in instance '%s'.",
            List.of("Publishes one message to the target topic on the reachable brokers."),
            List.of("The message body and properties are delivered as supplied; verify the topic and tag before confirming."));

    private final MetadataService metadataService;

    public MessageSendToolHandler(MetadataService metadataService) {
        super(MessageSendInput.class);
        this.metadataService = metadataService;
    }

    @Override
    public String name() {
        return "rmq.message.send";
    }

    @Override
    public ToolPlan preview(MessageSendInput input, ToolExecutionContext context) {
        validateForTopicType(resolveTopicType(context.instanceId(), input.topicName()), input);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("topic", input.topicName());
        after.put("tag", input.tag());
        after.put("key", input.key());
        after.put("bodySize", input.body() == null ? 0 : input.body().length());
        after.put("properties", input.properties());
        if (input.messageGroup() != null) {
            after.put("messageGroup", input.messageGroup());
        }
        if (input.deliveryTimestamp() != null) {
            after.put("deliveryTimestamp", input.deliveryTimestamp());
        }
        return PLAN_DESCRIPTION.builder(input.topicName(), context.instanceId())
                .after(after)
                .build();
    }

    @Override
    public MessageSendOutput execute(MessageSendInput input, ToolExecutionContext context) {
        validateForTopicType(resolveTopicType(context.instanceId(), input.topicName()), input);
        SendMessageDTO request = SendMessageDTO.builder()
                .instanceId(context.instanceId())
                .topic(input.topicName())
                .tag(input.tag())
                .key(input.key())
                .body(input.body())
                .messageGroup(input.messageGroup())
                .deliveryTimestamp(input.deliveryTimestamp())
                .properties(parseProperties(input.properties()))
                .build();
        SendMessageVO sent = metadataService.sendMessage(request);
        return MessageSendOutput.from(sent);
    }

    /** The registered topic type is the single source of truth; unknown topics keep plain-send behavior. */
    private TopicType resolveTopicType(String instanceId, String topicName) {
        return metadataService.findTopic(instanceId, null, topicName)
                .map(TopicVO::getType)
                .orElse(null);
    }

    private static void validateForTopicType(TopicType topicType, MessageSendInput input) {
        if (topicType == null) {
            return;
        }
        switch (topicType) {
            case FIFO -> {
                if (!StringUtils.hasText(input.messageGroup())) {
                    throw new BusinessException(400, "messageGroup is required for FIFO topic");
                }
            }
            case DELAY -> {
                if (input.deliveryTimestamp() == null) {
                    throw new BusinessException(400, "deliveryTimestamp is required for DELAY topic");
                }
                if (input.deliveryTimestamp() <= System.currentTimeMillis()) {
                    throw new BusinessException(400, "deliveryTimestamp must be in the future for DELAY topic");
                }
            }
            case TRANSACTION -> throw new BusinessException(400, "sending transaction messages is not supported");
            default -> {
            }
        }
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
