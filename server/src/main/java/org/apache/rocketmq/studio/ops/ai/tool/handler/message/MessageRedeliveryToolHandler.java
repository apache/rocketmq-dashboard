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

import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.topic.SendMessageVO;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageRedeliveryInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageRedeliveryOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Group-directed redelivery: the copy goes to {@code %RETRY%<groupName>} by default so only that
 * group re-consumes it; an explicit targetTopic makes it visible to all subscribers instead.
 */
@Component
public class MessageRedeliveryToolHandler extends MutationToolHandler<MessageRedeliveryInput, MessageRedeliveryOutput> {

    private static final String RETRY_TOPIC_PREFIX = "%RETRY%";

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "redeliver message '%s' for consumer group '%s' in instance '%s'.",
            List.of("Publishes a new message containing the source payload and properties."),
            List.of("The source is the message explorer's display projection; a lossy source "
                    + "(truncated or binary body, abbreviated user properties) is refused "
                    + "before the plan is produced."));

    private final MetadataService metadataService;

    public MessageRedeliveryToolHandler(MetadataService metadataService) {
        super(MessageRedeliveryInput.class);
        this.metadataService = metadataService;
    }

    @Override
    public String name() {
        return "rmq.message.redelivery";
    }

    @Override
    public ToolPlan preview(MessageRedeliveryInput input, ToolExecutionContext context) {
        String groupName = requireGroupName(input);
        MessageRecordVO source = metadataService.findMessageForRedelivery(
                context.instanceId(), input.sourceTopic(), input.msgId());
        // Same exact-source guard execute() applies, so the plan can never promise a
        // publish that the execution stage would refuse.
        metadataService.requireExactSourceMessage(source);
        String destination = resolveDestination(input, groupName);
        return PLAN_DESCRIPTION.builder(input.msgId(), groupName, context.instanceId())
                .before(new RedeliverySource(source.getMsgId(), source.getTopic(), source.getTag(), source.getKey(),
                        source.getStoreTime(), source.getSize()))
                .after(new RedeliveryEffect("REDELIVERY", source.getMsgId(), destination))
                .build();
    }

    @Override
    public MessageRedeliveryOutput execute(MessageRedeliveryInput input, ToolExecutionContext context) {
        String groupName = requireGroupName(input);
        SendMessageVO sent = metadataService.redeliverMessage(
                context.instanceId(), groupName, input.sourceTopic(), input.msgId(), input.targetTopic());
        return new MessageRedeliveryOutput(input.msgId(), sent.getMsgId(), resolveDestination(input, groupName));
    }

    private static String requireGroupName(MessageRedeliveryInput input) {
        if (!StringUtils.hasText(input.groupName())) {
            throw ToolError.REQUEST_PARAMETER_REQUIRED.exception("groupName");
        }
        return input.groupName().trim();
    }

    private static String resolveDestination(MessageRedeliveryInput input, String groupName) {
        return StringUtils.hasText(input.targetTopic())
                ? input.targetTopic().trim()
                : RETRY_TOPIC_PREFIX + groupName;
    }

    private record RedeliverySource(String msgId, String topic, String tag, String key, long storeTime, int size) {
    }

    private record RedeliveryEffect(String operation, String sourceMsgId, String targetTopic) {
    }
}
