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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

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

        when(metadataService.listTopics(isNull(), isNull(), isNull())).thenReturn(List.of(topic));

        mockMvc.perform(get("/api/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("test-topic"))
                .andExpect(jsonPath("$.data[0].writeQueues").value(8));
    }

    @Test
    void listTopicsShouldPassQueryParams() throws Exception {
        when(metadataService.listTopics(eq("cluster-1"), eq("NORMAL"), eq("test")))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/topics")
                        .param("clusterId", "cluster-1")
                        .param("type", "NORMAL")
                        .param("search", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(metadataService).listTopics(eq("cluster-1"), eq("NORMAL"), eq("test"));
    }

    @Test
    void createTopicShouldReturnCreatedTopic() throws Exception {
        TopicVO input = new TopicVO();
        input.setName("new-topic");
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

        verify(metadataService).deleteTopic("test-topic");
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
}
