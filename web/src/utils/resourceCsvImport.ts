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

import type { ConsumerGroup, Topic } from '../api/metadata';

export const RESOURCE_IMPORT_ROW_LIMIT = 100;

export interface CsvRecord {
  lineNumber: number;
  values: Record<string, string>;
}

/** A row-level validation finding: a translation key plus optional params. */
export interface ImportIssue {
  key: string;
  params?: Record<string, string | number>;
}

export interface ResourceImportRow<T> {
  key: string;
  lineNumber: number;
  name: string;
  payload: T;
  status: 'pending' | 'invalid' | 'success' | 'failed';
  /** Validation findings from this util, resolved by the page through t(key, params). */
  issues?: ImportIssue[];
  /** Result-phase text set by the page after the import request settles. */
  message?: string;
}

export interface ResourceImportValidation<T> {
  rows: ResourceImportRow<T>[];
  errors: string[];
}

interface ParsedCsvRow {
  lineNumber: number;
  cells: string[];
}

const FORMULA_SAFE_PREFIX_PATTERN = /^'(?='*[=+\-@\t\r\n])/;

// Aligned with RocketMQ's TopicValidator/GroupValidator: a shared character set (letters,
// digits, underscore, hyphen, % and |) with per-kind length caps. Topics cap at 127 and
// consumer groups at 120; both may start with a digit or symbol, so no leading-letter rule.
export const RESOURCE_NAME_PATTERN = /^[%|a-zA-Z0-9_-]+$/;
export const RESOURCE_NAME_MAX_LENGTH = { topic: 127, group: 120 } as const;

export type ResourceNameKind = keyof typeof RESOURCE_NAME_MAX_LENGTH;

// Returns a translation key (with optional params) instead of display text; callers resolve
// it through t(key, params).
export const validateResourceName = (name: string, kind: ResourceNameKind): ImportIssue | null => {
  if (!name) {
    return { key: 'csvImport.nameEmpty' };
  }
  const maxLength = RESOURCE_NAME_MAX_LENGTH[kind];
  if (name.length > maxLength) {
    return { key: 'csvImport.nameTooLong', params: { max: maxLength } };
  }
  if (!RESOURCE_NAME_PATTERN.test(name)) {
    return { key: 'csvImport.nameInvalidChars' };
  }
  return null;
};

const TOPIC_TYPES = new Set(['NORMAL', 'FIFO', 'DELAY', 'TRANSACTION', 'LITE']);
const TOPIC_PERMISSIONS = new Set(['RW', 'RO', 'WO']);
const GROUP_SUBSCRIPTION_MODES = new Set(['Push', 'Pop']);
const GROUP_CONSUME_TYPES = new Set(['CLUSTERING', 'BROADCASTING']);
const GROUP_SUBSCRIPTION_DATA_TYPES = new Set(['NORMAL', 'FIFO', 'DELAY', 'TRANSACTION']);
const GROUP_DELIVERY_ORDER_TYPES = new Set(['PARTITON_ORDER', 'PARTITION_ORDER', 'MESSAGES_ORDER']);

const restoreFormulaSafeCell = (value: string): string =>
  value.replace(FORMULA_SAFE_PREFIX_PATTERN, '');

const normalizeHeader = (header: string): string => restoreFormulaSafeCell(header).trim();

const normalizeValue = (value: string | undefined): string => (value ?? '').trim();

const normalizeDeliveryOrderType = (value: string): string =>
  value === 'MESSAGES ORDER' ? 'MESSAGES_ORDER' : value;

const parseInteger = (
  value: string,
  fieldName: string,
  min: number,
  max: number,
  fallback: number,
  errors: string[],
): number => {
  if (!value) return fallback;
  if (!/^-?\d+$/.test(value)) {
    errors.push({ key: 'csvImport.fieldInteger', params: { field: fieldName } });
    return fallback;
  }

  const parsed = Number(value);
  if (parsed < min || parsed > max) {
    errors.push({ key: 'csvImport.fieldRange', params: { field: fieldName, min, max } });
    return fallback;
  }
  return parsed;
};

const readCsvRows = (content: string): ParsedCsvRow[] => {
  const rows: ParsedCsvRow[] = [];
  const text = content.startsWith('\uFEFF') ? content.slice(1) : content;
  let cells: string[] = [];
  let cell = '';
  let inQuotes = false;
  let quoteJustClosed = false;
  let rowStartLine = 1;
  let lineNumber = 1;

  const pushRow = () => {
    const nextCells = [...cells, cell];
    if (nextCells.some((value) => value.trim() !== '')) {
      rows.push({
        lineNumber: rowStartLine,
        cells: nextCells,
      });
    }
    cells = [];
    cell = '';
    quoteJustClosed = false;
    rowStartLine = lineNumber;
  };

  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    const next = text[index + 1];

    if (inQuotes) {
      if (char === '"') {
        if (next === '"') {
          cell += '"';
          index += 1;
        } else {
          inQuotes = false;
          quoteJustClosed = true;
        }
      } else {
        if (char === '\n' || (char === '\r' && next !== '\n')) lineNumber += 1;
        cell += char;
      }
      continue;
    }

    if (quoteJustClosed && char !== ',' && char !== '\r' && char !== '\n') {
      throw new Error(`第 ${lineNumber} 行 CSV 引号格式错误`);
    }

    if (char === '"') {
      if (cell.length > 0) {
        throw new Error(`第 ${lineNumber} 行 CSV 引号格式错误`);
      }
      inQuotes = true;
      quoteJustClosed = false;
      continue;
    }

    if (char === ',') {
      cells.push(cell);
      cell = '';
      quoteJustClosed = false;
      continue;
    }

    if (char === '\r' || char === '\n') {
      pushRow();
      if (char === '\r' && next === '\n') index += 1;
      lineNumber += 1;
      rowStartLine = lineNumber;
      continue;
    }

    cell += char;
    quoteJustClosed = false;
  }

  if (inQuotes) {
    throw new Error(`第 ${rowStartLine} 行 CSV 引号未闭合`);
  }

  pushRow();
  return rows;
};

export const parseCsvTable = (content: string): CsvRecord[] => {
  const rows = readCsvRows(content);
  if (rows.length === 0) {
    throw new Error('CSV 文件为空');
  }

  const headers = rows[0].cells.map(normalizeHeader);
  const duplicateHeaders = headers.filter(
    (header, index) => header && headers.indexOf(header) !== index,
  );
  if (headers.some((header) => !header)) {
    throw new Error('CSV 表头不能为空');
  }
  if (duplicateHeaders.length > 0) {
    throw new Error(`CSV 表头重复：${Array.from(new Set(duplicateHeaders)).join(', ')}`);
  }

  const records = rows.slice(1).map((row) => {
    if (row.cells.length > headers.length) {
      throw new Error(`第 ${row.lineNumber} 行字段数超过表头字段数`);
    }

    return {
      lineNumber: row.lineNumber,
      values: headers.reduce<Record<string, string>>((acc, header, index) => {
        acc[header] = restoreFormulaSafeCell(row.cells[index] ?? '').trim();
        return acc;
      }, {}),
    };
  });

  if (records.length === 0) {
    throw new Error('CSV 没有可导入的数据行');
  }
  if (records.length > RESOURCE_IMPORT_ROW_LIMIT) {
    throw new Error(`一次最多导入 ${RESOURCE_IMPORT_ROW_LIMIT} 行`);
  }

  return records;
};

const buildDuplicateNameLines = (records: CsvRecord[]): Map<number, number> => {
  const firstLineByName = new Map<string, number>();
  const lineByDuplicate = new Map<number, number>();

  records.forEach((record) => {
    const name = normalizeValue(record.values.Name);
    if (!name) return;

    const firstLine = firstLineByName.get(name);
    if (firstLine != null) {
      lineByDuplicate.set(record.lineNumber, firstLine);
    } else {
      firstLineByName.set(name, record.lineNumber);
    }
  });

  return lineByDuplicate;
};

export const validateTopicCsvImport = (
  records: CsvRecord[],
  selectedInstanceId?: string,
): ResourceImportValidation<Partial<Topic>> => {
  const duplicateLines = buildDuplicateNameLines(records);
  const rows: ResourceImportRow<Partial<Topic>>[] = [];

  records.forEach((record, index) => {
    const rowErrors: ImportIssue[] = [];
    const name = normalizeValue(record.values.Name);
    const type = normalizeValue(record.values.Type) || 'NORMAL';
    const writeQueues = parseInteger(
      normalizeValue(record.values['Write Queues']),
      'Write Queues',
      1,
      256,
      8,
      rowErrors,
    );
    const readQueues = parseInteger(
      normalizeValue(record.values['Read Queues']),
      'Read Queues',
      1,
      256,
      8,
      rowErrors,
    );
    const perm = normalizeValue(record.values.Permission) || 'RW';
    const remark = normalizeValue(record.values.Remark);
    const duplicateLine = duplicateLines.get(record.lineNumber);
    if (duplicateLine != null) {
      rowErrors.push({ key: 'csvImport.nameDuplicate', params: { line: duplicateLine, name } });
    }

    const nameError = validateResourceName(name, 'topic');
    if (nameError) {
      rowErrors.push(nameError);
    }
    if (!TOPIC_TYPES.has(type)) {
      rowErrors.push({ key: 'csvImport.unsupportedType', params: { value: type } });
    }
    if (!TOPIC_PERMISSIONS.has(perm)) {
      rowErrors.push({ key: 'csvImport.unsupportedPermission', params: { value: perm } });
    }

    rows.push({
      key: `${record.lineNumber}-${name || index}`,
      lineNumber: record.lineNumber,
      name,
      payload: {
        name,
        type,
        writeQueues,
        readQueues,
        perm,
        remark,
        ...(selectedInstanceId ? { instanceId: selectedInstanceId } : {}),
      },
      status: rowErrors.length > 0 ? 'invalid' : 'pending',
      issues: rowErrors.length > 0 ? rowErrors : undefined,
    });
  });

  return { rows, errors: [] };
};

export const validateConsumerGroupCsvImport = (
  records: CsvRecord[],
  selectedInstanceId?: string,
): ResourceImportValidation<Partial<ConsumerGroup>> => {
  const duplicateLines = buildDuplicateNameLines(records);
  const rows: ResourceImportRow<Partial<ConsumerGroup>>[] = [];

  records.forEach((record, index) => {
    const rowErrors: ImportIssue[] = [];
    const name = normalizeValue(record.values.Name);
    const subscriptionMode = normalizeValue(record.values['Subscription Mode']) || 'Push';
    const consumeType = normalizeValue(record.values['Consume Type']) || 'CLUSTERING';
    const retryMaxTimes = parseInteger(
      normalizeValue(record.values['Retry Max Times']),
      'Retry Max Times',
      0,
      128,
      16,
      rowErrors,
    );
    const subscriptionDataType =
      normalizeValue(record.values['Subscription Data Type']) || 'NORMAL';
    const deliveryOrderType = normalizeDeliveryOrderType(
      normalizeValue(record.values['Delivery Order Type']),
    );
    const duplicateLine = duplicateLines.get(record.lineNumber);
    if (duplicateLine != null) {
      rowErrors.push({ key: 'csvImport.nameDuplicate', params: { line: duplicateLine, name } });
    }

    const nameError = validateResourceName(name, 'group');
    if (nameError) {
      rowErrors.push(nameError);
    }
    if (!GROUP_SUBSCRIPTION_MODES.has(subscriptionMode)) {
      rowErrors.push({
        key: 'csvImport.unsupportedSubscriptionMode',
        params: { value: subscriptionMode },
      });
    }
    if (!GROUP_CONSUME_TYPES.has(consumeType)) {
      rowErrors.push({ key: 'csvImport.unsupportedConsumeType', params: { value: consumeType } });
    }
    if (!GROUP_SUBSCRIPTION_DATA_TYPES.has(subscriptionDataType)) {
      rowErrors.push({
        key: 'csvImport.unsupportedSubscriptionDataType',
        params: { value: subscriptionDataType },
      });
    }
    if (
      deliveryOrderType &&
      subscriptionDataType === 'FIFO' &&
      !GROUP_DELIVERY_ORDER_TYPES.has(deliveryOrderType)
    ) {
      rowErrors.push({
        key: 'csvImport.unsupportedDeliveryOrderType',
        params: { value: deliveryOrderType },
      });
    }

    rows.push({
      key: `${record.lineNumber}-${name || index}`,
      lineNumber: record.lineNumber,
      name,
      payload: {
        name,
        subscriptionMode,
        consumeType,
        retryMaxTimes,
        subscriptionDataType,
        ...(subscriptionDataType === 'FIFO' && deliveryOrderType ? { deliveryOrderType } : {}),
        subscribedTopics: [],
        ...(selectedInstanceId ? { instanceId: selectedInstanceId } : {}),
      },
      status: rowErrors.length > 0 ? 'invalid' : 'pending',
      issues: rowErrors.length > 0 ? rowErrors : undefined,
    });
  });

  return { rows, errors: [] };
};
