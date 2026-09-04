/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import type { CloudCredential } from '../../api/cloudCredential';
import type { Instance, InstanceVendor } from '../../api/instance';

export type CredentialUsageStatus = 'USED' | 'UNUSED' | 'VENDOR_MISMATCH';

export interface CredentialUsageRow {
  credentialId: number;
  credentialName: string;
  vendor: InstanceVendor;
  status: CredentialUsageStatus;
  usageCount: number;
  instanceNames: string[];
  regions: string[];
  mismatchedInstances: string[];
  createdAt: string;
}

export interface OrphanCredentialReference {
  instanceId: number;
  instanceName: string;
  instanceVendor: InstanceVendor;
  credentialId: number;
  region: string;
}

export interface CredentialUsageReport {
  rows: CredentialUsageRow[];
  orphanReferences: OrphanCredentialReference[];
  summary: {
    credentials: number;
    used: number;
    unused: number;
    mismatched: number;
    orphanReferences: number;
    coveredInstances: number;
  };
}

export interface CredentialUsageFilters {
  vendor?: InstanceVendor;
  status?: CredentialUsageStatus;
  search?: string;
}

const normalizeVendor = (vendor?: InstanceVendor): InstanceVendor => vendor ?? 'APACHE';

const distinctSorted = (values: Array<string | undefined>) =>
  [...new Set(values.filter((value): value is string => Boolean(value?.trim())).map(String))].sort(
    (left, right) => left.localeCompare(right),
  );

/**
 * Builds a deterministic report from already-masked credential metadata and instance metadata.
 * SecretKey and AccessKey values are deliberately absent from the result contract so callers
 * cannot accidentally expose them in tables or exports.
 */
export const buildCredentialUsageReport = (
  credentials: CloudCredential[],
  instances: Instance[],
): CredentialUsageReport => {
  const credentialById = new Map(credentials.map((credential) => [credential.id, credential]));
  const instancesByCredential = new Map<number, Instance[]>();
  const orphanReferences: OrphanCredentialReference[] = [];

  for (const instance of instances) {
    if (instance.credentialId === undefined || instance.credentialId === null) continue;

    const credential = credentialById.get(instance.credentialId);
    if (!credential) {
      orphanReferences.push({
        instanceId: instance.id,
        instanceName: instance.name,
        instanceVendor: normalizeVendor(instance.vendor),
        credentialId: instance.credentialId,
        region: instance.regionName || instance.regionId || '-',
      });
      continue;
    }

    const current = instancesByCredential.get(credential.id) ?? [];
    current.push(instance);
    instancesByCredential.set(credential.id, current);
  }

  const rows = credentials
    .map<CredentialUsageRow>((credential) => {
      const linkedInstances = instancesByCredential.get(credential.id) ?? [];
      const mismatchedInstances = linkedInstances
        .filter((instance) => normalizeVendor(instance.vendor) !== credential.vendor)
        .map((instance) => instance.name)
        .sort((left, right) => left.localeCompare(right));
      const status: CredentialUsageStatus =
        mismatchedInstances.length > 0
          ? 'VENDOR_MISMATCH'
          : linkedInstances.length > 0
            ? 'USED'
            : 'UNUSED';

      return {
        credentialId: credential.id,
        credentialName: credential.name,
        vendor: credential.vendor,
        status,
        usageCount: linkedInstances.length,
        instanceNames: distinctSorted(linkedInstances.map((instance) => instance.name)),
        regions: distinctSorted(
          linkedInstances.map((instance) => instance.regionName || instance.regionId),
        ),
        mismatchedInstances,
        createdAt: credential.gmtCreate,
      };
    })
    .sort((left, right) => {
      const riskOrder: Record<CredentialUsageStatus, number> = {
        VENDOR_MISMATCH: 0,
        UNUSED: 1,
        USED: 2,
      };
      return (
        riskOrder[left.status] - riskOrder[right.status] ||
        left.credentialName.localeCompare(right.credentialName)
      );
    });

  return {
    rows,
    orphanReferences: orphanReferences.sort(
      (left, right) =>
        left.credentialId - right.credentialId ||
        left.instanceName.localeCompare(right.instanceName),
    ),
    summary: {
      credentials: rows.length,
      used: rows.filter((row) => row.usageCount > 0).length,
      unused: rows.filter((row) => row.status === 'UNUSED').length,
      mismatched: rows.filter((row) => row.status === 'VENDOR_MISMATCH').length,
      orphanReferences: orphanReferences.length,
      coveredInstances: rows.reduce((total, row) => total + row.usageCount, 0),
    },
  };
};

export const filterCredentialUsageRows = (
  rows: CredentialUsageRow[],
  filters: CredentialUsageFilters,
) => {
  const search = filters.search?.trim().toLocaleLowerCase();
  return rows.filter((row) => {
    if (filters.vendor && row.vendor !== filters.vendor) return false;
    if (filters.status && row.status !== filters.status) return false;
    if (!search) return true;
    return [row.credentialName, ...row.instanceNames, ...row.regions]
      .join('\n')
      .toLocaleLowerCase()
      .includes(search);
  });
};

export const credentialUsageCsvRows = (rows: CredentialUsageRow[]) =>
  rows.map((row) => ({
    credentialId: row.credentialId,
    credentialName: row.credentialName,
    vendor: row.vendor,
    status: row.status,
    usageCount: row.usageCount,
    instances: row.instanceNames.join('; '),
    regions: row.regions.join('; '),
    mismatchedInstances: row.mismatchedInstances.join('; '),
    createdAt: row.createdAt,
  }));
