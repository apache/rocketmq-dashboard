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
import axios, {
  AxiosError,
  AxiosHeaders,
  type AxiosAdapter,
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios';
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

function expectFixedSafeAxiosError(
  rejectedError: AxiosError,
  originalError: unknown,
  status: number | undefined,
  token: string,
): void {
  expect(rejectedError).not.toBe(originalError);
  expect(axios.isAxiosError(rejectedError)).toBe(true);
  expect(rejectedError).toMatchObject({
    name: 'AxiosError',
    message: status === undefined ? 'Request failed' : `Request failed with status code ${status}`,
  });
  expect(rejectedError.status).toBe(status);
  expect(rejectedError.code).toBeUndefined();
  expect(rejectedError.config).toEqual({ headers: expect.any(AxiosHeaders) });
  expect(rejectedError.config?.headers.toJSON()).toEqual({});
  expect(rejectedError.request).toBeUndefined();
  if (status === undefined) {
    expect(rejectedError.response).toBeUndefined();
  } else {
    expect(rejectedError.response).toEqual({
      data: undefined,
      status,
      statusText: '',
      headers: expect.any(AxiosHeaders),
      config: rejectedError.config,
    });
  }
  expect(
    JSON.stringify({
      message: rejectedError.message,
      stack: rejectedError.stack,
      json: rejectedError.toJSON(),
      config: rejectedError.config,
      request: rejectedError.request,
      response: rejectedError.response,
      code: rejectedError.code,
      name: rejectedError.name,
    }),
  ).not.toContain(token);
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

  it('returns a fixed error, clears the session, and redirects once for HTTP 401', async () => {
    const token = 'secret-expired-token';
    persistAuthSession(token, 'studio-admin');
    const navigate = vi.fn();
    const unauthorizedClient = createApiClient(navigate);
    const unauthorizedMock = new MockAdapter(unauthorizedClient);
    unauthorizedMock.onGet('/protected').reply(401);
    const getAdapterError = captureAdapterRejection(unauthorizedClient);

    const rejectedError = (await unauthorizedClient
      .get('/protected')
      .catch((error: unknown) => error)) as AxiosError;

    expectFixedSafeAxiosError(rejectedError, getAdapterError(), 401, token);
    expect(readAuthSession()).toEqual({ token: null, user: null });
    expect(navigate).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith('/');
    unauthorizedMock.restore();
  });

  it('returns a fixed HTTP 401 error when navigation fails', async () => {
    const token = 'secret-expired-token';
    persistAuthSession(token, 'studio-admin');
    const navigate = vi.fn(() => {
      throw new Error('navigation unavailable');
    });
    const unauthorizedClient = createApiClient(navigate);
    const unauthorizedMock = new MockAdapter(unauthorizedClient);
    unauthorizedMock.onGet('/protected').reply(401);
    const getAdapterError = captureAdapterRejection(unauthorizedClient);

    const rejectedError = (await unauthorizedClient
      .get('/protected')
      .catch((error: unknown) => error)) as AxiosError;
    unauthorizedMock.restore();

    expect(readAuthSession()).toEqual({ token: null, user: null });
    expect(navigate).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith('/');
    expectFixedSafeAxiosError(rejectedError, getAdapterError(), 401, token);
  });

  it('returns a fixed non-401 error without clearing or redirecting', async () => {
    const token = 'secret-valid-token';
    persistAuthSession(token, 'studio-admin');
    const navigate = vi.fn();
    const nonUnauthorizedClient = createApiClient(navigate);
    const nonUnauthorizedMock = new MockAdapter(nonUnauthorizedClient);
    nonUnauthorizedMock.onGet('/protected').reply(500);
    const getAdapterError = captureAdapterRejection(nonUnauthorizedClient);

    const rejectedError = (await nonUnauthorizedClient
      .get('/protected', { headers: { 'X-Trace-Id': 'trace-1' } })
      .catch((error: unknown) => error)) as AxiosError;

    expectFixedSafeAxiosError(rejectedError, getAdapterError(), 500, token);
    expect(readAuthSession()).toEqual({ token, user: 'studio-admin' });
    expect(navigate).not.toHaveBeenCalled();
    nonUnauthorizedMock.restore();
  });

  it('returns a fixed error for the official fetch adapter', async () => {
    const token = 'secret-fetch-token';
    persistAuthSession(token, 'studio-admin');
    const fetchClient = createApiClient();
    fetchClient.defaults.adapter = axios.getAdapter('fetch');
    const getAdapterError = captureAdapterRejection(fetchClient);
    const originalFetch = globalThis.fetch;
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 500, message: 'failed' }), {
        status: 500,
        headers: {
          'Content-Type': 'application/json',
          'X-Response-Id': 'response-1',
        },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    try {
      const rejectedError = (await fetchClient
        .get('https://studio.example/api/protected', {
          headers: { 'X-Trace-Id': 'trace-fetch' },
        })
        .catch((error: unknown) => error)) as AxiosError;

      expectFixedSafeAxiosError(rejectedError, getAdapterError(), 500, token);
    } finally {
      vi.stubGlobal('fetch', originalFetch);
    }
  });

  it('returns a fixed minimal AxiosError for readonly headers', async () => {
    const token = 'secret-readonly-token';
    persistAuthSession(token, 'studio-admin');
    const readonlyClient = createApiClient();
    let adapterError: AxiosError | undefined;
    let unsafeConfig: InternalAxiosRequestConfig | undefined;
    let unsafeRequest: { headers: Readonly<Record<string, string>> } | undefined;
    let unsafeResponse: AxiosResponse | undefined;

    readonlyClient.defaults.adapter = async (config) => {
      const frozenHeaders = Object.freeze({
        ...config.headers.toJSON(),
        authorization: `Bearer ${token}`,
        'X-Trace-Id': 'trace-readonly',
      });
      Object.defineProperty(config, 'headers', {
        value: frozenHeaders,
        writable: false,
        configurable: false,
      });
      unsafeConfig = config;
      unsafeRequest = {
        headers: Object.freeze({
          Authorization: `Bearer ${token}`,
          'X-Request-Id': 'request-1',
        }),
      };
      unsafeResponse = {
        data: { code: 500, message: 'upstream failed' },
        status: 500,
        statusText: 'Internal Server Error',
        headers: new AxiosHeaders({ 'X-Response-Id': 'response-2' }),
        config,
        request: unsafeRequest,
      };
      adapterError = new AxiosError(
        'Request failed with status code 500',
        AxiosError.ERR_BAD_RESPONSE,
        config,
        unsafeRequest,
        unsafeResponse,
      );
      return Promise.reject(adapterError);
    };

    const rejectedError = (await readonlyClient
      .get('/protected')
      .catch((error: unknown) => error)) as AxiosError;

    expect(rejectedError).not.toBe(adapterError);
    expect(axios.isAxiosError(rejectedError)).toBe(true);
    expect(rejectedError).toMatchObject({
      name: 'AxiosError',
      message: 'Request failed with status code 500',
      status: 500,
    });
    expect(rejectedError.code).toBeUndefined();
    expect(rejectedError.config === unsafeConfig).toBe(false);
    expect(rejectedError.config).toEqual({ headers: expect.any(AxiosHeaders) });
    expect(rejectedError.config?.headers.toJSON()).toEqual({});
    expect(rejectedError.request).toBeUndefined();
    expect(rejectedError.response).not.toBe(unsafeResponse);
    expect(rejectedError.response).toEqual({
      data: undefined,
      status: 500,
      statusText: '',
      headers: expect.any(AxiosHeaders),
      config: rejectedError.config,
    });
    expect(Object.prototype.hasOwnProperty.call(rejectedError, 'cause')).toBe(false);
    expect(
      JSON.stringify({
        json: rejectedError.toJSON(),
        config: rejectedError.config,
        request: rejectedError.request,
        response: rejectedError.response,
      }),
    ).not.toContain(token);
  });

  it('returns a fixed AxiosError when proxy-backed config blocks header reads', async () => {
    const token = 'secret-proxy-token';
    persistAuthSession(token, 'studio-admin');
    const proxyClient = createApiClient();
    let unsafeConfig: InternalAxiosRequestConfig | undefined;

    proxyClient.defaults.adapter = async (config) => {
      const proxyConfig = new Proxy(config, {
        get(target, key, receiver) {
          if (key === 'headers') {
            throw new Error('headers unavailable');
          }
          return Reflect.get(target, key, receiver);
        },
      });
      unsafeConfig = proxyConfig;
      const originalError = new AxiosError(
        'proxy request failed',
        AxiosError.ERR_NETWORK,
        proxyConfig,
      );
      originalError.status = 500;
      return Promise.reject(originalError);
    };

    const rejectedError = (await proxyClient
      .get('/proxy-protected')
      .catch((error: unknown) => error)) as AxiosError;

    expect(axios.isAxiosError(rejectedError)).toBe(true);
    expect(rejectedError).toMatchObject({
      name: 'AxiosError',
      message: 'Request failed with status code 500',
      status: 500,
    });
    expect(rejectedError.code).toBeUndefined();
    expect(rejectedError.config === unsafeConfig).toBe(false);
    expect(rejectedError.config).toEqual({ headers: expect.any(AxiosHeaders) });
    expect(rejectedError.config?.headers.toJSON()).toEqual({});
    expect(rejectedError.config?.url).toBeUndefined();
    expect(rejectedError.request).toBeUndefined();
    expect(rejectedError.response).toEqual({
      data: undefined,
      status: 500,
      statusText: '',
      headers: expect.any(AxiosHeaders),
      config: rejectedError.config,
    });
    expect(JSON.stringify(rejectedError.toJSON())).not.toContain(token);
    expect(Object.prototype.hasOwnProperty.call(rejectedError, 'cause')).toBe(false);
  });

  it('returns a fixed error when a proxy hides authorization from enumeration', async () => {
    const token = 'secret-hostile-proxy-token';
    persistAuthSession(token, 'studio-admin');
    const proxyClient = createApiClient();
    let originalError: AxiosError | undefined;

    proxyClient.defaults.adapter = async (config) => {
      const hostileHeaders = new Proxy(Object.create(null) as Record<PropertyKey, unknown>, {
        ownKeys: () => [],
        get: (_target, key) => {
          if (key === 'authorization') {
            return `Bearer ${token}`;
          }
          return undefined;
        },
        getOwnPropertyDescriptor: () => undefined,
      });
      Object.defineProperty(config, 'headers', {
        value: hostileHeaders,
        writable: true,
        configurable: true,
      });
      originalError = new AxiosError('Request failed', AxiosError.ERR_BAD_RESPONSE, config);
      originalError.status = 500;
      return Promise.reject(originalError);
    };

    const rejectedError = (await proxyClient
      .get('/hostile-proxy')
      .catch((error: unknown) => error)) as AxiosError;

    expect(rejectedError).not.toBe(originalError);
    expect(axios.isAxiosError(rejectedError)).toBe(true);
    expect(rejectedError.status).toBe(500);
    expect(Reflect.get(rejectedError.config?.headers ?? {}, 'Authorization')).toBeUndefined();
    expect(Reflect.get(rejectedError.config?.headers ?? {}, 'authorization')).toBeUndefined();
    expect(
      JSON.stringify({
        json: rejectedError.toJSON(),
        config: rejectedError.config,
        request: rejectedError.request,
        response: rejectedError.response,
      }),
    ).not.toContain(token);
  });

  it('replaces errors when a proxy hides uppercase authorization from ownKeys', async () => {
    const token = 'secret-hidden-uppercase-token';
    persistAuthSession(token, 'studio-admin');
    const proxyClient = createApiClient();
    let originalError: AxiosError | undefined;

    proxyClient.defaults.adapter = async (config) => {
      const hostileHeaders = new Proxy(Object.create(null) as Record<PropertyKey, unknown>, {
        ownKeys: () => [],
        get: (_target, key) => (key === 'AUTHORIZATION' ? `Bearer ${token}` : undefined),
        getOwnPropertyDescriptor: () => undefined,
      });
      Object.defineProperty(config, 'headers', {
        value: hostileHeaders,
        writable: true,
        configurable: true,
      });
      originalError = new AxiosError('Request failed', AxiosError.ERR_BAD_RESPONSE, config);
      originalError.status = 500;
      return Promise.reject(originalError);
    };

    const rejectedError = (await proxyClient
      .get('/hidden-uppercase')
      .catch((error: unknown) => error)) as AxiosError;

    expectFixedSafeAxiosError(rejectedError, originalError, 500, token);
  });

  it('replaces errors when request rawHeaders retain the credential', async () => {
    const token = 'secret-raw-headers-token';
    persistAuthSession(token, 'studio-admin');
    const rawHeadersClient = createApiClient();
    let originalError: AxiosError | undefined;

    rawHeadersClient.defaults.adapter = async (config) => {
      const request = {
        rawHeaders: ['Authorization', `Bearer ${token}`],
      };
      const response: AxiosResponse = {
        data: { code: 500, message: 'failed' },
        status: 500,
        statusText: 'Internal Server Error',
        headers: new AxiosHeaders(),
        config,
        request,
      };
      originalError = new AxiosError(
        'Request failed with status code 500',
        AxiosError.ERR_BAD_RESPONSE,
        config,
        request,
        response,
      );
      return Promise.reject(originalError);
    };

    const rejectedError = (await rawHeadersClient
      .get('/raw-headers')
      .catch((error: unknown) => error)) as AxiosError;

    expectFixedSafeAxiosError(rejectedError, originalError, 500, token);
  });

  it('replaces errors when a config function property retains the credential', async () => {
    const token = 'secret-function-property-token';
    persistAuthSession(token, 'studio-admin');
    const functionPropertyClient = createApiClient();
    let originalError: AxiosError | undefined;

    functionPropertyClient.defaults.adapter = async (config) => {
      const transformRequest = Object.assign(() => config.data, {
        debugCredential: token,
      });
      config.transformRequest = [transformRequest];
      const response: AxiosResponse = {
        data: { code: 500, message: 'failed' },
        status: 500,
        statusText: 'Internal Server Error',
        headers: new AxiosHeaders(),
        config,
      };
      originalError = new AxiosError(
        'Request failed with status code 500',
        AxiosError.ERR_BAD_RESPONSE,
        config,
        undefined,
        response,
      );
      return Promise.reject(originalError);
    };

    const rejectedError = (await functionPropertyClient
      .get('/function-property')
      .catch((error: unknown) => error)) as AxiosError;

    expectFixedSafeAxiosError(rejectedError, originalError, 500, token);
  });

  it.each(['message', 'config', 'response data', 'response headers'] as const)(
    'returns a fixed error when mutable error %s contains a credential',
    async (surface) => {
      const token = `secret-mutable-${surface.replace(' ', '-')}-token`;
      persistAuthSession(token, 'studio-admin');
      const reflectedClient = createApiClient();
      let originalError: AxiosError | undefined;

      reflectedClient.defaults.adapter = async (config) => {
        const response: AxiosResponse = {
          data: { code: 500, message: 'failed' },
          status: 500,
          statusText: 'Internal Server Error',
          headers: new AxiosHeaders({ 'X-Response-Id': 'response-3' }),
          config,
        };
        originalError = new AxiosError(
          'Request failed with status code 500',
          AxiosError.ERR_BAD_RESPONSE,
          config,
          undefined,
          response,
        );
        if (surface === 'message') {
          originalError.message = `upstream echoed ${token}`;
          originalError.stack = `AxiosError: ${token}`;
        } else if (surface === 'config') {
          Object.assign(config as InternalAxiosRequestConfig & Record<string, unknown>, {
            debugCredential: token,
          });
        } else if (surface === 'response data') {
          response.data = { nested: { credential: token } };
        } else {
          response.headers = new AxiosHeaders({ 'X-Debug-Credential': token });
        }
        return Promise.reject(originalError);
      };

      const rejectedError = (await reflectedClient
        .get('/mutable-reflected-credential')
        .catch((error: unknown) => error)) as AxiosError;

      expect(rejectedError).not.toBe(originalError);
      expect(rejectedError).toMatchObject({
        name: 'AxiosError',
        message: 'Request failed with status code 500',
        status: 500,
      });
      expect(rejectedError.code).toBeUndefined();
      expect(rejectedError.config).toEqual({ headers: expect.any(AxiosHeaders) });
      expect(rejectedError.request).toBeUndefined();
      expect(rejectedError.response).toEqual({
        data: undefined,
        status: 500,
        statusText: '',
        headers: expect.any(AxiosHeaders),
        config: rejectedError.config,
      });
      expect(JSON.stringify(rejectedError.toJSON())).not.toContain(token);
    },
  );

  it('returns a fixed minimal error when the credential appears across the original error', async () => {
    const token = 'secret-reflected-token';
    persistAuthSession(token, 'studio-admin');
    const reflectedClient = createApiClient();
    let originalError: AxiosError | undefined;

    reflectedClient.defaults.adapter = async (config) => {
      Object.assign(config as InternalAxiosRequestConfig & Record<string, unknown>, {
        debugCredential: token,
      });
      Object.defineProperty(config, 'headers', {
        value: Object.freeze({
          ...config.headers.toJSON(),
          Authorization: `Bearer ${token}`,
          'X-Debug-Credential': token,
        }),
        writable: false,
        configurable: false,
      });
      const unsafeRequest = {
        headers: Object.freeze({
          Authorization: `Bearer ${token}`,
          'X-Debug-Credential': token,
        }),
      };
      const unsafeResponse: AxiosResponse = {
        data: { nested: { credential: token } },
        status: 500,
        statusText: `failed ${token}`,
        headers: new AxiosHeaders({ 'X-Debug-Credential': token }),
        config,
        request: unsafeRequest,
      };
      originalError = new AxiosError(
        `upstream echoed ${token}`,
        `ERR_${token}`,
        config,
        unsafeRequest,
        unsafeResponse,
      );
      originalError.name = token;
      originalError.stack = `AxiosError: ${token}`;
      Object.assign(originalError, {
        cause: new Error(token),
        debugCredential: token,
      });
      return Promise.reject(originalError);
    };

    const rejectedError = (await reflectedClient
      .get('/reflected-credential')
      .catch((error: unknown) => error)) as AxiosError & {
      debugCredential?: string;
    };

    expect(rejectedError).not.toBe(originalError);
    expect(axios.isAxiosError(rejectedError)).toBe(true);
    expect(rejectedError).toMatchObject({
      name: 'AxiosError',
      message: 'Request failed with status code 500',
      status: 500,
    });
    expect(rejectedError.code).toBeUndefined();
    expect(rejectedError.config).toEqual({ headers: expect.any(AxiosHeaders) });
    expect(rejectedError.config?.headers.toJSON()).toEqual({});
    expect(rejectedError.config?.url).toBeUndefined();
    expect(rejectedError.config?.method).toBeUndefined();
    expect(rejectedError.request).toBeUndefined();
    expect(rejectedError.response).toEqual({
      data: undefined,
      status: 500,
      statusText: '',
      headers: expect.any(AxiosHeaders),
      config: rejectedError.config,
    });
    expect(Object.prototype.hasOwnProperty.call(rejectedError, 'cause')).toBe(false);
    expect(rejectedError.debugCredential).toBeUndefined();
    expect(
      JSON.stringify({
        message: rejectedError.message,
        stack: rejectedError.stack,
        json: rejectedError.toJSON(),
        config: rejectedError.config,
        request: rejectedError.request,
        response: rejectedError.response,
        code: rejectedError.code,
        name: rejectedError.name,
      }),
    ).not.toContain(token);
  });

  it('returns a fixed error for request interceptor rejections', async () => {
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

    const rejectedError = (await requestClient
      .get('/protected')
      .catch((error: unknown) => error)) as AxiosError;

    expectFixedSafeAxiosError(rejectedError, originalError, undefined, token);
  });
});
