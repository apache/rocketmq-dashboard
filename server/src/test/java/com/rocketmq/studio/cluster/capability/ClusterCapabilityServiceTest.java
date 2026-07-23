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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketmq.studio.cluster.capability.ClusterCapabilityVO.CapabilityState;
import com.rocketmq.studio.cluster.capability.ClusterCapabilityVO.CapabilityStatus;
import com.rocketmq.studio.common.domain.enums.ClusterType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClusterCapabilityServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldNormalizePartialCapabilityMapAndExposeItAsImmutable() {
        Map<ClusterCapability, CapabilityStatus> configured = new EnumMap<>(ClusterCapability.class);
        configured.put(ClusterCapability.LITE_TOPIC, CapabilityStatus.unsupported("Lite topic is disabled"));
        configured.put(ClusterCapability.REMOTING_ADMIN, null);

        ClusterCapabilityVO capabilityVO =
                new ClusterCapabilityVO("cluster-a", ClusterType.V5_PROXY_LOCAL, configured);
        configured.clear();

        assertThat(capabilityVO.getCapabilities()).hasSize(ClusterCapability.values().length);
        assertThat(capabilityVO.getCapabilities().get(ClusterCapability.METADATA_READ).getState())
                .isEqualTo(CapabilityState.UNKNOWN);
        assertThat(capabilityVO.getCapabilities().get(ClusterCapability.REMOTING_ADMIN).getState())
                .isEqualTo(CapabilityState.UNKNOWN);
        assertThat(capabilityVO.getCapabilities().get(ClusterCapability.LITE_TOPIC).getState())
                .isEqualTo(CapabilityState.UNSUPPORTED);
        assertThat(capabilityVO.getCapabilities().get(ClusterCapability.LITE_TOPIC).getReason())
                .isEqualTo("Lite topic is disabled");
        assertThatThrownBy(() -> capabilityVO.getCapabilities()
                .put(ClusterCapability.METADATA_READ, CapabilityStatus.unknown()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldExposeCapabilityCatalogInExactContractOrder() {
        assertThat(ClusterCapability.values()).containsExactly(
                ClusterCapability.METADATA_READ,
                ClusterCapability.METADATA_WRITE,
                ClusterCapability.NAMESPACE,
                ClusterCapability.TYPED_TOPIC,
                ClusterCapability.LITE_TOPIC,
                ClusterCapability.REMOTING_ADMIN,
                ClusterCapability.GRPC_ADMIN,
                ClusterCapability.POP_CONSUME,
                ClusterCapability.BATCH_CONSUME,
                ClusterCapability.ACL_1,
                ClusterCapability.ACL_2,
                ClusterCapability.CLIENT_RUNTIME_DETAIL,
                ClusterCapability.PROMETHEUS_METRICS);
        assertThat(Arrays.stream(ClusterCapability.values()).map(ClusterCapability::getWireName))
                .containsExactly(
                        "metadata-read",
                        "metadata-write",
                        "namespace",
                        "typed-topic",
                        "lite-topic",
                        "remoting-admin",
                        "grpc-admin",
                        "pop-consume",
                        "batch-consume",
                        "acl-1",
                        "acl-2",
                        "client-runtime-detail",
                        "prometheus-metrics");
    }

    @Test
    void shouldSerializeCapabilityStatesWithExactWireValues() throws Exception {
        assertThat(objectMapper.writeValueAsString(CapabilityState.SUPPORTED)).isEqualTo("\"supported\"");
        assertThat(objectMapper.writeValueAsString(CapabilityState.UNSUPPORTED)).isEqualTo("\"unsupported\"");
        assertThat(objectMapper.writeValueAsString(CapabilityState.UNKNOWN)).isEqualTo("\"unknown\"");
    }

    @Test
    void shouldMapConfiguredClusterTypesToAccessTypes() {
        assertThat(newCapabilityVO(ClusterType.V4_DIRECT).getAccessType()).isEqualTo("v4-namesrv");
        assertThat(newCapabilityVO(ClusterType.V5_PROXY_LOCAL).getAccessType()).isEqualTo("v5-proxy-local");
        assertThat(newCapabilityVO(ClusterType.V5_PROXY_CLUSTER).getAccessType()).isEqualTo("v5-proxy-cluster");
        assertThat(newCapabilityVO(null).getAccessType()).isEqualTo("unknown");
    }

    @Test
    void shouldSerializeCompleteCapabilityContractWithoutSensitiveFields() throws Exception {
        Map<ClusterCapability, CapabilityStatus> configured = new EnumMap<>(ClusterCapability.class);
        configured.put(ClusterCapability.LITE_TOPIC, CapabilityStatus.unsupported("not configured"));
        ClusterCapabilityVO capabilityVO =
                new ClusterCapabilityVO("cluster-safe", ClusterType.V5_PROXY_CLUSTER, configured);

        String json = objectMapper.writeValueAsString(capabilityVO);
        JsonNode root = objectMapper.readTree(json);
        JsonNode expected = objectMapper.readTree("""
                {
                  "schemaVersion": "v1",
                  "clusterId": "cluster-safe",
                  "accessType": "v5-proxy-cluster",
                  "source": "configured-default",
                  "capabilities": {
                    "metadata-read": {"state": "unknown", "reason": null},
                    "metadata-write": {"state": "unknown", "reason": null},
                    "namespace": {"state": "unknown", "reason": null},
                    "typed-topic": {"state": "unknown", "reason": null},
                    "lite-topic": {"state": "unsupported", "reason": "not configured"},
                    "remoting-admin": {"state": "unknown", "reason": null},
                    "grpc-admin": {"state": "unknown", "reason": null},
                    "pop-consume": {"state": "unknown", "reason": null},
                    "batch-consume": {"state": "unknown", "reason": null},
                    "acl-1": {"state": "unknown", "reason": null},
                    "acl-2": {"state": "unknown", "reason": null},
                    "client-runtime-detail": {"state": "unknown", "reason": null},
                    "prometheus-metrics": {"state": "unknown", "reason": null}
                  }
                }
                """);

        assertThat(root).isEqualTo(expected);
        List<String> capabilityWireNames = new ArrayList<>();
        root.path("capabilities").fieldNames().forEachRemaining(capabilityWireNames::add);
        assertThat(capabilityWireNames).containsExactly(
                "metadata-read",
                "metadata-write",
                "namespace",
                "typed-topic",
                "lite-topic",
                "remoting-admin",
                "grpc-admin",
                "pop-consume",
                "batch-consume",
                "acl-1",
                "acl-2",
                "client-runtime-detail",
                "prometheus-metrics");

        String lowercaseJson = json.toLowerCase(Locale.ROOT);
        assertThat(lowercaseJson)
                .doesNotContain(
                        "endpoint",
                        "nameserveraddress",
                        "proxyaddress",
                        "username",
                        "password",
                        "token",
                        "accesskey",
                        "secretkey",
                        "credential",
                        "super-secret-value");
    }

    private ClusterCapabilityVO newCapabilityVO(ClusterType clusterType) {
        return new ClusterCapabilityVO("cluster-a", clusterType, Collections.emptyMap());
    }
}
