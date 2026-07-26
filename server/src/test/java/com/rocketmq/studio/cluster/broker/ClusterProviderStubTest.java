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
package com.rocketmq.studio.cluster.broker;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterProviderStubTest {

    private final ClusterProviderStub provider = new ClusterProviderStub();

    @Test
    void discoverClustersShouldReturnStableClusterId() {
        List<ClusterVO> clusters = provider.discoverClusters();

        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).getId()).isEqualTo("cluster-001");
        assertThat(clusters.get(0).getBrokers()).isNotEmpty();
        assertThat(clusters.get(0).getProxies()).isNotEmpty();
        assertThat(clusters.get(0).getNameServers()).isNotEmpty();
    }

    @Test
    void refreshClusterDetailShouldPreserveRequestedClusterId() {
        ClusterVO detail = provider.refreshClusterDetail("cluster-prod");

        assertThat(detail.getId()).isEqualTo("cluster-prod");
        assertThat(detail.getName()).isEqualTo("rmq-cluster-01");
        assertThat(detail.getBrokers()).hasSize(2);
    }
}
