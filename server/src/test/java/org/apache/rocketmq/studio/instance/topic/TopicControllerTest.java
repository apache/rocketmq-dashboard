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

package org.apache.rocketmq.studio.instance.topic;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TopicController.class)
@AutoConfigureMockMvc(addFilters = false)
class TopicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MetadataService metadataService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listTopicsShouldReturnTopics() throws Exception {
        TopicVO topic = new TopicVO();
        topic.setName("test-topic");
        topic.setWriteQueues(8);
        topic.setReadQueues(8);

        when(metadataService.listTopics(isNull(), isNull(), isNull(), isNull())).thenReturn(List.of(topic));

        mockMvc.perform(get("/api/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("test-topic"))
                .andExpect(jsonPath("$.data[0].writeQueues").value(8));
    }

    @Test
    void listTopicsShouldPassQueryParams() throws Exception {
        when(metadataService.listTopics(isNull(), eq("cluster-1"), eq("NORMAL"), eq("test")))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/topics")
                        .param("clusterId", "cluster-1")
                        .param("type", "NORMAL")
                        .param("search", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(metadataService).listTopics(isNull(), eq("cluster-1"), eq("NORMAL"), eq("test"));
    }

    @Test
    void topicRuntimeDiagnosticsShouldPassSelectedInstance() throws Exception {
        when(metadataService.getTopicRoutes("instance-a", "orders")).thenReturn(List.of());
        when(metadataService.getTopicConsumers("instance-a", "orders")).thenReturn(List.of());

        mockMvc.perform(get("/api/topics/orders/routes").param("instanceId", "instance-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/topics/orders/consumers").param("instanceId", "instance-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(metadataService).getTopicRoutes("instance-a", "orders");
        verify(metadataService).getTopicConsumers("instance-a", "orders");
    }

    @Test
    void topicConsumerPageShouldPassSelectedInstanceAndPaging() throws Exception {
        TopicConsumerPageVO page = TopicConsumerPageVO.builder()
                .items(List.of()).total(3).page(2).pageSize(20).build();
        when(metadataService.getTopicConsumersPage("instance-a", "orders", 2, 20)).thenReturn(page);

        mockMvc.perform(get("/api/topics/orders/consumers/page")
                        .param("instanceId", "instance-a")
                        .param("page", "2")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));

        verify(metadataService).getTopicConsumersPage("instance-a", "orders", 2, 20);
    }

    @Test
    void createTopicShouldReturnCreatedTopic() throws Exception {
        TopicVO input = new TopicVO();
        input.setName("new-topic");
        input.setInstanceId(1L);
        input.setWriteQueues(16);
        input.setReadQueues(16);

        TopicVO created = new TopicVO();
        created.setName("new-topic");
        created.setWriteQueues(16);
        created.setReadQueues(16);

        when(metadataService.createTopic(any(TopicVO.class))).thenReturn(created);

        mockMvc.perform(post("/api/topics/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("new-topic"))
                .andExpect(jsonPath("$.data.writeQueues").value(16));

        ArgumentCaptor<TopicVO> captor = ArgumentCaptor.forClass(TopicVO.class);
        verify(metadataService).createTopic(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("new-topic");
        assertThat(captor.getValue().getInstanceId()).isEqualTo(1L);
        assertThat(captor.getValue().getWriteQueues()).isEqualTo(16);
        assertThat(captor.getValue().getReadQueues()).isEqualTo(16);
    }

    @Test
    void createTopicShouldRejectMissingName() throws Exception {
        mockMvc.perform(post("/api/topics/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "writeQueues": 8,
                                  "readQueues": 8
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("name is required"));

        verifyNoInteractions(metadataService);
    }

    @Test
    void createTopicShouldRejectNegativeQueueCount() throws Exception {
        mockMvc.perform(post("/api/topics/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "new-topic",
                                  "writeQueues": -1,
                                  "readQueues": 8
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("writeQueues must be zero or positive"));

        verifyNoInteractions(metadataService);
    }

    @Test
    void topicWriteEndpointsShouldRejectNullRequestBody() throws Exception {
        mockMvc.perform(post("/api/topics/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Topic request is required"));

        mockMvc.perform(post("/api/topics/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Topic request is required"));

        mockMvc.perform(post("/api/topics/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Topic delete request is required"));

        mockMvc.perform(post("/api/topics/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Topic send message request is required"));

        verifyNoInteractions(metadataService);
    }

    @Test
    void updateTopicShouldRejectBlankName() throws Exception {
        mockMvc.perform(post("/api/topics/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("name is required"));

        verifyNoInteractions(metadataService);
    }

    @Test
    void sendMessageShouldReturnResult() throws Exception {
        SendMessageDTO request = SendMessageDTO.builder()
                .topic("test-topic")
                .tag("TagA")
                .body("hello world")
                .build();

        SendMessageVO result = SendMessageVO.builder()
                .msgId("msg-001")
                .sendTime(1720000000000L)
                .offsetMsgId("offset-001")
                .build();

        when(metadataService.sendMessage(any(SendMessageDTO.class))).thenReturn(result);

        mockMvc.perform(post("/api/topics/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.msgId").value("msg-001"))
                .andExpect(jsonPath("$.data.offsetMsgId").value("offset-001"));
    }

    @Test
    void deleteTopicShouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/api/topics/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "test-topic"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        verify(metadataService).deleteTopic(isNull(), eq("test-topic"));
    }

    @Test
    void deleteTopicShouldRejectMissingName() throws Exception {
        mockMvc.perform(post("/api/topics/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("name is required"));

        verifyNoInteractions(metadataService);
    }

    @Test
    void deleteTopicShouldRejectBlankName() throws Exception {
        mockMvc.perform(post("/api/topics/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("name is required"));

        verifyNoInteractions(metadataService);
    }

    @Test
    void sendMessageShouldRejectMissingTopic() throws Exception {
        mockMvc.perform(post("/api/topics/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hello\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("topic is required"));

        verifyNoInteractions(metadataService);
    }
}
