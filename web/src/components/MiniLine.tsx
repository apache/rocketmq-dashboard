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

let _lineId = 0;

interface MiniLineProps {
  data: number[];
  color?: string;
  height?: number;
  width?: number;
  /** Fill area under the curve */
  fill?: boolean;
  /** Stroke width */
  strokeWidth?: number;
  /** Show dot on last data point */
  showDot?: boolean;
  /** Animate on mount */
  animated?: boolean;
  /** Make SVG responsive (width=100%, preserves aspect ratio) */
  responsive?: boolean;
}

const MiniLine = ({
  data,
  color = '#1677ff',
  height = 32,
  width = 120,
  fill = true,
  strokeWidth = 2,
  showDot = true,
  animated = true,
  responsive = false,
}: MiniLineProps) => {
  const max = Math.max(...data, 1);
  const min = Math.min(...data, 0);
  const range = max - min || 1;

  const pad = 4;
  const innerW = width - pad * 2;
  const innerH = height - pad * 2;

  const gradientId = useMemo(() => `ml-grad-${++_lineId}`, []);
  const glowId = useMemo(() => `ml-glow-${++_lineId}`, []);

  if (data.length < 2) return null;

  // Build smooth Catmull-Rom → Bezier control points
  const points = data.map((v, i) => ({
    x: pad + (i / (data.length - 1)) * innerW,
    y: pad + innerH - ((v - min) / range) * innerH,
  }));

  // Convert points to a smooth SVG path using cardinal spline
  const smoothPath = (() => {
    if (points.length < 2) return '';
    let d = `M${points[0].x},${points[0].y}`;
    for (let i = 0; i < points.length - 1; i++) {
      const p0 = points[Math.max(0, i - 1)];
      const p1 = points[i];
      const p2 = points[i + 1];
      const p3 = points[Math.min(points.length - 1, i + 2)];
      const tension = 0.3;
      const cp1x = p1.x + (p2.x - p0.x) * tension;
      const cp1y = p1.y + (p2.y - p0.y) * tension;
      const cp2x = p2.x - (p3.x - p1.x) * tension;
      const cp2y = p2.y - (p3.y - p1.y) * tension;
      d += ` C${cp1x},${cp1y} ${cp2x},${cp2y} ${p2.x},${p2.y}`;
    }
    return d;
  })();

  const areaPath = `${smoothPath} L${pad + innerW},${height - pad} L${pad},${height - pad} Z`;

  const lastPoint = points[points.length - 1];

  return (
    <svg
      width={responsive ? '100%' : width}
      height={height}
      viewBox={responsive ? `0 0 ${width} ${height}` : undefined}
      preserveAspectRatio={responsive ? 'none' : undefined}
      style={{ display: 'block', overflow: 'visible' }}
    >
      {' '}
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity={0.3} />
          <stop offset="100%" stopColor={color} stopOpacity={0.02} />
        </linearGradient>
        <filter id={glowId}>
          <feGaussianBlur stdDeviation="2" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>
      {fill && <path d={areaPath} fill={`url(#${gradientId})`} />}
      <path
        d={smoothPath}
        fill="none"
        stroke={color}
        strokeWidth={strokeWidth}
        strokeLinecap="round"
        strokeLinejoin="round"
        filter={`url(#${glowId})`}
        style={
          animated
            ? {
                strokeDasharray: 500,
                strokeDashoffset: 500,
                animation: 'miniLineDraw 1s ease-out forwards',
              }
            : undefined
        }
      />
      {showDot && (
        <>
          <circle cx={lastPoint.x} cy={lastPoint.y} r={4} fill={color} opacity={0.2} />
          <circle
            cx={lastPoint.x}
            cy={lastPoint.y}
            r={2.5}
            fill="#fff"
            stroke={color}
            strokeWidth={1.5}
          />
        </>
      )}
      <style>{`
        @keyframes miniLineDraw {
          to { stroke-dashoffset: 0; }
        }
      `}</style>
    </svg>
  );
};

export default MiniLine;
