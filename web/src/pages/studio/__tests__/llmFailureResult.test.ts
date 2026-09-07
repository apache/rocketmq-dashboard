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
import { buildLlmFailureResult } from '../llmFailureResult';

describe('buildLlmFailureResult', () => {
  it('falls back to the caller message when the API result is missing', () => {
    expect(buildLlmFailureResult(null, '连接失败')).toEqual({
      success: false,
      msg: '连接失败',
      code: undefined,
      hint: undefined,
    });
  });

  it('prefers the API error message and preserves optional diagnostics', () => {
    expect(
      buildLlmFailureResult(
        { status: 0, errMsg: 'invalid key', code: 'INVALID_KEY', hint: '检查密钥' },
        '连接失败',
      ),
    ).toEqual({
      success: false,
      msg: 'invalid key',
      code: 'INVALID_KEY',
      hint: '检查密钥',
    });
  });
});
