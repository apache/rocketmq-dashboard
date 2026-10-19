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

import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolInvocation;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolRiskLevel;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Pins {@link ToolAuditFilter}: the audit leg of the tool chain. Two contracts matter - the audit
 * trail records BOTH outcomes with the tool's identity, and an audit-store failure never takes the
 * tool call down with it (the answer the operator is waiting for outranks the log line).
 */
class ToolAuditFilterTest {

    private final AuditService auditService = mock(AuditService.class);
    private final ToolAuditFilter filter = new ToolAuditFilter(auditService);

    private static final class PlannedChain implements ToolExecutionFilter.Chain {

        private final Object output;
        private final RuntimeException failure;

        PlannedChain(Object output, RuntimeException failure) {
            this.output = output;
            this.failure = failure;
        }

        @Override
        public Object proceed(ToolInvocation invocation) {
            if (failure != null) {
                throw failure;
            }
            return output;
        }
    }

    private static ToolInvocation invocation() {
        ToolDefinition definition = new ToolDefinition(
                "rmq.topic.list",
                new ToolDefinition.Cli("topic", "list"),
                "lists topics",
                ToolRiskLevel.L1,
                "topic:read",
                List.of(),
                Map.of(),
                Map.of(),
                null,
                false,
                null);
        ToolExecutionContext context =
                ToolExecutionContext.of("instance-a", definition, Map.of("instanceId", "instance-a"));
        ToolHandler<Map<String, Object>, Object> handler = new ToolHandler<>() {
            @Override
            public String name() {
                return definition.name();
            }

            @Override
            @SuppressWarnings("unchecked")
            public Class<Map<String, Object>> inputType() {
                return (Class<Map<String, Object>>) (Class<?>) Map.class;
            }

            @Override
            public Object execute(Map<String, Object> input, ToolExecutionContext executionContext) {
                return "executed";
            }
        };
        return new ToolInvocation(context, handler);
    }

    @Test
    void aSuccessfulCallIsAuditedAsSuccessAndItsOutputFlowsThrough() {
        Object output = new Object();
        ToolInvocation call = invocation();

        Object result = filter.filter(call, new PlannedChain(output, null));

        assertThat(result).isSameAs(output);
        verify(auditService).record(
                eq("LIST_TOPIC"), eq("TOPIC"), eq("rmq.topic.list"), eq("instance-a"),
                isNull(), eq("SUCCESS"));
    }

    @Test
    void aFailingCallIsAuditedAsFailedWithItsMessageAndTheFailureRethrown() {
        IllegalStateException boom = new IllegalStateException("agent stream broke");
        ToolInvocation call = invocation();

        assertThatThrownBy(() -> filter.filter(call, new PlannedChain(null, boom)))
                .isSameAs(boom);
        verify(auditService).record(
                eq("LIST_TOPIC"), eq("TOPIC"), eq("rmq.topic.list"), eq("instance-a"),
                eq("agent stream broke"), eq("FAILED"));
    }

    /**
     * The audit store going down must never take the tool call with it: the answer the operator is
     * waiting for outranks the log line.
     */
    @Test
    void anAuditStoreFailureNeverFailsASuccessfulToolCall() {
        doThrow(new RuntimeException("audit repository down")).when(auditService)
                .record(any(), any(), any(), any(), any(), any());
        Object output = new Object();
        ToolInvocation call = invocation();

        Object result = filter.filter(call, new PlannedChain(output, null));

        assertThat(result).isSameAs(output);
    }

    @Test
    void anAuditStoreFailureOnAFailingCallStillRethrowsTheOriginalFailure() {
        doThrow(new RuntimeException("audit repository down")).when(auditService)
                .record(any(), any(), any(), any(), any(), any());
        IllegalStateException boom = new IllegalStateException("agent stream broke");
        ToolInvocation call = invocation();

        assertThatThrownBy(() -> filter.filter(call, new PlannedChain(null, boom)))
                .isSameAs(boom);
    }

    @Test
    void theFilterDeclaresTheAuditType() {
        assertThat(filter.type()).isEqualTo(ToolExecutionFilter.Type.AUDIT);
    }
}
