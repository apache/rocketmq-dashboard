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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LiteTopicController.class)
@AutoConfigureMockMvc(addFilters = false)
class LiteTopicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LiteTopicService liteTopicService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listLiteTopicsShouldPassFilters() throws Exception {
        LiteTopicItemVO item = LiteTopicItemVO.builder()
                .topicPattern("chat/{sessionId}")
                .namespace("default")
                .topicCount(96)
                .build();

        when(liteTopicService.listLiteTopics("chat", "default")).thenReturn(List.of(item));

        mockMvc.perform(get("/api/liteTopic/list")
                        .param("pattern", "chat")
                        .param("namespace", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].topicPattern").value("chat/{sessionId}"));

        verify(liteTopicService).listLiteTopics("chat", "default");
    }

    @Test
    void getQuotaShouldReturnQuota() throws Exception {
        LiteTopicQuotaVO quota = LiteTopicQuotaVO.builder()
                .currentTopicCount(128)
                .maxTopicCount(1_000_000)
                .remainingQuota(999_872)
                .build();

        when(liteTopicService.getQuota("default")).thenReturn(quota);

        mockMvc.perform(get("/api/liteTopic/quota").param("namespace", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentTopicCount").value(128))
                .andExpect(jsonPath("$.data.remainingQuota").value(999872));
    }

    @Test
    void getCapabilityShouldReturnSupportedFlag() throws Exception {
        when(liteTopicService.getCapability()).thenReturn(new LiteTopicCapabilityVO(true));

        mockMvc.perform(get("/api/liteTopic/capability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supported").value(true));
    }

    @Test
    void getSessionShouldReturnSession() throws Exception {
        LiteTopicSessionVO session = LiteTopicSessionVO.builder()
                .sessionId("sess-001")
                .clientId("grpc-client-sess-001")
                .popProgress(96)
                .build();

        when(liteTopicService.getSession("sess-001")).thenReturn(session);

        mockMvc.perform(get("/api/liteTopic/session/sess-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value("sess-001"))
                .andExpect(jsonPath("$.data.popProgress").value(96));
    }

    @Test
    void extendTTLShouldDelegateToService() throws Exception {
        LiteTopicTTLUpdateDTO request = new LiteTopicTTLUpdateDTO();
        request.setTopicPattern("chat/{sessionId}");
        request.setNewTTL(7_200_000L);

        mockMvc.perform(post("/api/liteTopic/extendTTL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(liteTopicService).extendTTL(eq("chat/{sessionId}"), eq(7_200_000L));
    }

    @Test
    void extendTTLShouldRejectMissingTopicPattern() throws Exception {
        LiteTopicTTLUpdateDTO request = new LiteTopicTTLUpdateDTO();
        request.setNewTTL(7_200_000L);

        mockMvc.perform(post("/api/liteTopic/extendTTL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("topicPattern is required"));

        verifyNoInteractions(liteTopicService);
    }

    @Test
    void extendTTLShouldRejectNonPositiveTTL() throws Exception {
        LiteTopicTTLUpdateDTO request = new LiteTopicTTLUpdateDTO();
        request.setTopicPattern("chat/{sessionId}");
        request.setNewTTL(0L);

        mockMvc.perform(post("/api/liteTopic/extendTTL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("newTTL must be positive"));

        verifyNoInteractions(liteTopicService);
    }
}
