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

import org.apache.rocketmq.studio.instance.message.MessageService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageRequest;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageQueryByTopicInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageQueryOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Query messages by topic and optional time range. Read-only and safe for AI/MCP/CLI callers;
 * delegates to the standard message service used by the web UI.
 */
@Component
@RequiredArgsConstructor
public class MessageQueryByTopicToolHandler
        implements ToolHandler<MessageQueryByTopicInput, MessageQueryOutput> {

    private final MessageService messageService;

    @Override
    public String name() {
        return "rmq.message.query_by_topic";
    }

    @Override
    public Class<MessageQueryByTopicInput> inputType() {
        return MessageQueryByTopicInput.class;
    }

    @Override
    public MessageQueryOutput execute(
            MessageQueryByTopicInput input, ToolExecutionContext context) {
        PageRequest page = input.page() != null ? input.page() : new PageRequest(1, 20);
        return MessageQueryOutput.fromPage(messageService.queryMessagesPage(
                context.instanceId(), input.topicName(), null, input.tag(), null,
                input.startTime(), input.endTime(), page.page(), page.pageSize()), input.includeBody());
    }
}
