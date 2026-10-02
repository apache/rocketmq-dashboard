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
import { normalizeAiMarkdown } from '../markdown';

/**
 * `normalizeAiMarkdown` exists to repair the three mistakes models make (`##结论`, `-第一项`, a fence
 * info string running into the command) and promises to be "purely textual and idempotent: well-formed
 * input comes back unchanged". These cases pin both halves of that promise: the repairs still happen,
 * and Markdown that is already correct is handed to `react-markdown` byte for byte.
 */
describe('normalizeAiMarkdown', () => {
  it('separates a heading marker that runs into its text', () => {
    expect(normalizeAiMarkdown('##结论\n正文')).toBe('## 结论\n正文');
    expect(normalizeAiMarkdown('#结论')).toBe('# 结论');
    expect(normalizeAiMarkdown('######结论')).toBe('###### 结论');
  });

  it('leaves a well-formed heading at its own level', () => {
    expect(normalizeAiMarkdown('## 概述\n正文')).toBe('## 概述\n正文');
    expect(normalizeAiMarkdown('# 一级标题')).toBe('# 一级标题');
    expect(normalizeAiMarkdown('### 三级标题')).toBe('### 三级标题');
    expect(normalizeAiMarkdown('###### 六级标题')).toBe('###### 六级标题');
  });

  it('leaves a run of seven hashes alone', () => {
    // Not an ATX heading at all: the marker run is longer than six, so there is no level to repair.
    expect(normalizeAiMarkdown('####### 不是标题')).toBe('####### 不是标题');
  });

  it('leaves strong emphasis alone', () => {
    expect(normalizeAiMarkdown('**加粗**：一切正常')).toBe('**加粗**：一切正常');
    expect(normalizeAiMarkdown('**bold** start')).toBe('**bold** start');
  });

  it('leaves emphasis alone', () => {
    expect(normalizeAiMarkdown('*斜体*说明')).toBe('*斜体*说明');
    expect(normalizeAiMarkdown('*italic* start')).toBe('*italic* start');
  });

  it('leaves a thematic break alone', () => {
    expect(normalizeAiMarkdown('第一段\n---\n第二段')).toBe('第一段\n---\n第二段');
    expect(normalizeAiMarkdown('---')).toBe('---');
  });

  it('leaves a signed number at the start of a line alone', () => {
    expect(normalizeAiMarkdown('-1 表示未知\n+2 表示重试')).toBe('-1 表示未知\n+2 表示重试');
  });

  it('separates a list bullet that runs into its text', () => {
    expect(normalizeAiMarkdown('-第一项\n*第二项\n+第三项')).toBe('- 第一项\n* 第二项\n+ 第三项');
  });

  it('leaves an already separated list alone', () => {
    expect(normalizeAiMarkdown('- 第一项\n* 第二项\n+ 第三项')).toBe('- 第一项\n* 第二项\n+ 第三项');
  });

  it('terminates a fence info string that runs into the command', () => {
    expect(normalizeAiMarkdown('```bashls -la\n```')).toBe('```bash\nls -la\n```');
  });

  it('leaves fenced code content alone', () => {
    const diff = '```diff\n-old line\n+new line\n```';
    expect(normalizeAiMarkdown(diff)).toBe(diff);
    expect(normalizeAiMarkdown('```bash\n#注释\n##结论\n```')).toBe('```bash\n#注释\n##结论\n```');
  });

  it('repairs markers outside a fence while the fence keeps its content', () => {
    expect(normalizeAiMarkdown('##结论\n```diff\n-旧行\n```\n-第一项')).toBe(
      '## 结论\n```diff\n-旧行\n```\n- 第一项',
    );
  });

  it('is idempotent', () => {
    const content = '##结论\n-第一项\n**加粗**\n```bashls\n```\n尾部';
    const once = normalizeAiMarkdown(content);
    expect(normalizeAiMarkdown(once)).toBe(once);
  });
});
