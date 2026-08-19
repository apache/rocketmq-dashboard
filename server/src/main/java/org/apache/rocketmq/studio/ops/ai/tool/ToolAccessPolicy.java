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
package org.apache.rocketmq.studio.ops.ai.tool;

import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

@Component
public class ToolAccessPolicy {

    private static final String TOOL_EXECUTION_PREFIX = "/api/ai/tools/";
    private static final String TOOL_EXECUTION_SUFFIX = "/execute";
    /**
     * These read-labeled tools still let callers steer RocketMQ remoting to user-influenced
     * broker addresses via msgId-derived lookups, so readers must not gain access before the
     * address-ownership validation bug is fixed.
     */
    private static final Set<String> READER_DENY_LIST = Set.of(
            "rmq.message.query",
            "rmq.message.trace");

    private final ToolCatalog catalog;

    public ToolAccessPolicy(ToolCatalog catalog) {
        this.catalog = catalog;
    }

    public boolean isToolExecutionPath(String requestPath) {
        return requestPath != null
                && requestPath.startsWith(TOOL_EXECUTION_PREFIX)
                && requestPath.endsWith(TOOL_EXECUTION_SUFFIX);
    }

    public boolean isReaderAccessiblePath(String requestPath) {
        return resolveExecutionDefinition(requestPath)
                .map(this::isReaderAccessible)
                .orElse(false);
    }

    public boolean isReaderAccessible(ToolDefinition definition) {
        return definition != null
                && definition.isLowRiskReadOnly()
                && !READER_DENY_LIST.contains(definition.name());
    }

    public void authorizeCurrentUser(ToolDefinition definition) {
        if (AuthenticatedUserContext.currentUserIsAdmin() || isReaderAccessible(definition)) {
            return;
        }
        throw new BusinessException(403, "Admin permission required");
    }

    private Optional<ToolDefinition> resolveExecutionDefinition(String requestPath) {
        if (!isToolExecutionPath(requestPath)) {
            return Optional.empty();
        }
        String encodedName = requestPath.substring(
                TOOL_EXECUTION_PREFIX.length(),
                requestPath.length() - TOOL_EXECUTION_SUFFIX.length());
        if (encodedName.isBlank()) {
            return Optional.empty();
        }
        String toolName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8);
        return catalog.find(toolName);
    }
}
