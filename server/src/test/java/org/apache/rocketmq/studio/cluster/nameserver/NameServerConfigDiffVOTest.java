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
package org.apache.rocketmq.studio.cluster.nameserver;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the NameServer config diff of {@link NameServerConfigDiffVO}: the cluster-wide
 * comparison summary with its nested per-node reachability rows and per-key value rows, and a
 * fresh diff reading as incomplete, drift-free and empty.
 */
class NameServerConfigDiffVOTest {

    @Test
    void theDiffSummaryRoundTrips() {
        NameServerConfigDiffVO.NodeStatusVO node = NameServerConfigDiffVO.NodeStatusVO.builder()
                .address("10.0.0.1:9876")
                .reachable(true)
                .build();
        NameServerConfigDiffVO.ConfigValueVO value = NameServerConfigDiffVO.ConfigValueVO.builder()
                .address("10.0.0.1:9876")
                .configured(true)
                .value("5000")
                .build();
        NameServerConfigDiffVO.ConfigDifferenceVO difference = NameServerConfigDiffVO.ConfigDifferenceVO.builder()
                .key("orderMessageTimeout")
                .values(List.of(value))
                .build();

        NameServerConfigDiffVO diff = NameServerConfigDiffVO.builder()
                .cluster("cluster-1")
                .complete(true)
                .driftDetected(true)
                .nodeCount(2)
                .reachableNodeCount(2)
                .comparedKeys(List.of("orderMessageTimeout"))
                .nodes(List.of(node))
                .differences(List.of(difference))
                .build();

        assertThat(diff.getCluster()).isEqualTo("cluster-1");
        assertThat(diff.isComplete()).isTrue();
        assertThat(diff.isDriftDetected()).isTrue();
        assertThat(diff.getNodeCount()).isEqualTo(2);
        assertThat(diff.getReachableNodeCount()).isEqualTo(2);
        assertThat(diff.getComparedKeys()).containsExactly("orderMessageTimeout");
        assertThat(diff.getNodes()).hasSize(1);
        assertThat(diff.getNodes().get(0).getAddress()).isEqualTo("10.0.0.1:9876");
        assertThat(diff.getDifferences()).hasSize(1);
        assertThat(diff.getDifferences().get(0).getKey()).isEqualTo("orderMessageTimeout");
        assertThat(diff.getDifferences().get(0).getValues()).hasSize(1);
    }

    @Test
    void aFreshDiffIsIncompleteDriftFreeAndEmpty() {
        NameServerConfigDiffVO diff = new NameServerConfigDiffVO();
        assertThat(diff.isComplete()).isFalse();
        assertThat(diff.isDriftDetected()).isFalse();
        assertThat(diff.getNodeCount()).isZero();
        assertThat(diff.getNodes()).isNull();
        assertThat(diff.getDifferences()).isNull();
    }
}
