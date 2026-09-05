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
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CapabilityResolver}: each cluster type exposes the capability set
 * that drives which AI tools are offered, and a cluster without a resolvable type is
 * rejected instead of silently narrowing the tool surface.
 */
@ExtendWith(MockitoExtension.class)
class CapabilityResolverTest {

    @Mock
    private ClusterService clusterService;

    @InjectMocks
    private CapabilityResolver capabilityResolver;

    @Test
    void resolvesCapabilitiesForEachClusterType() {
        assertThat(capabilitiesFor(ClusterType.V4_DIRECT)).containsExactly("REMOTING", "ROCKETMQ_4");
        assertThat(capabilitiesFor(ClusterType.V5_PROXY_LOCAL)).containsExactly(
                "ACL_V2", "GRPC", "LITE_TOPIC", "LOCAL_PROXY", "POP", "REMOTING", "ROCKETMQ_5");
        assertThat(capabilitiesFor(ClusterType.V5_PROXY_CLUSTER)).containsExactly(
                "ACL_V2", "CLUSTER_PROXY", "GRPC", "LITE_TOPIC", "POP", "REMOTING", "ROCKETMQ_5");
    }

    @Test
    void resolvesCapabilitiesFromAClusterId() {
        ClusterVO cluster = ClusterVO.builder().id("c1").type(ClusterType.V4_DIRECT).build();
        when(clusterService.getCluster("c1")).thenReturn(cluster);

        assertThat(capabilityResolver.resolve("c1")).containsExactly("REMOTING", "ROCKETMQ_4");
    }

    @Test
    void rejectsClustersWithoutAResolvableType() {
        ClusterVO cluster = ClusterVO.builder().id("unknown").build();

        assertThatThrownBy(() -> capabilityResolver.resolve(cluster))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(400));
    }

    private List<String> capabilitiesFor(ClusterType type) {
        return capabilityResolver.resolve(ClusterVO.builder().id("c").type(type).build());
    }
}
