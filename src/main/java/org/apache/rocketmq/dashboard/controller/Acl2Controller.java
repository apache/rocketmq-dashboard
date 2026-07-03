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

import org.apache.rocketmq.dashboard.model.Acl2PolicyContext;
import org.apache.rocketmq.dashboard.service.Acl2Service;
import org.apache.rocketmq.dashboard.support.JsonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ACL 2.0 RESTful Controller - Provides dedicated APIs for RBAC-based access control
 * management with namespace-scoped authorization for RocketMQ 5.0+ clusters.
 *
 * This controller handles all CRUD operations for ACL 2.0 policies along with
 * cluster capability detection. It operates independently from the legacy ACL 1.0
 * controller ({@link AclController}).
 *
 * <h3>API Overview</h3>
 * <table border="1">
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/acl2/status</td><td>Get current cluster ACL version</td></tr>
 *   <tr><td>GET</td><td>/api/acl2/policies</td><td>List all ACL 2.0 policies (optional namespace filter)</td></tr>
 *   <tr><td>POST</td><td>/api/acl2/policies</td><td>Create new ACL 2.0 policy</td></tr>
 *   <tr><td>PUT</td><td>/api/acl2/policies/{accessKey}</td><td>Update existing ACL 2.0 policy</td></tr>
 *   <tr><td>DELETE</td><td>/api/acl2/policies/{accessKey}</td><td>Delete ACL 2.0 policy</td></tr>
 *   <tr><td>POST</td><td>/api/acl2/detect</td><td>Manually trigger ACL version detection</td></tr>
 *   <tr><td>GET</td><td>/api/acl2/namespaces</td><td>List available namespaces for scope selection</td></tr>
 * </table>
 */
@Controller
@RequestMapping("/api/acl2")
public class Acl2Controller {

    private static final Logger log = LoggerFactory.getLogger(Acl2Controller.class);

    @Autowired
    private Acl2Service acl2Service;

    /**
     * GET /api/acl2/status - Get current cluster's ACL mode version detection result.
     *
     * Returns the detected ACL version (V1 / V2) based on cluster capabilities.
     * Also includes the raw detection metadata for troubleshooting.
     *
     * @return JsonResult containing ACL version info
     */
    @GetMapping("/status")
    @ResponseBody
    public Object getAclStatus() {
        try {
            String version = acl2Service.detectAclVersion();

            Map<String, Object> statusData = Map.of(
                "aclVersion", version,
                "supported", !"V1".equals(version),
                "message", getVersionMessage(version)
            );

            return new JsonResult<>(statusData);
        } catch (Exception e) {
            log.error("Failed to get ACL 2.0 status", e);
            return new JsonResult<>(1, "Failed to get ACL status: " + e.getMessage());
        }
    }

    /**
     * GET /api/acl2/policies - List all ACL 2.0 policies.
     *
     * Supports optional namespace filtering via query parameter. When namespace is
     * specified, only policies whose namespaceScopes include that namespace (or global
     * policies with empty scopes) are returned.
     *
     * @param namespace optional namespace filter
     * @return JsonResult containing paginated policy list
     */
    @GetMapping("/policies")
    @ResponseBody
    public Object listPolicies(@RequestParam(required = false) String namespace) {
        try {
            Object result = acl2Service.listPolicies(namespace);
            return new JsonResult<>(result);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for listing ACL 2.0 policies: {}", e.getMessage());
            return new JsonResult<>(1, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to list ACL 2.0 policies", e);
            return new JsonResult<>(1, "Failed to list ACL 2.0 policies: " + e.getMessage());
        }
    }

    /**
     * POST /api/acl2/policies - Create a new ACL 2.0 policy.
     *
     * Validates the policy context including required fields (accessKey, policyName, rules).
     * Fails with 400-style response if accessKey already exists or validation fails.
     *
     * @param context the complete policy data with RBAC rules
     * @return JsonResult with created policy details
     */
    @PostMapping("/policies")
    @ResponseBody
    public Object createPolicy(@RequestBody Acl2PolicyContext context) {
        try {
            if (context == null) {
                return new JsonResult<>(1, "Request body cannot be null");
            }
            Object result = acl2Service.createPolicy(context);
            return new JsonResult<>(result);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid ACL 2.0 policy creation request: {}", e.getMessage());
            return new JsonResult<>(1, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create ACL 2.0 policy", e);
            return new JsonResult<>(1, "Failed to create ACL 2.0 policy: " + e.getMessage());
        }
    }

    /**
     * PUT /api/acl2/policies/{accessKey} - Update an existing ACL 2.0 policy.
     *
     * The accessKey path parameter identifies which policy to update. The request body
     * must contain the updated policy data. Only non-null fields in the body will
     * replace the existing values.
     *
     * @param accessKey the access key identifying the policy to update
     * @param context the updated policy data
     * @return JsonResult with updated policy details
     */
    @PutMapping("/policies/{accessKey}")
    @ResponseBody
    public Object updatePolicy(@PathVariable String accessKey,
                               @RequestBody Acl2PolicyContext context) {
        try {
            if (context == null) {
                return new JsonResult<>(1, "Request body cannot be null");
            }
            Object result = acl2Service.updatePolicy(accessKey, context);
            return new JsonResult<>(result);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid ACL 2.0 policy update request: {}", e.getMessage());
            return new JsonResult<>(1, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to update ACL 2.0 policy for accessKey: {}", accessKey, e);
            return new JsonResult<>(1, "Failed to update ACL 2.0 policy: " + e.getMessage());
        }
    }

    /**
     * DELETE /api/acl2/policies/{accessKey} - Delete an ACL 2.0 policy.
     *
     * Permanently removes the policy identified by its accessKey. This operation
     * cannot be undone. The underlying broker ACL configuration will be affected
     * when persistence is enabled.
     *
     * @param accessKey the access key identifying the policy to delete
     * @return JsonResult confirming deletion
     */
    @DeleteMapping("/policies/{accessKey}")
    @ResponseBody
    public Object deletePolicy(@PathVariable String accessKey) {
        try {
            if (accessKey == null || accessKey.trim().isEmpty()) {
                return new JsonResult<>(1, "accessKey cannot be empty");
            }
            Object result = acl2Service.deletePolicy(accessKey);
            return new JsonResult<>(result);
        } catch (IllegalArgumentException e) {
            log.warn("ACL 2.0 policy deletion failed: {}", e.getMessage());
            return new JsonResult<>(1, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete ACL 2.0 policy for accessKey: {}", accessKey, e);
            return new JsonResult<>(1, "Failed to delete ACL 2.0 policy: " + e.getMessage());
        }
    }

    /**
     * POST /api/acl2/detect - Manually trigger ACL version detection.
     *
     * Forces a fresh detection of the cluster's ACL capabilities regardless of
     * the cached value. Useful after broker upgrades or cluster topology changes.
     *
     * @return JsonResult with detailed detection report
     */
    @PostMapping("/detect")
    @ResponseBody
    public Object detectAclVersion() {
        try {
            Map<String, Object> report = acl2Service.detectAndReport();
            return new JsonResult<>(report);
        } catch (Exception e) {
            log.error("Failed to trigger ACL version detection", e);
            return new JsonResult<>(1, "Failed to detect ACL version: " + e.getMessage());
        }
    }

    /**
     * GET /api/acl2/namespaces - List all available namespaces.
     *
     * Returns all namespaces from the MetadataProvider. This endpoint is primarily
     * used by the frontend to populate namespace selection dropdowns when creating
     * or editing ACL 2.0 policies with namespace-scoped authorization.
     *
     * @return JsonResult containing namespace list
     */
    @GetMapping("/namespaces")
    @ResponseBody
    public Object listNamespaces() {
        try {
            Object result = acl2Service.listNamespaces();
            return new JsonResult<>(result);
        } catch (Exception e) {
            log.error("Failed to list namespaces for ACL 2.0", e);
            return new JsonResult<>(1, "Failed to list namespaces: " + e.getMessage());
        }
    }

    /**
     * Generate a user-friendly message for the detected ACL version.
     */
    private String getVersionMessage(String version) {
        switch (version) {
            case "V2":
                return "Cluster supports ACL 2.0 with RBAC and namespace-scoped authorization";
            case "V1":
                return "Cluster only supports ACL 1.0 (legacy file-based ACL). Upgrade to RocketMQ 5.0+ for full ACL 2.0 support";
            default:
                return "Unable to determine ACL version";
        }
    }
}
