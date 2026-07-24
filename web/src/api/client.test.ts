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
import type { AxiosAdapter, AxiosInstance } from 'axios';
import { message } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { persistAuthSession, readAuthSession } from '../stores/authStorage';
import client, { createApiClient } from './client';

vi.mock('antd', () => ({
  message: {
    error: vi.fn(),
  },
}));

const mock = new MockAdapter(client);
const storage = new Map<string, string>();

vi.stubGlobal('localStorage', {
  getItem: (key: string) => storage.get(key) ?? null,
  setItem: (key: string, value: string) => storage.set(key, value),
  removeItem: (key: string) => storage.delete(key),
  clear: () => storage.clear(),
  key: (index: number) => [...storage.keys()][index] ?? null,
  get length() {
    return storage.size;
  },
});

function captureAdapterRejection(apiClient: AxiosInstance): () => unknown {
  const adapter = apiClient.defaults.adapter;
  if (typeof adapter !== 'function') {
    throw new Error('Expected a function adapter');
  }

  let capturedError: unknown;
  apiClient.defaults.adapter = async (config) => {
    try {
      return await (adapter as AxiosAdapter)(config);
    } catch (error) {
      capturedError = error;
      throw error;
    }
  };
  return () => capturedError;
}

describe('API client response contract', () => {
  beforeEach(() => {
    mock.reset();
    localStorage.clear();
    vi.mocked(message.error).mockClear();
  });

  afterEach(() => {
    mock.reset();
  });

  it('accepts the backend Result.ok response code', async () => {
    const payload = { code: 200, message: 'success', data: { name: 'cluster-a' } };
    mock.onGet('/clusters').reply(200, payload);

    const response = await client.get('/clusters');

    expect(response.data).toEqual(payload);
    expect(message.error).not.toHaveBeenCalled();
  });

  it('keeps compatibility with legacy zero success codes', async () => {
    const payload = { code: 0, message: 'success', data: ['topic-a'] };
    mock.onGet('/topics').reply(200, payload);

    await expect(client.get('/topics')).resolves.toMatchObject({ data: payload });
    expect(message.error).not.toHaveBeenCalled();
  });

  it('passes through responses without a business envelope', async () => {
    const payload = [{ id: 'broker-a' }];
    mock.onGet('/brokers').reply(200, payload);

    await expect(client.get('/brokers')).resolves.toMatchObject({ data: payload });
    expect(message.error).not.toHaveBeenCalled();
  });

  it('rejects non-success business codes with the backend message', async () => {
    mock.onPost('/topics/create').reply(200, {
      code: 400,
      message: 'Topic already exists',
      data: null,
    });

    await expect(client.post('/topics/create', { name: 'orders' })).rejects.toThrow(
      'Topic already exists',
    );
    expect(message.error).toHaveBeenCalledWith('Topic already exists');
  });

  it('uses a stable fallback for malformed error envelopes', async () => {
    mock.onGet('/clusters').reply(200, { code: '500', data: null });

    await expect(client.get('/clusters')).rejects.toThrow('请求失败');
    expect(message.error).toHaveBeenCalledWith('请求失败');
  });

  it('attaches the stored bearer token to outgoing requests', async () => {
    localStorage.setItem('token', 'test-token');
    mock.onGet('/clusters').reply((config) => {
      expect(config.headers?.Authorization).toBe('Bearer test-token');
      return [200, { code: 200, message: 'success', data: [] }];
    });

    await client.get('/clusters');
  });

  it('clears the session and redirects once for an HTTP 401 response', async () => {
    const token = 'secret-expired-token';
    persistAuthSession(token, 'studio-admin');
    const navigate = vi.fn();
    const unauthorizedClient = createApiClient(navigate);
    const unauthorizedMock = new MockAdapter(unauthorizedClient);
    unauthorizedMock.onGet('/protected').reply(401);
    const getAdapterError = captureAdapterRejection(unauthorizedClient);

    const rejectedError = (await unauthorizedClient
      .get('/protected')
      .catch((error: unknown) => error)) as Error & { toJSON?: () => unknown };

    expect(rejectedError).toBe(getAdapterError());
    expect(rejectedError).toMatchObject({ response: { status: 401 } });
    expect(JSON.stringify(rejectedError.toJSON?.() ?? rejectedError)).not.toContain(token);
    expect(`${rejectedError.message}\n${rejectedError.stack ?? ''}`).not.toContain(token);
    expect(readAuthSession()).toEqual({ token: null, user: null });
    expect(navigate).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith('/');
    unauthorizedMock.restore();
  });

  it('preserves the original non-401 error without clearing or redirecting', async () => {
    const token = 'secret-valid-token';
    persistAuthSession(token, 'studio-admin');
    const navigate = vi.fn();
    const nonUnauthorizedClient = createApiClient(navigate);
    const nonUnauthorizedMock = new MockAdapter(nonUnauthorizedClient);
    nonUnauthorizedMock.onGet('/protected').reply(500);
    const getAdapterError = captureAdapterRejection(nonUnauthorizedClient);

    const rejectedError = (await nonUnauthorizedClient
      .get('/protected', { headers: { 'X-Trace-Id': 'trace-1' } })
      .catch((error: unknown) => error)) as Error & { toJSON?: () => unknown };

    expect(rejectedError).toBe(getAdapterError());
    expect(rejectedError).toMatchObject({ response: { status: 500 } });
    const serializedError = JSON.stringify(rejectedError.toJSON?.() ?? rejectedError);
    expect(serializedError).not.toContain(token);
    expect(serializedError).toContain('trace-1');
    expect(`${rejectedError.message}\n${rejectedError.stack ?? ''}`).not.toContain(token);
    expect(readAuthSession()).toEqual({ token, user: 'studio-admin' });
    expect(navigate).not.toHaveBeenCalled();
    nonUnauthorizedMock.restore();
  });

  it('redacts case-insensitive authorization headers from request interceptor errors', async () => {
    const token = 'secret-request-token';
    const requestClient = createApiClient();
    requestClient.interceptors.response.eject(0);
    const originalError = Object.assign(new Error('request rejected'), {
      config: {
        headers: {
          authorization: `Bearer ${token}`,
          AUTHORIZATION: `Bearer ${token}`,
          'X-Trace-Id': 'trace-2',
        },
      },
    }) as Error & {
      config: { headers: Record<string, string> };
      toJSON?: () => unknown;
    };
    originalError.toJSON = () => ({
      message: originalError.message,
      stack: originalError.stack,
      config: originalError.config,
    });
    requestClient.interceptors.request.use(() => Promise.reject(originalError));

    const rejectedError = await requestClient.get('/protected').catch((error: unknown) => error);

    expect(rejectedError).toBe(originalError);
    const serializedError = JSON.stringify(originalError.toJSON?.() ?? originalError);
    expect(serializedError).not.toContain(token);
    expect(serializedError).toContain('trace-2');
  });
});
