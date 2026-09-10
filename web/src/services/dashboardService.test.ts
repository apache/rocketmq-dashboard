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

import { afterEach, describe, expect, it, vi } from 'vitest';

const { isMockMode } = vi.hoisted(() => ({ isMockMode: vi.fn() }));

vi.mock('./dataMode', () => ({ isMockMode }));
vi.mock('../config', () => ({
  API_BASE_URL: '/api',
}));
vi.mock('../api/metrics', () => ({
  getDashboard: vi.fn(),
}));

import * as metricsApi from '../api/metrics';
import { getDashboard } from './dashboardService';

describe('dashboardService', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('returns defensive copies for overview stats and cluster throughput in mock mode', async () => {
    isMockMode.mockReturnValue(true);
    const dashboard = await getDashboard();
    const originalTotalClusters = dashboard.stats.totalClusters;
    const originalClusterName = dashboard.clusters[0].name;
    const originalThroughput = dashboard.clusters[0].throughput[0];

    dashboard.stats.totalClusters = 0;
    dashboard.clusters[0].name = 'mutated-cluster';
    dashboard.clusters[0].throughput[0] = 0;

    const fresh = await getDashboard();

    expect(fresh.stats.totalClusters).toBe(originalTotalClusters);
    expect(fresh.clusters[0].name).toBe(originalClusterName);
    expect(fresh.clusters[0].throughput[0]).toBe(originalThroughput);
    expect(fresh.stats).not.toBe(dashboard.stats);
    expect(fresh.clusters).not.toBe(dashboard.clusters);
    expect(fresh.clusters[0]).not.toBe(dashboard.clusters[0]);
    expect(fresh.clusters[0].throughput).not.toBe(dashboard.clusters[0].throughput);
    expect(metricsApi.getDashboard).not.toHaveBeenCalled();
  });

  it('delegates to the metrics API with the selected instance when mock mode is off', async () => {
    isMockMode.mockReturnValue(false);
    const apiDashboard = {
      stats: { totalClusters: 2 },
      clusters: [],
    };
    vi.mocked(metricsApi.getDashboard).mockResolvedValue(apiDashboard);

    await expect(getDashboard('instance-prod')).resolves.toBe(apiDashboard);
    expect(metricsApi.getDashboard).toHaveBeenCalledWith('instance-prod');
  });

  it('delegates without an instance when none is selected', async () => {
    isMockMode.mockReturnValue(false);
    const apiDashboard = {
      stats: { totalClusters: 1 },
      clusters: [],
    };
    vi.mocked(metricsApi.getDashboard).mockResolvedValue(apiDashboard);

    await expect(getDashboard()).resolves.toBe(apiDashboard);
    expect(metricsApi.getDashboard).toHaveBeenCalledWith(undefined);
  });
});
