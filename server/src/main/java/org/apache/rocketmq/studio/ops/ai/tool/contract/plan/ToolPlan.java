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
package org.apache.rocketmq.studio.ops.ai.tool.contract.plan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ToolPlan(
        String summary,
        List<String> impact,
        Map<String, Object> before,
        Map<String, Object> after,
        List<String> warnings) {

    private static final ObjectMapper STATE_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ToolPlan {
        impact = List.copyOf(impact);
        before = immutableState(before);
        after = immutableState(after);
        warnings = List.copyOf(warnings);
    }

    public ToolPlan withWarning(String warning) {
        List<String> updated = new ArrayList<>(warnings);
        updated.add(warning);
        return new ToolPlan(summary, impact, before, after, updated);
    }

    public <T> T before(Class<T> stateType) {
        return STATE_MAPPER.convertValue(before, stateType);
    }

    public <T> T after(Class<T> stateType) {
        return STATE_MAPPER.convertValue(after, stateType);
    }

    public static Builder builder(String summary) {
        return new Builder(summary);
    }

    private static <K> Map<K, Object> immutableState(Map<K, ?> state) {
        Map<K, Object> snapshot = new LinkedHashMap<>();
        state.forEach((key, value) -> snapshot.put(key, immutableValue(value)));
        return Collections.unmodifiableMap(snapshot);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return immutableState(map);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(ToolPlan::immutableValue).toList();
        }
        return value;
    }

    private static Map<String, Object> objectState(Object value) {
        if (value == null) {
            return Map.of();
        }
        Map<String, Object> state = STATE_MAPPER.convertValue(
                value, new TypeReference<LinkedHashMap<String, Object>>() { });
        state.values().removeIf(Objects::isNull);
        return state;
    }

    public static final class Builder {

        private final String summary;
        private Map<String, Object> before = Map.of();
        private Map<String, Object> after = Map.of();
        private final List<String> impact = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        private Builder(String summary) {
            this.summary = summary;
        }

        public Builder before(Object value) {
            before = objectState(value);
            return this;
        }

        public Builder after(Object value) {
            after = objectState(value);
            return this;
        }

        public Builder impact(String value) {
            impact.add(value);
            return this;
        }

        public Builder warning(String value) {
            warnings.add(value);
            return this;
        }

        public Builder warnings(List<String> values) {
            warnings.addAll(values);
            return this;
        }

        public Builder warningIf(boolean condition, String value) {
            return condition ? warning(value) : this;
        }

        public ToolPlan build() {
            return new ToolPlan(summary, impact, before, after, warnings);
        }
    }
}
