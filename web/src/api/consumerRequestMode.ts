/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import client from './client';

export type RequestMode = 'POP' | 'PULL';
export interface RequestModeSelection {
  instanceId: string;
  topic: string;
  group: string;
}
export interface BrokerRequestMode {
  brokerName: string;
  address: string;
  mode: RequestMode;
  popShareQueueNum: number;
  explicit: boolean;
  serverLoadBalancerEnable: string;
}
export interface RequestModePreview {
  topic: string;
  group: string;
  brokers: BrokerRequestMode[];
}
export interface RequestModeReceipt {
  brokers: {
    before: BrokerRequestMode;
    status: 'CONFIRMED' | 'UNCHANGED' | 'UNKNOWN' | 'NOT_ATTEMPTED';
    observed: BrokerRequestMode | null;
  }[];
}
export async function previewConsumerRequestMode(
  params: RequestModeSelection,
  signal?: AbortSignal,
) {
  const response = await client.get<{ data: RequestModePreview }>('/consumer-request-mode', {
    params,
    signal,
  });
  return response.data.data;
}
export async function applyConsumerRequestMode(
  selection: RequestModeSelection,
  mode: RequestMode,
  popShareQueueNum: number,
  expected: BrokerRequestMode[],
  signal?: AbortSignal,
) {
  const response = await client.post<{ data: RequestModeReceipt }>(
    '/consumer-request-mode',
    { ...selection, mode, popShareQueueNum, expected },
    { signal, timeout: 0 },
  );
  return response.data.data;
}
