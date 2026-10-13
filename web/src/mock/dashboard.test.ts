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
import { formatUtcDateTime } from '../utils/format';
import { systemAlerts } from './dashboard';

/**
 * SystemAlertVO.time is a UTC LocalDateTime
 * (server/src/main/java/org/apache/rocketmq/studio/ops/alert/SystemAlertVO.java), so the alert API
 * serializes it as an ISO-8601 date-time without a zone offset, for example "2026-07-02T18:15:00".
 * The system alert page renders the field through `formatUtcDateTime`, which returns "-" for a
 * value it cannot parse, and its CSV export writes the field verbatim under a "Time (UTC)" header.
 */
const backendTime = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?$/;

describe('system alert mock contract', () => {
  it('pins the alert time to the UTC date-time the alert API serializes', () => {
    const values = systemAlerts.map((alert) => alert.time);

    expect(values.filter((time) => !backendTime.test(time))).toEqual([]);
  });

  it('keeps every alert time renderable by the page formatter', () => {
    const rendered = systemAlerts.map((alert) => formatUtcDateTime(alert.time, 'UTC'));

    expect(rendered.filter((value) => value === '-')).toEqual([]);
  });

  it('pins the alert level to the AlertLevel values the backend serializes', () => {
    const levels = [...new Set(systemAlerts.map((alert) => alert.level))];

    expect(levels.filter((level) => !['error', 'warning', 'info'].includes(level))).toEqual([]);
  });
});
