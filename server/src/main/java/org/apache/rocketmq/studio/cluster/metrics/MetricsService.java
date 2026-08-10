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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.model.MetricsDataSourceConfig;
import org.apache.rocketmq.studio.model.request.MetricsDataSourceQueryRequest;
import org.apache.rocketmq.studio.settings.DataSourceVO;
import org.apache.rocketmq.studio.settings.SettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {
    private static final long MAX_RANGE_SECONDS = 31L * 24 * 60 * 60;
    private static final long MAX_SAMPLE_POINTS = 11_000L;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final Pattern DURATION_PART_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)(ms|s|m|h|d|w|y)");
    private static final Map<String, BigDecimal> UNIT_TO_MILLIS = Map.of(
            "ms", BigDecimal.ONE,
            "s", BigDecimal.valueOf(1_000L),
            "m", BigDecimal.valueOf(60_000L),
            "h", BigDecimal.valueOf(3_600_000L),
            "d", BigDecimal.valueOf(86_400_000L),
            "w", BigDecimal.valueOf(604_800_000L),
            "y", BigDecimal.valueOf(31_536_000_000L)
    );

    private final MetricsSource metricsSource;
    private final MetricProfileService metricProfileService;
    private final MetricsSourceFactory metricsSourceFactory;
    private final SettingsService settingsService;

    public MetricDataVO query(MetricQueryDTO query) {
        if (query == null) {
            throw badRequest("Metric query is required");
        }
        MetricQueryDTO resolvedQuery = resolveMetricQuery(query);
        validateQueryWindow(resolvedQuery);
        log.debug("Querying metrics: start={}, end={}, step={}",
                resolvedQuery.getStart(), resolvedQuery.getEnd(), resolvedQuery.getStep());
        return metricsSource.query(resolvedQuery);
    }

    /**
     * Runs a PromQL range query against a configured data source, selected by its
     * persisted key. The backend type and URL come from the existing
     * {@link DataSourceVO} configuration (managed by {@link SettingsService});
     * credentials are supplied per request and are never persisted, mirroring the
     * existing data-source test flow.
     */
    public MetricDataVO queryByDataSource(String dataSourceKey, MetricsDataSourceQueryRequest request) {
        if (request == null || request.getQuery() == null) {
            throw badRequest("Metric query is required");
        }
        if (!StringUtils.hasText(dataSourceKey)) {
            throw badRequest("Data source key is required");
        }
        MetricQueryDTO resolvedQuery = resolveMetricQuery(request.getQuery());
        validateQueryWindow(resolvedQuery);
        DataSourceVO dataSource = settingsService.getDataSource(dataSourceKey);
        validateInstanceBinding(dataSourceKey, dataSource, request.getInstanceId());
        MetricsSource source = metricsSourceFactory.create(toConfig(dataSource, request));
        log.debug("Querying data source {} (type={}): start={}, end={}, step={}",
                dataSourceKey, dataSource.getType(),
                resolvedQuery.getStart(), resolvedQuery.getEnd(), resolvedQuery.getStep());
        return source.query(resolvedQuery);
    }

    private void validateInstanceBinding(String dataSourceKey, DataSourceVO dataSource, String instanceId) {
        List<String> bindings = dataSource.getInstanceIds();
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        String normalizedInstanceId = StringUtils.hasText(instanceId) ? instanceId.strip() : null;
        if (normalizedInstanceId == null || !bindings.contains(normalizedInstanceId)) {
            throw badRequest("Data source " + dataSourceKey + " is not available for instance "
                    + (normalizedInstanceId == null ? "<missing>" : normalizedInstanceId));
        }
    }

    private MetricsDataSourceConfig toConfig(DataSourceVO dataSource, MetricsDataSourceQueryRequest request) {
        MetricsDataSourceConfig config = new MetricsDataSourceConfig();
        config.setName(dataSource.getName());
        config.setProviderType(dataSource.getType());
        config.setUrl(dataSource.getUrl());
        config.setAuthType(normalizeAuth(dataSource.getAuth()));
        config.setUsername(request.getUsername());
        config.setPassword(request.getPassword());
        config.setBearerToken(request.getBearerToken());
        return config;
    }

    private String normalizeAuth(String auth) {
        if (!StringUtils.hasText(auth)) {
            return "none";
        }
        return switch (auth.trim().toLowerCase(Locale.ROOT)) {
            case "basic auth", "basic" -> "basic";
            case "bearer token", "bearer" -> "bearer";
            default -> "none";
        };
    }

    private void validateQueryWindow(MetricQueryDTO query) {
        long rangeSeconds = query.getEnd() - query.getStart();
        if (rangeSeconds <= 0) {
            throw badRequest("Metric query end must be later than start");
        }
        if (rangeSeconds > MAX_RANGE_SECONDS) {
            throw badRequest("Metric query range must not exceed 31 days");
        }
        BigDecimal stepMillis = parseStepMillis(query.getStep());
        if (stepMillis.signum() <= 0) {
            throw badRequest("Metric query step must be positive");
        }
        BigDecimal samplePoints = BigDecimal.valueOf(rangeSeconds)
                .multiply(BigDecimal.valueOf(1_000L))
                .divideToIntegralValue(stepMillis)
                .add(BigDecimal.ONE);
        if (samplePoints.compareTo(BigDecimal.valueOf(MAX_SAMPLE_POINTS)) > 0) {
            throw badRequest("Metric query returns too many samples; increase step or reduce range");
        }
    }

    private MetricQueryDTO resolveMetricQuery(MetricQueryDTO query) {
        boolean hasMetric = StringUtils.hasText(query.getMetric());
        boolean hasProfile = StringUtils.hasText(query.getProfileId());
        boolean hasSemanticMetric = StringUtils.hasText(query.getSemanticMetric());
        if (hasMetric) {
            if (hasProfile || hasSemanticMetric) {
                throw badRequest("Metric query cannot be combined with a semantic metric selection");
            }
            return query;
        }
        if (hasProfile != hasSemanticMetric) {
            throw badRequest("Metric profile and semantic metric are required together");
        }
        if (!hasProfile) {
            throw badRequest("Metric query is required");
        }

        String promql = metricProfileService.resolvePromql(
                query.getProfileId().strip(), query.getSemanticMetric().strip());
        return MetricQueryDTO.builder()
                .metric(promql)
                .start(query.getStart())
                .end(query.getEnd())
                .step(query.getStep())
                .build();
    }

    private BigDecimal parseStepMillis(String step) {
        if (!StringUtils.hasText(step)) {
            throw badRequest("Metric query step is required");
        }
        String value = step.strip();
        if (NUMBER_PATTERN.matcher(value).matches()) {
            return new BigDecimal(value).multiply(BigDecimal.valueOf(1_000L));
        }

        Matcher matcher = DURATION_PART_PATTERN.matcher(value);
        BigDecimal millis = BigDecimal.ZERO;
        int position = 0;
        while (matcher.find()) {
            if (matcher.start() != position) {
                throw badRequest("Metric query step is invalid");
            }
            BigDecimal amount = new BigDecimal(matcher.group(1));
            millis = millis.add(amount.multiply(UNIT_TO_MILLIS.get(matcher.group(2))));
            position = matcher.end();
        }
        if (position != value.length()) {
            throw badRequest("Metric query step is invalid");
        }
        return millis;
    }

    private PrometheusException badRequest(String message) {
        return new PrometheusException(HttpStatus.BAD_REQUEST.value(), message);
    }
}
