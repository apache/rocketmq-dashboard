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
package org.apache.rocketmq.studio.ops.ai.tool.contract.broker;

import org.apache.rocketmq.studio.cluster.config.BrokerConfigDiffVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link BrokerConfigOutput}: the projection of a broker config drift scan. The null-safe
 * list handling matters because the scan legitimately produces no brokers (an unreachable
 * cluster) or no differences (a consistent cluster), and the tool contract promises empty lists,
 * never nulls.
 */
class BrokerConfigOutputTest {

    @Test
    void mapsTheDriftVerdictCountsAndNestedDifferences() {
        BrokerConfigDiffVO diff = BrokerConfigDiffVO.builder()
                .cluster("cluster-a")
                .complete(true)
                .driftDetected(true)
                .brokerCount(2)
                .reachableBrokerCount(1)
                .comparedFields(List.of("sendMessageThreadPoolNums", "flushDiskType"))
                .brokers(List.of(
                        BrokerConfigDiffVO.BrokerStatusVO.builder()
                                .name("broker-a").address("10.0.0.1:10911")
                                .reachable(true).build(),
                        BrokerConfigDiffVO.BrokerStatusVO.builder()
                                .name("broker-b").address("10.0.0.2:10911")
                                .reachable(false).message("connect timed out").build()))
                .differences(List.of(BrokerConfigDiffVO.ConfigDifferenceVO.builder()
                        .field("sendMessageThreadPoolNums")
                        .brokerProperty("sendMessageThreadPoolNums")
                        .values(List.of(
                                BrokerConfigDiffVO.ConfigValueVO.builder()
                                        .brokerName("broker-a").address("10.0.0.1:10911")
                                        .configured(true).value("64").build(),
                                BrokerConfigDiffVO.ConfigValueVO.builder()
                                        .brokerName("broker-b").address("10.0.0.2:10911")
                                        .configured(false).build()))
                        .build()))
                .build();

        BrokerConfigOutput output = BrokerConfigOutput.from(diff);

        assertThat(output.cluster()).isEqualTo("cluster-a");
        assertThat(output.complete()).isTrue();
        assertThat(output.driftDetected()).isTrue();
        assertThat(output.brokerCount()).isEqualTo(2);
        assertThat(output.reachableBrokerCount()).isEqualTo(1);
        assertThat(output.comparedFields()).containsExactly(
                "sendMessageThreadPoolNums", "flushDiskType");
        assertThat(output.brokers()).hasSize(2);
        assertThat(output.brokers().get(1).name()).isEqualTo("broker-b");
        assertThat(output.brokers().get(1).reachable()).isFalse();
        assertThat(output.brokers().get(1).message()).isEqualTo("connect timed out");
        assertThat(output.differences()).singleElement().satisfies(difference -> {
            assertThat(difference.field()).isEqualTo("sendMessageThreadPoolNums");
            assertThat(difference.values()).hasSize(2);
            assertThat(difference.values().get(0).value()).isEqualTo("64");
            assertThat(difference.values().get(1).configured()).isFalse();
            assertThat(difference.values().get(1).value()).isNull();
        });
    }

    /**
     * The scan of an unreachable cluster legitimately has no broker list: the contract promises an
     * empty list, never a null the tool serializer would have to defend against.
     */
    @Test
    void aMissingBrokerListProjectsToAnEmptyList() {
        BrokerConfigOutput output = BrokerConfigOutput.from(BrokerConfigDiffVO.builder()
                .cluster("cluster-a").complete(false).brokerCount(0).reachableBrokerCount(0)
                .differences(List.of()).build());

        assertThat(output.brokers()).isEmpty();
    }

    @Test
    void aMissingDifferenceListProjectsToAnEmptyList() {
        BrokerConfigOutput output = BrokerConfigOutput.from(BrokerConfigDiffVO.builder()
                .cluster("cluster-a").complete(true).driftDetected(false)
                .brokers(List.of()).build());

        assertThat(output.differences()).isEmpty();
    }

    @Test
    void aMissingValueListInsideADifferenceProjectsToAnEmptyList() {
        BrokerConfigOutput output = BrokerConfigOutput.from(BrokerConfigDiffVO.builder()
                .cluster("cluster-a")
                .differences(List.of(BrokerConfigDiffVO.ConfigDifferenceVO.builder()
                        .field("sendMessageThreadPoolNums").brokerProperty("sendMessageThreadPoolNums")
                        .values(null).build()))
                .build());

        assertThat(output.differences()).singleElement()
                .extracting(BrokerConfigOutput.ConfigDifference::values)
                .satisfies(values -> assertThat(values).isEmpty());
    }
}
