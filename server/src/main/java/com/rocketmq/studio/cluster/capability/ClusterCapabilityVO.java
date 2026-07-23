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
package com.rocketmq.studio.cluster.capability;

import com.fasterxml.jackson.annotation.JsonValue;
import com.rocketmq.studio.common.domain.enums.ClusterType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ClusterCapabilityVO {
    private static final String SCHEMA_VERSION = "v1";
    private static final String SOURCE = "configured-default";

    private final String clusterId;
    private final String accessType;
    private final Map<ClusterCapability, CapabilityStatus> capabilities;

    public ClusterCapabilityVO(
            String clusterId,
            ClusterType clusterType,
            Map<ClusterCapability, CapabilityStatus> configuredCapabilities) {
        this.clusterId = clusterId;
        this.accessType = mapAccessType(clusterType);

        EnumMap<ClusterCapability, CapabilityStatus> normalized =
                new EnumMap<>(ClusterCapability.class);
        for (ClusterCapability capability : ClusterCapability.values()) {
            CapabilityStatus status =
                    configuredCapabilities == null ? null : configuredCapabilities.get(capability);
            normalized.put(capability, status == null ? CapabilityStatus.unknown() : status);
        }
        this.capabilities = Collections.unmodifiableMap(normalized);
    }

    public String getSchemaVersion() {
        return SCHEMA_VERSION;
    }

    public String getClusterId() {
        return clusterId;
    }

    public String getAccessType() {
        return accessType;
    }

    public String getSource() {
        return SOURCE;
    }

    public Map<ClusterCapability, CapabilityStatus> getCapabilities() {
        return capabilities;
    }

    private static String mapAccessType(ClusterType clusterType) {
        if (clusterType == null) {
            return "unknown";
        }
        return switch (clusterType) {
            case V4_DIRECT -> "v4-namesrv";
            case V5_PROXY_LOCAL -> "v5-proxy-local";
            case V5_PROXY_CLUSTER -> "v5-proxy-cluster";
        };
    }

    public enum CapabilityState {
        SUPPORTED("supported"),
        UNSUPPORTED("unsupported"),
        UNKNOWN("unknown");

        private final String wireName;

        CapabilityState(String wireName) {
            this.wireName = wireName;
        }

        @JsonValue
        public String getWireName() {
            return wireName;
        }
    }

    public static final class CapabilityStatus {
        private final CapabilityState state;
        private final String reason;

        private CapabilityStatus(CapabilityState state, String reason) {
            this.state = state;
            this.reason = reason;
        }

        public static CapabilityStatus unsupported(String reason) {
            return new CapabilityStatus(CapabilityState.UNSUPPORTED, reason);
        }

        public static CapabilityStatus unknown() {
            return new CapabilityStatus(CapabilityState.UNKNOWN, null);
        }

        public CapabilityState getState() {
            return state;
        }

        public String getReason() {
            return reason;
        }
    }
}
