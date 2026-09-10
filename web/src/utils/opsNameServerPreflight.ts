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

export type OpsNameServerReadiness = 'ready' | 'warning' | 'blocked';
export type OpsNameServerSeverity = 'critical' | 'warning' | 'info';

export interface NameServerEndpointDiagnostic {
  raw: string;
  normalized: string;
  host: string;
  port: number | null;
  valid: boolean;
  reason?: string;
}

export interface NameServerAddressDiagnostic {
  key: string;
  raw: string;
  normalized: string;
  endpoints: NameServerEndpointDiagnostic[];
  valid: boolean;
  duplicate: boolean;
  current: boolean;
  selected: boolean;
  issueText: string[];
}

export interface OpsNameServerIssue {
  id: string;
  severity: OpsNameServerSeverity;
  title: string;
  description: string;
}

export interface OpsNameServerPendingChange {
  id: string;
  severity: OpsNameServerSeverity;
  title: string;
  description: string;
}

export interface OpsNameServerCandidate {
  raw: string;
  normalized: string;
  status: 'empty' | 'ready' | 'invalid' | 'duplicate';
  message: string;
  endpoints: NameServerEndpointDiagnostic[];
}

export interface OpsNameServerPreflightInput {
  namesrvAddrList: string[];
  currentNamesrv: string;
  selectedNamesrv: string;
  newNamesrvAddr: string;
  configurationAvailable: boolean;
  unavailableReason?: string;
  canWrite: boolean;
  useVIPChannel: boolean;
  useTLS: boolean;
}

export interface OpsNameServerPreflight {
  readiness: OpsNameServerReadiness;
  statusText: string;
  statusDescription: string;
  addresses: NameServerAddressDiagnostic[];
  issues: OpsNameServerIssue[];
  pendingChanges: OpsNameServerPendingChange[];
  recommendations: string[];
  newAddress: OpsNameServerCandidate;
  stats: {
    configuredAddresses: number;
    validAddresses: number;
    invalidAddresses: number;
    duplicateAddresses: number;
    endpointCount: number;
    validEndpointCount: number;
  };
  selectedAddressValid: boolean;
  currentAddressKnown: boolean;
}

const HOST_PATTERN = /^[a-zA-Z0-9.-]+$/;
const IPV4_PATTERN = /^(25[0-5]|2[0-4]\d|1?\d?\d)(\.(25[0-5]|2[0-4]\d|1?\d?\d)){3}$/;

const trimAddress = (value: string | null | undefined) => (value ?? '').trim();

const parsePort = (value: string): number | null => {
  if (!/^\d+$/.test(value)) return null;
  const port = Number(value);
  return port >= 1 && port <= 65535 ? port : null;
};

const normalizeHost = (host: string) => host.trim().toLowerCase();

const isHostnameLike = (host: string) => {
  if (!host || /\s/.test(host)) return false;
  if (host === 'localhost') return true;
  if (IPV4_PATTERN.test(host)) return true;
  if (!HOST_PATTERN.test(host)) return false;
  return !host.startsWith('.') && !host.endsWith('.') && !host.includes('..');
};

const parseBracketedEndpoint = (raw: string): NameServerEndpointDiagnostic | null => {
  const match = /^\[([^\]]+)]:(\d+)$/.exec(raw);
  if (!match) return null;
  const host = normalizeHost(match[1]);
  const port = parsePort(match[2]);
  const normalized = port == null ? raw : `[${host}]:${port}`;
  return {
    raw,
    normalized,
    host,
    port,
    valid: Boolean(host && port != null),
    reason: host && port != null ? undefined : 'IPv6 地址必须包含有效端口',
  };
};

export function parseNameServerEndpoint(rawEndpoint: string): NameServerEndpointDiagnostic {
  const raw = rawEndpoint.trim();
  if (!raw) {
    return {
      raw,
      normalized: raw,
      host: '',
      port: null,
      valid: false,
      reason: '地址为空',
    };
  }
  if (raw.includes('://')) {
    return {
      raw,
      normalized: raw,
      host: raw,
      port: null,
      valid: false,
      reason: 'NameServer 地址不应包含协议前缀',
    };
  }

  const bracketed = parseBracketedEndpoint(raw);
  if (bracketed) return bracketed;
  if (raw.startsWith('[') || raw.includes(']')) {
    return {
      raw,
      normalized: raw,
      host: raw,
      port: null,
      valid: false,
      reason: 'IPv6 地址格式应为 [host]:port',
    };
  }

  const firstColon = raw.indexOf(':');
  const lastColon = raw.lastIndexOf(':');
  if (firstColon <= 0 || firstColon !== lastColon || lastColon === raw.length - 1) {
    return {
      raw,
      normalized: raw,
      host: raw,
      port: null,
      valid: false,
      reason: 'NameServer 地址格式应为 host:port',
    };
  }

  const host = normalizeHost(raw.slice(0, lastColon));
  const port = parsePort(raw.slice(lastColon + 1));
  const normalized = port == null ? raw : `${host}:${port}`;
  if (!isHostnameLike(host)) {
    return {
      raw,
      normalized,
      host,
      port,
      valid: false,
      reason: 'NameServer host 只能包含域名、IP 或 localhost',
    };
  }
  if (port == null) {
    return {
      raw,
      normalized,
      host,
      port: null,
      valid: false,
      reason: 'NameServer 端口必须在 1-65535 之间',
    };
  }

  return { raw, normalized, host, port, valid: true };
}

export function parseNameServerAddress(
  rawAddress: string,
): Omit<NameServerAddressDiagnostic, 'key' | 'duplicate' | 'current' | 'selected'> {
  const raw = trimAddress(rawAddress);
  const parts = raw ? raw.split(/[;,]/).map((part) => part.trim()) : [];
  const endpoints = parts.length > 0 ? parts.map(parseNameServerEndpoint) : [];
  const valid = endpoints.length > 0 && endpoints.every((endpoint) => endpoint.valid);
  const normalized = valid ? endpoints.map((endpoint) => endpoint.normalized).join(';') : raw;
  const endpointCounts = endpoints.reduce((counts, endpoint) => {
    if (endpoint.valid) counts.set(endpoint.normalized, (counts.get(endpoint.normalized) ?? 0) + 1);
    return counts;
  }, new Map<string, number>());
  const repeatedEndpoints = endpoints
    .filter((endpoint) => endpoint.valid && (endpointCounts.get(endpoint.normalized) ?? 0) > 1)
    .map((endpoint) => endpoint.normalized);
  const issueText = [
    ...endpoints.filter((endpoint) => !endpoint.valid).map((endpoint) => endpoint.reason ?? ''),
    ...(repeatedEndpoints.length > 0
      ? [`同一个地址串内包含重复 endpoint: ${[...new Set(repeatedEndpoints)].join(', ')}`]
      : []),
  ].filter(Boolean);

  return {
    raw,
    normalized,
    endpoints,
    valid: valid && repeatedEndpoints.length === 0,
    issueText,
  };
}

const buildCandidate = (
  rawAddress: string,
  existingNormalized: Set<string>,
): OpsNameServerCandidate => {
  const raw = trimAddress(rawAddress);
  if (!raw) {
    return {
      raw,
      normalized: '',
      status: 'empty',
      message: '输入新的 NameServer 地址后会在这里预检',
      endpoints: [],
    };
  }
  const parsed = parseNameServerAddress(raw);
  if (!parsed.valid) {
    return {
      raw,
      normalized: parsed.normalized,
      status: 'invalid',
      message: parsed.issueText[0] ?? 'NameServer 地址格式无效',
      endpoints: parsed.endpoints,
    };
  }
  if (existingNormalized.has(parsed.normalized)) {
    return {
      raw,
      normalized: parsed.normalized,
      status: 'duplicate',
      message: '该 NameServer 地址已存在，无需重复添加',
      endpoints: parsed.endpoints,
    };
  }
  return {
    raw,
    normalized: parsed.normalized,
    status: 'ready',
    message: `将新增 ${parsed.endpoints.length} 个 endpoint`,
    endpoints: parsed.endpoints,
  };
};

const unique = <T>(items: T[]) => [...new Set(items)];

export function analyzeOpsNameServerPreflight(
  input: OpsNameServerPreflightInput,
): OpsNameServerPreflight {
  const parsedAddresses = input.namesrvAddrList.map((address, index) => ({
    ...parseNameServerAddress(address),
    key: `${index}:${address}`,
    duplicate: false,
    current: false,
    selected: false,
  }));
  const validNormalizedCounts = parsedAddresses.reduce((counts, address) => {
    if (address.valid) {
      counts.set(address.normalized, (counts.get(address.normalized) ?? 0) + 1);
    }
    return counts;
  }, new Map<string, number>());
  const existingNormalized = new Set(validNormalizedCounts.keys());
  const current = parseNameServerAddress(input.currentNamesrv);
  const selected = parseNameServerAddress(input.selectedNamesrv);
  const currentAddressKnown = Boolean(
    current.valid && parsedAddresses.some((address) => address.normalized === current.normalized),
  );
  const selectedAddressValid = Boolean(
    selected.valid && existingNormalized.has(selected.normalized),
  );
  const addresses = parsedAddresses.map((address) => ({
    ...address,
    duplicate: address.valid && (validNormalizedCounts.get(address.normalized) ?? 0) > 1,
    current: current.valid && address.normalized === current.normalized,
    selected: selected.valid && address.normalized === selected.normalized,
  }));
  const newAddress = buildCandidate(input.newNamesrvAddr, existingNormalized);
  const invalidAddresses = addresses.filter((address) => !address.valid);
  const duplicateAddresses = addresses.filter((address) => address.duplicate);
  const validAddresses = addresses.filter((address) => address.valid);
  const issues: OpsNameServerIssue[] = [];

  if (!input.configurationAvailable) {
    issues.push({
      id: 'configuration-unavailable',
      severity: 'critical',
      title: '运行时配置不可写',
      description: input.unavailableReason || '当前环境无法读取或更新 Ops 配置',
    });
  }
  if (addresses.length === 0) {
    issues.push({
      id: 'empty-list',
      severity: 'critical',
      title: 'NameServer 地址列表为空',
      description: '至少保留一个可连接的 NameServer 地址，避免客户端无法发现 Broker',
    });
  }
  if (invalidAddresses.length > 0) {
    issues.push({
      id: 'invalid-addresses',
      severity: 'warning',
      title: '存在格式无效的 NameServer 地址',
      description: invalidAddresses.map((address) => address.raw || '(empty)').join(', '),
    });
  }
  if (duplicateAddresses.length > 0) {
    issues.push({
      id: 'duplicate-addresses',
      severity: 'warning',
      title: '存在重复 NameServer 地址',
      description: unique(duplicateAddresses.map((address) => address.normalized)).join(', '),
    });
  }
  if (current.raw && !currentAddressKnown) {
    issues.push({
      id: 'current-missing',
      severity: 'critical',
      title: '当前使用地址不在候选列表中',
      description: `当前地址 ${input.currentNamesrv} 不在已配置列表内，更新前应先补齐或切换`,
    });
  }
  if (input.selectedNamesrv && !selectedAddressValid) {
    issues.push({
      id: 'selected-invalid',
      severity: 'critical',
      title: '选中的 NameServer 地址不可用',
      description: '请选择列表中格式有效的 NameServer 地址后再更新',
    });
  }
  if (newAddress.status === 'invalid') {
    issues.push({
      id: 'new-address-invalid',
      severity: 'warning',
      title: '待新增地址格式无效',
      description: newAddress.message,
    });
  }
  if (newAddress.status === 'duplicate') {
    issues.push({
      id: 'new-address-duplicate',
      severity: 'warning',
      title: '待新增地址已存在',
      description: newAddress.normalized,
    });
  }

  const pendingChanges: OpsNameServerPendingChange[] = [];
  if (
    selectedAddressValid &&
    current.valid &&
    selected.normalized &&
    selected.normalized !== current.normalized
  ) {
    pendingChanges.push({
      id: 'switch-nameserver',
      severity: 'info',
      title: '待切换当前 NameServer',
      description: `${current.normalized} -> ${selected.normalized}`,
    });
  }
  if (newAddress.status === 'ready') {
    pendingChanges.push({
      id: 'add-nameserver',
      severity: 'info',
      title: '待新增 NameServer',
      description: newAddress.normalized,
    });
  }
  if (input.useTLS) {
    pendingChanges.push({
      id: 'tls-enabled',
      severity: 'info',
      title: 'TLS 已启用',
      description: '请确认客户端、Broker 与 NameServer 端口均支持 TLS 握手',
    });
  }
  if (input.useVIPChannel) {
    pendingChanges.push({
      id: 'vip-enabled',
      severity: 'info',
      title: 'VIP 通道已启用',
      description: '请确认 Broker VIP 端口与防火墙策略已放通',
    });
  }

  const hasCritical = issues.some((issue) => issue.severity === 'critical');
  const hasWarning = issues.some((issue) => issue.severity === 'warning');
  const readiness: OpsNameServerReadiness = hasCritical
    ? 'blocked'
    : hasWarning
      ? 'warning'
      : 'ready';
  const recommendations = [
    ...(invalidAddresses.length > 0 ? ['修正或删除格式无效的 NameServer 地址'] : []),
    ...(duplicateAddresses.length > 0 ? ['删除重复地址，避免切换时误判可用性'] : []),
    ...(!currentAddressKnown && current.raw
      ? ['先把当前地址补回列表，或切换到列表中的有效地址']
      : []),
    ...(newAddress.status === 'ready' ? ['新增前确认该 endpoint 已在防火墙和 DNS 中可达'] : []),
    ...(!input.canWrite ? ['当前账号只读，预检结果仅供排查参考'] : []),
  ];
  if (recommendations.length === 0) {
    recommendations.push('当前 NameServer 配置可直接更新');
  }

  return {
    readiness,
    statusText:
      readiness === 'blocked'
        ? '需要先处理阻断项'
        : readiness === 'warning'
          ? '建议先处理风险项'
          : '配置预检通过',
    statusDescription:
      readiness === 'blocked'
        ? '存在会影响 Ops 写入或 NameServer 切换的阻断问题'
        : readiness === 'warning'
          ? '配置可继续操作，但建议先清理重复或格式问题'
          : '当前地址列表、选中地址和待新增地址均未发现阻断风险',
    addresses,
    issues,
    pendingChanges,
    recommendations,
    newAddress,
    stats: {
      configuredAddresses: addresses.length,
      validAddresses: validAddresses.length,
      invalidAddresses: invalidAddresses.length,
      duplicateAddresses: unique(duplicateAddresses.map((address) => address.normalized)).length,
      endpointCount: addresses.reduce((sum, address) => sum + address.endpoints.length, 0),
      validEndpointCount: addresses.reduce(
        (sum, address) => sum + address.endpoints.filter((endpoint) => endpoint.valid).length,
        0,
      ),
    },
    selectedAddressValid,
    currentAddressKnown,
  };
}
