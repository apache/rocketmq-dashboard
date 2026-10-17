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
package org.apache.rocketmq.studio.ops.ai.tool.handler.group;

import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupDetailInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupDetailOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GroupDetailToolHandlerTest {

    private MetadataService metadataService;
    private GroupDetailToolHandler handler;

    @BeforeEach
    void setUp() {
        metadataService = mock(MetadataService.class);
        handler = new GroupDetailToolHandler(metadataService);
        when(metadataService.getGroupSubscriptions("inst-1", "orders")).thenReturn(List.of());
        when(metadataService.consumerGroupConfigurations("inst-1", "orders")).thenReturn(List.of());
        when(metadataService.getGroupProgress("inst-1", "orders")).thenReturn(List.of());
    }

    private static ConsumerGroupVO group(boolean statsAvailable, int onlineInstances, long totalLag) {
        ConsumerGroupVO vo = new ConsumerGroupVO();
        vo.setName("orders");
        vo.setConsumeStatsAvailable(statsAvailable);
        vo.setOnlineInstances(onlineInstances);
        vo.setTotalLag(totalLag);
        return vo;
    }

    private GroupDetailOutput.Health executeHealth(ConsumerGroupVO group) {
        when(metadataService.consumerGroupRuntimeView("inst-1", "orders")).thenReturn(group);
        GroupDetailOutput output = handler.execute(
                new GroupDetailInput(null, "orders", null),
                new ToolExecutionContext("inst-1", null, null, null));
        return output.health();
    }

    @Test
    void theHandlerComposesEveryMetadataReadIntoTheAggregatedOutput() {
        ConsumerGroupVO group = group(true, 2, 0);
        when(metadataService.consumerGroupRuntimeView("inst-1", "orders")).thenReturn(group);

        GroupDetailOutput output = handler.execute(
                new GroupDetailInput(null, "orders", null),
                new ToolExecutionContext("inst-1", null, null, null));

        assertThat(output.group()).isEqualTo("orders");
        assertThat(output.instanceId()).isEqualTo("inst-1");
    }

    @Test
    void unavailableBrokerStatisticsMeansUnknownHealth() {
        GroupDetailOutput.Health health = executeHealth(group(false, 5, 0));

        assertThat(health.status()).isEqualTo("UNKNOWN");
        assertThat(health.reasons()).containsExactly("Broker consume statistics are unavailable.");
    }

    @Test
    void anUnavailableConnectionInventoryMeansUnknownHealth() {
        GroupDetailOutput.Health health = executeHealth(group(true, -1, 500));

        assertThat(health.status()).isEqualTo("UNKNOWN");
        assertThat(health.reasons()).containsExactly("Consumer connection information is unavailable.");
    }

    @Test
    void accumulatedMessagesWithNoOnlineConsumerIsUnhealthy() {
        GroupDetailOutput.Health health = executeHealth(group(true, 0, 100));

        assertThat(health.status()).isEqualTo("UNHEALTHY");
        assertThat(health.reasons()).containsExactly("The group has accumulated messages but no online consumer.");
    }

    @Test
    void noOnlineConsumerWithoutLagIsAWarning() {
        GroupDetailOutput.Health health = executeHealth(group(true, 0, 0));

        assertThat(health.status()).isEqualTo("WARNING");
        assertThat(health.reasons()).containsExactly("The group has no online consumer.");
    }

    @Test
    void onlineConsumersWithLagIsAWarning() {
        GroupDetailOutput.Health health = executeHealth(group(true, 3, 100));

        assertThat(health.status()).isEqualTo("WARNING");
        assertThat(health.reasons()).containsExactly("The group has accumulated messages.");
    }

    @Test
    void onlineConsumersWithoutLagIsHealthyWithNoReasons() {
        GroupDetailOutput.Health health = executeHealth(group(true, 3, 0));

        assertThat(health.status()).isEqualTo("HEALTHY");
        assertThat(health.reasons()).isEmpty();
    }

    @Test
    void theTopicNameFilterIsForwardedToTheAggregation() {
        ConsumerGroupVO group = group(true, 1, 0);
        when(metadataService.consumerGroupRuntimeView("inst-1", "orders")).thenReturn(group);
        when(metadataService.getGroupProgress("inst-1", "orders")).thenReturn(List.of(
                org.apache.rocketmq.studio.instance.group.QueueProgressVO.builder()
                        .topic("orders-events").broker("b").queueId(0)
                        .brokerOffset(10L).consumerOffset(8L).diffTotal(2L).build(),
                org.apache.rocketmq.studio.instance.group.QueueProgressVO.builder()
                        .topic("payments").broker("b").queueId(0)
                        .brokerOffset(10L).consumerOffset(10L).diffTotal(0L).build()));

        GroupDetailOutput output = handler.execute(
                new GroupDetailInput(null, "orders", "payments"),
                new ToolExecutionContext("inst-1", null, null, null));

        // The filter must reach the aggregation: only the payments queue survives.
        assertThat(output.progress().queues()).hasSize(1);
        assertThat(output.progress().queues().get(0).broker()).isEqualTo("b");
        assertThat(output.clients().totalClients()).isZero();
    }

    @Test
    void theHandlerAdvertisesTheGroupDetailToolNameAndInputType() {
        assertThat(handler.name()).isEqualTo("rmq.group.detail");
        assertThat(handler.inputType()).isEqualTo(GroupDetailInput.class);
    }
}
