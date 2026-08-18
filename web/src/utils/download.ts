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

export const downloadBlob = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.style.display = 'none';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
};

export interface CsvColumn<T> {
  header: string;
  value: (row: T) => unknown;
}

export const escapeCsvCell = (value: unknown) => {
  const text = value == null ? '' : String(value);
  const formulaSafeText = /^[=+\-@\t\r\n]/.test(text) ? `'${text}` : text;
  return `"${formulaSafeText.replace(/"/g, '""')}"`;
};

export const buildCsv = <T>(columns: CsvColumn<T>[], rows: T[]) =>
  [
    columns.map((column) => escapeCsvCell(column.header)).join(','),
    ...rows.map((row) => columns.map((column) => escapeCsvCell(column.value(row))).join(',')),
  ].join('\n');

export const downloadCsv = (filename: string, csv: string) => {
  downloadBlob(new Blob([csv], { type: 'text/csv;charset=utf-8' }), filename);
};
