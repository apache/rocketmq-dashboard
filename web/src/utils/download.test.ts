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
import { buildCsv, downloadBlob, downloadCsv } from './download';
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
        "\"''=literal\",\"'''+two-apostrophes\"",
      ].join('\n'),
    );
  });
});

describe('downloadCsv', () => {
  afterEach(() => {
    document.body.innerHTML = '';
    vi.restoreAllMocks();
  });

  it('prefixes a UTF-8 BOM so spreadsheet apps detect the encoding', async () => {
    const createObjectURL = vi.fn((_blob: Blob) => 'blob:csv');
    Object.defineProperty(URL, 'createObjectURL', {
      writable: true,
      value: createObjectURL,
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      writable: true,
      value: vi.fn(),
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    const csv = '"Name","Remark"\n"topic-a","中文备注"';

    downloadCsv('export.csv', csv);

    const blob = createObjectURL.mock.calls[0][0];
    const bytes = new Uint8Array(await blob.arrayBuffer());
    // blob.text() strips a leading BOM (TextDecoder's default), so assert the raw bytes:
    // EF BB BF is the UTF-8 encoding of U+FEFF. Without it Excel decodes the file as
    // ANSI/GBK and garbles every non-ASCII cell.
    expect(Array.from(bytes.slice(0, 3))).toEqual([0xef, 0xbb, 0xbf]);
    expect(await blob.text()).toBe(csv);
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
