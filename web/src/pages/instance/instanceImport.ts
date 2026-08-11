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

import type { InstanceImportItem } from '../../api/instance';

export interface ParsedInstanceBundle {
  schemaVersion?: number;
  instances: InstanceImportItem[];
}

export function parseInstanceBundle(contents: string): ParsedInstanceBundle {
  const value: unknown = JSON.parse(contents);
  if (Array.isArray(value)) return { instances: value as InstanceImportItem[] };
  if (!value || typeof value !== 'object') throw new Error('导入文件必须是 JSON 对象或数组');
  const bundle = value as { schemaVersion?: number; instances?: unknown };
  if (bundle.schemaVersion !== undefined && bundle.schemaVersion !== 1) {
    throw new Error(`不支持的 schemaVersion：${bundle.schemaVersion}`);
  }
  if (!Array.isArray(bundle.instances) || bundle.instances.length === 0) {
    throw new Error('导入文件中没有实例记录');
  }
  if (bundle.instances.length > 200) throw new Error('单次最多导入 200 个实例');
  return {
    schemaVersion: bundle.schemaVersion,
    instances: bundle.instances as InstanceImportItem[],
  };
}
