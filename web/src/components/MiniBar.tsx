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

import { useMemo } from 'react';

let _barId = 0;

interface MiniBarProps {
  data: number[];
  color?: string;
  height?: number;
  width?: number;
  /** Show rounded top caps on bars */
  rounded?: boolean;
  /** Animate bars on mount */
  animated?: boolean;
}

const MiniBar = ({
  data,
  color = '#1677ff',
  height = 32,
  width = 120,
  rounded = true,
  animated = true,
}: MiniBarProps) => {
  const max = Math.max(...data, 1);
  const gap = 2;
  const barWidth = Math.max(3, (width - (data.length - 1) * gap) / data.length);

  const gradientId = useMemo(() => `mb-grad-${++_barId}`, []);

  return (
    <svg width={width} height={height} style={{ display: 'block', overflow: 'visible' }} role="img">
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity={0.95} />
          <stop offset="100%" stopColor={color} stopOpacity={0.35} />
        </linearGradient>
      </defs>
      {data.map((value, i) => {
        const barH = Math.max(3, (value / max) * (height - 2));
        const x = i * (barWidth + gap);
        const y = height - barH;
        const opacity = 0.5 + (i / data.length) * 0.5;
        const radius = rounded ? Math.min(barWidth / 2, 3) : 0;

        return (
          <rect
            key={i}
            x={x}
            y={y}
            width={barWidth}
            height={barH}
            rx={radius}
            ry={radius}
            fill={`url(#${gradientId})`}
            opacity={opacity}
            style={
              animated
                ? {
                    animation: `miniBarGrow 0.5s ease-out ${i * 30}ms both`,
                  }
                : undefined
            }
          />
        );
      })}
      {/* Inline keyframes — avoids global CSS dependency */}
      <style>{`
        @keyframes miniBarGrow {
          from { transform: scaleY(0); transform-origin: bottom; }
          to   { transform: scaleY(1); transform-origin: bottom; }
        }
      `}</style>
    </svg>
  );
};

export default MiniBar;
