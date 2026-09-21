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
package org.apache.rocketmq.studio.ops.ai.tool.handler.audit;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.ops.AuditItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.ops.AuditListInput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.audit.AuditRecordVO;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditListToolHandler implements ToolHandler<AuditListInput, PageOutput<AuditItem>> {

    private final AuditService auditService;

    @Override
    public String name() {
        return "rmq.audit.list";
    }

    @Override
    public Class<AuditListInput> inputType() {
        return AuditListInput.class;
    }

    /**
     * Platform-level query: spans every Instance. Rows written by the tool chain carry the
     * calling Instance in {@code cluster_id} (see {@code ToolAuditFilter}), which is the
     * attribution column projected on {@code AuditItem}.
     */
    @Override
    public PageOutput<AuditItem> execute(AuditListInput input, ToolExecutionContext context) {
        PageResult<AuditRecordVO> result = auditService.queryLogs(
                input.page().page(), input.page().pageSize(),
                input.search(), input.operationType(), input.resourceType(),
                null, null, false, input.startDate(), input.endDate(), input.result(), null, null);
        return PageOutput.from(result, AuditItem::from);
    }
}
