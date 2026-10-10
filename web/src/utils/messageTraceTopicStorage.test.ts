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

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { readMessageTraceTopic, writeMessageTraceTopic } from './messageTraceTopicStorage';

const accountA = { userId: 12, username: 'operator-a' };
const accountB = { userId: 13, username: 'operator-b' };
const anonymous = { userId: null, username: null };

describe('message trace topic storage', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('stores normalized topics independently for each instance', () => {
    writeMessageTraceTopic('instance/a', '  CUSTOM_TRACE  ', accountA);
    writeMessageTraceTopic('instance-b', 'OTHER_TRACE', accountA);

    expect(readMessageTraceTopic('instance/a', accountA)).toBe('CUSTOM_TRACE');
    expect(readMessageTraceTopic('instance-b', accountA)).toBe('OTHER_TRACE');
    expect(readMessageTraceTopic('instance-c', accountA)).toBe('');
  });

  it('does not share a trace topic between browser accounts or anonymous mode', () => {
    writeMessageTraceTopic('instance-a', 'TRACE_A', accountA);
    writeMessageTraceTopic('instance-a', 'TRACE_B', accountB);

    expect(readMessageTraceTopic('instance-a', accountA)).toBe('TRACE_A');
    expect(readMessageTraceTopic('instance-a', accountB)).toBe('TRACE_B');
    expect(readMessageTraceTopic('instance-a', anonymous)).toBe('');
  });

  it('ignores an unscoped legacy value whose owner cannot be identified', () => {
    localStorage.setItem('rocketmq-studio-message-trace-topic:instance-a', 'LEGACY_TRACE');

    expect(readMessageTraceTopic('instance-a', accountA)).toBe('');
    expect(readMessageTraceTopic('instance-a', accountB)).toBe('');
  });

  it('removes blank topics so the provider default is restored', () => {
    writeMessageTraceTopic('instance-a', 'CUSTOM_TRACE', accountA);

    writeMessageTraceTopic('instance-a', '   ', accountA);

    expect(readMessageTraceTopic('instance-a', accountA)).toBe('');
  });

  it('treats denied browser storage as an optional preference', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('storage denied', 'SecurityError');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('storage denied', 'SecurityError');
    });

    expect(() => writeMessageTraceTopic('instance-a', 'CUSTOM_TRACE', accountA)).not.toThrow();
    expect(readMessageTraceTopic('instance-a', accountA)).toBe('');
  });
});
