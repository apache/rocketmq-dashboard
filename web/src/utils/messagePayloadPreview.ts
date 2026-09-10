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

import { parseMessageProperties } from './messageProperties';

export type MessagePropertyMode = 'form' | 'text';
export type MessagePayloadPreviewStatus = 'ready' | 'warning' | 'error';
export type MessagePayloadIssueSeverity = 'info' | 'warning' | 'error';
export type MessageBodyFormat =
  'empty' | 'json-object' | 'json-array' | 'json-scalar' | 'plain-text';

export type MessagePayloadIssueCode =
  | 'EMPTY_BODY'
  | 'BODY_SIZE_LIMIT'
  | 'INVALID_PROPERTY_FORMAT'
  | 'EMPTY_PROPERTY_KEY'
  | 'DUPLICATE_PROPERTY_KEY'
  | 'RESERVED_PROPERTY_KEY'
  | 'EMPTY_PROPERTY_VALUE'
  | 'PROPERTY_COUNT_LIMIT'
  | 'PROPERTY_SIZE_LIMIT'
  | 'TRIMMED_TAG'
  | 'TRIMMED_KEY'
  | 'PLAIN_TEXT_BODY'
  | 'SCALAR_JSON_BODY';

export interface MessagePropertyInput {
  key?: string;
  value?: string;
}

export interface MessagePayloadIssue {
  code: MessagePayloadIssueCode;
  severity: MessagePayloadIssueSeverity;
  title: string;
  description: string;
  field?: 'body' | 'tag' | 'key' | 'properties';
  names?: string[];
}

export interface MessagePayloadPreviewInput {
  topic?: string;
  tag?: string;
  key?: string;
  body?: string;
  propsMode: MessagePropertyMode;
  propsText?: string;
  properties?: MessagePropertyInput[];
}

export interface MessagePayloadPreviewOptions {
  maxBodyBytes?: number;
  maxProperties?: number;
  maxPropertyBytes?: number;
  lang?: 'zh' | 'en';
}

export interface MessagePropertyPreviewEntry {
  key: string;
  value: string;
  keyBytes: number;
  valueBytes: number;
  reserved: boolean;
}

export interface MessagePayloadPreview {
  status: MessagePayloadPreviewStatus;
  issues: MessagePayloadIssue[];
  blockingIssues: MessagePayloadIssue[];
  normalized: {
    topic: string;
    tag?: string;
    key?: string;
    body: string;
  };
  properties: Record<string, string>;
  propertyEntries: MessagePropertyPreviewEntry[];
  summary: {
    bodyBytes: number;
    tagBytes: number;
    keyBytes: number;
    propertyCount: number;
    propertyBytes: number;
    bodyFormat: MessageBodyFormat;
    maxBodyBytes: number;
    maxProperties: number;
    maxPropertyBytes: number;
  };
}

export const DEFAULT_MAX_MESSAGE_BODY_BYTES = 4 * 1024 * 1024;
export const DEFAULT_MAX_MESSAGE_PROPERTIES = 32;
export const DEFAULT_MAX_MESSAGE_PROPERTY_BYTES = 16 * 1024;

const RESERVED_PROPERTY_NAMES = new Set([
  'TAGS',
  'KEYS',
  'UNIQ_KEY',
  'WAIT',
  'DELAY',
  'RETRY_TOPIC',
  'REAL_TOPIC',
  'REAL_QID',
  'TRAN_MSG',
  'PGROUP',
]);

const textBytes = (value?: string): number => new TextEncoder().encode(value ?? '').length;

const normalizeText = (value?: string): string => value?.trim() ?? '';

const issue = (
  code: MessagePayloadIssueCode,
  severity: MessagePayloadIssueSeverity,
  title: string,
  description: string,
  field?: MessagePayloadIssue['field'],
  names?: string[],
): MessagePayloadIssue => ({
  code,
  severity,
  title,
  description,
  field,
  names,
});

const bodyFormat = (body: string): MessageBodyFormat => {
  const trimmed = body.trim();
  if (!trimmed) return 'empty';

  try {
    const parsed = JSON.parse(trimmed) as unknown;
    if (Array.isArray(parsed)) return 'json-array';
    if (parsed !== null && typeof parsed === 'object') return 'json-object';
    return 'json-scalar';
  } catch {
    return 'plain-text';
  }
};

const fromEntries = (entries: MessagePropertyPreviewEntry[]): Record<string, string> =>
  Object.fromEntries(entries.map((entry) => [entry.key, entry.value]));

export const isReservedMessageProperty = (key: string): boolean =>
  RESERVED_PROPERTY_NAMES.has(key.trim().toUpperCase());

export const buildMessagePropertiesFromRows = (
  rows: MessagePropertyInput[] = [],
  lang: 'zh' | 'en' = 'zh',
): { entries: MessagePropertyPreviewEntry[]; issues: MessagePayloadIssue[] } => {
  const entries: MessagePropertyPreviewEntry[] = [];
  const issues: MessagePayloadIssue[] = [];
  const seen = new Map<string, string>();
  const duplicates = new Set<string>();

  rows.forEach((row) => {
    if (!row) return;
    const rawKey = row.key ?? '';
    const rawValue = row.value ?? '';
    const key = rawKey.trim();
    const value = rawValue.trim();

    if (!key && !value) return;
    if (!key) {
      issues.push(
        issue(
          'EMPTY_PROPERTY_KEY',
          'error',
          lang === 'en' ? 'Empty property name' : '属性名不能为空',
          lang === 'en'
            ? `Property value "${value}" has no property name.`
            : `属性值“${value}”缺少对应属性名。`,
          'properties',
        ),
      );
      return;
    }

    if (seen.has(key)) {
      duplicates.add(key);
      return;
    }

    seen.set(key, value);
    entries.push({
      key,
      value,
      keyBytes: textBytes(key),
      valueBytes: textBytes(value),
      reserved: isReservedMessageProperty(key),
    });
  });

  if (duplicates.size > 0) {
    issues.push(
      issue(
        'DUPLICATE_PROPERTY_KEY',
        'error',
        lang === 'en' ? 'Duplicate property name' : '属性名重复',
        lang === 'en'
          ? `Duplicate keys will override earlier values: ${[...duplicates].sort().join(', ')}`
          : `重复属性会覆盖前面的值：${[...duplicates].sort().join(', ')}`,
        'properties',
        [...duplicates].sort(),
      ),
    );
  }

  return { entries, issues };
};

const buildMessagePropertiesFromText = (
  text: string,
  lang: 'zh' | 'en' = 'zh',
): { entries: MessagePropertyPreviewEntry[]; issues: MessagePayloadIssue[] } => {
  const parsed = parseMessageProperties(text, lang);
  const entries = Object.entries(parsed.properties).map(([key, value]) => ({
    key,
    value,
    keyBytes: textBytes(key),
    valueBytes: textBytes(value),
    reserved: isReservedMessageProperty(key),
  }));

  return {
    entries,
    issues: parsed.errors.map((errorText) =>
      issue(
        'INVALID_PROPERTY_FORMAT',
        'error',
        lang === 'en' ? 'Invalid property format' : '属性格式错误',
        errorText,
        'properties',
      ),
    ),
  };
};

export const analyzeMessagePayloadPreview = (
  input: MessagePayloadPreviewInput,
  options: MessagePayloadPreviewOptions = {},
): MessagePayloadPreview => {
  const lang = options.lang ?? 'zh';
  const pick = (zh: string, en: string): string => (lang === 'en' ? en : zh);
  const maxBodyBytes = options.maxBodyBytes ?? DEFAULT_MAX_MESSAGE_BODY_BYTES;
  const maxProperties = options.maxProperties ?? DEFAULT_MAX_MESSAGE_PROPERTIES;
  const maxPropertyBytes = options.maxPropertyBytes ?? DEFAULT_MAX_MESSAGE_PROPERTY_BYTES;
  const topic = normalizeText(input.topic);
  const tag = normalizeText(input.tag);
  const key = normalizeText(input.key);
  const body = input.body ?? '';
  const normalizedBody = body;
  const issues: MessagePayloadIssue[] = [];

  if (input.tag && input.tag !== tag) {
    issues.push(
      issue(
        'TRIMMED_TAG',
        'info',
        pick('Tag 会去除首尾空白', 'Tag will be trimmed'),
        pick('发送时会使用去除首尾空白后的 Tag。', 'The trimmed Tag is used when sending.'),
        'tag',
      ),
    );
  }
  if (input.key && input.key !== key) {
    issues.push(
      issue(
        'TRIMMED_KEY',
        'info',
        pick('Key 会去除首尾空白', 'Key will be trimmed'),
        pick('发送时会使用去除首尾空白后的 Key。', 'The trimmed Key is used when sending.'),
        'key',
      ),
    );
  }

  const format = bodyFormat(normalizedBody);
  const bodyBytes = textBytes(normalizedBody);
  if (format === 'empty') {
    issues.push(
      issue(
        'EMPTY_BODY',
        'error',
        pick('消息体为空', 'Message body is empty'),
        pick('发送消息必须提供 Body。', 'A Body is required to send a message.'),
        'body',
      ),
    );
  } else if (bodyBytes > maxBodyBytes) {
    issues.push(
      issue(
        'BODY_SIZE_LIMIT',
        'error',
        pick('消息体超过默认上限', 'Body exceeds the default limit'),
        lang === 'en'
          ? `Current Body is ${bodyBytes} bytes, over the ${maxBodyBytes} bytes limit.`
          : `当前 Body 为 ${bodyBytes} bytes，超过 ${maxBodyBytes} bytes。`,
        'body',
      ),
    );
  } else if (format === 'plain-text') {
    issues.push(
      issue(
        'PLAIN_TEXT_BODY',
        'info',
        pick('Body 不是 JSON', 'Body is not JSON'),
        pick(
          'RocketMQ 支持文本消息，当前 Body 会按原始文本发送。',
          'RocketMQ supports text messages; the current Body is sent as raw text.',
        ),
        'body',
      ),
    );
  } else if (format === 'json-scalar') {
    issues.push(
      issue(
        'SCALAR_JSON_BODY',
        'info',
        pick('Body 是 JSON 标量', 'Body is a JSON scalar'),
        pick(
          '当前 Body 是合法 JSON，但不是对象或数组。',
          'The current Body is valid JSON but is neither an object nor an array.',
        ),
        'body',
      ),
    );
  }

  const propertyResult =
    input.propsMode === 'text'
      ? buildMessagePropertiesFromText(input.propsText ?? '', lang)
      : buildMessagePropertiesFromRows(input.properties, lang);
  issues.push(...propertyResult.issues);

  const reservedNames = propertyResult.entries
    .filter((entry) => entry.reserved)
    .map((entry) => entry.key)
    .sort();
  if (reservedNames.length > 0) {
    issues.push(
      issue(
        'RESERVED_PROPERTY_KEY',
        'warning',
        pick('属性名可能与系统属性冲突', 'Property names may clash with system properties'),
        lang === 'en'
          ? `Use business property names to avoid overriding or confusing system properties: ${reservedNames.join(', ')}`
          : `建议改用业务属性名，避免覆盖或混淆系统属性：${reservedNames.join(', ')}`,
        'properties',
        reservedNames,
      ),
    );
  }

  const emptyValueNames = propertyResult.entries
    .filter((entry) => entry.value.length === 0)
    .map((entry) => entry.key)
    .sort();
  if (emptyValueNames.length > 0) {
    issues.push(
      issue(
        'EMPTY_PROPERTY_VALUE',
        'info',
        pick('存在空属性值', 'Some property values are empty'),
        lang === 'en'
          ? `These properties are sent with empty strings: ${emptyValueNames.join(', ')}`
          : `这些属性会以空字符串发送：${emptyValueNames.join(', ')}`,
        'properties',
        emptyValueNames,
      ),
    );
  }

  const propertyBytes = propertyResult.entries.reduce(
    (sum, entry) => sum + entry.keyBytes + entry.valueBytes,
    0,
  );
  if (propertyResult.entries.length > maxProperties) {
    issues.push(
      issue(
        'PROPERTY_COUNT_LIMIT',
        'warning',
        pick('属性数量较多', 'Many properties'),
        lang === 'en'
          ? `Currently ${propertyResult.entries.length} properties; keep it under ${maxProperties}.`
          : `当前 ${propertyResult.entries.length} 个属性，建议控制在 ${maxProperties} 个以内。`,
        'properties',
      ),
    );
  }
  if (propertyBytes > maxPropertyBytes) {
    issues.push(
      issue(
        'PROPERTY_SIZE_LIMIT',
        'warning',
        pick('属性总大小较大', 'Large total property size'),
        lang === 'en'
          ? `Properties are about ${propertyBytes} bytes; keep them under ${maxPropertyBytes} bytes.`
          : `当前属性约 ${propertyBytes} bytes，建议控制在 ${maxPropertyBytes} bytes 以内。`,
        'properties',
      ),
    );
  }

  const blockingIssues = issues.filter((item) => item.severity === 'error');
  const warningIssues = issues.filter((item) => item.severity === 'warning');
  const status: MessagePayloadPreviewStatus =
    blockingIssues.length > 0 ? 'error' : warningIssues.length > 0 ? 'warning' : 'ready';

  return {
    status,
    issues,
    blockingIssues,
    normalized: {
      topic,
      ...(tag ? { tag } : {}),
      ...(key ? { key } : {}),
      body: normalizedBody,
    },
    properties: fromEntries(propertyResult.entries),
    propertyEntries: propertyResult.entries,
    summary: {
      bodyBytes,
      tagBytes: textBytes(tag),
      keyBytes: textBytes(key),
      propertyCount: propertyResult.entries.length,
      propertyBytes,
      bodyFormat: format,
      maxBodyBytes,
      maxProperties,
      maxPropertyBytes,
    },
  };
};
