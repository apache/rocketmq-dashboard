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

export type CsvLang = 'zh' | 'en';

export const RESOURCE_IMPORT_ROW_LIMIT = 100;

export interface CsvRecord {
  lineNumber: number;
  values: Record<string, string>;
}

export interface ResourceImportRow<T> {
  key: string;
  lineNumber: number;
  name: string;
  payload: T;
  status: 'pending' | 'invalid' | 'success' | 'failed';
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

const FORMULA_SAFE_PREFIX_PATTERN = /^'(?=[=+\-@\t\r\n])/;

// Aligned with RocketMQ's TopicValidator/GroupValidator: a shared character set (letters,
// digits, underscore, hyphen, % and |) with per-kind length caps. Topics cap at 127 and
// consumer groups at 120; both may start with a digit or symbol, so no leading-letter rule.
export const RESOURCE_NAME_PATTERN = /^[%|a-zA-Z0-9_-]+$/;
export const RESOURCE_NAME_MAX_LENGTH = { topic: 127, group: 120 } as const;

export type ResourceNameKind = keyof typeof RESOURCE_NAME_MAX_LENGTH;

export const validateResourceName = (
  name: string,
  kind: ResourceNameKind,
  lang: CsvLang = 'zh',
): string | null => {
  if (!name) {
    return lang === 'zh' ? 'Name 不能为空' : 'Name is required';
  }
  const maxLength = RESOURCE_NAME_MAX_LENGTH[kind];
  if (name.length > maxLength) {
    return lang === 'zh'
      ? `Name 长度不能超过 ${maxLength} 个字符`
      : `Name must be at most ${maxLength} characters`;
  }
  if (!RESOURCE_NAME_PATTERN.test(name)) {
    return lang === 'zh'
      ? 'Name 仅支持字母、数字、下划线、短横线、% 和 |'
      : 'Name supports only letters, digits, underscore, hyphen, % and |';
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

const normalizeValue = (value: string | undefined): string =>
  restoreFormulaSafeCell(value ?? '').trim();

const normalizeDeliveryOrderType = (value: string): string =>
  value === 'MESSAGES ORDER' ? 'MESSAGES_ORDER' : value;

const parseInteger = (
  value: string,
  fieldName: string,
  min: number,
  max: number,
  fallback: number,
  errors: string[],
  lang: CsvLang = 'zh',
): number => {
  if (!value) return fallback;
  if (!/^-?\d+$/.test(value)) {
    errors.push(
      lang === 'zh' ? `${fieldName} 必须是整数` : `${fieldName} must be an integer`,
    );
    return fallback;
  }

  const parsed = Number(value);
  if (parsed < min || parsed > max) {
    errors.push(
      lang === 'zh'
        ? `${fieldName} 必须在 ${min}..${max} 之间`
        : `${fieldName} must be between ${min} and ${max}`,
    );
    return fallback;
  }
  return parsed;
};

const readCsvRows = (content: string, lang: CsvLang = 'zh'): ParsedCsvRow[] => {
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
        cells: nextCells.map(restoreFormulaSafeCell),
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
      throw new Error(
        lang === 'zh'
          ? `第 ${lineNumber} 行 CSV 引号格式错误`
          : `CSV quote format error on line ${lineNumber}`,
      );
    }

    if (char === '"') {
      if (cell.length > 0) {
        throw new Error(
          lang === 'zh'
            ? `第 ${lineNumber} 行 CSV 引号格式错误`
            : `CSV quote format error on line ${lineNumber}`,
        );
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
    throw new Error(
      lang === 'zh'
        ? `第 ${rowStartLine} 行 CSV 引号未闭合`
        : `Unclosed CSV quote on line ${rowStartLine}`,
    );
  }

  pushRow();
  return rows;
};

export const parseCsvTable = (content: string, lang: CsvLang = 'zh'): CsvRecord[] => {
  const rows = readCsvRows(content, lang);
  if (rows.length === 0) {
    throw new Error(lang === 'zh' ? 'CSV 文件为空' : 'CSV file is empty');
  }

  const headers = rows[0].cells.map(normalizeHeader);
  const duplicateHeaders = headers.filter(
    (header, index) => header && headers.indexOf(header) !== index,
  );
  if (headers.some((header) => !header)) {
    throw new Error(lang === 'zh' ? 'CSV 表头不能为空' : 'CSV header cannot be empty');
  }
  if (duplicateHeaders.length > 0) {
    throw new Error(
      lang === 'zh'
        ? `CSV 表头重复：${Array.from(new Set(duplicateHeaders)).join(', ')}`
        : `Duplicate CSV headers: ${Array.from(new Set(duplicateHeaders)).join(', ')}`,
    );
  }

  const records = rows.slice(1).map((row) => {
    if (row.cells.length > headers.length) {
      throw new Error(
        lang === 'zh'
          ? `第 ${row.lineNumber} 行字段数超过表头字段数`
          : `Row ${row.lineNumber} has more fields than the header`,
      );
    }

    return {
      lineNumber: row.lineNumber,
      values: headers.reduce<Record<string, string>>((acc, header, index) => {
        acc[header] = normalizeValue(row.cells[index]);
        return acc;
      }, {}),
    };
  });

  if (records.length === 0) {
    throw new Error(lang === 'zh' ? 'CSV 没有可导入的数据行' : 'CSV has no importable data rows');
  }
  if (records.length > RESOURCE_IMPORT_ROW_LIMIT) {
    throw new Error(
      lang === 'zh'
        ? `一次最多导入 ${RESOURCE_IMPORT_ROW_LIMIT} 行`
        : `At most ${RESOURCE_IMPORT_ROW_LIMIT} rows can be imported at once`,
    );
  }

  return records;
};

const buildDuplicateNameMessages = (records: CsvRecord[], lang: CsvLang = 'zh'): Map<number, string> => {
  const firstLineByName = new Map<string, number>();
  const messagesByLine = new Map<number, string>();

  records.forEach((record) => {
    const name = normalizeValue(record.values.Name);
    if (!name) return;

    const firstLine = firstLineByName.get(name);
    if (firstLine != null) {
      messagesByLine.set(
        record.lineNumber,
        lang === 'zh'
          ? `Name 与第 ${firstLine} 行重复：${name}`
          : `Name duplicates row ${firstLine}: ${name}`,
      );
    } else {
      firstLineByName.set(name, record.lineNumber);
    }
  });

  return messagesByLine;
};

export const validateTopicCsvImport = (
  records: CsvRecord[],
  selectedInstanceId?: string,
  lang: CsvLang = 'zh',
): ResourceImportValidation<Partial<Topic>> => {
  const duplicateMessages = buildDuplicateNameMessages(records, lang);
  const rows: ResourceImportRow<Partial<Topic>>[] = [];

  records.forEach((record, index) => {
    const rowErrors: string[] = [];
    const name = normalizeValue(record.values.Name);
    const type = normalizeValue(record.values.Type) || 'NORMAL';
    const writeQueues = parseInteger(
      normalizeValue(record.values['Write Queues']),
      'Write Queues',
      1,
      256,
      8,
      rowErrors,
      lang,
    );
    const readQueues = parseInteger(
      normalizeValue(record.values['Read Queues']),
      'Read Queues',
      1,
      256,
      8,
      rowErrors,
      lang,
    );
    const perm = normalizeValue(record.values.Permission) || 'RW';
    const remark = normalizeValue(record.values.Remark);
    const duplicateMessage = duplicateMessages.get(record.lineNumber);
    if (duplicateMessage) rowErrors.push(duplicateMessage);

    const nameError = validateResourceName(name, 'topic', lang);
    if (nameError) {
      rowErrors.push(nameError);
    }
    if (!TOPIC_TYPES.has(type)) {
      rowErrors.push(lang === 'zh' ? `Type 不支持：${type}` : `Unsupported Type: ${type}`);
    }
    if (!TOPIC_PERMISSIONS.has(perm)) {
      rowErrors.push(
        lang === 'zh' ? `Permission 不支持：${perm}` : `Unsupported Permission: ${perm}`,
      );
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
      message: rowErrors.join(lang === 'zh' ? '；' : '; ') || undefined,
    });
  });

  return { rows, errors: [] };
};

export const validateConsumerGroupCsvImport = (
  records: CsvRecord[],
  selectedInstanceId?: string,
  lang: CsvLang = 'zh',
): ResourceImportValidation<Partial<ConsumerGroup>> => {
  const duplicateMessages = buildDuplicateNameMessages(records, lang);
  const rows: ResourceImportRow<Partial<ConsumerGroup>>[] = [];

  records.forEach((record, index) => {
    const rowErrors: string[] = [];
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
      lang,
    );
    const subscriptionDataType =
      normalizeValue(record.values['Subscription Data Type']) || 'NORMAL';
    const deliveryOrderType = normalizeDeliveryOrderType(
      normalizeValue(record.values['Delivery Order Type']),
    );
    const duplicateMessage = duplicateMessages.get(record.lineNumber);
    if (duplicateMessage) rowErrors.push(duplicateMessage);

    const nameError = validateResourceName(name, 'group', lang);
    if (nameError) {
      rowErrors.push(nameError);
    }
    if (!GROUP_SUBSCRIPTION_MODES.has(subscriptionMode)) {
      rowErrors.push(
        lang === 'zh'
          ? `Subscription Mode 不支持：${subscriptionMode}`
          : `Unsupported Subscription Mode: ${subscriptionMode}`,
      );
    }
    if (!GROUP_CONSUME_TYPES.has(consumeType)) {
      rowErrors.push(
        lang === 'zh' ? `Consume Type 不支持：${consumeType}` : `Unsupported Consume Type: ${consumeType}`,
      );
    }
    if (!GROUP_SUBSCRIPTION_DATA_TYPES.has(subscriptionDataType)) {
      rowErrors.push(
        lang === 'zh'
          ? `Subscription Data Type 不支持：${subscriptionDataType}`
          : `Unsupported Subscription Data Type: ${subscriptionDataType}`,
      );
    }
    if (
      deliveryOrderType &&
      subscriptionDataType === 'FIFO' &&
      !GROUP_DELIVERY_ORDER_TYPES.has(deliveryOrderType)
    ) {
      rowErrors.push(
        lang === 'zh'
          ? `Delivery Order Type 不支持：${deliveryOrderType}`
          : `Unsupported Delivery Order Type: ${deliveryOrderType}`,
      );
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
      message: rowErrors.join(lang === 'zh' ? '；' : '; ') || undefined,
    });
  });

  return { rows, errors: [] };
};
