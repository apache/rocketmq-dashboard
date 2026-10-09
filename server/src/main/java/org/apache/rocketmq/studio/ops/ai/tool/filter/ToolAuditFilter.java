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
package org.apache.rocketmq.studio.ops.ai.tool.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.MutationOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolInvocation;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ToolAuditFilter implements ToolExecutionFilter {

    private final AuditService auditService;

    @Override
    public Type type() {
        return Type.AUDIT;
    }

    /** Detail marker for a preview, so the row cannot be read as an applied change. */
    private static final String DRY_RUN_DETAIL = "dry_run=true (preview only, nothing applied)";

    @Override
    public Object filter(ToolInvocation invocation, Chain chain) {
        ToolExecutionContext context = invocation.context();
        Object output;
        try {
            output = chain.proceed(invocation);
        } catch (Exception exception) {
            this.record(context, "FAILED", exception.getMessage());
            throw exception;
        }
        // A dry run stops at the mutation filter's plan, so it performs none of the change the row's
        // operation name implies; without the marker an auditor cannot tell "who deleted topic X"
        // from "who previewed deleting it".
        boolean planned = output instanceof MutationOutput<?> mutation
                && mutation.status() == MutationOutput.Status.PLANNED;
        this.record(context, "SUCCESS", planned ? DRY_RUN_DETAIL : null);
        return output;
    }

    private void record(ToolExecutionContext context, String result, String errorMessage) {
        try {
            auditService.record(context.operationType(),
                    context.resourceType(),
                    context.definition().name(),
                    context.instanceId(),
                    errorMessage,
                    result
            );
        } catch (RuntimeException auditFailure) {
            log.warn("Failed to record tool audit result={} tool={} instance={}: {}",
                    result, context.definition().name(), context.instanceId(), auditFailure.getMessage());
        }
    }

}
