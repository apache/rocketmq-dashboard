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
package org.apache.rocketmq.studio.ops.ai.tool.handler.dlq;

import org.apache.rocketmq.studio.instance.dlq.DLQResendResultVO;
import org.apache.rocketmq.studio.instance.dlq.DLQService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.dlq.DLQResendInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.dlq.DLQResendOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DLQResendToolHandler extends MutationToolHandler<DLQResendInput, DLQResendOutput> {

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "resend DLQ messages for consumer group '%s' in cluster '%s'.",
            List.of(),
            List.of());

    private final DLQService dlqService;

    public DLQResendToolHandler(DLQService dlqService) {
        super(DLQResendInput.class);
        this.dlqService = dlqService;
    }

    @Override
    public String name() {
        return "rmq.dlq.resend";
    }

    @Override
    public ToolPlan preview(DLQResendInput input, ToolExecutionContext context) {
        String group = input.group();
        String targetTopic = input.targetTopic() == null || input.targetTopic().isBlank()
                ? "ORIGINAL_TOPIC" : input.targetTopic();
        Long startTime = input.time() != null ? input.time().startTime() : null;
        Long endTime = input.time() != null ? input.time().endTime() : null;
        return PLAN_DESCRIPTION.builder(group, context.cluster())
                .before(new ResendScope(group, targetTopic, startTime, endTime))
                .after(new ResendEffect("RESEND", targetTopic))
                .impact("Republishes matching dead-letter messages for consumer group '" + group
                        + "' without deleting the originals.")
                .build();
    }

    @Override
    public DLQResendOutput execute(DLQResendInput input, ToolExecutionContext context) {
        Long startTime = input.time() != null ? input.time().startTime() : null;
        Long endTime = input.time() != null ? input.time().endTime() : null;
        DLQResendResultVO result = dlqService.resendMessages(
                context.cluster(), input.group(), startTime, endTime, input.targetTopic());
        return DLQResendOutput.from(result);
    }

    private record ResendScope(String group, String targetTopic, Long startTime, Long endTime) {
    }

    private record ResendEffect(String operation, String targetTopic) {
    }
}
