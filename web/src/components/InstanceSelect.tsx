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

import { Select } from 'antd';
import type { CSSProperties } from 'react';

export interface InstanceOption {
  value: number;
  label: string;
}

interface InstanceSelectProps {
  value?: number;
  onChange: (value: number, option?: unknown) => void;
  options: InstanceOption[];
  style?: CSSProperties;
  placeholder?: string;
}

/**
 * 实例维度页面统一的实例选择器：支持输入并按实例 ID 筛选（showSearch），
 * 选中后由页面通过 onChange 切换路由实例。
 */
export function InstanceSelect({
  value,
  onChange,
  options,
  style,
  placeholder = '选择实例',
}: InstanceSelectProps) {
  return (
    <Select
      showSearch
      allowClear
      placeholder={placeholder}
      value={value ?? undefined}
      onChange={(next, option) => {
        if (next === undefined || next === null) {
          const first = options[0]?.value;
          if (first !== undefined) {
            onChange(first);
          }
          return;
        }
        onChange(next, option);
      }}
      options={options}
      optionFilterProp="label"
      filterOption={(input, option) =>
        String(option?.label ?? '')
          .toLowerCase()
          .includes(input.toLowerCase())
      }
      notFoundContent="暂无匹配实例"
      style={style ?? { width: 220 }}
    />
  );
}

export default InstanceSelect;
