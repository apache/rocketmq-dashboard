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
import { readLocalStorage, removeLocalStorage, writeLocalStorage } from './browserStorage';

describe('browserStorage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    window.localStorage.clear();
  });

  it('reads, writes and removes available storage', () => {
    expect(writeLocalStorage('key', 'value')).toBe(true);
    expect(readLocalStorage('key')).toBe('value');
    expect(removeLocalStorage('key')).toBe(true);
    expect(readLocalStorage('key')).toBeNull();
  });

  it('round-trips an empty string value as stored, not as absent', () => {
    expect(writeLocalStorage('flag', '')).toBe(true);
    expect(readLocalStorage('flag')).toBe('');
    expect(writeLocalStorage('flag', 'false')).toBe(true);
    expect(readLocalStorage('flag')).toBe('false');
  });

  it('keeps keys isolated from each other', () => {
    writeLocalStorage('alpha', 'first');
    writeLocalStorage('beta', 'second');
    expect(readLocalStorage('alpha')).toBe('first');
    expect(readLocalStorage('beta')).toBe('second');
    removeLocalStorage('alpha');
    expect(readLocalStorage('alpha')).toBeNull();
    expect(readLocalStorage('beta')).toBe('second');
  });

  it('removing an absent key is a successful no-op', () => {
    expect(removeLocalStorage('never-written')).toBe(true);
    expect(removeLocalStorage('never-written')).toBe(true);
  });

  it('returns safe fallbacks when storage operations throw', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });

    expect(readLocalStorage('key')).toBeNull();
    expect(writeLocalStorage('key', 'value')).toBe(false);
    expect(removeLocalStorage('key')).toBe(false);
  });

  it('isolates a failing read from subsequent writes and removes', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError');
    });

    expect(readLocalStorage('key')).toBeNull();
    expect(writeLocalStorage('key', 'value')).toBe(true);
    expect(removeLocalStorage('key')).toBe(true);
  });

  it('treats a quota failure on write as a failed write without masking reads', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('full', 'QuotaExceededError');
    });

    expect(writeLocalStorage('key', 'value')).toBe(false);
    expect(readLocalStorage('key')).toBeNull();
    expect(removeLocalStorage('key')).toBe(true);
  });

  it('falls back safely when the storage global is unavailable', () => {
    vi.stubGlobal('localStorage', undefined);

    expect(readLocalStorage('key')).toBeNull();
    expect(writeLocalStorage('key', 'value')).toBe(false);
    expect(removeLocalStorage('key')).toBe(false);
  });
});
