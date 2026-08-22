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
import { buildCsv, downloadBlob } from './download';
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
      ],
    );

    expect(csv).toBe(
      ['"Name","Remark"', '"\'=SUM(A1:A2)","hello, ""mq"""', '"\'\nline-feed",""'].join('\n'),
    );
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
