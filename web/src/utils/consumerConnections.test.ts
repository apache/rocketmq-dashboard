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
import {
  UNKNOWN_ONLINE_INSTANCES,
  formatOnlineInstances,
  isOnlineInstancesAvailable,
  onlineInstancesSortValue,
} from './consumerConnections';

describe('consumer connection helpers', () => {
  it('treats the -1 sentinel and missing values as unavailable', () => {
    expect(UNKNOWN_ONLINE_INSTANCES).toBe(-1);
    expect(isOnlineInstancesAvailable(UNKNOWN_ONLINE_INSTANCES)).toBe(false);
    expect(isOnlineInstancesAvailable(-5)).toBe(false);
    expect(isOnlineInstancesAvailable(0)).toBe(true);
    expect(isOnlineInstancesAvailable(8)).toBe(true);
    expect(isOnlineInstancesAvailable(undefined)).toBe(false);
    expect(isOnlineInstancesAvailable(null)).toBe(false);
    expect(isOnlineInstancesAvailable(Number.NaN)).toBe(false);
  });

  it('formats known counts numerically and unavailable counts with the label', () => {
    expect(formatOnlineInstances(0, '不可用')).toBe('0');
    expect(formatOnlineInstances(1000, '不可用')).toBe(formatOnlineInstances(1000));
    expect(formatOnlineInstances(UNKNOWN_ONLINE_INSTANCES, '不可用')).toBe('不可用');
    expect(formatOnlineInstances(undefined, 'unknown')).toBe('unknown');
  });

  it('sorts unavailable connection counts after every known count', () => {
    expect(onlineInstancesSortValue(0)).toBe(0);
    expect(onlineInstancesSortValue(64)).toBe(64);
    expect(onlineInstancesSortValue(UNKNOWN_ONLINE_INSTANCES)).toBe(Number.MAX_SAFE_INTEGER);
    expect(onlineInstancesSortValue(null)).toBe(Number.MAX_SAFE_INTEGER);
  });
});
