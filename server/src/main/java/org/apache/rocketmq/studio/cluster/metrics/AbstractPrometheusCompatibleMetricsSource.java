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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * Shared implementation for every Prometheus-compatible metrics backend
 * (Prometheus, VictoriaMetrics, Thanos, Cortex, Mimir, ARMS, ...).
 * <p>
 * The only behavioural difference between backends is the URL path under which
 * the Prometheus HTTP query API is exposed; query/parse/authentication logic is
 * identical. Concrete subclasses only pick a {@link MetricsBackendType}.
 * </p>
 */
@Slf4j
public abstract class AbstractPrometheusCompatibleMetricsSource implements MetricsSource {

    private static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_SERIES = 1_000;
    private static final int MAX_TOTAL_SAMPLES = 100_000;
    private static final int MAX_LABELS_PER_SERIES = 64;
    private static final int MAX_LABEL_KEY_LENGTH = 256;
    private static final int MAX_LABEL_VALUE_LENGTH = 4_096;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MetricsSourceSettings settings;

    protected AbstractPrometheusCompatibleMetricsSource(RestClient.Builder restClientBuilder,
                                                         ObjectMapper objectMapper,
                                                         MetricsSourceSettings settings) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(settings.getConnectTimeout());
        requestFactory.setReadTimeout(settings.getReadTimeout());
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.settings = settings;
    }

    protected MetricsBackendType backendType() {
        return settings.getBackendType();
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

        log.debug("Querying {} range: start={}, end={}, step={}",
                settings.getBackendType(), query.getStart(), query.getEnd(), query.getStep());

        try {
            JsonNode response = restClient.post()
                    .uri(queryRangeUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .headers(this::applyAuthentication)
                    .body(form)
                    .exchange((request, clientResponse) -> {
                        byte[] rawBody = readResponseBody(clientResponse.getBody());
                        int upstreamStatus = responseStatus(clientResponse.getStatusCode());
                        if (clientResponse.getStatusCode().isError()) {
                            // Check the status before parsing so a non-JSON error body (e.g. a proxy
                            // HTML/plain-text page) is not misreported as a connection failure.
                            try {
                                throw responseBodyException(objectMapper.readTree(rawBody), upstreamStatus);
                            } catch (IOException nonJsonBody) {
                                throw new PrometheusException(upstreamStatus,
                                        backendLabel() + " query failed (HTTP " + upstreamStatus + ")");
                            }
                        }
                        try {
                            return objectMapper.readTree(rawBody);
                        } catch (IOException malformedBody) {
                            throw new PrometheusException(HttpStatus.BAD_GATEWAY.value(),
                                    backendLabel() + " returned a non-JSON response");
                        }
                    });
            return parseResponse(response);
        } catch (PrometheusException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            if (hasCause(exception, SocketTimeoutException.class)) {
                throw new PrometheusException(HttpStatus.GATEWAY_TIMEOUT.value(),
                        backendLabel() + " query timed out", exception);
            }
            throw new PrometheusException(HttpStatus.BAD_GATEWAY.value(),
                    "Failed to connect to " + backendLabel(), exception);
        } catch (RestClientException exception) {
            if (hasCause(exception, SocketTimeoutException.class)) {
                throw new PrometheusException(HttpStatus.GATEWAY_TIMEOUT.value(),
                        backendLabel() + " query timed out", exception);
            }
            throw new PrometheusException(HttpStatus.BAD_GATEWAY.value(),
                    backendLabel() + " query failed", exception);
        }
    }

    private void validateQuery(MetricQueryDTO query) {
        if (query == null) {
            throw new PrometheusException(HttpStatus.BAD_REQUEST.value(), "Metric query is required");
        }
        if (!StringUtils.hasText(query.getMetric())) {
            throw new PrometheusException(HttpStatus.BAD_REQUEST.value(), "Metric query is required");
        }
        if (query.getStart() <= 0) {
            throw new PrometheusException(HttpStatus.BAD_REQUEST.value(), "Metric query start must be positive");
        }
        if (query.getEnd() <= 0) {
            throw new PrometheusException(HttpStatus.BAD_REQUEST.value(), "Metric query end must be positive");
        }
        if (query.getEnd() < query.getStart()) {
            throw new PrometheusException(HttpStatus.BAD_REQUEST.value(),
                    "Metric query end must not be earlier than start");
        }
        if (!StringUtils.hasText(query.getStep())) {
            throw new PrometheusException(HttpStatus.BAD_REQUEST.value(), "Metric query step is required");
        }
    }

    private URI queryRangeUri() {
        if (!StringUtils.hasText(settings.getBaseUrl())) {
            throw new PrometheusException(HttpStatus.SERVICE_UNAVAILABLE.value(),
                    backendLabel() + " base URL is not configured");
        }
        try {
            String baseUrl = settings.getBaseUrl().strip();
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            URI uri = URI.create(baseUrl + settings.getQueryPath());
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("Unsupported " + backendLabel() + " URL scheme");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new PrometheusException(HttpStatus.SERVICE_UNAVAILABLE.value(),
                    backendLabel() + " base URL is invalid", exception);
        }
    }

    private void applyAuthentication(HttpHeaders headers) {
        if (StringUtils.hasText(settings.getBearerToken())) {
            headers.setBearerAuth(settings.getBearerToken());
            return;
        }
        boolean hasUsername = StringUtils.hasText(settings.getUsername());
        boolean hasPassword = StringUtils.hasText(settings.getPassword());
        if (hasUsername != hasPassword) {
            throw new PrometheusException(HttpStatus.SERVICE_UNAVAILABLE.value(),
                    backendLabel() + " basic authentication is incomplete");
        }
        if (hasUsername) {
            headers.setBasicAuth(settings.getUsername(), settings.getPassword());
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
                    backendLabel() + " returned a malformed response");
        }
        validateResponseLimits(result);

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
        if (seriesNode == null) {
            throw new PrometheusException(HttpStatus.BAD_GATEWAY.value(),
                    backendLabel() + " returned a malformed time series");
        }
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
                    backendLabel() + " returned a malformed time series");
        }
        if (metric.size() > MAX_LABELS_PER_SERIES) {
            throw new PrometheusException(HttpStatus.PAYLOAD_TOO_LARGE.value(),
                    backendLabel() + " query returned too many labels in a time series; narrow the query");
        }

        Map<String, String> labels = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = metric.fields();
        fields.forEachRemaining(entry -> {
            String key = entry.getKey();
            String value = entry.getValue().asText();
            if (key.length() > MAX_LABEL_KEY_LENGTH || value.length() > MAX_LABEL_VALUE_LENGTH) {
                throw new PrometheusException(HttpStatus.PAYLOAD_TOO_LARGE.value(),
                        backendLabel() + " query returned an oversized time-series label; narrow the query");
            }
            labels.put(key, value);
        });

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
        if (sampleNode == null || !sampleNode.isArray() || sampleNode.size() != 2
                || !sampleNode.get(0).isNumber()) {
            throw new PrometheusException(HttpStatus.BAD_GATEWAY.value(),
                    backendLabel() + " returned a malformed sample");
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
                    backendLabel() + " returned a malformed histogram sample");
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

    private byte[] readResponseBody(InputStream input) throws IOException {
        try (InputStream response = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = response.read(buffer)) != -1) {
                if (output.size() > MAX_RESPONSE_BYTES - read) {
                    throw new PrometheusException(HttpStatus.PAYLOAD_TOO_LARGE.value(),
                            backendLabel() + " response exceeds 5 MiB; narrow the query");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private void validateResponseLimits(JsonNode result) {
        if (result.size() > MAX_SERIES) {
            throw new PrometheusException(HttpStatus.PAYLOAD_TOO_LARGE.value(),
                    backendLabel() + " query returned too many series; narrow the query");
        }
        long totalSamples = 0;
        for (JsonNode series : result) {
            if (series == null) {
                continue;
            }
            totalSamples += series.path("values").size();
            totalSamples += series.path("histograms").size();
            if (totalSamples > MAX_TOTAL_SAMPLES) {
                throw new PrometheusException(HttpStatus.PAYLOAD_TOO_LARGE.value(),
                        backendLabel() + " query returned too many samples; increase step or narrow the query");
            }
        }
    }

    private int responseStatus(HttpStatusCode statusCode) {
        int upstreamStatus = statusCode.value();
        return switch (upstreamStatus) {
            case 400, 422, 503, 504 -> upstreamStatus;
            default -> HttpStatus.BAD_GATEWAY.value();
        };
    }

    private PrometheusException responseBodyException(JsonNode response, int statusCode) {
        String errorType = response == null ? "" : response.path("errorType").asText();
        String error = response == null ? "" : response.path("error").asText();
        if (StringUtils.hasText(error)) {
            String message = StringUtils.hasText(errorType)
                    ? backendLabel() + " query failed (" + errorType + "): " + error
                    : backendLabel() + " query failed: " + error;
            return new PrometheusException(statusCode, message);
        }
        return new PrometheusException(statusCode, backendLabel() + " query failed");
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

    private String backendLabel() {
        return settings.getBackendType() == MetricsBackendType.PROMETHEUS
                ? "Prometheus" : settings.getBackendType().name();
    }
}
