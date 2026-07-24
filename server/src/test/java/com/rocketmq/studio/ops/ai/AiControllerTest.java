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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiControllerTest {

    private static final String DIGEST = "a".repeat(64);

    private AiService aiService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        aiService = mock(AiService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AiController(aiService)).build();

        when(aiService.catalogVersion()).thenReturn("1.0.0");
        when(aiService.catalogDigest()).thenReturn(DIGEST);
        when(aiService.minimumClientVersion()).thenReturn("1.0.0");
    }

    @Test
    void listToolsKeepsTheExistingBodyAndAddsCatalogHeaders() throws Exception {
        AiToolVO tool = AiToolVO.builder()
                .name("rmq.cluster.list")
                .description("List clusters")
                .parameters(Map.of("type", "object"))
                .riskLevel("L1")
                .permission("cluster:read")
                .requiredCapabilities(Collections.emptyList())
                .outputSchema(Map.of("type", "array"))
                .viewHint("table")
                .deprecated(false)
                .build();
        when(aiService.listTools()).thenReturn(List.of(tool));

        mockMvc.perform(get("/api/ai/tools"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RMQ-Catalog-Version", "1.0.0"))
                .andExpect(header().string("X-RMQ-Catalog-Digest", DIGEST))
                .andExpect(header().string("X-RMQ-Minimum-Client-Version", "1.0.0"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data[0].name").value("rmq.cluster.list"))
                .andExpect(jsonPath("$.data[0].parameters.type").value("object"))
                .andExpect(jsonPath("$.data[0].riskLevel").value("L1"))
                .andExpect(jsonPath("$.data[0].permission").value("cluster:read"))
                .andExpect(jsonPath("$.data[0].viewHint").value("table"));
    }

    @Test
    void listToolsDelegatesTheSelectedCluster() throws Exception {
        when(aiService.listTools("cluster-001")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/ai/tools").queryParam("cluster", "cluster-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(aiService).listTools("cluster-001");
    }

    @Test
    void executeToolPreservesStructuredInputAndDottedName() throws Exception {
        Map<String, Object> input = Map.of("cluster", "cluster-001");
        Map<String, Object> output = Map.of(
                "cluster", "cluster-001",
                "capabilities", List.of("GRPC"));
        when(aiService.executeTool("rmq.capabilities", input)).thenReturn(output);

        mockMvc.perform(post("/api/ai/tools/rmq.capabilities/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cluster":"cluster-001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.cluster").value("cluster-001"))
                .andExpect(jsonPath("$.data.capabilities[0]").value("GRPC"));

        verify(aiService).executeTool("rmq.capabilities", input);
    }

    @Test
    void executeClusterListNormalizesAnAbsentBody() throws Exception {
        when(aiService.executeTool("rmq.cluster.list", Collections.emptyMap()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(post("/api/ai/tools/rmq.cluster.list/execute")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(aiService).executeTool("rmq.cluster.list", Collections.emptyMap());
    }
}
