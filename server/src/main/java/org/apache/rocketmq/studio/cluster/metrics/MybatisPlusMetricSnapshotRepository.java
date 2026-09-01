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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.ops.alert.AlertDomain;
import org.apache.rocketmq.studio.persistence.entity.RmqMetricSnapshot;
import org.apache.rocketmq.studio.persistence.mapper.RmqMetricSnapshotMapper;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MybatisPlusMetricSnapshotRepository implements MetricSnapshotRepository {
    private final RmqMetricSnapshotMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public void saveAll(List<MetricSample> samples) {
        for (MetricSample sample : samples) {
            mapper.insert(toEntity(sample));
        }
    }

    @Override
    public int deleteBefore(Instant cutoff) {
        return Math.toIntExact(mapper.delete(new QueryWrapper<RmqMetricSnapshot>()
                .lt("collected_at", LocalDateTime.ofInstant(cutoff, ZoneOffset.UTC))));
    }

    @Override
    public List<MetricSample> findRecent(MetricSample scope, Instant since) {
        String labelsJson = serializeLabels(scope.labels());
        return mapper.selectList(new QueryWrapper<RmqMetricSnapshot>()
                        .eq("instance_id", scope.instanceId()).eq("metric_key", scope.metricKey())
                        .eq("domain", scope.domain().name()).eq("labels_hash", sha256(labelsJson))
                        .isNull(scope.clusterId() == null, "cluster_id")
                        .eq(scope.clusterId() != null, "cluster_id", scope.clusterId())
                        .eq("availability", MetricAvailability.AVAILABLE.name())
                        .ge("collected_at", LocalDateTime.ofInstant(since, ZoneOffset.UTC))
                        .orderByAsc("collected_at"))
                .stream().map(this::toSample).filter(Objects::nonNull).toList();
    }

    private RmqMetricSnapshot toEntity(MetricSample sample) {
        String labelsJson = serializeLabels(sample.labels());
        RmqMetricSnapshot entity = new RmqMetricSnapshot();
        entity.setInstanceId(sample.instanceId());
        entity.setMetricKey(sample.metricKey());
        entity.setDomain(sample.domain().name());
        entity.setClusterId(sample.clusterId());
        entity.setLabelsHash(sha256(labelsJson));
        entity.setLabelsJson(labelsJson);
        entity.setValue(sample.value());
        entity.setAvailability(sample.availability().name());
        entity.setCollectedAt(LocalDateTime.ofInstant(sample.collectedAt(), ZoneOffset.UTC));
        return entity;
    }

    /**
     * Materializes a persisted snapshot row, returning null (and logging) when the row is
     * structurally invalid - e.g. a NULL value on an available row, or an enum value left behind
     * by an older Studio version. Skipping one bad row keeps it from breaking the recent-samples
     * window that alert aggregation reads until the retention cleanup removes it.
     */
    private MetricSample toSample(RmqMetricSnapshot entity) {
        try {
            return new MetricSample(entity.getMetricKey(), AlertDomain.valueOf(entity.getDomain()),
                    entity.getInstanceId(), entity.getClusterId(), objectMapper.readValue(entity.getLabelsJson(), new TypeReference<>() { }),
                    entity.getValue(), MetricAvailability.valueOf(entity.getAvailability()),
                    entity.getCollectedAt().toInstant(ZoneOffset.UTC));
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException error) {
            log.warn("Skipping unreadable metric snapshot row id={}: {}", entity.getId(), error.toString());
            return null;
        }
    }

    private String serializeLabels(Map<String, String> labels) {
        try {
            return objectMapper.writeValueAsString(new TreeMap<>(labels));
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Unable to serialize metric labels", error);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
