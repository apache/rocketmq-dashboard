// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.

import { isMockMode } from './dataMode';
import * as metricsApi from '../api/metrics';
import type { MetricsDataSource, MetricData, MetricQuery } from '../api/metrics';
import { mockMetricsDataSources } from '../mock/metricsDataSources';

let mockStore: MetricsDataSource[] = mockMetricsDataSources.map((source) => ({ ...source }));

function copySource(source: MetricsDataSource): MetricsDataSource {
  return { ...source };
}

export async function listMetricDataSources(): Promise<MetricsDataSource[]> {
  if (isMockMode()) {
    return mockStore.map(copySource);
  }
  return metricsApi.listMetricDataSources();
}

export async function createMetricDataSource(
  config: MetricsDataSource,
): Promise<MetricsDataSource> {
  if (isMockMode()) {
    const created = copySource(config);
    const index = mockStore.findIndex((source) => source.name === config.name);
    if (index >= 0) {
      mockStore[index] = created;
    } else {
      mockStore.push(created);
    }
    return created;
  }
  return metricsApi.createMetricDataSource(config);
}

export async function updateMetricDataSource(
  config: MetricsDataSource,
): Promise<MetricsDataSource> {
  if (isMockMode()) {
    const index = mockStore.findIndex((source) => source.name === config.name);
    if (index < 0) {
      throw new Error(`Data source not found: ${config.name}`);
    }
    mockStore[index] = copySource(config);
    return mockStore[index];
  }
  return metricsApi.updateMetricDataSource(config);
}

export async function deleteMetricDataSource(name: string): Promise<void> {
  if (isMockMode()) {
    mockStore = mockStore.filter((source) => source.name !== name);
    return;
  }
  return metricsApi.deleteMetricDataSource(name);
}

export async function queryMetricDataSource(
  dataSource: string,
  query: MetricQuery,
): Promise<MetricData> {
  if (isMockMode()) {
    return {
      resultType: 'matrix',
      series: [],
      warnings: [],
    };
  }
  return metricsApi.queryMetricDataSource(dataSource, query);
}
