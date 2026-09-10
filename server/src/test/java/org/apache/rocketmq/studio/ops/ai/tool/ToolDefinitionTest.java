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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ToolDefinition}: the record defensively copies its schema and
 * capability collections at construction, rejects non-string JSON Schema keys, and derives
 * the low-risk read-only flag from risk level plus a case-insensitive parsed permission.
 */
class ToolDefinitionTest {

    private static ToolDefinition definition(String riskLevel, String permission) {
        return new ToolDefinition("tool", new ToolDefinition.Cli("topic", "list"), "desc",
                riskLevel, permission, List.of(), Map.of(), Map.of(), null, false, null);
    }

    @Test
    void copiesCapabilitiesAndSchemasDefensively() {
        List<String> capabilities = new ArrayList<>(List.of("REMOTING"));
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("type", "string");
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("properties", properties);
        List<Object> items = new ArrayList<>(List.of("a", "b"));
        Map<String, Object> outputSchema = new LinkedHashMap<>();
        outputSchema.put("items", items);

        ToolDefinition definition = new ToolDefinition("tool", new ToolDefinition.Cli("topic", "list"),
                "desc", "L1", "topic:list", capabilities, inputSchema, outputSchema, null, false, null);

        // Mutating the source collections after construction must not leak into the record.
        capabilities.add("GRPC");
        properties.put("description", "mutated");
        items.add("c");

        assertThat(definition.requiredCapabilities()).containsExactly("REMOTING");
        Map<String, Object> copiedProperties = (Map<String, Object>) definition.inputSchema().get("properties");
        assertThat(copiedProperties).doesNotContainKey("description");
        List<Object> copiedItems = (List<Object>) definition.outputSchema().get("items");
        assertThat(copiedItems).hasSize(2);

        assertThatThrownBy(() -> definition.requiredCapabilities().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> definition.inputSchema().put("k", "v"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> copiedItems.add("d"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNestedSchemaKeysThatAreNotStrings() {
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        Map<Object, Object> nested = new LinkedHashMap<>();
        nested.put(1, "not-allowed");
        inputSchema.put("properties", nested);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ToolDefinition("tool", new ToolDefinition.Cli("topic", "list"),
                        "desc", "L1", "topic:list", List.of(), inputSchema, Map.of(), null, false, null))
                .withMessage("JSON Schema keys must be strings");
    }

    @Test
    void flagsOnlyLowRiskReadOnlyToolsAsSafe() {
        assertThat(definition("L1", "topic:read").isLowRiskReadOnly()).isTrue();
        assertThat(definition("l1", "  TOPIC : READ ").isLowRiskReadOnly()).isTrue();
        assertThat(definition("L1", "topic:write").isLowRiskReadOnly()).isFalse();
        assertThat(definition("L2", "topic:read").isLowRiskReadOnly()).isFalse();
        assertThat(definition("L1", null).isLowRiskReadOnly()).isFalse();
        assertThat(definition(null, "topic:read").isLowRiskReadOnly()).isFalse();
    }

    @Test
    void parsesPermissionCaseInsensitivelyAndTrimsWhitespace() {
        ToolPermission permission = definition("L1", "  Cluster:READ ").parsedPermission();
        assertThat(permission.resource()).isEqualTo("cluster");
        assertThat(permission.action()).isEqualTo("read");
        assertThat(permission.isReadOnly()).isTrue();

        assertThat(definition("L1", "cluster:write").parsedPermission().isReadOnly()).isFalse();
        assertThat(definition("L1", null).parsedPermission()).isEqualTo(new ToolPermission("", ""));
        assertThat(definition("L1", "  ").parsedPermission()).isEqualTo(new ToolPermission("", ""));
    }
}
