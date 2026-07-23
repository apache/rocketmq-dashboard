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
package com.rocketmq.studio.ops.ai.tool;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCatalogTest {

    @Test
    void loadsAndIndexesTheCanonicalCatalog() {
        ToolCatalog catalog = ToolCatalog.load(canonicalCatalog(), canonicalSchema());

        assertThat(catalog.getVersion()).isEqualTo("1.0.0");
        assertThat(catalog.getMinimumClientVersion()).isEqualTo("1.0.0");
        assertThat(catalog.getDigest()).matches("[0-9a-f]{64}");
        assertThat(catalog.list()).extracting(ToolDefinition::getName)
                .containsExactly("rmq.cluster.list", "rmq.capabilities");
        assertThat(catalog.find("rmq.cluster.list")).isPresent();
        assertThat(catalog.find("rmq.unknown")).isEmpty();
    }

    @Test
    void rejectsCatalogThatDoesNotMatchItsJsonSchema() {
        Resource invalid = utf8Resource("""
                version: 1.0.0
                minimumClientVersion: 1.0.0
                tools: not-a-list
                """);

        assertThatThrownBy(() -> ToolCatalog.load(invalid, canonicalSchema()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("catalog schema validation failed");
    }

    @Test
    void rejectsDuplicateToolNames() {
        Resource duplicate = catalog(
                tool("rmq.cluster.list", false),
                tool("rmq.cluster.list", false));

        assertThatThrownBy(() -> ToolCatalog.load(duplicate, canonicalSchema()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate tool name");
    }

    @Test
    void requiresClusterForEveryRemoteToolExceptClusterList() {
        Resource missingCluster = catalog(tool("rmq.capabilities", false));

        assertThatThrownBy(() -> ToolCatalog.load(missingCluster, canonicalSchema()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must require cluster");
    }

    @Test
    void rejectsIncompatibleMinimumClientMajorVersion() {
        Resource incompatible = utf8Resource("""
                version: 2.0.0
                minimumClientVersion: 1.0.0
                tools:
                %s
                """.formatted(tool("rmq.cluster.list", false)));

        assertThatThrownBy(() -> ToolCatalog.load(incompatible, canonicalSchema()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("major versions must match");
    }

    private static Resource canonicalCatalog() {
        return new ClassPathResource("tool-catalog/rmq-tools.yaml");
    }

    private static Resource canonicalSchema() {
        return new ClassPathResource("tool-catalog/rmq-tools.schema.json");
    }

    private static Resource catalog(String... tools) {
        return utf8Resource("""
                version: 1.0.0
                minimumClientVersion: 1.0.0
                tools:
                %s
                """.formatted(String.join("\n", tools)));
    }

    private static String tool(String name, boolean clusterRequired) {
        String required = clusterRequired ? """
                    required:
                      - cluster
                    properties:
                      cluster:
                        type: string
                        minLength: 1
                """ : "";
        return """
                  - name: %s
                    cli:
                      resource: cluster
                      verb: list
                    description: Test tool.
                    riskLevel: L1
                    permission: cluster:read
                    requiredCapabilities: []
                    inputSchema:
                      type: object
                %s      additionalProperties: false
                    outputSchema:
                      type: object
                    viewHint: object
                    deprecated: false
                """.formatted(name, required);
    }

    private static Resource utf8Resource(String value) {
        return new ByteArrayResource(value.getBytes(StandardCharsets.UTF_8));
    }
}
