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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import lombok.Getter;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ToolCatalog {

    static final String MANIFEST_RESOURCE = "classpath:tool-catalog/manifest.yaml";
    static final String SHARD_PATTERN = "classpath*:tool-catalog/tools/*.yaml";
    static final String SCHEMA_RESOURCE = "classpath:tool-catalog/rmq-tools.schema.json";

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @Getter
    private final String version;
    private final List<ToolDefinition> definitions;
    private final Map<String, ToolDefinition> definitionsByName;

    @Autowired
    public ToolCatalog(ResourceLoader resourceLoader) {
        ToolCatalog loaded = load(resourceLoader);
        this.version = loaded.version;
        this.definitions = loaded.definitions;
        this.definitionsByName = loaded.definitionsByName;
    }

    private ToolCatalog(
            String version,
            List<ToolDefinition> definitions,
            Map<String, ToolDefinition> definitionsByName) {
        this.version = version;
        this.definitions = definitions;
        this.definitionsByName = definitionsByName;
    }

    static ToolCatalog load(ResourceLoader resourceLoader) {
        try {
            Resource manifestResource = resourceLoader.getResource(MANIFEST_RESOURCE);
            Resource schemaResource = resourceLoader.getResource(SCHEMA_RESOURCE);
            byte[] manifestBytes = manifestResource.getContentAsByteArray();
            ManifestDocument manifest = YAML_MAPPER.readValue(manifestBytes, ManifestDocument.class);

            ResourcePatternResolver resourcePatternResolver =
                    ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
            Resource[] shards = resourcePatternResolver.getResources(SHARD_PATTERN);
            List<Resource> shardResources = Arrays.stream(shards)
                    .sorted(Comparator.comparing(Resource::getFilename, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .toList();
            if (shardResources.isEmpty()) {
                throw new IllegalStateException("Tool catalog contains no shards");
            }

            String schemaJson = schemaResource.getContentAsString(StandardCharsets.UTF_8);
            SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                    SpecificationVersion.DRAFT_2020_12);
            Schema schema = registry.getSchema(schemaJson, InputFormat.JSON);

            List<ToolDefinition> allTools = new ArrayList<>();
            for (Resource shard : shardResources) {
                byte[] shardBytes = shard.getContentAsByteArray();
                String shardYaml = new String(shardBytes, StandardCharsets.UTF_8);
                List<Error> shardErrors = new ArrayList<>(
                        schema.validate(shardYaml, InputFormat.YAML));
                if (!shardErrors.isEmpty()) {
                    shardErrors.sort(Comparator.comparing(
                            error -> error.getInstanceLocation().toString()));
                    throw new IllegalStateException(
                            "Tool catalog shard validation failed for " + shard.getFilename()
                                    + ": " + shardErrors);
                }
                ShardDocument shardDoc = YAML_MAPPER.readValue(shardBytes, ShardDocument.class);
                if (!manifest.version().equals(shardDoc.version())) {
                    throw new IllegalStateException(
                            "Tool catalog shard " + shard.getFilename()
                                    + " version mismatch: expected " + manifest.version()
                                    + ", got " + shardDoc.version());
                }
                allTools.addAll(shardDoc.tools());
            }

            CatalogDocument document = new CatalogDocument(manifest.version(), allTools);
            return validatedCatalog(document);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load RocketMQ tool catalog", e);
        }
    }

    private static ToolCatalog validatedCatalog(CatalogDocument document) {
        Map<String, ToolDefinition> byName = new LinkedHashMap<>();
        for (ToolDefinition definition : document.tools()) {
            if (byName.putIfAbsent(definition.name(), definition) != null) {
                throw new IllegalStateException(
                        "Tool catalog contains duplicate tool name: " + definition.name());
            }
            validateClusterConvention(definition);
        }

        List<ToolDefinition> immutableDefinitions = List.copyOf(byName.values());
        return new ToolCatalog(
                document.version(),
                immutableDefinitions,
                Map.copyOf(byName));
    }

    private static void validateClusterConvention(ToolDefinition definition) {
        Object required = definition.inputSchema().get("required");
        if (!(required instanceof List<?> requiredFields) || !requiredFields.contains("cluster")) {
            throw new IllegalStateException(
                    "Tool must require cluster: " + definition.name());
        }
    }

    public List<ToolDefinition> list() {
        return definitions;
    }

    public Optional<ToolDefinition> find(String name) {
        return Optional.ofNullable(definitionsByName.get(name));
    }

    public ToolDefinition getDefinition(String name) {
        if (name == null || name.isBlank()) {
            throw ToolError.TOOL_NAME_REQUIRED.exception();
        }
        ToolDefinition definition = definitionsByName.get(name);
        if (definition == null) {
            throw ToolError.TOOL_NOT_FOUND.exception(name);
        }
        return definition;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ManifestDocument(String version) {
    }

    private record ShardDocument(
            String version,
            List<ToolDefinition> tools) {
    }

    private record CatalogDocument(
            String version,
            List<ToolDefinition> tools) {
    }
}
