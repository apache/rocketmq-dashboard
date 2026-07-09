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
package org.apache.rocketmq.dashboard.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.dashboard.skill.Skill;
import org.apache.rocketmq.dashboard.skill.SkillExecutor;
import org.apache.rocketmq.dashboard.skill.SkillRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for Skill management and execution.
 * Provides endpoints for listing, registering, unregistering, and executing skills.
 */
@Slf4j
@RestController
@RequestMapping("/api/skill")
public class SkillRegistryController {

    @Autowired
    private SkillRegistry skillRegistry;

    @Autowired
    private SkillExecutor skillExecutor;

    /**
     * List all registered skills with metadata
     */
    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> listSkills() {
        List<Map<String, Object>> metadata = skillRegistry.getSkillMetadata();
        return ResponseEntity.ok(metadata);
    }

    /**
     * Get skill details by ID
     */
    @GetMapping("/{skillId}")
    public ResponseEntity<Map<String, Object>> getSkill(@PathVariable String skillId) {
        Skill skill = skillRegistry.getSkill(skillId);
        if (skill == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", skill.getId());
        metadata.put("name", skill.getName());
        metadata.put("description", skill.getDescription());
        metadata.put("resourceType", skill.getResourceType());
        metadata.put("verb", skill.getVerb());
        metadata.put("riskLevel", skill.getRiskLevel().name());
        metadata.put("available", skill.isAvailable());
        metadata.put("parameters", skill.getParameters());

        return ResponseEntity.ok(metadata);
    }

    /**
     * Execute a skill by ID
     */
    @PostMapping("/{skillId}/execute")
    public ResponseEntity<Map<String, Object>> executeSkill(
            @PathVariable String skillId,
            @RequestBody(required = false) Map<String, Object> parameters) {

        Map<String, Object> result = skillExecutor.executeTool(skillId, parameters);

        Boolean success = (Boolean) result.get("success");
        if (success != null && success) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * List skills in MCP tool format (for LLM/MCP integration)
     */
    @GetMapping("/mcp/tools")
    public ResponseEntity<List<Map<String, Object>>> listMcpTools() {
        List<Map<String, Object>> tools = skillExecutor.listTools();
        return ResponseEntity.ok(tools);
    }

    /**
     * Execute a skill via MCP tool call format
     */
    @PostMapping("/mcp/execute")
    public ResponseEntity<Map<String, Object>> executeMcpTool(@RequestBody Map<String, Object> request) {
        String toolName = (String) request.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) request.get("arguments");

        if (toolName == null || toolName.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Tool name is required");
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, Object> result = skillExecutor.executeTool(toolName, arguments);

        Boolean success = (Boolean) result.get("success");
        if (success != null && success) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Get skill statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSkills", skillRegistry.getSkillCount());
        stats.put("availableSkills", skillRegistry.getAvailableSkills().size());

        // Count by risk level
        Map<String, Long> byRiskLevel = new HashMap<>();
        byRiskLevel.put("L1", (long) skillRegistry.getSkillsByRiskLevel(Skill.RiskLevel.L1).size());
        byRiskLevel.put("L2", (long) skillRegistry.getSkillsByRiskLevel(Skill.RiskLevel.L2).size());
        byRiskLevel.put("L3", (long) skillRegistry.getSkillsByRiskLevel(Skill.RiskLevel.L3).size());
        stats.put("byRiskLevel", byRiskLevel);

        // Count by resource type
        Map<String, Long> byResourceType = new HashMap<>();
        for (Skill skill : skillRegistry.getAllSkills()) {
            String resourceType = skill.getResourceType();
            byResourceType.put(resourceType, byResourceType.getOrDefault(resourceType, 0L) + 1);
        }
        stats.put("byResourceType", byResourceType);

        return ResponseEntity.ok(stats);
    }

    /**
     * Register a new skill dynamically (for runtime extensibility)
     * Note: This requires the skill class to be already loaded in the classpath
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerSkill(@RequestBody Map<String, Object> request) {
        String className = (String) request.get("className");
        if (className == null || className.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "className is required");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            Class<?> clazz = Class.forName(className);
            if (!Skill.class.isAssignableFrom(clazz)) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Class does not implement Skill interface: " + className);
                return ResponseEntity.badRequest().body(error);
            }

            @SuppressWarnings("unchecked")
            Class<? extends Skill> skillClass = (Class<? extends Skill>) clazz;
            Skill skill = skillClass.getDeclaredConstructor().newInstance();
            skillRegistry.registerSkill(skill);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Skill registered successfully: " + skill.getId());
            result.put("skillId", skill.getId());
            return ResponseEntity.ok(result);

        } catch (ClassNotFoundException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Class not found: " + className);
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Failed to register skill: {}", className, e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to register skill: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Unregister a skill by ID
     */
    @DeleteMapping("/{skillId}")
    public ResponseEntity<Map<String, Object>> unregisterSkill(@PathVariable String skillId) {
        boolean removed = skillRegistry.unregisterSkill(skillId);

        Map<String, Object> result = new HashMap<>();
        if (removed) {
            result.put("success", true);
            result.put("message", "Skill unregistered: " + skillId);
            return ResponseEntity.ok(result);
        } else {
            result.put("success", false);
            result.put("error", "Skill not found: " + skillId);
            return ResponseEntity.notFound().build();
        }
    }
}
