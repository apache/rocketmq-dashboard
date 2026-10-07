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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins {@link ToolSchemaValidator} against a purpose-built one-tool catalog: the input rejection
 * contract, the output rejection contract, the Jackson 2 normalization that makes output
 * validation observe the wire names, and the fail-fast on a catalog schema that is not valid
 * JSON Schema.
 */
class ToolSchemaValidatorTest {

    private static final String TOOL = "rmq.test.echo";

    /**
     * A DTO whose wire name is produced by the application mapper's naming strategy, not by its
     * component name: {@code physicalCluster} becomes {@code physical_cluster}.
     */
    public record ClusterRow(String physicalCluster) {
    }

    private static Resource shard(String inputSchema) {
        return utf8Resource("""
                version: 1.0.0
                tools:
                  - name: rmq.test.echo
                    cli:
                      resource: test
                      verb: echo
                    description: A tool defined for ToolSchemaValidatorTest.
                    riskLevel: L1
                    permission: test:read
                    requiredCapabilities:
                      - CLUSTER_TOPOLOGY
                    viewHint: object
                    deprecated: false
                    inputSchema:
                %s
                    outputSchema:
                      type: object
                      required:
                        - items
                      additionalProperties: false
                      properties:
                        items:
                          type: array
                          items:
                            type: object
                            required:
                              - physical_cluster
                            additionalProperties: false
                            properties:
                              physical_cluster:
                                type: string
                                minLength: 1
                """.formatted(inputSchema));
    }

    /** Indented to nest under the shard's {@code inputSchema:} line. */
    private static String inputSchema(String relativeYaml) {
        return relativeYaml.lines()
                .map(line -> "      " + line)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static final String VALID_INPUT_SCHEMA = inputSchema(String.join("\n",
            "type: object",
            "required:",
            "  - instanceId",
            "additionalProperties: false",
            "properties:",
            "  instanceId:",
            "    type: string",
            "    minLength: 1",
            "  count:",
            "    type: integer"));

    /** {@code minProperties} must be an integer in JSON Schema; this one is a plain string. */
    private static final String META_INVALID_INPUT_SCHEMA = inputSchema(String.join("\n",
            "type: object",
            "required:",
            "  - instanceId",
            "minProperties: three",
            "properties:",
            "  instanceId:",
            "    type: string"));

    private static Resource utf8Resource(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }

    private static ToolCatalog loadCatalog(Resource shard) {
        Resource manifest = utf8Resource("version: 1.0.0\n");
        return new ToolCatalog(new PathMatchingResourcePatternResolver() {
            @Override
            public Resource getResource(String location) {
                return "classpath:tool-catalog/manifest.yaml".equals(location) ? manifest
                        : super.getResource(location);
            }

            @Override
            public Resource[] getResources(String locationPattern) throws IOException {
                return "classpath*:tool-catalog/tools/*.yaml".equals(locationPattern)
                        ? new Resource[] {shard}
                        : super.getResources(locationPattern);
            }
        });
    }

    private final ToolCatalog catalog = loadCatalog(shard(VALID_INPUT_SCHEMA));
    /** The application's mapper, configured the way the real converter is: SNAKE_CASE wire names. */
    private final ObjectMapper appMapper =
            new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final ToolSchemaValidator validator = new ToolSchemaValidator(
            catalog, appMapper, JsonMapper.builder().build());

    private ToolDefinition definition() {
        return catalog.getDefinition(TOOL);
    }

    @Test
    void acceptsInputThatSatisfiesTheSchema() {
        assertThatCode(() -> validator.validateInput(definition(),
                Map.of("instanceId", "instance-a", "count", 2))).doesNotThrowAnyException();
    }

    @Test
    void rejectsInputThatMissesARequiredArgument() {
        assertThatThrownBy(() -> validator.validateInput(definition(), Map.of("count", 2)))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Tool input validation failed for " + TOOL)
                .extracting("code").isEqualTo(400);
    }

    @Test
    void rejectsInputThatAddsAnUnknownArgument() {
        assertThatThrownBy(() -> validator.validateInput(definition(),
                Map.of("instanceId", "instance-a", "unexpected", true)))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Tool input validation failed for " + TOOL);
    }

    @Test
    void rejectsInputWithAWronglyTypedArgument() {
        assertThatThrownBy(() -> validator.validateInput(definition(),
                Map.of("instanceId", "instance-a", "count", "two")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Tool input validation failed for " + TOOL);
    }

    @Test
    void acceptsOutputThatSatisfiesTheSchema() {
        assertThatCode(() -> validator.validateOutput(definition(),
                Map.of("items", List.of(Map.of("physical_cluster", "cluster-a")))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsOutputThatViolatesTheSchema() {
        assertThatThrownBy(() -> validator.validateOutput(definition(),
                Map.of("items", List.of(Map.of("physical_cluster", "")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tool output validation failed for " + TOOL);
    }

    /**
     * Tool DTOs carry their wire names through the application's configured Jackson 2 converter -
     * here a SNAKE_CASE naming strategy - while schema validation runs on a bare Jackson 3 mapper
     * that knows nothing of it. The validator therefore normalizes through the application mapper
     * first, so validation observes the same names the HTTP and MCP responses use: the component
     * {@code physicalCluster} reaches the schema as {@code physical_cluster}. Skipping that dance
     * would fail every DTO whose component names differ from its wire names.
     */
    @Test
    void normalizesTheOutputThroughTheApplicationsJacksonFirst() {
        assertThatCode(() -> validator.validateOutput(definition(),
                Map.of("items", List.of(new ClusterRow("cluster-a")))))
                .doesNotThrowAnyException();
    }

    /**
     * A catalog schema that is not valid JSON Schema must fail at construction, not at the first
     * tool call. The catalog's own schema only checks that inputSchema is an object, so the
     * meta-schema check is the only gate: {@code minProperties: three} is a string where JSON
     * Schema requires an integer.
     */
    @Test
    void aCatalogSchemaThatIsNotValidJsonSchemaFailsAtStartup() {
        ToolCatalog invalidCatalog = loadCatalog(shard(META_INVALID_INPUT_SCHEMA));

        assertThatThrownBy(() -> new ToolSchemaValidator(
                invalidCatalog, new ObjectMapper(), JsonMapper.builder().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tool input schema is invalid for " + TOOL);
    }

    @Test
    void theCatalogExposesTheDefinitionUnderTest() {
        assertThat(definition()).isNotNull();
        assertThat(definition().name()).isEqualTo(TOOL);
    }
}
