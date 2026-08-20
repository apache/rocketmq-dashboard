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

import { describe, expect, it } from 'vitest';
import { API_BASE_URL, normalizeApiBaseUrl } from './config';

describe('config API base url', () => {
  it('defaults to a relative /api path when no env override is set', () => {
    expect(API_BASE_URL).toBe('/api');
  });

  it('never resolves to an absolute localhost address by default', () => {
    expect(API_BASE_URL.startsWith('http://localhost')).toBe(false);
    expect(API_BASE_URL.startsWith('https://localhost')).toBe(false);
    expect(API_BASE_URL.startsWith('//localhost')).toBe(false);
  });

  it.each([
    [undefined, '/api'],
    ['', '/api'],
    ['   ', '/api'],
    ['/api', '/api'],
    [' /api/ ', '/api'],
    ['/api///', '/api'],
    [' https://gateway.example.com/studio/api/// ', 'https://gateway.example.com/studio/api'],
  ])('normalizes API base override %j to %j', (input, expected) => {
    expect(normalizeApiBaseUrl(input)).toBe(expected);
  });

  it('represents a same-origin root override without a trailing slash', () => {
    expect(normalizeApiBaseUrl('/')).toBe('');
    expect(normalizeApiBaseUrl(' /// ')).toBe('');
  });
});
