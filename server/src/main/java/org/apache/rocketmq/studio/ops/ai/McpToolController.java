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
package org.apache.rocketmq.studio.ops.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.ops.ai.auth.McpAuthentication;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolExecutionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/mcp/tools")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true")
public class McpToolController {

    private final ToolExecutionService toolExecutor;
    private final ObjectMapper objectMapper;

    @PostMapping("/call")
    public Result<Object> callTool(
            @RequestBody(required = false) AiToolCallDTO call,
            HttpServletRequest request) {
        McpAuthentication authentication = authentication(request);
        if (call == null) {
            throw ToolError.TOOL_CALL_REQUIRED.exception();
        }
        AiPayloadGuard.validateToolInvocation(call.getName(), call.getArguments(), objectMapper);
        log.info("Calling registered AI tool: {}", call.getName());
        return Result.ok(toolExecutor.execute(
                call.getName(), call.getArguments(), authentication));
    }

    private static McpAuthentication authentication(HttpServletRequest request) {
        Object authentication = request.getAttribute(McpAuthentication.ATTRIBUTE);
        if (authentication instanceof McpAuthentication mcpAuthentication) {
            return mcpAuthentication;
        }
        throw ToolError.MCP_AUTHENTICATION_REQUIRED.exception();
    }
}
