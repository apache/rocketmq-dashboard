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

import jakarta.annotation.Resource;
import org.apache.rocketmq.dashboard.service.ClusterService;
import org.apache.rocketmq.dashboard.skill.Skill;
import org.apache.rocketmq.dashboard.skill.SkillParameter;
import org.apache.rocketmq.dashboard.skill.SkillRegistry;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Compatibility Alias Controller.
 *
 * <p>Provides alias endpoints for cross-subsystem discovery and backward
 * compatibility. These endpoints aggregate data from existing services and
 * present them in a unified, self-describing format suitable for e2e test
 * discovery and external integrations.</p>
 *
 * <h3>API Overview</h3>
 * <table border="1">
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/skills</td><td>List all registered skills with metadata</td></tr>
 *   <tr><td>GET</td><td>/broker/brokerStatus.query</td><td>Query broker configuration and runtime status</td></tr>
 * </table>
 */
@RestController
@RequestMapping("")
public class CompatibilityAliasController {

    private static final Logger log = LoggerFactory.getLogger(CompatibilityAliasController.class);

    @Resource
    private SkillRegistry skillRegistry;

    @Resource
    private ClusterService clusterService;

    @Resource
    private MQAdminExt mqAdminExt;

    // ==================== Skills Endpoint ====================

    /**
     * GET /api/skills - List all registered skills with full metadata.
     *
     * <p>Returns a list of all registered skills including name, description,
     * parameters, and risk level. This endpoint provides a unified skill
     * discovery interface for e2e tests and external integrations.</p>
     *
     * @return map containing skill list and summary statistics
     */
    @GetMapping("/api/skills")
    public Map<String, Object> listSkills() {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Skill> allSkills = skillRegistry.getAllSkills();
        List<Map<String, Object>> skillList = new ArrayList<>();

        for (Skill skill : allSkills) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", skill.getId());
            entry.put("name", skill.getName());
            entry.put("description", skill.getDescription());
            entry.put("resourceType", skill.getResourceType());
            entry.put("verb", skill.getVerb());
            entry.put("riskLevel", skill.getRiskLevel().name());
            entry.put("available", skill.isAvailable());

            // Convert parameters to serializable format
            List<Map<String, Object>> params = new ArrayList<>();
            if (skill.getParameters() != null) {
                for (SkillParameter param : skill.getParameters()) {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("name", param.getName());
                    p.put("type", param.getType());
                    p.put("required", param.isRequired());
                    p.put("description", param.getDescription());
                    p.put("defaultValue", param.getDefaultValue());
                    if (param.getAllowedValues() != null) {
                        p.put("allowedValues", param.getAllowedValues());
                    }
                    params.add(p);
                }
            }
            entry.put("parameters", params);

            skillList.add(entry);
        }

        result.put("skills", skillList);
        result.put("total", skillList.size());

        // Summary by risk level
        Map<String, Long> byRiskLevel = new LinkedHashMap<>();
        byRiskLevel.put("L1", allSkills.stream().filter(s -> s.getRiskLevel() == Skill.RiskLevel.L1).count());
        byRiskLevel.put("L2", allSkills.stream().filter(s -> s.getRiskLevel() == Skill.RiskLevel.L2).count());
        byRiskLevel.put("L3", allSkills.stream().filter(s -> s.getRiskLevel() == Skill.RiskLevel.L3).count());
        result.put("byRiskLevel", byRiskLevel);

        return result;
    }

    // ==================== Broker Status Endpoint ====================

    /**
     * GET /broker/brokerStatus.query - Query broker configuration and runtime status.
     *
     * <p>Retrieves both the broker configuration properties and runtime statistics
     * for the specified broker address. This is a compatibility alias that combines
     * broker config and runtime stats into a single response.</p>
     *
     * @param brokerAddr broker address (e.g., "192.168.1.1:10911")
     * @return map containing brokerConfig and runtimeStats
     */
    @GetMapping("/broker/brokerStatus.query")
    public Map<String, Object> queryBrokerStatus(@RequestParam String brokerAddr) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (brokerAddr == null || brokerAddr.trim().isEmpty()) {
            result.put("error", "brokerAddr parameter is required");
            return result;
        }

        // Get broker configuration
        try {
            Properties config = clusterService.getBrokerConfig(brokerAddr.trim());
            result.put("brokerConfig", config);
        } catch (Exception e) {
            log.warn("Failed to get broker config for {}: {}", brokerAddr, e.getMessage());
            result.put("brokerConfig", null);
            result.put("brokerConfigError", e.getMessage());
        }

        // Get broker runtime stats via MQAdminExt
        try {
            org.apache.rocketmq.remoting.protocol.body.KVTable kvTable =
                    mqAdminExt.fetchBrokerRuntimeStats(brokerAddr.trim());
            if (kvTable != null && kvTable.getTable() != null) {
                result.put("runtimeStats", new LinkedHashMap<>(kvTable.getTable()));
            } else {
                result.put("runtimeStats", null);
            }
        } catch (Exception e) {
            log.warn("Failed to get runtime stats for {}: {}", brokerAddr, e.getMessage());
            result.put("runtimeStats", null);
            result.put("runtimeStatsError", e.getMessage());
        }

        result.put("brokerAddr", brokerAddr.trim());
        return result;
    }
}