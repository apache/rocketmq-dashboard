/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

export interface DLQGroup {
  groupName: string;
  dlqTopic: string;
  messageCount: number;
  lastEnqueueTime: string;
  retryCount: number;
  status: 'active' | 'empty';
}

export const mockDLQGroups: DLQGroup[] = [
  {
    groupName: 'cg-order-processor',
    dlqTopic: '%DLQ%cg-order-processor',
    messageCount: 23,
    lastEnqueueTime: '2026-07-01T14:32:18.000Z',
    retryCount: 16,
    status: 'active',
  },
  {
    groupName: 'cg-payment-handler',
    dlqTopic: '%DLQ%cg-payment-handler',
    messageCount: 8,
    lastEnqueueTime: '2026-07-01T15:10:45.000Z',
    retryCount: 16,
    status: 'active',
  },
  {
    groupName: 'cg-notification',
    dlqTopic: '%DLQ%cg-notification',
    messageCount: 156,
    lastEnqueueTime: '2026-07-01T16:05:22.000Z',
    retryCount: 16,
    status: 'active',
  },
  {
    groupName: 'cg-inventory-sync',
    dlqTopic: '%DLQ%cg-inventory-sync',
    messageCount: 3,
    lastEnqueueTime: '2026-07-01T12:48:33.000Z',
    retryCount: 16,
    status: 'active',
  },
  {
    groupName: 'cg-analytics',
    dlqTopic: '%DLQ%cg-analytics',
    messageCount: 42,
    lastEnqueueTime: '2026-07-01T15:55:10.000Z',
    retryCount: 16,
    status: 'active',
  },
  {
    groupName: 'cg-user-tracking',
    dlqTopic: '%DLQ%cg-user-tracking',
    messageCount: 0,
    lastEnqueueTime: '2026-06-28T09:12:00.000Z',
    retryCount: 16,
    status: 'empty',
  },
  {
    groupName: 'cg-sms-gateway',
    dlqTopic: '%DLQ%cg-sms-gateway',
    messageCount: 67,
    lastEnqueueTime: '2026-07-01T16:20:55.000Z',
    retryCount: 16,
    status: 'active',
  },
  {
    groupName: 'cg-email-service',
    dlqTopic: '%DLQ%cg-email-service',
    messageCount: 0,
    lastEnqueueTime: '2026-06-30T18:05:12.000Z',
    retryCount: 16,
    status: 'empty',
  },
];
