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
package org.apache.rocketmq.studio.cluster.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.apache.rocketmq.studio.model.request.MetricsDataSourceQueryRequest;
import org.apache.rocketmq.studio.settings.DataSourceVO;
import org.apache.rocketmq.studio.settings.SettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricsStepTransportTest {

    private HttpServer server;
    private MetricsService service;
    private final AtomicReference<String> receivedStep = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/query_range", exchange -> {
            requests.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            receivedStep.set(Arrays.stream(body.split("&"))
                    .filter(field -> field.startsWith("step="))
                    .map(field -> URLDecoder.decode(field.substring(5), StandardCharsets.UTF_8))
                    .findFirst().orElse(null));
            byte[] response = """
                    {"status":"success","data":{"resultType":"matrix","result":[]}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        PrometheusMetricsSource source = new PrometheusMetricsSource(RestClient.builder(), new ObjectMapper(),
                MetricsSourceSettings.builder().baseUrl(baseUrl).build()) {
            @Override
            protected void validateQueryHost(String url) {
                // Allow only this test fixture to use a loopback destination.
            }
        };
        MetricsSourceFactory factory = mock(MetricsSourceFactory.class);
        when(factory.create(any())).thenReturn(source);
        SettingsService settings = mock(SettingsService.class);
        when(settings.getDataSource("metrics")).thenReturn(DataSourceVO.builder()
                .key("metrics").name("Metrics").type("prometheus").url(baseUrl).auth("none").build());
        service = new MetricsService(source, mock(MetricProfileService.class), factory, settings);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @MethodSource("acceptedSteps")
    void acceptedStepReachesHttpTransportWithoutPaddingTest(boolean configuredSource, String input, String expected) {
        MetricQueryDTO query = query(input);

        MetricDataVO result = execute(configuredSource, query);

        assertThat(receivedStep.get()).isEqualTo(expected);
        assertThat(requests.get()).isEqualTo(1);
        assertThat(result.getResultType()).isEqualTo("matrix");
        assertThat(query.getStep()).isEqualTo(input);
    }

    @ParameterizedTest
    @MethodSource("rejectedSteps")
    void invalidOrExcessiveStepDoesNotReachHttpTransportTest(boolean configuredSource, String step) {
        assertThatThrownBy(() -> execute(configuredSource, query(step)))
                .isInstanceOf(PrometheusException.class)
                .satisfies(exception -> assertThat(((PrometheusException) exception).getStatusCode()).isEqualTo(400));

        assertThat(requests.get()).isZero();
        assertThat(receivedStep.get()).isNull();
    }

    private MetricDataVO execute(boolean configuredSource, MetricQueryDTO query) {
        if (!configuredSource) {
            return service.query(query);
        }
        MetricsDataSourceQueryRequest request = new MetricsDataSourceQueryRequest();
        request.setQuery(query);
        return service.queryByDataSource("metrics", request);
    }

    private MetricQueryDTO query(String step) {
        return MetricQueryDTO.builder().metric("up").start(1_000_000L).end(1_000_300L).step(step).build();
    }

    private static Stream<Arguments> acceptedSteps() {
        return Stream.of(false, true).flatMap(configuredSource -> Stream.of(
                Arguments.of(configuredSource, "30s", "30s"),
                Arguments.of(configuredSource, "30", "30"),
                Arguments.of(configuredSource, "0.5", "0.5"),
                Arguments.of(configuredSource, "1m30s", "1m30s"),
                Arguments.of(configuredSource, " 30s ", "30s"),
                Arguments.of(configuredSource, " 30 ", "30"),
                Arguments.of(configuredSource, "\t0.5\n", "0.5"),
                Arguments.of(configuredSource, "\u20031m30s\u2003", "1m30s")));
    }

    private static Stream<Arguments> rejectedSteps() {
        return Stream.of(false, true).flatMap(configuredSource -> Stream.of(" \t ", " 30 s ", "0", "0.001")
                .map(step -> Arguments.of(configuredSource, step)));
    }
}
