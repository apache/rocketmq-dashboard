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
package org.apache.rocketmq.studio.ops.ai.tool.handler.instance;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.CapabilityResolver;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.InstanceInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.instance.InstanceCapabilitiesOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InstanceCapabilitiesToolHandler
        implements ToolHandler<InstanceInput, InstanceCapabilitiesOutput> {

    private final CapabilityResolver capabilityResolver;

    @Override
    public String name() {
        return "rmq.instance.capabilities";
    }

    @Override
    public Class<InstanceInput> inputType() {
        return InstanceInput.class;
    }

    @Override
    public InstanceCapabilitiesOutput execute(InstanceInput input, ToolExecutionContext context) {
        return new InstanceCapabilitiesOutput(context.instanceId(),
                capabilityResolver.resolve(context.instanceId()).stream().sorted().toList());
    }
}
