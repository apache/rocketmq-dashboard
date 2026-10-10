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
  titleKey: string;
  descriptionKey: string;
  params?: Record<string, string | number>;
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
  titleKey: string,
  descriptionKey: string,
  field?: MessagePayloadIssue['field'],
  names?: string[],
  params?: Record<string, string | number>,
): MessagePayloadIssue => ({
  code,
  severity,
  titleKey,
  descriptionKey,
  params,
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
          'sendCheck.emptyPropertyKey.title',
          'sendCheck.emptyPropertyKey.desc',
          'properties',
          undefined,
          { value },
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
    const duplicateNames = [...duplicates].sort();
    issues.push(
      issue(
        'DUPLICATE_PROPERTY_KEY',
        'error',
        'sendCheck.duplicatePropertyKey.title',
        'sendCheck.duplicatePropertyKey.desc',
        'properties',
        duplicateNames,
        { names: duplicateNames.join(', ') },
      ),
    );
  }

  return { entries, issues };
};

const buildMessagePropertiesFromText = (
  text: string,
): { entries: MessagePropertyPreviewEntry[]; issues: MessagePayloadIssue[] } => {
  const parsed = parseMessageProperties(text);
  const entries = Object.entries(parsed.properties).map(([key, value]) => ({
    key,
    value,
    keyBytes: textBytes(key),
    valueBytes: textBytes(value),
    reserved: isReservedMessageProperty(key),
  }));

  return {
    entries,
    issues: parsed.errors.map((parseError) =>
      issue(
        'INVALID_PROPERTY_FORMAT',
        'error',
        'sendCheck.invalidPropertyFormat.title',
        parseError.key,
        'properties',
        undefined,
        parseError.params,
      ),
    ),
  };
};

export const analyzeMessagePayloadPreview = (
  input: MessagePayloadPreviewInput,
  options: MessagePayloadPreviewOptions = {},
): MessagePayloadPreview => {
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
        'sendCheck.trimmedTag.title',
        'sendCheck.trimmedTag.desc',
        'tag',
      ),
    );
  }
  if (input.key && input.key !== key) {
    issues.push(
      issue(
        'TRIMMED_KEY',
        'info',
        'sendCheck.trimmedKey.title',
        'sendCheck.trimmedKey.desc',
        'key',
      ),
    );
  }

  const format = bodyFormat(normalizedBody);
  const bodyBytes = textBytes(normalizedBody);
  if (format === 'empty') {
    issues.push(
      issue('EMPTY_BODY', 'error', 'sendCheck.emptyBody.title', 'sendCheck.emptyBody.desc', 'body'),
    );
  } else if (bodyBytes > maxBodyBytes) {
    issues.push(
      issue(
        'BODY_SIZE_LIMIT',
        'error',
        'sendCheck.bodySizeLimit.title',
        'sendCheck.bodySizeLimit.desc',
        'body',
        undefined,
        { bytes: bodyBytes, max: maxBodyBytes },
      ),
    );
  } else if (format === 'plain-text') {
    issues.push(
      issue(
        'PLAIN_TEXT_BODY',
        'info',
        'sendCheck.plainTextBody.title',
        'sendCheck.plainTextBody.desc',
        'body',
      ),
    );
  } else if (format === 'json-scalar') {
    issues.push(
      issue(
        'SCALAR_JSON_BODY',
        'info',
        'sendCheck.scalarJsonBody.title',
        'sendCheck.scalarJsonBody.desc',
        'body',
      ),
    );
  }

  const propertyResult =
    input.propsMode === 'text'
      ? buildMessagePropertiesFromText(input.propsText ?? '')
      : buildMessagePropertiesFromRows(input.properties);
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
        'sendCheck.reservedPropertyKey.title',
        'sendCheck.reservedPropertyKey.desc',
        'properties',
        reservedNames,
        { names: reservedNames.join(', ') },
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
        'sendCheck.emptyPropertyValue.title',
        'sendCheck.emptyPropertyValue.desc',
        'properties',
        emptyValueNames,
        { names: emptyValueNames.join(', ') },
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
        'sendCheck.propertyCountLimit.title',
        'sendCheck.propertyCountLimit.desc',
        'properties',
        undefined,
        { count: propertyResult.entries.length, max: maxProperties },
      ),
    );
  }
  if (propertyBytes > maxPropertyBytes) {
    issues.push(
      issue(
        'PROPERTY_SIZE_LIMIT',
        'warning',
        'sendCheck.propertySizeLimit.title',
        'sendCheck.propertySizeLimit.desc',
        'properties',
        undefined,
        { bytes: propertyBytes, max: maxPropertyBytes },
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
