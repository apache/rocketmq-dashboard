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
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolRiskLevel;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolTokenService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ToolMutationFilterTest {

    private final ToolTokenService tokens = mock(ToolTokenService.class);
    private final ToolMutationFilter filter = new ToolMutationFilter(tokens, true);
    private final ToolFilterChain chain = new ToolFilterChain(List.of(filter));
    private final TestHandler handler = new TestHandler();

    @Test
    void generatesResponsePlanAndExecutesWithOriginalContext() {
        ToolExecutionContext context = context(ToolRiskLevel.L2, Map.of(
                "topic", " orders ", "confirm_token", "token"));
        MutationOutput<?> output = (MutationOutput<?>) chain.execute(new ToolInvocation(context, handler));

        assertThat(output).isEqualTo(new MutationOutput<>(
                MutationOutput.Status.EXECUTED, "instance-a", output.plan(), null, Map.of("topic", " orders ")));
        assertThat(handler.executionContext).isSameAs(context);
        assertThat(output.plan().after()).containsEntry("topic", " orders ");
        assertThat(handler.executions).isEqualTo(1);
        assertThat(handler.previews).isEqualTo(1);
        verify(tokens).verify(context);
        verifyNoMoreInteractions(tokens);
    }

    @Test
    void dryRunReturnsEnhancedPreviewAndSignsRequestWithoutExecuting() {
        ToolExecutionContext context = context(ToolRiskLevel.L3, Map.of(
                "topic", " orders ", "dry_run", true, "confirm_token", "previous-token"));
        when(tokens.issue(context)).thenReturn("signed-request");

        Object result = filter.filter(new ToolInvocation(context, handler), ignored -> {
            throw new AssertionError("dry-run must not reach downstream execution");
        });

        MutationOutput<?> output = (MutationOutput<?>) result;
        assertThat(output.status()).isEqualTo(MutationOutput.Status.PLANNED);
        assertThat(output.confirmToken()).isEqualTo("signed-request");
        assertThat(output.plan().summary()).isEqualTo("Create topic");
        assertThat(output.plan().impact()).containsExactly("Creates the topic configuration.");
        assertThat(output.plan().before()).containsExactlyInAnyOrderEntriesOf(Map.of("exists", false));
        assertThat(output.plan().after()).containsExactlyInAnyOrderEntriesOf(Map.of("topic", " orders "));
        assertThat(output.plan().warnings()).containsExactly(
                "Review the topic configuration.",
                "This high-risk operation requires break-glass approval and an explicit reason.");
        assertThat(handler.previews).isEqualTo(1);
        assertThat(handler.executions).isZero();
        verify(tokens).issue(context);
        verifyNoMoreInteractions(tokens);
    }

    @Test
    void readOnlyCallExecutesWithoutTokenOrPreview() {
        ToolExecutionContext context = context(ToolRiskLevel.L1, Map.of("topic", "orders"));

        assertThat(chain.execute(new ToolInvocation(context, handler))).isEqualTo(Map.of("topic", "orders"));
        assertThat(handler.executions).isEqualTo(1);
        assertThat(handler.previews).isZero();
        verifyNoInteractions(tokens);
    }

    @Test
    void approvedL3CallGeneratesExecutionPlanWithWarning() {
        ToolExecutionContext context = context(ToolRiskLevel.L3, Map.of("topic", "orders",
                "confirm_token", "token", "break_glass", true, "reason", "maintenance"));
        MutationOutput<?> output = (MutationOutput<?>) chain.execute(new ToolInvocation(context, handler));
        assertThat(output.status()).isEqualTo(MutationOutput.Status.EXECUTED);
        assertThat(output.result()).isEqualTo(Map.of("topic", "orders"));
        assertThat(output.plan().warnings()).contains(
                "This high-risk operation requires break-glass approval and an explicit reason.");
        assertThat(handler.executionContext).isSameAs(context);
        assertThat(handler.previews).isEqualTo(1);
        assertThat(handler.executions).isEqualTo(1);
        verify(tokens).verify(context);
    }

    @Test
    void invalidTokenPreventsPlanGenerationAndExecution() {
        ToolExecutionContext context = context(ToolRiskLevel.L2, Map.of("topic", "orders", "confirm_token", "invalid"));
        var failure = ToolError.CONFIRMATION_TOKEN_INVALID.exception("rmq.topic.create");
        doThrow(failure).when(tokens).verify(context);

        assertThatThrownBy(() -> chain.execute(new ToolInvocation(context, handler))).isSameAs(failure);
        assertThat(handler.previews).isZero();
        assertThat(handler.executions).isZero();
    }

    private static ToolExecutionContext context(ToolRiskLevel risk, Map<String, Object> input) {
        ToolDefinition definition = new ToolDefinition("rmq.topic.create",
                new ToolDefinition.Cli("topic", "create"), "Create topic", risk,
                "topic:write", List.of(), Map.of(), Map.of(), null, false, null);
        return ToolExecutionContext.of("instance-a", definition, input, "alice");
    }

    private record Input(String topic) {
    }

    private static class TestHandler extends MutationToolHandler<Input, Object> {
        private int previews;
        private int executions;
        private ToolExecutionContext executionContext;

        private TestHandler() {
            super(Input.class);
        }

        @Override
        public String name() {
            return "rmq.topic.create";
        }

        @Override
        public ToolPlan preview(Input input, ToolExecutionContext context) {
            previews++;
            return ToolPlan.builder("Create topic")
                    .before(Map.of("exists", false))
                    .after(Map.of("topic", input.topic()))
                    .impact("Creates the topic configuration.")
                    .warning("Review the topic configuration.")
                    .build();
        }

        @Override
        public Object execute(Input input, ToolExecutionContext context) {
            executions++;
            executionContext = context;
            return Map.of("topic", input.topic());
        }
    }
}
