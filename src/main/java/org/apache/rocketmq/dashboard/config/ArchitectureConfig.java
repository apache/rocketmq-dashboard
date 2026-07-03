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
package org.apache.rocketmq.dashboard.config;

import org.apache.rocketmq.dashboard.architecture.AdminClient;
import org.apache.rocketmq.dashboard.architecture.ClusterAccessType;
import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.architecture.impl.GrpcAdminClient;
import org.apache.rocketmq.dashboard.architecture.impl.RemotingAdminClient;
import org.apache.rocketmq.dashboard.architecture.impl.V4ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.impl.V4MetadataProvider;
import org.apache.rocketmq.dashboard.architecture.impl.V5ProxyClusterProvider;
import org.apache.rocketmq.dashboard.architecture.impl.V5ProxyMetadataProvider;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RocketMQ 5.0 Control Plane architecture configuration.
 *
 * <p>Manages the creation and switching of architecture-specific components:
 * <ul>
 *   <li>{@link ClusterProvider} - cluster topology discovery and capability detection</li>
 *   <li>{@link AdminClient} - protocol channel for admin operations</li>
 *   <li>{@link MetadataProvider} - domain-specific metadata management</li>
 * </ul>
 *
 * <p>Supports three architecture types as first-class citizens:
 * <ul>
 *   <li>V4_NAMESRV - 4.0 direct connection via NameServer + Broker</li>
 *   <li>V5_PROXY_LOCAL - 5.0 Proxy co-located with Broker</li>
 *   <li>V5_PROXY_CLUSTER - 5.0 independent Proxy cluster</li>
 * </ul>
 */
@Configuration
public class ArchitectureConfig {

    private static final Logger log = LoggerFactory.getLogger(ArchitectureConfig.class);

    /**
     * Default Remoting AdminClient implementation (compatible with V4 cluster).
     * Currently used by default, can be dynamically switched by cluster type.
     */
    @Bean
    @Primary
    public AdminClient remotingAdminClient(MQAdminExt mqAdminExt) {
        return new RemotingAdminClient(mqAdminExt);
    }

    /**
     * V4 cluster provider (default implementation).
     */
    @Bean
    @Primary
    public ClusterProvider v4ClusterProvider(MQAdminExt mqAdminExt) {
        return new V4ClusterProvider(mqAdminExt);
    }

    /**
     * V4 metadata provider (default implementation).
     */
    @Bean
    @Primary
    public MetadataProvider v4MetadataProvider(MQAdminExt mqAdminExt) {
        return new V4MetadataProvider(mqAdminExt);
    }

    /**
     * Cluster capability detector bean.
     */
    @Bean
    public ClusterCapabilityDetector clusterCapabilityDetector() {
        return new ClusterCapabilityDetector();
    }

    /**
     * Architecture adaptation manager - handles dynamic switching between
     * different cluster architecture types at runtime.
     */
    @Bean
    public ArchitectureAdaptationManager architectureAdaptationManager(MQAdminExt mqAdminExt) {
        return new ArchitectureAdaptationManager(mqAdminExt);
    }

    /**
     * Detects cluster capabilities from the current ClusterProvider.
     */
    public static class ClusterCapabilityDetector {

        /**
         * Detect capabilities from the given cluster provider.
         * Falls back to V4 defaults if detection fails.
         *
         * @param provider the cluster provider to probe
         * @return detected or default cluster capabilities
         */
        public ClusterCapability detectCapability(ClusterProvider provider) {
            try {
                return provider.getClusterCapability();
            } catch (Exception e) {
                log.warn("Failed to detect cluster capability, falling back to V4 defaults: {}", e.getMessage());
                ClusterCapability fallback = new ClusterCapability();
                fallback.setArchitectureVersion("4.0");
                fallback.setNamespaceSupported(false);
                fallback.setLiteTopicSupported(false);
                fallback.setPopConsumeSupported(false);
                fallback.setGrpcClientSupported(false);
                fallback.setAclV2Supported(false);
                fallback.setDelayMessageSupported(true);
                fallback.setTransactionMessageSupported(true);
                fallback.setFifoMessageSupported(true);
                return fallback;
            }
        }
    }

    /**
     * Manages dynamic architecture switching at runtime.
     *
     * <p>When the user switches the cluster access type (e.g., from V4_NAMESRV to
     * V5_PROXY_CLUSTER), this manager creates the appropriate Provider and AdminClient
     * implementations without requiring a server restart.</p>
     *
     * <p>Thread-safe: all state mutations are synchronized.</p>
     */
    public static class ArchitectureAdaptationManager {

        private static final Logger log = LoggerFactory.getLogger(ArchitectureAdaptationManager.class);

        private final MQAdminExt mqAdminExt;
        private final ConcurrentHashMap<ClusterAccessType, ClusterProvider> providerCache = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<ClusterAccessType, AdminClient> adminClientCache = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<ClusterAccessType, MetadataProvider> metadataProviderCache = new ConcurrentHashMap<>();

        private volatile ClusterProvider currentProvider;
        private volatile AdminClient currentAdminClient;
        private volatile MetadataProvider currentMetadataProvider;
        private volatile ClusterAccessType currentAccessType;

        public ArchitectureAdaptationManager(MQAdminExt mqAdminExt) {
            this.mqAdminExt = mqAdminExt;
            // Initialize with V4 as default
            this.currentAccessType = ClusterAccessType.V4_NAMESRV;
            this.currentProvider = new V4ClusterProvider(mqAdminExt);
            this.currentAdminClient = new RemotingAdminClient(mqAdminExt);
            this.currentMetadataProvider = new V4MetadataProvider(mqAdminExt);
        }

        /**
         * Switch to a different architecture type.
         * Creates or retrieves cached provider/client implementations for the target type.
         *
         * @param accessType the target cluster access type
         * @throws IllegalArgumentException if the access type is not supported
         */
        public synchronized void switchToArchitecture(ClusterAccessType accessType) {
            if (accessType == null) {
                throw new IllegalArgumentException("AccessType must not be null");
            }
            if (accessType.equals(currentAccessType)) {
                log.debug("Already on architecture type: {}", accessType);
                return;
            }

            log.info("Switching architecture from {} to {}", currentAccessType, accessType);

            // Shutdown previous provider if it's cached and different
            if (currentProvider != null && !providerCache.containsValue(currentProvider)) {
                try {
                    currentProvider.shutdown();
                } catch (Exception e) {
                    log.warn("Error shutting down previous provider: {}", e.getMessage());
                }
            }

            // Create or retrieve from cache
            this.currentProvider = providerCache.computeIfAbsent(accessType, this::createProvider);
            this.currentAdminClient = adminClientCache.computeIfAbsent(accessType, this::createAdminClient);
            this.currentMetadataProvider = metadataProviderCache.computeIfAbsent(accessType, this::createMetadataProvider);
            this.currentAccessType = accessType;

            // Initialize the provider
            try {
                this.currentProvider.initialize();
            } catch (Exception e) {
                log.error("Failed to initialize provider for {}: {}", accessType, e.getMessage());
                throw new RuntimeException("Provider initialization failed for " + accessType, e);
            }

            log.info("Architecture switch complete: {} (capability={})", accessType, getCurrentCapability());
        }

        /**
         * Switch to V5 Proxy architecture with explicit proxy addresses.
         *
         * @param accessType     must be V5_PROXY_LOCAL or V5_PROXY_CLUSTER
         * @param proxyAddresses array of proxy node addresses
         * @param nameSrvAddress NameServer address for Remoting fallback
         * @param namespace      optional namespace scope
         */
        public synchronized void switchToV5Proxy(ClusterAccessType accessType,
                                                   String[] proxyAddresses,
                                                   String nameSrvAddress,
                                                   Optional<String> namespace) {
            if (!accessType.isV5Architecture()) {
                throw new IllegalArgumentException("accessType must be V5 architecture, got: " + accessType);
            }

            log.info("Switching to V5 Proxy: type={}, proxies={}, namesrv={}",
                accessType, proxyAddresses, nameSrvAddress);

            // Create V5-specific provider
            V5ProxyClusterProvider v5Provider = new V5ProxyClusterProvider(proxyAddresses, nameSrvAddress, namespace);
            try {
                v5Provider.initialize();
            } catch (Exception e) {
                log.error("Failed to initialize V5ProxyClusterProvider: {}", e.getMessage());
                throw new RuntimeException("V5 Provider initialization failed", e);
            }

            // Create V5-specific admin client with Remoting fallback
            GrpcAdminClient grpcClient = new GrpcAdminClient(proxyAddresses[0], mqAdminExt);

            // Create V5-specific metadata provider
            V5ProxyMetadataProvider v5Metadata = new V5ProxyMetadataProvider(
                v5Provider.getMqAdminExt(), namespace);
            v5Metadata.setCachedCapability(v5Provider.getClusterCapability());

            // Cache and set current
            providerCache.put(accessType, v5Provider);
            adminClientCache.put(accessType, grpcClient);
            metadataProviderCache.put(accessType, v5Metadata);

            this.currentProvider = v5Provider;
            this.currentAdminClient = grpcClient;
            this.currentMetadataProvider = v5Metadata;
            this.currentAccessType = accessType;

            log.info("V5 Proxy architecture switch complete: {}", accessType);
        }

        /**
         * Get current cluster capabilities.
         */
        public ClusterCapability getCurrentCapability() {
            if (currentProvider != null) {
                try {
                    return currentProvider.getClusterCapability();
                } catch (Exception e) {
                    log.warn("Failed to get capability from current provider: {}", e.getMessage());
                }
            }
            return new ClusterCapability();
        }

        /**
         * Get current cluster access type.
         */
        public ClusterAccessType getCurrentAccessType() {
            return currentAccessType;
        }

        public ClusterProvider getClusterProvider() {
            return currentProvider;
        }

        public AdminClient getAdminClient() {
            return currentAdminClient;
        }

        public MetadataProvider getMetadataProvider() {
            return currentMetadataProvider;
        }

        public void setClusterProvider(ClusterProvider clusterProvider) {
            this.currentProvider = clusterProvider;
        }

        public void setAdminClient(AdminClient adminClient) {
            this.currentAdminClient = adminClient;
        }

        public void setMetadataProvider(MetadataProvider metadataProvider) {
            this.currentMetadataProvider = metadataProvider;
        }

        // ==================== Private factory methods ====================

        private ClusterProvider createProvider(ClusterAccessType accessType) {
            switch (accessType) {
                case V4_NAMESRV:
                    return new V4ClusterProvider(mqAdminExt);
                case V5_PROXY_LOCAL:
                case V5_PROXY_CLUSTER:
                    // V5 providers require explicit proxy addresses - use switchToV5Proxy() instead
                    throw new IllegalArgumentException(
                        "V5 Proxy architecture requires explicit proxy addresses. Use switchToV5Proxy() instead.");
                default:
                    throw new IllegalArgumentException("Unsupported access type: " + accessType);
            }
        }

        private AdminClient createAdminClient(ClusterAccessType accessType) {
            switch (accessType) {
                case V4_NAMESRV:
                    return new RemotingAdminClient(mqAdminExt);
                case V5_PROXY_LOCAL:
                case V5_PROXY_CLUSTER:
                    // V5 admin clients require explicit proxy addresses - use switchToV5Proxy() instead
                    throw new IllegalArgumentException(
                        "V5 Proxy admin client requires explicit proxy addresses. Use switchToV5Proxy() instead.");
                default:
                    throw new IllegalArgumentException("Unsupported access type: " + accessType);
            }
        }

        private MetadataProvider createMetadataProvider(ClusterAccessType accessType) {
            switch (accessType) {
                case V4_NAMESRV:
                    return new V4MetadataProvider(mqAdminExt);
                case V5_PROXY_LOCAL:
                case V5_PROXY_CLUSTER:
                    throw new IllegalArgumentException(
                        "V5 Proxy metadata provider requires explicit proxy addresses. Use switchToV5Proxy() instead.");
                default:
                    throw new IllegalArgumentException("Unsupported access type: " + accessType);
            }
        }
    }
}