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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE-based MCP transport.
 * Provides an SSE endpoint for server-to-client streaming events
 * and a POST endpoint for client-to-server JSON-RPC messages.
 */
@Service
@RestController
public class SseTransport implements McpTransport {

    private static final Logger log = LoggerFactory.getLogger(SseTransport.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private McpMessageHandler handler;

    @Override
    public void start(McpMessageHandler handler) {
        this.handler = handler;
        log.info("SseTransport started");
    }

    @Override
    public void stop() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("Error completing SSE emitter: {}", e.getMessage());
            }
        }
        emitters.clear();
        log.info("SseTransport stopped");
    }

    @Override
    public void sendMessage(String jsonMessage) {
        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name("message")
                .data(jsonMessage, MediaType.APPLICATION_JSON);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(event);
            } catch (IOException e) {
                log.warn("Error sending SSE event, removing emitter: {}", e.getMessage());
                emitters.remove(emitter);
            }
        }
    }

    /**
     * SSE endpoint for clients to subscribe to server-to-client events.
     */
    @CrossOrigin
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data("/mcp/message"));
        } catch (IOException e) {
            log.error("Error sending endpoint event: {}", e.getMessage());
            emitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * POST endpoint for client-to-server JSON-RPC messages.
     */
    @CrossOrigin
    @PostMapping(value = "/mcp/message", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public String handleClientMessage(@RequestBody String jsonMessage) {
        log.debug("Received POST: {}", jsonMessage);
        if (handler == null) {
            return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Transport not started\"},\"id\":null}";
        }
        try {
            String response = handler.handleMessage(jsonMessage);
            // Also send via SSE for streaming clients
            if (response != null && !emitters.isEmpty()) {
                sendMessage(response);
            }
            return response != null ? response
                    : "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"No response\"},\"id\":null}";
        } catch (Exception e) {
            log.error("Error handling POST message: {}", e.getMessage(), e);
            return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"},\"id\":null}";
        }
    }
}
