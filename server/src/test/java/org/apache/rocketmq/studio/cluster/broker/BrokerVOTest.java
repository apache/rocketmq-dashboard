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

import org.apache.rocketmq.studio.common.domain.enums.BrokerStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the broker view shape of {@link BrokerVO}: the daily counters round-trip through the
 * builder, and a broker built without runtime statistics still reads as available unless the
 * producer says otherwise (the {@code runtimeStatsAvailable} builder default).
 */
class BrokerVOTest {

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        BrokerVO broker = BrokerVO.builder()
                .name("broker-a")
                .addr("10.0.0.1:10911")
                .version("5.5.0")
                .status(BrokerStatus.running)
                .diskUsage(0.42)
                .tpsIn(1200L)
                .tpsOut(3400L)
                .putMessagesToday(100_000L)
                .putMessagesYesterday(90_000L)
                .getMessagesToday(80_000L)
                .getMessagesYesterday(70_000L)
                .build();

        assertThat(broker.getName()).isEqualTo("broker-a");
        assertThat(broker.getAddr()).isEqualTo("10.0.0.1:10911");
        assertThat(broker.getVersion()).isEqualTo("5.5.0");
        assertThat(broker.getStatus()).isEqualTo(BrokerStatus.running);
        assertThat(broker.getDiskUsage()).isEqualTo(0.42);
        assertThat(broker.getTpsIn()).isEqualTo(1200L);
        assertThat(broker.getTpsOut()).isEqualTo(3400L);
        assertThat(broker.getPutMessagesToday()).isEqualTo(100_000L);
        assertThat(broker.getPutMessagesYesterday()).isEqualTo(90_000L);
        assertThat(broker.getGetMessagesToday()).isEqualTo(80_000L);
        assertThat(broker.getGetMessagesYesterday()).isEqualTo(70_000L);
    }

    @Test
    void aBuiltBrokerDefaultsToRuntimeStatsAvailable() {
        BrokerVO broker = BrokerVO.builder().name("broker-a").build();
        assertThat(broker.isRuntimeStatsAvailable()).isTrue();
    }

    @Test
    void anEmptyBrokerReportsZeroedCounters() {
        BrokerVO broker = new BrokerVO();
        assertThat(broker.getTpsIn()).isZero();
        assertThat(broker.getTpsOut()).isZero();
        assertThat(broker.getPutMessagesToday()).isZero();
        assertThat(broker.getDiskUsage()).isZero();
        assertThat(broker.isRuntimeStatsAvailable()).isTrue();
    }

    @Test
    void aBrokerWithoutRuntimeStatsCanBeFlagged() {
        BrokerVO broker = BrokerVO.builder()
                .name("broker-b")
                .runtimeStatsAvailable(false)
                .build();
        assertThat(broker.isRuntimeStatsAvailable()).isFalse();
    }
}
