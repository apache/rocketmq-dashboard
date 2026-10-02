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
import { message } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import client, { handleSessionUnauthorized } from './client';

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

let originalLocation: Location | undefined;

/** jsdom makes `window.location` non-navigable, so a test that observes a redirect replaces it. */
const stubSessionLocation = (pathname: string, search: string) => {
  originalLocation ??= window.location;
  const stub = { origin: 'http://localhost:3000', pathname, search, href: '' };
  Object.defineProperty(window, 'location', { configurable: true, writable: true, value: stub });
  return { href: () => stub.href };
};

const restoreWindowLocation = () => {
  if (!originalLocation) return;
  Object.defineProperty(window, 'location', {
    configurable: true,
    writable: true,
    value: originalLocation,
  });
  originalLocation = undefined;
};

describe('API client response contract', () => {
  beforeEach(() => {
    mock.reset();
    localStorage.clear();
    vi.mocked(message.error).mockClear();
  });

  afterEach(() => {
    restoreWindowLocation();
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

  it('rejects failed HTTP envelopes with the backend message', async () => {
    mock.onPost('/topics/create').reply(400, {
      code: 400,
      message: 'Topic name is required',
      data: null,
    });

    await expect(client.post('/topics/create', {})).rejects.toThrow('Topic name is required');
    expect(message.error).toHaveBeenCalledWith('Topic name is required');
  });

  it('uses a stable fallback for malformed error envelopes', async () => {
    mock.onGet('/clusters').reply(200, { code: '500', data: null });

    await expect(client.get('/clusters')).rejects.toThrow('请求失败');
    expect(message.error).toHaveBeenCalledWith('请求失败');
  });

  it('passes through domain payloads with string code fields', async () => {
    const payload = {
      status: 1,
      errMsg: 'LLM API key is required',
      code: 'llm.config.missing_api_key',
      hint: 'Configure an API key for provider openai.',
    };
    mock.onPost('/llm/config/test').reply(200, payload);

    await expect(client.post('/llm/config/test', {})).resolves.toMatchObject({ data: payload });
    expect(message.error).not.toHaveBeenCalled();
  });

  it('uses credentials without attaching a client-readable bearer token', async () => {
    mock.onGet('/clusters').reply((config) => {
      expect(config.headers?.Authorization).toBeUndefined();
      expect(config.withCredentials).toBe(true);
      return [200, { code: 200, message: 'success', data: [] }];
    });

    await client.get('/clusters');
  });

  it.each(['/auth/login', '/auth/status'])(
    'does not clear the current session when public auth request %s returns 401',
    async (path) => {
      localStorage.setItem('rocketmq-studio-user', 'admin');
      localStorage.setItem('rocketmq-studio-user-admin', 'true');
      mock.onAny(path).reply(401, { code: 401, message: 'Unauthorized', data: null });

      await expect(client.get(path)).rejects.toMatchObject({ response: { status: 401 } });

      expect(localStorage.getItem('token')).toBeNull();
      expect(localStorage.getItem('rocketmq-studio-user')).toBe('admin');
      expect(localStorage.getItem('rocketmq-studio-user-admin')).toBe('true');
    },
  );

  it('clears the current session when a protected API request returns 401', async () => {
    localStorage.setItem('rocketmq-studio-user', 'admin');
    localStorage.setItem('rocketmq-studio-user-admin', 'true');
    mock.onGet('/clusters').reply(401, { code: 401, message: 'Unauthorized', data: null });

    await expect(client.get('/clusters')).rejects.toMatchObject({ response: { status: 401 } });

    expect(localStorage.getItem('rocketmq-studio-user')).toBeNull();
    expect(localStorage.getItem('rocketmq-studio-user-admin')).toBeNull();
    expect(message.error).not.toHaveBeenCalled();
  });

  it('preserves the original 401 error when the request URL is malformed', async () => {
    mock.onGet('http://[').reply(401, { code: 401, message: 'Unauthorized', data: null });

    await expect(client.get('http://[')).rejects.toMatchObject({ response: { status: 401 } });
  });

  it('surfaces an actionable hint when the server rejects the origin via CORS', async () => {
    mock.onPost('/instances/delete').reply(403, 'Invalid CORS request');

    await expect(client.post('/instances/delete', { id: 'x' })).rejects.toThrow(/CORS/);
    expect(message.error).toHaveBeenCalledWith(
      expect.stringContaining('STUDIO_CORS_ALLOWED_ORIGINS'),
    );
  });

  it('does not treat a business-envelope 403 as a CORS rejection', async () => {
    mock
      .onPost('/instances/delete')
      .reply(403, { code: 403, message: 'Admin permission required', data: null });

    await expect(client.post('/instances/delete', { id: 'x' })).rejects.toThrow(
      'Admin permission required',
    );
  });

  it('sends an expired session to the login page carrying the page it was on', () => {
    const session = stubSessionLocation('/ops/alerts', '?level=error');

    handleSessionUnauthorized();

    expect(session.href()).toBe('/login?redirect=%2Fops%2Falerts%3Flevel%3Derror');
  });

  it('carries the current page when a protected request expires the session', async () => {
    const session = stubSessionLocation('/instance/message', '?topic=orders');
    mock.onGet('/clusters').reply(401, { code: 401, message: 'Unauthorized', data: null });

    await expect(client.get('/clusters')).rejects.toMatchObject({ response: { status: 401 } });

    expect(session.href()).toBe(
      `/login?redirect=${encodeURIComponent('/instance/message?topic=orders')}`,
    );
  });

  it('redirects plainly when the session expires on the login page itself', () => {
    const session = stubSessionLocation('/login', '?stale=1');

    handleSessionUnauthorized();

    expect(session.href()).toBe('/login');
  });
});
