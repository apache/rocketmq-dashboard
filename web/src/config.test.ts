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

import { afterEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL } from './config';

describe('config API base url', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.resetModules();
  });

  it('defaults to a relative /api path when no env override is set', () => {
    expect(API_BASE_URL).toBe('/api');
  });

  it('never resolves to an absolute localhost address by default', () => {
    expect(API_BASE_URL.startsWith('http://localhost')).toBe(false);
    expect(API_BASE_URL.startsWith('https://localhost')).toBe(false);
    expect(API_BASE_URL.startsWith('//localhost')).toBe(false);
  });

  it('strips a single trailing slash', () => {
    expect(API_BASE_URL.endsWith('/')).toBe(false);
  });

  it('uses an env override and strips its trailing slash', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://gateway.example/api/');
    vi.resetModules();

    const mod = await import('./config');

    expect(mod.API_BASE_URL).toBe('https://gateway.example/api');
  });
});
