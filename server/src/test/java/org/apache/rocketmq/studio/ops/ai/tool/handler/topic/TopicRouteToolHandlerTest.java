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
package org.apache.rocketmq.studio.ops.ai.tool.handler.topic;

import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.instance.topic.BrokerRouteVO;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicRouteInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicRouteItem;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopicRouteToolHandlerTest {

    private MetadataService metadataService;
    private TopicRouteToolHandler handler;

    @BeforeEach
    void setUp() {
        metadataService = mock(MetadataService.class);
        handler = new TopicRouteToolHandler(metadataService);
    }

    private static BrokerRouteVO route(String brokerName) {
        return BrokerRouteVO.builder()
                .brokerName(brokerName).brokerAddr("10.0.0.1:10911").masterAddr("10.0.0.1:10911")
                .brokerAddrs(Map.of(0L, "10.0.0.1:10911")).brokerIds(List.of(0L))
                .replicaCount(1).writeQueues(8).readQueues(8)
                .perm(TopicPerm.RW).permCode(6).readable(true).writable(true)
                .topicSysFlag(0).build();
    }

    @Test
    void theHandlerFetchesTheRoutesUnderTheContextInstanceAndMapsEveryRoute() {
        when(metadataService.getTopicRoutes("inst-1", "orders"))
                .thenReturn(List.of(route("broker-a"), route("broker-b")));

        ListOutput<TopicRouteItem> output = handler.execute(
                new TopicRouteInput(null, "orders"),
                new ToolExecutionContext("inst-1", null, null, null));

        verify(metadataService).getTopicRoutes("inst-1", "orders");
        assertThat(output.items()).extracting(TopicRouteItem::brokerName)
                .containsExactly("broker-a", "broker-b");
    }

    @Test
    void everyRouteFieldRendersThroughTheItemMapping() {
        when(metadataService.getTopicRoutes("inst-1", "orders")).thenReturn(List.of(route("broker-a")));

        TopicRouteItem item = handler.execute(
                new TopicRouteInput(null, "orders"),
                new ToolExecutionContext("inst-1", null, null, null)).items().get(0);

        assertThat(item.brokerName()).isEqualTo("broker-a");
        assertThat(item.brokerAddr()).isEqualTo("10.0.0.1:10911");
        assertThat(item.perm()).isEqualTo("RW");
        assertThat(item.permCode()).isEqualTo(6);
        assertThat(item.readable()).isTrue();
        assertThat(item.writable()).isTrue();
        assertThat(item.brokerAddrs()).containsEntry(0L, "10.0.0.1:10911");
        assertThat(item.brokerIds()).containsExactly(0L);
        assertThat(item.replicaCount()).isEqualTo(1);
        assertThat(item.topicSysFlag()).isZero();
    }

    @Test
    void aNullRoutePermissionRendersAsNullInsteadOfFailing() {
        BrokerRouteVO unpermissioned = BrokerRouteVO.builder()
                .brokerName("broker-a").brokerAddrs(Map.of()).brokerIds(List.of()).build();
        when(metadataService.getTopicRoutes("inst-1", "orders")).thenReturn(List.of(unpermissioned));

        TopicRouteItem item = handler.execute(
                new TopicRouteInput(null, "orders"),
                new ToolExecutionContext("inst-1", null, null, null)).items().get(0);

        assertThat(item.perm()).isNull();
        assertThat(item.permCode()).isZero();
    }

    @Test
    void anEmptyRouteListProducesAnEmptyOutputNeverNull() {
        when(metadataService.getTopicRoutes("inst-1", "orders")).thenReturn(List.of());

        ListOutput<TopicRouteItem> output = handler.execute(
                new TopicRouteInput(null, "orders"),
                new ToolExecutionContext("inst-1", null, null, null));

        assertThat(output.items()).isNotNull();
        assertThat(output.items()).isEmpty();
    }

    @Test
    void theHandlerAdvertisesTheRouteToolNameAndInputType() {
        assertThat(handler.name()).isEqualTo("rmq.topic.route");
        assertThat(handler.inputType()).isEqualTo(TopicRouteInput.class);
    }
}
