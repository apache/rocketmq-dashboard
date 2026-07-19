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
import { filterNavigationEntries, isNavigationSearchShortcut } from './navigationSearch';

describe('navigation search helpers', () => {
  const entries = [
    { key: '/cluster', label: 'RocketMQ 集群' },
    { key: '/settings', label: 'Settings' },
  ];

  it('filters labels case-insensitively and ignores query whitespace', () => {
    expect(filterNavigationEntries(entries, ' SETTINGS ')).toEqual([entries[1]]);
    expect(filterNavigationEntries(entries, '集群')).toEqual([entries[0]]);
  });

  it('recognizes Control/Command-K but rejects alternative shortcuts', () => {
    expect(isNavigationSearchShortcut({ key: 'k', ctrlKey: true, metaKey: false, altKey: false })).toBe(true);
    expect(isNavigationSearchShortcut({ key: 'K', ctrlKey: false, metaKey: true, altKey: false })).toBe(true);
    expect(isNavigationSearchShortcut({ key: 'k', ctrlKey: false, metaKey: false, altKey: false })).toBe(false);
    expect(isNavigationSearchShortcut({ key: 'k', ctrlKey: true, metaKey: false, altKey: true })).toBe(false);
  });
});
