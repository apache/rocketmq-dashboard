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

import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.architecture.impl.RemotingAdminClient;
import org.apache.rocketmq.dashboard.architecture.impl.V4ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.impl.V4MetadataProvider;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * RocketMQ 5.0 Control Plane architecture configuration
 *
 */
@Configuration
public class ArchitectureConfig {

    /**
     * Default Remoting AdminClient implementation (compatible with V4 cluster)
     * Currently used by default, can be dynamically switched by cluster type in the future
     */
    @Bean
    @Primary
    public org.apache.rocketmq.dashboard.architecture.AdminClient remotingAdminClient(MQAdminExt mqAdminExt) {
        return new RemotingAdminClient(mqAdminExt);
    }

    /**
     * V4 cluster provider (default implementation)
     */
    @Bean
    @Primary
    public ClusterProvider v4ClusterProvider(MQAdminExt mqAdminExt) {
        return new V4ClusterProvider(mqAdminExt);
    }

    /**
 *
     */
    @Bean
    @Primary
    public MetadataProvider v4MetadataProvider(MQAdminExt mqAdminExt) {
        return new V4MetadataProvider(mqAdminExt);
    }

    /**
 *
     */
    @Bean
    public ClusterCapabilityDetector clusterCapabilityDetector() {
        return new ClusterCapabilityDetector();
    }

    /**
 *
     */
    @Bean
    public ArchitectureAdaptationManager architectureAdaptationManager() {
        return new ArchitectureAdaptationManager();
    }

    /**
 *
     */
    public static class ClusterCapabilityDetector {
        
        /**
 *
         */
        public org.apache.rocketmq.dashboard.model.ClusterCapability detectCapability(
                org.apache.rocketmq.dashboard.architecture.ClusterProvider provider) {
            try {
                return provider.getClusterCapability();
            } catch (Exception e) {
                // Removed
                org.apache.rocketmq.dashboard.model.ClusterCapability fallback = new org.apache.rocketmq.dashboard.model.ClusterCapability();
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
 *
     */
    public static class ArchitectureAdaptationManager {
        
        private org.apache.rocketmq.dashboard.architecture.ClusterProvider clusterProvider;
        private org.apache.rocketmq.dashboard.architecture.AdminClient adminClient;
        private org.apache.rocketmq.dashboard.architecture.MetadataProvider metadataProvider;

        /**
 *
         */
        public void switchToArchitecture(org.apache.rocketmq.dashboard.architecture.ClusterAccessType accessType) {
            // Removed
            switch (accessType) {
                case V4_NAMESRV:
                    // Removed
                    break;
                case V5_PROXY_LOCAL:
                case V5_PROXY_CLUSTER:
                    // Removed
                    throw new UnsupportedOperationException("V5 architecture support coming soon");
                default:
                    throw new IllegalArgumentException("Unsupported access type: " + accessType);
            }
        }

        /**
 *
         */
        public org.apache.rocketmq.dashboard.model.ClusterCapability getCurrentCapability() {
            if (clusterProvider != null) {
                try {
                    return clusterProvider.getClusterCapability();
                } catch (Exception e) {
                    // Removed
                }
            }
            return new org.apache.rocketmq.dashboard.model.ClusterCapability();
        }

        public void setClusterProvider(org.apache.rocketmq.dashboard.architecture.ClusterProvider clusterProvider) {
            this.clusterProvider = clusterProvider;
        }

        public void setAdminClient(org.apache.rocketmq.dashboard.architecture.AdminClient adminClient) {
            this.adminClient = adminClient;
        }

        public void setMetadataProvider(org.apache.rocketmq.dashboard.architecture.MetadataProvider metadataProvider) {
            this.metadataProvider = metadataProvider;
        }
    }
}