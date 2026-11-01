/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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
import { normalizeAiMarkdown } from '../markdown';

describe('Markdown fenced code preservation', () => {
  it.each(['```', '~~~', '````', '~~~~'])('preservesCodeBodyTest: %s', (fence) => {
    const input = `${fence}diff\n-removed\n+added\n#comment\n**literal**\n${fence}\n`;
    expect(normalizeAiMarkdown(input)).toBe(input);
  });

  it.each(['\n', '\r\n'])('preservesUnclosedStreamingFenceTest: %j', (eol) => {
    const input = ['```sh', '#!/bin/sh', '-1', '+2'].join(eol);
    expect(normalizeAiMarkdown(input)).toBe(input);
  });

  it('shortOrDifferentFenceCannotEndCodeTest', () => {
    const input = '````text\n```\n#inside\n~~~\n+inside\n````\n##结论';
    expect(normalizeAiMarkdown(input)).toBe(input.replace('##结论', '## 结论'));
  });

  it('fenceWithTrailingTextCannotEndCodeTest', () => {
    const input = '```text\n```not-a-close\n-unchanged\n```';
    expect(normalizeAiMarkdown(input)).toBe(input);
  });

  it('allowsIndentedOpeningAndLongerClosingFenceTest', () => {
    const input = '  ~~~text\n#inside\n  ~~~~  \n-项目';
    expect(normalizeAiMarkdown(input)).toBe(input.replace('-项目', '- 项目'));
  });

  it('repairsProseAndMalformedOpeningWhilePreservingCodeTest', () => {
    const input = '##结论\n-项目\n```bashprintf ok\n#comment\n```\n+下一项';
    expect(normalizeAiMarkdown(input)).toBe(
      '## 结论\n- 项目\n```bash\nprintf ok\n#comment\n```\n+ 下一项',
    );
  });
});
