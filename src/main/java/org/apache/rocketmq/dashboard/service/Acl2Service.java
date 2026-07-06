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
package org.apache.rocketmq.dashboard.service;

import org.apache.rocketmq.dashboard.model.Acl2PolicyContext;

import java.util.Map;

/**
 * ACL 2.0 Business Logic Service Interface.
 *
 * Provides RBAC-based access control management with namespace-scoped authorization
 * for RocketMQ 5.0+ clusters. Supports both direct admin API calls (when broker supports
 * ACL 2.0) and local file storage fallback mode.
 */
public interface Acl2Service {

    /**
     * Detect current cluster's ACL mode version.
     *
     * @return "V1" if ACL 1.0 only, "V2" if ACL 2.0 supported, "AUTO" if auto-detection pending
     */
    String detectAclVersion();

    /**
     * List all ACL 2.0 policies, optionally filtered by namespace.
     *
     * @param namespace target namespace filter (null or empty returns all namespaces)
     * @return Map containing policy list and metadata
     */
    Object listPolicies(String namespace);

    /**
     * Create a new ACL 2.0 policy.
     *
     * @param context the policy data including RBAC rules and namespace scope
     * @return result map with created policy details
     * @throws IllegalArgumentException if validation fails
     */
    Object createPolicy(Acl2PolicyContext context);

    /**
     * Update an existing ACL 2.0 policy identified by accessKey.
     *
     * @param accessKey the access key identifying the policy to update
     * @param context   the updated policy data
     * @return result map with updated policy details
     * @throws IllegalArgumentException if validation fails or policy not found
     */
    Object updatePolicy(String accessKey, Acl2PolicyContext context);

    /**
     * Delete an ACL 2.0 policy by its access key.
     *
     * @param accessKey the access key identifying the policy to delete
     * @return result map confirming deletion
     * @throws IllegalArgumentException if accessKey is empty
     */
    Object deletePolicy(String accessKey);

    /**
     * List all available namespaces for authorization scope selection.
     *
     * @return list of namespace info objects
     */
    Object listNamespaces();

    /**
     * Manually trigger ACL version detection and cache the result.
     *
     * @return map with detection result details
     */
    Map<String, Object> detectAndReport();
}
