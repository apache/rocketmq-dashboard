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
package org.apache.rocketmq.studio.ops.ai.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import org.apache.rocketmq.studio.ops.ai.auth.McpAuthenticationFilter;
import org.apache.rocketmq.studio.ops.ai.auth.McpAuthenticator;
import org.apache.rocketmq.studio.ops.ai.auth.McpAuthentication;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpConfigurationTest {

    private static final McpAuthentication AUTHENTICATION =
            new McpAuthentication("instance-id", "access-key");

    @Test
    void registersAuthenticationFilterForMcpAndRmqctlEntryPoints() {
        McpServerStreamableHttpProperties properties =
                mock(McpServerStreamableHttpProperties.class);
        when(properties.getMcpEndpoint()).thenReturn("/custom/mcp");

        FilterRegistrationBean<McpAuthenticationFilter> registration =
                new McpConfiguration().mcpAuthenticationFilterRegistration(
                        mock(McpAuthenticator.class), new ObjectMapper(), properties);

        assertThat(registration.getUrlPatterns()).containsExactlyInAnyOrder(
                "/custom/mcp",
                "/api/mcp/tools/call");
    }

    @Test
    void extractsAuthenticationFromServletRequest() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(
                McpAuthentication.ATTRIBUTE, AUTHENTICATION);
        ServerRequest request = ServerRequest.create(
                servletRequest, List.of(new StringHttpMessageConverter()));

        McpTransportContext context = McpConfiguration.extractContext(request);

        assertThat(context.get(McpAuthentication.ATTRIBUTE))
                .isSameAs(AUTHENTICATION);
    }

}
