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

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.instance.dlq.DLQGroupVO;
import org.apache.rocketmq.studio.instance.dlq.DLQMessageVO;
import org.apache.rocketmq.studio.instance.dlq.DLQService;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.dlq.DLQListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.dlq.DLQListOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DLQListToolHandler implements ToolHandler<DLQListInput, DLQListOutput> {

    private final DLQService dlqService;

    @Override
    public String name() {
        return "rmq.dlq.list";
    }

    @Override
    public Class<DLQListInput> inputType() {
        return DLQListInput.class;
    }

    @Override
    public DLQListOutput execute(DLQListInput input, ToolExecutionContext context) {
        int page = input.page() != null ? input.page().page() : 1;
        int pageSize = input.page() != null ? input.page().pageSize() : 20;
        Long startTime = input.time() != null ? input.time().startTime() : null;
        Long endTime = input.time() != null ? input.time().endTime() : null;
        if (input.group() == null || input.group().isBlank()) {
            return listGroups(
                    context.cluster(), input.search(), page, pageSize);
        }
        return listMessages(
                context.cluster(), input.group(), startTime, endTime,
                page, pageSize);
    }

    private DLQListOutput listGroups(String instanceId, String search, int page, int pageSize) {
        PageResult<DLQGroupVO> result = dlqService.listDLQGroups(instanceId, search, page, pageSize);
        return DLQListOutput.ofGroups(
                instanceId,
                result.getPage(),
                result.getSize(),
                result.getTotal(),
                result.getItems().stream()
                        .map(DLQListOutput.DLQGroupItem::from)
                        .toList());
    }

    private DLQListOutput listMessages(String instanceId, String group, Long startTime, Long endTime,
                                       int page, int pageSize) {
        PageResult<DLQMessageVO> result = dlqService.listMessages(instanceId, group, startTime, endTime,
                page, pageSize);
        return DLQListOutput.ofMessages(
                instanceId,
                group,
                result.getPage(),
                result.getSize(),
                result.getTotal(),
                result.getItems().stream()
                        .map(DLQListOutput.DLQMessageItem::from)
                        .toList());
    }
}
