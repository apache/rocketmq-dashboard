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
import { DEFAULT_VENDOR, VENDOR_OPTIONS } from './vendorOptions';

describe('instance vendor options', () => {
  it('defaults to the Apache open-source vendor', () => {
    expect(DEFAULT_VENDOR).toBe('APACHE');
  });

  it('offers the three supported vendors in order', () => {
    expect(VENDOR_OPTIONS.map((option) => option.key)).toEqual([
      'APACHE',
      'ALIYUN',
      'TENCENT',
    ]);
  });

  it('describes every vendor with a label and logo', () => {
    for (const option of VENDOR_OPTIONS) {
      expect(option.label).not.toBe('');
      expect(option.logo).not.toBe('');
      expect(option.description).not.toBe('');
    }
  });

  it('keeps the Apache entry pointing at the default vendor', () => {
    expect(VENDOR_OPTIONS.find((option) => option.key === DEFAULT_VENDOR)).toBeDefined();
  });
});
