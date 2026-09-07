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
import { FALLBACK_MODELS, fallbackModelOptions } from '../llmModelOptions';

describe('fallbackModelOptions', () => {
  it('returns provider fallbacks as value/label options', () => {
    expect(fallbackModelOptions('openai')).toEqual(
      FALLBACK_MODELS.openai.map((item) => ({ value: item, label: item })),
    );
  });

  it('uses the current model for unknown providers when one is supplied', () => {
    expect(fallbackModelOptions('custom', 'gpt-5.6-sol')).toEqual([
      { value: 'gpt-5.6-sol', label: 'gpt-5.6-sol' },
    ]);
  });

  it('returns an empty list for an unknown provider without a current model', () => {
    expect(fallbackModelOptions('custom')).toEqual([]);
  });
});
