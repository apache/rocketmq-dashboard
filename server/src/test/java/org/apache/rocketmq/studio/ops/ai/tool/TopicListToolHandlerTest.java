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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicListToolHandlerTest {

    @Mock
    private MetadataService metadataService;

    @InjectMocks
    private TopicListToolHandler handler;

    @Test
    void executeShouldRouteClusterToClusterScopedRead() {
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setClusterId("DefaultCluster");
        topic.setType(TopicType.NORMAL);
        topic.setPerm(TopicPerm.RW);
        topic.setWriteQueues(8);
        topic.setReadQueues(8);
        topic.setMessageCount(100L);
        topic.setTps(1.5D);
        topic.setConsumerGroupCount(2);
        when(metadataService.listTopicsPage(isNull(), eq("DefaultCluster"), eq("NORMAL"), eq("order"), eq(1), eq(20)))
                .thenReturn(PageResult.of(List.of(topic), 1L, 1, 20));

        Object result = handler.execute(Map.of(
                "cluster", "DefaultCluster", "type", "NORMAL", "search", "order"));

        Map<?, ?> page = (Map<?, ?>) result;
        assertThat(page.get("total")).isEqualTo(1L);
        assertThat(page.get("page")).isEqualTo(1);
        assertThat(page.get("size")).isEqualTo(20);
        List<?> items = (List<?>) page.get("items");
        assertThat(items).hasSize(1);
        Map<?, ?> row = (Map<?, ?>) items.get(0);
        assertThat(row.get("name")).isEqualTo("orders");
        assertThat(row.get("type")).isEqualTo("NORMAL");
        assertThat(row.get("perm")).isEqualTo("RW");
        verify(metadataService).listTopicsPage(isNull(), eq("DefaultCluster"), eq("NORMAL"), eq("order"), eq(1), eq(20));
    }
}
