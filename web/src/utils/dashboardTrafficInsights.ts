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

import type { ClusterOverview, DashboardData } from '../api/metrics';

export type TrafficHealthLevel = 'healthy' | 'notice' | 'warning' | 'critical';

export type TrafficTrendDirection = 'rising' | 'falling' | 'stable' | 'unknown';

export type DashboardTrafficIssueCode =
  | 'NO_ACTIVE_TRAFFIC'
  | 'TRAFFIC_CONCENTRATION'
  | 'UNHEALTHY_TRAFFIC'
  | 'BROKER_LOAD_SKEW'
  | 'RECENT_TRAFFIC_DROP'
  | 'RECENT_TRAFFIC_SPIKE'
  | 'TOPOLOGY_COUNT_UNAVAILABLE'
  | 'IDLE_CLUSTER';

export interface DashboardTrafficIssue {
  code: DashboardTrafficIssueCode;
  level: TrafficHealthLevel;
  clusterId?: string;
  clusterName?: string;
  value?: number;
  threshold?: number;
}

export interface DashboardTrafficClusterInsight {
  id: string;
  name: string;
  status: string;
  tpsIn: number;
  tpsOut: number;
  totalTps: number;
  sharePercent: number;
  brokers: number;
  topics: number;
  groups: number;
  proxies: number | null;
  perBrokerTps: number;
  inOutRatio: number | null;
  trendDirection: TrafficTrendDirection;
  trendDeltaPercent: number | null;
  throughput: number[];
  issues: DashboardTrafficIssue[];
}

export interface DashboardTrafficInsights {
  level: TrafficHealthLevel;
  totalTps: number;
  activeClusterCount: number;
  totalClusterCount: number;
  unhealthyClusterCount: number;
  unhealthyTrafficTps: number;
  unhealthyTrafficPercent: number;
  topCluster: DashboardTrafficClusterInsight | null;
  topClusterSharePercent: number;
  balanceScore: number;
  averagePerBrokerTps: number;
  trendDirection: TrafficTrendDirection;
  trendDeltaPercent: number | null;
  aggregateThroughput: number[];
  rows: DashboardTrafficClusterInsight[];
  issues: DashboardTrafficIssue[];
}

const CONCENTRATION_WARNING_PERCENT = 60;
const CONCENTRATION_CRITICAL_PERCENT = 75;
const UNHEALTHY_TRAFFIC_WARNING_PERCENT = 10;
const UNHEALTHY_TRAFFIC_CRITICAL_PERCENT = 25;
const BROKER_SKEW_MULTIPLIER = 2.5;
const MIN_TRAFFIC_FOR_SKEW = 100;
const TRAFFIC_DROP_WARNING_PERCENT = -30;
const TRAFFIC_SPIKE_NOTICE_PERCENT = 50;
const TREND_THRESHOLD_PERCENT = 10;

const issueLevelWeight: Record<TrafficHealthLevel, number> = {
  healthy: 0,
  notice: 1,
  warning: 2,
  critical: 3,
};

const isHealthyStatus = (status?: string | null) => {
  const normalized = (status ?? '').trim().toLowerCase();
  return normalized === 'healthy' || normalized === 'running';
};

const finiteNumber = (value: number | null | undefined): number =>
  typeof value === 'number' && Number.isFinite(value) ? value : 0;

const nonNegativeNumber = (value: number | null | undefined): number =>
  Math.max(0, finiteNumber(value));

const sanitizeSeries = (values: number[] | null | undefined): number[] =>
  (values ?? []).map(nonNegativeNumber);

const round = (value: number, precision = 1): number => {
  const factor = 10 ** precision;
  return Math.round(value * factor) / factor;
};

const percent = (value: number, total: number): number => {
  if (total <= 0) return 0;
  return round((value / total) * 100);
};

const average = (values: number[]): number => {
  if (values.length === 0) return 0;
  return values.reduce((sum, value) => sum + value, 0) / values.length;
};

const maxIssueLevel = (issues: DashboardTrafficIssue[]): TrafficHealthLevel =>
  issues.reduce<TrafficHealthLevel>(
    (current, issue) =>
      issueLevelWeight[issue.level] > issueLevelWeight[current] ? issue.level : current,
    'healthy',
  );

const compareTrend = (
  series: number[],
): {
  direction: TrafficTrendDirection;
  deltaPercent: number | null;
} => {
  if (series.length < 4) {
    return { direction: 'unknown', deltaPercent: null };
  }

  const midpoint = Math.floor(series.length / 2);
  const earlyAverage = average(series.slice(0, midpoint));
  const lateAverage = average(series.slice(midpoint));
  if (earlyAverage <= 0 && lateAverage <= 0) {
    return { direction: 'stable', deltaPercent: 0 };
  }
  if (earlyAverage <= 0) {
    return { direction: 'rising', deltaPercent: 100 };
  }

  const deltaPercent = round(((lateAverage - earlyAverage) / earlyAverage) * 100);
  if (deltaPercent >= TREND_THRESHOLD_PERCENT) {
    return { direction: 'rising', deltaPercent };
  }
  if (deltaPercent <= -TREND_THRESHOLD_PERCENT) {
    return { direction: 'falling', deltaPercent };
  }
  return { direction: 'stable', deltaPercent };
};

const aggregateThroughput = (clusters: ClusterOverview[]): number[] => {
  const maxLength = Math.max(0, ...clusters.map((cluster) => cluster.throughput?.length ?? 0));
  return Array.from({ length: maxLength }, (_, index) =>
    clusters.reduce((sum, cluster) => sum + nonNegativeNumber(cluster.throughput?.[index]), 0),
  );
};

const calculateBalanceScore = (rows: DashboardTrafficClusterInsight[]): number => {
  const activeRows = rows.filter((row) => row.totalTps > 0);
  if (activeRows.length <= 1) return 100;

  const hhi = activeRows.reduce((sum, row) => {
    const share = row.sharePercent / 100;
    return sum + share * share;
  }, 0);
  const idealHhi = 1 / activeRows.length;
  const normalized = (hhi - idealHhi) / (1 - idealHhi);
  return Math.max(0, Math.min(100, round((1 - normalized) * 100, 0)));
};

const buildClusterIssues = (
  cluster: ClusterOverview,
  totalTps: number,
  clusterTps: number,
): DashboardTrafficIssue[] => {
  const issues: DashboardTrafficIssue[] = [];
  if (clusterTps === 0) {
    issues.push({
      code: 'IDLE_CLUSTER',
      level: 'notice',
      clusterId: cluster.id,
      clusterName: cluster.name,
    });
  }
  if (!isHealthyStatus(cluster.status) && clusterTps > 0) {
    issues.push({
      code: 'UNHEALTHY_TRAFFIC',
      level:
        percent(clusterTps, totalTps) >= UNHEALTHY_TRAFFIC_CRITICAL_PERCENT
          ? 'critical'
          : 'warning',
      clusterId: cluster.id,
      clusterName: cluster.name,
      value: percent(clusterTps, totalTps),
      threshold: UNHEALTHY_TRAFFIC_WARNING_PERCENT,
    });
  }
  if (cluster.proxies == null) {
    issues.push({
      code: 'TOPOLOGY_COUNT_UNAVAILABLE',
      level: 'notice',
      clusterId: cluster.id,
      clusterName: cluster.name,
    });
  }
  return issues;
};

const buildRows = (
  clusters: ClusterOverview[],
  totalTps: number,
): DashboardTrafficClusterInsight[] =>
  clusters
    .map((cluster) => {
      const tpsIn = nonNegativeNumber(cluster.tpsIn);
      const tpsOut = nonNegativeNumber(cluster.tpsOut);
      const clusterTps = tpsIn + tpsOut;
      const brokers = Math.max(0, Math.floor(nonNegativeNumber(cluster.brokers)));
      const throughput = sanitizeSeries(cluster.throughput);
      const trend = compareTrend(throughput);
      return {
        id: cluster.id,
        name: cluster.name,
        status: cluster.status,
        tpsIn,
        tpsOut,
        totalTps: clusterTps,
        sharePercent: percent(clusterTps, totalTps),
        brokers,
        topics: Math.max(0, Math.floor(nonNegativeNumber(cluster.topics))),
        groups: Math.max(0, Math.floor(nonNegativeNumber(cluster.groups))),
        proxies:
          cluster.proxies == null
            ? null
            : Math.max(0, Math.floor(nonNegativeNumber(cluster.proxies))),
        perBrokerTps: brokers > 0 ? round(clusterTps / brokers) : 0,
        inOutRatio: tpsIn > 0 ? round(tpsOut / tpsIn, 2) : null,
        trendDirection: trend.direction,
        trendDeltaPercent: trend.deltaPercent,
        throughput,
        issues: buildClusterIssues(cluster, totalTps, clusterTps),
      };
    })
    .sort((left, right) => right.totalTps - left.totalTps || left.name.localeCompare(right.name));

const findBrokerSkewIssue = (
  rows: DashboardTrafficClusterInsight[],
  averagePerBrokerTps: number,
): DashboardTrafficIssue | null => {
  if (averagePerBrokerTps <= 0) return null;
  const skewed = rows.find(
    (row) =>
      row.totalTps >= MIN_TRAFFIC_FOR_SKEW &&
      row.perBrokerTps >= averagePerBrokerTps * BROKER_SKEW_MULTIPLIER,
  );
  if (!skewed) return null;
  return {
    code: 'BROKER_LOAD_SKEW',
    level: 'warning',
    clusterId: skewed.id,
    clusterName: skewed.name,
    value: skewed.perBrokerTps,
    threshold: round(averagePerBrokerTps * BROKER_SKEW_MULTIPLIER),
  };
};

const buildGlobalIssues = (
  rows: DashboardTrafficClusterInsight[],
  topCluster: DashboardTrafficClusterInsight | null,
  unhealthyTrafficPercent: number,
  trend: ReturnType<typeof compareTrend>,
  averagePerBrokerTps: number,
): DashboardTrafficIssue[] => {
  const issues: DashboardTrafficIssue[] = [];
  const activeRows = rows.filter((row) => row.totalTps > 0);

  if (activeRows.length === 0) {
    issues.push({ code: 'NO_ACTIVE_TRAFFIC', level: 'notice' });
  }

  if (
    topCluster &&
    activeRows.length > 1 &&
    topCluster.sharePercent >= CONCENTRATION_WARNING_PERCENT
  ) {
    issues.push({
      code: 'TRAFFIC_CONCENTRATION',
      level: topCluster.sharePercent >= CONCENTRATION_CRITICAL_PERCENT ? 'critical' : 'warning',
      clusterId: topCluster.id,
      clusterName: topCluster.name,
      value: topCluster.sharePercent,
      threshold: CONCENTRATION_WARNING_PERCENT,
    });
  }

  if (unhealthyTrafficPercent >= UNHEALTHY_TRAFFIC_WARNING_PERCENT) {
    issues.push({
      code: 'UNHEALTHY_TRAFFIC',
      level: unhealthyTrafficPercent >= UNHEALTHY_TRAFFIC_CRITICAL_PERCENT ? 'critical' : 'warning',
      value: unhealthyTrafficPercent,
      threshold: UNHEALTHY_TRAFFIC_WARNING_PERCENT,
    });
  }

  const skewIssue = findBrokerSkewIssue(rows, averagePerBrokerTps);
  if (skewIssue) issues.push(skewIssue);

  if (
    trend.direction === 'falling' &&
    trend.deltaPercent != null &&
    trend.deltaPercent <= TRAFFIC_DROP_WARNING_PERCENT
  ) {
    issues.push({
      code: 'RECENT_TRAFFIC_DROP',
      level: 'warning',
      value: trend.deltaPercent,
      threshold: TRAFFIC_DROP_WARNING_PERCENT,
    });
  }

  if (
    trend.direction === 'rising' &&
    trend.deltaPercent != null &&
    trend.deltaPercent >= TRAFFIC_SPIKE_NOTICE_PERCENT
  ) {
    issues.push({
      code: 'RECENT_TRAFFIC_SPIKE',
      level: 'notice',
      value: trend.deltaPercent,
      threshold: TRAFFIC_SPIKE_NOTICE_PERCENT,
    });
  }

  return issues;
};

export function buildDashboardTrafficInsights(
  dashboard: DashboardData | null | undefined,
): DashboardTrafficInsights {
  const clusters = dashboard?.clusters ?? [];
  const totalTps = clusters.reduce(
    (sum, cluster) => sum + nonNegativeNumber(cluster.tpsIn) + nonNegativeNumber(cluster.tpsOut),
    0,
  );
  const rows = buildRows(clusters, totalTps);
  const activeClusterCount = rows.filter((row) => row.totalTps > 0).length;
  const unhealthyClusterCount = rows.filter((row) => !isHealthyStatus(row.status)).length;
  const unhealthyTrafficTps = rows
    .filter((row) => !isHealthyStatus(row.status))
    .reduce((sum, row) => sum + row.totalTps, 0);
  const totalBrokers = rows.reduce((sum, row) => sum + row.brokers, 0);
  const averagePerBrokerTps = totalBrokers > 0 ? round(totalTps / totalBrokers) : 0;
  const topCluster = rows[0] ?? null;
  const aggregateSeries = aggregateThroughput(clusters);
  const trend = compareTrend(aggregateSeries);
  const unhealthyTrafficPercent = percent(unhealthyTrafficTps, totalTps);
  const issues = [
    ...buildGlobalIssues(rows, topCluster, unhealthyTrafficPercent, trend, averagePerBrokerTps),
    ...rows.flatMap((row) => row.issues),
  ];

  return {
    level: maxIssueLevel(issues),
    totalTps,
    activeClusterCount,
    totalClusterCount: rows.length,
    unhealthyClusterCount,
    unhealthyTrafficTps,
    unhealthyTrafficPercent,
    topCluster,
    topClusterSharePercent: topCluster?.sharePercent ?? 0,
    balanceScore: calculateBalanceScore(rows),
    averagePerBrokerTps,
    trendDirection: trend.direction,
    trendDeltaPercent: trend.deltaPercent,
    aggregateThroughput: aggregateSeries,
    rows,
    issues,
  };
}
