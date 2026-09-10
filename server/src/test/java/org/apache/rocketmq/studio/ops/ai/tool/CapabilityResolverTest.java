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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapabilityResolverTest {

    @Mock
    private ClusterService clusterService;

    private CapabilityResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CapabilityResolver(clusterService);
    }

    private ClusterVO cluster(ClusterType type) {
        return ClusterVO.builder().id("cluster-a").type(type).build();
    }

    @Test
    void resolvesV4DirectCapabilities() {
        when(clusterService.getCluster("cluster-a")).thenReturn(cluster(ClusterType.V4_DIRECT));

        List<String> capabilities = resolver.resolve("cluster-a");

        assertThat(capabilities).containsExactly("REMOTING", "ROCKETMQ_4");
    }

    @Test
    void resolvesV5ProxyCapabilities() {
        assertThat(resolver.resolve(cluster(ClusterType.V5_PROXY_LOCAL)))
                .contains("GRPC", "LOCAL_PROXY", "POP", "ROCKETMQ_5");
        assertThat(resolver.resolve(cluster(ClusterType.V5_PROXY_CLUSTER)))
                .contains("GRPC", "CLUSTER_PROXY", "POP", "ROCKETMQ_5");
    }

    @Test
    void rejectsNullAndTypelessClusters() {
        assertThatThrownBy(() -> resolver.resolve((ClusterVO) null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
        ClusterVO typeless = ClusterVO.builder().id("cluster-a").build();
        assertThatThrownBy(() -> resolver.resolve(typeless))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("unavailable");
    }
}
