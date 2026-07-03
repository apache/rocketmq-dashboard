import { USE_MOCK } from '../config';
import * as metricsApi from '../api/metrics';
import { dashboardStats, clusterOverview } from '../mock/dashboard';
import type { DashboardData } from '../api/metrics';

export async function getDashboard(): Promise<DashboardData> {
  if (USE_MOCK) {
    return {
      stats: dashboardStats,
      clusters: clusterOverview as unknown as DashboardData['clusters'],
    };
  }
  return metricsApi.getDashboard();
}
