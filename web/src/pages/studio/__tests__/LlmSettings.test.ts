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
import { fallbackModelOptions } from '../llmModelOptions';

describe('LlmSettingsPage', () => {
  it('keeps structured LLM failure details for display', () => {
    expect(
      buildLlmFailureResult(
        {
          status: 1,
          errMsg: 'LLM API key is required',
          code: 'llm.config.missing_api_key',
          hint: 'Configure an API key for provider openai.',
        },
        'Connection test failed',
      ),
    ).toEqual({
      success: false,
      msg: 'LLM API key is required',
      code: 'llm.config.missing_api_key',
      hint: 'Configure an API key for provider openai.',
    });
  });

  it('falls back to the default failure message without structured details', () => {
    expect(buildLlmFailureResult(null, 'Connection test failed')).toEqual({
      success: false,
      msg: 'Connection test failed',
      code: undefined,
      hint: undefined,
    });
  });

  it('keeps provider fallback models available before config is saved', () => {
    expect(fallbackModelOptions('openai')).toEqual([
      { value: 'gpt-4o', label: 'gpt-4o' },
      { value: 'gpt-4-turbo', label: 'gpt-4-turbo' },
      { value: 'gpt-4', label: 'gpt-4' },
      { value: 'gpt-3.5-turbo', label: 'gpt-3.5-turbo' },
    ]);
  });

  it('falls back to the current model for unknown providers', () => {
    expect(fallbackModelOptions('custom', 'custom-model')).toEqual([
      { value: 'custom-model', label: 'custom-model' },
    ]);
  });
});
