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
import { buildAlertRuleBackupParts, ALERT_RULE_BACKUP_PART_LIMIT } from './alertRuleBackup';

const transfer = (count: number) => ({
  version: 1,
  domain: 'CLUSTER' as const,
  rules: Array.from({ length: count }, (_, index) => ({
    name: `Rule ${index}`,
    metric: 'broker.availability',
    operator: '<',
    threshold: 1,
    duration: '1m',
    channels: ['email'],
    enabled: true,
    description: 'Keep all portable fields',
    aggregation: 'AVG' as const,
    windowSeconds: 60,
    consecutiveSamples: 2,
    reminderInterval: '30m',
    notificationTemplate: '${title}: ${value}',
    severity: 'warning',
    clusterName: 'cluster-a',
    brokerName: 'broker-a',
    instanceId: 'instance-a',
  })),
});

describe('alert rule backup parts', () => {
  it.each([0, 1, 200, 201, 400, 401])(
    'preserves every rule in bounded version-1 documents (count=%s)',
    (count) => {
      const original = transfer(count);
      const before = JSON.stringify(original);
      Object.freeze(original.rules);
      const parts = buildAlertRuleBackupParts(original);
      expect(parts).toHaveLength(Math.max(1, Math.ceil(count / ALERT_RULE_BACKUP_PART_LIMIT)));
      expect(parts.flatMap((part) => part.document.rules)).toEqual(original.rules);
      expect(
        parts.every((part) => part.document.version === 1 && part.document.domain === 'CLUSTER'),
      ).toBe(true);
      expect(parts.every((part) => part.document.rules.length <= 200)).toBe(true);
      expect(new Set(parts.map((part) => part.filename)).size).toBe(parts.length);
      expect(JSON.stringify(original)).toBe(before);
      expect(parts.every((part) => part.document.rules !== original.rules)).toBe(true);
      if (count <= 200) expect(parts[0].filename).toBe('rocketmq-studio-cluster-alert-rules.json');
      else {
        expect(parts[0].filename).toBe(
          `rocketmq-studio-cluster-alert-rules-part-1-of-${parts.length}.json`,
        );
        expect(parts[parts.length - 1].filename).toBe(
          `rocketmq-studio-cluster-alert-rules-part-${parts.length}-of-${parts.length}.json`,
        );
      }
    },
  );

  it('uses the captured document domain for every filename and import envelope', () => {
    const original = { ...transfer(201), domain: 'BUSINESS' as const };
    const parts = buildAlertRuleBackupParts(original);
    expect(parts.map((part) => part.document.rules.length)).toEqual([200, 1]);
    expect(
      parts.every(
        (part) => part.filename.includes('-business-') && part.document.domain === 'BUSINESS',
      ),
    ).toBe(true);
  });
});
