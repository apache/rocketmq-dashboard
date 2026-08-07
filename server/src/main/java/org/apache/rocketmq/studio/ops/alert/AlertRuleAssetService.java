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
package org.apache.rocketmq.studio.ops.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads the Prometheus alert rule YAML assets bundled under
 * {@code classpath*:alerts/*.yaml} and exposes them for listing, viewing,
 * exporting and as the default alert rule set.
 */
@Slf4j
@Service
public class AlertRuleAssetService {

    private static final String LOCATION_PATTERN = "classpath*:alerts/*.yaml";

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    /**
     * Lists metadata for every bundled alert rule asset.
     */
    public List<AlertRuleAssetInfo> listAssets() {
        List<AlertRuleAssetInfo> infos = new ArrayList<>();
        for (Resource resource : resolveResources()) {
            String name = nameOf(resource);
            if (name == null) {
                continue;
            }
            try (InputStream in = resource.getInputStream()) {
                JsonNode root = yamlMapper.readTree(in);
                if (root == null || !root.isObject()) {
                    log.warn("Skipping invalid alert rule asset {}: expected a YAML object", resource);
                    continue;
                }
                List<PrometheusAlertRule> rules = parseRules(root);
                Set<String> severities = new LinkedHashSet<>();
                String group = rules.isEmpty() ? "" : rules.get(0).group();
                rules.forEach(rule -> severities.add(rule.severity()));
                infos.add(new AlertRuleAssetInfo(name, group, rules.size(), new ArrayList<>(severities)));
            } catch (IOException e) {
                log.warn("Skipping unreadable alert rule asset {}: {}", resource, e.getMessage());
            }
        }
        infos.sort((a, b) -> a.name().compareTo(b.name()));
        return infos;
    }

    /**
     * Returns the raw YAML content for the given asset name.
     *
     * @throws BusinessException with code 404 when the asset is unknown
     */
    public String getAssetYaml(String name) {
        Resource resource = findResource(name);
        if (resource == null) {
            throw new BusinessException(404, "Alert rule asset not found: " + name);
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(500, "Failed to read alert rule asset: " + name);
        }
    }

    /**
     * Loads every rule from every bundled asset as the default alert set.
     */
    public List<PrometheusAlertRule> loadDefaultRules() {
        List<PrometheusAlertRule> rules = new ArrayList<>();
        for (Resource resource : resolveResources()) {
            if (nameOf(resource) == null) {
                continue;
            }
            try (InputStream in = resource.getInputStream()) {
                rules.addAll(parseRules(yamlMapper.readTree(in)));
            } catch (IOException e) {
                log.warn("Skipping unreadable alert rule asset {}: {}", resource, e.getMessage());
            }
        }
        return rules;
    }

    private List<PrometheusAlertRule> parseRules(JsonNode root) {
        if (root == null || !root.isObject()) {
            return List.of();
        }
        List<PrometheusAlertRule> rules = new ArrayList<>();
        JsonNode groups = root.get("groups");
        if (groups == null || !groups.isArray()) {
            return rules;
        }
        for (JsonNode group : groups) {
            String groupName = textOr(group, "name", "");
            JsonNode ruleNodes = group.get("rules");
            if (ruleNodes == null || !ruleNodes.isArray()) {
                continue;
            }
            for (JsonNode rule : ruleNodes) {
                if (!rule.isObject()) {
                    continue;
                }
                String alert = textOr(rule, "alert", "");
                String expr = textOr(rule, "expr", "");
                String duration = textOr(rule, "for", "5m");
                String severity = textOr(labelsOf(rule), "severity", "warning");
                String team = textOr(labelsOf(rule), "team", "broker");
                String summary = textOr(annotationsOf(rule), "summary", alert);
                String description = textOr(annotationsOf(rule), "description", "");
                rules.add(new PrometheusAlertRule(groupName, alert, expr, duration, severity, team, summary, description));
            }
        }
        return rules;
    }

    private Resource findResource(String name) {
        for (Resource resource : resolveResources()) {
            if (name.equals(nameOf(resource))) {
                return resource;
            }
        }
        return null;
    }

    protected Resource[] resolveResources() {
        try {
            return resourceResolver.getResources(LOCATION_PATTERN);
        } catch (IOException e) {
            log.warn("Unable to resolve alert rule assets: {}", e.getMessage());
            return new Resource[0];
        }
    }

    private static String nameOf(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null || !filename.endsWith(".yaml")) {
            return null;
        }
        return filename.substring(0, filename.length() - ".yaml".length());
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : fallback;
    }

    private static JsonNode labelsOf(JsonNode rule) {
        JsonNode labels = rule.get("labels");
        return labels != null && labels.isObject() ? labels : rule;
    }

    private static JsonNode annotationsOf(JsonNode rule) {
        JsonNode annotations = rule.get("annotations");
        return annotations != null && annotations.isObject() ? annotations : rule;
    }
}
