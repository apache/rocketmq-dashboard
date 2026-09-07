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

export interface LlmConfig {
  provider: string;
  engine?: string;
  apiKey?: string;
  clearApiKey?: boolean;
  apiKeyConfigured?: boolean;
  apiBase: string;
  model: string;
  maxTokens: number;
  temperature: number;
  enabled: boolean;
  ready?: boolean;
  deploymentName?: string;
  apiVersion?: string;
  awsRegion?: string;
}

export interface LlmTestResult {
  status: number;
  msg?: string;
  errMsg?: string;
  code?: string;
  hint?: string;
  models?: LlmModelItem[];
}

export interface LlmModelItem {
  id?: string;
  name?: string;
}

export interface LlmModelsResult {
  status: number;
  data?: LlmModelItem[];
  source?: 'provider' | 'builtin' | 'fallback';
  warning?: string;
  warningCode?: string;
  hint?: string;
}

export async function getLlmConfig(): Promise<LlmConfig> {
  const res = await client.get<{ data: LlmConfig }>('/llm/config');
  return res.data.data;
}

export async function saveLlmConfig(config: LlmConfig): Promise<LlmTestResult> {
  const res = await client.post<{ data: LlmTestResult }>('/llm/config', config);
  return res.data.data;
}

export async function testLlmConnection(config: LlmConfig): Promise<LlmTestResult> {
  const res = await client.post<{ data: LlmTestResult }>('/llm/config/test', config);
  return res.data.data;
}

export async function getLlmModels(): Promise<LlmModelsResult> {
  const res = await client.get<{ data: LlmModelsResult }>('/llm/models');
  return res.data.data;
}
