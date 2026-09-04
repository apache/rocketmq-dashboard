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

export const ALERT_NOTIFICATION_TEMPLATE_VARIABLES = [
  'ruleName',
  'title',
  'description',
  'transition',
  'metric',
  'instanceId',
  'value',
  'threshold',
  'thresholdUnit',
  'level',
  'time',
  'labels',
] as const;

export type AlertNotificationTemplateVariable =
  (typeof ALERT_NOTIFICATION_TEMPLATE_VARIABLES)[number];

export type AlertTemplatePreviewStatus = 'ready' | 'attention';

export type AlertTemplatePreviewIssueCode =
  'EMPTY_TEMPLATE' | 'UNKNOWN_VARIABLE' | 'MISSING_VALUE' | 'LENGTH_LIMIT' | 'NO_DYNAMIC_VARIABLE';

export interface AlertTemplatePreviewIssue {
  code: AlertTemplatePreviewIssueCode;
  severity: 'info' | 'warning';
  message: string;
  variables?: string[];
}

export interface AlertTemplatePreviewContext {
  ruleName?: string | null;
  title?: string | null;
  description?: string | null;
  transition?: string | null;
  metric?: string | null;
  instanceId?: string | null;
  value?: string | number | null;
  threshold?: string | number | null;
  thresholdUnit?: string | null;
  level?: string | null;
  time?: string | number | Date | null;
  labels?: Record<string, string | number | boolean | null | undefined> | string | null;
}

export interface AlertTemplatePreviewToken {
  type: 'text' | 'variable';
  text: string;
  variable?: string;
  known?: boolean;
  resolved?: string;
}

export interface AlertTemplatePreview {
  template: string;
  rendered: string;
  tokens: AlertTemplatePreviewToken[];
  status: AlertTemplatePreviewStatus;
  usedVariables: string[];
  unknownVariables: string[];
  missingVariables: string[];
  unusedContextVariables: string[];
  issues: AlertTemplatePreviewIssue[];
  length: number;
  maxLength: number;
}

const VARIABLE_PATTERN = /\$\{([^{}]+)\}/g;
const DEFAULT_MAX_LENGTH = 4000;
const knownVariables = new Set<string>(ALERT_NOTIFICATION_TEMPLATE_VARIABLES);

const defaultContext: Required<AlertTemplatePreviewContext> = {
  ruleName: 'Broker disk usage',
  title: 'Broker disk usage is firing',
  description: 'Broker broker-a disk usage is above the configured threshold.',
  transition: 'FIRING',
  metric: 'broker.disk.usage_ratio',
  instanceId: 'rocketmq-prod',
  value: '91%',
  threshold: '85',
  thresholdUnit: '%',
  level: 'CRITICAL',
  time: '2026-09-03 10:00:00',
  labels: {
    broker: 'broker-a',
    cluster: 'DefaultCluster',
    instanceId: 'rocketmq-prod',
  },
};

const uniqueSorted = (values: string[]) =>
  Array.from(new Set(values.filter(Boolean))).sort((left, right) => left.localeCompare(right));

const normalizeTemplate = (template?: string | null) => template ?? '';

const isEmptyValue = (value: unknown) =>
  value === undefined || value === null || (typeof value === 'string' && value.trim() === '');

const stringifyTime = (value: AlertTemplatePreviewContext['time']) => {
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? '' : value.toISOString();
  }
  if (typeof value === 'number') {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '' : date.toISOString();
  }
  return value == null ? '' : String(value);
};

const stringifyLabels = (labels: AlertTemplatePreviewContext['labels']) => {
  if (labels == null) return '';
  if (typeof labels === 'string') return labels;

  return Object.entries(labels)
    .filter(([, value]) => !isEmptyValue(value))
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => `${key}=${String(value)}`)
    .join(', ');
};

export const buildAlertTemplatePreviewContext = (
  context: AlertTemplatePreviewContext = {},
): Record<AlertNotificationTemplateVariable, string> => ({
  ruleName: String(context.ruleName ?? defaultContext.ruleName),
  title: String(context.title ?? defaultContext.title),
  description: String(context.description ?? defaultContext.description),
  transition: String(context.transition ?? defaultContext.transition),
  metric: String(context.metric ?? defaultContext.metric),
  instanceId: String(context.instanceId ?? defaultContext.instanceId),
  value: String(context.value ?? defaultContext.value),
  threshold: String(context.threshold ?? defaultContext.threshold),
  thresholdUnit: String(context.thresholdUnit ?? defaultContext.thresholdUnit),
  level: String(context.level ?? defaultContext.level),
  time: stringifyTime(context.time ?? defaultContext.time),
  labels: stringifyLabels(context.labels ?? defaultContext.labels),
});

const appendTextToken = (tokens: AlertTemplatePreviewToken[], text: string) => {
  if (!text) return;
  const previous = tokens[tokens.length - 1];
  if (previous?.type === 'text') {
    previous.text += text;
    return;
  }
  tokens.push({ type: 'text', text });
};

const parseTemplate = (
  template: string,
  values: Record<AlertNotificationTemplateVariable, string>,
) => {
  const tokens: AlertTemplatePreviewToken[] = [];
  const usedVariables: string[] = [];
  const unknownVariables: string[] = [];
  const missingVariables: string[] = [];
  let rendered = '';
  let cursor = 0;
  let match: RegExpExecArray | null;

  VARIABLE_PATTERN.lastIndex = 0;
  while ((match = VARIABLE_PATTERN.exec(template)) !== null) {
    const [placeholder, rawVariable] = match;
    const variable = rawVariable.trim();
    appendTextToken(tokens, template.slice(cursor, match.index));

    if (!knownVariables.has(variable)) {
      unknownVariables.push(variable);
      tokens.push({
        type: 'variable',
        text: placeholder,
        variable,
        known: false,
        resolved: placeholder,
      });
      rendered += template.slice(cursor, match.index) + placeholder;
    } else {
      const value = values[variable as AlertNotificationTemplateVariable] ?? '';
      usedVariables.push(variable);
      if (!value.trim()) {
        missingVariables.push(variable);
      }
      tokens.push({
        type: 'variable',
        text: placeholder,
        variable,
        known: true,
        resolved: value,
      });
      rendered += template.slice(cursor, match.index) + value;
    }
    cursor = match.index + placeholder.length;
  }

  appendTextToken(tokens, template.slice(cursor));
  rendered += template.slice(cursor);

  return {
    rendered,
    tokens,
    usedVariables: uniqueSorted(usedVariables),
    unknownVariables: uniqueSorted(unknownVariables),
    missingVariables: uniqueSorted(missingVariables),
  };
};

const collectUnusedContextVariables = (usedVariables: string[], values: Record<string, string>) => {
  const used = new Set(usedVariables);
  return ALERT_NOTIFICATION_TEMPLATE_VARIABLES.filter(
    (variable) => !used.has(variable) && values[variable]?.trim(),
  );
};

export const createDefaultAlertTemplate = () =>
  [
    '[${level}] ${title}',
    'Rule: ${ruleName}',
    'Metric: ${metric}, value=${value}, threshold=${threshold}${thresholdUnit}',
    'Instance: ${instanceId}',
    'Labels: ${labels}',
    'Time: ${time}',
    '${description}',
  ].join('\n');

export function previewAlertNotificationTemplate(
  templateInput?: string | null,
  context: AlertTemplatePreviewContext = {},
  options: { maxLength?: number } = {},
): AlertTemplatePreview {
  const template = normalizeTemplate(templateInput);
  const maxLength = options.maxLength ?? DEFAULT_MAX_LENGTH;
  const values = buildAlertTemplatePreviewContext(context);
  const parsed = parseTemplate(template, values);
  const unusedContextVariables = collectUnusedContextVariables(parsed.usedVariables, values);
  const issues: AlertTemplatePreviewIssue[] = [];

  if (!template.trim()) {
    issues.push({
      code: 'EMPTY_TEMPLATE',
      severity: 'info',
      message: 'No custom notification template is configured.',
    });
  }
  if (parsed.usedVariables.length === 0 && template.trim()) {
    issues.push({
      code: 'NO_DYNAMIC_VARIABLE',
      severity: 'warning',
      message: 'The template does not include any dynamic variables.',
    });
  }
  if (parsed.unknownVariables.length > 0) {
    issues.push({
      code: 'UNKNOWN_VARIABLE',
      severity: 'warning',
      message: `Unknown variables: ${parsed.unknownVariables.join(', ')}`,
      variables: parsed.unknownVariables,
    });
  }
  if (parsed.missingVariables.length > 0) {
    issues.push({
      code: 'MISSING_VALUE',
      severity: 'info',
      message: `Variables without sample values: ${parsed.missingVariables.join(', ')}`,
      variables: parsed.missingVariables,
    });
  }
  if (template.length > maxLength) {
    issues.push({
      code: 'LENGTH_LIMIT',
      severity: 'warning',
      message: `Template length ${template.length} exceeds the ${maxLength} character limit.`,
    });
  }
  return {
    template,
    rendered: parsed.rendered,
    tokens: parsed.tokens,
    status: issues.some((issue) => issue.severity === 'warning') ? 'attention' : 'ready',
    usedVariables: parsed.usedVariables,
    unknownVariables: parsed.unknownVariables,
    missingVariables: parsed.missingVariables,
    unusedContextVariables,
    issues,
    length: template.length,
    maxLength,
  };
}
