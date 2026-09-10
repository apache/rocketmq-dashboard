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

import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  buildSupportBundleFilename,
  buildSupportBundleSummary,
  collectSupportBundle,
  formatSupportBundle,
  type SupportBundleProduct,
} from './supportBundle';

const product: SupportBundleProduct = {
  name: 'RocketMQ Studio',
  version: '0.1.0',
  buildCommit: 'abc1234',
  buildTime: '2026-09-10 12:00',
  rocketmqVersions: '4.x / 5.x',
  frontendFramework: 'React 18 + Ant Design 5',
  backendFramework: 'Spring Boot 3 + RocketMQ MCP Server',
  license: 'Apache 2.0',
};

const generatedAt = new Date('2026-09-10T01:02:03.456Z');

function seedSafePreferences() {
  localStorage.setItem('rocketmq-studio-language', 'en');
  localStorage.setItem('rocketmq-studio-theme', 'dark');
  localStorage.setItem('rocketmq-studio-compact', 'true');
  localStorage.setItem(
    'rocketmq-studio-data-mode',
    JSON.stringify({ state: { useMock: true }, version: 0 }),
  );
  localStorage.setItem('rocketmq-studio-user', 'operator-a');
  localStorage.setItem('rocketmq-studio-user-id', '7');
  localStorage.setItem('rocketmq-studio-user-admin', 'false');
  localStorage.setItem('proxyAddr', 'proxy-a:8081');
  localStorage.setItem('clusterId', 'DefaultCluster');
  localStorage.setItem('rocketmq-studio.metric-profile', 'brokerThroughput');
  localStorage.setItem('rocketmq-studio-message-trace-topic:instance-a', 'TRACE_TOPIC_A');
}

describe('support bundle', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    window.history.pushState({}, '', '/');
    vi.restoreAllMocks();
  });

  it('collects build, runtime, browser and allow-listed preference state', () => {
    seedSafePreferences();

    const bundle = collectSupportBundle(product, {
      now: generatedAt,
      effectiveMockMode: false,
    });

    expect(bundle.generatedAt).toBe('2026-09-10T01:02:03.456Z');
    expect(bundle.product).toEqual(product);
    expect(bundle.runtime.apiBaseUrl).toBe('/api');
    expect(bundle.runtime.pathname).toBe('/');
    expect(bundle.preferences).toMatchObject({
      language: 'en',
      theme: 'dark',
      compactMode: 'yes',
      effectiveDataMode: 'live',
      persistedDataMode: 'mock',
      authUserPresent: 'yes',
      authUserIdPresent: 'yes',
      authAdmin: 'no',
      proxyAddressConfigured: 'yes',
      clusterIdConfigured: 'yes',
      metricsProfileConfigured: 'yes',
      customTraceTopicScopes: 1,
    });
    expect(bundle.browser.storage.localStorage.status).toBe('available');
    expect(bundle.browser.storage.sessionStorage.status).toBe('available');
    expect(bundle.browser.viewport.width).toBe(window.innerWidth);
  });

  it('omits sensitive localStorage values from the formatted JSON', () => {
    seedSafePreferences();
    localStorage.setItem('token', 'bearer-token-value');
    localStorage.setItem('rocketmq-studio-secret', 'signing-secret-value');
    localStorage.setItem('password', 'plain-password-value');
    localStorage.setItem('custom-ui-state', 'not-needed');
    sessionStorage.setItem(
      'rocketmq-studio-ai-chat-history',
      JSON.stringify({ conversations: [{ content: 'incident details' }] }),
    );

    const text = formatSupportBundle(collectSupportBundle(product, { now: generatedAt }));

    expect(text).toContain('"unsafeLocalStorageKeysOmitted": 3');
    expect(text).toContain('"unknownLocalStorageKeysOmitted": 1');
    expect(text).toContain('"sessionStorageValuesOmitted": true');
    expect(text).not.toContain('bearer-token-value');
    expect(text).not.toContain('signing-secret-value');
    expect(text).not.toContain('plain-password-value');
    expect(text).not.toContain('not-needed');
    expect(text).not.toContain('incident details');
    expect(text).not.toContain('TRACE_TOPIC_A');
    expect(text).not.toContain('operator-a');
    expect(text).not.toContain('proxy-a:8081');
    expect(text).not.toContain('DefaultCluster');
  });

  it('strips query strings and hash fragments while recording their presence', () => {
    window.history.pushState({}, '', '/settings?token=abc#frag');

    const bundle = collectSupportBundle(product, { now: generatedAt });

    expect(bundle.runtime.pathname).toBe('/settings');
    expect(bundle.runtime.hasQueryString).toBe(true);
    expect(bundle.runtime.hasHash).toBe(true);
    expect(bundle.redaction.routeQueryAndHashOmitted).toBe(true);
    expect(formatSupportBundle(bundle)).not.toContain('token=abc');
    expect(formatSupportBundle(bundle)).not.toContain('#frag');
  });

  it('reports unavailable storage without throwing', () => {
    const storage = {
      get length() {
        throw new Error('blocked');
      },
      key: () => {
        throw new Error('blocked');
      },
      getItem: () => {
        throw new Error('blocked');
      },
      setItem: () => {
        throw new Error('blocked');
      },
      removeItem: () => {
        throw new Error('blocked');
      },
      clear: () => undefined,
    } as unknown as Storage;

    const bundle = collectSupportBundle(product, {
      now: generatedAt,
      localStorageRef: storage,
      sessionStorageRef: storage,
    });

    expect(bundle.browser.storage.localStorage).toMatchObject({
      status: 'unavailable',
      readable: false,
      writable: false,
      keyCount: 0,
    });
    expect(bundle.browser.storage.localStorage.error).toContain('Error: blocked');
    expect(bundle.preferences.language).toBe('unknown');
    expect(bundle.preferences.persistedDataMode).toBe('unset');
  });

  it('marks malformed persisted data mode separately from the effective data mode', () => {
    localStorage.setItem('rocketmq-studio-data-mode', '{"state":');

    const bundle = collectSupportBundle(product, {
      now: generatedAt,
      effectiveMockMode: true,
    });

    expect(bundle.preferences.persistedDataMode).toBe('invalid');
    expect(bundle.preferences.effectiveDataMode).toBe('mock');
  });

  it('builds a stable support bundle filename from commit and generation time', () => {
    const bundle = collectSupportBundle(
      {
        ...product,
        buildCommit: 'feature/support bundle',
      },
      { now: generatedAt },
    );

    expect(buildSupportBundleFilename(bundle)).toBe(
      'rocketmq-studio-support-feature_supp-2026-09-10T01-02-03-456Z.json',
    );
  });

  it('returns compact summary rows for the About page', () => {
    seedSafePreferences();

    const summary = buildSupportBundleSummary(
      collectSupportBundle(product, { now: generatedAt, effectiveMockMode: true }),
    );

    expect(summary).toEqual(
      expect.arrayContaining([
        { label: '构建', value: '0.1.0 / abc1234' },
        { label: 'API 前缀', value: '/api' },
        { label: '当前路径', value: window.location.pathname },
        { label: '数据模式', value: 'mock' },
        { label: '界面偏好', value: 'en / dark / compact=yes' },
      ]),
    );
  });
});
