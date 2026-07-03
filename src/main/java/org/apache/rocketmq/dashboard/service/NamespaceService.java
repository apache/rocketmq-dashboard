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

import org.apache.rocketmq.dashboard.model.NamespaceInfo;

import java.util.List;
import java.util.Optional;

/**
 * Namespace Management Service Interface.
 *
 * <p>Provides CRUD operations for RocketMQ 5.0 namespace management.
 * In V4 architecture, namespace is a degenerate concept (single DEFAULT namespace).
 * In V5 architecture, namespace is a first-class resource with quota and isolation.</p>
 *
 * <p>This service delegates to the active {@link org.apache.rocketmq.dashboard.architecture.MetadataProvider}
 * based on the current cluster architecture type, ensuring transparent namespace
 * management across V4 and V5 clusters.</p>
 */
public interface NamespaceService {

    /**
     * List all namespaces in the current cluster.
     *
     * @return list of namespace info objects
     * @throws Exception if metadata provider fails
     */
    List<NamespaceInfo> listNamespaces() throws Exception;

    /**
     * Get a specific namespace by name.
     *
     * @param namespaceName the namespace name to look up
     * @return Optional containing the namespace info, or empty if not found
     * @throws Exception if metadata provider fails
     */
    Optional<NamespaceInfo> getNamespace(String namespaceName) throws Exception;

    /**
     * Create a new namespace.
     *
     * @param namespaceInfo the namespace data to create
     * @throws IllegalArgumentException if validation fails or namespace already exists
     * @throws Exception if metadata provider fails
     */
    void createNamespace(NamespaceInfo namespaceInfo) throws Exception;

    /**
     * Update an existing namespace.
     *
     * @param namespaceInfo the namespace data to update
     * @throws IllegalArgumentException if validation fails or namespace not found
     * @throws Exception if metadata provider fails
     */
    void updateNamespace(NamespaceInfo namespaceInfo) throws Exception;

    /**
     * Delete a namespace by name.
     *
     * @param namespaceName the namespace name to delete
     * @throws IllegalArgumentException if namespace name is empty or is the default namespace
     * @throws Exception if metadata provider fails
     */
    void deleteNamespace(String namespaceName) throws Exception;

    /**
     * Check if the current cluster architecture supports namespace management.
     *
     * @return true if namespace is supported (V5 architecture), false otherwise
     */
    boolean isNamespaceSupported();
}