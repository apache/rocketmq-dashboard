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
package org.apache.rocketmq.studio.ops.ai.tool.filter;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.CapabilityResolver;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolInvocation;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Refuses a tool unless the Instance bound to the execution context supports every capability the
 * tool declares.
 *
 * <p>Platform-level tools ({@code ToolCatalog.isInstanceIdExempt}) take no {@code instanceId}
 * argument, but the gate is deliberately <em>not</em> skipped for them: their Instance is the one the
 * transport already authenticated (the signed MCP {@code x-rmq-instance-id} header, or the target the
 * console operator selected) and {@code ToolExecutionService} binds it into the context. An exempt
 * tool therefore still runs only against an Instance that supports it, and an exempt tool with no
 * binding at all fails loudly with {@code CAPABILITY_INSTANCE_REQUIRED} instead of running unscoped.
 *
 * <p>Exempt handlers never read {@code context.instanceId()} -- only this gate and
 * {@link ToolAuditFilter} do -- so binding one cannot widen the data a platform-level tool returns.
 * Tools that declare no {@code requiredCapabilities} at all (today {@code rmq.audit.list},
 * {@code rmq.alert.rule.list}, {@code rmq.proxy.list} and {@code rmq.proxy.config}) stay ungated:
 * their data is deployment-wide, so there is no capability to check and no Instance to bind.
 * The Proxy pair aggregates every manageable Instance through the heartbeat-syncer group, so
 * gating them on the bound Instance's type would refuse legitimate discovery whenever that
 * Instance is not Proxy-attached even though Proxies exist elsewhere in the deployment.
 */
@Component
@RequiredArgsConstructor
public class ToolCapabilityFilter implements ToolExecutionFilter {

    private final CapabilityResolver capabilityResolver;

    @Override
    public Type type() {
        return Type.CAPABILITY;
    }

    @Override
    public Object filter(ToolInvocation invocation, Chain chain) {
        ToolExecutionContext context = invocation.context();
        ToolDefinition definition = context.definition();
        if (definition.requiredCapabilities().isEmpty()) {
            return chain.proceed(invocation);
        }

        Set<String> capabilities = capabilityResolver.resolve(context.instanceId());
        if (!capabilities.containsAll(definition.requiredCapabilities())) {
            throw ToolError.TOOL_CAPABILITY_UNSUPPORTED.exception(definition.name());
        }
        return chain.proceed(invocation);
    }
}
