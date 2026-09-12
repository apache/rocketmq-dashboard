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

/**
 * A tool call and its request context. Input conversion is deferred until the
 * preview or terminal invocation so failures remain inside the validation and audit chain.
 */
public record ToolInvocation(ToolExecutionContext context, ToolHandler<?, ?> handler) {

    public Object execute() {
        return invoke(handler, context);
    }

    public ToolPlan preview() {
        if (!(handler instanceof MutationToolHandler<?, ?> mutation)) {
            throw new IllegalStateException("Mutation tool requires MutationToolHandler: "
                    + context.definition().name());
        }
        return preview(mutation, context);
    }

    private static <I> ToolPlan preview(MutationToolHandler<I, ?> handler, ToolExecutionContext context) {
        I input = context.convertInput(handler.inputType());
        return handler.preview(input, context);
    }

    private static <I, O> O invoke(ToolHandler<I, O> handler, ToolExecutionContext context) {
        I input = context.convertInput(handler.inputType());
        return handler.execute(input, context);
    }
}
