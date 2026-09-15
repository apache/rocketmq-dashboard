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

package org.apache.rocketmq.dashboard.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.dashboard.model.MessageSchemaReport;
import org.apache.rocketmq.dashboard.service.MessageSchemaRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MessageSchemaRegistryServiceImpl implements MessageSchemaRegistryService {

    private static final Logger log = LoggerFactory.getLogger(MessageSchemaRegistryServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, MessageSchemaReport> registry = new ConcurrentHashMap<>();

    @Override
    public MessageSchemaReport getSchemaReport(String topic) {
        if (StringUtils.isBlank(topic)) {
            topic = "DEFAULT_TOPIC";
        }
        final String targetTopic = topic;
        return registry.computeIfAbsent(topic, k -> buildInitialSchema(targetTopic));
    }

    @Override
    public MessageSchemaReport testSchemaEvolution(String topic, String newSchemaDefinition, String compatibilityMode) {
        MessageSchemaReport report = getSchemaReport(topic);
        if (StringUtils.isNotBlank(compatibilityMode)) {
            report.setCompatibilityMode(compatibilityMode);
        }

        List<String> diffs = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        boolean compatible = true;

        if (StringUtils.isBlank(newSchemaDefinition)) {
            errors.add("New schema definition must not be empty.");
            report.setEvolutionCompatible(false);
            report.setValidationErrors(errors);
            return report;
        }

        try {
            JsonNode newJson = MAPPER.readTree(newSchemaDefinition);
            JsonNode currentJson = MAPPER.readTree(report.getCurrentSchemaDefinition());

            if (!newJson.has("properties")) {
                diffs.add("Schema structure modified: missing 'properties' object.");
            }

            if ("BACKWARD".equalsIgnoreCase(report.getCompatibilityMode()) || "FULL".equalsIgnoreCase(report.getCompatibilityMode())) {
                if (currentJson.has("required") && newJson.has("required")) {
                    for (JsonNode reqField : newJson.get("required")) {
                        boolean wasRequired = false;
                        for (JsonNode oldReq : currentJson.get("required")) {
                            if (oldReq.asText().equals(reqField.asText())) {
                                wasRequired = true;
                                break;
                            }
                        }
                        if (!wasRequired) {
                            diffs.add(String.format("Added required field [%s] without default, breaking backward compatibility.", reqField.asText()));
                            compatible = false;
                        }
                    }
                }
            }

            if (compatible) {
                diffs.add("Field schema evolution adheres to " + report.getCompatibilityMode() + " compatibility constraints.");
            }
        } catch (Exception e) {
            log.warn("Invalid schema JSON definition: {}", e.getMessage());
            errors.add("JSON Syntax Error: " + e.getMessage());
            compatible = false;
        }

        report.setEvolutionCompatible(compatible);
        report.setCompatibilityDiffs(diffs);
        report.setValidationErrors(errors);

        return report;
    }

    @Override
    public boolean validatePayload(String topic, String jsonPayload) {
        if (StringUtils.isBlank(jsonPayload)) {
            return false;
        }
        try {
            MAPPER.readTree(jsonPayload);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private MessageSchemaReport buildInitialSchema(String topic) {
        MessageSchemaReport report = new MessageSchemaReport();
        report.setTopic(topic);
        report.setSchemaName(topic + "-schema");
        report.setSchemaType("JSON_SCHEMA");
        report.setCurrentVersion(2);
        report.setCompatibilityMode("BACKWARD");
        report.setEvolutionCompatible(true);

        String sampleSchemaV2 = "{\n" +
            "  \"$schema\": \"http://json-schema.org/draft-07/schema#\",\n" +
            "  \"type\": \"object\",\n" +
            "  \"properties\": {\n" +
            "    \"orderId\": { \"type\": \"string\" },\n" +
            "    \"amount\": { \"type\": \"number\" },\n" +
            "    \"timestamp\": { \"type\": \"integer\" }\n" +
            "  },\n" +
            "  \"required\": [\"orderId\", \"amount\"]\n" +
            "}";
        report.setCurrentSchemaDefinition(sampleSchemaV2);

        List<MessageSchemaReport.SchemaVersionItem> history = new ArrayList<>();
        history.add(new MessageSchemaReport.SchemaVersionItem(
            2, "JSON_SCHEMA", System.currentTimeMillis() - 86400000L, "system", "Added timestamp property", sampleSchemaV2));
        history.add(new MessageSchemaReport.SchemaVersionItem(
            1, "JSON_SCHEMA", System.currentTimeMillis() - 86400000L * 7, "admin", "Initial schema baseline", "{\n  \"type\": \"object\"\n}"));
        report.setVersionHistory(history);

        List<String> diffs = new ArrayList<>();
        diffs.add("Base schema initialized. Backward compatibility verified.");
        report.setCompatibilityDiffs(diffs);

        return report;
    }
}
