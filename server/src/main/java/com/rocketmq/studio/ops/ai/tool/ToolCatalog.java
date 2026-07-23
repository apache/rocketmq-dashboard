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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ToolCatalog {

    static final String CATALOG_RESOURCE = "classpath:tool-catalog/rmq-tools.yaml";
    static final String SCHEMA_RESOURCE = "classpath:tool-catalog/rmq-tools.schema.json";

    private static final String CLUSTER_LIST_TOOL = "rmq.cluster.list";
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private final String version;
    private final String minimumClientVersion;
    private final String digest;
    private final List<ToolDefinition> definitions;
    private final Map<String, ToolDefinition> definitionsByName;

    public ToolCatalog(ResourceLoader resourceLoader) {
        ToolCatalog loaded = load(
                resourceLoader.getResource(CATALOG_RESOURCE),
                resourceLoader.getResource(SCHEMA_RESOURCE));
        this.version = loaded.version;
        this.minimumClientVersion = loaded.minimumClientVersion;
        this.digest = loaded.digest;
        this.definitions = loaded.definitions;
        this.definitionsByName = loaded.definitionsByName;
    }

    private ToolCatalog(
            String version,
            String minimumClientVersion,
            String digest,
            List<ToolDefinition> definitions,
            Map<String, ToolDefinition> definitionsByName) {
        this.version = version;
        this.minimumClientVersion = minimumClientVersion;
        this.digest = digest;
        this.definitions = definitions;
        this.definitionsByName = definitionsByName;
    }

    static ToolCatalog load(Resource catalogResource, Resource schemaResource) {
        try {
            byte[] catalogBytes = catalogResource.getInputStream().readAllBytes();
            String catalogYaml = new String(catalogBytes, StandardCharsets.UTF_8);
            String schemaJson = schemaResource.getContentAsString(StandardCharsets.UTF_8);

            SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                    SpecificationVersion.DRAFT_2020_12);
            Schema schema = registry.getSchema(schemaJson, InputFormat.JSON);
            List<Error> validationErrors = new ArrayList<>(
                    schema.validate(catalogYaml, InputFormat.YAML));
            if (!validationErrors.isEmpty()) {
                validationErrors.sort(Comparator.comparing(
                        error -> error.getInstanceLocation().toString()));
                throw new IllegalStateException(
                        "Tool catalog schema validation failed: " + validationErrors);
            }

            CatalogDocument document = YAML_MAPPER.readValue(catalogBytes, CatalogDocument.class);
            return validatedCatalog(document, sha256(catalogBytes));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load RocketMQ tool catalog", e);
        }
    }

    private static ToolCatalog validatedCatalog(CatalogDocument document, String digest) {
        int catalogMajor = majorVersion(document.version());
        int minimumClientMajor = majorVersion(document.minimumClientVersion());
        if (catalogMajor != minimumClientMajor) {
            throw new IllegalStateException(
                    "Catalog and minimum client major versions must match");
        }

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
                document.minimumClientVersion(),
                digest,
                immutableDefinitions,
                Map.copyOf(byName));
    }

    private static void validateClusterConvention(ToolDefinition definition) {
        if (CLUSTER_LIST_TOOL.equals(definition.name())) {
            return;
        }
        Object required = definition.inputSchema().get("required");
        if (!(required instanceof List<?> requiredFields) || !requiredFields.contains("cluster")) {
            throw new IllegalStateException(
                    "Remote tool must require cluster: " + definition.name());
        }
    }

    private static int majorVersion(String version) {
        return Integer.parseInt(version.substring(0, version.indexOf('.')));
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public String getVersion() {
        return version;
    }

    public String getMinimumClientVersion() {
        return minimumClientVersion;
    }

    public String getDigest() {
        return digest;
    }

    public List<ToolDefinition> list() {
        return definitions;
    }

    public Optional<ToolDefinition> find(String name) {
        return Optional.ofNullable(definitionsByName.get(name));
    }

    private record CatalogDocument(
            String version,
            String minimumClientVersion,
            List<ToolDefinition> tools) {
    }
}
