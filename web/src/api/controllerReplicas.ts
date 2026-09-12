/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import client from './client';

export interface ControllerReplicaSnapshot {
  brokerName: string;
  configSource: string;
  mode: 'ENABLED' | 'DISABLED' | 'UNKNOWN';
  sampledAt: string;
  agreement: 'MATCHING' | 'DIVERGENT' | 'PARTIAL' | 'UNAVAILABLE';
  controllers: {
    address: string;
    group: string | null;
    leaderId: string | null;
    leaderAddress: string | null;
    leader: boolean | null;
    peers: string | null;
    error: string | null;
  }[];
  membership: {
    discoveryAddress: string;
    masterBrokerId: string | null;
    masterAddress: string | null;
    masterEpoch: number;
    syncStateSetEpoch: number;
    replicas: {
      brokerId: string;
      address: string | null;
      inSyncSet: boolean;
      alive: boolean | null;
    }[];
  } | null;
  membershipError: string | null;
}
export async function inspectControllerReplicas(
  instanceId: string,
  brokerName: string,
  signal?: AbortSignal,
) {
  const response = await client.get<{ data: ControllerReplicaSnapshot }>(
    '/brokers/controller-replicas',
    {
      params: { instanceId, brokerName },
      signal,
      timeout: 60000,
    },
  );
  return response.data.data;
}
