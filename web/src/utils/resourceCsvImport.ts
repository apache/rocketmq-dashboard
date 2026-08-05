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

const FORMULA_SAFE_PREFIX_PATTERN = /^'(?=[=+\-@])/;
const TOPIC_NAME_PATTERN = /^[a-zA-Z0-9_\-/*]+$/;
const GROUP_NAME_PATTERN = /^[a-zA-Z][a-zA-Z0-9_-]*$/;

const TOPIC_TYPES = new Set(['NORMAL', 'FIFO', 'DELAY', 'TRANSACTION', 'LITE']);
const TOPIC_PERMISSIONS = new Set(['RW', 'RO', 'WO']);
const GROUP_SUBSCRIPTION_MODES = new Set(['Push', 'Pop']);
const GROUP_CONSUME_TYPES = new Set(['CLUSTERING', 'BROADCASTING']);
const GROUP_SUBSCRIPTION_DATA_TYPES = new Set(['NORMAL', 'FIFO', 'DELAY', 'TRANSACTION']);
const GROUP_DELIVERY_ORDER_TYPES = new Set(['PARTITON_ORDER', 'PARTITION_ORDER', 'MESSAGES ORDER']);

const restoreFormulaSafeCell = (value: string): string =>
  value.replace(FORMULA_SAFE_PREFIX_PATTERN, '');

const normalizeHeader = (header: string): string => restoreFormulaSafeCell(header).trim();

const normalizeValue = (value: string | undefined): string =>
  restoreFormulaSafeCell(value ?? '').trim();

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
    errors.push(`${fieldName} 必须是整数`);
    return fallback;
  }

  const parsed = Number(value);
  if (parsed < min || parsed > max) {
    errors.push(`${fieldName} 必须在 ${min}..${max} 之间`);
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
        if (char === '\n') lineNumber += 1;
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
        acc[header] = normalizeValue(row.cells[index]);
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

const buildDuplicateNameMessages = (records: CsvRecord[]): Map<number, string> => {
  const firstLineByName = new Map<string, number>();
  const messagesByLine = new Map<number, string>();

  records.forEach((record) => {
    const name = normalizeValue(record.values.Name);
    if (!name) return;

    const firstLine = firstLineByName.get(name);
    if (firstLine != null) {
      messagesByLine.set(record.lineNumber, `Name 与第 ${firstLine} 行重复：${name}`);
    } else {
      firstLineByName.set(name, record.lineNumber);
    }
  });

  return messagesByLine;
};

export const validateTopicCsvImport = (
  records: CsvRecord[],
  selectedInstanceId?: string,
): ResourceImportValidation<Partial<Topic>> => {
  const duplicateMessages = buildDuplicateNameMessages(records);
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
    const duplicateMessage = duplicateMessages.get(record.lineNumber);
    if (duplicateMessage) rowErrors.push(duplicateMessage);

    if (!name) {
      rowErrors.push('Name 不能为空');
    } else if (!TOPIC_NAME_PATTERN.test(name)) {
      rowErrors.push('Name 仅支持字母、数字、下划线、中划线、斜杠和星号');
    }
    if (!TOPIC_TYPES.has(type)) {
      rowErrors.push(`Type 不支持：${type}`);
    }
    if (!TOPIC_PERMISSIONS.has(perm)) {
      rowErrors.push(`Permission 不支持：${perm}`);
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
      message: rowErrors.join('；') || undefined,
    });
  });

  return { rows, errors: [] };
};

export const validateConsumerGroupCsvImport = (
  records: CsvRecord[],
  selectedInstanceId?: string,
): ResourceImportValidation<Partial<ConsumerGroup>> => {
  const duplicateMessages = buildDuplicateNameMessages(records);
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
    );
    const subscriptionDataType =
      normalizeValue(record.values['Subscription Data Type']) || 'NORMAL';
    const deliveryOrderType = normalizeValue(record.values['Delivery Order Type']);
    const duplicateMessage = duplicateMessages.get(record.lineNumber);
    if (duplicateMessage) rowErrors.push(duplicateMessage);

    if (!name) {
      rowErrors.push('Name 不能为空');
    } else if (!GROUP_NAME_PATTERN.test(name)) {
      rowErrors.push('Name 需以字母开头，仅包含字母、数字、下划线和短横线');
    }
    if (!GROUP_SUBSCRIPTION_MODES.has(subscriptionMode)) {
      rowErrors.push(`Subscription Mode 不支持：${subscriptionMode}`);
    }
    if (!GROUP_CONSUME_TYPES.has(consumeType)) {
      rowErrors.push(`Consume Type 不支持：${consumeType}`);
    }
    if (!GROUP_SUBSCRIPTION_DATA_TYPES.has(subscriptionDataType)) {
      rowErrors.push(`Subscription Data Type 不支持：${subscriptionDataType}`);
    }
    if (
      deliveryOrderType &&
      subscriptionDataType === 'FIFO' &&
      !GROUP_DELIVERY_ORDER_TYPES.has(deliveryOrderType)
    ) {
      rowErrors.push(`Delivery Order Type 不支持：${deliveryOrderType}`);
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
      message: rowErrors.join('；') || undefined,
    });
  });

  return { rows, errors: [] };
};
