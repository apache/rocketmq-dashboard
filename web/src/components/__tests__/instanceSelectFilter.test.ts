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
import { matchesInstanceOption } from '../InstanceSelect';

describe('matchesInstanceOption', () => {
  it('matches option labels case-insensitively', () => {
    const option = { label: 'instance-a' };
    expect(matchesInstanceOption('INSTANCE-A', option)).toBe(true);
    expect(matchesInstanceOption('instance', option)).toBe(true);
    expect(matchesInstanceOption('instance-b', option)).toBe(false);
  });

  it('matches every option for an empty search string', () => {
    expect(matchesInstanceOption('', { label: 'any' })).toBe(true);
  });

  it('does not throw for nullish or non-string labels and input', () => {
    expect(matchesInstanceOption('x', { label: null })).toBe(false);
    expect(matchesInstanceOption('x', {})).toBe(false);
    expect(matchesInstanceOption('x', undefined)).toBe(false);
    expect(matchesInstanceOption(undefined, { label: 'any' })).toBe(true);
    expect(matchesInstanceOption('1', { label: 21 })).toBe(true);
  });
});
