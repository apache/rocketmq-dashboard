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
import { getInitialLanguage, LANGUAGE_STORAGE_KEY, persistLanguage } from './languagePreference';

describe('language preference', () => {
  afterEach(() => {
    localStorage.clear();
  });

  it('defaults to Chinese when no valid preference is stored', () => {
    expect(getInitialLanguage()).toBe('zh');
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'fr');
    expect(getInitialLanguage()).toBe('zh');
  });

  it('persists a supported language for the next application load', () => {
    persistLanguage('en');

    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('en');
    expect(getInitialLanguage()).toBe('en');
  });
});
