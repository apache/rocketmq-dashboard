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
  it('inserts the space CommonMark requires after a heading marker or bullet', () => {
    expect(normalizeAiMarkdown('##结论')).toBe('## 结论');
    expect(normalizeAiMarkdown('#标题')).toBe('# 标题');
    expect(normalizeAiMarkdown('-第一项')).toBe('- 第一项');
    expect(normalizeAiMarkdown('+加号列表')).toBe('+ 加号列表');
    expect(normalizeAiMarkdown('```bashls -la\n```')).toBe('```bash\nls -la\n```');
  });

  it('leaves well-formed markdown unchanged (idempotence)', () => {
    const wellFormed =
      '# 标题\n\n## 结论\n\n### 三级\n\n- 第一项\n- 第二项\n\n```bash\nls -la\n```\n';
    expect(normalizeAiMarkdown(wellFormed)).toBe(wellFormed);
  });

  it('does not rewrite a well-formed ATX heading into a deeper stray marker', () => {
    // A plain (?=\S) lookahead lets `#{1,6}` backtrack: `## heading` matched as one `#`
    // followed by `#` and was rewritten to `# # heading` — every level-2+ heading in an
    // assistant answer was corrupted before this guard existed.
    expect(normalizeAiMarkdown('## heading')).toBe('## heading');
    expect(normalizeAiMarkdown('### heading')).toBe('### heading');
    expect(normalizeAiMarkdown('###### heading')).toBe('###### heading');
  });

  it('leaves thematic breaks and setext underlines intact', () => {
    // --- / *** are valid CommonMark thematic breaks, and --- under a title line is a
    // setext heading. Repairing them would rewrite a horizontal rule into a bullet
    // with stray dashes.
    expect(normalizeAiMarkdown('---')).toBe('---');
    expect(normalizeAiMarkdown('***')).toBe('***');
    expect(normalizeAiMarkdown('标题\n---\n')).toBe('标题\n---\n');
    expect(normalizeAiMarkdown('前文\n\n---\n\n后文\n')).toBe('前文\n\n---\n\n后文\n');
  });
});
