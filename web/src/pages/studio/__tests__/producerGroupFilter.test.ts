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
import { matchesProducerGroupOption } from '../Producer';

describe('matchesProducerGroupOption', () => {
  it('matches group values case-insensitively', () => {
    const option = { value: 'order-events-producer' };
    expect(matchesProducerGroupOption('ORDER-EVENTS', option)).toBe(true);
    expect(matchesProducerGroupOption('producer', option)).toBe(true);
    expect(matchesProducerGroupOption('missing-group', option)).toBe(false);
  });

  it('matches every option for an empty search string', () => {
    expect(matchesProducerGroupOption('', { value: 'any-group' })).toBe(true);
  });

  it('does not throw when the option value is null or undefined', () => {
    expect(matchesProducerGroupOption('group', { value: null })).toBe(false);
    expect(matchesProducerGroupOption('group', {})).toBe(false);
    expect(matchesProducerGroupOption('group', undefined)).toBe(false);
    expect(matchesProducerGroupOption('', { value: null })).toBe(true);
  });

  it('does not throw when the input value is undefined', () => {
    expect(matchesProducerGroupOption(undefined, { value: 'any-group' })).toBe(true);
  });

  it('coerces non-string option values before comparing', () => {
    expect(matchesProducerGroupOption('1234', { value: 12345 })).toBe(true);
  });
});
