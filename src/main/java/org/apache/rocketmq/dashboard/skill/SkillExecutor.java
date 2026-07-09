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
package org.apache.rocketmq.dashboard.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SkillExecutor bridges the MCP tool calling protocol with the Skill system.
 * It translates MCP tool calls into Skill executions and formats results
 * back into MCP-compatible responses.
 */
@Slf4j
@Component
public class SkillExecutor {

    @Autowired
    private SkillRegistry skillRegistry;

    /**
     * Execute a skill by its ID (used as MCP tool name)
     *
     * @param toolName the tool name (skill ID)
     * @param arguments the arguments map
     * @return MCP-formatted result
     */
    public Map<String, Object> executeTool(String toolName, Map<String, Object> arguments) {
        Skill skill = skillRegistry.getSkill(toolName);
        if (skill == null) {
            return buildErrorResult("Skill not found: " + toolName);
        }

        if (!skill.isAvailable()) {
            return buildErrorResult("Skill is not available: " + toolName);
        }

        try {
            log.debug("Executing skill: {} with arguments: {}", toolName, arguments);
            SkillResult result = skill.execute(arguments != null ? arguments : new HashMap<>());

            if (result.isSuccess()) {
                return buildSuccessResult(result);
            } else {
                return buildErrorResult(result.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Error executing skill: {}", toolName, e);
            return buildErrorResult("Skill execution failed: " + e.getMessage());
        }
    }

    /**
     * List all available skills as MCP tool definitions
     *
     * @return list of MCP tool definitions
     */
    public List<Map<String, Object>> listTools() {
        return skillRegistry.getAvailableSkills().stream()
                .map(this::toMcpToolDefinition)
                .collect(Collectors.toList());
    }

    /**
     * Convert a Skill to MCP tool definition format
     */
    private Map<String, Object> toMcpToolDefinition(Skill skill) {
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", skill.getId());
        tool.put("description", skill.getDescription());

        // Build input schema from skill parameters
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        List<String> required = new java.util.ArrayList<>();

        for (SkillParameter param : skill.getParameters()) {
            Map<String, Object> prop = new HashMap<>();
            prop.put("type", mapToJsonSchemaType(param.getType()));
            prop.put("description", param.getDescription());

            if (param.getDefaultValue() != null) {
                prop.put("default", param.getDefaultValue());
            }

            if (param.getAllowedValues() != null && !param.getAllowedValues().isEmpty()) {
                prop.put("enum", param.getAllowedValues());
            }

            properties.put(param.getName(), prop);

            if (param.isRequired()) {
                required.add(param.getName());
            }
        }

        inputSchema.put("properties", properties);
        if (!required.isEmpty()) {
            inputSchema.put("required", required);
        }

        tool.put("inputSchema", inputSchema);
        return tool;
    }

    /**
     * Map SkillParameter type to JSON Schema type
     */
    private String mapToJsonSchemaType(String type) {
        if (type == null) {
            return "string";
        }
        switch (type.toUpperCase()) {
            case "STRING":
            case "ENUM":
                return "string";
            case "INTEGER":
            case "INT":
            case "LONG":
                return "integer";
            case "BOOLEAN":
            case "BOOL":
                return "boolean";
            case "OBJECT":
                return "object";
            case "ARRAY":
                return "array";
            default:
                return "string";
        }
    }

    /**
     * Build a success result in MCP format
     */
    private Map<String, Object> buildSuccessResult(SkillResult result) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);

        // Format the data based on return type
        Object data = result.getData();
        String returnType = result.getReturnType();

        if ("TEXT".equals(returnType) && data instanceof String) {
            response.put("content", data);
        } else if ("LIST".equals(returnType) && data instanceof List) {
            response.put("content", data);
            response.put("count", ((List<?>) data).size());
        } else if ("OBJECT".equals(returnType)) {
            response.put("content", data);
        } else {
            response.put("content", data != null ? data.toString() : "");
        }

        if (result.getMetadata() != null) {
            response.put("metadata", result.getMetadata());
        }

        return response;
    }

    /**
     * Build an error result in MCP format
     */
    private Map<String, Object> buildErrorResult(String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", errorMessage);
        return response;
    }
}
