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

import org.apache.rocketmq.studio.ops.ai.tool.contract.common.MutationOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolInvocation;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ToolMutationFilter implements ToolExecutionFilter {

    private static final String L3_WARNING =
            "This high-risk operation requires break-glass approval and an explicit reason.";

    private final ToolTokenService tokenService;
    private final boolean l3Enabled;

    public ToolMutationFilter(
            ToolTokenService tokenService,
            @Value("${studio.ai.allow-l3-tools:false}") boolean l3Enabled) {
        this.tokenService = tokenService;
        this.l3Enabled = l3Enabled;
    }

    @Override
    public Type type() {
        return Type.MUTATION;
    }

    @Override
    public Object filter(ToolInvocation invocation, Chain chain) {
        ToolExecutionContext context = invocation.context();
        if (context.definition().isReadOnly()) {
            return chain.proceed(invocation);
        }
        if (!context.dryRun()) {
            if (context.definition().requiresReason()) {
                verifyL3Requirements(context);
            }
            tokenService.verify(context);
        }

        ToolPlan plan = invocation.preview();
        if (context.definition().requiresReason()) {
            plan = plan.withWarning(L3_WARNING);
        }
        if (context.dryRun()) {
            String token = tokenService.issue(context);
            return new MutationOutput<>(MutationOutput.Status.PLANNED, context.cluster(), plan, token, null);
        }

        Object result = chain.proceed(invocation);
        return new MutationOutput<>(MutationOutput.Status.EXECUTED, context.cluster(), plan, null, result);
    }

    private void verifyL3Requirements(ToolExecutionContext context) {
        ToolDefinition definition = context.definition();
        if (!l3Enabled) {
            throw ToolError.L3_DISABLED.exception();
        }
        if (!context.breakGlass()) {
            throw ToolError.L3_APPROVAL_REQUIRED.exception(definition.name());
        }
        String reason = context.reason();
        if (reason == null || reason.isBlank()) {
            throw ToolError.L3_REASON_REQUIRED.exception(definition.name());
        }
    }
}
