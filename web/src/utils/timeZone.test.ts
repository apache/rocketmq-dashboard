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
import { zonedLocalDateTimeToUtc } from './timeZone';

describe('zonedLocalDateTimeToUtc', () => {
  it('converts a positive fixed offset independently of the browser zone', () => {
    expect(zonedLocalDateTimeToUtc('2026-09-07T09:00', 'Asia/Shanghai')).toBe(
      '2026-09-07T01:00:00.000Z',
    );
  });

  it('converts a negative winter offset', () => {
    expect(zonedLocalDateTimeToUtc('2026-01-15T09:30:45', 'America/New_York')).toBe(
      '2026-01-15T14:30:45.000Z',
    );
  });

  it('uses daylight-saving offset after the spring transition', () => {
    expect(zonedLocalDateTimeToUtc('2026-03-08T03:30', 'America/New_York')).toBe(
      '2026-03-08T07:30:00.000Z',
    );
  });

  it('rejects a wall-clock time skipped by daylight saving', () => {
    expect(() => zonedLocalDateTimeToUtc('2026-03-08T02:30', 'America/New_York')).toThrow(
      'Local date time does not exist in America/New_York',
    );
  });

  it('supports UTC and second precision', () => {
    expect(zonedLocalDateTimeToUtc('2026-09-07T09:00:59', 'UTC')).toBe('2026-09-07T09:00:59.000Z');
  });

  it('rejects impossible calendar values before conversion', () => {
    expect(() => zonedLocalDateTimeToUtc('2026-02-30T09:00', 'UTC')).toThrow(
      'Invalid local date time',
    );
  });

  it('rejects unknown IANA time zones', () => {
    expect(() => zonedLocalDateTimeToUtc('2026-09-07T09:00', 'Mars/Olympus')).toThrow();
  });
});
