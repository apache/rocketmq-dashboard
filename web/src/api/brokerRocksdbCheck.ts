/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import client from './client';
export interface RocksdbCheckTarget {
  instanceId: string;
  brokerName: string;
  address: string;
}
export interface RocksdbSettings {
  doubleWriteEnabled: boolean;
  loadingStores: string[];
}
export interface RocksdbCheckPreview {
  brokerName: string;
  address: string;
  sampledAt: string;
  settings: RocksdbSettings;
  eligible: boolean;
  topics: string[];
}
export interface RocksdbCheckReceipt {
  brokerName: string;
  address: string;
  topic: string;
  checkFromMillis: string;
  status: 'ACCEPTED' | 'UNKNOWN' | 'BROKER_RESPONSE';
  brokerStatus: number | null;
  brokerRemark: string | null;
  submittedAt: string;
  receivedAt: string;
}
export async function previewRocksdbCheck(params: RocksdbCheckTarget, signal?: AbortSignal) {
  const response = await client.get<{ data: RocksdbCheckPreview }>('/brokers/rocksdb-check', {
    params,
    signal,
  });
  return response.data.data;
}
export async function submitRocksdbCheck(
  target: RocksdbCheckTarget,
  topic: string,
  checkFromMillis: string,
  expectedSettings: RocksdbSettings,
  signal?: AbortSignal,
) {
  const response = await client.post<{ data: RocksdbCheckReceipt }>(
    '/brokers/rocksdb-check',
    { ...target, topic, checkFromMillis, expectedSettings, confirmed: true },
    { signal, timeout: 0 },
  );
  return response.data.data;
}
