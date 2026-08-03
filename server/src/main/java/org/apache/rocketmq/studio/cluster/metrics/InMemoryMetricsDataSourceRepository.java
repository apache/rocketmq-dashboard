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

import org.apache.rocketmq.studio.model.MetricsDataSourceConfig;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link MetricsDataSourceRepository}.
 * <p>
 * Seeded with one example data source per supported backend type so that the
 * multi-backend capability is demonstrable out of the box. In a production
 * deployment this would be replaced by a persistent store (database or the
 * cloud control plane).
 * </p>
 */
@Repository
public class InMemoryMetricsDataSourceRepository implements MetricsDataSourceRepository {

    private final Map<String, MetricsDataSourceConfig> store = new ConcurrentHashMap<>();

    @PostConstruct
    void seed() {
        store.clear();
        store.put("prometheus-prod", dataSource("prometheus-prod", "PROMETHEUS",
                "http://prometheus:9090", "Production Prometheus"));
        store.put("victoriametrics-prod", dataSource("victoriametrics-prod", "VICTORIAMETRICS",
                "http://victoria-metrics:8428", "Production VictoriaMetrics"));
        store.put("thanos-prod", dataSource("thanos-prod", "THANOS",
                "http://thanos-query:9090", "Production Thanos"));
        store.put("cortex-prod", dataSource("cortex-prod", "CORTEX",
                "http://cortex-query:9009", "Production Cortex"));
        store.put("mimir-prod", dataSource("mimir-prod", "MIMIR",
                "http://mimir-query:8080", "Production Grafana Mimir"));
        store.put("arms-prod", dataSource("arms-prod", "ARMS",
                "http://arms-prometheus:9090", "Production ARMS Prometheus"));
    }

    private MetricsDataSourceConfig dataSource(String name, String providerType, String url, String description) {
        MetricsDataSourceConfig config = new MetricsDataSourceConfig();
        config.setName(name);
        config.setProviderType(providerType);
        config.setUrl(url);
        config.setAuthType("none");
        config.setEnabled(true);
        config.setScrapeInterval(15);
        config.setTlsEnabled(url.startsWith("https"));
        return config;
    }

    @Override
    public List<MetricsDataSourceConfig> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Optional<MetricsDataSourceConfig> findByName(String name) {
        return Optional.ofNullable(store.get(name));
    }

    @Override
    public MetricsDataSourceConfig save(MetricsDataSourceConfig config) {
        store.put(config.getName(), config);
        return config;
    }

    @Override
    public void deleteByName(String name) {
        store.remove(name);
    }
}
