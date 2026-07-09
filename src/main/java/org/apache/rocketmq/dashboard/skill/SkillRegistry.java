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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SkillRegistry manages the lifecycle of all Skill instances.
 * It auto-discovers Skill beans from Spring context and supports dynamic registration/deregistration.
 */
@Slf4j
@Component
public class SkillRegistry implements ApplicationListener<ContextRefreshedEvent> {

    private final Map<String, Skill> skills = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;

    @Autowired(required = false)
    private ApplicationContext applicationContext;

    /**
     * Called when Spring context is refreshed (application startup).
     * Auto-discovers all Skill beans and registers them.
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (initialized) {
            return;
        }
        if (applicationContext == null) {
            log.warn("ApplicationContext is null, skipping skill auto-discovery");
            return;
        }

        Map<String, Skill> skillBeans = applicationContext.getBeansOfType(Skill.class);
        log.info("Discovered {} skill beans, registering...", skillBeans.size());

        for (Map.Entry<String, Skill> entry : skillBeans.entrySet()) {
            String beanName = entry.getKey();
            Skill skill = entry.getValue();
            try {
                registerSkill(skill);
                log.info("Registered skill: {} ({})", skill.getId(), beanName);
            } catch (Exception e) {
                log.error("Failed to register skill: {} ({})", skill.getId(), beanName, e);
            }
        }

        initialized = true;
        log.info("SkillRegistry initialized with {} skills", skills.size());
    }

    /**
     * Register a skill instance and initialize it
     *
     * @param skill the skill to register
     * @throws IllegalArgumentException if a skill with the same ID already exists
     */
    public void registerSkill(Skill skill) {
        if (skill == null) {
            throw new IllegalArgumentException("Skill cannot be null");
        }
        String id = skill.getId();
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Skill ID cannot be null or empty");
        }
        if (skills.containsKey(id)) {
            throw new IllegalArgumentException("Skill with ID '" + id + "' already registered");
        }

        skill.initialize();
        skills.put(id, skill);
        log.info("Skill registered: {} [{}]", id, skill.getName());
    }

    /**
     * Unregister a skill by ID and destroy it
     *
     * @param skillId the skill ID to unregister
     * @return true if the skill was found and unregistered
     */
    public boolean unregisterSkill(String skillId) {
        Skill skill = skills.remove(skillId);
        if (skill != null) {
            try {
                skill.destroy();
                log.info("Skill unregistered and destroyed: {}", skillId);
            } catch (Exception e) {
                log.error("Error destroying skill: {}", skillId, e);
            }
            return true;
        }
        log.warn("Skill not found for unregistration: {}", skillId);
        return false;
    }

    /**
     * Get a skill by ID
     *
     * @param skillId the skill ID
     * @return the skill, or null if not found
     */
    public Skill getSkill(String skillId) {
        return skills.get(skillId);
    }

    /**
     * Get all registered skills
     *
     * @return unmodifiable list of all skills
     */
    public List<Skill> getAllSkills() {
        return Collections.unmodifiableList(new ArrayList<>(skills.values()));
    }

    /**
     * Get all available skills (where isAvailable() returns true)
     *
     * @return list of available skills
     */
    public List<Skill> getAvailableSkills() {
        return skills.values().stream()
                .filter(Skill::isAvailable)
                .collect(Collectors.toList());
    }

    /**
     * Get skills by resource type
     *
     * @param resourceType the resource type to filter by
     * @return list of matching skills
     */
    public List<Skill> getSkillsByResourceType(String resourceType) {
        return skills.values().stream()
                .filter(s -> resourceType.equals(s.getResourceType()))
                .collect(Collectors.toList());
    }

    /**
     * Get skills by risk level
     *
     * @param riskLevel the risk level to filter by
     * @return list of matching skills
     */
    public List<Skill> getSkillsByRiskLevel(Skill.RiskLevel riskLevel) {
        return skills.values().stream()
                .filter(s -> s.getRiskLevel() == riskLevel)
                .collect(Collectors.toList());
    }

    /**
     * Check if a skill with the given ID is registered
     *
     * @param skillId the skill ID
     * @return true if registered
     */
    public boolean hasSkill(String skillId) {
        return skills.containsKey(skillId);
    }

    /**
     * Get the total number of registered skills
     *
     * @return skill count
     */
    public int getSkillCount() {
        return skills.size();
    }

    /**
     * Get skill metadata summary for all skills
     *
     * @return list of skill metadata maps
     */
    public List<Map<String, Object>> getSkillMetadata() {
        return skills.values().stream()
                .map(skill -> {
                    Map<String, Object> meta = new ConcurrentHashMap<>();
                    meta.put("id", skill.getId());
                    meta.put("name", skill.getName());
                    meta.put("description", skill.getDescription());
                    meta.put("resourceType", skill.getResourceType());
                    meta.put("verb", skill.getVerb());
                    meta.put("riskLevel", skill.getRiskLevel().name());
                    meta.put("available", skill.isAvailable());
                    meta.put("parameters", skill.getParameters());
                    return meta;
                })
                .collect(Collectors.toList());
    }

    /**
     * Destroy all skills on application shutdown
     */
    @PreDestroy
    public void destroyAll() {
        log.info("Destroying all {} registered skills...", skills.size());
        for (Map.Entry<String, Skill> entry : skills.entrySet()) {
            try {
                entry.getValue().destroy();
                log.debug("Destroyed skill: {}", entry.getKey());
            } catch (Exception e) {
                log.error("Error destroying skill: {}", entry.getKey(), e);
            }
        }
        skills.clear();
        log.info("All skills destroyed");
    }
}
