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
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicListItem;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopicListToolHandlerTest {

    private MetadataService metadataService;
    private TopicListToolHandler handler;

    @BeforeEach
    void setUp() {
        metadataService = mock(MetadataService.class);
        handler = new TopicListToolHandler(metadataService);
    }

    private static TopicVO topic(String name) {
        TopicVO vo = new TopicVO();
        vo.setName(name);
        vo.setClusterId("cluster-a");
        vo.setType(TopicType.FIFO);
        vo.setWriteQueues(16);
        vo.setReadQueues(16);
        vo.setPerm(TopicPerm.RO);
        vo.setMessageCount(42L);
        vo.setTps(3.5);
        vo.setConsumerGroupCount(7);
        return vo;
    }

    @Test
    void theInputFiltersAreForwardedVerbatimUnderTheContextInstance() {
        when(metadataService.listTopics("inst-1", null, "FIFO", "orders")).thenReturn(List.of(topic("orders")));

        ListOutput<TopicListItem> output = handler.execute(
                new TopicListInput(null, "orders", "FIFO"),
                new ToolExecutionContext("inst-1", null, null, null));

        verify(metadataService).listTopics("inst-1", null, "FIFO", "orders");
        assertThat(output.items()).hasSize(1);
    }

    @Test
    void absentFiltersTravelAsNullsNotBlanks() {
        when(metadataService.listTopics("inst-1", null, null, null)).thenReturn(List.of());

        handler.execute(
                new TopicListInput(null, null, null),
                new ToolExecutionContext("inst-1", null, null, null));

        // A null filter must reach the service as null so the service can treat it as
        // "no filter"; a blank string would become a match-nothing search.
        verify(metadataService).listTopics("inst-1", null, null, null);
    }

    @Test
    void everyTopicFieldRendersThroughTheItemMapping() {
        when(metadataService.listTopics("inst-1", null, null, null)).thenReturn(List.of(topic("orders")));

        TopicListItem item = handler.execute(
                new TopicListInput(null, null, null),
                new ToolExecutionContext("inst-1", null, null, null)).items().get(0);

        assertThat(item.name()).isEqualTo("orders");
        assertThat(item.clusterId()).isEqualTo("cluster-a");
        assertThat(item.type()).isEqualTo(TopicType.FIFO);
        assertThat(item.writeQueues()).isEqualTo(16);
        assertThat(item.readQueues()).isEqualTo(16);
        assertThat(item.perm()).isEqualTo(TopicPerm.RO);
        assertThat(item.messageCount()).isEqualTo(42L);
        assertThat(item.tps()).isEqualTo(3.5);
        assertThat(item.consumerGroupCount()).isEqualTo(7);
    }

    @Test
    void theListOrderIsPreserved() {
        when(metadataService.listTopics("inst-1", null, null, null))
                .thenReturn(List.of(topic("zeta"), topic("alpha")));

        ListOutput<TopicListItem> output = handler.execute(
                new TopicListInput(null, null, null),
                new ToolExecutionContext("inst-1", null, null, null));

        assertThat(output.items()).extracting(TopicListItem::name)
                .containsExactly("zeta", "alpha");
    }

    @Test
    void anEmptyTopicListProducesAnEmptyOutputNeverNull() {
        when(metadataService.listTopics("inst-1", null, null, null)).thenReturn(List.of());

        ListOutput<TopicListItem> output = handler.execute(
                new TopicListInput(null, null, null),
                new ToolExecutionContext("inst-1", null, null, null));

        assertThat(output.items()).isNotNull().isEmpty();
    }

    @Test
    void theHandlerAdvertisesTheListToolNameAndInputType() {
        assertThat(handler.name()).isEqualTo("rmq.topic.list");
        assertThat(handler.inputType()).isEqualTo(TopicListInput.class);
    }
}
