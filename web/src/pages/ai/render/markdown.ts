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
 * CommonMark requires a space after an ATX heading marker and after a list bullet, and a fence's
 * info string must be terminated by a newline. Models (especially when answering in Chinese) skip
 * all three, and the result is not a slightly-worse render but NO render: a heading written as
 * `##结论` stays a literal paragraph, `-第一项` stays a literal line, and a bash fence whose info
 * string runs straight into the command swallows the whole code block.
 *
 * Purely textual and idempotent: well-formed input comes back unchanged, so this is safe to apply to
 * every assistant text block, live or replayed.
 */
export function normalizeAiMarkdown(content: string): string {
  return content
    .replace(/^(#{1,6})(?=\S)/gm, '$1 ')
    .replace(/^([-+*])(?=\S)/gm, '$1 ')
    .replace(/^```(bash|sh|shell|json|ya?ml|sql|text)(?=\S)/gim, '```$1\n');
}
