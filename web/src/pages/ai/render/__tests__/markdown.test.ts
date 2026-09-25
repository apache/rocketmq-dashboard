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

describe('normalizeAiMarkdown', () => {
  it('separates a heading marker that runs into its text', () => {
    expect(normalizeAiMarkdown('##结论\n正文')).toBe('## 结论\n正文');
  });

  it('leaves a well-formed heading alone', () => {
    expect(normalizeAiMarkdown('## 概述\n正文')).toBe('## 概述\n正文');
    expect(normalizeAiMarkdown('### 三级标题')).toBe('### 三级标题');
    expect(normalizeAiMarkdown('###### 六级标题')).toBe('###### 六级标题');
    expect(normalizeAiMarkdown('# 一级标题')).toBe('# 一级标题');
  });

  it('leaves seven hashes alone instead of treating them as a heading', () => {
    expect(normalizeAiMarkdown('####### 不是标题')).toBe('####### 不是标题');
  });

  it('separates a list bullet that runs into its text', () => {
    expect(normalizeAiMarkdown('-第一项\n*第二项\n+第三项')).toBe('- 第一项\n* 第二项\n+ 第三项');
  });

  it('terminates a fence info string that runs into the command', () => {
    expect(normalizeAiMarkdown('```bashls -la\n```')).toBe('```bash\nls -la\n```');
  });

  it('leaves strong emphasis alone', () => {
    expect(normalizeAiMarkdown('**bold** start')).toBe('**bold** start');
    expect(normalizeAiMarkdown('**结论**：一切正常')).toBe('**结论**：一切正常');
  });

  it('leaves emphasis alone', () => {
    expect(normalizeAiMarkdown('*italic* start')).toBe('*italic* start');
    expect(normalizeAiMarkdown('*重点*')).toBe('*重点*');
  });

  it('leaves a thematic break alone', () => {
    expect(normalizeAiMarkdown('第一段\n---\n第二段')).toBe('第一段\n---\n第二段');
    expect(normalizeAiMarkdown('---')).toBe('---');
  });

  it('leaves a signed number at the start of a line alone', () => {
    expect(normalizeAiMarkdown('-1 表示未知\n+2 表示重试')).toBe('-1 表示未知\n+2 表示重试');
  });

  it('leaves fenced code content alone', () => {
    const diff = '```diff\n-old line\n+new line\n```';
    expect(normalizeAiMarkdown(diff)).toBe(diff);
    const yaml = '```yaml\n---\nkey: value\n---\n```';
    expect(normalizeAiMarkdown(yaml)).toBe(yaml);
  });

  it('still repairs a bullet that follows a fenced block', () => {
    expect(normalizeAiMarkdown('```text\n-kept\n```\n-修好的')).toBe(
      '```text\n-kept\n```\n- 修好的',
    );
  });

  it('is idempotent', () => {
    const content = '##结论\n-第一项\n**加粗**\n```bashls\n```\n尾部';
    const once = normalizeAiMarkdown(content);
    expect(normalizeAiMarkdown(once)).toBe(once);
  });
});
