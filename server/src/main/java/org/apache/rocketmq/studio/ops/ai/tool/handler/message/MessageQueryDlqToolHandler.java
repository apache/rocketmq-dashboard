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

import org.apache.rocketmq.studio.instance.dlq.DLQGroupVO;
import org.apache.rocketmq.studio.instance.dlq.DLQService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageQueryDlqInput;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.instance.dlq.DLQMessagePageVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageQueryDlqOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Query dead-letter groups, or the dead-letter messages of one group ({@code %DLQ%<group>}).
 */
@Component
@RequiredArgsConstructor
public class MessageQueryDlqToolHandler implements ToolHandler<MessageQueryDlqInput, MessageQueryDlqOutput> {

    private final DLQService dlqService;

    @Override
    public String name() {
        return "rmq.message.query_dlq";
    }

    @Override
    public Class<MessageQueryDlqInput> inputType() {
        return MessageQueryDlqInput.class;
    }

    @Override
    public MessageQueryDlqOutput execute(MessageQueryDlqInput input, ToolExecutionContext context) {
        int page = input.page() != null ? input.page().page() : 1;
        int pageSize = input.page() != null ? input.page().pageSize() : 20;
        Long startTime = input.time() != null ? input.time().startTime() : null;
        Long endTime = input.time() != null ? input.time().endTime() : null;
        if (input.groupName() == null || input.groupName().isBlank()) {
            return listGroups(
                    context.instanceId(), input.search(), page, pageSize);
        }
        return listMessages(
                context.instanceId(), input.groupName(), startTime, endTime,
                page, pageSize);
    }

    private MessageQueryDlqOutput listGroups(String instanceId, String search, int page, int pageSize) {
        PageResult<DLQGroupVO> result = dlqService.listDLQGroups(instanceId, search, page, pageSize);
        return MessageQueryDlqOutput.ofGroups(
                instanceId,
                result.getPage(),
                result.getSize(),
                result.getTotal(),
                result.getItems().stream()
                        .map(MessageQueryDlqOutput.DlqGroupItem::from)
                        .toList());
    }

    private MessageQueryDlqOutput listMessages(String instanceId, String groupName, Long startTime, Long endTime,
                                               int page, int pageSize) {
        DLQMessagePageVO result = dlqService.listMessages(instanceId, groupName, startTime, endTime,
                page, pageSize);
        return MessageQueryDlqOutput.ofMessages(
                instanceId,
                groupName,
                result.getPage(),
                result.getSize(),
                result.getTotal(),
                result.getItems().stream()
                        .map(MessageQueryDlqOutput.DlqMessageItem::from)
                        .toList());
    }
}
