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

export interface TransactionRecoveryCommand {
  instanceId: string;
  offsetMessageId: string;
}

export interface TransactionRecoveryPreview {
  offsetMessageId: string;
  brokerAddress: string;
  originalTopic: string;
  producerGroup: string;
  checkTimes: string | null;
  transactionId: string | null;
  storedAt: number;
}

export async function inspectTransactionRecovery(command: TransactionRecoveryCommand) {
  const response = await client.post<{ data: TransactionRecoveryPreview }>(
    '/messages/transaction-recovery/preview',
    command,
  );
  return response.data.data;
}

export async function recoverTransactionCheck(command: TransactionRecoveryCommand) {
  const response = await client.post<{ data: TransactionRecoveryPreview }>(
    '/messages/transaction-recovery',
    command,
  );
  return response.data.data;
}
