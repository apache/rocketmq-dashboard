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
package org.apache.rocketmq.dashboard.skill;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Abstract base implementation of Skill with common functionality
 */
@Slf4j
public abstract class AbstractSkill implements Skill {

    @Override
    public void initialize() {
        log.debug("Initializing skill: {}", getId());
    }

    @Override
    public void destroy() {
        log.debug("Destroying skill: {}", getId());
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    /**
     * Validate required parameters before execution
     *
     * @param parameters the parameters to validate
     * @throws IllegalArgumentException if validation fails
     */
    protected void validateParameters(Map<String, Object> parameters) {
        List<SkillParameter> paramDefs = getParameters();
        for (SkillParameter paramDef : paramDefs) {
            if (paramDef.isRequired()) {
                Object value = parameters.get(paramDef.getName());
                if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
                    throw new IllegalArgumentException(
                            String.format("Required parameter '%s' is missing or empty", paramDef.getName()));
                }
            }
        }
    }

    /**
     * Get a parameter value with type casting
     *
     * @param parameters the parameters map
     * @param name parameter name
     * @param type expected type
     * @return the parameter value
     */
    @SuppressWarnings("unchecked")
    protected <T> T getParameter(Map<String, Object> parameters, String name, Class<T> type) {
        Object value = parameters.get(name);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        // Handle common type conversions
        if (type == String.class) {
            return (T) String.valueOf(value);
        }
        if (type == Integer.class && value instanceof Number) {
            return (T) Integer.valueOf(((Number) value).intValue());
        }
        if (type == Long.class && value instanceof Number) {
            return (T) Long.valueOf(((Number) value).longValue());
        }
        if (type == Boolean.class && value instanceof String) {
            return (T) Boolean.valueOf((String) value);
        }
        throw new IllegalArgumentException(
                String.format("Parameter '%s' expected type %s but got %s",
                        name, type.getSimpleName(), value.getClass().getSimpleName()));
    }

    /**
     * Get a required parameter value
     *
     * @param parameters the parameters map
     * @param name parameter name
     * @param type expected type
     * @return the parameter value
     * @throws IllegalArgumentException if parameter is missing
     */
    protected <T> T getRequiredParameter(Map<String, Object> parameters, String name, Class<T> type) {
        T value = getParameter(parameters, name, type);
        if (value == null) {
            throw new IllegalArgumentException(String.format("Required parameter '%s' is missing", name));
        }
        return value;
    }

    /**
     * Get a parameter value with default
     *
     * @param parameters the parameters map
     * @param name parameter name
     * @param type expected type
     * @param defaultValue default value if parameter is missing
     * @return the parameter value or default
     */
    protected <T> T getParameterOrDefault(Map<String, Object> parameters, String name, Class<T> type, T defaultValue) {
        T value = getParameter(parameters, name, type);
        return value != null ? value : defaultValue;
    }
}
