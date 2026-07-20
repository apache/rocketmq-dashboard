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
  if (!data.length) {
    return <span aria-label={label || '暂无趋势数据'} style={{ color: '#8c8c8c' }}>—</span>;
  }

  const max = Math.max(...data, 1);
  const barWidth = Math.max(2, (width - (data.length - 1) * 2) / data.length);

  return (
    <div
      role="img"
      aria-label={label || `趋势数据：${data.join('、')}`}
      style={{
        display: 'inline-flex',
        alignItems: 'flex-end',
        gap: 2,
        height,
        width,
      }}
    >
      {data.map((value, i) => (
        <div
          key={i}
          style={{
            width: barWidth,
            height: `${Math.max(4, (value / max) * height)}px`,
            backgroundColor: color,
            borderRadius: 2,
            opacity: 0.3 + (i / data.length) * 0.7,
            transition: 'height 0.3s ease',
          }}
        />
      ))}
    </div>
  );
};

export default MiniBar;
