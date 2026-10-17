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
package org.apache.rocketmq.studio.ops.ai.tool.contract.topic;

import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.instance.topic.BrokerRouteVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link TopicRouteItem}: the tool-side projection of one broker's route entry for a topic.
 * The enum-to-name translation is the subtle half - the perm travels as its wire name (the tool
 * contract is JSON), and a perm the enum does not know survives as null rather than crashing the
 * whole route listing.
 */
class TopicRouteItemTest {

    @Test
    void mapsEveryRouteFieldThrough() {
        BrokerRouteVO source = BrokerRouteVO.builder()
                .brokerName("broker-a")
                .brokerAddr("10.0.0.1:10911")
                .masterAddr("10.0.0.1:10911")
                .brokerAddrs(Map.of(0L, "10.0.0.1:10911", 1L, "10.0.0.2:10911"))
                .brokerIds(List.of(0L, 1L))
                .replicaCount(2)
                .writeQueues(8)
                .readQueues(8)
                .perm(TopicPerm.RW)
                .permCode(6)
                .readable(true)
                .writable(true)
                .topicSysFlag(0)
                .build();

        TopicRouteItem item = TopicRouteItem.from(source);

        assertThat(item.brokerName()).isEqualTo("broker-a");
        assertThat(item.brokerAddr()).isEqualTo("10.0.0.1:10911");
        assertThat(item.masterAddr()).isEqualTo("10.0.0.1:10911");
        assertThat(item.brokerAddrs()).containsEntry(1L, "10.0.0.2:10911");
        assertThat(item.brokerIds()).containsExactly(0L, 1L);
        assertThat(item.replicaCount()).isEqualTo(2);
        assertThat(item.writeQueues()).isEqualTo(8);
        assertThat(item.readQueues()).isEqualTo(8);
        assertThat(item.perm()).isEqualTo("RW");
        assertThat(item.permCode()).isEqualTo(6);
        assertThat(item.readable()).isTrue();
        assertThat(item.writable()).isTrue();
        assertThat(item.topicSysFlag()).isZero();
    }

    @Test
    void thePermTravelsAsItsWireName() {
        BrokerRouteVO ro = BrokerRouteVO.builder().perm(TopicPerm.RO).build();
        BrokerRouteVO wo = BrokerRouteVO.builder().perm(TopicPerm.WO).build();

        assertThat(TopicRouteItem.from(ro).perm()).isEqualTo("RO");
        assertThat(TopicRouteItem.from(wo).perm()).isEqualTo("WO");
    }

    @Test
    void anUnknownPermSurvivesAsNullNotACrash() {
        // a broker advertising a perm this build's enum does not know must
        // not cost the whole route listing
        BrokerRouteVO source = BrokerRouteVO.builder().brokerName("broker-a").build();

        TopicRouteItem item = TopicRouteItem.from(source);

        assertThat(item.perm()).isNull();
        assertThat(item.brokerName()).isEqualTo("broker-a");
    }
}
