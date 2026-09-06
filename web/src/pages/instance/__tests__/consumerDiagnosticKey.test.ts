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
import { diagnosticCacheKey } from '../consumer';

describe('diagnosticCacheKey', () => {
  it('builds a key from the instance and consumer group', () => {
    expect(diagnosticCacheKey('instance-a', 'cg-order')).toBe('instance-a\u0000cg-order');
  });

  it('uses an empty instance segment when no instance is selected', () => {
    expect(diagnosticCacheKey(undefined, 'cg-order')).toBe('\u0000cg-order');
  });

  it('keeps instance and group boundaries unambiguous', () => {
    // Without a separator, instance "a" + group "bc" would collide with instance "ab" + "c".
    expect(diagnosticCacheKey('a', 'bc')).not.toBe(diagnosticCacheKey('ab', 'c'));
  });

  it('distinguishes groups that share a prefix', () => {
    expect(diagnosticCacheKey('instance-a', 'cg')).not.toBe(
      diagnosticCacheKey('instance-a', 'cg-order'),
    );
  });
});
