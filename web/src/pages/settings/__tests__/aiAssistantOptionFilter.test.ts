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
import { matchesAiOption } from '../AiAssistantTab';

describe('matchesAiOption', () => {
  it('matches the option value case-insensitively', () => {
    const option = { value: 'qwen-plus', label: 'Qwen Plus' };
    expect(matchesAiOption('QWEN-PLUS', option)).toBe(true);
    expect(matchesAiOption('qwen', option)).toBe(true);
    expect(matchesAiOption('gpt-4', option)).toBe(false);
  });

  it('matches the option label when the value does not match', () => {
    const option = { value: 'https://example.com/v1', label: 'Azure endpoint' };
    expect(matchesAiOption('azure', option)).toBe(true);
  });

  it('matches every option for an empty search string', () => {
    expect(matchesAiOption('', { value: 'qwen-plus' })).toBe(true);
  });

  it('does not throw for nullish or non-string values and input', () => {
    expect(matchesAiOption('x', { value: null, label: undefined })).toBe(false);
    expect(matchesAiOption('x', undefined)).toBe(false);
    expect(matchesAiOption(undefined, { value: 'qwen-plus' })).toBe(true);
    expect(matchesAiOption('123', { value: 456 })).toBe(false);
    expect(matchesAiOption('45', { value: 456 })).toBe(true);
  });
});
