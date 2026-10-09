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

import org.apache.rocketmq.studio.cluster.broker.BrokerVO;
import org.apache.rocketmq.studio.common.domain.enums.BrokerStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link BrokerDescribeOutput}: the single-broker full view - replica-group detail merged
 * with runtime statistics (decision 20). The runtimeStatsAvailable flag is the honest-degradation
 * seam: a broker whose runtime info could not be collected must describe itself as such rather
 * than present zeroed statistics as measured facts.
 */
class BrokerDescribeOutputTest {

    @Test
    void mapsTheReplicaGroupDetailAndRuntimeStatistics() {
        BrokerVO broker = BrokerVO.builder()
                .name("broker-a").addr("10.0.0.1:10911").version("5.3.1")
                .status(BrokerStatus.running)
                .diskUsage(0.42)
                .tpsIn(1200).tpsOut(3400)
                .putMessagesToday(9_000_000).putMessagesYesterday(8_800_000)
                .getMessagesToday(25_000_000).getMessagesYesterday(24_500_000)
                .runtimeStatsAvailable(true)
                .build();

        BrokerDescribeOutput output = BrokerDescribeOutput.from(broker);

        assertThat(output.brokerName()).isEqualTo("broker-a");
        assertThat(output.addr()).isEqualTo("10.0.0.1:10911");
        assertThat(output.version()).isEqualTo("5.3.1");
        assertThat(output.status()).isEqualTo(BrokerStatus.running);
        assertThat(output.diskUsage()).isEqualTo(0.42);
        assertThat(output.tpsIn()).isEqualTo(1200);
        assertThat(output.tpsOut()).isEqualTo(3400);
        assertThat(output.putMessagesToday()).isEqualTo(9_000_000);
        assertThat(output.putMessagesYesterday()).isEqualTo(8_800_000);
        assertThat(output.getMessagesToday()).isEqualTo(25_000_000);
        assertThat(output.getMessagesYesterday()).isEqualTo(24_500_000);
        assertThat(output.runtimeStatsAvailable()).isTrue();
    }

    @Test
    void theRuntimeStatsFlagTravelsUntouched() {
        // the honest-degradation seam: false means the zeroed counters are
        // "not collected", not "measured zero"
        BrokerVO degraded = BrokerVO.builder()
                .name("broker-b").runtimeStatsAvailable(false)
                .tpsIn(0).tpsOut(0)
                .build();

        BrokerDescribeOutput output = BrokerDescribeOutput.from(degraded);

        assertThat(output.runtimeStatsAvailable()).isFalse();
        assertThat(output.tpsIn()).isZero();
        assertThat(output.tpsOut()).isZero();
    }

    @Test
    void aMaintenanceBrokerCarriesItsStatus() {
        BrokerVO broker = BrokerVO.builder()
                .name("broker-c").status(BrokerStatus.maintenance).build();

        assertThat(BrokerDescribeOutput.from(broker).status()).isEqualTo(BrokerStatus.maintenance);
    }
}
