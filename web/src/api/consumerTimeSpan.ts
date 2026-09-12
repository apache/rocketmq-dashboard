/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import client from './client';

export type CursorState =
  | 'RECORDED_OFFSET_REFERENCE'
  | 'EARLIEST_MESSAGE_FALLBACK'
  | 'OUTSIDE_RETAINED_RANGE'
  | 'OFFSET_UNAVAILABLE'
  | 'TIMESTAMP_UNAVAILABLE';

export interface ConsumerQueueTimeSpan {
  brokerName: string;
  queueId: number;
  minOffset: string;
  maxOffset: string;
  consumerOffset: string | null;
  earliestTime: string | null;
  latestTime: string | null;
  cursorTime: string | null;
  cursorState: CursorState;
  spanAvailable: boolean;
}
export interface ConsumerTimeSpanSnapshot {
  topic: string;
  group: string;
  sampledAt: string;
  queues: ConsumerQueueTimeSpan[];
}
export async function inspectConsumerTimeSpan(
  params: {
    instanceId: string;
    topic: string;
    group: string;
  },
  signal?: AbortSignal,
) {
  const response = await client.get<{ data: ConsumerTimeSpanSnapshot }>('/consumer-time-spans', {
    params,
    signal,
  });
  return response.data.data;
}
