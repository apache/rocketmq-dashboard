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

import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.model.MetricsDataSourceConfig;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for managing Prometheus-compatible metrics data sources and
 * querying them by name. Complements {@link MetricsController}, which runs
 * queries against the default Prometheus server.
 */
@RestController
@RequestMapping("/api/metrics/datasources")
@RequiredArgsConstructor
public class MetricsDataSourceController {

    private final MetricsDataSourceService dataSourceService;

    @GetMapping
    public Result<List<MetricsDataSourceConfig>> listDataSources() {
        return Result.ok(dataSourceService.listDataSources());
    }

    @PostMapping("/create")
    public Result<MetricsDataSourceConfig> createDataSource(@RequestBody MetricsDataSourceConfig config) {
        return Result.ok(dataSourceService.createDataSource(config));
    }

    @PostMapping("/update")
    public Result<MetricsDataSourceConfig> updateDataSource(@RequestBody MetricsDataSourceConfig config) {
        return Result.ok(dataSourceService.updateDataSource(config));
    }

    @DeleteMapping
    public Result<Void> deleteDataSource(@RequestParam(required = false) String name) {
        dataSourceService.deleteDataSource(name);
        return Result.ok();
    }

    @PostMapping("/query")
    public Result<MetricDataVO> query(@RequestParam String dataSource,
                                      @Valid @RequestBody MetricQueryDTO query) {
        return Result.ok(dataSourceService.query(dataSource, query));
    }
}
