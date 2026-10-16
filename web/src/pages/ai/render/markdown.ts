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

/**
 * Repair the Markdown models actually emit before handing it to `react-markdown`.
 *
 * CommonMark requires a space after an ATX heading marker and after a list bullet. Models
 * sometimes omit these spaces, so repair those markers outside fenced code blocks.
 * Preserve fence info strings verbatim: a language name such as `shell` or `jsonc` cannot be
 * distinguished from a supposedly missing newline by matching a shorter language prefix.
 *
 * Purely textual and idempotent: well-formed input comes back unchanged, so this is safe to apply to
 * every assistant text block, live or replayed.
 */
export function normalizeAiMarkdown(content: string): string {
  let fence: { marker: string; length: number } | null = null;
  // Split on the newline itself rather than a lookbehind (`/(?<=\n)/`): esbuild cannot downlevel
  // lookbehind assertions, and the Vite default build target still covers Safari versions that
  // throw a SyntaxError while merely parsing such a literal.
  return content
    .split('\n')
    .map((line) => {
      const marker = /^ {0,3}(`{3,}|~{3,})([^\r\n]*)/.exec(line);
      if (fence) {
        if (
          marker &&
          marker[1][0] === fence.marker &&
          marker[1].length >= fence.length &&
          !marker[2].trim()
        ) {
          fence = null;
        }
        return line;
      }
      // A backtick fence cannot contain backticks in its info string. Tilde fences can.
      if (marker && (marker[1][0] === '~' || !marker[2].includes('`'))) {
        fence = { marker: marker[1][0], length: marker[1].length };
        return line;
      }
      return line.replace(/^(#{1,6})(?=\S)/, '$1 ').replace(/^([-+*])(?=\S)/, '$1 ');
    })
    .join('\n');
}
