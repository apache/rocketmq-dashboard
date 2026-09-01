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

interface MiniBarProps {
  data: number[];
  color?: string;
  height?: number;
  width?: number;
  label?: string;
}

const MiniBar = ({ data, color = '#1677ff', height = 32, width = 120, label }: MiniBarProps) => {
  // Non-array data (e.g. a malformed wire payload) renders the empty state, and non-finite
  // elements (NaN/Infinity) are dropped so they cannot poison the scale or the bar heights.
  const values = (Array.isArray(data) ? data : []).filter((value) => Number.isFinite(value));
  if (values.length === 0) {
    return (
      <span aria-label={label || '暂无趋势数据'} style={{ color: '#8c8c8c' }}>
        —
      </span>
    );
  }

  let max = 1;
  for (const value of values) {
    if (value > max) max = value;
  }
  const barWidth = Math.max(2, (width - (values.length - 1) * 2) / values.length);

  return (
    <div
      role="img"
      aria-label={label || `趋势数据：${values.join('、')}`}
      style={{
        display: 'inline-flex',
        alignItems: 'flex-end',
        gap: 2,
        height,
        width,
      }}
    >
      {values.map((value, i) => (
        <div
          key={i}
          style={{
            width: barWidth,
            height: `${value === 0 ? 0 : Math.max(4, (value / max) * height)}px`,
            backgroundColor: color,
            borderRadius: 2,
            opacity: 0.3 + (i / values.length) * 0.7,
            transition: 'height 0.3s ease',
          }}
        />
      ))}
    </div>
  );
};

export default MiniBar;
