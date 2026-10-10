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
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;

import java.util.Map;

public abstract class MutationToolHandler<I, R>
        implements ToolHandler<I, R> {

    private final Class<I> inputType;

    protected MutationToolHandler(Class<I> inputType) {
        this.inputType = inputType;
    }

    @Override
    public final Class<I> inputType() {
        return inputType;
    }

    /**
     * Stable business state to bind to confirmation, or null to retain request-only confirmation.
     * Opt in only when the projection excludes presentation text and volatile measurements.
     */
    public Map<String, Object> confirmationState(ToolPlan plan) {
        return null;
    }

    public abstract ToolPlan preview(I input, ToolExecutionContext context);
}
