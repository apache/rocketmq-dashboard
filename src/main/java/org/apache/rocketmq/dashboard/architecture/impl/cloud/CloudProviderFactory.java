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
package org.apache.rocketmq.dashboard.architecture.impl.cloud;

import org.apache.rocketmq.dashboard.architecture.AdminClient;
import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.model.CloudProviderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating cloud provider components (ClusterProvider, MetadataProvider, AdminClient).
 *
 * <p>Per RIP-1 ARCH-01 §5.1 M1, this factory supports two provider discovery modes:</p>
 * <ol>
 *   <li><b>Built-in providers</b>: Aliyun, Tencent Cloud, Huawei Cloud adapters
 *       that are bundled with the dashboard distribution.</li>
 *   <li><b>SPI providers</b>: Custom cloud provider adapters discovered via
 *       {@link ServiceLoader} mechanism, allowing third-party extensions.</li>
 * </ol>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * CloudProviderConfig config = new CloudProviderConfig();
 * config.setProviderType("cloud-aliyun");
 * config.setInstanceId("rmq-cn-xxxxx");
 * config.setRegionId("cn-hangzhou");
 *
 * CloudProviderFactory.CloudProviderBundle bundle =
 *     CloudProviderFactory.createBundle(config);
 *
 * ClusterProvider clusterProvider = bundle.getClusterProvider();
 * MetadataProvider metadataProvider = bundle.getMetadataProvider();
 * AdminClient adminClient = bundle.getAdminClient();
 * }</pre>
 *
 * <h3>Thread Safety</h3>
 * <p>This factory is thread-safe. Provider instances are cached by instance ID
 * to avoid redundant creation.</p>
 */
public class CloudProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(CloudProviderFactory.class);

    /** Supported built-in cloud provider type prefixes. */
    public static final String PROVIDER_TYPE_ALIYUN = "cloud-aliyun";
    public static final String PROVIDER_TYPE_TENCENT = "cloud-tencent";
    public static final String PROVIDER_TYPE_HUAWEI = "cloud-huawei";

    /** Cache of created provider bundles by instance ID. */
    private static final Map<String, CloudProviderBundle> BUNDLE_CACHE = new ConcurrentHashMap<>();

    /** Cache of SPI-discovered CloudProviderPlugin instances. */
    private static final Map<String, CloudProviderPlugin> PLUGIN_CACHE = new ConcurrentHashMap<>();

    static {
        // Discover and cache SPI plugins at class loading time
        discoverSpiPlugins();
    }

    /**
     * Create a complete cloud provider bundle for the given configuration.
     *
     * <p>The bundle includes ClusterProvider, MetadataProvider, and AdminClient
     * instances that are pre-wired for the specified cloud provider.</p>
     *
     * @param config cloud provider configuration
     * @return bundle containing all provider components
     * @throws IllegalArgumentException if the provider type is not supported
     */
    public static CloudProviderBundle createBundle(CloudProviderConfig config) {
        if (config == null || config.getProviderType() == null) {
            throw new IllegalArgumentException("CloudProviderConfig and providerType must not be null");
        }

        String cacheKey = config.getInstanceId();
        if (cacheKey == null || cacheKey.isEmpty()) {
            cacheKey = config.getProviderType() + "-" + System.identityHashCode(config);
        }

        return BUNDLE_CACHE.computeIfAbsent(cacheKey, key -> {
            log.info("Creating cloud provider bundle for type: {}, instance: {}",
                config.getProviderType(), config.getInstanceId());
            return doCreateBundle(config);
        });
    }

    /**
     * Create only a ClusterProvider for the given configuration.
     */
    public static ClusterProvider createClusterProvider(CloudProviderConfig config) {
        return createBundle(config).getClusterProvider();
    }

    /**
     * Create only a MetadataProvider for the given configuration.
     */
    public static MetadataProvider createMetadataProvider(CloudProviderConfig config) {
        return createBundle(config).getMetadataProvider();
    }

    /**
     * Create only an AdminClient for the given configuration.
     */
    public static AdminClient createAdminClient(CloudProviderConfig config) {
        return createBundle(config).getAdminClient();
    }

    /**
     * Invalidate cached provider bundle for the given instance ID.
     * This forces re-creation on next access.
     */
    public static void invalidateCache(String instanceId) {
        CloudProviderBundle removed = BUNDLE_CACHE.remove(instanceId);
        if (removed != null) {
            log.info("Invalidated cloud provider bundle cache for instance: {}", instanceId);
            try {
                removed.getClusterProvider().shutdown();
            } catch (Exception e) {
                log.warn("Error shutting down ClusterProvider for instance: {}", instanceId, e);
            }
        }
    }

    /**
     * Clear all cached provider bundles and shutdown providers.
     */
    public static void clearCache() {
        BUNDLE_CACHE.forEach((instanceId, bundle) -> {
            try {
                bundle.getClusterProvider().shutdown();
            } catch (Exception e) {
                log.warn("Error shutting down ClusterProvider for instance: {}", instanceId, e);
            }
        });
        BUNDLE_CACHE.clear();
        log.info("Cleared all cloud provider bundle caches");
    }

    /**
     * Check if a provider type is supported (either built-in or via SPI).
     */
    public static boolean isProviderTypeSupported(String providerType) {
        if (PROVIDER_TYPE_ALIYUN.equals(providerType)
            || PROVIDER_TYPE_TENCENT.equals(providerType)
            || PROVIDER_TYPE_HUAWEI.equals(providerType)) {
            return true;
        }
        return PLUGIN_CACHE.containsKey(providerType);
    }

    // ==================== Internal Implementation ====================

    private static CloudProviderBundle doCreateBundle(CloudProviderConfig config) {
        String providerType = config.getProviderType();

        // First, check SPI plugins
        CloudProviderPlugin plugin = PLUGIN_CACHE.get(providerType);
        if (plugin != null) {
            log.info("Using SPI plugin for cloud provider type: {}", providerType);
            return plugin.createBundle(config);
        }

        // Fall back to built-in providers
        switch (providerType) {
            case PROVIDER_TYPE_ALIYUN:
                return createAliyunBundle(config);
            case PROVIDER_TYPE_TENCENT:
                return createTencentBundle(config);
            case PROVIDER_TYPE_HUAWEI:
                return createHuaweiBundle(config);
            default:
                throw new IllegalArgumentException(
                    "Unsupported cloud provider type: " + providerType
                    + ". Supported types: " + PROVIDER_TYPE_ALIYUN + ", "
                    + PROVIDER_TYPE_TENCENT + ", " + PROVIDER_TYPE_HUAWEI
                    + ", or custom SPI plugins");
        }
    }

    private static CloudProviderBundle createAliyunBundle(CloudProviderConfig config) {
        AliyunClusterProvider clusterProvider = new AliyunClusterProvider(config);
        AliyunMetadataProvider metadataProvider = new AliyunMetadataProvider(config);
        // AdminClient for cloud uses OpenAPI channel, not Remoting/gRPC
        CloudAdminClient adminClient = new CloudAdminClient(config);

        return new CloudProviderBundle(clusterProvider, metadataProvider, adminClient, config);
    }

    private static CloudProviderBundle createTencentBundle(CloudProviderConfig config) {
        TencentCloudClusterProvider clusterProvider = new TencentCloudClusterProvider(config);
        // TODO: Create TencentCloudMetadataProvider when implemented
        // For now, use a stub that throws UnsupportedOperationException
        AliyunMetadataProvider metadataProvider = new AliyunMetadataProvider(config);
        CloudAdminClient adminClient = new CloudAdminClient(config);

        log.warn("Tencent Cloud MetadataProvider is using Aliyun stub - needs dedicated implementation");
        return new CloudProviderBundle(clusterProvider, metadataProvider, adminClient, config);
    }

    private static CloudProviderBundle createHuaweiBundle(CloudProviderConfig config) {
        HuaweiCloudClusterProvider clusterProvider = new HuaweiCloudClusterProvider(config);
        // TODO: Create HuaweiCloudMetadataProvider when implemented
        AliyunMetadataProvider metadataProvider = new AliyunMetadataProvider(config);
        CloudAdminClient adminClient = new CloudAdminClient(config);

        log.warn("Huawei Cloud MetadataProvider is using Aliyun stub - needs dedicated implementation");
        return new CloudProviderBundle(clusterProvider, metadataProvider, adminClient, config);
    }

    private static void discoverSpiPlugins() {
        try {
            ServiceLoader<CloudProviderPlugin> loader = ServiceLoader.load(CloudProviderPlugin.class);
            for (CloudProviderPlugin plugin : loader) {
                String type = plugin.getProviderType();
                if (type != null && !type.isEmpty()) {
                    PLUGIN_CACHE.put(type, plugin);
                    log.info("Discovered SPI cloud provider plugin: {} -> {}",
                        type, plugin.getClass().getName());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to discover SPI cloud provider plugins", e);
        }
    }

    // ==================== Inner Classes ====================

    /**
     * Bundle containing all provider components for a cloud instance.
     */
    public static class CloudProviderBundle {
        private final ClusterProvider clusterProvider;
        private final MetadataProvider metadataProvider;
        private final AdminClient adminClient;
        private final CloudProviderConfig config;

        public CloudProviderBundle(ClusterProvider clusterProvider,
                                   MetadataProvider metadataProvider,
                                   AdminClient adminClient,
                                   CloudProviderConfig config) {
            this.clusterProvider = clusterProvider;
            this.metadataProvider = metadataProvider;
            this.adminClient = adminClient;
            this.config = config;
        }

        public ClusterProvider getClusterProvider() {
            return clusterProvider;
        }

        public MetadataProvider getMetadataProvider() {
            return metadataProvider;
        }

        public AdminClient getAdminClient() {
            return adminClient;
        }

        public CloudProviderConfig getConfig() {
            return config;
        }
    }

    /**
     * SPI interface for third-party cloud provider plugins.
     *
     * <p>Implementations are discovered via {@link ServiceLoader}. Each plugin
     * must declare a unique provider type via {@link #getProviderType()}.</p>
     */
    public interface CloudProviderPlugin {
        /** Return the unique provider type identifier (e.g., "cloud-aws"). */
        String getProviderType();

        /** Create a complete provider bundle for the given configuration. */
        CloudProviderBundle createBundle(CloudProviderConfig config);
    }
}