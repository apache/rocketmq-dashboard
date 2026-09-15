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
package org.apache.rocketmq.studio;

import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolDiscoveryService;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.instance.MybatisPlusInstanceRepository;
import org.apache.rocketmq.studio.persistence.mapper.RmqInstanceMapper;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "studio.auth.login-required=false")
@AutoConfigureMockMvc
class StudioApplicationTest {

    @Autowired
    private ToolCatalog toolCatalog;

    @Autowired
    private ToolDiscoveryService toolDiscoveryService;

    @Autowired
    private RmqInstanceMapper instanceMapper;

    @Autowired
    private MybatisPlusInstanceRepository instanceRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private McpServerProperties mcpServerProperties;

    @Autowired
    private McpServerStreamableHttpProperties mcpServerStreamableHttpProperties;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private WebMvcStreamableServerTransportProvider mcpTransportProvider;

    @Test
    void applicationContextLoadsWithInitializedDevSchema() throws Exception {
        assertThat(toolCatalog.list()).isNotEmpty();
        assertThatThrownBy(() -> toolDiscoveryService.listTools(null))
                .isInstanceOf(ToolExecutionException.class);
        assertThat(instanceMapper.selectList(null)).isEmpty();

        mockMvc.perform(get("/api/instances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void mcpServerUsesSingleStreamableHttpEndpoint() {
        assertThat(mcpServerProperties.getProtocol())
                .isEqualTo(McpServerProperties.ServerProtocol.STREAMABLE);
        assertThat(mcpServerStreamableHttpProperties.getMcpEndpoint())
                .isEqualTo("/api/mcp");
        assertThat(applicationContext.containsBean("webMvcStreamableServerTransportProvider"))
                .isTrue();
        assertThat(applicationContext.containsBean("webMvcSseServerTransportProvider"))
                .isFalse();
    }

    @Test
    void streamableHttpEndpointNegotiatesAnMcpSession() throws Exception {
        String protocolVersion = mcpTransportProvider.protocolVersions().getFirst();
        MockMvc transportMockMvc = MockMvcBuilders
                .routerFunctions(mcpTransportProvider.getRouterFunction())
                .build();

        transportMockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/mcp")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .accept(
                                org.springframework.http.MediaType.APPLICATION_JSON,
                                org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "jsonrpc": "2.0",
                                  "id": 1,
                                  "method": "initialize",
                                  "params": {
                                    "protocolVersion": "%s",
                                    "capabilities": {},
                                    "clientInfo": {"name": "studio-test", "version": "1.0"}
                                  }
                                }
                                """.formatted(protocolVersion)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().exists("Mcp-Session-Id"))
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.result.protocolVersion").value(protocolVersion));
    }

    @Test
    void instanceRepositoryPersistsAdminCredentialReference() {
        InstanceVO instance = InstanceVO.builder()
                .name("acl-admin-ref-roundtrip")
                .type(InstanceType.DIRECT)
                .vendor(InstanceVendor.APACHE)
                .endpoint("127.0.0.1:9876")
                .adminCredentialRef("production-admin")
                .build();

        instanceRepository.save(instance);

        assertThat(instanceMapper.selectById(instance.getId()).getAdminCredentialRef())
                .isEqualTo("production-admin");
        assertThat(instanceRepository.findById(instance.getId()))
                .get()
                .extracting(InstanceVO::getAdminCredentialRef)
                .isEqualTo("production-admin");

        instanceRepository.deleteById(instance.getId());
    }
}
