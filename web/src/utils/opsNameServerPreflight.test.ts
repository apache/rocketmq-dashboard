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
  analyzeOpsNameServerPreflight,
  parseNameServerAddress,
  parseNameServerEndpoint,
} from './opsNameServerPreflight';

describe('parseNameServerEndpoint', () => {
  it('normalizes host and port based NameServer endpoints', () => {
    expect(parseNameServerEndpoint(' NameSrv-A.EXAMPLE.com:9876 ')).toMatchObject({
      normalized: 'namesrv-a.example.com:9876',
      host: 'namesrv-a.example.com',
      port: 9876,
      valid: true,
    });
  });

  it.each([
    ['https://namesrv.example.com:9876', 'NameServer 地址不应包含协议前缀'],
    ['namesrv.example.com', 'NameServer 地址格式应为 host:port'],
    ['namesrv.example.com:70000', 'NameServer 端口必须在 1-65535 之间'],
    ['name srv.example.com:9876', 'NameServer host 只能包含域名、IP 或 localhost'],
    ['2001:db8::1:9876', 'NameServer 地址格式应为 host:port'],
  ])('rejects invalid endpoint %s', (value, reason) => {
    expect(parseNameServerEndpoint(value)).toMatchObject({
      valid: false,
      reason,
    });
  });
});

describe('parseNameServerAddress', () => {
  it('parses a semicolon or comma separated endpoint chain', () => {
    expect(parseNameServerAddress('namesrv-a:9876; namesrv-b:9876,localhost:9876')).toMatchObject({
      normalized: 'namesrv-a:9876;namesrv-b:9876;localhost:9876',
      valid: true,
    });
  });

  it('marks repeated endpoints inside the same address as invalid', () => {
    const result = parseNameServerAddress('namesrv-a:9876;namesrv-a:9876');

    expect(result.valid).toBe(false);
    expect(result.issueText).toContain('同一个地址串内包含重复 endpoint: namesrv-a:9876');
  });
});

describe('analyzeOpsNameServerPreflight', () => {
  const baseInput = {
    namesrvAddrList: ['namesrv-a:9876', 'namesrv-b:9876'],
    currentNamesrv: 'namesrv-a:9876',
    selectedNamesrv: 'namesrv-a:9876',
    newNamesrvAddr: '',
    configurationAvailable: true,
    canWrite: true,
    useVIPChannel: false,
    useTLS: false,
  };

  it('passes healthy NameServer settings without issues', () => {
    const result = analyzeOpsNameServerPreflight(baseInput);

    expect(result.readiness).toBe('ready');
    expect(result.issues).toEqual([]);
    expect(result.stats).toMatchObject({
      configuredAddresses: 2,
      validAddresses: 2,
      invalidAddresses: 0,
      duplicateAddresses: 0,
      endpointCount: 2,
      validEndpointCount: 2,
    });
    expect(result.currentAddressKnown).toBe(true);
    expect(result.selectedAddressValid).toBe(true);
  });

  it('reports invalid and duplicated saved addresses', () => {
    const result = analyzeOpsNameServerPreflight({
      ...baseInput,
      namesrvAddrList: ['namesrv-a:9876', 'namesrv-a:9876', 'bad-address'],
    });

    expect(result.readiness).toBe('warning');
    expect(result.stats.duplicateAddresses).toBe(1);
    expect(result.stats.invalidAddresses).toBe(1);
    expect(result.issues.map((issue) => issue.id)).toEqual(
      expect.arrayContaining(['duplicate-addresses', 'invalid-addresses']),
    );
  });

  it('blocks updates when the current address is missing from the configured list', () => {
    const result = analyzeOpsNameServerPreflight({
      ...baseInput,
      namesrvAddrList: ['namesrv-b:9876'],
      currentNamesrv: 'namesrv-a:9876',
      selectedNamesrv: 'namesrv-b:9876',
    });

    expect(result.readiness).toBe('blocked');
    expect(result.currentAddressKnown).toBe(false);
    expect(result.issues).toContainEqual(
      expect.objectContaining({
        id: 'current-missing',
        severity: 'critical',
      }),
    );
  });

  it('summarizes a valid pending switch and add operation', () => {
    const result = analyzeOpsNameServerPreflight({
      ...baseInput,
      selectedNamesrv: 'namesrv-b:9876',
      newNamesrvAddr: 'namesrv-c:9876',
      useTLS: true,
      useVIPChannel: true,
    });

    expect(result.newAddress).toMatchObject({
      normalized: 'namesrv-c:9876',
      status: 'ready',
    });
    expect(result.pendingChanges.map((change) => change.id)).toEqual([
      'switch-nameserver',
      'add-nameserver',
      'tls-enabled',
      'vip-enabled',
    ]);
  });

  it('rejects a pending duplicate add before it reaches the API', () => {
    const result = analyzeOpsNameServerPreflight({
      ...baseInput,
      newNamesrvAddr: ' NameSrv-A:9876 ',
    });

    expect(result.newAddress).toMatchObject({
      normalized: 'namesrv-a:9876',
      status: 'duplicate',
    });
    expect(result.issues).toContainEqual(
      expect.objectContaining({
        id: 'new-address-duplicate',
        severity: 'warning',
      }),
    );
  });

  it('marks unavailable runtime configuration as blocked and read-only as advisory', () => {
    const result = analyzeOpsNameServerPreflight({
      ...baseInput,
      configurationAvailable: false,
      unavailableReason: 'configuration disabled',
      canWrite: false,
    });

    expect(result.readiness).toBe('blocked');
    expect(result.issues).toContainEqual(
      expect.objectContaining({
        id: 'configuration-unavailable',
        description: 'configuration disabled',
      }),
    );
    expect(result.recommendations).toContain('当前账号只读，预检结果仅供排查参考');
  });
});
