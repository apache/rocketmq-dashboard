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
import { buildLlmFailureResult } from './llmFailureResult';

describe('LLM failure result builder', () => {
  it('prefers the provider error message and keeps code and hint', () => {
    const failure = buildLlmFailureResult(
      { errMsg: 'connection refused', code: 'llm.connection', hint: 'check the endpoint' },
      'fallback',
    );

    expect(failure).toEqual({
      success: false,
      msg: 'connection refused',
      code: 'llm.connection',
      hint: 'check the endpoint',
    });
  });

  it('falls back to the supplied message when the provider omits one', () => {
    const failure = buildLlmFailureResult({ code: 'llm.connection' }, 'unable to reach the model');

    expect(failure).toEqual({
      success: false,
      msg: 'unable to reach the model',
      code: 'llm.connection',
      hint: undefined,
    });
  });

  it('handles a null result with only the fallback message', () => {
    expect(buildLlmFailureResult(null, 'no response')).toEqual({
      success: false,
      msg: 'no response',
      code: undefined,
      hint: undefined,
    });
  });
});
