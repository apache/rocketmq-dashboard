/*
 * Licensed to the Apache Software Foundation (ASF) under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.instance.topic.BrokerRouteVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TopicDetailOutputTest {

    private static TopicVO topic() {
        TopicVO vo = new TopicVO();
        vo.setName("orders");
        vo.setClusterId("cluster-a");
        vo.setType(TopicType.NORMAL);
        vo.setWriteQueues(8);
        vo.setReadQueues(8);
        vo.setPerm(TopicPerm.RW);
        vo.setMessageCount(1000L);
        vo.setTps(12.5);
        vo.setConsumerGroupCount(3);
        vo.setRemark("test topic");
        return vo;
    }

    @Test
    void theTopicFieldsRoundTripFromTheSourceVO() {
        TopicDetailOutput output = TopicDetailOutput.from(topic(), "inst-1", null, null, null);

        assertThat(output.instanceId()).isEqualTo("inst-1");
        assertThat(output.name()).isEqualTo("orders");
        assertThat(output.clusterId()).isEqualTo("cluster-a");
        assertThat(output.type()).isEqualTo(TopicType.NORMAL);
        assertThat(output.writeQueues()).isEqualTo(8);
        assertThat(output.readQueues()).isEqualTo(8);
        assertThat(output.perm()).isEqualTo(TopicPerm.RW);
        assertThat(output.messageCount()).isEqualTo(1000L);
        assertThat(output.tps()).isEqualTo(12.5);
        assertThat(output.consumerGroupCount()).isEqualTo(3);
        assertThat(output.remark()).isEqualTo("test topic");
    }

    @Test
    void nullConsumerAndRouteListsBecomeEmptyNeverNull() {
        TopicDetailOutput output = TopicDetailOutput.from(topic(), "inst-1", null, null, null);

        assertThat(output.consumerGroups()).isEmpty();
        assertThat(output.routes()).isEmpty();
    }

    @Test
    void emptyQueueStatsStayNullSoNonNullRenderingOmitsTheBlock() {
        TopicDetailOutput empty = TopicDetailOutput.from(topic(), "inst-1", null, null, List.of());
        TopicDetailOutput absent = TopicDetailOutput.from(topic(), "inst-1", null, null, null);

        assertThat(empty.queueStats()).isNull();
        assertThat(absent.queueStats()).isNull();
    }

    @Test
    void presentQueueStatsAreCopiedIntoAnImmutableList() {
        TopicQueueStatsItem item = new TopicQueueStatsItem("broker-a", 0, 0L, 100L, 4L);
        List<TopicQueueStatsItem> source = new java.util.ArrayList<>(List.of(item));

        TopicDetailOutput output = TopicDetailOutput.from(topic(), "inst-1", null, null, source);
        source.add(new TopicQueueStatsItem("broker-b", 0, 0L, 100L, 4L));

        assertThat(output.queueStats()).hasSize(1);
        assertThat(output.queueStats()).containsExactly(item);
    }

    @Test
    void consumerGroupsCarryTheirMetricsAvailabilityFlag() {
        TopicConsumerVO available = TopicConsumerVO.builder()
                .group("g1").consumeType(ConsumeType.CLUSTERING).messageModel("CLUSTERING")
                .consumeTps(3.0).diffTotal(10L).metricsAvailable(true).build();
        TopicConsumerVO unavailable = TopicConsumerVO.builder()
                .group("g2").consumeType(null).messageModel("BROADCASTING")
                .consumeTps(0.0).diffTotal(-1L).metricsAvailable(false).build();

        TopicDetailOutput output = TopicDetailOutput.from(
                topic(), "inst-1", List.of(available, unavailable), null, null);

        assertThat(output.consumerGroups()).hasSize(2);
        TopicDetailOutput.ConsumerGroup first = output.consumerGroups().get(0);
        assertThat(first.group()).isEqualTo("g1");
        assertThat(first.consumeType()).isEqualTo("CLUSTERING");
        assertThat(first.metricsAvailable()).isTrue();
        assertThat(first.diffTotal()).isEqualTo(10L);
        TopicDetailOutput.ConsumerGroup second = output.consumerGroups().get(1);
        // A null consume type stays null (omitted from JSON), never stringified.
        assertThat(second.consumeType()).isNull();
        assertThat(second.metricsAvailable()).isFalse();
        assertThat(second.diffTotal()).isEqualTo(-1L);
    }

    @Test
    void routesRenderThePermissionBothAsNameAndCode() {
        BrokerRouteVO route = BrokerRouteVO.builder()
                .brokerName("broker-a").brokerAddr("10.0.0.1:10911").masterAddr("10.0.0.1:10911")
                .brokerAddrs(Map.of(0L, "10.0.0.1:10911")).brokerIds(List.of(0L))
                .replicaCount(1).writeQueues(8).readQueues(8)
                .perm(TopicPerm.RW).permCode(6).readable(true).writable(true)
                .topicSysFlag(0).build();

        TopicDetailOutput output = TopicDetailOutput.from(topic(), "inst-1", null, List.of(route), null);

        TopicDetailOutput.Route rendered = output.routes().get(0);
        assertThat(rendered.brokerName()).isEqualTo("broker-a");
        assertThat(rendered.perm()).isEqualTo("RW");
        assertThat(rendered.permCode()).isEqualTo(6);
        assertThat(rendered.readable()).isTrue();
        assertThat(rendered.writable()).isTrue();
        assertThat(rendered.brokerAddrs()).containsEntry(0L, "10.0.0.1:10911");
    }

    @Test
    void aNullRoutePermissionStaysNullInsteadOfFailing() {
        BrokerRouteVO route = BrokerRouteVO.builder()
                .brokerName("broker-a").brokerAddrs(Map.of()).brokerIds(List.of()).build();

        TopicDetailOutput output = TopicDetailOutput.from(topic(), "inst-1", null, List.of(route), null);

        assertThat(output.routes().get(0).perm()).isNull();
        assertThat(output.routes().get(0).permCode()).isZero();
    }
}
