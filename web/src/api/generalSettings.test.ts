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
import type { GeneralSettings } from './settings';

const mock = new MockAdapter(client);
const settings: GeneralSettings = {
  theme: 'dark',
  compact: true,
  desktopNotify: true,
  notifySound: false,
  sessionTimeout: 60,
  requireLogin: true,
  llmProvider: 'openai',
  apiKey: 'sk-test',
  model: 'gpt-5',
  baseUrl: 'https://api.example.com/v1',
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

  it('persists all editable settings fields', async () => {
    mock.onPost('/settings/general/save').reply((config) => {
      expect(JSON.parse(config.data)).toEqual(settings);
      return [200, { code: 200, data: null }];
    });

    await expect(saveGeneralSettings(settings)).resolves.toBeUndefined();
  });
});
