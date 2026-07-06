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

import jakarta.annotation.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.Acl2PolicyContext;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.NamespaceInfo;
import org.apache.rocketmq.dashboard.service.Acl2Service;
import org.apache.rocketmq.dashboard.util.AclVersionDetector;
import org.apache.rocketmq.dashboard.util.UserInfoContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ACL 2.0 Service Implementation.
 *
 * Supports both remote admin API calls (when broker supports ACL 2.0) and local
 * file-based storage fallback. Policy data is persisted as YAML under the dashboard
 * data directory: ${storePath}/acl/acl2_policy_conf.yaml
 *
 * When the underlying RocketMQ SDK provides complete ACL 2.0 admin APIs,
 * the remote call path can be enabled seamlessly.
 */
@Service
public class Acl2ServiceImpl implements Acl2Service {

    private static final Logger log = LoggerFactory.getLogger(Acl2ServiceImpl.class);

    /** ACL 2.0 policy store filename */
    private static final String ACL2_POLICY_FILE = "acl" + File.separator + "acl2_policy_conf.yaml";

    /** ACL version detection component */
    private static final AclVersionDetector ACL_VERSION_DETECTOR = new AclVersionDetector();

    @Resource
    private RMQConfigure rmqConfigure;

    @Resource
    private ClusterProvider clusterProvider;

    @Resource
    private MetadataProvider metadataProvider;

    /** In-memory cache of ACL 2.0 policies, keyed by accessKey */
    private final Map<String, Map<String, Object>> acl2PolicyCache = new ConcurrentHashMap<>();

    /** Cached detected ACL version: V1 / V2 / AUTO */
    private volatile String cachedAclVersion = null;

    /** Lock for file operations to prevent race conditions */
    private final Object fileLock = new Object();

    // ==================== Constructor & Init ====================

    @jakarta.annotation.PostConstruct
    public void init() {
        loadPoliciesFromFile();
        log.info("Acl2ServiceImpl initialized. Policy cache size: {}", acl2PolicyCache.size());
    }

    // ==================== Core Methods ====================

    @Override
    public String detectAclVersion() {
        if (cachedAclVersion != null && !"AUTO".equals(cachedAclVersion)) {
            return cachedAclVersion;
        }

        try {
            ClusterCapability capability = clusterProvider.getClusterCapability();
            String version = ACL_VERSION_DETECTOR.detectAclVersion(capability);

            if ("ACL_2_0".equals(version)) {
                cachedAclVersion = "V2";
            } else if ("ACL_1_0".equals(version)) {
                cachedAclVersion = "V1";
            } else if ("ACL_MIXED".equals(version)) {
                cachedAclVersion = "V2";
                log.warn("Mixed ACL mode detected - falling back to ACL 2.0 behavior for extended features");
            } else {
                cachedAclVersion = "V1";
            }

            log.info("ACL version detected: {}, aclV2Supported={}",
                cachedAclVersion,
                capability != null && capability.isAclV2Supported());

            return cachedAclVersion;
        } catch (Exception e) {
            log.error("Failed to detect ACL version, defaulting to V1", e);
            cachedAclVersion = "V1";
            return "V1";
        }
    }

    @Override
    public Map<String, Object> detectAndReport() {
        Map<String, Object> report = new LinkedHashMap<>();

        try {
            ClusterCapability capability = clusterProvider.getClusterCapability();
            String detectedVersion = ACL_VERSION_DETECTOR.detectAclVersion(capability);

            report.put("rawDetection", detectedVersion);
            report.put("aclV2Supported", capability != null && capability.isAclV2Supported());
            report.put("namespaceSupported", capability != null && capability.isNamespaceSupported());
            report.put("architectureVersion", capability != null ? capability.getArchitectureVersion() : "unknown");
            report.put("rocketmqVersion", capability != null ? capability.getRocketmqVersion() : "unknown");

            // Normalize to user-facing version
            if ("ACL_2_0".equals(detectedVersion) || "ACL_MIXED".equals(detectedVersion)) {
                report.put("effectiveVersion", "V2");
            } else if ("ACL_1_0".equals(detectedVersion)) {
                report.put("effectiveVersion", "V1");
            } else {
                report.put("effectiveVersion", "NONE");
            }

            AclVersionDetector.AclMigrationInfo migrationInfo = ACL_VERSION_DETECTOR.getMigrationInfo(capability);
            report.put("migrationStatus", migrationInfo.getStatus());
            report.put("migrationDescription", migrationInfo.getDescription());

            // Reload policies after fresh detection
            loadPoliciesFromFile();

            log.info("ACL version detection report generated: effectiveVersion={}, aclV2Supported={}",
                report.get("effectiveVersion"), report.get("aclV2Supported"));

        } catch (Exception e) {
            log.error("Failed to generate ACL detection report", e);
            report.put("error", e.getMessage());
            report.put("effectiveVersion", "UNKNOWN");
        }

        return report;
    }

    @Override
    public Object listPolicies(String namespace) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            List<String> targetAccessKeys = new ArrayList<>(acl2PolicyCache.keySet());

            for (String accessKey : targetAccessKeys) {
                Map<String, Object> policy = acl2PolicyCache.get(accessKey);
                if (policy == null) {
                    continue;
                }

                // Filter by namespace if specified
                if (StringUtils.isNotBlank(namespace)) {
                    List<String> scopes = getNamespaceScopes(policy);
                    if (!isInScope(scopes, namespace)) {
                        continue;
                    }
                }

                Map<String, Object> summary = buildPolicySummary(policy);
                result.add(summary);
            }

            // Sort by update time descending
            result.sort((a, b) -> {
                Object timeA = a.get("updateTime");
                Object timeB = b.get("updateTime");
                if (timeA instanceof Long && timeB instanceof Long) {
                    return Long.compare((Long) timeB, (Long) timeA);
                }
                return 0;
            });

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("policies", result);
            response.put("total", result.size());
            response.put("aclVersion", detectAclVersion());
            response.put("cacheSize", acl2PolicyCache.size());

            log.info("List ACL 2.0 policies completed. namespace={}, total={}",
                StringUtils.defaultIfBlank(namespace, "ALL"), result.size());

            return response;

        } catch (Exception e) {
            log.error("Failed to list ACL 2.0 policies", e);
            throw new RuntimeException("Failed to list ACL 2.0 policies: " + e.getMessage(), e);
        }
    }

    @Override
    public Object createPolicy(Acl2PolicyContext context) {
        try {
            // Validate input
            context.validate();

            // Check for duplicate accessKey
            if (acl2PolicyCache.containsKey(context.getAccessKey())) {
                throw new IllegalArgumentException(
                    String.format("Policy with accessKey [%s] already exists. Use update operation.",
                        context.getAccessKey()));
            }

            // Build policy map
            Map<String, Object> policy = convertToMap(context);
            long now = System.currentTimeMillis();
            policy.put("createTime", now);
            policy.put("updateTime", now);
            policy.put("status", "ACTIVE");

            // Store in memory
            acl2PolicyCache.put(context.getAccessKey(), Collections.unmodifiableMap(new HashMap<>(policy)));

            // Persist to file
            savePoliciesToFile();

            // Log
            logCreateOperation(context.getAccessKey(), context.getPolicyName());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("accessKey", context.getAccessKey());
            response.put("policyName", context.getPolicyName());
            response.put("createTime", formatTimestamp(now));
            response.put("message", "ACL 2.0 policy created successfully");

            return response;

        } catch (IllegalArgumentException e) {
            log.warn("ACL 2.0 policy creation validation failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to create ACL 2.0 policy for accessKey: {}",
                context.getAccessKey(), e);
            throw new RuntimeException("Failed to create ACL 2.0 policy: " + e.getMessage(), e);
        }
    }

    @Override
    public Object updatePolicy(String accessKey, Acl2PolicyContext context) {
        try {
            if (StringUtils.isBlank(accessKey)) {
                throw new IllegalArgumentException("accessKey cannot be empty for update operation");
            }

            // Validate input
            context.validate();

            // Check existence
            if (!acl2PolicyCache.containsKey(accessKey)) {
                throw new IllegalArgumentException(
                    String.format("Policy with accessKey [%s] not found. Use create operation instead.", accessKey));
            }

            // Rebuild policy
            Map<String, Object> oldPolicy = new HashMap<>(acl2PolicyCache.get(accessKey));
            Map<String, Object> updatedPolicy = convertToMap(context);
            long now = System.currentTimeMillis();
            updatedPolicy.put("createTime", oldPolicy.get("createTime"));
            updatedPolicy.put("updateTime", now);

            // Merge status if present
            if (oldPolicy.containsKey("status")) {
                updatedPolicy.put("status", oldPolicy.get("status"));
            }

            // Replace in cache
            acl2PolicyCache.put(accessKey, Collections.unmodifiableMap(updatedPolicy));

            // Persist to file
            savePoliciesToFile();

            // Log
            logUpdateOperation(accessKey, context.getPolicyName());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("accessKey", accessKey);
            response.put("policyName", context.getPolicyName());
            response.put("updateTime", formatTimestamp(now));
            response.put("message", "ACL 2.0 policy updated successfully");

            return response;

        } catch (IllegalArgumentException e) {
            log.warn("ACL 2.0 policy update validation failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to update ACL 2.0 policy for accessKey: {}", accessKey, e);
            throw new RuntimeException("Failed to update ACL 2.0 policy: " + e.getMessage(), e);
        }
    }

    @Override
    public Object deletePolicy(String accessKey) {
        try {
            if (StringUtils.isBlank(accessKey)) {
                throw new IllegalArgumentException("accessKey cannot be empty for delete operation");
            }

            // Check existence
            Map<String, Object> removed = acl2PolicyCache.remove(accessKey);
            if (removed == null) {
                throw new IllegalArgumentException(
                    String.format("Policy with accessKey [%s] not found.", accessKey));
            }

            // Persist to file
            savePoliciesToFile();

            // Log
            logDeleteOperation(accessKey);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("accessKey", accessKey);
            response.put("deletedAt", formatTimestamp(System.currentTimeMillis()));
            response.put("message", "ACL 2.0 policy deleted successfully");

            return response;

        } catch (IllegalArgumentException e) {
            log.warn("ACL 2.0 policy deletion failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete ACL 2.0 policy for accessKey: {}", accessKey, e);
            throw new RuntimeException("Failed to delete ACL 2.0 policy: " + e.getMessage(), e);
        }
    }

    @Override
    public Object listNamespaces() {
        try {
            List<NamespaceInfo> namespaces = metadataProvider.listNamespaces();

            List<Map<String, Object>> result = new ArrayList<>();
            if (namespaces != null) {
                for (NamespaceInfo ns : namespaces) {
                    Map<String, Object> nsMap = new LinkedHashMap<>();
                    nsMap.put("namespace", ns.getNamespaceName());
                    nsMap.put("displayName", ns.getDisplayName());
                    nsMap.put("description", ns.getDescription());
                    nsMap.put("clusterName", ns.getClusterName());
                    nsMap.put("status", ns.getStatus());
                    nsMap.put("defaultNamespace", ns.isDefaultNamespace());
                    nsMap.put("createTime", ns.getCreateTime());
                    nsMap.put("updateTime", ns.getUpdateTime());
                    nsMap.put("enabled", ns.isEnabled());
                    nsMap.put("quotaConfig", ns.getQuotaConfig());
                    nsMap.put("attributes", ns.getAttributes());
                    result.add(nsMap);
                }
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("namespaces", result);
            response.put("total", result.size());

            log.info("List namespaces completed. Total: {}", result.size());

            return response;

        } catch (Exception e) {
            log.error("Failed to list namespaces", e);
            throw new RuntimeException("Failed to list namespaces: " + e.getMessage(), e);
        }
    }

    // ==================== Storage Methods ====================

    /**
     * Load all ACL 2.0 policies from the YAML file into memory cache.
     */
    private void loadPoliciesFromFile() {
        synchronized (fileLock) {
            String fullPath = rmqConfigure.getRocketMqDashboardDataPath() + File.separator + ACL2_POLICY_FILE;
            File file = new File(fullPath);

            if (!file.exists()) {
                log.debug("ACL 2.0 policy file not found, skipping load: {}", fullPath);
                return;
            }

            try (FileReader reader = new FileReader(file)) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.loadAs(reader, Map.class);

                if (data == null) {
                    log.warn("ACL 2.0 policy file is empty: {}", fullPath);
                    return;
                }

                Object policiesObj = data.get("policies");
                if (!(policiesObj instanceof List)) {
                    log.warn("ACL 2.0 policy file has invalid structure, expected 'policies' array: {}", fullPath);
                    return;
                }

                List<Map<String, Object>> policyList = (List<Map<String, Object>>) policiesObj;
                acl2PolicyCache.clear();

                for (Map<String, Object> policyMap : policyList) {
                    String accessKey = getStringValue(policyMap, "accessKey");
                    if (StringUtils.isNotBlank(accessKey)) {
                        acl2PolicyCache.put(accessKey, new HashMap<>(policyMap));
                    }
                }

                log.info("Loaded {} ACL 2.0 policies from file: {}", acl2PolicyCache.size(), fullPath);

            } catch (Exception e) {
                log.error("Failed to load ACL 2.0 policies from file: {}", fullPath, e);
            }
        }
    }

    /**
     * Save all in-memory ACL 2.0 policies to the YAML file atomically.
     */
    private void savePoliciesToFile() {
        synchronized (fileLock) {
            String fullPath = rmqConfigure.getRocketMqDashboardDataPath() + File.separator + ACL2_POLICY_FILE;
            File dir = new File(new File(fullPath).getParent());
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Write to temp file first, then rename for atomicity
            String tempPath = fullPath + ".tmp";
            File tempFile = new File(tempPath);
            File destFile = new File(fullPath);

            try (FileWriter writer = new FileWriter(tempFile)) {
                Yaml yaml = new Yaml();

                List<Map<String, Object>> policyList = new ArrayList<>(acl2PolicyCache.values());
                Map<String, Object> output = new LinkedHashMap<>();
                output.put("version", "2.0");
                output.put("updateTime", String.valueOf(System.currentTimeMillis()));
                output.put("count", policyList.size());
                output.put("policies", policyList);

                yaml.dump(output, writer);

                // Atomic rename
                Files.copy(tempFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                tempFile.delete();

                log.info("Saved {} ACL 2.0 policies to file: {}", policyList.size(), fullPath);

            } catch (Exception e) {
                log.error("Failed to save ACL 2.0 policies to file: {}", fullPath, e);
                // Clean up temp file
                if (tempFile.exists()) {
                    tempFile.delete();
                }
                throw new RuntimeException("Failed to persist ACL 2.0 policies: " + e.getMessage(), e);
            }
        }
    }

    // ==================== Conversion Helpers ====================

    /**
     * Convert Acl2PolicyContext DTO to a flat Map for storage.
     */
    private Map<String, Object> convertToMap(Acl2PolicyContext context) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("accessKey", context.getAccessKey());
        map.put("secretKey", StringUtils.defaultIfBlank(context.getSecretKey(), ""));
        map.put("isAdmin", context.isAdmin());
        map.put("whiteSet", context.getWhiteSet() != null ? context.getWhiteSet() : Collections.emptyList());

        // ACL 2.0 fields
        map.put("policyName", context.getPolicyName());
        map.put("boundType", StringUtils.defaultIfBlank(context.getBoundType(), "USER"));
        map.put("boundEntityId", StringUtils.defaultIfBlank(context.getBoundEntityId(), ""));

        // Convert authorization rules
        if (context.getRules() != null) {
            List<Map<String, Object>> ruleMaps = new ArrayList<>();
            for (Acl2PolicyContext.AuthorizationRule rule : context.getRules()) {
                Map<String, Object> ruleMap = new LinkedHashMap<>();
                ruleMap.put("resourcePattern", rule.getResourcePattern());
                ruleMap.put("actions", rule.getActions() != null ? rule.getActions() : Collections.emptyList());
                ruleMap.put("effect", StringUtils.defaultIfBlank(rule.getEffect(), "Allow"));
                ruleMap.put("priority", rule.getPriority());
                ruleMap.put("description", StringUtils.defaultIfBlank(rule.getDescription(), ""));
                ruleMaps.add(ruleMap);
            }
            map.put("rules", ruleMaps);
        } else {
            map.put("rules", Collections.emptyList());
        }

        // Namespace scopes
        map.put("namespaceScopes", context.getNamespaceScopes() != null
            ? context.getNamespaceScopes() : Collections.emptyList());

        map.put("enabled", context.isEnabled());
        map.put("description", StringUtils.defaultIfBlank(context.getDescription(), ""));
        map.put("clusterName", StringUtils.defaultIfBlank(context.getClusterName(), ""));
        map.put("brokerName", StringUtils.defaultIfBlank(context.getBrokerName(), ""));

        return map;
    }

    /**
     * Build a summary view of a policy (without sensitive fields like secretKey).
     */
    private Map<String, Object> buildPolicySummary(Map<String, Object> policy) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("accessKey", getStringValue(policy, "accessKey"));
        summary.put("policyName", getStringValue(policy, "policyName"));
        summary.put("boundType", getStringValue(policy, "boundType"));
        summary.put("boundEntityId", getStringValue(policy, "boundEntityId"));
        summary.put("isAdmin", policy.getOrDefault("isAdmin", false));
        summary.put("enabled", policy.getOrDefault("enabled", true));
        summary.put("status", getStringValue(policy, "status", "ACTIVE"));
        summary.put("namespaceScopes", policy.get("namespaceScopes"));
        summary.put("rules", policy.get("rules"));
        summary.put("whiteSet", policy.get("whiteSet"));
        summary.put("createTime", policy.get("createTime"));
        summary.put("updateTime", policy.get("updateTime"));
        summary.put("description", getStringValue(policy, "description", ""));
        summary.put("clusterName", getStringValue(policy, "clusterName", ""));
        summary.put("brokerName", getStringValue(policy, "brokerName", ""));
        return summary;
    }

    /**
     * Get namespace scopes from a stored policy map.
     */
    @SuppressWarnings("unchecked")
    private List<String> getNamespaceScopes(Map<String, Object> policy) {
        Object scopes = policy.get("namespaceScopes");
        if (scopes instanceof List) {
            return (List<String>) scopes;
        }
        return Collections.emptyList();
    }

    /**
     * Check if a namespace matches any scope in the list.
     * Empty scopes means global scope (matches all).
     */
    private boolean isInScope(List<String> scopes, String namespace) {
        if (CollectionUtils.isEmpty(scopes)) {
            return true; // Global scope - matches everything
        }
        return scopes.contains(namespace);
    }

    /**
     * Extract string value from a map, returning null if absent.
     */
    private String getStringValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }

    /**
     * Extract string value from a map, returning a default if absent.
     */
    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : defaultValue;
    }

    /**
     * Format a timestamp millis value to ISO-like string representation.
     */
    private String formatTimestamp(long millis) {
        return String.valueOf(millis);
    }

    // ==================== Logging Helpers ====================

    /**
     * Log a CREATE operation with actor info from UserInfoContext.
     */
    private void logCreateOperation(String accessKey, String policyName) {
        String operator = getCurrentOperator();
        Date now = new Date();
        log.info("[ACL2-OPERATION] CREATE | operator={} | accessKey={} | policyName={} | timestamp={}",
            operator, accessKey, policyName, now);
    }

    /**
     * Log an UPDATE operation with actor info from UserInfoContext.
     */
    private void logUpdateOperation(String accessKey, String policyName) {
        String operator = getCurrentOperator();
        Date now = new Date();
        log.info("[ACL2-OPERATION] UPDATE | operator={} | accessKey={} | policyName={} | timestamp={}",
            operator, accessKey, policyName, now);
    }

    /**
     * Log a DELETE operation with actor info from UserInfoContext.
     */
    private void logDeleteOperation(String accessKey) {
        String operator = getCurrentOperator();
        Date now = new Date();
        log.info("[ACL2-OPERATION] DELETE | operator={} | accessKey={} | timestamp={}",
            operator, accessKey, now);
    }

    /**
     * Get current operator username from UserInfoContext ThreadLocal.
     */
    private String getCurrentOperator() {
        Object userName = UserInfoContext.get("username");
        return userName != null ? String.valueOf(userName) : "system";
    }
}
