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
package org.apache.rocketmq.studio.ops.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.ops.ai.auth.McpAuthentication;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolDiscoveryService;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ToolControllerTest {

    private ToolDiscoveryService toolDiscoveryService;
    private ToolExecutionService toolExecutor;
    private ObjectMapper objectMapper;
    private McpAuthentication authentication;
    private ToolController controller;
    private McpToolController mcpController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        toolDiscoveryService = mock(ToolDiscoveryService.class);
        toolExecutor = mock(ToolExecutionService.class);
        objectMapper = new ObjectMapper();
        authentication = new McpAuthentication("instance-id", "access-key");
        controller = new ToolController(toolDiscoveryService, toolExecutor, objectMapper);
        mcpController = new McpToolController(toolExecutor, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller, mcpController)
                .setControllerAdvice(new ToolExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void listToolsReturnsCatalogEntriesTest() throws Exception {
        AiToolVO tool = AiToolVO.builder()
                .name("rmq.cluster.list")
                .version("1.0.0")
                .cli(Map.of("resource", "cluster", "verb", "list"))
                .description("List clusters")
                .parameters(Map.of("type", "object"))
                .riskLevel("L1")
                .operationLevel("L1_READ_ONLY")
                .permission("cluster:read")
                .requiredCapabilities(Collections.emptyList())
                .outputSchema(Map.of("type", "array"))
                .viewHint("table")
                .build();
        when(toolDiscoveryService.listTools("instance-id")).thenReturn(List.of(tool));

        mockMvc.perform(get("/api/ai/tools").queryParam("instanceId", "instance-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("rmq.cluster.list"))
                .andExpect(jsonPath("$.data[0].version").value("1.0.0"));
    }

    @Test
    void listToolsDelegatesTheSelectedTargetTest() throws Exception {
        when(toolDiscoveryService.listTools("cluster-001"))
                .thenReturn(Collections.emptyList());
        when(toolDiscoveryService.listTools("instance-id"))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/ai/tools").queryParam("cluster", "cluster-001"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ai/tools")
                        .queryParam("instanceId", "instance-id")
                        .queryParam("cluster", "cluster-002"))
                .andExpect(status().isOk());

        verify(toolDiscoveryService).listTools("cluster-001");
        verify(toolDiscoveryService).listTools("instance-id");
    }

    /** The global-tool scope of the AI page lists platform tools without binding an Instance. */
    @Test
    void listToolsAcceptsAMissingTargetForPlatformToolsTest() throws Exception {
        when(toolDiscoveryService.listTools(null)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/ai/tools"))
                .andExpect(status().isOk());

        verify(toolDiscoveryService).listTools(null);
    }

    @Test
    void executeToolPreservesStructuredInputAndDottedNameTest() throws Exception {
        Map<String, Object> input = Map.of("instanceId", "instance-id");
        Map<String, Object> output = Map.of(
                "instanceId", "instance-id",
                "capabilities", List.of("REMOTING"));
        when(toolExecutor.execute("rmq.instance.capabilities", input))
                .thenReturn(output);

        mockMvc.perform(post("/api/ai/tools/rmq.instance.capabilities/execute")
                        .queryParam("instanceId", "instance-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"instanceId":"instance-id"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.instanceId").value("instance-id"))
                .andExpect(jsonPath("$.data.capabilities[0]").value("REMOTING"));

        verify(toolExecutor).execute("rmq.instance.capabilities", input);
    }

    /** Platform tools are addressed by a physical clusterName, so the target stays out of their payload. */
    @Test
    void executeToolKeepsPlatformToolArgumentsUntouchedTest() throws Exception {
        Map<String, Object> input = Map.of("clusterName", "DefaultCluster");
        when(toolExecutor.execute("rmq.broker.list", input))
                .thenReturn(Map.of("items", Collections.emptyList()));

        mockMvc.perform(post("/api/ai/tools/rmq.broker.list/execute")
                        .queryParam("instanceId", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clusterName":"DefaultCluster"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());

        verify(toolExecutor).execute("rmq.broker.list", input);
    }

    @Test
    void callToolDelegatesAuthenticatedInputTest() throws Exception {
        Map<String, Object> input = Map.of(
                "instanceId", "instance-id",
                "topic", "order-topic",
                "dry_run", true);
        when(toolExecutor.execute("rmq.topic.create", input, authentication))
                .thenReturn(Map.of("status", "PLANNED"));

        mockMvc.perform(post("/api/mcp/tools/call")
                        .requestAttr(McpAuthentication.ATTRIBUTE, authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "rmq.topic.create",
                                  "arguments": {
                                    "instanceId": "instance-id",
                                    "topic": "order-topic",
                                    "dry_run": true
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PLANNED"));

        verify(toolExecutor).execute(
                eq("rmq.topic.create"), argThat(arguments ->
                        Boolean.TRUE.equals(arguments.get("dry_run"))), eq(authentication));
    }

}
