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

import { afterEach, describe, expect, it } from 'vitest';
import {
  COMPACT_STORAGE_KEY,
  THEME_STORAGE_KEY,
  getStoredCompact,
  getStoredThemeMode,
  persistCompact,
  persistThemeMode,
} from './themePreference';

describe('theme preference', () => {
  afterEach(() => {
    localStorage.clear();
  });

  it('falls back to system mode for missing or invalid stored values', () => {
    expect(getStoredThemeMode()).toBe('system');
    localStorage.setItem(THEME_STORAGE_KEY, 'neon');
    expect(getStoredThemeMode()).toBe('system');
  });

  it('persists light, dark and system theme choices', () => {
    persistThemeMode('dark');
    expect(getStoredThemeMode()).toBe('dark');

    persistThemeMode('light');
    expect(getStoredThemeMode()).toBe('light');

    persistThemeMode('system');
    expect(getStoredThemeMode()).toBe('system');
  });

  it('persists the compact mode preference', () => {
    expect(getStoredCompact()).toBe(false);

    persistCompact(true);
    expect(getStoredCompact()).toBe(true);
    expect(localStorage.getItem(COMPACT_STORAGE_KEY)).toBe('true');

    persistCompact(false);
    expect(getStoredCompact()).toBe(false);
  });
});
