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

import { filterModeMeta } from '../../pages/instance/consumer';

describe('filterModeMeta value domains', () => {
  it('maps the backend TAG form to the tag color and i18n key', () => {
    expect(filterModeMeta('TAG')).toEqual({ color: 'blue', labelKey: 'consumer.filterTag' });
  });

  it('maps the backend SQL form to the sql color and i18n key', () => {
    expect(filterModeMeta('SQL')).toEqual({ color: 'purple', labelKey: 'consumer.filterSql92' });
  });

  it('maps the Tencent raw SQL92 form like the normalized SQL form', () => {
    expect(filterModeMeta('SQL92').color).toBe('purple');
    expect(filterModeMeta('SQL92').labelKey).toBe('consumer.filterSql92');
  });

  it('maps CLASS_FILTER to a distinct color and keeps its raw label', () => {
    expect(filterModeMeta('CLASS_FILTER')).toEqual({ color: 'cyan', labelKey: null });
  });

  it('keeps mapping the mock Chinese values (control)', () => {
    expect(filterModeMeta('Tag 过滤').color).toBe('blue');
    expect(filterModeMeta('SQL92 过滤').color).toBe('purple');
    expect(filterModeMeta('全量')).toEqual({ color: 'default', labelKey: 'consumer.filterAll' });
  });

  it('falls back to the default color and the raw label for unknown values', () => {
    expect(filterModeMeta('whatever')).toEqual({ color: 'default', labelKey: null });
    expect(filterModeMeta(undefined)).toEqual({ color: 'default', labelKey: 'consumer.filterAll' });
  });
});
