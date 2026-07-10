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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;import org.apache.rocketmq.dashboard.aspect.admin.annotation.OriginalControllerReturnValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Enumeration;
import java.util.List;

/**
 * LLM Proxy Controller
 */
@RequestMapping("/api/llm")
@Controller
public class LlmProxyController {

    private static final Logger logger = LoggerFactory.getLogger(LlmProxyController.class);
    private static final List<String> SKIP_HEADERS = List.of(
            "host", "connection", "content-length", "accept-encoding", "transfer-encoding");

    @Value("${rocketmq.dashboard.llm.baseUrl:http://localhost:8084}")
    private String llmBaseUrl;
    private final RestTemplate restTemplate = new RestTemplate();
    private final HttpClient sseClent = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * LLM Proxy
     * @param request HttpServletRequest
     * @param body request body
     * @return ResponseEntity
     */
    @OriginalControllerReturnValue
    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public ResponseEntity<byte[]> proxy(HttpServletRequest request,
                                        HttpServletResponse  response,
                                        @RequestBody(required = false) byte[]  body) {
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        if ("GET".equalsIgnoreCase(request.getMethod()) && requestPath.endsWith("/chat/stream")) {
            handleSseStream(request, response);
            return null;
        }
        String targetUrl = buildTargetUrl(request);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        HttpHeaders headers = copyHeaders(request);
        HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<byte[]> responseEntity = restTemplate.exchange(targetUrl, method, entity, byte[].class);
            return ResponseEntity.status(responseEntity.getStatusCode())
                    .headers(filterResponseHeaders(responseEntity.getHeaders()))
                    .body(responseEntity.getBody());
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .headers(filterResponseHeaders(e.getResponseHeaders()))
                    .body(e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            logger.error("Failed to call LLM API {} : {}", targetUrl, e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(("LLM proxy error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }


    /**
     * LLM SSE Stream
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     */
    private void handleSseStream(HttpServletRequest request, HttpServletResponse  response) {
        String targetUrl = buildTargetUrl(request);
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(java.net.URI.create(targetUrl))
                .timeout(Duration.ofMinutes(10))
                .header("Accept", "text/event-stream");
        conyHeadersToHttpClient(request, builder);

        try {
            HttpResponse<java.io.InputStream> httpResponse =
                    sseClent.send(builder.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(httpResponse.body(), StandardCharsets.UTF_8));
                 OutputStream out = response.getOutputStream()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            }
        } catch (Exception e) {
            logger.error("Error processing LLM SSE stream: {}", e.getMessage(), e);
            try {
                OutputStream out = response.getOutputStream();
                out.write(("event: error\ndata: {\"message\":\"" + e.getMessage() + "\"}\n\n")
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException ex) {

            }
        }
    }

    private String buildTargetUrl(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();
        String path = requestUri.substring(request.getContextPath().length());
        StringBuilder url = new StringBuilder(llmBaseUrl);
        if (!llmBaseUrl.endsWith("/") && !path.startsWith("/")) {
            url.append("/");
        }
        url.append(path);
        if (queryString != null) {
            url.append("?").append(queryString);
        }
        return url.toString();
    }

    private HttpHeaders copyHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (SKIP_HEADERS.contains(name.toLowerCase())) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                headers.add(name, values.nextElement());
            }
        }
        if (headers.getContentType() == null && "POST".equalsIgnoreCase(request.getMethod())) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return headers;
    }

    private void conyHeadersToHttpClient(HttpServletRequest request, HttpRequest.Builder builder) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (SKIP_HEADERS.contains(name.toLowerCase())) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                builder.header(name, values.nextElement());
            }
        }
    }

    private HttpHeaders filterResponseHeaders(HttpHeaders headers) {
        if (headers == null) {
            return new HttpHeaders();
        }
        HttpHeaders filtered = new HttpHeaders();
        headers.forEach((name, values) -> {
            if (!SKIP_HEADERS.contains(name.toLowerCase())) {
                filtered.put(name, values);
            }
        });
        return filtered;
    }
}
