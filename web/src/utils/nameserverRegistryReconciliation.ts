/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import type { ClusterInfo, NameserverRegistryEntry } from '../api/cluster';

export type RegistryReconciliationStatus =
  | 'MATCHED'
  | 'ADDRESS_MISMATCH'
  | 'REGISTRY_ONLY'
  | 'DISCOVERED_ONLY'
  | 'AMBIGUOUS'
  | 'DUPLICATE_MAPPING';

export interface RegistryReconciliationRow {
  key: string;
  status: RegistryReconciliationStatus;
  registryId: number | null;
  registryName: string;
  clusterId: string | null;
  clusterName: string;
  configuredAddresses: string[];
  discoveredAddresses: string[];
  missingAddresses: string[];
  unexpectedAddresses: string[];
  candidateClusters: string[];
}

export interface RegistryReconciliationReport {
  rows: RegistryReconciliationRow[];
  summary: {
    registryEntries: number;
    discoveredClusters: number;
    matched: number;
    addressMismatches: number;
    registryOnly: number;
    discoveredOnly: number;
    ambiguous: number;
    duplicateMappings: number;
    attentionRequired: number;
  };
}

/** 规范化用户保存的地址列表，使协议前缀、分隔符与尾部斜杠不影响核对结果。 */
export const normalizeNameserverAddresses = (value?: string | null): string[] => {
  const addresses = (value ?? '')
    .split(/[;,\s]+/)
    .map((address) =>
      address
        .trim()
        .replace(/^https?:\/\//i, '')
        .replace(/\/+$/, ''),
    )
    .filter(Boolean);
  return [...new Set(addresses)].sort((left, right) => left.localeCompare(right));
};

const normalizedName = (value?: string | null) => (value ?? '').trim().toLowerCase();

const discoveredAddresses = (cluster: ClusterInfo) =>
  normalizeNameserverAddresses(
    [cluster.endpoint, ...(cluster.nameServers ?? []).map((server) => server.addr)]
      .filter(Boolean)
      .join(';'),
  );

const intersects = (left: string[], right: string[]) => {
  const rightSet = new Set(right);
  return left.some((value) => rightSet.has(value));
};

const difference = (left: string[], right: string[]) => {
  const rightSet = new Set(right);
  return left.filter((value) => !rightSet.has(value));
};

const candidateClusters = (entry: NameserverRegistryEntry, clusters: ClusterInfo[]) => {
  const registryName = normalizedName(entry.name);
  const configured = normalizeNameserverAddresses(entry.namesrvAddr);
  return clusters.filter((cluster) => {
    const nameMatch =
      Boolean(registryName) &&
      [cluster.name, cluster.nsClusterName, cluster.id]
        .map(normalizedName)
        .some((name) => name === registryName);
    return nameMatch || intersects(configured, discoveredAddresses(cluster));
  });
};

const registryRow = (
  entry: NameserverRegistryEntry,
  clusters: ClusterInfo[],
): RegistryReconciliationRow => {
  const candidates = candidateClusters(entry, clusters);
  const configured = normalizeNameserverAddresses(entry.namesrvAddr);
  if (candidates.length === 0) {
    return {
      key: `registry-${entry.id}`,
      status: 'REGISTRY_ONLY',
      registryId: entry.id,
      registryName: entry.name,
      clusterId: null,
      clusterName: '-',
      configuredAddresses: configured,
      discoveredAddresses: [],
      missingAddresses: configured,
      unexpectedAddresses: [],
      candidateClusters: [],
    };
  }
  if (candidates.length > 1) {
    return {
      key: `registry-${entry.id}`,
      status: 'AMBIGUOUS',
      registryId: entry.id,
      registryName: entry.name,
      clusterId: null,
      clusterName: '-',
      configuredAddresses: configured,
      discoveredAddresses: [],
      missingAddresses: [],
      unexpectedAddresses: [],
      candidateClusters: candidates.map((cluster) => cluster.name || cluster.id).sort(),
    };
  }

  const cluster = candidates[0];
  const discovered = discoveredAddresses(cluster);
  const missing = difference(configured, discovered);
  const unexpected = difference(discovered, configured);
  return {
    key: `registry-${entry.id}`,
    status: missing.length || unexpected.length ? 'ADDRESS_MISMATCH' : 'MATCHED',
    registryId: entry.id,
    registryName: entry.name,
    clusterId: cluster.id,
    clusterName: cluster.name || cluster.nsClusterName || cluster.id,
    configuredAddresses: configured,
    discoveredAddresses: discovered,
    missingAddresses: missing,
    unexpectedAddresses: unexpected,
    candidateClusters: [cluster.name || cluster.id],
  };
};

/**
 * 核对持久化 NameServer 注册表与实例当前发现的集群视图。
 * 该函数只处理现有接口返回值，不探测网络，也不会修改注册信息。
 */
export const reconcileNameserverRegistry = (
  registry: NameserverRegistryEntry[],
  clusters: ClusterInfo[],
): RegistryReconciliationReport => {
  const registryRows = registry.map((entry) => registryRow(entry, clusters));
  const usage = new Map<string, number>();
  registryRows.forEach((row) => {
    if (row.clusterId) usage.set(row.clusterId, (usage.get(row.clusterId) ?? 0) + 1);
  });
  registryRows.forEach((row) => {
    if (row.clusterId && (usage.get(row.clusterId) ?? 0) > 1) row.status = 'DUPLICATE_MAPPING';
  });

  const consumed = new Set(registryRows.map((row) => row.clusterId).filter(Boolean));
  const discoveredOnlyRows: RegistryReconciliationRow[] = clusters
    .filter((cluster) => !consumed.has(cluster.id))
    .map((cluster) => ({
      key: `cluster-${cluster.id}`,
      status: 'DISCOVERED_ONLY',
      registryId: null,
      registryName: '-',
      clusterId: cluster.id,
      clusterName: cluster.name || cluster.nsClusterName || cluster.id,
      configuredAddresses: [],
      discoveredAddresses: discoveredAddresses(cluster),
      missingAddresses: [],
      unexpectedAddresses: discoveredAddresses(cluster),
      candidateClusters: [cluster.name || cluster.id],
    }));
  const rows = [...registryRows, ...discoveredOnlyRows];
  const count = (status: RegistryReconciliationStatus) =>
    rows.filter((row) => row.status === status).length;
  const matched = count('MATCHED');

  return {
    rows,
    summary: {
      registryEntries: registry.length,
      discoveredClusters: clusters.length,
      matched,
      addressMismatches: count('ADDRESS_MISMATCH'),
      registryOnly: count('REGISTRY_ONLY'),
      discoveredOnly: count('DISCOVERED_ONLY'),
      ambiguous: count('AMBIGUOUS'),
      duplicateMappings: count('DUPLICATE_MAPPING'),
      attentionRequired: rows.length - matched,
    },
  };
};
