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
import org.apache.rocketmq.dashboard.mcp.transport.McpTransport;
import org.apache.rocketmq.dashboard.mcp.transport.StdioTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP Server entry point for RocketMQ Dashboard.
 * <p>
 * Supports two transport modes:
 * <ul>
 *   <li><b>stdio</b> — Standard input/output (default), used by MCP clients like Claude Desktop</li>
 *   <li><b>sse</b> — Server-Sent Events over HTTP, for web-based MCP clients</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 *   java -jar rocketmq-dashboard-mcp.jar                          # stdio mode (default)
 *   java -jar rocketmq-dashboard-mcp.jar --transport sse           # SSE mode on port 8083
 *   java -jar rocketmq-dashboard-mcp.jar --transport sse --port 9090  # SSE mode on custom port
 *   java -jar rocketmq-dashboard-mcp.jar --enable-dangerous-ops    # Allow L3 (dangerous) operations
 * </pre>
 */
@SpringBootApplication
public class McpServerApplication {

    private static final Logger log = LoggerFactory.getLogger(McpServerApplication.class);

    public static void main(String[] args) {
        // Parse command-line arguments
        String transport = "stdio";
        int port = 8083;
        boolean enableDangerousOps = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--transport":
                    if (i + 1 < args.length) {
                        transport = args[++i];
                    }
                    break;
                case "--port":
                    if (i + 1 < args.length) {
                        try {
                            port = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            log.warn("Invalid port number: {}", args[i]);
                        }
                    }
                    break;
                case "--enable-dangerous-ops":
                    enableDangerousOps = true;
                    break;
                case "--help":
                case "-h":
                    printUsage();
                    return;
                default:
                    break;
            }
        }

        log.info("Starting MCP server: transport={}, port={}, dangerousOps={}",
                transport, port, enableDangerousOps);

        // Create security gate
        SecurityGate securityGate = new SecurityGate(enableDangerousOps);

        // Create MCP components
        McpToolRegistry toolRegistry = new McpToolRegistry(securityGate);
        ResourceProvider resourceProvider = new ResourceProvider();
        McpProtocolHandler protocolHandler = new McpProtocolHandler(toolRegistry, resourceProvider);

        if ("sse".equalsIgnoreCase(transport)) {
            // SSE mode: use Spring Boot with SseTransport as a @Service
            System.setProperty("server.port", String.valueOf(port));
            log.info("Starting SSE transport on port {}", port);
            SpringApplication.run(McpServerApplication.class, args);
        } else {
            // Stdio mode: use StdioTransport directly, no Spring context
            log.info("Starting stdio transport");
            McpTransport stdioTransport = new StdioTransport();
            stdioTransport.start(protocolHandler);

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down MCP server");
                stdioTransport.stop();
            }, "mcp-shutdown"));

            // Keep main thread alive while daemon reader thread runs
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                log.info("Main thread interrupted, shutting down");
                stdioTransport.stop();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void printUsage() {
        System.out.println("RocketMQ Dashboard MCP Server");
        System.out.println();
        System.out.println("Usage: java -jar rocketmq-dashboard-mcp.jar [OPTIONS]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --transport <stdio|sse>    Transport mode (default: stdio)");
        System.out.println("  --port <port>              HTTP port for SSE mode (default: 8083)");
        System.out.println("  --enable-dangerous-ops     Allow L3 dangerous operations");
        System.out.println("  --help, -h                 Show this help message");
    }
}
