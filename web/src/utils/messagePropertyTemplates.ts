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

import type { MessagePropertyInput } from './messagePayloadPreview';
import { parseMessageProperties } from './messageProperties';

export type MessagePropertyTemplateId =
  'trace-context' | 'tenant-routing' | 'retry-audit' | 'order-diagnostics';

export interface MessagePropertyTemplateEntry {
  key: string;
  value: string;
  description: string;
}

export interface MessagePropertyTemplate {
  id: MessagePropertyTemplateId;
  label: string;
  description: string;
  entries: MessagePropertyTemplateEntry[];
}

export interface MessagePropertyTemplateApplyResult {
  rows: MessagePropertyInput[];
  appliedKeys: string[];
  skippedKeys: string[];
}

export const messagePropertyTemplates: MessagePropertyTemplate[] = [
  {
    id: 'trace-context',
    label: 'Trace context',
    description: 'Correlate a produced test message with upstream tracing or log search.',
    entries: [
      {
        key: 'traceId',
        value: 'trace-${timestamp}',
        description: 'Cross-service trace identifier used in logs and dashboards.',
      },
      {
        key: 'spanId',
        value: 'studio-send',
        description: 'Producer-side span label for the manual Studio send action.',
      },
      {
        key: 'source',
        value: 'rocketmq-studio',
        description: 'Identifies the manual producer used to create the message.',
      },
    ],
  },
  {
    id: 'tenant-routing',
    label: 'Tenant routing',
    description: 'Carry tenant and region metadata for multi-tenant routing smoke tests.',
    entries: [
      {
        key: 'tenantId',
        value: 'tenant-demo',
        description: 'Logical tenant identifier used by consumers or stream processors.',
      },
      {
        key: 'region',
        value: 'cn-hangzhou',
        description: 'Region hint used by cross-region consumer or routing rules.',
      },
      {
        key: 'environment',
        value: 'staging',
        description: 'Environment marker that keeps test traffic separate from production data.',
      },
    ],
  },
  {
    id: 'retry-audit',
    label: 'Retry audit',
    description: 'Annotate a manual retry or backfill message with operator context.',
    entries: [
      {
        key: 'retryReason',
        value: 'manual-replay',
        description: 'Reason a message is being replayed or reconstructed.',
      },
      {
        key: 'operator',
        value: 'rocketmq-studio',
        description: 'Actor that initiated the manual send action.',
      },
      {
        key: 'requestedAt',
        value: '${isoTimestamp}',
        description: 'ISO timestamp captured when the template is applied.',
      },
    ],
  },
  {
    id: 'order-diagnostics',
    label: 'Order diagnostics',
    description: 'Add stable business keys for order-flow verification messages.',
    entries: [
      {
        key: 'orderId',
        value: 'order-${timestamp}',
        description: 'Business order id that can be searched across systems.',
      },
      {
        key: 'businessType',
        value: 'order-created',
        description: 'Business event type represented by the produced message.',
      },
      {
        key: 'shardHint',
        value: 'order',
        description: 'Optional consumer-side shard or grouping hint for diagnostics.',
      },
    ],
  },
];

export const messagePropertyTemplateOptions = messagePropertyTemplates.map((template) => ({
  label: template.label,
  value: template.id,
}));

export const findMessagePropertyTemplate = (
  id?: MessagePropertyTemplateId,
): MessagePropertyTemplate | undefined =>
  id ? messagePropertyTemplates.find((template) => template.id === id) : undefined;

const timestampTokens = (now: Date) => ({
  timestamp: String(now.getTime()),
  isoTimestamp: now.toISOString(),
});

const expandTemplateValue = (value: string, now: Date): string => {
  const tokens = timestampTokens(now);
  return value.replace(/\$\{timestamp\}|\$\{isoTimestamp\}/g, (token) => {
    if (token === '${timestamp}') return tokens.timestamp;
    if (token === '${isoTimestamp}') return tokens.isoTimestamp;
    return token;
  });
};

const normalizePropertyRows = (rows: MessagePropertyInput[] = []): MessagePropertyInput[] =>
  rows
    .filter((row) => row && ((row.key ?? '').trim() || (row.value ?? '').trim()))
    .map((row) => ({
      key: row.key ?? '',
      value: row.value ?? '',
    }));

const existingKeys = (rows: MessagePropertyInput[]): Set<string> =>
  new Set(rows.map((row) => row.key?.trim()).filter((key): key is string => Boolean(key)));

export const applyMessagePropertyTemplate = (
  rows: MessagePropertyInput[] | undefined,
  templateId: MessagePropertyTemplateId,
  now = new Date(),
): MessagePropertyTemplateApplyResult => {
  const template = findMessagePropertyTemplate(templateId);
  const normalizedRows = normalizePropertyRows(rows);
  if (!template) {
    return { rows: normalizedRows, appliedKeys: [], skippedKeys: [] };
  }

  const keys = existingKeys(normalizedRows);
  const appliedKeys: string[] = [];
  const skippedKeys: string[] = [];
  const additions: MessagePropertyInput[] = [];

  for (const entry of template.entries) {
    if (keys.has(entry.key)) {
      skippedKeys.push(entry.key);
      continue;
    }
    additions.push({
      key: entry.key,
      value: expandTemplateValue(entry.value, now),
    });
    appliedKeys.push(entry.key);
  }

  return {
    rows: [...normalizedRows, ...additions],
    appliedKeys,
    skippedKeys,
  };
};

const parseExistingTextKeys = (text: string): Set<string> => {
  const parsed = parseMessageProperties(text);
  return new Set(Object.keys(parsed.properties));
};

export const templateToPropertyText = (
  templateId: MessagePropertyTemplateId,
  now = new Date(),
): string => {
  const template = findMessagePropertyTemplate(templateId);
  if (!template) return '';
  return template.entries
    .map((entry) => `${entry.key}=${expandTemplateValue(entry.value, now)}`)
    .join('\n');
};

export const appendMessagePropertyTemplateText = (
  text: string | undefined,
  templateId: MessagePropertyTemplateId,
  now = new Date(),
): { text: string; appliedKeys: string[]; skippedKeys: string[] } => {
  const template = findMessagePropertyTemplate(templateId);
  const currentText = text ?? '';
  if (!template) return { text: currentText, appliedKeys: [], skippedKeys: [] };

  const keys = parseExistingTextKeys(currentText);
  const lines: string[] = [];
  const appliedKeys: string[] = [];
  const skippedKeys: string[] = [];

  for (const entry of template.entries) {
    if (keys.has(entry.key)) {
      skippedKeys.push(entry.key);
      continue;
    }
    lines.push(`${entry.key}=${expandTemplateValue(entry.value, now)}`);
    appliedKeys.push(entry.key);
  }

  const trimmed = currentText.trim();
  const suffix = lines.join('\n');
  return {
    text: [trimmed, suffix].filter(Boolean).join('\n'),
    appliedKeys,
    skippedKeys,
  };
};
