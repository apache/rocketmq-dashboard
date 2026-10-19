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
package org.apache.rocketmq.studio.common.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Bounds response bodies before message converters or HTTP error handlers can buffer them. */
public final class BoundedResponseBodyInterceptor implements ClientHttpRequestInterceptor {

    private final int maxResponseBytes;

    public BoundedResponseBodyInterceptor(int maxResponseBytes) {
        if (maxResponseBytes <= 0 || maxResponseBytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Response body limit must be positive and less than Integer.MAX_VALUE");
        }
        this.maxResponseBytes = maxResponseBytes;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        try {
            byte[] boundedBody;
            // Close the upstream stream even on rejection, before response.close() can try to drain it.
            try (InputStream stream = response.getBody()) {
                if (response.getHeaders().getContentLength() > maxResponseBytes) {
                    throw new ResponseTooLargeException(maxResponseBytes);
                }
                // The extra byte distinguishes an exact-boundary body from an oversized/chunked one.
                boundedBody = stream.readNBytes(maxResponseBytes + 1);
                if (boundedBody.length > maxResponseBytes) {
                    throw new ResponseTooLargeException(maxResponseBytes);
                }
            }
            return new BufferedResponse(response, boundedBody);
        } catch (IOException | RuntimeException exception) {
            response.close();
            throw exception;
        }
    }

    public static final class ResponseTooLargeException extends RestClientException {
        public ResponseTooLargeException(int maxResponseBytes) {
            super("HTTP response body exceeds " + maxResponseBytes + " bytes");
        }
    }

    private static final class BufferedResponse implements ClientHttpResponse {
        private final ClientHttpResponse response;
        private final InputStream body;

        private BufferedResponse(ClientHttpResponse response, byte[] body) {
            this.response = response;
            this.body = new ByteArrayInputStream(body);
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return response.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return response.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return response.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return body;
        }

        @Override
        public void close() {
            response.close();
        }
    }
}
