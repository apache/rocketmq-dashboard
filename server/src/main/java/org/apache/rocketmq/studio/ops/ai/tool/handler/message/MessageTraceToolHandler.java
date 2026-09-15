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

import org.apache.rocketmq.studio.instance.message.ConsumerStatusVO;
import org.apache.rocketmq.studio.instance.message.MessageService;
import org.apache.rocketmq.studio.instance.message.TraceNodeVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageQueryByIdInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageTraceOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageTraceToolHandler
        implements ToolHandler<MessageQueryByIdInput, MessageTraceOutput> {

    private final MessageService messageService;

    @Override
    public String name() {
        return "rmq.message.trace";
    }

    @Override
    public Class<MessageQueryByIdInput> inputType() {
        return MessageQueryByIdInput.class;
    }

    @Override
    public MessageTraceOutput execute(
            MessageQueryByIdInput input,
            ToolExecutionContext context) {
        TraceRecordVO trace = messageService.getMessageTrace(
                context.cluster(), input.msgId(), input.topic());
        return project(input.msgId(), trace);
    }

    private static MessageTraceOutput project(String msgId, TraceRecordVO trace) {
        return new MessageTraceOutput(
                msgId,
                trace.getNodes().stream()
                        .map(MessageTraceToolHandler::projectNode)
                        .toList(),
                trace.getConsumerStatus().stream()
                        .map(MessageTraceToolHandler::projectConsumerStatus)
                        .toList());
    }

    private static MessageTraceOutput.Node projectNode(TraceNodeVO node) {
        return new MessageTraceOutput.Node(
                node.getTitle(),
                node.getTimestamp(),
                node.getStatus(),
                node.getCostTime(),
                node.getDescription());
    }

    private static MessageTraceOutput.ConsumerStatus projectConsumerStatus(ConsumerStatusVO status) {
        return new MessageTraceOutput.ConsumerStatus(
                status.getGroup(),
                status.getDeliveryStatus() == null ? null : status.getDeliveryStatus().name(),
                status.getConsumeTime(),
                status.getRetryCount());
    }
}
