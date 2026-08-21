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

import { tableScrollX } from './table';

describe('tableScrollX', () => {
  it('sums the declared column widths', () => {
    expect(tableScrollX([{ width: 220 }, { width: 200 }, { width: 100 }])).toBe(520);
  });

  it('reserves room for the selection and expand columns', () => {
    expect(tableScrollX([{ width: 100 }], { selection: true })).toBe(140);
    expect(tableScrollX([{ width: 100 }], { expandable: true })).toBe(148);
    expect(tableScrollX([{ width: 100 }], { selection: true, expandable: true, extra: 12 })).toBe(
      200,
    );
  });

  it('falls back to a default share for columns without a numeric width', () => {
    expect(tableScrollX([{ width: 100 }, {}, { width: '30%' }])).toBe(340);
    expect(tableScrollX([{ width: '80px' }])).toBe(80);
  });

  it('adds up grouped children and skips hidden columns', () => {
    expect(tableScrollX([{ children: [{ width: 100 }, { width: 60 }] }, { width: 40 }])).toBe(200);
    expect(tableScrollX([{ width: 100 }, { width: 999, hidden: true }])).toBe(100);
  });

  it('tolerates an undefined column list', () => {
    expect(tableScrollX(undefined)).toBe(0);
  });
});
