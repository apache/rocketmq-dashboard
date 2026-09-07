/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 * You may obtain a copy of the License at
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
import { UNKNOWN_LAG, formatLag, isLagAvailable, lagSortValue } from './consumerLag';

describe('consumer lag helpers', () => {
  it('treats the -1 sentinel and missing values as unavailable', () => {
    expect(UNKNOWN_LAG).toBe(-1);
    expect(isLagAvailable(UNKNOWN_LAG)).toBe(false);
    expect(isLagAvailable(-5)).toBe(false);
    expect(isLagAvailable(0)).toBe(true);
    expect(isLagAvailable(1280)).toBe(true);
    expect(isLagAvailable(undefined)).toBe(false);
    expect(isLagAvailable(null)).toBe(false);
    expect(isLagAvailable(Number.NaN)).toBe(false);
  });

  it('formats known lags numerically and unknown lags with the label', () => {
    expect(formatLag(0, 'unavailable')).toBe('0');
    expect(formatLag(10000, 'unavailable')).toBe(formatLag(10000));
    expect(formatLag(UNKNOWN_LAG, 'unavailable')).toBe('unavailable');
    expect(formatLag(undefined, 'unavailable')).toBe('unavailable');
  });

  it('sorts unknown lags after every known lag', () => {
    expect(lagSortValue(0)).toBe(0);
    expect(lagSortValue(999999999)).toBe(999999999);
    expect(lagSortValue(UNKNOWN_LAG)).toBe(Number.MAX_SAFE_INTEGER);
    expect(lagSortValue(null)).toBe(Number.MAX_SAFE_INTEGER);
  });
});
