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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record ToolExecutionContext(
        String instanceId,
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

    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigDecimal DECIMAL_LONG_MIN = new BigDecimal(Long.MIN_VALUE);
    private static final BigDecimal DECIMAL_LONG_MAX = new BigDecimal(Long.MAX_VALUE);

    public ToolExecutionContext {
        input = input == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(input));
    }

    public static ToolExecutionContext of(String instanceId, ToolDefinition definition, Map<String, Object> input) {
        return new ToolExecutionContext(instanceId, definition, input, null);
    }

    public static ToolExecutionContext of(
            String instanceId,
            ToolDefinition definition,
            Map<String, Object> input,
            String principal) {
        return new ToolExecutionContext(instanceId, definition, input, principal);
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
     * Converts business fields into the tool's input record. The input schema declares every
     * numeric argument as an integer, which JSON Schema accepts for an integral value of any
     * magnitude, so an argument may still not fit the {@code long}/{@code int} component it binds
     * to. That is a caller error and is reported as one instead of failing as an internal error.
     */
    @SuppressWarnings("unchecked")
    public <I> I convertInput(Class<I> inputType) {
        Map<String, Object> business = businessInput();
        if (inputType == Map.class) {
            return (I) business;
        }
        requireBindableNumbers(business, "");
        try {
            return INPUT_MAPPER.convertValue(business, inputType);
        } catch (IllegalArgumentException exception) {
            throw ToolError.REQUEST_PARAMETER_INVALID.exception(exception.getMessage());
        }
    }

    /** Rejects a numeric argument that is too large for any {@code long}/{@code int} component. */
    private static void requireBindableNumbers(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> requireBindableNumbers(
                    nested, path.isEmpty() ? String.valueOf(key) : path + "." + key));
            return;
        }
        if (value instanceof Iterable<?> items) {
            items.forEach(item -> requireBindableNumbers(item, path));
            return;
        }
        if (value instanceof Number number && !withinLongRange(number)) {
            throw ToolError.REQUEST_PARAMETER_INVALID.exception(path);
        }
    }

    /** The range Jackson enforces when coercing a JSON number into a long component. */
    private static boolean withinLongRange(Number number) {
        if (number instanceof BigInteger integer) {
            return integer.compareTo(LONG_MIN) >= 0 && integer.compareTo(LONG_MAX) <= 0;
        }
        if (number instanceof BigDecimal decimal) {
            return decimal.compareTo(DECIMAL_LONG_MIN) >= 0
                    && decimal.compareTo(DECIMAL_LONG_MAX) <= 0;
        }
        if (number instanceof Double || number instanceof Float) {
            double primitive = number.doubleValue();
            return primitive >= (double) Long.MIN_VALUE && primitive <= (double) Long.MAX_VALUE;
        }
        return true;
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
     * Returns the instance identifier, or throws when it is missing or blank.
     */
    public String requireInstance() {
        if (instanceId == null || instanceId.isBlank()) {
            throw ToolError.TOOL_INSTANCE_REQUIRED.exception(definition.name());
        }
        return instanceId;
    }
}
