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
import { queryAlertRules } from './alertManagement';

const mock = new MockAdapter(client);

describe('AlertManagement API', () => {
  beforeEach(() => {
    mock.reset();
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });
  });

  afterEach(() => {
    mock.reset();
    vi.unstubAllGlobals();
  });

  it('queries alert rules data', async () => {
    const rulesYaml =
      'groups:\n  - name: test\n    rules:\n      - alert: HighCPU\n        expr: cpu > 80';
    mock.onGet('/alert/rules').reply(200, { code: 200, data: { rules: rulesYaml } });

    const result = await queryAlertRules();
    expect(result.rules).toBe(rulesYaml);
    expect(result.rules).toContain('HighCPU');
  });

  it('handles empty alert rules', async () => {
    mock.onGet('/alert/rules').reply(200, { code: 200, data: { rules: '' } });

    const result = await queryAlertRules();
    expect(result.rules).toBe('');
  });
});
