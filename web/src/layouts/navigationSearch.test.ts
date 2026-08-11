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

import { afterEach, describe, expect, it, vi } from 'vitest';
import { filterNavigationEntries, isNavigationSearchShortcut } from './navigationSearch';

describe('navigation search helpers', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  const entries = [
    { key: '/cluster', label: 'RocketMQ 集群' },
    { key: '/settings', label: 'Settings' },
    { key: '/ops/system-alerts', label: 'Alert Events' },
  ];

  it('filters labels case-insensitively and ignores query whitespace', () => {
    expect(filterNavigationEntries(entries, ' SETTINGS ')).toEqual([entries[1]]);
    expect(filterNavigationEntries(entries, '集群')).toEqual([entries[0]]);
  });

  it('normalizes searches independently of the browser locale', () => {
    const localeLowerCase = String.prototype.toLocaleLowerCase;
    vi.spyOn(String.prototype, 'toLocaleLowerCase').mockImplementation(function () {
      return localeLowerCase.call(this, 'tr');
    });

    expect(filterNavigationEntries(entries, 'SETTINGS')).toEqual([entries[1]]);
  });

  it('filters by route keys case-insensitively', () => {
    expect(filterNavigationEntries(entries, 'CLUSTER')).toEqual([entries[0]]);
    expect(filterNavigationEntries(entries, '/settings')).toEqual([entries[1]]);
  });

  it('matches multiple terms across labels and normalized route segments', () => {
    expect(filterNavigationEntries(entries, 'system alerts')).toEqual([entries[2]]);
    expect(filterNavigationEntries(entries, 'OPS / SYSTEM')).toEqual([entries[2]]);
    expect(filterNavigationEntries(entries, 'rocketmq cluster')).toEqual([entries[0]]);
    expect(filterNavigationEntries(entries, 'system cluster')).toEqual([]);
  });

  it('ranks exact and prefix matches before broader matches', () => {
    const rankedEntries = [
      { key: '/settings/data-sources', label: 'Data Source Settings' },
      { key: '/settings', label: 'Settings' },
      { key: '/settings/general', label: 'Settings - General' },
    ];

    expect(filterNavigationEntries(rankedEntries, 'settings')).toEqual([
      rankedEntries[1],
      rankedEntries[2],
      rankedEntries[0],
    ]);
  });

  it('recognizes Control/Command-K but rejects alternative shortcuts', () => {
    expect(
      isNavigationSearchShortcut({ key: 'k', ctrlKey: true, metaKey: false, altKey: false }),
    ).toBe(true);
    expect(
      isNavigationSearchShortcut({ key: 'K', ctrlKey: false, metaKey: true, altKey: false }),
    ).toBe(true);
    expect(
      isNavigationSearchShortcut({ key: 'k', ctrlKey: false, metaKey: false, altKey: false }),
    ).toBe(false);
    expect(
      isNavigationSearchShortcut({ key: 'k', ctrlKey: true, metaKey: false, altKey: true }),
    ).toBe(false);
  });

  it('does not capture the shortcut from editable elements', () => {
    const input = document.createElement('input');
    const textarea = document.createElement('textarea');
    const select = document.createElement('select');
    const editable = document.createElement('div');
    editable.setAttribute('contenteditable', 'true');
    const editableChild = document.createElement('span');
    editable.appendChild(editableChild);

    for (const target of [input, textarea, select, editable, editableChild]) {
      expect(
        isNavigationSearchShortcut({
          key: 'k',
          ctrlKey: true,
          metaKey: false,
          altKey: false,
          target,
        }),
      ).toBe(false);
    }
  });
});
