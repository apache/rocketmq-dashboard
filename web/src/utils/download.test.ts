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

import { afterEach, describe, expect, it, vi } from 'vitest';
import { buildCsv, downloadBlob, downloadCsv, escapeCsvCell } from './download';
describe('buildCsv', () => {
  it('escapes quotes, empty values, and spreadsheet formulas', () => {
    const csv = buildCsv(
      [
        { header: 'Name', value: (row: { name: string }) => row.name },
        { header: 'Remark', value: (row: { remark?: string | null }) => row.remark },
      ],
      [
        { name: '=SUM(A1:A2)', remark: 'hello, "mq"' },
        { name: '\nline-feed', remark: null },
        { name: "'=literal", remark: "''+two-apostrophes" },
      ],
    );

    expect(csv).toBe(
      [
        '"Name","Remark"',
        '"\'=SUM(A1:A2)","hello, ""mq"""',
        '"\'\nline-feed",""',
        '"\'\'=literal","\'\'\'+two-apostrophes"',
      ].join('\n'),
    );
  });

  it('emits only the header row for an empty table', () => {
    expect(buildCsv([{ header: 'Cluster', value: () => '' }], [])).toBe('"Cluster"');
  });

  it('stringifies typed cell values and treats null as empty', () => {
    const csv = buildCsv(
      [
        { header: 'Count', value: (row: { count: unknown }) => row.count },
        { header: 'Enabled', value: (row: { enabled: unknown }) => row.enabled },
      ],
      [
        { count: 5, enabled: false },
        { count: null, enabled: true },
      ],
    );

    expect(csv).toBe('"Count","Enabled"\n"5","false"\n"","true"');
  });

  it('produces an empty document when neither columns nor rows exist', () => {
    expect(buildCsv([], [])).toBe('');
  });
});

describe('escapeCsvCell', () => {
  it('wraps every value in quotes and doubles embedded quotes', () => {
    expect(escapeCsvCell('plain')).toBe('"plain"');
    expect(escapeCsvCell('a"b')).toBe('"a""b"');
    expect(escapeCsvCell('  spaced  ')).toBe('"  spaced  "');
  });

  it('treats null and undefined as empty quoted cells', () => {
    expect(escapeCsvCell(null)).toBe('""');
    expect(escapeCsvCell(undefined)).toBe('""');
  });

  it('protects formulas introduced by = + - @ and lone apostrophes', () => {
    expect(escapeCsvCell('=SUM(A1)')).toBe('"\'=SUM(A1)"');
    expect(escapeCsvCell('+plus')).toBe('"\'+plus"');
    expect(escapeCsvCell('-dash')).toBe('"\'-dash"');
    expect(escapeCsvCell('@at')).toBe('"\'@at"');
    expect(escapeCsvCell("'=lit")).toBe('"\'\'=lit"');
    expect(escapeCsvCell('==double')).toBe('"\'==double"');
    expect(escapeCsvCell('=')).toBe('"\'="');
  });

  it('keeps ordinary text with an apostrophe in the middle untouched', () => {
    expect(escapeCsvCell("don't")).toBe('"don\'t"');
    expect(escapeCsvCell("rocketmq's")).toBe('"rocketmq\'s"');
  });

  it('stringifies numbers and booleans', () => {
    expect(escapeCsvCell(123)).toBe('"123"');
    expect(escapeCsvCell(0)).toBe('"0"');
    expect(escapeCsvCell(true)).toBe('"true"');
    expect(escapeCsvCell(false)).toBe('"false"');
  });
});

describe('downloadBlob', () => {
  afterEach(() => {
    vi.useRealTimers();
    document.body.innerHTML = '';
    vi.restoreAllMocks();
  });

  it('defers object URL revocation until the browser can start the download', () => {
    vi.useFakeTimers();
    const createObjectURL = vi.fn(() => 'blob:download');
    const revokeObjectURL = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', {
      writable: true,
      value: createObjectURL,
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      writable: true,
      value: revokeObjectURL,
    });
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      expect(document.body.contains(this)).toBe(true);
      expect(this.download).toBe('export.csv');
      expect(this.href).toBe('blob:download');
    });

    const blob = new Blob(['content'], { type: 'text/csv' });

    downloadBlob(blob, 'export.csv');

    expect(createObjectURL).toHaveBeenCalledWith(blob);
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(document.querySelector('a[download="export.csv"]')).not.toBeInTheDocument();
    expect(revokeObjectURL).not.toHaveBeenCalled();

    vi.runAllTimers();

    expect(revokeObjectURL).toHaveBeenCalledWith('blob:download');
  });

  it('still removes the anchor and schedules cleanup when the click fails', () => {
    vi.useFakeTimers();
    const revokeObjectURL = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', {
      writable: true,
      value: vi.fn(() => 'blob:failed-download'),
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      writable: true,
      value: revokeObjectURL,
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {
      throw new Error('download blocked');
    });

    expect(() => downloadBlob(new Blob(['content']), 'export.csv')).toThrow('download blocked');
    expect(document.querySelector('a[download="export.csv"]')).not.toBeInTheDocument();
    expect(revokeObjectURL).not.toHaveBeenCalled();

    vi.runAllTimers();

    expect(revokeObjectURL).toHaveBeenCalledWith('blob:failed-download');
  });
});

describe('downloadCsv', () => {
  afterEach(() => {
    vi.useRealTimers();
    document.body.innerHTML = '';
    vi.restoreAllMocks();
  });

  it('wraps the csv text in a text/csv blob and starts the download', async () => {
    vi.useFakeTimers();
    let capturedBlob: Blob | null = null;
    Object.defineProperty(URL, 'createObjectURL', {
      writable: true,
      value: vi.fn((blob: Blob) => {
        capturedBlob = blob;
        return 'blob:csv-export';
      }),
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      writable: true,
      value: vi.fn(),
    });
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      expect(this.download).toBe('instances.csv');
    });

    downloadCsv('instances.csv', '"id"\n"1"');

    expect(capturedBlob).not.toBeNull();
    expect(capturedBlob?.type).toBe('text/csv;charset=utf-8');
    await expect(capturedBlob?.text()).resolves.toBe('"id"\n"1"');
    expect(clickSpy).toHaveBeenCalledTimes(1);
    vi.runAllTimers();
  });
});
