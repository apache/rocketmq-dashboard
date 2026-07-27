import { USE_MOCK } from '../config';
import * as metricsApi from '../api/metrics';
import { dashboardStats, clusterOverview } from '../mock/dashboard';
import type { DashboardData } from '../api/metrics';

function copyClusterOverview(cluster: DashboardData['clusters'][number]) {
  return {
    ...cluster,
    throughput: [...cluster.throughput],
  };
}

export async function getDashboard(): Promise<DashboardData> {
  if (USE_MOCK) {
    return {
      stats: { ...dashboardStats },
      clusters: (clusterOverview as DashboardData['clusters']).map(copyClusterOverview),
    };
  }
  return metricsApi.getDashboard();
}
