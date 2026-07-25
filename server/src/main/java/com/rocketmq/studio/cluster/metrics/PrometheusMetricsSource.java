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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@Slf4j
@Component
public class PrometheusMetricsSource implements MetricsSource {

    private static final String QUERY_RANGE_PATH = "/api/v1/query_range";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final PrometheusProperties properties;

    public PrometheusMetricsSource(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
                                   PrometheusProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public MetricDataVO query(MetricQueryDTO query) {
        validateQuery(query);
        URI queryRangeUri = queryRangeUri();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("query", query.getMetric());
        form.add("start", Long.toString(query.getStart()));
        form.add("end", Long.toString(query.getEnd()));
        form.add("step", query.getStep());

        log.debug("Querying Prometheus range: start={}, end={}, step={}",
                query.getStart(), query.getEnd(), query.getStep());

        try {
            JsonNode response = restClient.post()
                    .uri(queryRangeUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .headers(this::applyAuthentication)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            return parseResponse(response);
        } catch (PrometheusException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw responseException(exception);
        } catch (ResourceAccessException exception) {
            if (hasCause(exception, SocketTimeoutException.class)) {
                throw new PrometheusException(HttpStatus.GATEWAY_TIMEOUT.value(),
                        "Prometheus query timed out", exception);
            }
            throw new PrometheusException(HttpStatus.BAD_GATEWAY.value(),
                    "Failed to connect to Prometheus", exception);
        } catch (RestClientException exception) {
            if (hasCause(exception, SocketTimeoutException.class)) {
                throw new PrometheusException(HttpStatus.GATEWAY_TIMEOUT.value(),
                        "Prometheus query timed out", exception);
            }
            throw new PrometheusException(HttpStatus.BAD_GATEWAY.value(),
                    "Prometheus query failed", exception);
        }
    }

    private void validateQuery(MetricQueryDTO query) {
        if (query == null) {
            throw new PrometheusException(HttpStatus.BAD_REQUEST.value(), "Metric query is required");
        }
        if (query.getEnd() < query.getStart()) {
            throw new PrometheusException(HttpStatus.BAD_REQUEST.value(),
                    "Metric query end must not be earlier than start");
        }
    }

    private URI queryRangeUri() {
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new PrometheusException(HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "Prometheus base URL is not configured");
        }
        try {
            String baseUrl = properties.getBaseUrl().strip();
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            URI uri = URI.create(baseUrl + QUERY_RANGE_PATH);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("Unsupported Prometheus URL scheme");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new PrometheusException(HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "Prometheus base URL is invalid", exception);
        }
    }

    private void applyAuthentication(HttpHeaders headers) {
        if (StringUtils.hasText(properties.getBearerToken())) {
            headers.setBearerAuth(properties.getBearerToken());
            return;
        }
        boolean hasUsername = StringUtils.hasText(properties.getUsername());
        boolean hasPassword = StringUtils.hasText(properties.getPassword());
        if (hasUsername != hasPassword) {
            throw new PrometheusException(HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "Prometheus basic authentication is incomplete");
        }
        if (hasUsername) {
            headers.setBasicAuth(properties.getUsername(), properties.getPassword());
        }
    }

    private MetricDataVO parseResponse(JsonNode response) {
        if (response == null || !"success".equals(response.path("status").asText())) {
            throw responseBodyException(response, HttpStatus.BAD_GATEWAY.value());
        }

        JsonNode data = response.path("data");
        JsonNode result = data.path("result");
        if (!data.isObject() || !result.isArray() || !StringUtils.hasText(data.path("resultType").asText())) {
            throw new PrometheusException(HttpStatus.BAD_GATEWAY.value(),
                    "Prometheus returned a malformed response");
        }

        List<MetricDataVO.MetricSeriesVO> series = StreamSupport.stream(result.spliterator(), false)
                .map(this::parseSeries)
                .toList();
        List<String> warnings = parseWarnings(response.path("warnings"));

        return MetricDataVO.builder()
                .resultType(data.path("resultType").asText())
                .series(series)
                .warnings(warnings)
                .build();
    }

    private MetricDataVO.MetricSeriesVO parseSeries(JsonNode seriesNode) {
        JsonNode metric = seriesNode.path("metric");
        JsonNode values = seriesNode.path("values");
        JsonNode histograms = seriesNode.path("histograms");
        boolean hasValues = values.isArray();
        boolean hasHistograms = histograms.isArray();
        boolean hasSamples = hasValues || hasHistograms;
        boolean invalidValues = !values.isMissingNode() && !hasValues;
        boolean invalidHistograms = !histograms.isMissingNode() && !hasHistograms;
        if (!metric.isObject() || invalidValues || invalidHistograms || !hasSamples) {
            throw new PrometheusException(HttpStatus.BAD_GATEWAY.value(),
                    "Prometheus returned a malformed time series");
        }

        Map<String, String> labels = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = metric.fields();
        fields.forEachRemaining(entry -> labels.put(entry.getKey(), entry.getValue().asText()));

        List<MetricDataVO.MetricSampleVO> samples = hasValues
                ? StreamSupport.stream(values.spliterator(), false).map(this::parseSample).toList()
                : List.of();
        List<MetricDataVO.MetricHistogramSampleVO> histogramSamples = hasHistograms
                ? StreamSupport.stream(histograms.spliterator(), false).map(this::parseHistogramSample).toList()
                : List.of();
        return MetricDataVO.MetricSeriesVO.builder()
                .labels(labels)
                .values(samples)
                .histograms(histogramSamples)
                .build();
    }

    private MetricDataVO.MetricSampleVO parseSample(JsonNode sampleNode) {
        if (!sampleNode.isArray() || sampleNode.size() != 2 || !sampleNode.get(0).isNumber()) {
            throw new PrometheusException(HttpStatus.BAD_GATEWAY.value(),
                    "Prometheus returned a malformed sample");
        }
        return MetricDataVO.MetricSampleVO.builder()
                .timestamp(sampleNode.get(0).asDouble())
                .value(sampleNode.get(1).asText())
                .build();
    }

    private MetricDataVO.MetricHistogramSampleVO parseHistogramSample(JsonNode sampleNode) {
        if (!sampleNode.isArray() || sampleNode.size() != 2
                || !sampleNode.get(0).isNumber() || !sampleNode.get(1).isObject()) {
            throw new PrometheusException(HttpStatus.BAD_GATEWAY.value(),
                    "Prometheus returned a malformed histogram sample");
        }
        return MetricDataVO.MetricHistogramSampleVO.builder()
                .timestamp(sampleNode.get(0).asDouble())
                .histogram(sampleNode.get(1))
                .build();
    }

    private List<String> parseWarnings(JsonNode warningsNode) {
        if (!warningsNode.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(warningsNode.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    private PrometheusException responseException(RestClientResponseException exception) {
        JsonNode response = null;
        try {
            response = objectMapper.readTree(exception.getResponseBodyAsString());
        } catch (IOException ignored) {
            log.debug("Failed to parse Prometheus error response");
        }
        int upstreamStatus = exception.getStatusCode().value();
        int statusCode = switch (upstreamStatus) {
            case 400, 422, 503 -> upstreamStatus;
            default -> HttpStatus.BAD_GATEWAY.value();
        };
        return responseBodyException(response, statusCode);
    }

    private PrometheusException responseBodyException(JsonNode response, int statusCode) {
        String errorType = response == null ? "" : response.path("errorType").asText();
        String error = response == null ? "" : response.path("error").asText();
        if (StringUtils.hasText(error)) {
            String message = StringUtils.hasText(errorType)
                    ? "Prometheus query failed (" + errorType + "): " + error
                    : "Prometheus query failed: " + error;
            return new PrometheusException(statusCode, message);
        }
        return new PrometheusException(statusCode, "Prometheus query failed");
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
