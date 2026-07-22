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

import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import client from './client';
import {
  getLlmConfig,
  saveLlmConfig,
  testLlmConnection,
  getLlmModels,
  type LlmConfig,
} from './llm';

const mock = new MockAdapter(client);

const sampleConfig: LlmConfig = {
  provider: 'openai',
  apiKey: 'sk-test-key-1234',
  apiBase: 'https://api.openai.com/v1',
  model: 'gpt-4o',
  maxTokens: 4096,
  temperature: 0.7,
  enabled: true,
};

describe('LLM API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('fetches LLM config', async () => {
    mock.onGet('/llm/config').reply(200, sampleConfig);

    const result = await getLlmConfig();
    expect(result).toEqual(sampleConfig);
    expect(result.provider).toBe('openai');
    expect(result.enabled).toBe(true);
  });

  it('saves LLM config and returns success result', async () => {
    mock.onPost('/llm/config').reply((config) => {
      const body = JSON.parse(config.data);
      expect(body.provider).toBe('openai');
      expect(body.model).toBe('gpt-4o');
      return [200, { status: 0, msg: 'saved' }];
    });

    const result = await saveLlmConfig(sampleConfig);
    expect(result.status).toBe(0);
    expect(result.msg).toBe('saved');
  });

  it('tests LLM connection with success', async () => {
    mock.onPost('/llm/config/test').reply((config) => {
      const body = JSON.parse(config.data);
      expect(body.apiKey).toBe('sk-test-key-1234');
      return [200, { status: 0, msg: 'Connection successful' }];
    });

    const result = await testLlmConnection(sampleConfig);
    expect(result.status).toBe(0);
    expect(result.msg).toBe('Connection successful');
  });

  it('tests LLM connection with failure', async () => {
    mock.onPost('/llm/config/test').reply(200, {
      status: 1,
      errMsg: 'Invalid API Key',
    });

    const result = await testLlmConnection(sampleConfig);
    expect(result.status).toBe(1);
    expect(result.errMsg).toBe('Invalid API Key');
  });

  it('fetches available models', async () => {
    const models = [
      { id: 'gpt-4o', name: 'GPT-4o' },
      { id: 'gpt-4-turbo', name: 'GPT-4 Turbo' },
    ];
    mock.onGet('/llm/models').reply(200, { status: 0, data: models });

    const result = await getLlmModels();
    expect(result.status).toBe(0);
    expect(result.data).toHaveLength(2);
    expect(result.data![0].id).toBe('gpt-4o');
  });

  it('handles empty models list', async () => {
    mock.onGet('/llm/models').reply(200, { status: 0, data: [] });

    const result = await getLlmModels();
    expect(result.status).toBe(0);
    expect(result.data).toHaveLength(0);
  });

  it('saves config with Azure extra fields', async () => {
    const azureConfig: LlmConfig = {
      ...sampleConfig,
      provider: 'azure',
      deploymentName: 'my-gpt4',
      apiVersion: '2024-02-15-preview',
    };
    mock.onPost('/llm/config').reply((config) => {
      const body = JSON.parse(config.data);
      expect(body.provider).toBe('azure');
      expect(body.deploymentName).toBe('my-gpt4');
      expect(body.apiVersion).toBe('2024-02-15-preview');
      return [200, { status: 0 }];
    });

    await saveLlmConfig(azureConfig);
  });
});
