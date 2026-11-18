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
import translations from '../translations';

/**
 * The dictionary is looked up by string, so nothing in the type system connects a `t('...')`
 * call to an entry. A key that is misspelled, renamed or dropped during a refactor renders as
 * the raw key - `t` falls back to returning it - and the whole suite still passes. These two
 * invariants are what makes that failure loud.
 *
 * The sources are read as text rather than walked as modules because the point is to see the
 * keys a file *asks for*, including in branches no test renders.
 */
const sources = import.meta.glob('../../**/*.{ts,tsx}', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;

/** A literal key handed to `t`. Template keys such as `` t(`audit.op.${code}`) `` are composed
 * from values that only exist at runtime, so they are out of scope for a static scan. */
const LITERAL_KEY = /\bt\(\s*'([^']+)'/g;

describe('translations dictionary', () => {
  it.each(['llm.apiKeyEncrypted', 'llm.securityNote'])(
    'describes server-side storage and API response protection accurately for %s',
    (key) => {
      expect(translations[key].zh).toContain('服务端');
      expect(translations[key].zh).toContain('只写字段');
      expect(translations[key].zh).toContain('没有应用层静态加密');
      expect(translations[key].en).toContain('server-side');
      expect(translations[key].en).toContain('write-only');
      expect(translations[key].en).toContain('no application-level encryption at rest');
    },
  );

  it('keeps both home placeholders free of unsupported security claims', () => {
    expect(translations['home.placeholder']).toEqual({
      zh: '向 RocketMQ Bot 提问',
      en: 'Ask RocketMQ Bot',
    });
  });

  it('gives every entry a non-empty Chinese and an English value', () => {
    const incomplete = Object.entries(translations)
      .filter(([, value]) => !value.zh.trim() || !value.en.trim())
      .map(([key]) => key);

    expect(incomplete, 'entries missing a locale').toEqual([]);
  });

  it('resolves every literal key the sources ask t() for', () => {
    const missing = new Map<string, string[]>();

    for (const [path, source] of Object.entries(sources)) {
      // Test files mock the language context and invent keys on purpose.
      if (path.includes('.test.')) continue;
      for (const match of source.matchAll(LITERAL_KEY)) {
        const key = match[1];
        if (key in translations) continue;
        missing.set(key, [...(missing.get(key) ?? []), path]);
      }
    }

    const detail = [...missing].map(([key, paths]) => `${key} <- ${paths.join(', ')}`);
    expect(detail, 'literal keys with no dictionary entry').toEqual([]);
  });
});
