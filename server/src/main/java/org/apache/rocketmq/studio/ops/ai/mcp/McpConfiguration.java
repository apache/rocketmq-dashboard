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
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.apache.rocketmq.studio.ops.ai.auth.McpAuthenticationFilter;
import org.apache.rocketmq.studio.ops.ai.auth.McpAuthenticator;
import org.apache.rocketmq.studio.ops.ai.auth.McpAuthentication;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.instance.InstanceResolver;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialRepository;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolExecutionService;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.ServerRequest;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true")
public class McpConfiguration {

    private static final String TOOL_CALL_ENDPOINT = "/api/mcp/tools/call";

    @Bean
    public McpAuthenticator mcpAuthenticator(
            InstanceResolver instanceResolver,
            RuntimeAdminClientResolver adminClientResolver,
            CloudCredentialRepository cloudCredentialRepository) {
        return new McpAuthenticator(instanceResolver, adminClientResolver, cloudCredentialRepository);
    }

    @Bean
    public FilterRegistrationBean<McpAuthenticationFilter> mcpAuthenticationFilterRegistration(
            McpAuthenticator authenticator,
            ObjectMapper objectMapper,
            McpServerStreamableHttpProperties serverProperties) {
        FilterRegistrationBean<McpAuthenticationFilter> registration =
                new FilterRegistrationBean<>(new McpAuthenticationFilter(authenticator, objectMapper));
        registration.addUrlPatterns(
                serverProperties.getMcpEndpoint(),
                TOOL_CALL_ENDPOINT);
        return registration;
    }

    @Bean
    public WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
            @Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper,
            McpServerStreamableHttpProperties serverProperties) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .mcpEndpoint(serverProperties.getMcpEndpoint())
                .keepAliveInterval(serverProperties.getKeepAliveInterval())
                .disallowDelete(serverProperties.isDisallowDelete())
                .contextExtractor(McpConfiguration::extractContext)
                .build();
    }

    @Bean
    public List<McpServerFeatures.SyncToolSpecification> toolSpecifications(
            ToolCatalog toolCatalog,
            ToolExecutionService toolExecutor,
            ObjectMapper objectMapper) {
        return McpToolRegistrar.toToolSpecifications(toolCatalog, toolExecutor, objectMapper);
    }

    static McpTransportContext extractContext(ServerRequest request) {
        Object authentication = request.servletRequest()
                .getAttribute(McpAuthentication.ATTRIBUTE);
        if (!(authentication instanceof McpAuthentication mcpAuthentication)) {
            return McpTransportContext.EMPTY;
        }
        return McpTransportContext.create(Map.of(
                McpAuthentication.ATTRIBUTE, mcpAuthentication));
    }
}
