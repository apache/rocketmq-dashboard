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

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.ops.ai.AiToolCallDTO;
import org.apache.rocketmq.studio.ops.ai.AiToolExecutionPolicy;
import org.apache.rocketmq.studio.ops.ai.AiToolExecutionResultVO;
import org.apache.rocketmq.studio.ops.ai.AiToolVO;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ToolRuntimeService implements ToolDiscoveryService, ToolInvocationService {

    private final ToolGatewayService toolGatewayService;
    private final ToolCatalog toolCatalog;

    @Override
    public List<AiToolVO> listTools() {
        return toolGatewayService.discover(null);
    }

    @Override
    public List<AiToolVO> listTools(String clusterId) {
        return toolGatewayService.discover(clusterId);
    }

    @Override
    public AiToolExecutionResultVO callTool(AiToolCallDTO call) {
        String requestId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        String toolName = toolName(call);
        if (toolName == null) {
            return failed(
                    requestId,
                    null,
                    source(call),
                    null,
                    startedAt,
                    ToolErrorCodes.TOOL_NAME_REQUIRED,
                    "Tool name is required");
        }
        ToolDefinition definition = toolCatalog.find(toolName).orElse(null);
        if (definition == null) {
            return failed(
                    requestId,
                    toolName,
                    source(call),
                    null,
                    startedAt,
                    ToolErrorCodes.TOOL_NOT_FOUND,
                    "Tool not found: " + toolName);
        }
        Map<String, Object> input = new LinkedHashMap<>();
        if (call.getArguments() != null) {
            input.putAll(call.getArguments());
        }
        input.remove("apply");
        boolean applyRequested = call.isApply() || Boolean.TRUE.equals(input.get("confirmed"));
        if (call.isDryRun()) {
            input.put("dryRun", true);
        }
        if (call.isApply()) {
            input.put("confirmed", true);
        }

        String operationLevel = definition.riskLevel().operationLevel();
        try {
            Object result = toolGatewayService.execute(toolName, input);
            Instant finishedAt = Instant.now();
            boolean dryRun = Boolean.TRUE.equals(input.get("dryRun"));
            boolean executed = !dryRun;
            AiToolExecutionPolicy policy = executionPolicy(executed, dryRun);
            return AiToolExecutionResultVO.builder()
                    .requestId(requestId)
                    .toolName(toolName)
                    .source(source(call))
                    .operationLevel(operationLevel)
                    .startedAt(startedAt)
                    .finishedAt(finishedAt)
                    .dryRun(dryRun)
                    .executed(executed)
                    .policy(policy)
                    .policyReason(policyReason(policy, definition.riskLevel(), applyRequested))
                    .message(executed ? "Tool executed" : "Dry run completed")
                    .result(result)
                    .build();
        } catch (RuntimeException e) {
            return failed(
                    requestId,
                    toolName,
                    source(call),
                    operationLevel,
                    startedAt,
                    errorCode(e),
                    e.getMessage());
        }
    }

    @Override
    public ToolCatalogMetadata metadata() {
        return new ToolCatalogMetadata(
                toolCatalog.getVersion(),
                toolCatalog.getDigest(),
                toolCatalog.getMinimumClientVersion());
    }

    private static String source(AiToolCallDTO call) {
        return call == null || call.getSource() == null || call.getSource().isBlank()
                ? "UNKNOWN"
                : call.getSource();
    }

    private static String toolName(AiToolCallDTO call) {
        if (call == null || call.getName() == null || call.getName().isBlank()) {
            return null;
        }
        return call.getName();
    }

    private static AiToolExecutionResultVO failed(
            String requestId,
            String toolName,
            String source,
            String operationLevel,
            Instant startedAt,
            String errorCode,
            String message) {
        return AiToolExecutionResultVO.builder()
                .requestId(requestId)
                .toolName(toolName)
                .source(source)
                .operationLevel(operationLevel)
                .startedAt(startedAt)
                .finishedAt(Instant.now())
                .dryRun(false)
                .executed(false)
                .policy(AiToolExecutionPolicy.BLOCKED)
                .policyReason("Tool execution was blocked or failed before completion.")
                .errorCode(errorCode)
                .message(message)
                .build();
    }

    private static AiToolExecutionPolicy executionPolicy(boolean executed, boolean dryRun) {
        if (dryRun) {
            return AiToolExecutionPolicy.DRY_RUN;
        }
        if (executed) {
            return AiToolExecutionPolicy.EXECUTE;
        }
        return AiToolExecutionPolicy.BLOCKED;
    }

    private static String policyReason(
            AiToolExecutionPolicy policy,
            ToolRiskLevel riskLevel,
            boolean applyRequested) {
        if (AiToolExecutionPolicy.DRY_RUN == policy) {
            return "Mutation and high-risk tools return dry-run plans unless explicit apply confirmation is provided.";
        }
        if (AiToolExecutionPolicy.EXECUTE == policy && riskLevel.readOnly()) {
            return "Read-only L1 tool executed directly.";
        }
        if (AiToolExecutionPolicy.EXECUTE == policy && applyRequested) {
            return "Tool execution was explicitly requested with apply confirmation.";
        }
        if (AiToolExecutionPolicy.EXECUTE == policy) {
            return "Tool executed directly.";
        }
        return "Tool execution was blocked or failed before completion.";
    }

    private static String errorCode(RuntimeException exception) {
        if (exception instanceof ToolExecutionException toolException) {
            return toolException.getErrorCode();
        }
        if (exception instanceof IllegalStateException) {
            return ToolErrorCodes.TOOL_OUTPUT_INVALID;
        }
        return ToolErrorCodes.TOOL_EXECUTION_FAILED;
    }
}
