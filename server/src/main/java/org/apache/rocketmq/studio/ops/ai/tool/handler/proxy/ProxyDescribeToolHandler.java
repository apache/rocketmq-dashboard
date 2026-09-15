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
package org.apache.rocketmq.studio.ops.ai.tool.handler.proxy;

import org.apache.rocketmq.studio.cluster.proxy.ProxyAddressService;
import org.apache.rocketmq.studio.cluster.proxy.ProxyTopologyVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ClusterInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProxyDescribeToolHandler
        implements ToolHandler<ClusterInput, ListOutput<ProxyTopologyVO>> {

    private final ProxyAddressService proxyAddressService;

    @Override
    public String name() {
        return "rmq.proxy.describe";
    }

    @Override
    public Class<ClusterInput> inputType() {
        return ClusterInput.class;
    }

    @Override
    public ListOutput<ProxyTopologyVO> execute(
            ClusterInput input, ToolExecutionContext context) {
        List<ProxyTopologyVO> topology = proxyAddressService.buildTopology();
        return new ListOutput<>(topology);
    }
}
