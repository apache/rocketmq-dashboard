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
package org.apache.rocketmq.studio.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClusterTopologyTest {

    @Test
    void addNodeShouldRouteEachSupportedType() {
        ClusterTopology topology = new ClusterTopology();

        topology.addNode("namesrv-a", null, "10.0.0.1:9876", "NAMESRV");
        topology.addNode("broker-a", 0L, "10.0.0.2:10911", "BROKER");
        topology.addNode("proxy-a", null, "10.0.0.3:8080", "PROXY");

        assertThat(topology.getNamesrvNodes()).hasSize(1);
        assertThat(topology.getBrokerNodes()).hasSize(1);
        assertThat(topology.getProxyNodes()).hasSize(1);
        assertThat(topology.getNodeMap()).hasSize(3);
        assertThat(topology.getTotalNodeCount()).isEqualTo(3);
    }

    @Test
    void addNodeShouldRejectUnknownTypeWithoutChangingTopology() {
        ClusterTopology topology = new ClusterTopology();
        topology.addNode("broker-a", 0L, "10.0.0.2:10911", "BROKER");

        assertThatThrownBy(() -> topology.addNode("ghost", 1L, "10.0.0.4:10911", "OTHER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported node type: OTHER");

        assertThat(topology.getNodeMap()).hasSize(1);
        assertThat(topology.getTotalNodeCount()).isEqualTo(1);
        assertThat(topology.getNodeMap()).doesNotContainKey("OTHER-ghost-1");
    }

    @Test
    void addNodeShouldRejectNullTypeWithoutChangingTopology() {
        ClusterTopology topology = new ClusterTopology();

        assertThatThrownBy(() -> topology.addNode("ghost", 1L, "10.0.0.4:10911", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported node type: null");

        assertThat(topology.getNodeMap()).isEmpty();
        assertThat(topology.getTotalNodeCount()).isZero();
    }
}
