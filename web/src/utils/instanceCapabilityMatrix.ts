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

import type {
  Instance,
  InstanceCapabilities,
  InstanceCapability,
  InstanceType,
  InstanceVendor,
} from '../api/instance';

export const INSTANCE_CAPABILITIES: readonly InstanceCapability[] = [
  'TOPIC_MANAGEMENT',
  'CONSUMER_GROUP_MANAGEMENT',
  'MESSAGE_QUERY',
  'MESSAGE_TRACE',
  'ACL_MANAGEMENT',
  'DLQ_MANAGEMENT',
];

export type CapabilityLoadStatus = 'AVAILABLE' | 'FAILED';

export interface InstanceCapabilityMatrixRow {
  key: string;
  instanceId: string;
  vendor: InstanceVendor;
  accessType: InstanceType;
  endpoint: string;
  status: CapabilityLoadStatus;
  capabilities: InstanceCapability[];
  supportedCount: number;
  missingCapabilities: InstanceCapability[];
  error: string;
}

export interface InstanceCapabilityCoverage {
  capability: InstanceCapability;
  supported: number;
  loaded: number;
  percent: number;
}

export interface InstanceCapabilityMatrixSummary {
  requested: number;
  loaded: number;
  failed: number;
  fullCoverage: number;
  limited: number;
  coverage: InstanceCapabilityCoverage[];
}

export interface InstanceCapabilityMatrix {
  rows: InstanceCapabilityMatrixRow[];
  summary: InstanceCapabilityMatrixSummary;
}

export interface InstanceCapabilityMatrixFilters {
  search: string;
  vendor: InstanceVendor | 'ALL';
  accessType: InstanceType | 'ALL';
  capability: InstanceCapability | 'ALL';
  support: 'ALL' | 'SUPPORTED' | 'MISSING';
  status: CapabilityLoadStatus | 'ALL';
}

export interface CapabilityLoadResult {
  instance: Instance;
  value?: InstanceCapabilities;
  error?: string;
}

const normalizedVendor = (instance: Instance): InstanceVendor => instance.vendor ?? 'APACHE';

const normalizeCapabilities = (values: InstanceCapability[]): InstanceCapability[] => {
  const received = new Set(values);
  return INSTANCE_CAPABILITIES.filter((capability) => received.has(capability));
};

const buildRow = (result: CapabilityLoadResult): InstanceCapabilityMatrixRow => {
  const capabilities = result.value ? normalizeCapabilities(result.value.capabilities) : [];
  const supported = new Set(capabilities);
  return {
    key: result.instance.name,
    instanceId: result.instance.name,
    vendor: result.value?.vendor ?? normalizedVendor(result.instance),
    accessType: result.value?.accessType ?? result.instance.type,
    endpoint: result.instance.endpoint,
    status: result.value ? 'AVAILABLE' : 'FAILED',
    capabilities,
    supportedCount: capabilities.length,
    missingCapabilities: INSTANCE_CAPABILITIES.filter((capability) => !supported.has(capability)),
    error: result.error ?? '',
  };
};

export const buildInstanceCapabilityMatrix = (
  results: CapabilityLoadResult[],
): InstanceCapabilityMatrix => {
  const rows = results.map(buildRow).sort((left, right) => {
    if (left.status !== right.status) return left.status === 'AVAILABLE' ? -1 : 1;
    const byVendor = left.vendor.localeCompare(right.vendor);
    return byVendor !== 0 ? byVendor : left.instanceId.localeCompare(right.instanceId);
  });
  const loadedRows = rows.filter((row) => row.status === 'AVAILABLE');
  const coverage = INSTANCE_CAPABILITIES.map((capability) => {
    const supported = loadedRows.filter((row) => row.capabilities.includes(capability)).length;
    return {
      capability,
      supported,
      loaded: loadedRows.length,
      percent: loadedRows.length === 0 ? 0 : Math.round((supported / loadedRows.length) * 100),
    };
  });
  return {
    rows,
    summary: {
      requested: rows.length,
      loaded: loadedRows.length,
      failed: rows.length - loadedRows.length,
      fullCoverage: loadedRows.filter((row) => row.supportedCount === INSTANCE_CAPABILITIES.length)
        .length,
      limited: loadedRows.filter((row) => row.supportedCount < INSTANCE_CAPABILITIES.length).length,
      coverage,
    },
  };
};

export const filterInstanceCapabilityRows = (
  rows: InstanceCapabilityMatrixRow[],
  filters: InstanceCapabilityMatrixFilters,
): InstanceCapabilityMatrixRow[] => {
  const search = filters.search.trim().toLocaleLowerCase();
  return rows.filter((row) => {
    if (filters.vendor !== 'ALL' && row.vendor !== filters.vendor) return false;
    if (filters.accessType !== 'ALL' && row.accessType !== filters.accessType) return false;
    if (filters.status !== 'ALL' && row.status !== filters.status) return false;
    if (filters.capability !== 'ALL') {
      const supported = row.capabilities.includes(filters.capability);
      if (filters.support === 'SUPPORTED' && !supported) return false;
      if (filters.support === 'MISSING' && (supported || row.status === 'FAILED')) return false;
    }
    return (
      !search ||
      [row.instanceId, row.endpoint, row.vendor, row.accessType, row.error].some((value) =>
        value.toLocaleLowerCase().includes(search),
      )
    );
  });
};

export const summarizeVisibleCapabilityRows = (rows: InstanceCapabilityMatrixRow[]) => ({
  instances: rows.length,
  loaded: rows.filter((row) => row.status === 'AVAILABLE').length,
  failed: rows.filter((row) => row.status === 'FAILED').length,
  limited: rows.filter(
    (row) => row.status === 'AVAILABLE' && row.supportedCount < INSTANCE_CAPABILITIES.length,
  ).length,
});

export const describeCapabilityGaps = (row: InstanceCapabilityMatrixRow): string => {
  if (row.status === 'FAILED') return row.error;
  if (row.missingCapabilities.length === 0) return '';
  return row.missingCapabilities.join(';');
};
