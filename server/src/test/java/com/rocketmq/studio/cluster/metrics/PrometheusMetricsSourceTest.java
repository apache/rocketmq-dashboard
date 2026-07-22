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
package com.rocketmq.studio.cluster.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrometheusMetricsSourceTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void queryShouldPreserveSeriesLabelsDecimalValuesAndWarnings() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> requestMethod = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        server.createContext("/api/v1/query_range", exchange -> {
            requestMethod.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {
                      "status": "success",
                      "data": {
                        "resultType": "matrix",
                        "result": [
                          {
                            "metric": {"node_id": "broker-a", "cluster": "cluster-a"},
                            "values": [[1784107658, "0.30000000000000004"], [1784107688, "NaN"]]
                          },
                          {
                            "metric": {"node_id": "broker-b", "cluster": "cluster-a"},
                            "values": [[1784107658.5, "1.25"]]
                          }
                        ]
                      },
                      "warnings": ["partial response"]
                    }
                    """);
        });

        PrometheusMetricsSource source = source(Duration.ofSeconds(2));
        MetricDataVO result = source.query(query());

        assertThat(result.getResultType()).isEqualTo("matrix");
        assertThat(result.getSeries()).hasSize(2);
        assertThat(result.getSeries().get(0).getLabels())
                .containsEntry("node_id", "broker-a")
                .containsEntry("cluster", "cluster-a");
        assertThat(result.getSeries().get(0).getValues().get(0).getValue())
                .isEqualTo("0.30000000000000004");
        assertThat(result.getSeries().get(0).getValues().get(1).getValue()).isEqualTo("NaN");
        assertThat(result.getSeries().get(1).getValues().get(0).getTimestamp()).isEqualTo(1784107658.5D);
        assertThat(result.getWarnings()).containsExactly("partial response");

        assertThat(requestMethod.get()).isEqualTo("POST");
        assertThat(contentType.get()).startsWith("application/x-www-form-urlencoded");
        String decodedBody = URLDecoder.decode(requestBody.get(), StandardCharsets.UTF_8);
        assertThat(decodedBody).contains("query=sum(rate(rocketmq_messages_in_total[1m]))");
        assertThat(decodedBody).contains("start=1784107658");
        assertThat(decodedBody).contains("end=1784108558");
        assertThat(decodedBody).contains("step=30s");
    }

    @Test
    void queryShouldPreserveHistogramOnlySeries() {
        server.createContext("/api/v1/query_range", exchange -> respond(exchange, 200, """
                {
                  "status": "success",
                  "data": {
                    "resultType": "matrix",
                    "result": [{
                      "metric": {"__name__": "rocketmq_rpc_latency"},
                      "histograms": [[1784107658, {
                        "count": "12",
                        "sum": "3.5",
                        "buckets": [[3, "-0.5", "0.5", "4"], [0, "0.5", "+Inf", "8"]]
                      }]]
                    }]
                  }
                }
                """));

        MetricDataVO.MetricSeriesVO series = source(Duration.ofSeconds(2)).query(query()).getSeries().get(0);

        assertThat(series.getValues()).isEmpty();
        assertThat(series.getHistograms()).hasSize(1);
        assertThat(series.getHistograms().get(0).getTimestamp()).isEqualTo(1784107658D);
        assertThat(series.getHistograms().get(0).getHistogram().path("count").asText()).isEqualTo("12");
        assertThat(series.getHistograms().get(0).getHistogram().path("sum").asText()).isEqualTo("3.5");
        assertThat(series.getHistograms().get(0).getHistogram().path("buckets")).hasSize(2);
    }

    @Test
    void queryShouldPreserveFloatAndHistogramSamplesInSameSeries() {
        server.createContext("/api/v1/query_range", exchange -> respond(exchange, 200, """
                {
                  "status": "success",
                  "data": {
                    "resultType": "matrix",
                    "result": [{
                      "metric": {"__name__": "request_duration_seconds"},
                      "values": [[1784107658, "1.25"]],
                      "histograms": [[1784107688, {
                        "count": "2",
                        "sum": "1.5",
                        "buckets": [[3, "-0.5", "0.5", "2"]]
                      }]]
                    }]
                  }
                }
                """));

        MetricDataVO.MetricSeriesVO series = source(Duration.ofSeconds(2)).query(query()).getSeries().get(0);

        assertThat(series.getValues()).hasSize(1);
        assertThat(series.getValues().get(0).getValue()).isEqualTo("1.25");
        assertThat(series.getHistograms()).hasSize(1);
        assertThat(series.getHistograms().get(0).getHistogram().path("count").asText()).isEqualTo("2");
    }

    @Test
    void queryShouldApplyBasicAuthentication() {
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/api/v1/query_range", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, successResponse());
        });
        PrometheusProperties properties = properties(Duration.ofSeconds(2));
        properties.setUsername("studio");
        properties.setPassword("secret");

        source(properties).query(query());

        String credentials = Base64.getEncoder().encodeToString("studio:secret".getBytes(StandardCharsets.UTF_8));
        assertThat(authorization.get()).isEqualTo("Basic " + credentials);
    }

    @Test
    void queryShouldPreferBearerAuthentication() {
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/api/v1/query_range", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, successResponse());
        });
        PrometheusProperties properties = properties(Duration.ofSeconds(2));
        properties.setBearerToken("test-token");
        properties.setUsername("ignored-user");
        properties.setPassword("ignored-password");

        source(properties).query(query());

        assertThat(authorization.get()).isEqualTo("Bearer test-token");
    }

    @Test
    void queryShouldExposePrometheusErrorDetails() {
        server.createContext("/api/v1/query_range", exchange -> respond(exchange, 422, """
                {"status":"error","errorType":"execution","error":"invalid expression"}
                """));

        PrometheusMetricsSource source = source(Duration.ofSeconds(2));

        assertThatThrownBy(() -> source.query(query()))
                .isInstanceOf(PrometheusException.class)
                .satisfies(exception -> {
                    PrometheusException prometheusException = (PrometheusException) exception;
                    assertThat(prometheusException.getStatusCode()).isEqualTo(422);
                    assertThat(prometheusException.getMessage())
                            .isEqualTo("Prometheus query failed (execution): invalid expression");
                });
    }

    @Test
    void queryShouldRejectMalformedPrometheusResponse() {
        server.createContext("/api/v1/query_range", exchange -> respond(exchange, 200, """
                {"status":"success","data":{"resultType":"matrix","result":{}}}
                """));

        assertThatThrownBy(() -> source(Duration.ofSeconds(2)).query(query()))
                .isInstanceOf(PrometheusException.class)
                .satisfies(exception -> assertThat(((PrometheusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_GATEWAY.value()))
                .hasMessage("Prometheus returned a malformed response");
    }

    @Test
    void queryShouldRejectEndEarlierThanStart() {
        MetricQueryDTO invalidQuery = MetricQueryDTO.builder()
                .metric("up")
                .start(2L)
                .end(1L)
                .step("30s")
                .build();

        assertThatThrownBy(() -> source(Duration.ofSeconds(2)).query(invalidQuery))
                .isInstanceOf(PrometheusException.class)
                .satisfies(exception -> assertThat(((PrometheusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST.value()))
                .hasMessage("Metric query end must not be earlier than start");
    }

    @Test
    void queryShouldFailLoudWhenPrometheusIsNotConfigured() {
        PrometheusProperties properties = new PrometheusProperties();
        PrometheusMetricsSource source = new PrometheusMetricsSource(
                RestClient.builder(), new ObjectMapper(), properties);

        assertThatThrownBy(() -> source.query(query()))
                .isInstanceOf(PrometheusException.class)
                .satisfies(exception -> assertThat(((PrometheusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value()))
                .hasMessage("Prometheus base URL is not configured");
    }

    @Test
    void queryShouldReportReadTimeout() {
        server.createContext("/api/v1/query_range", exchange -> {
            try {
                Thread.sleep(300);
                respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":[]}}");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The client closes the exchange after the expected timeout.
            }
        });

        PrometheusMetricsSource source = source(Duration.ofMillis(50));

        assertThatThrownBy(() -> source.query(query()))
                .isInstanceOf(PrometheusException.class)
                .satisfies(exception -> {
                    PrometheusException prometheusException = (PrometheusException) exception;
                    assertThat(prometheusException.getStatusCode())
                            .isEqualTo(HttpStatus.GATEWAY_TIMEOUT.value());
                })
                .hasMessage("Prometheus query timed out");
    }

    private PrometheusMetricsSource source(Duration readTimeout) {
        return source(properties(readTimeout));
    }

    private PrometheusMetricsSource source(PrometheusProperties properties) {
        return new PrometheusMetricsSource(RestClient.builder(), new ObjectMapper(), properties);
    }

    private PrometheusProperties properties(Duration readTimeout) {
        PrometheusProperties properties = new PrometheusProperties();
        properties.setBaseUrl(baseUrl);
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(readTimeout);
        return properties;
    }

    private MetricQueryDTO query() {
        return MetricQueryDTO.builder()
                .metric("sum(rate(rocketmq_messages_in_total[1m]))")
                .start(1784107658L)
                .end(1784108558L)
                .step("30s")
                .build();
    }

    private void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private String successResponse() {
        return "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":[]}}";
    }
}
