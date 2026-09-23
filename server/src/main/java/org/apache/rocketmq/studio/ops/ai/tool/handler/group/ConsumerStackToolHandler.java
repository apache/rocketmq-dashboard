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
package org.apache.rocketmq.studio.ops.ai.tool.handler.group;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.instance.group.ConsumerDiagnosticsService;
import org.apache.rocketmq.studio.instance.group.ConsumerStackTraceVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.ConsumerStackInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.ConsumerStackOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConsumerStackToolHandler implements ToolHandler<ConsumerStackInput, ConsumerStackOutput> {

    private final ConsumerDiagnosticsService consumerDiagnosticsService;

    @Override
    public String name() {
        return "rmq.group.consumer_stack";
    }

    @Override
    public Class<ConsumerStackInput> inputType() {
        return ConsumerStackInput.class;
    }

    @Override
    public ConsumerStackOutput execute(ConsumerStackInput input, ToolExecutionContext context) {
        ConsumerStackTraceVO stack = consumerDiagnosticsService.getConsumerStack(
                context.instanceId(), input.groupName(), input.clientId());
        return ConsumerStackOutput.from(context.instanceId(), stack);
    }
}
