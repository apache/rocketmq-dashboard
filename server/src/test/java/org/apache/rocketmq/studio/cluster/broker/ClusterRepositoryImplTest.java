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
package org.apache.rocketmq.studio.cluster.broker;

import org.apache.rocketmq.studio.cluster.config.ClusterConfigVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterRepositoryImplTest {

    @Test
    void findAllShouldReturnClustersInStableNameOrder() {
        ClusterRepositoryImpl repository = new ClusterRepositoryImpl(true);

        List<ClusterVO> clusters = repository.findAll();

        assertThat(clusters).extracting(ClusterVO::getName)
                .containsExactly("rmq-cluster-prod", "rmq-cluster-staging");
    }

    @Test
    void findAllShouldBeEmptyWhenDemoDataIsDisabled() {
        ClusterRepositoryImpl repository = new ClusterRepositoryImpl(false);

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void findByIdShouldReturnIndependentCopy() {
        ClusterRepositoryImpl repository = new ClusterRepositoryImpl(true);

        ClusterVO first = repository.findById("cluster-001").orElseThrow();
        first.setName("mutated");
        first.getConfig().setFileReservedTime(1);
        first.getBrokers().get(0).setName("mutated-broker");
        first.getProxies().get(0).setAddr("mutated-proxy");
        first.getNameServers().get(0).setAddr("mutated-nameserver");
        first.getTpsHistory().set(0, 999);

        ClusterVO second = repository.findById("cluster-001").orElseThrow();

        // Mutating the returned copy must not affect the cached cluster.
        assertThat(second.getName()).isEqualTo("rmq-cluster-prod");
        assertThat(first.getBrokers()).isNotSameAs(second.getBrokers());
        assertThat(first.getBrokers().get(0)).isNotSameAs(second.getBrokers().get(0));
        assertThat(second.getBrokers().get(0).getName()).isEqualTo("broker-a");
        assertThat(second.getProxies().get(0).getAddr()).isEqualTo("10.0.0.10:8081");
        assertThat(second.getNameServers().get(0).getAddr()).isEqualTo("10.0.0.20:9876");
        assertThat(first.getTpsHistory()).isNotSameAs(second.getTpsHistory());
        assertThat(second.getTpsHistory().get(0)).isEqualTo(1200);
        assertThat(second.getConfig()).isNotSameAs(first.getConfig());
        assertThat(second.getConfig().getFileReservedTime()).isEqualTo(72);
    }

    @Test
    void updateConfigShouldNotRetainCallerOwnedObject() {
        ClusterRepositoryImpl repository = new ClusterRepositoryImpl(true);
        ClusterConfigVO config = ClusterConfigVO.builder()
                .fileReservedTime(24)
                .build();

        repository.updateConfig("cluster-001", config);
        config.setFileReservedTime(1);

        assertThat(repository.findById("cluster-001").orElseThrow().getConfig().getFileReservedTime())
                .isEqualTo(24);
    }
}
