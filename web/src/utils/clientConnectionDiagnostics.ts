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

import type { ClientConnection } from '../api/connections';

export type ClientConnectionHealthStatus = 'healthy' | 'warning' | 'critical';
export type ClientConnectionIssueSeverity =
  Exclude<ClientConnectionHealthStatus, 'healthy'> | 'info';

export type ClientConnectionIssueCode =
  | 'NO_CONNECTIONS'
  | 'PARTIAL_CONNECTION_SCAN'
  | 'CLIENT_ID_COLLISION'
  | 'EXACT_DUPLICATE_CONNECTION'
  | 'MIXED_PROTOCOL_RESOURCE'
  | 'MIXED_VERSION_RESOURCE'
  | 'SINGLE_CONSUMER_INSTANCE'
  | 'ADDRESS_CONCENTRATION'
  | 'UNKNOWN_PROTOCOL'
  | 'UNKNOWN_LANGUAGE'
  | 'UNKNOWN_VERSION'
  | 'INVALID_CONNECTION_TIME';

export interface ClientConnectionIssue {
  id: string;
  code: ClientConnectionIssueCode;
  severity: ClientConnectionIssueSeverity;
  title: string;
  description: string;
  resource?: string;
  clientId?: string;
  evidence: string[];
  recommendation: string;
}

export interface ClientResourceSummary {
  id: string;
  type: string;
  resource: string;
  connectionCount: number;
  uniqueClientCount: number;
  uniqueAddressCount: number;
  protocols: string[];
  languages: string[];
  versions: string[];
  partial: boolean;
  status: ClientConnectionHealthStatus;
  issueCount: number;
}

export interface ClientConnectionHealthSummary {
  totalConnections: number;
  uniqueClientCount: number;
  uniqueAddressCount: number;
  resourceCount: number;
  partialConnectionCount: number;
  mixedProtocolResourceCount: number;
  mixedVersionResourceCount: number;
  singleConsumerGroupCount: number;
  concentratedAddressCount: number;
}

export interface ClientConnectionDiagnostics {
  status: ClientConnectionHealthStatus;
  statusText: string;
  statusColor: 'success' | 'warning' | 'error';
  score: number;
  summary: ClientConnectionHealthSummary;
  resources: ClientResourceSummary[];
  issues: ClientConnectionIssue[];
  recommendations: string[];
}

type ConnectionGroup = {
  type: string;
  resource: string;
  connections: ClientConnection[];
};

const STATUS_TEXT: Record<ClientConnectionHealthStatus, string> = {
  healthy: '客户端连接健康',
  warning: '客户端连接需要关注',
  critical: '客户端连接存在高风险',
};

const STATUS_COLOR: Record<ClientConnectionHealthStatus, 'success' | 'warning' | 'error'> = {
  healthy: 'success',
  warning: 'warning',
  critical: 'error',
};

const KNOWN_PROTOCOLS = new Set(['gRPC', 'Remoting']);
const KNOWN_LANGUAGES = new Set(['Java', 'Go', 'Python', 'Rust', 'Cpp', 'CSharp', 'NodeJS', 'PHP']);

const normalizeText = (value?: string | null, fallback = 'unknown'): string => {
  const trimmed = (value ?? '').trim();
  return trimmed || fallback;
};

const uniqueSorted = (values: string[]): string[] =>
  [...new Set(values.map((value) => normalizeText(value)).filter(Boolean))].sort((a, b) =>
    a.localeCompare(b),
  );

const countBy = (values: string[]): Map<string, number> => {
  const counts = new Map<string, number>();
  values.forEach((value) => {
    const normalized = normalizeText(value);
    counts.set(normalized, (counts.get(normalized) ?? 0) + 1);
  });
  return counts;
};

const issue = (
  code: ClientConnectionIssueCode,
  severity: ClientConnectionIssueSeverity,
  title: string,
  description: string,
  recommendation: string,
  options: {
    resource?: string;
    clientId?: string;
    evidence?: string[];
    id?: string;
  } = {},
): ClientConnectionIssue => ({
  id:
    options.id ??
    [options.resource, options.clientId, code, ...(options.evidence ?? [])]
      .filter(Boolean)
      .join(':'),
  code,
  severity,
  title,
  description,
  resource: options.resource,
  clientId: options.clientId,
  evidence: options.evidence ?? [],
  recommendation,
});

const connectionIdentity = (connection: ClientConnection): string =>
  [
    normalizeText(connection.type),
    normalizeText(connection.clientId),
    normalizeText(connection.groupOrTopic),
    normalizeText(connection.address),
  ].join('|');

const resourceKey = (connection: ClientConnection): string =>
  `${normalizeText(connection.type)}:${normalizeText(connection.groupOrTopic)}`;

const groupConnections = (connections: ClientConnection[]): ConnectionGroup[] => {
  const groups = new Map<string, ConnectionGroup>();

  connections.forEach((connection) => {
    const key = resourceKey(connection);
    const group = groups.get(key);
    if (group) {
      group.connections.push(connection);
      return;
    }
    groups.set(key, {
      type: normalizeText(connection.type),
      resource: normalizeText(connection.groupOrTopic),
      connections: [connection],
    });
  });

  return [...groups.values()].sort(
    (left, right) =>
      left.type.localeCompare(right.type) || left.resource.localeCompare(right.resource),
  );
};

const parseTime = (value?: string | null): number | null => {
  if (!value) return null;
  const normalized = value.includes('T') ? value : value.replace(' ', 'T');
  const timestamp = Date.parse(normalized);
  return Number.isFinite(timestamp) ? timestamp : null;
};

const resourceSeverity = (issues: ClientConnectionIssue[]): ClientConnectionHealthStatus => {
  if (issues.some((item) => item.severity === 'critical')) return 'critical';
  if (issues.some((item) => item.severity === 'warning')) return 'warning';
  return 'healthy';
};

const addInventoryIssues = (connections: ClientConnection[], issues: ClientConnectionIssue[]) => {
  if (connections.length === 0) {
    issues.push(
      issue(
        'NO_CONNECTIONS',
        'critical',
        '未发现客户端连接',
        '当前 NameServer 查询没有返回任何 Producer 或 Consumer 连接。',
        '确认目标 NameServer、Proxy 和 Broker 侧客户端注册链路是否正常。',
      ),
    );
    return;
  }

  const partialCount = connections.filter((connection) => connection.partial).length;
  if (partialCount > 0) {
    issues.push(
      issue(
        'PARTIAL_CONNECTION_SCAN',
        'warning',
        '客户端扫描结果不完整',
        '部分 Producer 连接来自受限 Topic 扫描，当前列表可能不是完整客户端清单。',
        '缩小 Topic 或集群范围后重新查询，并在排障时避免把当前列表视为全集。',
        { evidence: [`partial=${partialCount}`] },
      ),
    );
  }
};

const addClientIdIssues = (connections: ClientConnection[], issues: ClientConnectionIssue[]) => {
  const connectionsByClient = new Map<string, ClientConnection[]>();
  const identityCounts = new Map<string, number>();

  connections.forEach((connection) => {
    const clientId = normalizeText(connection.clientId);
    const existing = connectionsByClient.get(clientId) ?? [];
    existing.push(connection);
    connectionsByClient.set(clientId, existing);

    const identity = connectionIdentity(connection);
    identityCounts.set(identity, (identityCounts.get(identity) ?? 0) + 1);
  });

  connectionsByClient.forEach((clientConnections, clientId) => {
    const addresses = uniqueSorted(clientConnections.map((connection) => connection.address));
    if (addresses.length > 1) {
      issues.push(
        issue(
          'CLIENT_ID_COLLISION',
          'critical',
          'Client ID 连接到多个地址',
          '同一个 Client ID 同时出现在多个远端地址，可能是实例 ID 配置冲突或旧连接未及时清理。',
          '检查客户端 instanceName/clientId 配置，确保同一进程实例使用唯一标识。',
          {
            clientId,
            evidence: addresses,
          },
        ),
      );
    }
  });

  identityCounts.forEach((count, identity) => {
    if (count <= 1) return;
    const [, clientId, resource, address] = identity.split('|');
    issues.push(
      issue(
        'EXACT_DUPLICATE_CONNECTION',
        'info',
        '连接记录重复',
        '相同客户端、资源和地址出现了重复记录，可能来自采集侧合并或上游返回重复项。',
        '刷新连接清单；若重复持续存在，检查客户端连接采集路径是否重复汇总。',
        {
          clientId,
          resource,
          evidence: [`address=${address}`, `count=${count}`],
          id: `${identity}:EXACT_DUPLICATE_CONNECTION`,
        },
      ),
    );
  });
};

const addUnknownFieldIssues = (
  connections: ClientConnection[],
  issues: ClientConnectionIssue[],
) => {
  connections.forEach((connection, index) => {
    const clientId = normalizeText(connection.clientId);
    const resource = normalizeText(connection.groupOrTopic);
    const protocol = normalizeText(connection.protocol);
    const language = normalizeText(connection.language);
    const version = normalizeText(connection.version);

    if (!KNOWN_PROTOCOLS.has(protocol)) {
      issues.push(
        issue(
          'UNKNOWN_PROTOCOL',
          'warning',
          '协议类型未知',
          '该客户端连接的协议不在 Studio 已知协议列表中，统计和排障可能不准确。',
          '确认客户端协议版本和服务端采集字段，必要时补充协议映射。',
          {
            clientId,
            resource,
            evidence: [`protocol=${protocol}`],
            id: `${index}:UNKNOWN_PROTOCOL`,
          },
        ),
      );
    }

    if (!KNOWN_LANGUAGES.has(language)) {
      issues.push(
        issue(
          'UNKNOWN_LANGUAGE',
          'info',
          '客户端语言未知',
          '该客户端连接的语言不在 Studio 已知语言列表中。',
          '确认客户端 SDK 语言和采集字段，必要时补充语言展示映射。',
          {
            clientId,
            resource,
            evidence: [`language=${language}`],
            id: `${index}:UNKNOWN_LANGUAGE`,
          },
        ),
      );
    }

    if (version === 'unknown' || version === '-') {
      issues.push(
        issue(
          'UNKNOWN_VERSION',
          'warning',
          '客户端版本未知',
          '该客户端没有上报明确版本，升级治理和兼容性判断缺少依据。',
          '升级客户端 SDK 或检查版本字段采集，确保连接清单能展示真实客户端版本。',
          {
            clientId,
            resource,
            evidence: [`version=${version}`],
            id: `${index}:UNKNOWN_VERSION`,
          },
        ),
      );
    }

    if (connection.connectedAt && parseTime(connection.connectedAt) === null) {
      issues.push(
        issue(
          'INVALID_CONNECTION_TIME',
          'info',
          '连接时间无法解析',
          '该连接的时间字段无法被浏览器解析，排序和人工判断可能受影响。',
          '统一连接时间格式，优先返回 ISO-8601 或 yyyy-MM-dd HH:mm:ss。',
          {
            clientId,
            resource,
            evidence: [connection.connectedAt],
            id: `${index}:INVALID_CONNECTION_TIME`,
          },
        ),
      );
    }
  });
};

const addResourceIssues = (groups: ConnectionGroup[], issues: ClientConnectionIssue[]) => {
  groups.forEach((group) => {
    const protocols = uniqueSorted(group.connections.map((connection) => connection.protocol));
    const versions = uniqueSorted(group.connections.map((connection) => connection.version));
    const languages = uniqueSorted(group.connections.map((connection) => connection.language));
    const clients = uniqueSorted(group.connections.map((connection) => connection.clientId));
    const addresses = uniqueSorted(group.connections.map((connection) => connection.address));
    const resource = `${group.type}:${group.resource}`;

    if (protocols.length > 1) {
      issues.push(
        issue(
          'MIXED_PROTOCOL_RESOURCE',
          'warning',
          '同一资源存在多协议连接',
          '同一个 Group 或 Topic 同时存在 gRPC 与 Remoting 客户端，迁移期排障需要区分控制面来源。',
          '确认该资源是否处于协议迁移期，并分别检查 Proxy 与 Broker 侧连接状态。',
          {
            resource,
            evidence: protocols,
          },
        ),
      );
    }

    if (versions.length > 1) {
      issues.push(
        issue(
          'MIXED_VERSION_RESOURCE',
          'warning',
          '同一资源存在多版本客户端',
          '同一个 Group 或 Topic 内客户端版本不一致，可能导致重试、负载均衡或协议能力差异。',
          '按资源维度收敛客户端 SDK 版本，升级后再次确认连接清单。',
          {
            resource,
            evidence: versions,
          },
        ),
      );
    }

    if (group.type === 'Consumer' && clients.length === 1 && addresses.length === 1) {
      issues.push(
        issue(
          'SINGLE_CONSUMER_INSTANCE',
          'warning',
          'Consumer Group 只有单实例在线',
          '该 Consumer Group 当前只有一个客户端实例，进程故障会直接影响消费连续性。',
          '为关键 Consumer Group 保持至少两个实例在线，并确认负载均衡后队列分配正常。',
          {
            resource,
            evidence: [`address=${addresses[0]}`],
          },
        ),
      );
    }

    if (languages.length > 1 && versions.length > 1) {
      issues.push(
        issue(
          'MIXED_VERSION_RESOURCE',
          'info',
          '同一资源存在多语言多版本客户端',
          '该资源由多语言 SDK 共同访问，升级治理和兼容性排查需要同时关注语言与版本。',
          '记录各语言 SDK 的目标版本矩阵，避免只按单一语言判断升级完成度。',
          {
            resource,
            evidence: [...languages, ...versions],
            id: `${resource}:MIXED_LANGUAGE_VERSION_RESOURCE`,
          },
        ),
      );
    }
  });
};

const addAddressConcentrationIssues = (
  connections: ClientConnection[],
  issues: ClientConnectionIssue[],
) => {
  if (connections.length < 4) return;

  const addressCounts = countBy(connections.map((connection) => connection.address));
  const threshold = Math.max(4, Math.ceil(connections.length * 0.5));

  addressCounts.forEach((count, address) => {
    if (count < threshold) return;
    issues.push(
      issue(
        'ADDRESS_CONCENTRATION',
        'warning',
        '连接集中在单一地址',
        '较多客户端连接集中在同一个地址，主机或网关故障会影响多个生产或消费链路。',
        '检查该地址上的客户端部署密度，必要时拆分实例或调整负载分布。',
        {
          evidence: [`${address}: ${count}/${connections.length}`],
          id: `${address}:ADDRESS_CONCENTRATION`,
        },
      ),
    );
  });
};

const issuesForResource = (issues: ClientConnectionIssue[], resource: string) =>
  issues.filter((issue) => issue.resource === resource);

const buildResourceSummaries = (
  groups: ConnectionGroup[],
  issues: ClientConnectionIssue[],
): ClientResourceSummary[] =>
  groups
    .map((group) => {
      const resource = `${group.type}:${group.resource}`;
      const resourceIssues = issuesForResource(issues, resource);
      return {
        id: resource,
        type: group.type,
        resource: group.resource,
        connectionCount: group.connections.length,
        uniqueClientCount: uniqueSorted(group.connections.map((connection) => connection.clientId))
          .length,
        uniqueAddressCount: uniqueSorted(group.connections.map((connection) => connection.address))
          .length,
        protocols: uniqueSorted(group.connections.map((connection) => connection.protocol)),
        languages: uniqueSorted(group.connections.map((connection) => connection.language)),
        versions: uniqueSorted(group.connections.map((connection) => connection.version)),
        partial: group.connections.some((connection) => connection.partial),
        status: resourceSeverity(resourceIssues),
        issueCount: resourceIssues.length,
      };
    })
    .sort((left, right) => {
      const statusOrder: Record<ClientConnectionHealthStatus, number> = {
        critical: 0,
        warning: 1,
        healthy: 2,
      };
      return (
        statusOrder[left.status] - statusOrder[right.status] ||
        right.issueCount - left.issueCount ||
        left.resource.localeCompare(right.resource)
      );
    });

const buildSummary = (
  connections: ClientConnection[],
  issues: ClientConnectionIssue[],
  resources: ClientResourceSummary[],
): ClientConnectionHealthSummary => {
  const addressConcentrationIssues = issues.filter((item) => item.code === 'ADDRESS_CONCENTRATION');
  return {
    totalConnections: connections.length,
    uniqueClientCount: uniqueSorted(connections.map((connection) => connection.clientId)).length,
    uniqueAddressCount: uniqueSorted(connections.map((connection) => connection.address)).length,
    resourceCount: resources.length,
    partialConnectionCount: connections.filter((connection) => connection.partial).length,
    mixedProtocolResourceCount: resources.filter((resource) => resource.protocols.length > 1)
      .length,
    mixedVersionResourceCount: resources.filter((resource) => resource.versions.length > 1).length,
    singleConsumerGroupCount: issues.filter((item) => item.code === 'SINGLE_CONSUMER_INSTANCE')
      .length,
    concentratedAddressCount: addressConcentrationIssues.length,
  };
};

const scoreDiagnostics = (issues: ClientConnectionIssue[]): number => {
  const penalty = issues.reduce((sum, item) => {
    if (item.severity === 'critical') return sum + 24;
    if (item.severity === 'warning') return sum + 9;
    return sum + 3;
  }, 0);
  return Math.max(0, 100 - penalty);
};

const statusFromIssues = (
  issues: ClientConnectionIssue[],
  score: number,
): ClientConnectionHealthStatus => {
  if (issues.some((item) => item.severity === 'critical') || score < 60) return 'critical';
  if (issues.some((item) => item.severity === 'warning') || score < 90) return 'warning';
  return 'healthy';
};

const buildRecommendations = (issues: ClientConnectionIssue[]): string[] => {
  const recommendations: string[] = [];
  const seen = new Set<string>();

  issues.forEach((item) => {
    if (seen.has(item.recommendation)) return;
    seen.add(item.recommendation);
    recommendations.push(item.recommendation);
  });

  if (recommendations.length === 0) {
    recommendations.push('保持客户端连接清单按集群定期巡检，重点关注协议和 SDK 版本收敛。');
  }

  return recommendations.slice(0, 6);
};

export const analyzeClientConnections = (
  connections: ClientConnection[],
): ClientConnectionDiagnostics => {
  const normalizedConnections = connections.map((connection) => ({
    ...connection,
    clientId: normalizeText(connection.clientId),
    type: normalizeText(connection.type),
    groupOrTopic: normalizeText(connection.groupOrTopic),
    protocol: normalizeText(connection.protocol),
    address: normalizeText(connection.address),
    language: normalizeText(connection.language),
    version: normalizeText(connection.version),
  }));
  const groups = groupConnections(normalizedConnections);
  const issues: ClientConnectionIssue[] = [];

  addInventoryIssues(normalizedConnections, issues);
  addClientIdIssues(normalizedConnections, issues);
  addUnknownFieldIssues(normalizedConnections, issues);
  addResourceIssues(groups, issues);
  addAddressConcentrationIssues(normalizedConnections, issues);

  const resources = buildResourceSummaries(groups, issues);
  const score = scoreDiagnostics(issues);
  const status = statusFromIssues(issues, score);

  return {
    status,
    statusText: STATUS_TEXT[status],
    statusColor: STATUS_COLOR[status],
    score,
    summary: buildSummary(normalizedConnections, issues, resources),
    resources,
    issues,
    recommendations: buildRecommendations(issues),
  };
};
