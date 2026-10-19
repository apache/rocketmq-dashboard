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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the connectivity probe result of {@link ClusterProbeResult}: the endpoint the UI shows
 * after a test-connection - the topology preview with broker names and the measured latency -
 * and a fresh result reading as not connected.
 */
class ClusterProbeResultTest {

    @Test
    void theProbeOutcomeRoundTripsThroughTheBuilder() {
        ClusterProbeResult result = ClusterProbeResult.builder()
                .connected(true)
                .namesrvAddr("10.0.0.1:9876;10.0.0.2:9876")
                .clusterName("prod")
                .brokerCount(2)
                .brokerNames(List.of("broker-a", "broker-b"))
                .elapsedMillis(42L)
                .message("2 brokers registered")
                .build();

        assertThat(result.isConnected()).isTrue();
        assertThat(result.getNamesrvAddr()).isEqualTo("10.0.0.1:9876;10.0.0.2:9876");
        assertThat(result.getClusterName()).isEqualTo("prod");
        assertThat(result.getBrokerCount()).isEqualTo(2);
        assertThat(result.getBrokerNames()).containsExactly("broker-a", "broker-b");
        assertThat(result.getElapsedMillis()).isEqualTo(42L);
        assertThat(result.getMessage()).isEqualTo("2 brokers registered");
    }

    @Test
    void aFreshResultIsNotConnected() {
        ClusterProbeResult result = ClusterProbeResult.builder().build();
        assertThat(result.isConnected()).isFalse();
        assertThat(result.getBrokerCount()).isZero();
        assertThat(result.getElapsedMillis()).isZero();
    }
}
