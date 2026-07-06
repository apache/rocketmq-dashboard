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
package org.apache.rocketmq.dashboard.util;

import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ACL version detection utility
 * Automatically detects and adapts to ACL 1.0 or ACL 2.0 based on cluster capabilities
 */
@Component
public class AclVersionDetector {

    private static final Logger log = LoggerFactory.getLogger(AclVersionDetector.class);

    /**
     * ACL 1.0 capabilities
     */
    private static final String ACL_1_0 = "ACL_1_0";

    /**
     * ACL 2.0 capabilities
     */
    private static final String ACL_2_0 = "ACL_2_0";

    /**
     * Mixed mode for transition period
     */
    private static final String ACL_MIXED = "ACL_MIXED";

    /**
     * Detect ACL version from cluster capability
     */
    public String detectAclVersion(ClusterCapability capability) {
        if (capability == null) {
            log.warn("Cluster capability is null, defaulting to no ACL support");
            return "NONE";
        }

        // Check ACL 2.0 support via dedicated boolean flag
        boolean supportsAcl2 = capability.isAclV2Supported();

        // Check ACL 1.0 support via extended capabilities or architecture version
        boolean supportsAcl1 = capability.hasCapability(ACL_1_0)
            || "4.0".equals(capability.getArchitectureVersion());

        // Check mixed mode via extended capabilities
        boolean mixedMode = capability.hasCapability(ACL_MIXED);

        if (supportsAcl2 && supportsAcl1) {
            log.info("Detected mixed ACL mode (both 1.0 and 2.0 supported)");
            return ACL_MIXED;
        } else if (supportsAcl2) {
            log.info("Detected ACL 2.0 support");
            return ACL_2_0;
        } else if (supportsAcl1) {
            log.info("Detected ACL 1.0 support");
            return ACL_1_0;
        } else {
            log.warn("No ACL support detected in cluster");
            return "NONE";
        }
    }

    /**
     * Check if cluster supports ACL 1.0
     */
    public boolean supportsAcl1(ClusterCapability capability) {
        return capability != null && (capability.hasCapability(ACL_1_0)
            || "4.0".equals(capability.getArchitectureVersion()));
    }

    /**
     * Check if cluster supports ACL 2.0
     */
    public boolean supportsAcl2(ClusterCapability capability) {
        return capability != null && capability.isAclV2Supported();
    }

    /**
     * Check if cluster is in mixed ACL mode
     */
    public boolean isMixedMode(ClusterCapability capability) {
        return capability != null && capability.hasCapability(ACL_MIXED);
    }

    /**
     * Get ACL migration status information
     */
    public AclMigrationInfo getMigrationInfo(ClusterCapability capability) {
        AclMigrationInfo info = new AclMigrationInfo();

        String version = detectAclVersion(capability);
        info.setCurrentVersion(version);

        if (ACL_2_0.equals(version)) {
            info.setStatus("FULL_ACL_2_0");
            info.setDescription("Cluster fully supports ACL 2.0");
        } else if (ACL_1_0.equals(version)) {
            info.setStatus("LEGACY_ACL_1_0");
            info.setDescription("Cluster only supports legacy ACL 1.0");
        } else if (ACL_MIXED.equals(version)) {
            info.setStatus("MIGRATION_IN_PROGRESS");
            info.setDescription("Cluster is in ACL migration mode (both 1.0 and 2.0 enabled)");
        } else {
            info.setStatus("NO_ACL_SUPPORT");
            info.setDescription("Cluster does not support ACL");
        }

        return info;
    }

    /**
     * ACL migration information
     */
    public static class AclMigrationInfo {
        private String currentVersion;
        private String status;
        private String description;

        public String getCurrentVersion() {
            return currentVersion;
        }

        public void setCurrentVersion(String currentVersion) {
            this.currentVersion = currentVersion;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return String.format("AclMigrationInfo{currentVersion='%s', status='%s', description='%s'}",
                currentVersion, status, description);
        }
    }
}