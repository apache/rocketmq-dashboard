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
  titleKey: string;
  descriptionKey: string;
  resource?: string;
  clientId?: string;
  evidence: string[];
  recommendationKey: string;
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
  statusKey: string;
  statusColor: 'success' | 'warning' | 'error';
  score: number;
  summary: ClientConnectionHealthSummary;
  resources: ClientResourceSummary[];
  issues: ClientConnectionIssue[];
  recommendationKeys: string[];
}

type ConnectionGroup = {
  type: string;
  resource: string;
  connections: ClientConnection[];
};

// The analyzer returns translation keys, not display text: the page resolves them
// through t(), the same contract as messageTraceDiagnostics.
const STATUS_KEY: Record<ClientConnectionHealthStatus, string> = {
  healthy: 'clientDiag.statusHealthy',
  warning: 'clientDiag.statusWarning',
  critical: 'clientDiag.statusCritical',
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

const uniqueSorted = (values: Array<string | null | undefined>): string[] =>
  [...new Set(values.map((value) => normalizeText(value)).filter(Boolean))].sort((a, b) =>
    a.localeCompare(b),
  );

const countBy = (values: Array<string | null | undefined>): Map<string, number> => {
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
  titleKey: string,
  descriptionKey: string,
  recommendationKey: string,
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
  titleKey,
  descriptionKey,
  resource: options.resource,
  clientId: options.clientId,
  evidence: options.evidence ?? [],
  recommendationKey,
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
        'clientDiag.noConnections.title',
        'clientDiag.noConnections.desc',
        'clientDiag.noConnections.recommendation',
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
        'clientDiag.partialScan.title',
        'clientDiag.partialScan.desc',
        'clientDiag.partialScan.recommendation',
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
          'clientDiag.clientIdCollision.title',
          'clientDiag.clientIdCollision.desc',
          'clientDiag.clientIdCollision.recommendation',
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
        'clientDiag.exactDuplicate.title',
        'clientDiag.exactDuplicate.desc',
        'clientDiag.exactDuplicate.recommendation',
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
          'clientDiag.unknownProtocol.title',
          'clientDiag.unknownProtocol.desc',
          'clientDiag.unknownProtocol.recommendation',
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
          'clientDiag.unknownLanguage.title',
          'clientDiag.unknownLanguage.desc',
          'clientDiag.unknownLanguage.recommendation',
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
          'clientDiag.unknownVersion.title',
          'clientDiag.unknownVersion.desc',
          'clientDiag.unknownVersion.recommendation',
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
          'clientDiag.invalidTime.title',
          'clientDiag.invalidTime.desc',
          'clientDiag.invalidTime.recommendation',
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
          'clientDiag.mixedProtocol.title',
          'clientDiag.mixedProtocol.desc',
          'clientDiag.mixedProtocol.recommendation',
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
          'clientDiag.mixedVersion.title',
          'clientDiag.mixedVersion.desc',
          'clientDiag.mixedVersion.recommendation',
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
          'clientDiag.singleConsumerInstance.title',
          'clientDiag.singleConsumerInstance.desc',
          'clientDiag.singleConsumerInstance.recommendation',
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
          'clientDiag.mixedLanguageVersion.title',
          'clientDiag.mixedLanguageVersion.desc',
          'clientDiag.mixedLanguageVersion.recommendation',
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
        'clientDiag.addressConcentration.title',
        'clientDiag.addressConcentration.desc',
        'clientDiag.addressConcentration.recommendation',
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
    if (seen.has(item.recommendationKey)) return;
    seen.add(item.recommendationKey);
    recommendations.push(item.recommendationKey);
  });

  if (recommendations.length === 0) {
    recommendations.push('clientDiag.recommendation.default');
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
    statusKey: STATUS_KEY[status],
    statusColor: STATUS_COLOR[status],
    score,
    summary: buildSummary(normalizedConnections, issues, resources),
    resources,
    issues,
    recommendationKeys: buildRecommendations(issues),
  };
};
