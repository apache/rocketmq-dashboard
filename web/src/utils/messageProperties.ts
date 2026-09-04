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

export type PropertyParseError =
  | { kind: 'invalidFormat'; value: string }
  | { kind: 'emptyName'; value: string }
  | { kind: 'duplicate'; key: string };

interface ParsedProperties {
  properties: Record<string, string>;
  errors: PropertyParseError[];
}

// 解析批量粘贴的用户属性串：key=value 按换行或逗号分隔，等号只取第一个
export const parseMessageProperties = (text: string): ParsedProperties => {
  const entries = new Map<string, string>();
  const errors: string[] = [];
  for (const line of text.split(/[\n,]+/)) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    const eqIndex = trimmed.indexOf('=');
    if (eqIndex <= 0) {
      errors.push({ kind: 'invalidFormat', value: trimmed });
      continue;
    }
    const key = trimmed.slice(0, eqIndex).trim();
    if (!key) {
      errors.push({ kind: 'emptyName', value: trimmed });
    } else if (entries.has(key)) {
      errors.push({ kind: 'duplicate', key });
    } else {
      entries.set(key, trimmed.slice(eqIndex + 1).trim());
    }
  }
  return { properties: Object.fromEntries(entries), errors };
};
