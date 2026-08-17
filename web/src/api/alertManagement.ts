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

import client from './client';

export interface AlertRuleData {
  rules: string;
}

export interface AlertRule {
  id?: number;
  name: string;
  metric?: string;
  operator?: string;
  threshold?: number;
  thresholdUnit?: string;
  duration?: string;
  channels?: string[];
  enabled: boolean;
  description?: string;
  brokerName?: string;
  clusterName?: string;
  severity?: string;
  lastTriggered?: string | null;
}

export type AlertRuleRequest = Omit<AlertRule, 'lastTriggered'>;

export async function listAlertRules(): Promise<AlertRule[]> {
  const res = await client.get<{ data: AlertRule[] }>('/alert-rules');
  return res.data.data;
}

export async function exportAlertRulesYaml(): Promise<AlertRuleData> {
  const res = await client.get<{ data: AlertRuleData }>('/alert-rules/export');
  return res.data.data;
}

export async function queryAlertRules(): Promise<AlertRuleData> {
  return exportAlertRulesYaml();
}

export async function createAlertRule(data: AlertRuleRequest): Promise<AlertRule> {
  const res = await client.post<{ data: AlertRule }>('/alert-rules/create', data);
  return res.data.data;
}

export async function updateAlertRule(data: AlertRuleRequest): Promise<AlertRule> {
  const res = await client.post<{ data: AlertRule }>('/alert-rules/update', data);
  return res.data.data;
}

export async function toggleAlertRule(id: number, enabled: boolean): Promise<AlertRule> {
  const res = await client.post<{ data: AlertRule }>('/alert-rules/toggle', { id, enabled });
  return res.data.data;
}

export async function deleteAlertRule(id: number): Promise<void> {
  await client.post('/alert-rules/delete', { id });
}
