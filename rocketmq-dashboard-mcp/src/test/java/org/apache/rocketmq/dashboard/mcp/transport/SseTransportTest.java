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

import org.junit.Test;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class SseTransportTest {

    @Test
    public void testClassExists() {
        SseTransport transport = new SseTransport();
        assertNotNull("SseTransport should be instantiable", transport);
    }

    @Test
    public void testHasRestControllerAnnotation() {
        assertTrue("SseTransport should have @RestController annotation",
                SseTransport.class.isAnnotationPresent(RestController.class));
    }

    @Test
    public void testImplementsMcpTransport() {
        assertTrue("SseTransport should implement McpTransport",
                McpTransport.class.isAssignableFrom(SseTransport.class));
    }

    @Test
    public void testStartStopNoOp() {
        SseTransport transport = new SseTransport();
        // Start with null handler should not throw
        transport.start(null);
        // Stop should not throw
        transport.stop();
    }

    @Test
    public void testStartWithHandler() {
        SseTransport transport = new SseTransport();
        McpMessageHandler handler = new McpMessageHandler() {
            @Override
            public String handleMessage(String jsonMessage) {
                return "{\"jsonrpc\":\"2.0\",\"result\":\"ok\",\"id\":\"1\"}";
            }
        };
        transport.start(handler);
        // Start with a handler should not throw
        transport.stop();
    }

    @Test
    public void testSubscribeReturnsSseEmitter() {
        SseTransport transport = new SseTransport();
        SseEmitter emitter = transport.subscribe();
        assertNotNull("subscribe should return a non-null SseEmitter", emitter);
    }

    @Test
    public void testHandleClientMessageWithoutHandler() {
        SseTransport transport = new SseTransport();
        // Without starting the transport, handler is null
        String response = transport.handleClientMessage("{\"jsonrpc\":\"2.0\",\"method\":\"initialize\",\"id\":\"1\"}");
        assertNotNull("Response should not be null", response);
        assertTrue("Response should contain error when transport not started",
                response.contains("error"));
        assertTrue("Should indicate transport not started",
                response.contains("Transport not started") || response.contains("-32603"));
    }

    @Test
    public void testHandleClientMessageWithHandler() {
        SseTransport transport = new SseTransport();
        McpMessageHandler handler = new McpMessageHandler() {
            @Override
            public String handleMessage(String jsonMessage) {
                return "{\"jsonrpc\":\"2.0\",\"result\":{\"serverInfo\":{\"name\":\"test\"}},\"id\":\"1\"}";
            }
        };
        transport.start(handler);

        String response = transport.handleClientMessage(
                "{\"jsonrpc\":\"2.0\",\"method\":\"initialize\",\"id\":\"1\"}");
        assertNotNull("Response should not be null", response);
        assertTrue("Response should contain result", response.contains("result"));
        transport.stop();
    }

    @Test
    public void testSendMessageDoesNotThrow() {
        SseTransport transport = new SseTransport();
        // sendMessage with no emitters should not throw
        transport.sendMessage("{\"test\":\"message\"}");
    }

    @Test
    public void testSubscribeCreatesEmitterWithHandler() {
        SseTransport transport = new SseTransport();
        McpMessageHandler handler = new McpMessageHandler() {
            @Override
            public String handleMessage(String jsonMessage) {
                return "{\"jsonrpc\":\"2.0\",\"result\":\"ok\",\"id\":\"1\"}";
            }
        };
        transport.start(handler);

        SseEmitter emitter = transport.subscribe();
        assertNotNull("subscribe should return SseEmitter even with handler set", emitter);
        transport.stop();
    }

    @Test
    public void testStopMultipleTimesDoesNotThrow() {
        SseTransport transport = new SseTransport();
        transport.stop();
        transport.stop(); // Second stop should not throw
    }

    @Test
    public void testHandleClientMessageWithEmptyMessage() {
        SseTransport transport = new SseTransport();
        McpMessageHandler handler = new McpMessageHandler() {
            @Override
            public String handleMessage(String jsonMessage) {
                return "{\"jsonrpc\":\"2.0\",\"result\":\"empty\",\"id\":\"1\"}";
            }
        };
        transport.start(handler);

        String response = transport.handleClientMessage("");
        assertNotNull("Response should not be null for empty message", response);
        transport.stop();
    }

    @Test
    public void testHandleClientMessageWithException() {
        SseTransport transport = new SseTransport();
        McpMessageHandler handler = new McpMessageHandler() {
            @Override
            public String handleMessage(String jsonMessage) {
                throw new RuntimeException("Test exception");
            }
        };
        transport.start(handler);

        String response = transport.handleClientMessage("{\"test\":\"data\"}");
        assertNotNull("Response should not be null even on exception", response);
        assertTrue("Response should contain error on exception",
                response.contains("error"));
        assertTrue("Response should contain error code",
                response.contains("-32603"));
        transport.stop();
    }

    @Test
    public void testHandleClientMessageNullResponseFromHandler() {
        SseTransport transport = new SseTransport();
        McpMessageHandler handler = new McpMessageHandler() {
            @Override
            public String handleMessage(String jsonMessage) {
                return null;
            }
        };
        transport.start(handler);

        String response = transport.handleClientMessage("{\"test\":\"data\"}");
        assertNotNull("Response should not be null when handler returns null", response);
        assertTrue("Response should contain error when handler returns null",
                response.contains("error") || response.contains("No response"));
        transport.stop();
    }
}
