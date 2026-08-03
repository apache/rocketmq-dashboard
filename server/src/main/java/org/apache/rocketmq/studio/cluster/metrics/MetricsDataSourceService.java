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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.model.MetricsDataSourceConfig;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Manages Prometheus-compatible metrics data sources and runs queries against
 * the backend selected by a data source name.
 */
@Service
public class MetricsDataSourceService {

    private final MetricsDataSourceRepository repository;
    private final MetricsSourceFactory sourceFactory;

    public MetricsDataSourceService(MetricsDataSourceRepository repository, MetricsSourceFactory sourceFactory) {
        this.repository = repository;
        this.sourceFactory = sourceFactory;
    }

    public List<MetricsDataSourceConfig> listDataSources() {
        return repository.findAll();
    }

    public MetricsDataSourceConfig createDataSource(MetricsDataSourceConfig config) {
        if (config == null || !StringUtils.hasText(config.getName())) {
            throw new BusinessException(400, "Data source name is required");
        }
        return repository.save(config);
    }

    public MetricsDataSourceConfig updateDataSource(MetricsDataSourceConfig config) {
        if (config == null || !StringUtils.hasText(config.getName())) {
            throw new BusinessException(400, "Data source name is required");
        }
        if (repository.findByName(config.getName()).isEmpty()) {
            throw new BusinessException(404, "Data source not found: " + config.getName());
        }
        return repository.save(config);
    }

    public void deleteDataSource(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(400, "Data source name is required");
        }
        repository.deleteByName(name);
    }

    public MetricDataVO query(String dataSourceName, MetricQueryDTO query) {
        if (!StringUtils.hasText(dataSourceName)) {
            throw new BusinessException(400, "Data source name is required");
        }
        MetricsDataSourceConfig config = repository.findByName(dataSourceName)
                .orElseThrow(() -> new BusinessException(404, "Data source not found: " + dataSourceName));
        MetricsSource source = sourceFactory.create(config);
        return source.query(query);
    }
}
