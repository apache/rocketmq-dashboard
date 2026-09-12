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
package org.apache.rocketmq.studio.ops.ai.tool.handler.broker;

import org.apache.rocketmq.studio.cluster.broker.BrokerConfigDiffService;
import org.apache.rocketmq.studio.cluster.config.BrokerConfigDiffVO;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.broker.BrokerConfigOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.BrokerClusterInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BrokerConfigToolHandler implements ToolHandler<BrokerClusterInput, BrokerConfigOutput> {

    private final BrokerConfigDiffService brokerConfigDiffService;

    @Override
    public String name() {
        return "rmq.broker.config";
    }

    @Override
    public Class<BrokerClusterInput> inputType() {
        return BrokerClusterInput.class;
    }

    @Override
    public BrokerConfigOutput execute(
            BrokerClusterInput input, ToolExecutionContext context) {
        BrokerConfigDiffVO diff = brokerConfigDiffService.compareForInstance(context.cluster());
        return BrokerConfigOutput.from(diff, context.cluster());
    }
}
