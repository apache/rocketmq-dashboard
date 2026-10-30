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

import { describe, expect, it } from 'vitest';
import { formatBody } from '../message';

describe('formatBody numeric token preservation', () => {
  it('keeps int64 values beyond Number.MAX_SAFE_INTEGER verbatim', () => {
    const body = '{"orderId":9007199254740993}';
    expect(formatBody(body)).toBe(body);
  });

  it('keeps Int64 maximum verbatim', () => {
    const body = '{"max":9223372036854775807}';
    expect(formatBody(body)).toBe(body);
  });

  it('keeps high-precision decimals verbatim', () => {
    const body = '{"pi":0.1234567890123456789012345}';
    expect(formatBody(body)).toBe(body);
  });

  it('still pretty-prints bodies whose numbers round-trip unchanged', () => {
    expect(formatBody('{"a":42,"b":[1.5,null,true]}')).toBe(
      '{\n  "a": 42,\n  "b": [\n    1.5,\n    null,\n    true\n  ]\n}'
    );
  });

  it('pretty-prints digits inside strings without touching them', () => {
    const formatted = formatBody('{"note":"id 9007199254740993 here","n":7}');
    expect(formatted).toContain('"note": "id 9007199254740993 here"');
    expect(formatted).toContain('\n');
  });

  it('returns non-JSON bodies unchanged', () => {
    expect(formatBody('plain text body')).toBe('plain text body');
  });
});
