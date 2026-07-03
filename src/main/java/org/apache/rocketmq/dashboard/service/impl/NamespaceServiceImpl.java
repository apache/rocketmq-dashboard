/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work or additional information regarding copyright ownership.
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

import org.apache.rocketmq.dashboard.model.NamespaceInfo;
import org.apache.rocketmq.dashboard.service.ArchitectureBasedService;
import org.apache.rocketmq.dashboard.service.NamespaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Namespace Service Implementation.
 *
 * <p>Delegates namespace CRUD operations to the active MetadataProvider
 * based on the current cluster architecture type. In V4 architecture,
 * namespace operations are degenerate (single DEFAULT namespace only).
 * In V5 architecture, full namespace CRUD is supported.</p>
 */
@Service
public class NamespaceServiceImpl extends ArchitectureBasedService implements NamespaceService {

    private static final Logger log = LoggerFactory.getLogger(NamespaceServiceImpl.class);

    @Override
    public List<NamespaceInfo> listNamespaces() throws Exception {
        try {
            List<NamespaceInfo> namespaces = metadataProvider.listNamespaces();
            log.debug("Listed {} namespaces (architecture={})",
                namespaces != null ? namespaces.size() : 0,
                clusterCapability != null ? clusterCapability.getArchitectureVersion() : "unknown");
            return namespaces != null ? namespaces : Collections.emptyList();
        } catch (UnsupportedOperationException e) {
            log.warn("Namespace listing not supported in current architecture, returning DEFAULT only");
            return Collections.singletonList(buildDefaultNamespace());
        } catch (Exception e) {
            log.error("Failed to list namespaces", e);
            throw e;
        }
    }

    @Override
    public Optional<NamespaceInfo> getNamespace(String namespaceName) throws Exception {
        if (namespaceName == null || namespaceName.isEmpty()) {
            return Optional.of(buildDefaultNamespace());
        }

        try {
            return metadataProvider.getNamespace(namespaceName);
        } catch (UnsupportedOperationException e) {
            log.warn("Namespace lookup not supported in current architecture");
            if ("DEFAULT".equals(namespaceName) || namespaceName.isEmpty()) {
                return Optional.of(buildDefaultNamespace());
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get namespace: {}", namespaceName, e);
            throw e;
        }
    }

    @Override
    public void createNamespace(NamespaceInfo namespaceInfo) throws Exception {
        if (!isNamespaceSupported()) {
            throw new UnsupportedOperationException(
                "Namespace creation is not supported in current cluster architecture. " +
                "Upgrade to RocketMQ 5.0+ with Proxy to enable namespace management.");
        }

        if (namespaceInfo == null || !namespaceInfo.isValid()) {
            throw new IllegalArgumentException("NamespaceInfo must have a valid namespaceName");
        }

        log.info("Creating namespace: {} displayName={}",
            namespaceInfo.getNamespaceName(), namespaceInfo.getDisplayName());
        metadataProvider.createNamespace(namespaceInfo);
    }

    @Override
    public void updateNamespace(NamespaceInfo namespaceInfo) throws Exception {
        if (!isNamespaceSupported()) {
            throw new UnsupportedOperationException(
                "Namespace update is not supported in current cluster architecture. " +
                "Upgrade to RocketMQ 5.0+ with Proxy to enable namespace management.");
        }

        if (namespaceInfo == null || !namespaceInfo.isValid()) {
            throw new IllegalArgumentException("NamespaceInfo must have a valid namespaceName");
        }

        log.info("Updating namespace: {} displayName={}",
            namespaceInfo.getNamespaceName(), namespaceInfo.getDisplayName());
        metadataProvider.updateNamespace(namespaceInfo);
    }

    @Override
    public void deleteNamespace(String namespaceName) throws Exception {
        if (!isNamespaceSupported()) {
            throw new UnsupportedOperationException(
                "Namespace deletion is not supported in current cluster architecture. " +
                "Upgrade to RocketMQ 5.0+ with Proxy to enable namespace management.");
        }

        if (namespaceName == null || namespaceName.isEmpty()) {
            throw new IllegalArgumentException("Cannot delete the default namespace");
        }

        log.info("Deleting namespace: {}", namespaceName);
        metadataProvider.deleteNamespace(namespaceName);
    }

    @Override
    public boolean isNamespaceSupported() {
        return supportsNamespace();
    }

    /**
     * Build a default namespace info object for V4 compatibility.
     */
    private NamespaceInfo buildDefaultNamespace() {
        NamespaceInfo ns = new NamespaceInfo();
        ns.setNamespaceName("");
        ns.setDisplayName("DEFAULT");
        ns.setDescription("Default namespace (V4 compatibility mode)");
        ns.setDefaultNamespace(true);
        ns.setStatus("ENABLED");
        return ns;
    }
}