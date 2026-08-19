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

export const parseMessagePropertiesText = (text: string): Record<string, string> => {
  const properties: Record<string, string> = {};
  for (const token of text.split(/[\n,]+/)) {
    const entry = token.trim();
    if (!entry) continue;

    const equalsIndex = entry.indexOf('=');
    if (equalsIndex < 0) {
      throw new Error(`属性格式无效：“${entry}”，请使用 key=value`);
    }
    const key = entry.slice(0, equalsIndex).trim();
    if (!key) {
      throw new Error('属性名不能为空');
    }
    if (Object.prototype.hasOwnProperty.call(properties, key)) {
      throw new Error(`属性名重复：“${key}”`);
    }
    properties[key] = entry.slice(equalsIndex + 1).trim();
  }
  return properties;
};
