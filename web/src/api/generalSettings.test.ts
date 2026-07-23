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
import { getGeneralSettings, saveGeneralSettings } from './settings';
import type { GeneralSettings, GeneralSettingsUpdate } from './settings';

const mock = new MockAdapter(client);
const settings: GeneralSettings = {
  theme: 'dark',
  compact: true,
  desktopNotify: true,
  notifySound: false,
  sessionTimeout: 60,
  requireLogin: true,
  llmProvider: 'openai',
  apiKeyConfigured: true,
  model: 'gpt-5',
  baseUrl: 'https://api.example.com/v1',
};
const editableSettings: GeneralSettingsUpdate = {
  theme: settings.theme,
  compact: settings.compact,
  desktopNotify: settings.desktopNotify,
  notifySound: settings.notifySound,
  sessionTimeout: settings.sessionTimeout,
  requireLogin: settings.requireLogin,
  llmProvider: settings.llmProvider,
  model: settings.model,
  baseUrl: settings.baseUrl,
};

describe('general settings API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('loads the settings shape returned by GeneralSettingsVO', async () => {
    mock.onGet('/settings/general').reply(200, { code: 200, data: settings });

    await expect(getGeneralSettings()).resolves.toEqual(settings);
  });

  it('sends a replacement API key without requiring the stored secret', async () => {
    const update: GeneralSettingsUpdate = { ...editableSettings, apiKey: 'sk-new' };
    mock.onPost('/settings/general/save').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(update);
      return [200, { code: 200, data: null }];
    });

    await expect(saveGeneralSettings(update)).resolves.toBeUndefined();
  });

  it('omits blank and response-only API key fields when preserving the stored secret', async () => {
    const update = {
      ...settings,
      apiKey: '  ',
    } as GeneralSettingsUpdate & { apiKeyConfigured: boolean };
    mock.onPost('/settings/general/save').reply((config) => {
      const body = JSON.parse(config.data);
      expect(body).not.toHaveProperty('apiKey');
      expect(body).not.toHaveProperty('apiKeyConfigured');
      return [200, { code: 200, data: null }];
    });

    await expect(saveGeneralSettings(update)).resolves.toBeUndefined();
  });

  it('sends the explicit API key clear flag', async () => {
    const update: GeneralSettingsUpdate = { ...editableSettings, clearApiKey: true };
    mock.onPost('/settings/general/save').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(update);
      return [200, { code: 200, data: null }];
    });

    await expect(saveGeneralSettings(update)).resolves.toBeUndefined();
  });
});
