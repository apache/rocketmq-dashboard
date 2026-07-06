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

import org.apache.rocketmq.dashboard.model.NamespaceInfo;
import org.apache.rocketmq.dashboard.service.NamespaceService;
import org.apache.rocketmq.dashboard.support.JsonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Namespace REST API Controller.
 *
 * <p>Provides CRUD endpoints for RocketMQ 5.0 namespace management.
 * In V4 architecture, only the DEFAULT namespace is available (read-only).
 * In V5 architecture, full namespace CRUD is supported with quota management.</p>
 *
 * <h3>API Overview</h3>
 * <table border="1">
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/namespace/list</td><td>List all namespaces</td></tr>
 *   <tr><td>GET</td><td>/api/namespace/{name}</td><td>Get namespace by name</td></tr>
 *   <tr><td>POST</td><td>/api/namespace/create</td><td>Create a new namespace</td></tr>
 *   <tr><td>PUT</td><td>/api/namespace/update</td><td>Update an existing namespace</td></tr>
 *   <tr><td>DELETE</td><td>/api/namespace/{name}</td><td>Delete a namespace</td></tr>
 *   <tr><td>GET</td><td>/api/namespace/capability</td><td>Check namespace support status</td></tr>
 * </table>
 */
@Controller
@RequestMapping("/api/namespace")
public class NamespaceController {

    private static final Logger log = LoggerFactory.getLogger(NamespaceController.class);

    @Autowired
    private NamespaceService namespaceService;

    /**
     * GET /api/namespace/list - List all namespaces.
     *
     * @return JsonResult containing namespace list
     */
    @GetMapping("/list")
    @ResponseBody
    public Object listNamespaces() {
        try {
            List<NamespaceInfo> namespaces = namespaceService.listNamespaces();
            return new JsonResult<>(namespaces);
        } catch (Exception e) {
            log.error("Failed to list namespaces", e);
            return new JsonResult<>(1, "Failed to list namespaces: " + e.getMessage());
        }
    }

    /**
     * GET /api/namespace/{name} - Get a specific namespace by name.
     *
     * @param name the namespace name
     * @return JsonResult containing namespace info
     */
    @GetMapping("/{name}")
    @ResponseBody
    public Object getNamespace(@PathVariable String name) {
        try {
            Optional<NamespaceInfo> nsInfo = namespaceService.getNamespace(name);
            if (nsInfo.isPresent()) {
                return new JsonResult<>(nsInfo.get());
            } else {
                return new JsonResult<>(1, "Namespace '" + name + "' not found");
            }
        } catch (Exception e) {
            log.error("Failed to get namespace: {}", name, e);
            return new JsonResult<>(1, "Failed to get namespace: " + e.getMessage());
        }
    }

    /**
     * POST /api/namespace/create - Create a new namespace.
     *
     * @param namespaceInfo the namespace data to create
     * @return JsonResult with creation result
     */
    @PostMapping("/create")
    @ResponseBody
    public Object createNamespace(@RequestBody NamespaceInfo namespaceInfo) {
        try {
            if (namespaceInfo == null) {
                return new JsonResult<>(1, "Request body cannot be null");
            }

            // Pre-check namespace support
            if (!namespaceService.isNamespaceSupported()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("supported", false);
                result.put("message", "Namespace management is not supported in current cluster architecture. " +
                    "Upgrade to RocketMQ 5.0+ with Proxy to enable namespace management.");
                return new JsonResult<>(2, result, "Namespace not supported");
            }

            namespaceService.createNamespace(namespaceInfo);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("namespaceName", namespaceInfo.getNamespaceName());
            result.put("message", "Namespace created successfully");
            return new JsonResult<>(result);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid namespace creation request: {}", e.getMessage());
            return new JsonResult<>(1, e.getMessage());
        } catch (UnsupportedOperationException e) {
            log.warn("Namespace creation not supported: {}", e.getMessage());
            return new JsonResult<>(2, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create namespace", e);
            return new JsonResult<>(1, "Failed to create namespace: " + e.getMessage());
        }
    }

    /**
     * PUT /api/namespace/update - Update an existing namespace.
     *
     * @param namespaceInfo the namespace data to update
     * @return JsonResult with update result
     */
    @PutMapping("/update")
    @ResponseBody
    public Object updateNamespace(@RequestBody NamespaceInfo namespaceInfo) {
        try {
            if (namespaceInfo == null) {
                return new JsonResult<>(1, "Request body cannot be null");
            }

            namespaceService.updateNamespace(namespaceInfo);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("namespaceName", namespaceInfo.getNamespaceName());
            result.put("message", "Namespace updated successfully");
            return new JsonResult<>(result);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid namespace update request: {}", e.getMessage());
            return new JsonResult<>(1, e.getMessage());
        } catch (UnsupportedOperationException e) {
            log.warn("Namespace update not supported: {}", e.getMessage());
            return new JsonResult<>(2, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to update namespace", e);
            return new JsonResult<>(1, "Failed to update namespace: " + e.getMessage());
        }
    }

    /**
     * DELETE /api/namespace/{name} - Delete a namespace.
     *
     * @param name the namespace name to delete
     * @return JsonResult confirming deletion
     */
    @DeleteMapping("/{name}")
    @ResponseBody
    public Object deleteNamespace(@PathVariable String name) {
        try {
            if (name == null || name.trim().isEmpty()) {
                return new JsonResult<>(1, "Namespace name cannot be empty");
            }

            namespaceService.deleteNamespace(name);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("namespaceName", name);
            result.put("message", "Namespace deleted successfully");
            return new JsonResult<>(result);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid namespace deletion request: {}", e.getMessage());
            return new JsonResult<>(1, e.getMessage());
        } catch (UnsupportedOperationException e) {
            log.warn("Namespace deletion not supported: {}", e.getMessage());
            return new JsonResult<>(2, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete namespace: {}", name, e);
            return new JsonResult<>(1, "Failed to delete namespace: " + e.getMessage());
        }
    }

    /**
     * GET /api/namespace/capability - Check if namespace management is supported.
     *
     * @return JsonResult with namespace capability status
     */
    @GetMapping("/capability")
    @ResponseBody
    public Object getNamespaceCapability() {
        Map<String, Object> capability = new LinkedHashMap<>();
        capability.put("namespaceSupported", namespaceService.isNamespaceSupported());
        capability.put("message", namespaceService.isNamespaceSupported()
            ? "Namespace management is fully supported in this cluster"
            : "Namespace management requires RocketMQ 5.0+ with Proxy architecture");
        return new JsonResult<>(capability);
    }
}