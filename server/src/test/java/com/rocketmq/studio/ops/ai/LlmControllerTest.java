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

package com.rocketmq.studio.ops.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LlmController.class)
@AutoConfigureMockMvc(addFilters = false)
class LlmControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LlmConfigService llmConfigService;

    @Test
    void getConfigShouldReturnFrontendContractShape() throws Exception {
        when(llmConfigService.getConfig()).thenReturn(LlmConfigVO.builder()
                .provider("openai")
                .apiKey("sk-test")
                .apiBase("https://api.openai.com/v1")
                .model("gpt-4o")
                .maxTokens(4096)
                .temperature(0.7)
                .enabled(true)
                .build());

        mockMvc.perform(get("/api/llm/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("openai"))
                .andExpect(jsonPath("$.apiBase").value("https://api.openai.com/v1"))
                .andExpect(jsonPath("$.model").value("gpt-4o"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void saveConfigShouldReturnStatusZero() throws Exception {
        LlmConfigVO config = LlmConfigVO.builder()
                .provider("openai")
                .apiKey("sk-test")
                .model("gpt-4o")
                .enabled(true)
                .build();

        mockMvc.perform(post("/api/llm/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.msg").value("saved"));

        verify(llmConfigService).saveConfig(any(LlmConfigVO.class));
    }

    @Test
    void testConfigShouldReturnOperationResult() throws Exception {
        when(llmConfigService.testConfig(any(LlmConfigVO.class)))
                .thenReturn(LlmOperationResultVO.success("Connection successful"));

        mockMvc.perform(post("/api/llm/config/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LlmConfigVO.builder()
                                .provider("ollama")
                                .model("llama3")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.msg").value("Connection successful"));
    }

    @Test
    void listModelsShouldReturnStatusAndData() throws Exception {
        when(llmConfigService.listModels()).thenReturn(new LlmModelsResultVO(0, List.of(
                new LlmModelItemVO("gpt-4o", "GPT-4o"),
                new LlmModelItemVO("gpt-4", "GPT-4"))));

        mockMvc.perform(get("/api/llm/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.data[0].id").value("gpt-4o"))
                .andExpect(jsonPath("$.data[1].name").value("GPT-4"));
    }
}
