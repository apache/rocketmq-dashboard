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

import org.apache.rocketmq.dashboard.architecture.ClusterAccessType;
import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.config.ArchitectureConfig;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.ClusterTopology;
import org.apache.rocketmq.dashboard.model.request.ArchitectureSwitchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Architecture REST API controller.
 *
 * <p>Provides endpoints for:
 * <ul>
 *   <li>Querying current cluster capabilities and topology</li>
 *   <li>Switching between architecture types at runtime</li>
 *   <li>Health checking the current architecture</li>
 * </ul>
 *
 * <p>These endpoints enable the frontend to implement capability-driven UI rendering,
 * where menus, forms, and operations are dynamically shown/hidden based on the
 * current cluster's supported features.</p>
 */
@RestController
@RequestMapping("/api/architecture")
public class ArchitectureController {

    private static final Logger log = LoggerFactory.getLogger(ArchitectureController.class);

    @Resource
    private ArchitectureConfig.ArchitectureAdaptationManager adaptationManager;

    @Resource
    private ArchitectureConfig.ClusterCapabilityDetector capabilityDetector;

    /**
     * Get current cluster capabilities.
     *
     * <p>Returns the full {@link ClusterCapability} object which drives UI rendering.
     * The frontend uses this to determine which features to show/hide.</p>
     *
     * @return current cluster capabilities
     */
    @GetMapping("/capabilities")
    public ResponseEntity<ClusterCapability> getCapabilities() {
        ClusterCapability capability = adaptationManager.getCurrentCapability();
        return ResponseEntity.ok(capability);
    }

    /**
     * Get current architecture info including access type, capabilities, and topology.
     *
     * @return architecture info map
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getArchitectureInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("accessType", adaptationManager.getCurrentAccessType());
        info.put("capabilities", adaptationManager.getCurrentCapability());

        ClusterProvider provider = adaptationManager.getClusterProvider();
        if (provider != null) {
            try {
                info.put("topology", provider.getClusterTopology());
            } catch (Exception e) {
                log.warn("Failed to get cluster topology: {}", e.getMessage());
                info.put("topology", null);
            }
            try {
                info.put("healthy", provider.isClusterHealthy());
            } catch (Exception e) {
                log.warn("Failed to check cluster health: {}", e.getMessage());
                info.put("healthy", false);
            }
        }

        return ResponseEntity.ok(info);
    }

    /**
     * Get cluster topology (broker nodes, proxy nodes, name server nodes).
     *
     * @return cluster topology
     */
    @GetMapping("/topology")
    public ResponseEntity<ClusterTopology> getTopology() {
        ClusterProvider provider = adaptationManager.getClusterProvider();
        if (provider == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(provider.getClusterTopology());
        } catch (Exception e) {
            log.error("Failed to get cluster topology: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get cluster node list.
     *
     * @return list of cluster nodes
     */
    @GetMapping("/nodes")
    public ResponseEntity<List<?>> getNodes() {
        ClusterProvider provider = adaptationManager.getClusterProvider();
        if (provider == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(provider.getNodeList());
        } catch (Exception e) {
            log.error("Failed to get node list: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Check cluster health status.
     *
     * @return health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        Map<String, Object> health = new HashMap<>();
        ClusterProvider provider = adaptationManager.getClusterProvider();
        if (provider == null) {
            health.put("healthy", false);
            health.put("reason", "No cluster provider available");
            return ResponseEntity.ok(health);
        }

        try {
            boolean healthy = provider.isClusterHealthy();
            health.put("healthy", healthy);
            health.put("accessType", adaptationManager.getCurrentAccessType());
            if (!healthy) {
                health.put("reason", "Cluster health check failed");
            }
        } catch (Exception e) {
            health.put("healthy", false);
            health.put("reason", e.getMessage());
        }

        return ResponseEntity.ok(health);
    }

    /**
     * Switch architecture type at runtime.
     *
     * <p>Supports switching between V4_NAMESRV, V5_PROXY_LOCAL, and V5_PROXY_CLUSTER.
     * For V5 architectures, proxy addresses and NameServer address must be provided.</p>
     *
     * @param request architecture switch request
     * @return switch result
     */
    @PostMapping("/switch")
    public ResponseEntity<Map<String, Object>> switchArchitecture(
            @RequestBody ArchitectureSwitchRequest request) {
        Map<String, Object> result = new HashMap<>();

        try {
            ClusterAccessType targetType = ClusterAccessType.valueOf(request.getAccessType());

            if (targetType.isV5Architecture()) {
                // V5 requires proxy addresses
                if (request.getProxyAddresses() == null || request.getProxyAddresses().length == 0) {
                    result.put("success", false);
                    result.put("error", "Proxy addresses are required for V5 architecture");
                    return ResponseEntity.badRequest().body(result);
                }

                adaptationManager.switchToV5Proxy(
                    targetType,
                    request.getProxyAddresses(),
                    request.getNameSrvAddress(),
                    Optional.ofNullable(request.getNamespace())
                );
            } else {
                // V4 switching
                adaptationManager.switchToArchitecture(targetType);
            }

            result.put("success", true);
            result.put("accessType", adaptationManager.getCurrentAccessType());
            result.put("capabilities", adaptationManager.getCurrentCapability());
            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid architecture switch request: {}", e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            log.error("Architecture switch failed: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", "Switch failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * List all supported architecture types and their descriptions.
     *
     * @return list of supported architecture types
     */
    @GetMapping("/types")
    public ResponseEntity<Map<String, Object>> listArchitectureTypes() {
        Map<String, Object> types = new HashMap<>();

        for (ClusterAccessType accessType : ClusterAccessType.values()) {
            Map<String, Object> typeInfo = new HashMap<>();
            typeInfo.put("name", accessType.name());
            typeInfo.put("isV5", accessType.isV5Architecture());
            typeInfo.put("isV4", accessType.isV4Architecture());
            typeInfo.put("isCloud", accessType.isCloudProvider());
            types.put(accessType.name(), typeInfo);
        }

        return ResponseEntity.ok(types);
    }

    /**
     * Re-detect cluster capabilities from the current provider.
     *
     * @return updated capabilities
     */
    @PostMapping("/detect")
    public ResponseEntity<ClusterCapability> detectCapabilities() {
        ClusterProvider provider = adaptationManager.getClusterProvider();
        if (provider == null) {
            return ResponseEntity.badRequest().build();
        }

        ClusterCapability capability = capabilityDetector.detectCapability(provider);
        return ResponseEntity.ok(capability);
    }
}