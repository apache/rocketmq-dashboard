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
package org.apache.rocketmq.studio.ops.ai.tool.core;

import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins {@link ToolInvocation}: the pairing of a request context with its handler. The two
 * contracts that matter are the deferred input conversion (a malformed argument must fail inside
 * the invocation, not while the call is being assembled) and the preview type guard (only a
 * MutationToolHandler can preview, and the error must name the offending tool).
 */
class ToolInvocationTest {

    private record SampleInput(String name, int count) {
    }

    private static final class ReadOnlyHandler implements ToolHandler<SampleInput, String> {

        private SampleInput received;
        private ToolExecutionContext receivedContext;

        @Override
        public String name() {
            return "rmq.sample.describe";
        }

        @Override
        public Class<SampleInput> inputType() {
            return SampleInput.class;
        }

        @Override
        public String execute(SampleInput input, ToolExecutionContext context) {
            this.received = input;
            this.receivedContext = context;
            return "described";
        }
    }

    private static final class RecordingMutationHandler extends MutationToolHandler<SampleInput, String> {

        private SampleInput previewedInput;
        private ToolExecutionContext previewedContext;

        RecordingMutationHandler() {
            super(SampleInput.class);
        }

        @Override
        public String name() {
            return "rmq.sample.update";
        }

        @Override
        public ToolPlan preview(SampleInput input, ToolExecutionContext context) {
            this.previewedInput = input;
            this.previewedContext = context;
            return ToolPlan.builder("update the sample").build();
        }

        @Override
        public String execute(SampleInput input, ToolExecutionContext context) {
            return "updated";
        }
    }

    private static ToolDefinition definition(String name) {
        return new ToolDefinition(
                name,
                new ToolDefinition.Cli("sample", "describe"),
                "a sample tool",
                ToolRiskLevel.L1,
                "sample:read",
                List.of(),
                Map.of(),
                Map.of(),
                null,
                false,
                null);
    }

    @Test
    void executeConvertsTheBusinessInputAndDelegatesToTheHandler() {
        ReadOnlyHandler handler = new ReadOnlyHandler();
        ToolExecutionContext context =
                ToolExecutionContext.of("instance-a", definition("rmq.sample.describe"),
                        Map.of("name", "orders", "count", 2));
        ToolInvocation invocation = new ToolInvocation(context, handler);

        Object result = invocation.execute();

        assertThat(result).isEqualTo("described");
        assertThat(handler.received).isEqualTo(new SampleInput("orders", 2));
        assertThat(handler.receivedContext).isSameAs(context);
    }

    /**
     * The record's contract: conversion is deferred to the invocation, so assembling the call
     * never fails - a malformed argument surfaces inside the validation and audit chain instead.
     */
    @Test
    void inputConversionIsDeferredUntilTheInvocationItself() {
        ReadOnlyHandler handler = new ReadOnlyHandler();
        ToolExecutionContext context =
                ToolExecutionContext.of("instance-a", definition("rmq.sample.describe"),
                        Map.of("name", "orders", "count", "not-a-number"));
        ToolInvocation invocation = new ToolInvocation(context, handler);

        assertThatCode(() -> new ToolInvocation(context, handler)).doesNotThrowAnyException();
        assertThatThrownBy(invocation::execute).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void controlFieldsNeverReachTheHandlerInput() {
        ReadOnlyHandler handler = new ReadOnlyHandler();
        ToolExecutionContext context =
                ToolExecutionContext.of("instance-a", definition("rmq.sample.describe"),
                        Map.of("name", "orders", "count", 2,
                                "dry_run", true, "reason", "audit trail", "confirm_token", "tok"));
        ToolInvocation invocation = new ToolInvocation(context, handler);

        invocation.execute();

        assertThat(handler.received).isEqualTo(new SampleInput("orders", 2));
    }

    @Test
    void previewRejectsANonMutationHandlerAndNamesTheTool() {
        ReadOnlyHandler handler = new ReadOnlyHandler();
        ToolExecutionContext context =
                ToolExecutionContext.of("instance-a", definition("rmq.sample.describe"),
                        Map.of("name", "orders", "count", 2));
        ToolInvocation invocation = new ToolInvocation(context, handler);

        assertThatThrownBy(invocation::preview)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Mutation tool requires MutationToolHandler: rmq.sample.describe");
    }

    @Test
    void previewConvertsTheInputAndDelegatesToTheMutationHandler() {
        RecordingMutationHandler handler = new RecordingMutationHandler();
        ToolExecutionContext context =
                ToolExecutionContext.of("instance-a", definition("rmq.sample.update"),
                        Map.of("name", "orders", "count", 2));
        ToolInvocation invocation = new ToolInvocation(context, handler);

        ToolPlan plan = invocation.preview();

        assertThat(plan.summary()).isEqualTo("update the sample");
        assertThat(handler.previewedInput).isEqualTo(new SampleInput("orders", 2));
        assertThat(handler.previewedContext).isSameAs(context);
    }
}
