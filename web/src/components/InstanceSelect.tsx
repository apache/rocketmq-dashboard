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
import { useEffect, useMemo, useState } from 'react';
import type { CSSProperties } from 'react';
import { useLang } from '../i18n/LangContext';

export const INSTANCE_RECENTS_STORAGE_KEY = 'rocketmq-studio-recent-instances';
const RECENT_INSTANCE_LIMIT = 5;

function readRecentInstances(): string[] {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(INSTANCE_RECENTS_STORAGE_KEY) ?? '[]');
    if (!Array.isArray(parsed)) return [];
    return [...new Set(parsed.filter((value): value is string => typeof value === 'string'))].slice(
      0,
      RECENT_INSTANCE_LIMIT,
    );
  } catch {
    return [];
  }
}

function writeRecentInstances(values: string[]): void {
  try {
    localStorage.setItem(INSTANCE_RECENTS_STORAGE_KEY, JSON.stringify(values));
  } catch {
    // Instance selection still works when browser storage is unavailable.
  }
}

export interface InstanceOption {
  value: string;
  label: string;
}

interface InstanceSelectProps {
  value?: string;
  onChange: (value: string, option?: unknown) => void;
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
  const { lang } = useLang();
  const [recentValues, setRecentValues] = useState(readRecentInstances);

  const validRecentValues = useMemo(() => {
    const available = new Set(options.map((option) => option.value));
    return recentValues.filter((recent) => available.has(recent));
  }, [options, recentValues]);

  useEffect(() => {
    if (validRecentValues.length !== recentValues.length) {
      writeRecentInstances(validRecentValues);
    }
  }, [recentValues.length, validRecentValues]);

  const selectOptions = useMemo(() => {
    const recentSet = new Set(validRecentValues);
    const recent = validRecentValues
      .map((recentValue) => options.find((option) => option.value === recentValue))
      .filter((option): option is InstanceOption => Boolean(option));

    if (recent.length === 0) return options;

    return [
      { label: lang === 'zh' ? '最近使用' : 'Recent', options: recent },
      {
        label: lang === 'zh' ? '全部实例' : 'All instances',
        options: options.filter((option) => !recentSet.has(option.value)),
      },
    ];
  }, [lang, options, validRecentValues]);

  const remember = (selectedValue: string) => {
    const available = new Set(options.map((option) => option.value));
    setRecentValues((current) => {
      const next = [
        selectedValue,
        ...current.filter((item) => item !== selectedValue && available.has(item)),
      ].slice(0, RECENT_INSTANCE_LIMIT);
      writeRecentInstances(next);
      return next;
    });
  };

  return (
    <Select
      showSearch
      allowClear
      placeholder={placeholder}
      value={value || undefined}
      onChange={(next, option) => {
        if (next === undefined || next === null) {
          const first = options[0]?.value;
          if (first) {
            remember(first);
            onChange(first);
          }
          return;
        }
        remember(next);
        onChange(next, option);
      }}
      options={selectOptions}
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
