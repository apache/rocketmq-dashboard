/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import client from './client';

export interface QueueOffsetTarget {
  instanceId: string;
  group: string;
  topic: string;
  brokerName: string;
  queueId: number;
}

export interface QueueOffsetPreview {
  brokerAddress: string;
  currentOffset: string;
  targetOffset: string;
  minOffset: string;
  maxOffset: string;
  offsetDelta: string;
  projectedLag: string;
}

export async function previewQueueOffset(target: QueueOffsetTarget, offset: string) {
  const response = await client.post<{ data: QueueOffsetPreview }>('/groups/queue-offset/preview', {
    ...target,
    offset,
  });
  return response.data.data;
}

export async function applyQueueOffset(
  target: QueueOffsetTarget,
  offset: string,
  expectedCurrentOffset: string,
) {
  const response = await client.post<{ data: QueueOffsetPreview }>('/groups/queue-offset', {
    ...target,
    offset,
    expectedCurrentOffset,
  });
  return response.data.data;
}
