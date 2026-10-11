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
package org.apache.rocketmq.studio.instance.topic;

import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the broker route of {@link BrokerRouteVO}: one broker's addresses (including the
 * replica map and ids), its queue counts and its effective permissions - both the enum form
 * and the computed readable/writable flags the route table renders.
 */
class BrokerRouteVOTest {

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        BrokerRouteVO route = BrokerRouteVO.builder()
                .brokerName("broker-a")
                .brokerAddr("10.0.0.1:10911")
                .masterAddr("10.0.0.1:10911")
                .brokerAddrs(Map.of(0L, "10.0.0.1:10911"))
                .brokerIds(List.of(0L))
                .replicaCount(1)
                .writeQueues(8)
                .readQueues(8)
                .perm(TopicPerm.RW)
                .permCode(6)
                .readable(true)
                .writable(true)
                .build();

        assertThat(route.getBrokerName()).isEqualTo("broker-a");
        assertThat(route.getBrokerAddr()).isEqualTo("10.0.0.1:10911");
        assertThat(route.getBrokerAddrs()).containsEntry(0L, "10.0.0.1:10911");
        assertThat(route.getBrokerIds()).containsExactly(0L);
        assertThat(route.getReplicaCount()).isEqualTo(1);
        assertThat(route.getWriteQueues()).isEqualTo(8);
        assertThat(route.getReadQueues()).isEqualTo(8);
        assertThat(route.getPerm()).isEqualTo(TopicPerm.RW);
        assertThat(route.getPermCode()).isEqualTo(6);
        assertThat(route.isReadable()).isTrue();
        assertThat(route.isWritable()).isTrue();
    }

    @Test
    void aFreshRouteIsNeitherReadableNorWritable() {
        BrokerRouteVO route = new BrokerRouteVO();
        assertThat(route.isReadable()).isFalse();
        assertThat(route.isWritable()).isFalse();
    }
}
