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
        return executeInternal(name, input, null);
    }

    /** Called only with authentication established by the MCP transport. */
    public Object execute(String name, Map<String, Object> input, McpAuthentication authentication) {
        if (authentication == null) {
            throw ToolError.MCP_AUTHENTICATION_REQUIRED.exception();
        }
        return executeInternal(name, input, authentication);
    }

    private Object executeInternal(String name, Map<String, Object> input, McpAuthentication authentication) {
        try {
            ToolDefinition definition = catalog.getDefinition(name);
            Object value = input == null ? null : input.get("cluster");
            if (!(value instanceof String cluster) || cluster.isBlank()) {
                throw ToolError.TOOL_CLUSTER_REQUIRED.exception();
            }
            if (authentication != null) {
                if (!cluster.equals(authentication.cluster())) {
                    throw ToolError.TOOL_TARGET_MISMATCH.exception();
                }
            } else {
                instanceResolver.findByName(cluster)
                        .orElseThrow(() -> ToolError.INSTANCE_NOT_FOUND.exception(cluster));
            }
            String caller = authentication == null
                    ? AuthenticatedUserContext.currentUsernameOrSystem() : authentication.principal();
            ToolExecutionContext context = ToolExecutionContext.of(cluster, definition, input, caller);

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
}
