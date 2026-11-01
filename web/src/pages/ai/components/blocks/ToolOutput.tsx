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

import { memo, useState, type CSSProperties, type ReactNode } from 'react';
import { Table, theme } from 'antd';
import { useLang } from '../../../../i18n/LangContext';

/**
 * A tool result, rendered as the shape it actually holds instead of one opaque `<pre>`.
 *
 * The transport contract types output as a string that merely *usually* holds serialised JSON, so
 * the raw text is always one click away (the 原文 toggle) and anything unparseable still renders
 * verbatim. What this adds is the two shapes an operator actually reads: a list of uniform objects
 * becomes a table, and so does CSV-shaped text — scanning `rmq.topic.list` as rows beats scanning
 * it as escaped quotes. Plain JSON that is not a row list gets a pretty-printed, colour-coded
 * `<pre>`: indentation alone removes most of the reading cost of a minified payload.
 *
 * Row and column counts are capped on purpose. A tool that returns ten thousand rows would freeze
 * the transcript if it were tabled wholesale; past the cap the raw view is the honest one.
 */

/** Past this many rows or columns the table stops helping and starts costing. */
const MAX_ROWS = 200;
const MAX_COLUMNS = 12;

interface ParsedTable {
  columns: string[];
  rows: Record<string, string>[];
}

function cellText(value: unknown): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

/** A JSON array of plain objects with a bounded, non-empty key union; null otherwise. */
function tryJsonTable(text: string): ParsedTable | null {
  const trimmed = text.trim();
  if (!trimmed.startsWith('[') && !trimmed.startsWith('{')) return null;
  let value: unknown;
  try {
    value = JSON.parse(trimmed);
  } catch {
    return null;
  }
  // Most Studio tools wrap the row list in an envelope ({"items": [...]} plus counts and ids).
  // An envelope with exactly one row-list member is still a table; one with several, or with only
  // scalar arrays, is not — guessing which member the operator cares about would be a lie.
  if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
    const rowLists = Object.values(value as Record<string, unknown>).filter(isRowList);
    if (rowLists.length !== 1) return null;
    value = rowLists[0];
  }
  if (!isRowList(value)) return null;
  const rowsSource = value as Record<string, unknown>[];
  const columns: string[] = [];
  for (const row of rowsSource) {
    for (const key of Object.keys(row)) if (!columns.includes(key)) columns.push(key);
  }
  if (columns.length === 0 || columns.length > MAX_COLUMNS) return null;
  const rows = rowsSource.map((row, index) => {
    const record: Record<string, string> = { key: String(index) };
    for (const column of columns) record[column] = cellText(row[column]);
    return record;
  });
  return { columns, rows };
}

/** A non-empty JSON array whose every element is a plain object: table rows. */
function isRowList(value: unknown): boolean {
  return (
    Array.isArray(value) &&
    value.length > 0 &&
    value.length <= MAX_ROWS &&
    value.every((row) => row !== null && typeof row === 'object' && !Array.isArray(row))
  );
}

/**
 * CSV-shaped text: two or more lines, every line the same comma count, a header of non-empty
 * cells. Quoted fields containing commas are not supported — that shape degrades to the raw view,
 * which is correct rather than wrong, and a half-parsed CSV table would be wrong.
 */
function tryCsvTable(text: string): ParsedTable | null {
  const lines = text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
  if (lines.length < 2 || lines.length > MAX_ROWS + 1) return null;
  if (lines.some((line) => line.includes('"'))) return null;
  const columnCount = lines[0].split(',').length;
  if (columnCount < 2 || columnCount > MAX_COLUMNS) return null;
  if (!lines.every((line) => line.split(',').length === columnCount)) return null;
  const columns = lines[0].split(',').map((cell) => cell.trim());
  if (columns.some((column) => column.length === 0)) return null;
  const rows = lines.slice(1).map((line, index) => {
    const parts = line.split(',');
    const record: Record<string, string> = { key: String(index) };
    columns.forEach((column, position) => {
      record[column] = (parts[position] ?? '').trim();
    });
    return record;
  });
  return { columns, rows };
}

/** Pretty-printed JSON, or null when the text is not a JSON document. */
function tryPrettyJson(text: string): string | null {
  const trimmed = text.trim();
  if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return null;
  try {
    return JSON.stringify(JSON.parse(trimmed), null, 2);
  } catch {
    return null;
  }
}

const JSON_TOKEN_SOURCE =
  '("(?:\\\\u[a-fA-F0-9]{4}|\\\\[^u]|[^\\\\"])*"(?:\\s*:)?|\\b(?:true|false|null)\\b|-?\\d+(?:\\.\\d*)?(?:[eE][+-]?\\d+)?)';

/** Colour-coded JSON: keys, strings, literals and numbers each read as their own thing. */
const HighlightedJson = ({ text }: { text: string }) => {
  const { token } = theme.useToken();
  const nodes: ReactNode[] = [];
  let cursor = 0;
  let match: RegExpExecArray | null;
  // A fresh regex per render: a shared lastIndex is mutable module state, and two bubbles
  // highlighting concurrently would resume each other's scan.
  const jsonToken = new RegExp(JSON_TOKEN_SOURCE, 'g');
  let index = 0;
  while ((match = jsonToken.exec(text)) !== null) {
    if (match.index > cursor) nodes.push(text.slice(cursor, match.index));
    const [piece] = match;
    const isKey = /:\s*$/.test(piece);
    const color = isKey
      ? token.colorPrimary
      : piece.startsWith('"')
        ? token.colorSuccess
        : /^[tf]/.test(piece)
          ? token.colorWarning
          : piece === 'null'
            ? token.colorTextTertiary
            : token.colorInfo;
    nodes.push(
      <span key={`t${index}`} style={{ color }}>
        {piece}
      </span>,
    );
    cursor = match.index + piece.length;
    index += 1;
  }
  if (cursor < text.length) nodes.push(text.slice(cursor));
  return <>{nodes}</>;
};

export interface ToolOutputProps {
  text: string;
  preStyle: CSSProperties;
}

const ToolOutput = ({ text, preStyle }: ToolOutputProps) => {
  const { t } = useLang();
  const { token } = theme.useToken();
  const [showRaw, setShowRaw] = useState(false);

  const table = tryJsonTable(text) ?? tryCsvTable(text);
  const prettyJson = table === null ? tryPrettyJson(text) : null;
  const structured = table !== null || prettyJson !== null;

  if (!structured || showRaw) {
    return (
      <div data-testid="tool-result">
        {structured && (
          <button
            type="button"
            data-testid="ai-tool-output-raw-toggle"
            onClick={() => setShowRaw(false)}
            style={{
              marginBottom: 6,
              padding: 0,
              border: 0,
              background: 'transparent',
              color: token.colorPrimary,
              fontSize: 14,
              cursor: 'pointer',
            }}
          >
            {t('ai.tool.outputStructured')}
          </button>
        )}
        <pre data-testid="ai-tool-output-raw" style={preStyle}>
          {text}
        </pre>
      </div>
    );
  }

  return (
    <div data-testid="tool-result">
      <button
        type="button"
        data-testid="ai-tool-output-raw-toggle"
        onClick={() => setShowRaw(true)}
        style={{
          marginBottom: 6,
          padding: 0,
          border: 0,
          background: 'transparent',
          color: token.colorTextTertiary,
          fontSize: 14,
          cursor: 'pointer',
        }}
      >
        {t('ai.tool.outputRaw')}
      </button>
      {table !== null ? (
        <Table
          data-testid="ai-tool-output-table"
          size="small"
          pagination={false}
          scroll={{ y: 280 }}
          columns={table.columns.map((column) => ({
            title: column,
            dataIndex: column,
            ellipsis: true,
          }))}
          dataSource={table.rows}
          style={{ marginTop: 0 }}
        />
      ) : (
        <pre data-testid="ai-tool-output-json" style={preStyle}>
          <HighlightedJson text={prettyJson as string} />
        </pre>
      )}
    </div>
  );
};

export default memo(ToolOutput);
