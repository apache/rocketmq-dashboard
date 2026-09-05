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
  it('exposes an option for every supported vendor', () => {
    expect(VENDOR_OPTIONS.map((option) => option.key)).toEqual([
      'APACHE',
      'ALIYUN',
      'TENCENT',
    ]);
  });

  it('defaults to the Apache vendor', () => {
    expect(DEFAULT_VENDOR).toBe('APACHE');
  });

  it('keeps every option fully labelled', () => {
    for (const option of VENDOR_OPTIONS) {
      expect(option.label.length).toBeGreaterThan(0);
      expect(option.description.length).toBeGreaterThan(0);
      expect(option.logo.length).toBeGreaterThan(0);
    }
  });
});
