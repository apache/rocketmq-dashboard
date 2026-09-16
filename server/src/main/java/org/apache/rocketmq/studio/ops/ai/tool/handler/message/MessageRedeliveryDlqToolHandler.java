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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.dlq.DLQResendResultVO;
import org.apache.rocketmq.studio.instance.dlq.DLQService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageRedeliveryDlqInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageRedeliveryDlqOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redeliver dead-letter messages of one consumer group back to the original or an explicit
 * target topic; the originals are kept.
 */
@Component
public class MessageRedeliveryDlqToolHandler
        extends MutationToolHandler<MessageRedeliveryDlqInput, MessageRedeliveryDlqOutput> {

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "redeliver DLQ messages for consumer group '%s' in instance '%s'.",
            List.of(),
            List.of());

    private final DLQService dlqService;

    public MessageRedeliveryDlqToolHandler(DLQService dlqService) {
        super(MessageRedeliveryDlqInput.class);
        this.dlqService = dlqService;
    }

    @Override
    public String name() {
        return "rmq.message.redelivery_dlq";
    }

    @Override
    public ToolPlan preview(MessageRedeliveryDlqInput input, ToolExecutionContext context) {
        String groupName = input.groupName();
        String targetTopic = input.targetTopic() == null || input.targetTopic().isBlank()
                ? "ORIGINAL_TOPIC" : input.targetTopic();
        Long startTime = input.time() != null ? input.time().startTime() : null;
        Long endTime = input.time() != null ? input.time().endTime() : null;
        return PLAN_DESCRIPTION.builder(groupName, context.instanceId())
                .before(new RedeliveryScope(groupName, targetTopic, startTime, endTime))
                .after(new RedeliveryEffect("REDELIVERY", targetTopic))
                .impact("Republishes matching dead-letter messages for consumer group '" + groupName
                        + "' without deleting the originals.")
                .build();
    }

    @Override
    public MessageRedeliveryDlqOutput execute(MessageRedeliveryDlqInput input, ToolExecutionContext context) {
        Long startTime = input.time() != null ? input.time().startTime() : null;
        Long endTime = input.time() != null ? input.time().endTime() : null;
        try {
            DLQResendResultVO result = dlqService.resendMessages(
                    context.instanceId(), input.groupName(), startTime, endTime, input.targetTopic());
            return MessageRedeliveryDlqOutput.from(result);
        } catch (BusinessException e) {
            // The provider signals "this group has no %DLQ% topic" with a 404; surface it as a clean
            // NOT_FOUND (environment has no dead letters for the group) rather than a generic crash.
            if (e.getCode() == 404) {
                throw ToolError.DLQ_GROUP_NOT_FOUND.exception(input.groupName());
            }
            throw e;
        }
    }

    private record RedeliveryScope(String groupName, String targetTopic, Long startTime, Long endTime) {
    }

    private record RedeliveryEffect(String operation, String targetTopic) {
    }
}
