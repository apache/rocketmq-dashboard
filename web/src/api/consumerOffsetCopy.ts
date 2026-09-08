/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import client from './client';

export interface CopySelection {
  instanceId: string;
  topic: string;
  sourceGroup: string;
  targetGroup: string;
}
export interface CopyExpected {
  brokerName: string;
  brokerAddr: string;
  queueId: number;
  sourceOffset: string;
  targetOffset: string | null;
}
export interface CopyPreview {
  topic: string;
  sourceGroup: string;
  targetGroup: string;
  queues: { expected: CopyExpected; minOffset: string; maxOffset: string }[];
}
export interface CopyReceipt {
  queues: {
    queue: CopyExpected;
    status: 'CONFIRMED' | 'UNCHANGED' | 'UNKNOWN' | 'NOT_ATTEMPTED';
    observedOffset: string | null;
  }[];
}
export async function previewConsumerOffsetCopy(params: CopySelection, signal?: AbortSignal) {
  const response = await client.get<{ data: CopyPreview }>('/consumer-offset-copy', {
    params,
    signal,
  });
  return response.data.data;
}
export async function applyConsumerOffsetCopy(
  selection: CopySelection,
  expected: CopyExpected[],
  signal?: AbortSignal,
) {
  const response = await client.post<{ data: CopyReceipt }>(
    '/consumer-offset-copy',
    { ...selection, expected },
    { signal, timeout: 0 },
  );
  return response.data.data;
}
