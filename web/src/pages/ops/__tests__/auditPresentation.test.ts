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
import type { AuditRecord } from '../../../api/ops';
import {
  describeAuditRecord,
  formatAuditCode,
  getAuditOperationPresentation,
  getAuditResourcePresentation,
  getAuditResultPresentation,
  isControlPlaneAuditRecord,
  normalizeAuditCode,
  parseAuditDetail,
} from '../auditPresentation';

describe('audit presentation helpers', () => {
  it('normalizes audit codes before presentation lookup', () => {
    expect(normalizeAuditCode(' add_proxy_address ')).toBe('ADD_PROXY_ADDRESS');
    expect(formatAuditCode(' reload_proxy_config ')).toBe('Reload Proxy Config');
    expect(formatAuditCode('')).toBe('-');
  });

  it('presents proxy control-plane operations with stable labels and colors', () => {
    expect(getAuditOperationPresentation('ADD_PROXY_ADDRESS')).toEqual({
      label: 'Add Proxy Address',
      color: 'geekblue',
      category: 'proxy',
      labelKey: 'audit.op.ADD_PROXY_ADDRESS',
    });
    expect(getAuditOperationPresentation('REMOVE_PROXY_ADDRESS')).toEqual({
      labelKey: 'audit.op.REMOVE_PROXY_ADDRESS',
      label: 'Remove Proxy Address',
      color: 'volcano',
      category: 'proxy',
    });
    expect(getAuditOperationPresentation('RELOAD_PROXY_CONFIG')).toEqual({
      labelKey: 'audit.op.RELOAD_PROXY_CONFIG',
      label: 'Reload Proxy Config',
      color: 'purple',
      category: 'proxy',
    });
  });

  it('presents metadata operations without losing the original audit code', () => {
    expect(getAuditOperationPresentation('CREATE_TOPIC')).toMatchObject({
      label: 'Create Topic',
      category: 'metadata',
    });
    expect(getAuditOperationPresentation('RESET_OFFSET')).toMatchObject({
      label: 'Reset Offset',
      category: 'metadata',
    });
  });

  it('falls back to title-cased labels for new operation codes', () => {
    expect(getAuditOperationPresentation('UPSERT_NEW_RESOURCE')).toEqual({
      label: 'Upsert New Resource',
      color: 'default',
      category: 'other',
    });
  });

  it('maps resource and result codes to readable table labels', () => {
    expect(getAuditResourcePresentation('PROXY')).toEqual({
      label: 'Proxy',
      color: 'purple',
      labelKey: 'audit.res.PROXY',
    });
    expect(getAuditResourcePresentation('GROUP')).toEqual({
      label: 'Consumer Group',
      color: 'geekblue',
      labelKey: 'audit.res.GROUP',
    });
    expect(getAuditResourcePresentation('CONSUMER_GROUP')).toEqual({
      label: 'Consumer Group',
      color: 'geekblue',
      labelKey: 'audit.res.CONSUMER_GROUP',
    });
    expect(getAuditResultPresentation('SUCCESS')).toEqual({
      label: 'Success',
      color: 'green',
      labelKey: 'audit.result.SUCCESS',
    });
    expect(getAuditResultPresentation('FAILED')).toEqual({
      label: 'Failed',
      color: 'red',
      labelKey: 'audit.result.FAILED',
    });
  });

  it('parses key-value audit details from service-boundary metadata records', () => {
    expect(parseAuditDetail('type=NORMAL, writeQueues=16, readQueues=16, perm=6')).toEqual([
      { label: 'type', value: 'NORMAL' },
      { label: 'writeQueues', value: '16' },
      { label: 'readQueues', value: '16' },
      { label: 'perm', value: '6' },
    ]);
    expect(parseAuditDetail('topic=orders, timestamp=1784246400000')).toEqual([
      { label: 'topic', value: 'orders' },
      { label: 'timestamp', value: '1784246400000' },
    ]);
  });

  it('keeps free-form details intact when they are not key-value lists', () => {
    expect(parseAuditDetail('Removed stale Proxy address 10.0.30.9:8081')).toEqual([
      { label: '', value: 'Removed stale Proxy address 10.0.30.9:8081' },
    ]);
    expect(parseAuditDetail('created topic, owner missing')).toEqual([
      { label: '', value: 'created topic, owner missing' },
    ]);
    expect(parseAuditDetail(null)).toEqual([]);
  });

  it('describes records with operation, resource, target, and cluster context', () => {
    const record = {
      operationType: 'RELOAD_PROXY_CONFIG',
      resourceType: 'PROXY',
      target: '10.0.30.10:8081',
      clusterId: 'prod-cn',
    } as AuditRecord;

    expect(describeAuditRecord(record)).toBe(
      'Reload Proxy Config Proxy 10.0.30.10:8081 in prod-cn',
    );
  });

  it('identifies metadata, proxy, and cluster actions as control-plane records', () => {
    expect(isControlPlaneAuditRecord({ operationType: 'CREATE_TOPIC' })).toBe(true);
    expect(isControlPlaneAuditRecord({ operationType: 'RELOAD_PROXY_CONFIG' })).toBe(true);
    expect(isControlPlaneAuditRecord({ operationType: 'UPDATE_BROKER_CONFIG' })).toBe(true);
    expect(isControlPlaneAuditRecord({ operationType: 'SEND_MESSAGE' })).toBe(false);
  });
});
