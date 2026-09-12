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
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.proxy.ProxyConfigUpdateInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.proxy.ProxyConfigUpdateOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ProxyConfigUpdateToolHandler extends MutationToolHandler<ProxyConfigUpdateInput, ProxyConfigUpdateOutput> {

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "update proxy configuration '%s' in cluster '%s'.",
            List.of("Requests the selected proxy process to reload its configuration."),
            List.of());

    private final ProxyAddressService proxyAddressService;

    public ProxyConfigUpdateToolHandler(ProxyAddressService proxyAddressService) {
        super(ProxyConfigUpdateInput.class);
        this.proxyAddressService = proxyAddressService;
    }

    @Override
    public String name() {
        return "rmq.proxy.config_update";
    }

    @Override
    public ToolPlan preview(ProxyConfigUpdateInput input, ToolExecutionContext context) {
        ProxyTopologyVO proxy = proxyAddressService.previewReloadForInstance(context.cluster(), input.addr());
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("addr", proxy.getProxyAddr());
        before.put("status", proxy.getStatus());
        before.put("grpcReachable", proxy.isGrpcReachable());
        before.put("remotingReachable", proxy.isRemotingReachable());
        Map<String, Object> after = Map.of(
                "addr", input.addr(),
                "operation", "RELOAD_CONFIG",
                "status", "RE_EVALUATED_AT_APPLY");
        return PLAN_DESCRIPTION.builder(input.addr(), context.cluster())
                .before(before)
                .after(after)
                .build();
    }

    @Override
    public ProxyConfigUpdateOutput execute(
            ProxyConfigUpdateInput input, ToolExecutionContext context) {
        proxyAddressService.reloadConfigForInstance(context.cluster(), input.addr());
        return ProxyConfigUpdateOutput.of(input.addr());
    }
}
