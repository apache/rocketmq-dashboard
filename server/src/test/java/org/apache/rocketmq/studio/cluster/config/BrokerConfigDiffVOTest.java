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
package org.apache.rocketmq.studio.cluster.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the config diff view of {@link BrokerConfigDiffVO}: the cluster-wide comparison summary -
 * completeness, drift, reachability counts, the compared fields and the per-broker status rows -
 * with a fresh diff reading as complete, drift-free and empty.
 */
class BrokerConfigDiffVOTest {

    @Test
    void theDiffSummaryRoundTrips() {
        BrokerConfigDiffVO.BrokerStatusVO broker = BrokerConfigDiffVO.BrokerStatusVO.builder()
                .name("broker-a")
                .address("10.0.0.1:10911")
                .reachable(true)
                .message("ok")
                .build();
        BrokerConfigDiffVO.ConfigDifferenceVO difference = BrokerConfigDiffVO.ConfigDifferenceVO.builder()
                .field("flushDiskType")
                .build();

        BrokerConfigDiffVO diff = BrokerConfigDiffVO.builder()
                .cluster("cluster-1")
                .complete(true)
                .driftDetected(true)
                .brokerCount(2)
                .reachableBrokerCount(2)
                .comparedFields(List.of("flushDiskType"))
                .brokers(List.of(broker))
                .differences(List.of(difference))
                .build();

        assertThat(diff.getCluster()).isEqualTo("cluster-1");
        assertThat(diff.isComplete()).isTrue();
        assertThat(diff.isDriftDetected()).isTrue();
        assertThat(diff.getBrokerCount()).isEqualTo(2);
        assertThat(diff.getReachableBrokerCount()).isEqualTo(2);
        assertThat(diff.getComparedFields()).containsExactly("flushDiskType");
        assertThat(diff.getBrokers()).hasSize(1);
        assertThat(diff.getBrokers().get(0).getName()).isEqualTo("broker-a");
        assertThat(diff.getDifferences()).hasSize(1);
        assertThat(diff.getDifferences().get(0).getField()).isEqualTo("flushDiskType");
    }

    @Test
    void aFreshDiffIsCompleteDriftFreeAndEmpty() {
        BrokerConfigDiffVO diff = new BrokerConfigDiffVO();
        assertThat(diff.isComplete()).isFalse();
        assertThat(diff.isDriftDetected()).isFalse();
        assertThat(diff.getBrokerCount()).isZero();
        assertThat(diff.getBrokers()).isNull();
        assertThat(diff.getDifferences()).isNull();
    }
}
