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

import type { BrokerRoute } from '../api/metadata';

export type RouteDiagnosticStatus = 'healthy' | 'warning' | 'critical';

export type RouteIssueCode =
  | 'NO_ROUTE'
  | 'MISSING_BROKER_ADDRESS'
  | 'MISSING_MASTER_ADDRESS'
  | 'NO_WRITABLE_ROUTE'
  | 'NO_READABLE_ROUTE'
  | 'WRITE_QUEUE_UNAVAILABLE'
  | 'READ_QUEUE_UNAVAILABLE'
  | 'PERMISSION_NOT_WRITABLE'
  | 'PERMISSION_NOT_READABLE'
  | 'READ_WRITE_QUEUE_MISMATCH'
  | 'WRITE_QUEUE_SKEW'
  | 'READ_QUEUE_SKEW'
  | 'SINGLE_BROKER_ROUTE'
  | 'DUPLICATE_BROKER_ADDRESS';

export interface RouteDiagnosticIssue {
  id: string;
  code: RouteIssueCode;
  severity: Exclude<RouteDiagnosticStatus, 'healthy'>;
  brokerName?: string;
}

export interface RouteQueueSkew {
  gap: number;
  ratio: number;
}

export interface RouteDistribution {
  key: string;
  brokerName: string;
  brokerAddr: string;
  masterAddr: string;
  brokerIds: string[];
  replicaCount: number;
  writeQueues: number;
  readQueues: number;
  writeShare: number;
  readShare: number;
  perm: string;
  readable: boolean;
  writable: boolean;
  topicSysFlag?: number;
  status: RouteDiagnosticStatus;
  issues: RouteDiagnosticIssue[];
}

export interface RouteDiagnosticsSummary {
  brokerCount: number;
  addressCount: number;
  replicaCount: number;
  writableBrokerCount: number;
  readableBrokerCount: number;
  totalWriteQueues: number;
  totalReadQueues: number;
  writeSkew: RouteQueueSkew;
  readSkew: RouteQueueSkew;
}

export interface TopicRouteDiagnostics {
  status: RouteDiagnosticStatus;
  statusColor: 'success' | 'warning' | 'error';
  summary: RouteDiagnosticsSummary;
  distributions: RouteDistribution[];
  issues: RouteDiagnosticIssue[];
  recommendations: string[];
}

const STATUS_ORDER: Record<RouteDiagnosticStatus, number> = {
  healthy: 0,
  warning: 1,
  critical: 2,
};

const STATUS_COLOR: Record<RouteDiagnosticStatus, 'success' | 'warning' | 'error'> = {
  healthy: 'success',
  warning: 'warning',
  critical: 'error',
};

const EMPTY_SKEW: RouteQueueSkew = { gap: 0, ratio: 0 };

const issue = (
  code: RouteIssueCode,
  severity: Exclude<RouteDiagnosticStatus, 'healthy'>,
  brokerName?: string,
): RouteDiagnosticIssue => ({
  id: brokerName ? `${brokerName}:${code}` : code,
  code,
  severity,
  brokerName,
});

const queueCount = (value: number | undefined): number =>
  Number.isFinite(value) && value && value > 0 ? value : 0;

const queueShare = (value: number, total: number): number =>
  total <= 0 ? 0 : Math.round((value / total) * 1000) / 10;

const inferReadable = (route: BrokerRoute): boolean => {
  if (typeof route.readable === 'boolean') return route.readable;
  return route.perm === 'RW' || route.perm === 'RO';
};

const inferWritable = (route: BrokerRoute): boolean => {
  if (typeof route.writable === 'boolean') return route.writable;
  return route.perm === 'RW' || route.perm === 'WO';
};

const routeBrokerIds = (route: BrokerRoute): string[] => {
  if (route.brokerIds && route.brokerIds.length > 0) {
    return route.brokerIds.map(String).sort((left, right) => Number(left) - Number(right));
  }
  if (route.brokerAddrs) {
    return Object.keys(route.brokerAddrs).sort((left, right) => Number(left) - Number(right));
  }
  return [];
};

const routeAddresses = (route: BrokerRoute): string[] =>
  Object.values(route.brokerAddrs ?? {}).filter((addr) => addr.trim().length > 0);

const preferredBrokerAddr = (route: BrokerRoute): string => {
  if (route.brokerAddr) return route.brokerAddr;
  if (route.masterAddr) return route.masterAddr;
  return routeAddresses(route)[0] ?? '';
};

const routeReplicaCount = (route: BrokerRoute, brokerIds: string[]): number => {
  if (typeof route.replicaCount === 'number') return Math.max(0, route.replicaCount);
  return brokerIds.filter((id) => id !== '0').length;
};

const calculateSkew = (values: number[]): RouteQueueSkew => {
  const activeValues = values.filter((value) => value > 0);
  if (activeValues.length <= 1) return EMPTY_SKEW;

  const max = Math.max(...activeValues);
  const min = Math.min(...activeValues);
  const gap = max - min;
  if (gap === 0) return EMPTY_SKEW;

  const average = activeValues.reduce((sum, value) => sum + value, 0) / activeValues.length;
  return { gap, ratio: average === 0 ? 0 : Math.round((gap / average) * 100) / 100 };
};

const maxStatus = (issues: RouteDiagnosticIssue[]): RouteDiagnosticStatus =>
  issues.reduce<RouteDiagnosticStatus>(
    (status, current) =>
      STATUS_ORDER[current.severity] > STATUS_ORDER[status] ? current.severity : status,
    'healthy',
  );

const hasSkew = (skew: RouteQueueSkew): boolean => skew.gap > 0 && skew.ratio >= 0.5;

const collectAddressDuplicates = (routes: BrokerRoute[]): Set<string> => {
  const firstBrokerByAddr = new Map<string, string>();
  const duplicates = new Set<string>();

  routes.forEach((route) => {
    const brokerName = route.brokerName || 'unknown';
    routeAddresses(route).forEach((addr) => {
      const firstBroker = firstBrokerByAddr.get(addr);
      if (firstBroker && firstBroker !== brokerName) {
        duplicates.add(addr);
      } else {
        firstBrokerByAddr.set(addr, brokerName);
      }
    });
  });

  return duplicates;
};

const distributionIssues = (
  route: BrokerRoute,
  writeSkew: RouteQueueSkew,
  readSkew: RouteQueueSkew,
  duplicateAddresses: Set<string>,
): RouteDiagnosticIssue[] => {
  const brokerName = route.brokerName || 'unknown';
  const writeQueues = queueCount(route.writeQueues);
  const readQueues = queueCount(route.readQueues);
  const readable = inferReadable(route);
  const writable = inferWritable(route);
  const brokerAddr = preferredBrokerAddr(route);
  const masterAddr = route.masterAddr ?? route.brokerAddrs?.['0'] ?? '';
  const issues: RouteDiagnosticIssue[] = [];

  if (!brokerAddr) {
    issues.push(
      issue(
        'MISSING_BROKER_ADDRESS',
        'critical',
        brokerName,
      ),
    );
  }

  if (route.brokerAddrs && Object.keys(route.brokerAddrs).length > 0 && !masterAddr) {
    issues.push(
      issue(
        'MISSING_MASTER_ADDRESS',
        'warning',
        brokerName,
      ),
    );
  }

  if (writeQueues === 0) {
    issues.push(
      issue(
        'WRITE_QUEUE_UNAVAILABLE',
        'critical',
        brokerName,
      ),
    );
  }

  if (readQueues === 0) {
    issues.push(
      issue(
        'READ_QUEUE_UNAVAILABLE',
        'critical',
        brokerName,
      ),
    );
  }

  if (!writable) {
    issues.push(
      issue(
        'PERMISSION_NOT_WRITABLE',
        'warning',
        brokerName,
      ),
    );
  }

  if (!readable) {
    issues.push(
      issue(
        'PERMISSION_NOT_READABLE',
        'warning',
        brokerName,
      ),
    );
  }

  if (writeQueues !== readQueues) {
    issues.push(
      issue(
        'READ_WRITE_QUEUE_MISMATCH',
        'warning',
        brokerName,
      ),
    );
  }

  if (hasSkew(writeSkew)) {
    issues.push(
      issue(
        'WRITE_QUEUE_SKEW',
        'warning',
        brokerName,
      ),
    );
  }

  if (hasSkew(readSkew)) {
    issues.push(
      issue(
        'READ_QUEUE_SKEW',
        'warning',
        brokerName,
      ),
    );
  }

  if (routeAddresses(route).some((addr) => duplicateAddresses.has(addr))) {
    issues.push(
      issue(
        'DUPLICATE_BROKER_ADDRESS',
        'warning',
        brokerName,
      ),
    );
  }

  return issues;
};

const buildRecommendations = (issues: RouteDiagnosticIssue[]): string[] => {
  const actions: string[] = [];
  const codes = new Set(issues.map((item) => item.code));

  if (codes.has('NO_ROUTE')) {
    actions.push('topicRoute.rec.noRoute');
  }
  if (codes.has('MISSING_BROKER_ADDRESS') || codes.has('MISSING_MASTER_ADDRESS')) {
    actions.push('topicRoute.rec.checkRegistration');
  }
  if (codes.has('NO_WRITABLE_ROUTE') || codes.has('PERMISSION_NOT_WRITABLE')) {
    actions.push('topicRoute.rec.ensureWritePerm');
  }
  if (codes.has('NO_READABLE_ROUTE') || codes.has('PERMISSION_NOT_READABLE')) {
    actions.push('topicRoute.rec.ensureReadPerm');
  }
  if (
    codes.has('WRITE_QUEUE_UNAVAILABLE') ||
    codes.has('READ_QUEUE_UNAVAILABLE') ||
    codes.has('READ_WRITE_QUEUE_MISMATCH')
  ) {
    actions.push('topicRoute.rec.alignQueueConfig');
  }
  if (codes.has('WRITE_QUEUE_SKEW') || codes.has('READ_QUEUE_SKEW')) {
    actions.push('topicRoute.rec.balanceQueues');
  }
  if (codes.has('SINGLE_BROKER_ROUTE')) {
    actions.push('topicRoute.rec.confirmSingleBroker');
  }
  if (codes.has('DUPLICATE_BROKER_ADDRESS')) {
    actions.push('topicRoute.rec.cleanupRegistration');
  }

  return actions;
};

export const analyzeTopicRoutes = (routes: BrokerRoute[]): TopicRouteDiagnostics => {
  if (routes.length === 0) {
    const issues = [
      issue(
        'NO_ROUTE',
        'critical',
      ),
    ];
    return {
      status: 'critical',
      statusColor: STATUS_COLOR.critical,
      summary: {
        brokerCount: 0,
        addressCount: 0,
        replicaCount: 0,
        writableBrokerCount: 0,
        readableBrokerCount: 0,
        totalWriteQueues: 0,
        totalReadQueues: 0,
        writeSkew: EMPTY_SKEW,
        readSkew: EMPTY_SKEW,
      },
      distributions: [],
      issues,
      recommendations: buildRecommendations(issues),
    };
  }

  const writeCounts = routes.map((route) => queueCount(route.writeQueues));
  const readCounts = routes.map((route) => queueCount(route.readQueues));
  const totalWriteQueues = writeCounts.reduce((sum, value) => sum + value, 0);
  const totalReadQueues = readCounts.reduce((sum, value) => sum + value, 0);
  const writeSkew = calculateSkew(writeCounts);
  const readSkew = calculateSkew(readCounts);
  const duplicateAddresses = collectAddressDuplicates(routes);

  const distributions = routes.map<RouteDistribution>((route, index) => {
    const brokerIds = routeBrokerIds(route);
    const routeIssues = distributionIssues(route, writeSkew, readSkew, duplicateAddresses);

    return {
      key: `${route.brokerName || 'broker'}-${index}`,
      brokerName: route.brokerName || '-',
      brokerAddr: preferredBrokerAddr(route) || '-',
      masterAddr: route.masterAddr ?? route.brokerAddrs?.['0'] ?? '',
      brokerIds,
      replicaCount: routeReplicaCount(route, brokerIds),
      writeQueues: queueCount(route.writeQueues),
      readQueues: queueCount(route.readQueues),
      writeShare: queueShare(queueCount(route.writeQueues), totalWriteQueues),
      readShare: queueShare(queueCount(route.readQueues), totalReadQueues),
      perm: route.perm,
      readable: inferReadable(route),
      writable: inferWritable(route),
      topicSysFlag: route.topicSysFlag,
      status: maxStatus(routeIssues),
      issues: routeIssues,
    };
  });

  const issues = distributions.flatMap((distribution) => distribution.issues);
  const writableBrokerCount = distributions.filter(
    (distribution) => distribution.writable && distribution.writeQueues > 0,
  ).length;
  const readableBrokerCount = distributions.filter(
    (distribution) => distribution.readable && distribution.readQueues > 0,
  ).length;

  if (writableBrokerCount === 0) {
    issues.push(
      issue(
        'NO_WRITABLE_ROUTE',
        'critical',
      ),
    );
  }

  if (readableBrokerCount === 0) {
    issues.push(
      issue(
        'NO_READABLE_ROUTE',
        'critical',
      ),
    );
  }

  if (routes.length === 1) {
    issues.push(
      issue(
        'SINGLE_BROKER_ROUTE',
        'warning',
      ),
    );
  }

  const status = maxStatus(issues);
  const addressCount = new Set(routes.flatMap((route) => routeAddresses(route))).size;

  return {
    status,
    statusColor: STATUS_COLOR[status],
    summary: {
      brokerCount: routes.length,
      addressCount,
      replicaCount: distributions.reduce((sum, item) => sum + item.replicaCount, 0),
      writableBrokerCount,
      readableBrokerCount,
      totalWriteQueues,
      totalReadQueues,
      writeSkew,
      readSkew,
    },
    distributions,
    issues,
    recommendations: buildRecommendations(issues),
  };
};
