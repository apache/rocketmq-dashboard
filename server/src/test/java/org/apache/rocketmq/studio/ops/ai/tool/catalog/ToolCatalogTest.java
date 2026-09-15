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
package org.apache.rocketmq.studio.ops.ai.tool.catalog;

import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolRiskLevel;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCatalogTest {

    @Test
    void loadsAndIndexesTheCanonicalCatalog() {
        ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());

        assertThat(catalog.getVersion()).isNotBlank();
        assertThat(catalog.list()).isNotEmpty();
        for (ToolDefinition definition : catalog.list()) {
            assertThat(catalog.getDefinition(definition.name())).isSameAs(definition);
            assertThat(catalog.find(definition.name())).contains(definition);
        }
        ToolDefinition clusterList = catalog.getDefinition("rmq.cluster.list");
        assertThat(clusterList.riskLevel()).isEqualTo(ToolRiskLevel.L1);
        assertThat(catalog.find("rmq.unknown")).isEmpty();
    }

    @Test
    void rejectsCatalogThatDoesNotMatchItsJsonSchema() {
        Resource invalid = utf8Resource("""
                version: 1.0.0
                tools: not-a-list
                """);

        assertThatThrownBy(() -> ToolCatalogTestSupport.loadCatalog("1.0.0", invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("catalog shard validation failed");
    }

    @Test
    void rejectsDuplicateToolNamesAcrossShards() {
        Resource duplicate = catalog(tool("rmq.cluster.list", true));

        assertThatThrownBy(() -> ToolCatalogTestSupport.loadCatalog("1.0.0", duplicate, duplicate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate tool name");
    }

    @Test
    void requiresClusterForEveryToolIncludingClusterList() {
        Resource missingCluster = catalog(tool("rmq.cluster.list", false));

        assertThatThrownBy(() -> ToolCatalogTestSupport.loadCatalog("1.0.0", missingCluster))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must require cluster");
    }

    private static Resource catalog(String... tools) {
        return utf8Resource("""
                version: 1.0.0
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
                """.indent(6) : "";
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
