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
 * every assistant text block, live or replayed. That promise is also why each repair is written as
 * narrowly as it can be: a heading marker is separated only when the whole run runs into its text
 * (`## 概述` already carries its space, and `#######` is not a heading), a list bullet only when the
 * line really opens a list (`**加粗**` and `*斜体*` are emphasis, `---` is a thematic break, `-1` is
 * a signed number), and neither repair reaches inside a fenced code block.
 */

/** An ATX heading whose whole marker run runs straight into its text. */
const ATX_HEADING = /^(#{1,6})(?=[^\s#])/;

/** A line that could open a list: a bullet immediately followed by the item's text. */
const LIST_BULLET = /^([-+*])(?=\S)/;

/** A fence line, which delimits the regions the marker repairs must not touch. */
const FENCE_LINE = /^```/;

/** A fence info string that runs straight into the first command. */
const FENCE_INFO = /^```(bash|sh|shell|json|ya?ml|sql|text)(?=\S)/gim;

/**
 * A line may open with a list marker without being a list item, and separating the marker from what
 * follows would change what the line means.
 */
function opensAsList(marker: string, rest: string): boolean {
  // `**加粗**` and `---` repeat their own marker: emphasis, or a thematic break.
  if (rest.startsWith(marker)) return false;
  // `-1` / `+2` are signed numbers; `*` is never a sign, so only a bullet can follow it.
  if (marker !== '*' && /^\d/.test(rest)) return false;
  // `*斜体*` closes the emphasis, so a `*` line carrying a second one is not a bullet.
  return !(marker === '*' && rest.includes('*'));
}

/** Repair the heading or bullet marker of one line; a line is at most one of the two. */
function repairMarkers(line: string): string {
  const heading = line.replace(ATX_HEADING, '$1 ');
  if (heading !== line) return heading;

  const bullet = LIST_BULLET.exec(line);
  if (bullet && opensAsList(bullet[1], line.slice(bullet[1].length))) {
    return `${bullet[1]} ${line.slice(bullet[1].length)}`;
  }
  return line;
}
export function normalizeAiMarkdown(content: string): string {
  // The fence info string is repaired first, so the newline it inserts is what the line walk below
  // sees: the info line opens the fence and the command that ran into it is inside it.
  let inFence = false;

  return content
    .replace(FENCE_INFO, '```$1\n')
    .split('\n')
    .map((line) => {
      if (FENCE_LINE.test(line)) {
        inFence = !inFence;
        return line;
      }
      return inFence ? line : repairMarkers(line);
    })
    .join('\n');
}
