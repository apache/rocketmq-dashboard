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
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolInvocation;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolRiskLevel;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolTokenService;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The audit row for a tool call has to say whether the change was applied. A dry run returns the
 * mutation filter's plan without touching anything, so recording it exactly like an execution made
 * "who deleted topic X" indistinguishable from "who previewed deleting it".
 */
class ToolDryRunAuditTest {

    private final AuditService audit = mock(AuditService.class);
    private final ToolTokenService tokens = mock(ToolTokenService.class);
    private final ToolAuditFilter auditFilter = new ToolAuditFilter(audit);
    private final ToolMutationFilter mutationFilter = new ToolMutationFilter(tokens, true);
    private final ToolFilterChain chain = new ToolFilterChain(List.of(mutationFilter, auditFilter));
    private final TestHandler handler = new TestHandler();

    @Test
    void aPreviewIsAuditedAsSuchInsteadOfAsAnAppliedChangeTest() {
        ToolExecutionContext context = context(Map.of("topic", "orders", "dry_run", true));
        when(tokens.issue(context)).thenReturn("signed-request");

        MutationOutput<?> output = (MutationOutput<?>) chain.execute(new ToolInvocation(context, handler));

        assertThat(output.status()).isEqualTo(MutationOutput.Status.PLANNED);
        assertThat(handler.executions).isZero();
        verify(audit).record(any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq("SUCCESS"));
        org.mockito.ArgumentCaptor<String> detail = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(audit).record(any(), any(), any(), any(), detail.capture(), any());
        assertThat(detail.getValue()).contains("dry_run=true");
    }

    @Test
    void anAppliedChangeIsAuditedWithoutThePreviewMarkerTest() {
        ToolExecutionContext context = context(Map.of("topic", "orders"));
        org.mockito.Mockito.doNothing().when(tokens).verify(context);

        MutationOutput<?> output = (MutationOutput<?>) chain.execute(new ToolInvocation(context, handler));

        assertThat(output.status()).isEqualTo(MutationOutput.Status.EXECUTED);
        assertThat(handler.executions).isEqualTo(1);
        verify(audit).record(any(), any(), any(), any(), org.mockito.ArgumentMatchers.isNull(), any());
    }

    private static ToolExecutionContext context(Map<String, Object> input) {
        ToolDefinition definition = new ToolDefinition("rmq.topic.update",
                new ToolDefinition.Cli("topic", "update"), "Update topic", ToolRiskLevel.L2,
                "topic:write", List.of(), Map.of(), Map.of(), null, false, null);
        return ToolExecutionContext.of("instance-a", definition, input, "alice");
    }

    private record Input(String topic) {
    }

    private static class TestHandler extends MutationToolHandler<Input, Object> {
        private int executions;

        private TestHandler() {
            super(Input.class);
        }

        @Override
        public String name() {
            return "rmq.topic.update";
        }

        @Override
        public ToolPlan preview(Input input, ToolExecutionContext context) {
            return ToolPlan.builder("Update topic")
                    .before(Map.of("topic", input.topic()))
                    .after(Map.of("topic", input.topic()))
                    .impact("Updates the topic configuration.")
                    .build();
        }

        @Override
        public Object execute(Input input, ToolExecutionContext context) {
            executions++;
            return Map.of("topic", input.topic());
        }
    }
}
