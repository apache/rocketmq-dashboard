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
import { matchesClientSearch } from '../clientsSearch';

describe('matchesClientSearch', () => {
  it('matches client id case-insensitively', () => {
    const connection = { clientId: 'PID_10.0.0.5_1', address: '10.0.0.5:8080' };

    expect(matchesClientSearch(connection, 'pid_10.0.0.5')).toBe(true);
    expect(matchesClientSearch(connection, 'PID_10.0.0.5_1')).toBe(true);
    expect(matchesClientSearch(connection, 'nope')).toBe(false);
  });

  it('matches address case-insensitively', () => {
    const connection = { clientId: 'PID_1', address: '10.0.0.5:8080' };

    expect(matchesClientSearch(connection, '10.0.0.5:8080')).toBe(true);
  });

  it('trims whitespace from the search input', () => {
    const connection = { clientId: 'PID_1', address: '10.0.0.5:8080' };

    expect(matchesClientSearch(connection, '  pid_1  ')).toBe(true);
    expect(matchesClientSearch(connection, '  nope  ')).toBe(false);
  });

  it('treats empty and whitespace-only search as a full match', () => {
    const connection = { clientId: 'PID_1', address: '10.0.0.5:8080' };

    expect(matchesClientSearch(connection, '')).toBe(true);
    expect(matchesClientSearch(connection, '   ')).toBe(true);
    expect(matchesClientSearch(connection, undefined)).toBe(true);
  });

  it('does not throw when clientId or address is missing', () => {
    expect(matchesClientSearch({}, 'pid')).toBe(false);
    expect(matchesClientSearch({ clientId: null, address: undefined }, '  ')).toBe(true);
    expect(matchesClientSearch({ address: '10.0.0.9:8080' }, '10.0.0.9')).toBe(true);
    expect(matchesClientSearch({ clientId: 'PID_9' }, 'pid_9')).toBe(true);
  });
});
