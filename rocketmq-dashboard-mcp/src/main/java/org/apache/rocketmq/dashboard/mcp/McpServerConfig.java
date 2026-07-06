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
package org.apache.rocketmq.dashboard.mcp;

import org.apache.rocketmq.dashboard.mcp.resources.ResourceProvider;
import org.apache.rocketmq.dashboard.mcp.tools.McpToolRegistry;
import org.apache.rocketmq.dashboard.mcp.tools.SecurityGate;
import org.apache.rocketmq.dashboard.mcp.transport.SseTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for MCP server components in SSE mode.
 * <p>
 * Registers the core MCP components ({@link SecurityGate}, {@link McpToolRegistry},
 * {@link ResourceProvider}, {@link McpProtocolHandler}) as Spring beans and
 * initializes the {@link SseTransport} with the protocol handler via a
 * {@link CommandLineRunner}.
 * <p>
 * This configuration is only active when the MCP server is started in SSE mode
 * (i.e., when the Spring Boot context is launched by
 * {@link McpServerApplication#main}).
 */
@Configuration
public class McpServerConfig {

    private static final Logger log = LoggerFactory.getLogger(McpServerConfig.class);

    @Value("${mcp.enableDangerousOps:false}")
    private boolean enableDangerousOps;

    @Bean
    public SecurityGate securityGate() {
        log.info("Creating SecurityGate with enableDangerousOps={}", enableDangerousOps);
        return new SecurityGate(enableDangerousOps);
    }

    @Bean
    public McpToolRegistry mcpToolRegistry(SecurityGate securityGate) {
        return new McpToolRegistry(securityGate);
    }

    @Bean
    public ResourceProvider resourceProvider() {
        return new ResourceProvider();
    }

    @Bean
    public McpProtocolHandler mcpProtocolHandler(McpToolRegistry toolRegistry,
                                                  ResourceProvider resourceProvider) {
        return new McpProtocolHandler(toolRegistry, resourceProvider);
    }

    /**
     * Initialize the SseTransport with the McpProtocolHandler after the
     * Spring context is fully started. This bridges the gap between the
     * Spring-managed SseTransport bean and the protocol handler.
     */
    @Bean
    public CommandLineRunner initSseTransport(SseTransport sseTransport,
                                               McpProtocolHandler protocolHandler) {
        return args -> {
            log.info("Initializing SseTransport with McpProtocolHandler");
            sseTransport.start(protocolHandler);
        };
    }
}