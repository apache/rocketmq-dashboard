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
  title: string;
  description: string;
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
  statusText: string;
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

const STATUS_TEXT: Record<RouteDiagnosticStatus, string> = {
  healthy: '路由健康',
  warning: '需要关注',
  critical: '不可用',
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
  title: string,
  description: string,
  brokerName?: string,
): RouteDiagnosticIssue => ({
  id: brokerName ? `${brokerName}:${code}` : code,
  code,
  severity,
  title,
  description,
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
        'Broker 地址缺失',
        'NameServer 返回了队列元数据，但没有返回可用于定位 Broker 的地址。',
        brokerName,
      ),
    );
  }

  if (route.brokerAddrs && Object.keys(route.brokerAddrs).length > 0 && !masterAddr) {
    issues.push(
      issue(
        'MISSING_MASTER_ADDRESS',
        'warning',
        'Master 地址缺失',
        '该 Broker 只返回了非 master 地址，Topic 写入链路需要确认 master 是否在线。',
        brokerName,
      ),
    );
  }

  if (writeQueues === 0) {
    issues.push(
      issue(
        'WRITE_QUEUE_UNAVAILABLE',
        'critical',
        '写队列不可用',
        '该 Broker 没有可写队列，生产者不会把消息写到这个 Broker。',
        brokerName,
      ),
    );
  }

  if (readQueues === 0) {
    issues.push(
      issue(
        'READ_QUEUE_UNAVAILABLE',
        'critical',
        '读队列不可用',
        '该 Broker 没有可读队列，消费者不会从这个 Broker 拉取消息。',
        brokerName,
      ),
    );
  }

  if (!writable) {
    issues.push(
      issue(
        'PERMISSION_NOT_WRITABLE',
        'warning',
        '权限不允许写入',
        'Topic 权限缺少写权限，生产者发送可能失败或被路由到其他 Broker。',
        brokerName,
      ),
    );
  }

  if (!readable) {
    issues.push(
      issue(
        'PERMISSION_NOT_READABLE',
        'warning',
        '权限不允许读取',
        'Topic 权限缺少读权限，消费者订阅后可能无法正常消费。',
        brokerName,
      ),
    );
  }

  if (writeQueues !== readQueues) {
    issues.push(
      issue(
        'READ_WRITE_QUEUE_MISMATCH',
        'warning',
        '读写队列不一致',
        '该 Broker 的读队列数和写队列数不同，扩缩容或迁移后需要确认配置是否符合预期。',
        brokerName,
      ),
    );
  }

  if (routeAddresses(route).some((addr) => duplicateAddresses.has(addr))) {
    issues.push(
      issue(
        'DUPLICATE_BROKER_ADDRESS',
        'warning',
        'Broker 地址重复',
        '多个 BrokerName 返回了相同地址，请确认 NameServer 注册信息是否过期。',
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
    actions.push('确认 Topic 已在目标 Broker 上创建；必要时使用“在 Broker 上重建”。');
  }
  if (codes.has('MISSING_BROKER_ADDRESS') || codes.has('MISSING_MASTER_ADDRESS')) {
    actions.push('检查 Broker 是否仍向 NameServer 注册，并确认 master 节点可达。');
  }
  if (codes.has('NO_WRITABLE_ROUTE') || codes.has('PERMISSION_NOT_WRITABLE')) {
    actions.push('确认 Topic 权限包含写权限，避免生产者发送失败。');
  }
  if (codes.has('NO_READABLE_ROUTE') || codes.has('PERMISSION_NOT_READABLE')) {
    actions.push('确认 Topic 权限包含读权限，避免消费者订阅后无可读队列。');
  }
  if (
    codes.has('WRITE_QUEUE_UNAVAILABLE') ||
    codes.has('READ_QUEUE_UNAVAILABLE') ||
    codes.has('READ_WRITE_QUEUE_MISMATCH')
  ) {
    actions.push('对比各 Broker 上的 TopicConfig，统一读写队列数后再观察客户端路由。');
  }
  if (codes.has('WRITE_QUEUE_SKEW') || codes.has('READ_QUEUE_SKEW')) {
    actions.push('评估是否需要扩容、迁移或重新分配队列，降低单 Broker 负载集中风险。');
  }
  if (codes.has('SINGLE_BROKER_ROUTE')) {
    actions.push('确认该 Topic 是否预期只部署在单 Broker；生产业务建议准备冗余路由。');
  }
  if (codes.has('DUPLICATE_BROKER_ADDRESS')) {
    actions.push('清理过期 Broker 注册信息，避免客户端拿到重复或错误地址。');
  }

  return actions;
};

export const analyzeTopicRoutes = (routes: BrokerRoute[]): TopicRouteDiagnostics => {
  if (routes.length === 0) {
    const issues = [
      issue(
        'NO_ROUTE',
        'critical',
        'Broker 上没有 Topic 路由',
        '元数据中存在 Topic 记录，但当前实例没有返回任何 Broker 路由。',
      ),
    ];
    return {
      status: 'critical',
      statusText: STATUS_TEXT.critical,
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
    const routeIssues = distributionIssues(route, duplicateAddresses);

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
  if (hasSkew(writeSkew)) {
    issues.push(
      issue(
        'WRITE_QUEUE_SKEW',
        'warning',
        '写队列分布不均',
        '不同 Broker 的写队列数差距较大，生产流量可能无法均匀分摊。',
      ),
    );
  }
  if (hasSkew(readSkew)) {
    issues.push(
      issue(
        'READ_QUEUE_SKEW',
        'warning',
        '读队列分布不均',
        '不同 Broker 的读队列数差距较大，消费者负载可能无法均匀分摊。',
      ),
    );
  }
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
        '没有可写路由',
        '所有 Broker 都缺少写权限或写队列，生产者无法向该 Topic 发送消息。',
      ),
    );
  }

  if (readableBrokerCount === 0) {
    issues.push(
      issue(
        'NO_READABLE_ROUTE',
        'critical',
        '没有可读路由',
        '所有 Broker 都缺少读权限或读队列，消费者无法从该 Topic 拉取消息。',
      ),
    );
  }

  if (routes.length === 1) {
    issues.push(
      issue(
        'SINGLE_BROKER_ROUTE',
        'warning',
        '单 Broker 路由',
        '该 Topic 只返回一个 Broker 路由，生产业务需要确认是否符合容灾预期。',
      ),
    );
  }

  const status = maxStatus(issues);
  const addressCount = new Set(routes.flatMap((route) => routeAddresses(route))).size;

  return {
    status,
    statusText: STATUS_TEXT[status],
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
