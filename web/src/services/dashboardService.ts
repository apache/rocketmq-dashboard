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

import { isMockMode } from './dataMode';
import * as metricsApi from '../api/metrics';
import { dashboardStats, clusterOverview } from '../mock/dashboard';
import type { DashboardData } from '../api/metrics';

function copyClusterOverview(cluster: DashboardData['clusters'][number]) {
  return {
    ...cluster,
    throughput: [...cluster.throughput],
  };
}

export async function getDashboard(instanceId?: string): Promise<DashboardData> {
  if (isMockMode()) {
    return {
      stats: { ...dashboardStats },
      clusters: (clusterOverview as DashboardData['clusters']).map(copyClusterOverview),
    };
  }
  return metricsApi.getDashboard(instanceId);
}
