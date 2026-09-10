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
package org.apache.rocketmq.studio.ops.ai.tool;

import org.apache.rocketmq.studio.cluster.broker.ClusterService;
import org.apache.rocketmq.studio.cluster.broker.ClusterVO;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapabilitiesToolHandlerTest {

    @Test
    void nullClusterVersionIsEmittedAsBlankString() {
        // The Apache runtime provider reports no cluster version; the projection must not
        // emit a null into the schema-required "version" string.
        ClusterVO cluster = ClusterVO.builder()
                .name("DefaultCluster")
                .type(ClusterType.V4_DIRECT)
                .status(ClusterStatus.healthy)
                .build();
        cluster.setId("DefaultCluster");

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.getCluster("DefaultCluster")).thenReturn(cluster);
        CapabilityResolver capabilityResolver = mock(CapabilityResolver.class);
        when(capabilityResolver.resolve(cluster)).thenReturn(List.of("REMOTING"));

        Object output = new CapabilitiesToolHandler(clusterService, capabilityResolver)
                .execute(Map.of("cluster", "DefaultCluster"));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) output;
        assertThat(result.get("cluster")).isEqualTo("DefaultCluster");
        assertThat(result.get("type")).isEqualTo("V4_DIRECT");
        assertThat(result.get("version")).isEqualTo("");
        assertThat(result.get("capabilities")).isEqualTo(List.of("REMOTING"));
    }

    @Test
    void populatedClusterIsProjectedVerbatim() {
        ClusterVO cluster = ClusterVO.builder()
                .name("production-cluster")
                .type(ClusterType.V5_PROXY_CLUSTER)
                .status(ClusterStatus.healthy)
                .version("5.1.0")
                .build();
        cluster.setId("production-cluster");

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.getCluster("production-cluster")).thenReturn(cluster);
        CapabilityResolver capabilityResolver = mock(CapabilityResolver.class);
        when(capabilityResolver.resolve(cluster)).thenReturn(List.of("REMOTING", "gRPC"));

        Object output = new CapabilitiesToolHandler(clusterService, capabilityResolver)
                .execute(Map.of("cluster", "production-cluster"));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) output;
        assertThat(result.get("cluster")).isEqualTo("production-cluster");
        assertThat(result.get("type")).isEqualTo("V5_PROXY_CLUSTER");
        assertThat(result.get("version")).isEqualTo("5.1.0");
        assertThat(result.get("capabilities")).isEqualTo(List.of("REMOTING", "gRPC"));
        verify(capabilityResolver).resolve(cluster);
    }

    @Test
    void nullClusterTypeIsEmittedAsBlankString() {
        ClusterVO cluster = ClusterVO.builder()
                .name("type-less-cluster")
                .status(ClusterStatus.healthy)
                .build();
        cluster.setId("type-less-cluster");

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.getCluster("type-less-cluster")).thenReturn(cluster);
        CapabilityResolver capabilityResolver = mock(CapabilityResolver.class);
        when(capabilityResolver.resolve(cluster)).thenReturn(List.of());

        Object output = new CapabilitiesToolHandler(clusterService, capabilityResolver)
                .execute(Map.of("cluster", "type-less-cluster"));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) output;
        assertThat(result.get("type")).isEqualTo("");
        assertThat(result.get("capabilities")).isEqualTo(List.of());
    }

    @Test
    void handlerNameShouldBeStable() {
        ClusterService clusterService = mock(ClusterService.class);
        CapabilityResolver capabilityResolver = mock(CapabilityResolver.class);

        assertThat(new CapabilitiesToolHandler(clusterService, capabilityResolver).name())
                .isEqualTo("rmq.capabilities");
    }
}
