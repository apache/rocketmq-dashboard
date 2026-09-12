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
package org.apache.rocketmq.studio.ops.ai.tool.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record ToolExecutionContext(
        String cluster,
        ToolDefinition definition,
        Map<String, Object> input,
        String principal) {

    private static final ObjectMapper INPUT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Set<String> CONTROL_FIELDS = Set.of(
            "break_glass",
            "confirm_token",
            "dry_run",
            "reason");

    public ToolExecutionContext {
        input = input == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(input));
    }

    public static ToolExecutionContext of(String cluster, ToolDefinition definition, Map<String, Object> input) {
        return new ToolExecutionContext(cluster, definition, input, null);
    }

    public static ToolExecutionContext of(
            String cluster,
            ToolDefinition definition,
            Map<String, Object> input,
            String principal) {
        return new ToolExecutionContext(cluster, definition, input, principal);
    }

    public String operationType() {
        return definition.cli().verb().replace('-', '_').toUpperCase(Locale.ROOT) + "_" + resourceType();
    }

    public String resourceType() {
        return definition.cli().resource().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    /**
     * Returns business fields without changing their values.
     */
    public Map<String, Object> businessInput() {
        if (input.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> business = new LinkedHashMap<>(input);
        CONTROL_FIELDS.forEach(business::remove);
        return Collections.unmodifiableMap(business);
    }

    /**
     * Converts business fields into the tool's input record.
     */
    @SuppressWarnings("unchecked")
    public <I> I convertInput(Class<I> inputType) {
        Map<String, Object> business = businessInput();
        if (inputType == Map.class) {
            return (I) business;
        }
        return INPUT_MAPPER.convertValue(business, inputType);
    }

    /**
     * Resolves the {@code dry_run} control field from the raw input.
     */
    public boolean dryRun() {
        return booleanInput("dry_run");
    }

    public boolean breakGlass() {
        return booleanInput("break_glass");
    }

    public String reason() {
        return input.get("reason") instanceof String text ? text : null;
    }

    public String confirmToken() {
        return input.get("confirm_token") instanceof String text ? text : null;
    }

    private boolean booleanInput(String field) {
        Object value = input.get(field);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value instanceof String text && Boolean.parseBoolean(text);
    }

    /**
     * Returns the cluster name, or throws when it is missing or blank.
     */
    public String requireCluster() {
        if (cluster == null || cluster.isBlank()) {
            throw ToolError.TOOL_CLUSTER_REQUIRED.exception();
        }
        return cluster;
    }
}
