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
package org.apache.rocketmq.studio.ops.ai.tool.handler;

import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolInvocation;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolRiskLevel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link MutationToolHandler}: the base every mutating tool handler extends. The contract
 * has two fixed points - inputType() is final so a subclass can never lie about the type the
 * deferred conversion uses, and preview() is the abstract mutation-preview seam the whole
 * confirmation chain routes through.
 */
class MutationToolHandlerTest {

    private record SampleInput(String name) {
    }

    private static final class SampleHandler extends MutationToolHandler<SampleInput, String> {

        SampleHandler() {
            super(SampleInput.class);
        }

        @Override
        public String name() {
            return "rmq.sample.update";
        }

        @Override
        public ToolPlan preview(SampleInput input, ToolExecutionContext context) {
            return ToolPlan.builder("update the sample")
                    .impact("changes " + input.name())
                    .build();
        }

        @Override
        public String execute(SampleInput input, ToolExecutionContext context) {
            return "updated " + input.name();
        }
    }

    @Test
    void theInputTypeComesFromTheConstructor() {
        assertThat(new SampleHandler().inputType()).isEqualTo(SampleInput.class);
    }

    @Test
    void inputTypeIsFinalSoASubclassCannotLieAboutIt() {
        // the deferred conversion in ToolInvocation routes on exactly this
        // method; a subclass overriding it would desynchronise the chain
        for (Method method : MutationToolHandler.class.getDeclaredMethods()) {
            if (method.getName().equals("inputType")) {
                assertThat(Modifier.isFinal(method.getModifiers()))
                        .as("inputType() must stay final").isTrue();
            }
        }
    }

    @Test
    void previewIsTheAbstractMutationSeam() throws NoSuchMethodException {
        Method preview = MutationToolHandler.class.getDeclaredMethod(
                "preview", Object.class, ToolExecutionContext.class);

        assertThat(Modifier.isAbstract(preview.getModifiers()))
                .as("preview() is the contract every mutation handler must implement").isTrue();
    }

    @Test
    void aConcreteHandlerSatisfiesTheWholeToolHandlerContract() {
        SampleHandler handler = new SampleHandler();
        ToolDefinition definition = new ToolDefinition(
                "rmq.sample.update", new ToolDefinition.Cli("sample", "update"),
                "updates a sample", ToolRiskLevel.L2, "sample:write",
                List.of(), Map.of(), Map.of(), null, false, null);
        ToolExecutionContext context = ToolExecutionContext.of(
                "instance-a", definition, Map.of("name", "orders", "instanceId", "instance-a"));
        ToolInvocation invocation = new ToolInvocation(context, handler);

        assertThat(invocation.preview().summary()).isEqualTo("update the sample");
        assertThat(invocation.preview().impact()).containsExactly("changes orders");
        assertThat(invocation.execute()).isEqualTo("updated orders");
    }
}
