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
package org.apache.rocketmq.dashboard.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LlmProxyController}.
 *
 * <p>The non-streaming proxy branches are tested with a mocked RestTemplate
 * injected via reflection. The SSE branch is tested against a local
 * {@link HttpServer} (happy path) and a connection-refused address
 * (error path), both of which complete synchronously.</p>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class LlmProxyControllerTest {

    private LlmProxyController controller;

    @Mock
    private RestTemplate restTemplate;

    @Before
    public void setUp() {
        controller = new LlmProxyController();
        ReflectionTestUtils.setField(controller, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(controller, "llmBaseUrl", "http://llm-backend:8084");
    }

    // ==================== proxy: normal exchange ====================

    @Test
    public void testProxyGetSuccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/llm/models");
        request.setQueryString("page=1");
        request.addHeader("Authorization", "Bearer token");
        request.addHeader("Host", "dashboard:8080");
        MockHttpServletResponse response = new MockHttpServletResponse();

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add("X-Custom", "yes");
        responseHeaders.add("Transfer-Encoding", "chunked");
        ResponseEntity<byte[]> backendResponse = ResponseEntity.ok()
            .headers(responseHeaders)
            .body("{\"ok\":true}".getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<byte[]>> entityCaptor =
            ArgumentCaptor.forClass((Class) HttpEntity.class);
        when(restTemplate.exchange(urlCaptor.capture(), eq(HttpMethod.GET),
            entityCaptor.capture(), eq(byte[].class))).thenReturn(backendResponse);

        ResponseEntity<byte[]> result = controller.proxy(request, response, null);

        assertEquals(200, result.getStatusCode().value());
        assertEquals("{\"ok\":true}", new String(result.getBody(), StandardCharsets.UTF_8));
        // target URL keeps path and query string
        assertEquals("http://llm-backend:8084/api/llm/models?page=1", urlCaptor.getValue());
        // request headers: Authorization forwarded, Host skipped
        HttpHeaders sentHeaders = entityCaptor.getValue().getHeaders();
        assertEquals("Bearer token", sentHeaders.getFirst("Authorization"));
        assertFalse(sentHeaders.containsKey("Host"));
        // response headers: custom kept, transfer-encoding filtered
        assertEquals("yes", result.getHeaders().getFirst("X-Custom"));
        assertFalse(result.getHeaders().containsKey("Transfer-Encoding"));
    }

    @Test
    public void testProxyPostDefaultsToJsonContentType() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/llm/chat");
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[] body = "{\"q\":\"hi\"}".getBytes(StandardCharsets.UTF_8);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<byte[]>> entityCaptor =
            ArgumentCaptor.forClass((Class) HttpEntity.class);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST),
            entityCaptor.capture(), eq(byte[].class)))
            .thenReturn(ResponseEntity.ok("ok".getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<byte[]> result = controller.proxy(request, response, body);

        assertEquals(200, result.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_JSON, entityCaptor.getValue().getHeaders().getContentType());
        assertEquals(body, entityCaptor.getValue().getBody());
    }

    // ==================== proxy: error branches ====================

    @Test
    public void testProxyHttpStatusCodeException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/llm/models");
        MockHttpServletResponse response = new MockHttpServletResponse();

        HttpClientErrorException notFound = HttpClientErrorException.create(
            HttpStatus.NOT_FOUND, "Not Found", null,
            "missing".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
            any(HttpEntity.class), eq(byte[].class))).thenThrow(notFound);

        ResponseEntity<byte[]> result = controller.proxy(request, response, null);

        assertEquals(404, result.getStatusCode().value());
        assertEquals("missing", new String(result.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    public void testProxyGenericException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/llm/models");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
            any(HttpEntity.class), eq(byte[].class)))
            .thenThrow(new RuntimeException("connection \"reset\""));

        ResponseEntity<byte[]> result = controller.proxy(request, response, null);

        assertEquals(500, result.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_JSON, result.getHeaders().getContentType());
        String json = new String(result.getBody(), StandardCharsets.UTF_8);
        assertTrue(json.contains("LLM proxy error"));
        assertTrue(json.contains("connection \\\"reset\\\""));
    }

    // ==================== proxy: SSE branch ====================

    @Test
    public void testProxySseStreamHappyPath() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/llm/chat/stream", exchange -> {
            byte[] data = "data: hello\n\ndata: world\n\n".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
            exchange.close();
        });
        server.start();
        try {
            ReflectionTestUtils.setField(controller, "llmBaseUrl",
                "http://127.0.0.1:" + server.getAddress().getPort());

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("POST");
            request.setRequestURI("/api/llm/chat/stream");
            request.addHeader("X-Trace-Id", "t-1");
            request.addHeader("Host", "dashboard:8080");
            MockHttpServletResponse response = new MockHttpServletResponse();
            byte[] body = "{\"q\":\"hi\"}".getBytes(StandardCharsets.UTF_8);

            ResponseEntity<byte[]> result = controller.proxy(request, response, body);

            assertNull(result);
            assertTrue(response.getContentType().startsWith(MediaType.TEXT_EVENT_STREAM_VALUE));
            assertEquals("no-cache", response.getHeader("Cache-Control"));
            String content = response.getContentAsString();
            assertTrue(content.contains("data: hello"));
            assertTrue(content.contains("data: world"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testProxySseStreamConnectionError() throws Exception {
        // Port 1 is never listening: the SSE branch must emit an error event.
        ReflectionTestUtils.setField(controller, "llmBaseUrl", "http://127.0.0.1:1");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/llm/chat/stream");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<byte[]> result = controller.proxy(request, response, null);

        assertNull(result);
        assertTrue(response.getContentType().startsWith(MediaType.TEXT_EVENT_STREAM_VALUE));
        assertTrue(response.getContentAsString().startsWith("event: error"));
    }
}
