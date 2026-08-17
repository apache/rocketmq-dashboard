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

export async function getDashboard(instanceId?: number): Promise<DashboardData> {
  if (isMockMode()) {
    return {
      stats: { ...dashboardStats },
      clusters: (clusterOverview as DashboardData['clusters']).map(copyClusterOverview),
    };
  }
  return metricsApi.getDashboard(instanceId);
}
