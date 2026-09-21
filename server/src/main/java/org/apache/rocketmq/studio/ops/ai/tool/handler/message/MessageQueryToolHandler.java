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
import org.apache.rocketmq.studio.instance.message.MessageQueryResult;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.MessageService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageQueryInput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Unified message query: the first non-empty identifier among msgId, uniqueKey and key (in this
 * order) selects the query path; the time window only applies to the uniqueKey and key paths.
 */
@Component
@RequiredArgsConstructor
public class MessageQueryToolHandler
        implements ToolHandler<MessageQueryInput, ListOutput<MessageItem>> {

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
    public ListOutput<MessageItem> execute(MessageQueryInput input, ToolExecutionContext context) {
        String instanceId = context.instanceId();
        List<MessageRecordVO> messages;
        Boolean truncated;
        if (StringUtils.hasText(input.msgId())) {
            messages = messageService.queryMessages(
                    instanceId, input.topicName(), input.msgId(), null, null, null, null);
            truncated = false;
        } else if (StringUtils.hasText(input.uniqueKey())) {
            messages = messageService.queryMessageByUniqueKey(
                    instanceId, input.topicName(), input.uniqueKey(), input.startTime(), input.endTime());
            truncated = false;
        } else if (StringUtils.hasText(input.key())) {
            MessageQueryResult result = messageService.queryMessagesDetailed(
                    instanceId, input.topicName(), null, null, input.key(),
                    input.startTime(), input.endTime(), true);
            messages = result.messages();
            truncated = result.mayBeTruncated()
                    || result.messages().size() >= MessageService.TOPIC_QUERY_RESULT_LIMIT;
        } else {
            throw new BusinessException(400, "message query requires one of: msgId, uniqueKey, key");
        }
        return new ListOutput<>(messages.stream().map(MessageItem::from).toList(), truncated);
    }
}
