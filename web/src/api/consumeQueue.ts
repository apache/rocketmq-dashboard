/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import client from './client';

export interface ConsumeQueueQuery {
  instanceId: string;
  topic: string;
  brokerName: string;
  queueId: number;
  index: string;
  count: number;
  consumerGroup?: string;
}

export interface ConsumeQueueEntry {
  ordinal: number;
  physicalOffset: string;
  physicalSize: number;
  tagsCode: string;
  extension: string | null;
  bitmap: string | null;
  indexMatch: boolean | null;
  message: string | null;
}

export interface ConsumeQueueSnapshot {
  brokerAddress: string;
  minIndex: string;
  maxIndex: string;
  requestedIndex: string;
  count: number;
  atEnd: boolean;
  expressionType: string | null;
  expression: string | null;
  filterData: string | null;
  entries: ConsumeQueueEntry[];
}

export async function inspectConsumeQueue(params: ConsumeQueueQuery, signal?: AbortSignal) {
  const response = await client.get<{ data: ConsumeQueueSnapshot }>('/messages/consume-queue', {
    params,
    signal,
  });
  return response.data.data;
}
