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

import java.util.List;
import java.util.Map;

/**
 * Skill interface for AI/Agent tool capabilities.
 * Each Skill wraps an operational capability (e.g., create Topic, query consumer progress)
 * that can be invoked by LLM/MCP agents.
 */
public interface Skill {

    /**
     * Get unique skill identifier (e.g., "topic.query", "cluster.info")
     */
    String getId();

    /**
     * Get human-readable name
     */
    String getName();

    /**
     * Get description for LLM to understand when to use this skill
     */
    String getDescription();

    /**
     * Get parameter definitions
     */
    List<SkillParameter> getParameters();

    /**
     * Get the resource type this skill operates on (e.g., "topic", "cluster", "group")
     */
    String getResourceType();

    /**
     * Get the action verb (e.g., "query", "create", "delete")
     */
    String getVerb();

    /**
     * Get risk level (L1=read-only, L2=controlled mutation, L3=dangerous)
     */
    RiskLevel getRiskLevel();

    /**
     * Execute the skill with given parameters
     *
     * @param parameters map of parameter name to value
     * @return execution result
     */
    SkillResult execute(Map<String, Object> parameters);

    /**
     * Initialize the skill (called once during registration)
     */
    default void initialize() {
        // no-op by default
    }

    /**
     * Destroy the skill (called once during deregistration)
     */
    default void destroy() {
        // no-op by default
    }

    /**
     * Check if this skill is currently available
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * Risk level classification
     */
    enum RiskLevel {
        L1, // Read-only, safe
        L2, // Controlled mutation, requires dry-run confirmation
        L3  // Dangerous, blocked by default
    }
}
