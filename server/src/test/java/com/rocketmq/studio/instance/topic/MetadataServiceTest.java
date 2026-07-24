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

package com.rocketmq.studio.instance.topic;

import com.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetadataServiceTest {

    @Mock
    private MetadataProvider metadataProvider;

    @Mock
    private AdminClient adminClient;

    @InjectMocks
    private MetadataService metadataService;

    @Test
    void listTopicsShouldReturnTopicsFromProvider() {
        TopicVO topic = new TopicVO();
        topic.setName("test-topic");
        topic.setWriteQueues(8);
        topic.setReadQueues(8);

        when(metadataProvider.listTopics("cluster-1", "NORMAL", null)).thenReturn(List.of(topic));

        List<TopicVO> result = metadataService.listTopics("cluster-1", "NORMAL", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("test-topic");
        verify(metadataProvider).listTopics("cluster-1", "NORMAL", null);
    }

    @Test
    void listTopicsShouldReturnEmptyWhenNone() {
        when(metadataProvider.listTopics(null, null, "nonexistent")).thenReturn(List.of());

        List<TopicVO> result = metadataService.listTopics(null, null, "nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void listNamespacesShouldReturnNamespacesFromProvider() {
        NamespaceVO namespace = new NamespaceVO();
        namespace.setName("trade");
        namespace.setClusterId("cluster-1");

        when(metadataProvider.listNamespaces()).thenReturn(List.of(namespace));

        List<NamespaceVO> result = metadataService.listNamespaces();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("trade");
        verify(metadataProvider).listNamespaces();
    }

    @Test
    void createTopicShouldDelegateToAdminClient() {
        TopicVO input = new TopicVO();
        input.setName("new-topic");
        input.setWriteQueues(8);
        input.setReadQueues(8);

        TopicVO created = new TopicVO();
        created.setName("new-topic");
        created.setWriteQueues(8);
        created.setReadQueues(8);

        when(adminClient.createTopic(any(TopicVO.class))).thenReturn(created);

        TopicVO result = metadataService.createTopic(input);

        assertThat(result.getName()).isEqualTo("new-topic");
        verify(adminClient).createTopic(input);
    }

    @Test
    void deleteTopicShouldDelegateToAdminClient() {
        metadataService.deleteTopic("topic-to-delete");

        verify(adminClient).deleteTopic("topic-to-delete");
    }

    @Test
    void sendMessageShouldReturnResult() {
        SendMessageDTO request = SendMessageDTO.builder()
                .topic("test-topic")
                .tag("TagA")
                .body("hello")
                .build();

        SendMessageVO expectedResult = SendMessageVO.builder()
                .msgId("msg-001")
                .sendTime(System.currentTimeMillis())
                .offsetMsgId("offset-001")
                .build();

        when(adminClient.sendMessage(request)).thenReturn(expectedResult);

        SendMessageVO result = metadataService.sendMessage(request);

        assertThat(result.getMsgId()).isEqualTo("msg-001");
        assertThat(result.getOffsetMsgId()).isEqualTo("offset-001");
        verify(adminClient).sendMessage(request);
    }

    @Test
    void listConsumerGroupsShouldReturnGroupsFromProvider() {
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("test-group");

        when(metadataProvider.listConsumerGroups("cluster-1", null)).thenReturn(List.of(group));

        List<ConsumerGroupVO> result = metadataService.listConsumerGroups("cluster-1", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("test-group");
        verify(metadataProvider).listConsumerGroups("cluster-1", null);
    }

    @Test
    void listConsumerGroupsShouldPassSearchFilter() {
        when(metadataProvider.listConsumerGroups(null, "order")).thenReturn(List.of());

        List<ConsumerGroupVO> result = metadataService.listConsumerGroups(null, "order");

        assertThat(result).isEmpty();
        verify(metadataProvider).listConsumerGroups(null, "order");
    }
}
