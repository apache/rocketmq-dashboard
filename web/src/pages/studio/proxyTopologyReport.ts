/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import type { ProxyNode, ProxyTopologyNode } from '../../api/proxy';

export type ProxyTopologyStatus =
  ProxyTopologyNode['status'] | 'REGISTERED_ONLY' | 'DISCOVERED_ONLY';

export interface ProxyTopologyRow {
  address: string;
  host: string;
  registeredPort: number | null;
  status: ProxyTopologyStatus;
  registered: boolean;
  selected: boolean;
  grpcPort: number | null;
  remotingPort: number | null;
  grpcReachable: boolean | null;
  remotingReachable: boolean | null;
  latencyMs: number | null;
}

export interface ProxyHostGroup {
  host: string;
  nodes: number;
  addresses: string[];
  reachableNodes: number;
  ports: number[];
}

export interface ProxyTopologyReport {
  rows: ProxyTopologyRow[];
  hosts: ProxyHostGroup[];
  summary: {
    registeredNodes: number;
    probedNodes: number;
    fullyReachable: number;
    degradedOrDown: number;
    registrationGaps: number;
    uniqueHosts: number;
    busiestHostNodes: number;
  };
}

export interface ProxyTopologyFilters {
  status?: ProxyTopologyStatus;
  search?: string;
  registrationGapOnly?: boolean;
}

export const parseProxyAddress = (address: string): { host: string; port: number | null } => {
  const trimmed = address.trim();
  if (!trimmed) return { host: '', port: null };
  const bracketed = /^\[([^\]]+)](?::(\d+))?$/.exec(trimmed);
  if (bracketed) {
    const port = bracketed[2] ? Number(bracketed[2]) : null;
    return {
      host: bracketed[1],
      port: port !== null && Number.isInteger(port) && port > 0 && port <= 65535 ? port : null,
    };
  }
  const separator = trimmed.lastIndexOf(':');
  if (separator < 0 || trimmed.indexOf(':') !== separator) return { host: trimmed, port: null };
  const host = trimmed.slice(0, separator);
  const port = Number(trimmed.slice(separator + 1));
  return { host, port: Number.isInteger(port) && port > 0 && port <= 65535 ? port : null };
};

const statusOrder: Record<ProxyTopologyStatus, number> = {
  DOWN: 0,
  PARTIAL: 1,
  REGISTERED_ONLY: 2,
  DISCOVERED_ONLY: 3,
  UP: 4,
};

/** 关联注册地址和实时探测记录，同时保留任一侧缺失的节点以暴露注册漂移。 */
export const buildProxyTopologyReport = (
  registeredNodes: ProxyNode[],
  topologyNodes: ProxyTopologyNode[],
): ProxyTopologyReport => {
  const registeredByAddress = new Map(registeredNodes.map((node) => [node.address, node]));
  const topologyByAddress = new Map(topologyNodes.map((node) => [node.proxyAddr, node]));
  const addresses = new Set([...registeredByAddress.keys(), ...topologyByAddress.keys()]);
  const rows = [...addresses]
    .map<ProxyTopologyRow>((address) => {
      const registration = registeredByAddress.get(address);
      const probe = topologyByAddress.get(address);
      const parsed = parseProxyAddress(address);
      return {
        address,
        host: parsed.host,
        registeredPort: parsed.port,
        status: !registration ? 'DISCOVERED_ONLY' : (probe?.status ?? 'REGISTERED_ONLY'),
        registered: Boolean(registration),
        selected: registration?.isSelected ?? false,
        grpcPort: probe?.grpcPort ?? null,
        remotingPort: probe?.remotingPort ?? null,
        grpcReachable: probe?.grpcReachable ?? null,
        remotingReachable: probe?.remotingReachable ?? null,
        latencyMs: probe?.latencyMs ?? null,
      };
    })
    .sort(
      (left, right) =>
        statusOrder[left.status] - statusOrder[right.status] ||
        left.host.localeCompare(right.host) ||
        left.address.localeCompare(right.address),
    );
  const byHost = new Map<string, ProxyTopologyRow[]>();
  rows.forEach((row) => byHost.set(row.host, [...(byHost.get(row.host) ?? []), row]));
  const hosts = [...byHost.entries()]
    .map<ProxyHostGroup>(([host, nodes]) => ({
      host,
      nodes: nodes.length,
      addresses: nodes.map((node) => node.address).sort((a, b) => a.localeCompare(b)),
      reachableNodes: nodes.filter((node) => node.status === 'UP').length,
      ports: [
        ...new Set(
          nodes
            .flatMap((node) => [node.grpcPort, node.remotingPort])
            .filter((port): port is number => port !== null),
        ),
      ].sort((a, b) => a - b),
    }))
    .sort((left, right) => right.nodes - left.nodes || left.host.localeCompare(right.host));
  const registrationGaps = rows.filter(
    (row) => row.status === 'REGISTERED_ONLY' || row.status === 'DISCOVERED_ONLY',
  ).length;
  return {
    rows,
    hosts,
    summary: {
      registeredNodes: registeredNodes.length,
      probedNodes: topologyNodes.length,
      fullyReachable: rows.filter((row) => row.status === 'UP').length,
      degradedOrDown: rows.filter((row) => row.status === 'PARTIAL' || row.status === 'DOWN')
        .length,
      registrationGaps,
      uniqueHosts: hosts.length,
      busiestHostNodes: hosts[0]?.nodes ?? 0,
    },
  };
};

export const filterProxyTopologyRows = (
  rows: ProxyTopologyRow[],
  filters: ProxyTopologyFilters,
) => {
  const search = filters.search?.trim().toLocaleLowerCase();
  return rows.filter((row) => {
    if (filters.status && row.status !== filters.status) return false;
    if (
      filters.registrationGapOnly &&
      !['REGISTERED_ONLY', 'DISCOVERED_ONLY'].includes(row.status)
    ) {
      return false;
    }
    return (
      !search ||
      [row.address, row.host, row.status].some((value) =>
        value.toLocaleLowerCase().includes(search),
      )
    );
  });
};

export const proxyTopologyCsvRows = (rows: ProxyTopologyRow[]) =>
  rows.map((row) => ({
    address: row.address,
    host: row.host,
    registeredPort: row.registeredPort ?? '',
    status: row.status,
    registered: row.registered,
    selected: row.selected,
    grpcPort: row.grpcPort ?? '',
    remotingPort: row.remotingPort ?? '',
    grpcReachable: row.grpcReachable ?? '',
    remotingReachable: row.remotingReachable ?? '',
    latencyMs: row.latencyMs ?? '',
  }));
