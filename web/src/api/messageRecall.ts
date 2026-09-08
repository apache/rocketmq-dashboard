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

export interface RecallRequest {
  instanceId: string;
  topic: string;
  recallHandle: string;
}

export interface RecallTarget {
  topic: string;
  brokerName: string;
  messageId: string;
  deliveryTimestamp: number;
}

export interface RecallReceipt {
  messageId: string;
  acceptedAt: number;
}

export async function previewMessageRecall(request: RecallRequest) {
  const response = await client.post<{ data: RecallTarget }>('/messages/recall/preview', request);
  return response.data.data;
}

export async function recallDelayMessage(request: RecallRequest) {
  const response = await client.post<{ data: RecallReceipt }>('/messages/recall', request);
  return response.data.data;
}
