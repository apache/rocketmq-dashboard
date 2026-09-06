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
  ALERT_NOTIFICATION_TEMPLATE_VARIABLES,
  buildAlertTemplatePreviewContext,
  createDefaultAlertTemplate,
  previewAlertNotificationTemplate,
} from './alertTemplatePreview';

describe('alert template preview', () => {
  it('renders known variables with stable sample values', () => {
    const preview = previewAlertNotificationTemplate(
      '[${level}] ${ruleName}: ${metric}=${value} > ${threshold}${thresholdUnit}',
      {
        ruleName: 'Consumer lag high',
        metric: 'consumer.lag.total',
        value: 1200,
        threshold: 1000,
        thresholdUnit: 'messages',
        level: 'WARNING',
      },
    );

    expect(preview.status).toBe('ready');
    expect(preview.rendered).toBe(
      '[WARNING] Consumer lag high: consumer.lag.total=1200 > 1000messages',
    );
    expect(preview.usedVariables).toEqual([
      'level',
      'metric',
      'ruleName',
      'threshold',
      'thresholdUnit',
      'value',
    ]);
    expect(preview.unknownVariables).toEqual([]);
    expect(preview.tokens.filter((token) => token.type === 'variable')).toHaveLength(6);
  });

  it('keeps unknown placeholders visible and reports them', () => {
    const preview = previewAlertNotificationTemplate(
      'Alert ${ruleName} owner=${owner} zone=${zone}',
      { ruleName: 'Broker unavailable' },
    );

    expect(preview.status).toBe('attention');
    expect(preview.rendered).toBe('Alert Broker unavailable owner=${owner} zone=${zone}');
    expect(preview.unknownVariables).toEqual(['owner', 'zone']);
    expect(preview.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'UNKNOWN_VARIABLE',
          severity: 'warning',
          variables: ['owner', 'zone'],
        }),
      ]),
    );
  });

  it('reports static templates without dynamic variables', () => {
    const preview = previewAlertNotificationTemplate('Static alert body');

    expect(preview.status).toBe('attention');
    expect(preview.rendered).toBe('Static alert body');
    expect(preview.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'NO_DYNAMIC_VARIABLE',
          severity: 'warning',
        }),
      ]),
    );
  });

  it('distinguishes an empty custom template from a static template', () => {
    const preview = previewAlertNotificationTemplate('   ');

    expect(preview.status).toBe('ready');
    expect(preview.rendered).toBe('   ');
    expect(preview.issues).toEqual([
      expect.objectContaining({
        code: 'EMPTY_TEMPLATE',
        severity: 'info',
      }),
    ]);
  });

  it('formats label maps in deterministic key order', () => {
    const context = buildAlertTemplatePreviewContext({
      labels: {
        topic: 'orders',
        empty: '',
        broker: 'broker-a',
        cluster: 'DefaultCluster',
        ignored: null,
      },
    });

    expect(context.labels).toBe('broker=broker-a, cluster=DefaultCluster, topic=orders');
  });

  it('accepts a preformatted labels string', () => {
    const preview = previewAlertNotificationTemplate('Labels: ${labels}', {
      labels: 'broker=broker-a, queue=1',
    });

    expect(preview.rendered).toBe('Labels: broker=broker-a, queue=1');
  });

  it('reports missing sample values without failing the preview', () => {
    const preview = previewAlertNotificationTemplate('Instance=${instanceId}', {
      instanceId: '',
    });

    expect(preview.rendered).toBe('Instance=');
    expect(preview.missingVariables).toEqual(['instanceId']);
    expect(preview.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'MISSING_VALUE',
          severity: 'info',
          variables: ['instanceId'],
        }),
      ]),
    );
  });

  it('reports templates that exceed the configured length', () => {
    const preview = previewAlertNotificationTemplate(
      '${title}'.padEnd(12, 'x'),
      {},
      { maxLength: 8 },
    );

    expect(preview.status).toBe('attention');
    expect(preview.length).toBe(12);
    expect(preview.maxLength).toBe(8);
    expect(preview.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'LENGTH_LIMIT',
          severity: 'warning',
        }),
      ]),
    );
  });

  it('creates a default template that only references documented variables', () => {
    const template = createDefaultAlertTemplate();
    const preview = previewAlertNotificationTemplate(template);

    expect(preview.status).toBe('ready');
    expect(preview.unknownVariables).toEqual([]);
    expect(
      preview.usedVariables.every((variable) =>
        ALERT_NOTIFICATION_TEMPLATE_VARIABLES.includes(
          variable as (typeof ALERT_NOTIFICATION_TEMPLATE_VARIABLES)[number],
        ),
      ),
    ).toBe(true);
    expect(preview.rendered).toContain('Broker disk usage');
    expect(preview.rendered).toContain('broker=broker-a');
  });

  it('formats Date and numeric time context as ISO strings', () => {
    const fromDate = previewAlertNotificationTemplate('At ${time} alert fired', {
      time: new Date('2026-09-03T10:00:00.000Z'),
    });
    const fromMillis = previewAlertNotificationTemplate('At ${time} alert fired', {
      time: new Date('2026-09-03T10:00:00.000Z').getTime(),
    });

    expect(fromDate.rendered).toContain('2026-09-03T10:00:00.000Z');
    expect(fromMillis.rendered).toBe(fromDate.rendered);
  });

  it('trims whitespace inside placeholders before matching variables', () => {
    const preview = previewAlertNotificationTemplate('[${ level }] ${ ruleName } fired', {
      level: 'CRITICAL',
      ruleName: 'Broker down',
    });

    expect(preview.status).toBe('ready');
    expect(preview.usedVariables).toEqual(['level', 'ruleName']);
    expect(preview.unknownVariables).toEqual([]);
    expect(preview.rendered).toBe('[CRITICAL] Broker down fired');
  });

  it('reports context variables that the template never uses', () => {
    const preview = previewAlertNotificationTemplate('${title} only', {
      title: 'Disk usage firing',
      description: 'detailed description',
    });

    expect(preview.rendered).toContain('Disk usage firing');
    expect(preview.unusedContextVariables).toContain('description');
    expect(preview.unusedContextVariables).not.toContain('title');
  });

  it('deduplicates variables that appear more than once', () => {
    const preview = previewAlertNotificationTemplate(
      '${level}: ${title} (${level})',
      { level: 'CRITICAL', title: 'Broker down' },
    );

    expect(preview.usedVariables).toEqual(['level', 'title']);
    expect(preview.rendered).toBe('CRITICAL: Broker down (CRITICAL)');
  });
});
