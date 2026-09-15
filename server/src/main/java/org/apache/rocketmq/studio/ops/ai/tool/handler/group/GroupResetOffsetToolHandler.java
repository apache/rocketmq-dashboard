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
package org.apache.rocketmq.studio.ops.ai.tool.handler.group;

import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.group.ResetConsumerOffsetPreviewVO;
import org.apache.rocketmq.studio.instance.group.ResetConsumerOffsetQueuePreviewVO;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupResetOffsetInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.ResetOffsetOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GroupResetOffsetToolHandler extends MutationToolHandler<GroupResetOffsetInput, ResetOffsetOutput> {

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "reset offsets for consumer group '%s' in cluster '%s'.",
            List.of(),
            List.of());

    private final MetadataService metadataService;

    public GroupResetOffsetToolHandler(MetadataService metadataService) {
        super(GroupResetOffsetInput.class);
        this.metadataService = metadataService;
    }

    @Override
    public String name() {
        return "rmq.group.reset_offset";
    }

    @Override
    public ToolPlan preview(GroupResetOffsetInput input, ToolExecutionContext context) {
        ResetConsumerOffsetPreviewVO preview = metadataService.previewResetOffset(
                context.cluster(), input.group(),
                input.timestamp(), input.topic());
        if (!preview.isComplete() || !preview.isAllowReset()) {
            throw ToolError.OFFSET_RESET_PREVIEW_UNSAFE.exception(input.group());
        }
        List<String> warnings = preview.getWarnings() == null
                ? List.of() : List.copyOf(preview.getWarnings());
        return PLAN_DESCRIPTION.builder(input.group(), context.cluster())
                .before(OffsetState.current(input, preview))
                .after(OffsetState.proposed(input, preview))
                .impact("Moves offsets for " + preview.getQueueCount()
                        + " queues; projected lag changes from " + preview.getCurrentTotalLag()
                        + " to " + preview.getProjectedTotalLag() + ".")
                .warnings(warnings)
                .build();
    }

    @Override
    public ResetOffsetOutput execute(GroupResetOffsetInput input, ToolExecutionContext context) {
        metadataService.resetOffset(context.cluster(), input.group(), input.timestamp(), input.topic());
        return new ResetOffsetOutput(input.group(), input.topic(), input.timestamp());
    }

    private record OffsetState(String group, String topic, Long timestamp, long totalLag, List<QueueState> queues) {

        static OffsetState current(GroupResetOffsetInput input, ResetConsumerOffsetPreviewVO preview) {
            return new OffsetState(input.group(), input.topic(), null, preview.getCurrentTotalLag(),
                    preview.getQueues().stream().map(QueueState::current).toList());
        }

        static OffsetState proposed(GroupResetOffsetInput input, ResetConsumerOffsetPreviewVO preview) {
            return new OffsetState(input.group(), input.topic(), input.timestamp(), preview.getProjectedTotalLag(),
                    preview.getQueues().stream().map(QueueState::proposed).toList());
        }
    }

    private record QueueState(String topic, String broker, int queueId, long offset, long lag) {

        static QueueState current(ResetConsumerOffsetQueuePreviewVO queue) {
            return new QueueState(queue.getTopic(), queue.getBroker(), queue.getQueueId(),
                    queue.getConsumerOffset(), queue.getCurrentLag());
        }

        static QueueState proposed(ResetConsumerOffsetQueuePreviewVO queue) {
            return new QueueState(queue.getTopic(), queue.getBroker(), queue.getQueueId(),
                    queue.getTargetOffset(), queue.getProjectedLag());
        }
    }
}
