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

import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupListItem;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumerGroupListToolHandlerTest {

    private MetadataService metadataService;
    private ConsumerGroupListToolHandler handler;

    @BeforeEach
    void setUp() {
        metadataService = mock(MetadataService.class);
        handler = new ConsumerGroupListToolHandler(metadataService);
    }

    private static ConsumerGroupVO group(String name, int onlineInstances, long totalLag) {
        ConsumerGroupVO vo = new ConsumerGroupVO();
        vo.setName(name);
        vo.setClusterId("cluster-a");
        vo.setSubscriptionMode(SubscriptionMode.Push);
        vo.setConsumeType(ConsumeType.CLUSTERING);
        vo.setRetryMaxTimes(16);
        vo.setOnlineInstances(onlineInstances);
        vo.setTotalLag(totalLag);
        return vo;
    }

    @Test
    void theGroupSearchIsForwardedUnderTheContextInstance() {
        when(metadataService.listConsumerGroups("inst-1", null, "orders"))
                .thenReturn(List.of(group("orders", 1, 0)));

        ListOutput<GroupListItem> output = handler.execute(
                new GroupListInput(null, "orders"),
                new ToolExecutionContext("inst-1", null, null, null));

        verify(metadataService).listConsumerGroups("inst-1", null, "orders");
        assertThat(output.items()).hasSize(1);
    }

    @Test
    void anAbsentSearchTravelsAsNullNotBlank() {
        when(metadataService.listConsumerGroups("inst-1", null, null)).thenReturn(List.of());

        handler.execute(
                new GroupListInput(null, null),
                new ToolExecutionContext("inst-1", null, null, null));

        // A null search means "no filter" at the service; a blank would match nothing.
        verify(metadataService).listConsumerGroups("inst-1", null, null);
    }

    @Test
    void everyGroupFieldRendersThroughTheItemMapping() {
        when(metadataService.listConsumerGroups("inst-1", null, null))
                .thenReturn(List.of(group("orders", 3, 42L)));

        GroupListItem item = handler.execute(
                new GroupListInput(null, null),
                new ToolExecutionContext("inst-1", null, null, null)).items().get(0);

        assertThat(item.name()).isEqualTo("orders");
        assertThat(item.clusterId()).isEqualTo("cluster-a");
        assertThat(item.subscriptionMode()).isEqualTo(SubscriptionMode.Push);
        assertThat(item.consumeType()).isEqualTo(ConsumeType.CLUSTERING);
        assertThat(item.retryMaxTimes()).isEqualTo(16);
        assertThat(item.onlineInstances()).isEqualTo(3);
        assertThat(item.totalLag()).isEqualTo(42L);
    }

    @Test
    void aNegativeOnlineInstanceCountIsReportedAsUnavailable() {
        when(metadataService.listConsumerGroups("inst-1", null, null))
                .thenReturn(List.of(group("orders", -1, 0)));

        GroupListItem item = handler.execute(
                new GroupListInput(null, null),
                new ToolExecutionContext("inst-1", null, null, null)).items().get(0);

        // The decision-11 marker: -1 means the connection inventory is unavailable,
        // and it must reach the tool output verbatim rather than being clamped.
        assertThat(item.onlineInstances()).isEqualTo(-1);
    }

    @Test
    void aNullSubscribedTopicsListRendersAsEmptyNotNull() {
        ConsumerGroupVO vo = group("orders", 1, 0);
        vo.setSubscribedTopics(null);
        when(metadataService.listConsumerGroups("inst-1", null, null)).thenReturn(List.of(vo));

        GroupListItem item = handler.execute(
                new GroupListInput(null, null),
                new ToolExecutionContext("inst-1", null, null, null)).items().get(0);

        assertThat(item.subscribedTopics()).isNotNull().isEmpty();
    }

    @Test
    void anEmptyGroupListProducesAnEmptyOutputNeverNull() {
        when(metadataService.listConsumerGroups("inst-1", null, null)).thenReturn(List.of());

        ListOutput<GroupListItem> output = handler.execute(
                new GroupListInput(null, null),
                new ToolExecutionContext("inst-1", null, null, null));

        assertThat(output.items()).isNotNull().isEmpty();
    }

    @Test
    void theHandlerAdvertisesTheListToolNameAndInputType() {
        assertThat(handler.name()).isEqualTo("rmq.group.list");
        assertThat(handler.inputType()).isEqualTo(GroupListInput.class);
    }
}
