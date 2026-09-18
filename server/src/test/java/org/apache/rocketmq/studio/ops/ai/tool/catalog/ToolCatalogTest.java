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

import org.apache.rocketmq.studio.common.config.LegacyJackson2Config;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolRiskLevel;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolSchemaValidator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

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
    @SuppressWarnings("unchecked")
    void messageQueriesAdvertiseNestedPagingAndOptionalBodiesTest() {
        ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());
        for (String toolName : new String[]{"rmq.message.query", "rmq.message.query_by_topic"}) {
            ToolDefinition definition = catalog.getDefinition(toolName);
            Map<String, Object> inputProperties =
                    (Map<String, Object>) definition.inputSchema().get("properties");
            Map<String, Object> pageSchema = (Map<String, Object>) inputProperties.get("page");
            Map<String, Object> pageProperties =
                    (Map<String, Object>) pageSchema.get("properties");
            Map<String, Object> outputProperties =
                    (Map<String, Object>) definition.outputSchema().get("properties");

            assertThat(inputProperties).containsKeys("page", "includeBody").doesNotContainKey("pageSize");
            assertThat(pageProperties).containsKeys("page", "pageSize");
            assertThat((Map<String, Object>) pageProperties.get("pageSize"))
                    .containsEntry("maximum", 100);
            assertThat(outputProperties)
                    .containsKeys("items", "total", "page", "pageSize", "resultMayBeTruncated")
                    .doesNotContainKey("size");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void messageTraceAdvertisesOptionalCustomTraceTopicNameTest() {
        Map<String, Object> inputSchema = new ToolCatalog(new DefaultResourceLoader())
                .getDefinition("rmq.message.trace").inputSchema();
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");

        assertThat(properties).containsKey("traceTopicName").doesNotContainKey("traceTopic");
        assertThat((Map<String, Object>) properties.get("traceTopicName"))
                .containsEntry("type", "string");
        assertThat((List<String>) inputSchema.get("required"))
                .doesNotContain("traceTopicName");
    }

    @Test
    void rejectsInvalidNestedMessageQueryPagingThroughTheRuntimeSchemaTest() {
        ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());
        ToolSchemaValidator validator = new ToolSchemaValidator(catalog,
                new LegacyJackson2Config().jackson2ObjectMapper(), JsonMapper.builder().build());
        for (String toolName : new String[]{"rmq.message.query", "rmq.message.query_by_topic"}) {
            ToolDefinition definition = catalog.getDefinition(toolName);
            for (Map<String, Object> invalid : List.<Map<String, Object>>of(
                    Map.of("instanceId", "instance-a", "topicName", "TopicA",
                            "page", Map.of("page", 0)),
                    Map.of("instanceId", "instance-a", "topicName", "TopicA",
                            "page", Map.of("pageSize", 101)),
                    Map.of("instanceId", "instance-a", "topicName", "TopicA", "pageSize", 10))) {
                assertThatThrownBy(() -> validator.validateInput(definition, invalid))
                        .isInstanceOfSatisfying(ToolExecutionException.class,
                                error -> assertThat(error.getCode()).isEqualTo(400));
            }
        }
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
    void requiresInstanceIdForEveryNonExemptTool() {
        Resource missingInstanceId = catalog(tool("rmq.topic.list", false));

        assertThatThrownBy(() -> ToolCatalogTestSupport.loadCatalog("1.0.0", missingInstanceId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must require instanceId");
    }

    @Test
    void exemptsPlatformLevelToolsFromTheInstanceIdRequirement() {
        Resource exempt = catalog(tool("rmq.broker.list", false));

        ToolCatalog catalog = ToolCatalogTestSupport.loadCatalog("1.0.0", exempt);

        assertThat(catalog.getDefinition("rmq.broker.list").inputSchema())
                .doesNotContainKey("required");
    }

    private static Resource catalog(String... tools) {
        return utf8Resource("""
                version: 1.0.0
                tools:
                %s
                """.formatted(String.join("\n", tools)));
    }

    private static String tool(String name, boolean instanceIdRequired) {
        String required = instanceIdRequired ? """
                required:
                  - instanceId
                properties:
                  instanceId:
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
