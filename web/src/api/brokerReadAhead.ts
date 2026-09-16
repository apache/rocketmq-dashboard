/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import client from './client';

export interface ReadAheadTarget {
  instanceId: string;
  brokerName: string;
  address: string;
}
export interface ReadAheadSnapshot {
  brokerName: string;
  address: string;
  enabled: boolean;
  sampledAt: string;
}
export interface ReadAheadReceipt {
  status: 'UNCHANGED' | 'CONFIG_CONFIRMED' | 'UNKNOWN';
  acknowledged: boolean;
  before: ReadAheadSnapshot;
  observed: ReadAheadSnapshot | null;
}
export async function inspectBrokerReadAhead(params: ReadAheadTarget, signal?: AbortSignal) {
  const response = await client.get<{ data: ReadAheadSnapshot }>('/brokers/read-ahead', {
    params,
    signal,
  });
  return response.data.data;
}
export async function updateBrokerReadAhead(
  target: ReadAheadTarget,
  expectedEnabled: boolean,
  enabled: boolean,
  signal?: AbortSignal,
) {
  const response = await client.post<{ data: ReadAheadReceipt }>(
    '/brokers/read-ahead',
    { ...target, expectedEnabled, enabled },
    { signal, timeout: 0 },
  );
  return response.data.data;
}
