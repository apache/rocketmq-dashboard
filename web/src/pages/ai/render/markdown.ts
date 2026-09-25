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

/** An ATX heading whose full marker run runs straight into its text. */
const ATX_HEADING = /^(#{1,6})(?=[^\s#])/;
/** A list marker at the start of a line. */
const LIST_BULLET = /^([-+*])(?=\S)/;
/** A fence line, which delimits the regions the marker repairs must not touch. */
const FENCE_LINE = /^```/;
/** A fence info string that runs straight into the first command. */
const FENCE_INFO = /^```(bash|sh|shell|json|ya?ml|sql|text)(?=\S)/gim;

/**
 * A line may open with a list marker without being a list item: `**bold**` and `*italic*` are
 * emphasis, `---` is a thematic break (or a setext underline), and `-1` / `+2` are signed numbers.
 * Separating the marker from what follows would change what the line means, so keep it as written.
 */
function opensAsList(marker: string, rest: string): boolean {
  if (rest.startsWith(marker)) {
    return false;
  }
  if (marker !== '*' && /^\d/.test(rest)) {
    return false;
  }
  return marker !== '*' || !rest.includes('*');
}

function repairMarkers(line: string): string {
  const heading = line.replace(ATX_HEADING, '$1 ');
  if (heading !== line) {
    return heading;
  }
  const match = LIST_BULLET.exec(line);
  if (match && opensAsList(match[1], line.slice(match[1].length))) {
    return `${match[1]} ${line.slice(match[1].length)}`;
  }
  return line;
}

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
 * every assistant text block, live or replayed. A marker is separated only when it runs straight into
 * its own text: headings that already carry their space, emphasis runs, thematic breaks, signed
 * numbers and fenced code all come back as written.
 */
export function normalizeAiMarkdown(content: string): string {
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
