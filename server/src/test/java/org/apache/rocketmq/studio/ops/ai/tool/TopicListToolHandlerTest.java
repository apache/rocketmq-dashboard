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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicListToolHandlerTest {

    @Mock
    private MetadataService metadataService;

    private TopicListToolHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TopicListToolHandler(metadataService);
    }

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> handler.execute(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("input");
    }

    @Test
    void listsTopicsWithFiltersAndProjectsFields() {
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setNamespace("default");
        topic.setClusterId("cluster-a");
        topic.setType(org.apache.rocketmq.studio.common.domain.enums.TopicType.NORMAL);
        topic.setPerm(org.apache.rocketmq.studio.common.domain.enums.TopicPerm.RW);
        topic.setMessageCount(3);
        when(metadataService.listTopics("cluster-a", "NORMAL", "order")).thenReturn(List.of(topic));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) handler.execute(
                Map.of("cluster", "cluster-a", "type", "NORMAL", "search", "order"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("name", "orders");
        assertThat(result.get(0)).containsEntry("namespace", "default");
    }
}
