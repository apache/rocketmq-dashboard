/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
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
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageResendInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageResendOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MessageResendToolHandler extends MutationToolHandler<MessageResendInput, MessageResendOutput> {

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "resend message '%s' in cluster '%s'.",
            List.of("Publishes a new message containing the source payload and properties."),
            List.of());

    private final MetadataService metadataService;

    public MessageResendToolHandler(MetadataService metadataService) {
        super(MessageResendInput.class);
        this.metadataService = metadataService;
    }

    @Override
    public String name() {
        return "rmq.message.resend";
    }

    @Override
    public ToolPlan preview(MessageResendInput input, ToolExecutionContext context) {
        MessageRecordVO source = metadataService.findMessageForResend(
                context.cluster(), input.sourceTopic(), input.msgId());
        String destination = resolveDestination(input, source);
        return PLAN_DESCRIPTION.builder(input.msgId(), context.cluster())
                .before(new ResendSource(source.getMsgId(), source.getTopic(), source.getTag(), source.getKey(),
                        source.getStoreTime(), source.getSize()))
                .after(new ResendEffect("RESEND", source.getMsgId(), destination))
                .build();
    }

    @Override
    public MessageResendOutput execute(MessageResendInput input, ToolExecutionContext context) {
        MessageRecordVO source = metadataService.findMessageForResend(
                context.cluster(), input.sourceTopic(), input.msgId());
        String destination = resolveDestination(input, source);
        SendMessageVO sent = metadataService.resendMessage(context.cluster(), source, destination);
        return new MessageResendOutput(input.msgId(), sent.getMsgId(), destination);
    }

    private String resolveDestination(MessageResendInput input, MessageRecordVO source) {
        String destination = input.targetTopic() == null || input.targetTopic().isBlank()
                ? source.getTopic() : input.targetTopic().trim();
        if (destination == null || destination.isBlank()) {
            throw ToolError.RESEND_TARGET_TOPIC_REQUIRED.exception();
        }
        return destination;
    }

    private record ResendSource(String msgId, String topic, String tag, String key, long storeTime, int size) {
    }

    private record ResendEffect(String operation, String sourceMsgId, String targetTopic) {
    }
}
