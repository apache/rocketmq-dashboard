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
package org.apache.rocketmq.studio.ops.ai.tool.contract.group;

import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.ConsumerInstanceVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroupDetailOutputTest {

    private static ConsumerGroupVO group(String name) {
        ConsumerGroupVO vo = new ConsumerGroupVO();
        vo.setName(name);
        vo.setOnlineInstances(2);
        vo.setTotalLag(9L);
        vo.setRetryMaxTimes(16);
        return vo;
    }

    private static QueueProgressVO queue(String topic, long diffTotal) {
        return QueueProgressVO.builder()
                .topic(topic).broker("broker-a").queueId(0)
                .brokerOffset(100L).consumerOffset(100L - diffTotal).diffTotal(diffTotal)
                .build();
    }

    @Test
    void aBlankSourceNameFallsBackToTheRequestedGroup() {
        GroupDetailOutput output = GroupDetailOutput.from(
                group("   "), "inst-1", "requested-group", null, null, List.of(group("requested-group")),
                null, null);

        assertThat(output.group()).isEqualTo("requested-group");
        assertThat(output.instanceId()).isEqualTo("inst-1");
    }

    @Test
    void retryMaxTimesIsExposedOnlyForTheSingleConfigurationCase() {
        ConsumerGroupVO source = group("g");
        GroupDetailOutput single = GroupDetailOutput.from(
                source, "inst-1", "g", null, null, List.of(source), null, null);
        GroupDetailOutput multiple = GroupDetailOutput.from(
                source, "inst-1", "g", null, null, List.of(source, group("g2")), null, null);

        assertThat(single.retryMaxTimes()).isEqualTo(16);
        assertThat(multiple.retryMaxTimes()).isNull();
    }

    @Test
    void progressLagSumsAcrossQueues() {
        List<QueueProgressVO> progress = List.of(queue("t1", 4L), queue("t1", 5L));

        GroupDetailOutput output = GroupDetailOutput.from(
                group("g"), "inst-1", "g", null, null, List.of(group("g")), progress, null);

        assertThat(output.progress().totalLag()).isEqualTo(9L);
        assertThat(output.progress().queues()).hasSize(2);
        assertThat(output.progress().queues().get(0).lag()).isEqualTo(4L);
    }

    @Test
    void anUnknownLagQueueTurnsTheWholeProgressUnknown() {
        List<QueueProgressVO> progress = List.of(queue("t1", 4L), queue("t1", -1L));

        GroupDetailOutput output = GroupDetailOutput.from(
                group("g"), "inst-1", "g", null, null, List.of(group("g")), progress, null);

        assertThat(output.progress().totalLag()).isEqualTo(-1L);
    }

    @Test
    void theTopicFilterAppliesToBothProgressAndClients() {
        ConsumerInstanceVO matching = ConsumerInstanceVO.builder()
                .clientId("c1").subscribedTopics(List.of("t1")).build();
        ConsumerInstanceVO other = ConsumerInstanceVO.builder()
                .clientId("c2").subscribedTopics(List.of("t2")).build();
        ConsumerGroupVO source = group("g");
        source.setInstances(List.of(matching, other));
        List<QueueProgressVO> progress = List.of(queue("t1", 4L), queue("t2", 5L));

        GroupDetailOutput output = GroupDetailOutput.from(
                source, "inst-1", "g", null, null, List.of(source), progress, "t1");

        assertThat(output.progress().queues()).hasSize(1);
        assertThat(output.progress().totalLag()).isEqualTo(4L);
        assertThat(output.clients().totalClients()).isEqualTo(1);
        assertThat(output.clients().clients()).extracting(GroupDetailOutput.Client::clientId)
                .containsExactly("c1");
    }

    @Test
    void aClientMatchesTheFilterThroughItsTopicLagEvenWithoutSubscriptionList() {
        ConsumerInstanceVO lagOnly = ConsumerInstanceVO.builder()
                .clientId("c3").subscribedTopics(null).topicLag(java.util.Map.of("t1", 7L)).build();
        ConsumerGroupVO source = group("g");
        source.setInstances(List.of(lagOnly));

        GroupDetailOutput output = GroupDetailOutput.from(
                source, "inst-1", "g", null, null, List.of(source), null, "t1");

        assertThat(output.clients().totalClients()).isEqualTo(1);
    }

    @Test
    void clientsAndProgressStayEmptyNeverNullWithoutData() {
        GroupDetailOutput output = GroupDetailOutput.from(
                group("g"), "inst-1", "g", null, null, List.of(group("g")), null, null);

        assertThat(output.progress().queues()).isEmpty();
        assertThat(output.progress().totalLag()).isZero();
        assertThat(output.clients().totalClients()).isZero();
        assertThat(output.clients().clients()).isEmpty();
        assertThat(output.subscriptions()).isEmpty();
        assertThat(output.instances()).isEmpty();
    }

    @Test
    void nullInstancesInListsNeverReachTheMapping() {
        ConsumerGroupVO source = group("g");
        source.setInstances(java.util.Arrays.asList(null, ConsumerInstanceVO.builder().clientId("c1").build()));

        GroupDetailOutput output = GroupDetailOutput.from(
                source, "inst-1", "g",
                java.util.Arrays.asList(null, SubscriptionEntryVO.builder().topic("t1").build()),
                null, List.of(source), null, null);

        assertThat(output.instances()).hasSize(1);
        assertThat(output.subscriptions()).hasSize(1);
        assertThat(output.subscriptions().get(0).topic()).isEqualTo("t1");
    }

    @Test
    void protocolNullabilityAndHeartbeatRenderingFollowTheClientContract() {
        ConsumerInstanceVO withProtocol = ConsumerInstanceVO.builder()
                .clientId("c1").protocol(org.apache.rocketmq.studio.common.domain.enums.Protocol.gRPC)
                .lastHeartbeat(LocalDateTime.of(2026, 1, 1, 12, 0))
                .build();
        ConsumerInstanceVO without = ConsumerInstanceVO.builder().clientId("c2").build();
        ConsumerGroupVO source = group("g");
        source.setInstances(List.of(withProtocol, without));

        GroupDetailOutput output = GroupDetailOutput.from(
                source, "inst-1", "g", null, null, List.of(source), null, null);

        assertThat(output.instances()).extracting(GroupDetailOutput.Instance::protocol)
                .containsExactly("gRPC", "UNKNOWN");
        assertThat(output.instances()).extracting(GroupDetailOutput.Instance::lastHeartbeat)
                .containsExactly("2026-01-01T12:00", null);
        assertThat(output.instances()).extracting(GroupDetailOutput.Instance::subscribedTopics)
                .containsExactly(List.of(), List.of());
        assertThat(output.instances()).extracting(GroupDetailOutput.Instance::topicLag)
                .containsExactly(java.util.Map.of(), java.util.Map.of());
    }

    @Test
    void onlineInstancesUnavailableIsReportedAsMinusOne() {
        ConsumerGroupVO source = new ConsumerGroupVO();
        source.setName("g");
        source.setOnlineInstances(-1);

        GroupDetailOutput output = GroupDetailOutput.from(
                source, "inst-1", "g", null, null, List.of(source), null, null);

        assertThat(output.onlineInstances()).isEqualTo(-1);
    }
}
