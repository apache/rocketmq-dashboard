// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.

import type { MetricsDataSource } from '../api/metrics';

export const mockMetricsDataSources: MetricsDataSource[] = [
  {
    name: 'prometheus-prod',
    providerType: 'PROMETHEUS',
    url: 'http://prometheus:9090',
    authType: 'none',
    tlsEnabled: false,
    scrapeInterval: 15,
    enabled: true,
  },
  {
    name: 'victoriametrics-prod',
    providerType: 'VICTORIAMETRICS',
    url: 'http://victoria-metrics:8428',
    authType: 'none',
    tlsEnabled: false,
    scrapeInterval: 15,
    enabled: true,
  },
  {
    name: 'thanos-prod',
    providerType: 'THANOS',
    url: 'http://thanos-query:9090',
    authType: 'none',
    tlsEnabled: false,
    scrapeInterval: 15,
    enabled: true,
  },
  {
    name: 'cortex-prod',
    providerType: 'CORTEX',
    url: 'http://cortex-query:9009',
    authType: 'none',
    tlsEnabled: false,
    scrapeInterval: 15,
    enabled: true,
  },
  {
    name: 'mimir-prod',
    providerType: 'MIMIR',
    url: 'http://mimir-query:8080',
    authType: 'none',
    tlsEnabled: true,
    scrapeInterval: 15,
    enabled: true,
  },
  {
    name: 'arms-prod',
    providerType: 'ARMS',
    url: 'http://arms-prometheus:9090',
    authType: 'bearer',
    tlsEnabled: true,
    scrapeInterval: 15,
    enabled: true,
  },
];
