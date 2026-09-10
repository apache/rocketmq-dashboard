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
package org.apache.rocketmq.studio.settings;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceClientHttpRequestFactoryTest {

    @Test
    void prepareConnectionShouldDisableRedirectsTest() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://example.com")
                .toURL().openConnection();
        TestableDataSourceClientHttpRequestFactory requestFactory =
                new TestableDataSourceClientHttpRequestFactory();

        assertThat(connection.getInstanceFollowRedirects()).isTrue();

        requestFactory.prepare(connection, HttpMethod.GET.name());

        assertThat(connection.getInstanceFollowRedirects()).isFalse();
        connection.disconnect();
    }

    private static class TestableDataSourceClientHttpRequestFactory
            extends DataSourceClientHttpRequestFactory {

        void prepare(HttpURLConnection connection, String httpMethod) throws Exception {
            prepareConnection(connection, httpMethod);
        }
    }

    @Test
    void doesNotFollowHttpRedirectsEndToEndTest() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicBoolean redirectedEndpointHit = new AtomicBoolean(false);
        server.createContext("/target", exchange -> {
            redirectedEndpointHit.set(true);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/start", exchange -> {
            exchange.getResponseHeaders().set("Location",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        try {
            DataSourceClientHttpRequestFactory requestFactory =
                    new DataSourceClientHttpRequestFactory();
            URI startUri = URI.create("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/start");

            ClientHttpRequest request = requestFactory.createRequest(startUri, HttpMethod.GET);
            ClientHttpResponse response = request.execute();

            assertThat(response.getStatusCode().value()).isEqualTo(302);
            assertThat(redirectedEndpointHit).isFalse();
        } finally {
            server.stop(0);
        }
    }
}
