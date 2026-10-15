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

import type { NotificationDeliveryRecord } from '../../api/ops';

export type NotificationDeliveryStatus = NotificationDeliveryRecord['status'];

/** Tag colour per outbox status, shared by the delivery list, its drawer and the alert timeline. */
export const deliveryStatusColors: Record<NotificationDeliveryStatus, string> = {
  PENDING: 'default',
  SENDING: 'processing',
  DELIVERED: 'success',
  RETRY_WAIT: 'warning',
  FAILED: 'error',
};

/** Channels the outbox delivers to, in the order the console offers them. */
export const deliveryChannels = ['dingtalk', 'email', 'sms'];

const statusLabelKeys: Record<string, string> = {
  PENDING: 'deliveries.status.PENDING',
  SENDING: 'deliveries.status.SENDING',
  DELIVERED: 'deliveries.status.DELIVERED',
  RETRY_WAIT: 'deliveries.status.RETRY_WAIT',
  FAILED: 'deliveries.status.FAILED',
};

const channelLabelKeys: Record<string, string> = {
  dingtalk: 'deliveries.channel.DINGTALK',
  email: 'deliveries.channel.EMAIL',
  sms: 'deliveries.channel.SMS',
};

/**
 * The API describes a delivery with wire codes (`RETRY_WAIT`, `dingtalk`), and an operator reading
 * the console has no way to tell `RETRY_WAIT` from `PENDING`. Every surface that presents a
 * delivery resolves the code through the dictionary; a code without a label is returned unchanged
 * rather than hidden, so a new backend status stays visible until it gets a translation.
 */
export function formatDeliveryStatus(status: string, translate: (key: string) => string): string {
  const key = statusLabelKeys[status];
  return key ? translate(key) : status;
}

export function formatDeliveryChannel(channel: string, translate: (key: string) => string): string {
  const key = channelLabelKeys[channel];
  return key ? translate(key) : channel;
}
