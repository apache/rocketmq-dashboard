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

import org.apache.rocketmq.studio.cluster.broker.BrokerVO;
import org.apache.rocketmq.studio.cluster.broker.ClusterProvider;
import org.apache.rocketmq.studio.ops.ai.tool.contract.broker.BrokerDescribeInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ClusterListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BrokerDescribeToolHandler
        implements ToolHandler<BrokerDescribeInput, ClusterListOutput<BrokerVO>> {

    private final ClusterProvider clusterProvider;

    @Override
    public String name() {
        return "rmq.broker.describe";
    }

    @Override
    public Class<BrokerDescribeInput> inputType() {
        return BrokerDescribeInput.class;
    }

    @Override
    public ClusterListOutput<BrokerVO> execute(
            BrokerDescribeInput input, ToolExecutionContext context) {
        List<BrokerVO> matched = clusterProvider.discoverBrokers(context.cluster(), input.broker());
        return new ClusterListOutput<>(context.cluster(), matched);
    }
}
