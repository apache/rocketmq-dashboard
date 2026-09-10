/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import client from './client';
export interface StaticMappingTarget {
  instanceId: string;
  topic: string;
}
export interface MappingSegment {
  generation: number;
  brokerName: string;
  physicalQueueId: number;
  logicalStart: string;
  physicalStart: string;
  physicalEndExclusive: string;
}
export interface QueueHistory {
  logicalQueueId: number;
  lastMappedBroker: string;
  segments: MappingSegment[];
}
export interface MappingNode {
  brokerName: string;
  address: string | null;
  status: 'MAPPING' | 'NO_MAPPING' | 'UNAVAILABLE';
  error: string | null;
  advertised: {
    epoch: string;
    scope: string;
    totalQueues: number;
    currentQueues: { logicalQueueId: number; physicalQueueId: number }[];
  } | null;
  local: {
    epoch: string;
    scope: string;
    totalQueues: number;
    dirty: boolean;
    queues: QueueHistory[];
  } | null;
}
export interface StaticMappingSnapshot {
  topic: string;
  startedAt: string;
  finishedAt: string;
  partial: boolean;
  nodes: MappingNode[];
}
export async function inspectStaticTopicMapping(params: StaticMappingTarget, signal?: AbortSignal) {
  const response = await client.get<{ data: StaticMappingSnapshot }>('/static-topic-mappings', {
    params,
    signal,
  });
  return response.data.data;
}
