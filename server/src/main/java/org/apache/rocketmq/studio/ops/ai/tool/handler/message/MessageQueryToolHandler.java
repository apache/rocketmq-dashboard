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
import org.apache.rocketmq.studio.instance.message.MessageService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageRequest;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageQueryInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageQueryOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Unified message query: the first non-empty identifier among msgId, uniqueKey and key (in this
 * order) selects the query path; the time window only applies to the uniqueKey and key paths.
 */
@Component
@RequiredArgsConstructor
public class MessageQueryToolHandler
        implements ToolHandler<MessageQueryInput, MessageQueryOutput> {

    private final MessageService messageService;

    @Override
    public String name() {
        return "rmq.message.query";
    }

    @Override
    public Class<MessageQueryInput> inputType() {
        return MessageQueryInput.class;
    }

    @Override
    public MessageQueryOutput execute(MessageQueryInput input, ToolExecutionContext context) {
        String instanceId = context.instanceId();
        PageRequest page = input.page() != null ? input.page() : new PageRequest(1, 20);
        if (StringUtils.hasText(input.msgId())) {
            return MessageQueryOutput.fromPage(messageService.queryMessagesPage(
                    instanceId, input.topicName(), input.msgId(), null, null, null, null,
                    page.page(), page.pageSize()), input.includeBody());
        } else if (StringUtils.hasText(input.uniqueKey())) {
            return MessageQueryOutput.fromUniqueKey(messageService.queryMessageByUniqueKey(
                    instanceId, input.topicName(), input.uniqueKey(), input.startTime(), input.endTime()),
                    page.page(), page.pageSize(), input.includeBody());
        } else if (StringUtils.hasText(input.key())) {
            return MessageQueryOutput.fromPage(messageService.queryMessagesPage(
                    instanceId, input.topicName(), null, null, input.key(),
                    input.startTime(), input.endTime(), page.page(), page.pageSize()),
                    input.includeBody());
        } else {
            throw new BusinessException(400, "message query requires one of: msgId, uniqueKey, key");
        }
    }
}
