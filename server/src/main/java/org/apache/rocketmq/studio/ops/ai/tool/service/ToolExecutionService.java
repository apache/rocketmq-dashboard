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
package org.apache.rocketmq.studio.ops.ai.tool.service;

import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceResolver;
import org.apache.rocketmq.studio.ops.ai.auth.McpAuthentication;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolInvocation;
import org.apache.rocketmq.studio.ops.ai.tool.filter.ToolFilterChain;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolExecutionService {

    private final ToolCatalog catalog;
    private final ToolFilterChain filterChain;
    private final InstanceResolver instanceResolver;
    private final Map<String, ToolHandler<?, ?>> handlers;

    public ToolExecutionService(
            ToolCatalog catalog,
            List<ToolHandler<?, ?>> handlers,
            ToolFilterChain filterChain,
            InstanceResolver instanceResolver) {
        this.catalog = catalog;
        this.filterChain = filterChain;
        this.instanceResolver = instanceResolver;
        this.handlers = init(catalog, handlers);
    }

    private static Map<String, ToolHandler<?, ?>> init(ToolCatalog catalog, List<ToolHandler<?, ?>> handlers) {
        Map<String, ToolHandler<?, ?>> registered = new LinkedHashMap<>();
        for (ToolHandler<?, ?> handler : handlers) {
            if (registered.putIfAbsent(handler.name(), handler) != null) {
                throw new IllegalStateException("Duplicate tool handler: " + handler.name());
            }
            if (catalog.find(handler.name()).isEmpty()) {
                throw new IllegalStateException("Tool handler is absent from catalog: " + handler.name());
            }
            ToolDefinition definition = catalog.getDefinition(handler.name());
            if (!definition.isReadOnly() && !(handler instanceof MutationToolHandler<?, ?>)) {
                throw new IllegalStateException("Mutation tool requires MutationToolHandler: " + handler.name());
            }
        }
        List<String> missing = catalog.list().stream()
                .map(ToolDefinition::name)
                .filter(name -> !registered.containsKey(name))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Tool catalog contains tools without handlers: " + missing);
        }
        return Collections.unmodifiableMap(registered);
    }

    public Object execute(String name, Map<String, Object> input) {
        return executeInternal(name, input, null, null);
    }

    /**
     * Called only with authentication established by the MCP transport. The signed
     * {@code x-rmq-instance-id} header is the caller's Instance binding, which is what platform-level
     * tools are resolved against; see {@link #resolveTargetInstance}.
     */
    public Object execute(String name, Map<String, Object> input, McpAuthentication authentication) {
        if (authentication == null) {
            throw ToolError.MCP_AUTHENTICATION_REQUIRED.exception();
        }
        return executeInternal(name, input, authentication, authentication.instanceId());
    }

    /**
     * Console entry point. A browser session authenticates the operator but carries no MCP instance
     * header, so the Instance selected in the Tool Playground is passed as the target instead. It is
     * deliberately <em>not</em> merged into {@code input} for platform-level tools: their schemas set
     * {@code additionalProperties: false} and would reject the extra argument.
     */
    public Object executeWithTarget(String name, Map<String, Object> input, String targetInstanceId) {
        return executeInternal(name, input, null, targetInstanceId);
    }

    private Object executeInternal(
            String name,
            Map<String, Object> input,
            McpAuthentication authentication,
            String targetInstanceId) {
        try {
            ToolDefinition definition = catalog.getDefinition(name);
            String instanceId = resolveTargetInstance(definition, input, authentication, targetInstanceId);
            String caller = authentication == null
                    ? AuthenticatedUserContext.currentUsernameOrSystem() : authentication.principal();
            ToolExecutionContext context = ToolExecutionContext.of(instanceId, definition, input, caller);

            ToolHandler<?, ?> handler = this.handlers.get(name);
            return filterChain.execute(new ToolInvocation(context, handler));
        } catch (BusinessException exception) {
            throw ToolExecutionException.from(exception);
        } catch (RuntimeException exception) {
            ToolExecutionException internal = ToolError.UNEXPECTED_EXECUTION_FAILURE.exception();
            internal.initCause(exception);
            throw internal;
        }
    }

    /**
     * Reads the Studio instance target from the tool arguments. Platform-level tools are addressed
     * by a physical {@code clusterName} instead and their input schemas reject an {@code instanceId}
     * argument, so they skip both the mandatory-argument check and the authenticated-target
     * cross-check; their Instance comes from the transport binding (the signed MCP header, or the
     * target the console operator selected) so that the capability gate still has something to
     * resolve against.
     */
    private String resolveTargetInstance(
            ToolDefinition definition,
            Map<String, Object> input,
            McpAuthentication authentication,
            String targetInstanceId) {
        boolean exempt = ToolCatalog.isInstanceIdExempt(definition.name());
        Object value = input == null ? null : input.get(ToolCatalog.INSTANCE_ID_FIELD);
        if (!(value instanceof String instanceId) || instanceId.isBlank()) {
            if (exempt) {
                return targetInstanceId == null || targetInstanceId.isBlank() ? null : targetInstanceId;
            }
            throw ToolError.TOOL_INSTANCE_REQUIRED.exception(definition.name());
        }
        if (exempt) {
            return instanceId;
        }
        if (authentication != null) {
            if (!instanceId.equals(authentication.instanceId())) {
                // Echo the bound instance back: an agent that guessed a wrong id (a cluster id, a
                // stale value) self-corrects on the next call instead of probing candidate ids.
                throw ToolError.TOOL_TARGET_MISMATCH.exception(authentication.instanceId());
            }
        } else {
            instanceResolver.findByName(instanceId)
                    .orElseThrow(() -> ToolError.INSTANCE_NOT_FOUND.exception(instanceId));
        }
        return instanceId;
    }
}
