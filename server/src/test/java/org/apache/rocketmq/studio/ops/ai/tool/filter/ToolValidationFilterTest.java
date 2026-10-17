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
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolSchemaValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pins {@link ToolValidationFilter} and the ordering {@link ToolFilterChain} wraps it in: input is
 * validated BEFORE the tool runs, output AFTER it returns, and the chain composes filters by
 * their declared type order.
 */
class ToolValidationFilterTest {

    private final ToolSchemaValidator schemaValidator = mock(ToolSchemaValidator.class);
    private final ToolValidationFilter filter = new ToolValidationFilter(schemaValidator);

    /** Records the order of events across a whole chain run. */
    private static final class Journal {

        private final List<String> events = new java.util.ArrayList<>();
        private final AtomicInteger calls = new AtomicInteger();

        void add(String event) {
            events.add(event + "@" + calls.incrementAndGet());
        }
    }

    private static ToolInvocation invocation() {
        ToolDefinition definition = new ToolDefinition(
                "rmq.topic.list", new ToolDefinition.Cli("topic", "list"), "lists topics",
                ToolRiskLevel.L1, "topic:read", List.of(), Map.of(), Map.of(), null, false, null);
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
    void theFilterDeclaresTheValidationType() {
        assertThat(filter.type()).isEqualTo(ToolExecutionFilter.Type.VALIDATION);
    }

    @Test
    void inputIsValidatedBeforeTheToolRunsAndOutputAfter() {
        Journal journal = new Journal();
        ToolSchemaValidator recording = mock(ToolSchemaValidator.class);
        org.mockito.Mockito.doAnswer(inv -> {
            journal.add("validate-input");
            return null;
        }).when(recording).validateInput(any(), any());
        org.mockito.Mockito.doAnswer(inv -> {
            journal.add("validate-output");
            return null;
        }).when(recording).validateOutput(any(), any());
        ToolValidationFilter recordingFilter = new ToolValidationFilter(recording);
        ToolExecutionFilter.Chain chain = new ToolExecutionFilter.Chain() {
            @Override
            public Object proceed(ToolInvocation inv) {
                journal.add("tool");
                return "result";
            }
        };

        Object result = recordingFilter.filter(invocation(), chain);

        assertThat(result).isEqualTo("result");
        assertThat(journal.events).containsExactly("validate-input@1", "tool@2", "validate-output@3");
    }

    @Test
    void anInvalidInputNeverReachesTheTool() {
        doThrow(new org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "bad", "fix"))
                .when(schemaValidator).validateInput(any(), any());

        ToolExecutionFilter.Chain chain = mock(ToolExecutionFilter.Chain.class);

        assertThatThrownBy(() -> filter.filter(invocation(), chain))
                .isInstanceOf(org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException.class);
        verify(chain, never()).proceed(any());
        verify(schemaValidator, never()).validateOutput(any(), any());
    }

    @Test
    void anInvalidOutputSurfacesAfterTheToolRan() {
        doThrow(new IllegalStateException("output validation failed"))
                .when(schemaValidator).validateOutput(any(), any());
        ToolExecutionFilter.Chain chain = inv -> "computed";

        assertThatThrownBy(() -> filter.filter(invocation(), chain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("output validation failed");
    }

    @Test
    void theChainComposesFiltersInTypeOrder() {
        // order of Type enum values: AUDIT, VALIDATION, CAPABILITY, MUTATION
        StringBuilder order = new StringBuilder();
        ToolExecutionFilter audit = new ToolExecutionFilter() {
            @Override
            public Type type() {
                return Type.AUDIT;
            }

            @Override
            public Object filter(ToolInvocation inv, Chain chain) {
                order.append("audit>");
                return chain.proceed(inv);
            }
        };
        ToolExecutionFilter capability = new ToolExecutionFilter() {
            @Override
            public Type type() {
                return Type.CAPABILITY;
            }

            @Override
            public Object filter(ToolInvocation inv, Chain chain) {
                order.append("capability>");
                return chain.proceed(inv);
            }
        };
        ToolExecutionFilter mutation = new ToolExecutionFilter() {
            @Override
            public Type type() {
                return Type.MUTATION;
            }

            @Override
            public Object filter(ToolInvocation inv, Chain chain) {
                order.append("mutation>");
                return chain.proceed(inv);
            }
        };
        ToolExecutionFilter validation = new ToolExecutionFilter() {
            @Override
            public Type type() {
                return Type.VALIDATION;
            }

            @Override
            public Object filter(ToolInvocation inv, Chain chain) {
                order.append("validation>");
                return chain.proceed(inv);
            }
        };
        // handed over in scrambled order; the chain must sort by declared type
        ToolFilterChain chain = new ToolFilterChain(
                List.of(mutation, validation, audit, capability));

        Object result = chain.execute(invocation());

        assertThat(result).isEqualTo("executed");
        assertThat(order.toString()).isEqualTo("audit>validation>capability>mutation>");
    }

    @Test
    void anEmptyChainRunsTheToolDirectly() {
        ToolFilterChain chain = new ToolFilterChain(List.of());

        assertThat(chain.execute(invocation())).isEqualTo("executed");
    }
}
