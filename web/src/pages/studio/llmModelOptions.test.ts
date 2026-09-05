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
import { fallbackModelOptions } from './llmModelOptions';

describe('llm model fallback options', () => {
  it('serves the bundled model list for known providers', () => {
    const options = fallbackModelOptions('openai');

    expect(options[0]).toEqual({ value: 'gpt-5.6-sol', label: 'gpt-5.6-sol' });
    expect(options.map((option) => option.value)).toContain('gpt-5.6-terra');
  });

  it('falls back to the current model for unknown providers', () => {
    const options = fallbackModelOptions('custom', 'gpt-local');

    expect(options).toEqual([{ value: 'gpt-local', label: 'gpt-local' }]);
  });

  it('returns no options when the provider and model are both unknown', () => {
    expect(fallbackModelOptions('custom')).toEqual([]);
  });
});
