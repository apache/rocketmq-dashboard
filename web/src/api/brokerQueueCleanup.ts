/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import client from './client';
export type QueueCleanupOperation = 'UNUSED_TOPIC_QUEUES' | 'EXPIRED_CONSUME_QUEUES';
export interface QueueCleanupTarget {
  instanceId: string;
  brokerName: string;
  address: string;
}
export interface QueueCleanupPreview {
  brokerName: string;
  address: string;
  sampledAt: string;
  configuredTopics: string[];
}
export interface QueueCleanupReceipt {
  operation: QueueCleanupOperation;
  brokerName: string;
  address: string;
  status: 'BROKER_REPORTED_COMPLETION' | 'UNKNOWN';
  finishedAt: string;
}
export async function previewBrokerQueueCleanup(params: QueueCleanupTarget, signal?: AbortSignal) {
  const response = await client.get<{ data: QueueCleanupPreview }>('/brokers/queue-cleanup', {
    params,
    signal,
  });
  return response.data.data;
}
export async function applyBrokerQueueCleanup(
  target: QueueCleanupTarget,
  operation: QueueCleanupOperation,
  expectedConfiguredTopics: string[],
  confirmation: string,
  signal?: AbortSignal,
) {
  const response = await client.post<{ data: QueueCleanupReceipt }>(
    '/brokers/queue-cleanup',
    { ...target, operation, expectedConfiguredTopics, confirmation },
    { signal, timeout: 0 },
  );
  return response.data.data;
}
