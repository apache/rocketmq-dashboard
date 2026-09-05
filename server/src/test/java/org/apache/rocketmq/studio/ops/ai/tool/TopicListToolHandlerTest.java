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
package org.apache.rocketmq.studio.ops.ai.tool;

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TopicListToolHandler}: the list-style tool delegates to the metadata
 * service with its filter/paging inputs and projects each topic row for the schema.
 */
@ExtendWith(MockitoExtension.class)
class TopicListToolHandlerTest {

    @Mock
    private MetadataService metadataService;

    @InjectMocks
    private TopicListToolHandler handler;

    private static TopicVO topic(String name) {
        TopicVO topic = new TopicVO();
        topic.setName(name);
        topic.setClusterId("c1");
        topic.setType(TopicType.NORMAL);
        topic.setWriteQueues(8);
        topic.setReadQueues(8);
        topic.setPerm(TopicPerm.RW);
        topic.setMessageCount(100L);
        topic.setConsumerGroupCount(3);
        return topic;
    }

    @Test
    void reportsItsToolName() {
        assertThat(handler.name()).isEqualTo("rmq.topic.list");
    }

    @Test
    @SuppressWarnings("unchecked")
    void delegatesAndProjectsTheTopicRows() {
        TopicVO topic = topic("orders");
        when(metadataService.listTopicsPage("c1", null, "NORMAL", "orders", 1, 20))
                .thenReturn(PageResult.of(List.of(topic), 1L, 1, 20));

        Map<String, Object> result = (Map<String, Object>) handler.execute(
                Map.of("cluster", "c1", "type", "NORMAL", "search", "orders"));

        Map<String, Object> row = (Map<String, Object>) ((List<?>) result.get("items")).get(0);
        assertThat(row.get("name")).isEqualTo("orders");
        assertThat(row.get("namespace")).isEqualTo("");
        assertThat(row.get("clusterId")).isEqualTo("c1");
        assertThat(row.get("type")).isEqualTo("NORMAL");
        assertThat(row.get("perm")).isEqualTo("RW");
        assertThat(row.get("writeQueues")).isEqualTo(8);
        assertThat(row.get("messageCount")).isEqualTo(100L);
        assertThat(result.get("total")).isEqualTo(1L);
        assertThat(result.get("page")).isEqualTo(1);
        assertThat(result.get("size")).isEqualTo(20);
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultsThePageWindowAndOptionalFilters() {
        when(metadataService.listTopicsPage("c1", null, null, null, 1, 20))
                .thenReturn(PageResult.empty(1, 20));

        Map<String, Object> result = (Map<String, Object>) handler.execute(Map.of("cluster", "c1"));

        assertThat(result.get("items")).isEqualTo(List.of());
        assertThat(result.get("total")).isEqualTo(0L);
        verify(metadataService).listTopicsPage(eq("c1"), isNull(), isNull(), isNull(), eq(1), eq(20));
    }

    @Test
    void rejectsTopicsMissingRequiredProjectionFields() {
        when(metadataService.listTopicsPage("c1", null, null, null, 1, 20))
                .thenReturn(PageResult.of(List.of(topic("no-type")), 1L, 1, 20));
        TopicVO noType = topic("no-type");
        noType.setType(null);
        when(metadataService.listTopicsPage("c1", null, null, null, 1, 20))
                .thenReturn(PageResult.of(List.of(noType), 1L, 1, 20));

        assertThatThrownBy(() -> handler.execute(Map.of("cluster", "c1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("type");
    }
}
