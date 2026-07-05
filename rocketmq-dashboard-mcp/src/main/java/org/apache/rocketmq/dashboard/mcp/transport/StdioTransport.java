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
package org.apache.rocketmq.dashboard.mcp.transport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stdio-based MCP transport.
 * Reads JSON-RPC messages line-by-line from System.in and writes responses to System.out.
 * Runs in a loop on a daemon thread.
 */
public class StdioTransport implements McpTransport {

    private static final Logger log = LoggerFactory.getLogger(StdioTransport.class);

    private McpMessageHandler handler;
    private volatile boolean running = false;
    private Thread readerThread;

    @Override
    public void start(McpMessageHandler handler) {
        this.handler = handler;
        this.running = true;

        readerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    log.debug("Received: {}", line);
                    try {
                        String response = this.handler.handleMessage(line);
                        if (response != null) {
                            sendMessage(response);
                        }
                    } catch (Exception e) {
                        log.error("Error handling message: {}", e.getMessage(), e);
                        String errorResponse = String.format(
                                "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"%s\"},\"id\":null}",
                                escapeJson(e.getMessage()));
                        sendMessage(errorResponse);
                    }
                }
            } catch (IOException e) {
                if (running) {
                    log.error("Error reading from stdin: {}", e.getMessage(), e);
                }
            } finally {
                log.info("Stdio transport reader thread exiting");
            }
        }, "mcp-stdio-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        log.info("StdioTransport started");
    }

    @Override
    public void stop() {
        running = false;
        if (readerThread != null) {
            readerThread.interrupt();
        }
        log.info("StdioTransport stopped");
    }

    @Override
    public void sendMessage(String jsonMessage) {
        synchronized (System.out) {
            System.out.println(jsonMessage);
            System.out.flush();
        }
        log.debug("Sent: {}", jsonMessage);
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
