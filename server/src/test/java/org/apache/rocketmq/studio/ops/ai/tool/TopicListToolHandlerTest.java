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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopicListToolHandlerTest {

    @Test
    void executeShouldDelegateAndProjectTopics() {
        MetadataService service = mock(MetadataService.class);
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setNamespace("prod");
        topic.setClusterId("cluster-a");
        topic.setType(TopicType.NORMAL);
        topic.setWriteQueues(8);
        topic.setReadQueues(8);
        topic.setPerm(TopicPerm.RW);
        topic.setMessageCount(1000L);
        topic.setTps(5D);
        topic.setConsumerGroupCount(2);
        PageResult<TopicVO> page = PageResult.of(List.of(topic), 1, 1, 20);
        when(service.listTopicsPage(eq("cluster-a"), eq(null), eq(null), eq(null), eq(1), eq(20)))
                .thenReturn(page);

        Object output = new TopicListToolHandler(service).execute(Map.of("cluster", "cluster-a"));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) output;
        assertThat(result.get("total")).isEqualTo(1L);
        Map<?, ?> row = (Map<?, ?>) ((List<?>) result.get("items")).get(0);
        assertThat(row.get("name")).isEqualTo("orders");
        assertThat(row.get("namespace")).isEqualTo("prod");
        assertThat(row.get("type")).isEqualTo("NORMAL");
        assertThat(row.get("perm")).isEqualTo("RW");
        assertThat(row.get("writeQueues")).isEqualTo(8);
        assertThat(row.get("messageCount")).isEqualTo(1000L);
        verify(service).listTopicsPage("cluster-a", null, null, null, 1, 20);
    }

    @Test
    void executeShouldForwardTypeSearchAndPaging() {
        MetadataService service = mock(MetadataService.class);
        PageResult<TopicVO> page = PageResult.of(List.of(), 0, 2, 10);
        when(service.listTopicsPage("cluster-a", null, "FIFO", "orders", 2, 10)).thenReturn(page);

        new TopicListToolHandler(service).execute(Map.of(
                "cluster", "cluster-a", "type", "FIFO", "search", "orders",
                "page", 2, "pageSize", 10));

        verify(service).listTopicsPage("cluster-a", null, "FIFO", "orders", 2, 10);
    }

    @Test
    void handlerNameShouldBeRmqTopicList() {
        assertThat(new TopicListToolHandler(mock(MetadataService.class)).name())
                .isEqualTo("rmq.topic.list");
    }

    @Test
    void missingTopicNameIsRejected() {
        MetadataService service = mock(MetadataService.class);
        PageResult<TopicVO> page = PageResult.of(List.of(new TopicVO()), 1, 1, 20);
        when(service.listTopicsPage(eq("cluster-a"), eq(null), eq(null), eq(null), eq(1), eq(20)))
                .thenReturn(page);

        assertThatThrownBy(() -> new TopicListToolHandler(service)
                .execute(Map.of("cluster", "cluster-a")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Topic name is unavailable");
    }

    @Test
    void nullTypeAndPermAreRejected() {
        MetadataService service = mock(MetadataService.class);
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        PageResult<TopicVO> page = PageResult.of(List.of(topic), 1, 1, 20);
        when(service.listTopicsPage(eq("cluster-a"), eq(null), eq(null), eq(null), eq(1), eq(20)))
                .thenReturn(page);

        assertThatThrownBy(() -> new TopicListToolHandler(service)
                .execute(Map.of("cluster", "cluster-a")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Topic type is unavailable: orders");
    }

    @Test
    void absentOptionalFieldsProjectAsBlankStrings() {
        MetadataService service = mock(MetadataService.class);
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setType(TopicType.NORMAL);
        topic.setPerm(TopicPerm.RO);
        PageResult<TopicVO> page = PageResult.of(List.of(topic), 1, 1, 20);
        when(service.listTopicsPage(eq("cluster-a"), eq(null), eq(null), eq(null), eq(1), eq(20)))
                .thenReturn(page);

        Object output = new TopicListToolHandler(service).execute(Map.of("cluster", "cluster-a"));

        Map<?, ?> row = (Map<?, ?>) ((List<?>) ((Map<?, ?>) output).get("items")).get(0);
        assertThat(row.get("namespace")).isEqualTo("");
        assertThat(row.get("clusterId")).isEqualTo("");
        assertThat(row.get("messageCount")).isEqualTo(0L);
    }
}
