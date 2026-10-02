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
import { mockAuditRecords } from './audit';

/**
 * Operation -> resource type pairs the backend records, with the recording call site in
 * server/src/main/java. Mock mode derives the audit page filter options from these rows, so a row
 * outside this vocabulary offers an operation that no producer can emit.
 */
const backendRecordedOperations: Record<string, string> = {
  // MetadataService.java:170,185,209 with ResourceType.TOPIC
  CREATE_TOPIC: 'TOPIC',
  UPDATE_TOPIC: 'TOPIC',
  DELETE_TOPIC: 'TOPIC',
  // MetadataService.java:493,498,513,544,568 with ResourceType.GROUP
  CREATE_GROUP: 'GROUP',
  UPDATE_GROUP: 'GROUP',
  DELETE_GROUP: 'GROUP',
  RESET_OFFSET: 'GROUP',
  // ProxyAddressService.java:281,289,349 with ResourceType.PROXY
  ADD_PROXY_ADDRESS: 'PROXY',
  REMOVE_PROXY_ADDRESS: 'PROXY',
  RELOAD_PROXY_CONFIG: 'PROXY',
  // ClusterService.java:414
  UPDATE_CLUSTER_CONFIG: 'CLUSTER',
  // RocketMQBrokerConfigService.java:94
  UPDATE_BROKER_CONFIG: 'BROKER',
  // RocketMQDLQProvider.java:735
  RESEND_DLQ: 'DLQ',
  // AclService.java:117,158,172
  CREATE_ACL_RULE: 'ACL_RULE',
  UPDATE_ACL_RULE: 'ACL_RULE',
  DELETE_ACL_RULE: 'ACL_RULE',
};

describe('audit mock contract', () => {
  it('records only operations the backend can produce', () => {
    const unknown = [...new Set(mockAuditRecords.map((record) => record.operationType))].filter(
      (operation) => !(operation in backendRecordedOperations),
    );

    expect(unknown).toEqual([]);
  });

  it('pairs every operation with the resource type the backend uses', () => {
    const mismatched = mockAuditRecords
      .filter((record) => backendRecordedOperations[record.operationType] !== record.resourceType)
      .map((record) => `${record.operationType}/${record.resourceType}`);

    expect(mismatched).toEqual([]);
  });

  it('names ACL rule rows by the rule id the backend records', () => {
    const targets = mockAuditRecords
      .filter((record) => record.resourceType === 'ACL_RULE')
      .map((record) => String(record.target));

    expect(targets.length).toBeGreaterThan(0);
    expect(targets.filter((target) => !/^\d+$/.test(target))).toEqual([]);
  });
});
