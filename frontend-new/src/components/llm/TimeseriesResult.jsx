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

import React, { useRef, useEffect, useState } from 'react';
import { Tooltip } from 'antd';

/**
 * TimeseriesResult — 使用 SVG 渲染简单折线图
 * - 支持时间轴和分类轴
 * - 自动检测数据格式
 * - 紧凑尺寸适配聊天面板
 * - 悬停 tooltip 显示数值
 */
function TimeseriesResult({ data, title }) {
    const containerRef = useRef(null);
    const [dimensions, setDimensions] = useState({ width: 380, height: 200 });
    const [hoveredIndex, setHoveredIndex] = useState(null);

    // Responsive sizing
    useEffect(() => {
        if (!containerRef.current) return;
        const observer = new ResizeObserver(entries => {
            const { width } = entries[0].contentRect;
            setDimensions({ width: Math.max(width, 200), height: 200 });
        });
        observer.observe(containerRef.current);
        return () => observer.disconnect();
    }, []);

    if (!data || data.length === 0) {
        return <div className="timeseries-empty">暂无时序数据</div>;
    }

    // Normalize data points
    const points = data.map((item, i) => ({
        label: item.name || item.label || item.time || item.timestamp || item.date || `${i + 1}`,
        value: Number(item.value || item.count || item.metric || 0),
    }));

    const { width, height } = dimensions;
    const padding = { top: 24, right: 16, bottom: 36, left: 48 };
    const chartW = width - padding.left - padding.right;
    const chartH = height - padding.top - padding.bottom;

    const maxVal = Math.max(...points.map(p => p.value), 1);
    const minVal = Math.min(...points.map(p => p.value), 0);
    const valRange = maxVal - minVal || 1;

    // Scale functions
    const xScale = (i) => padding.left + (points.length > 1 ? (i / (points.length - 1)) * chartW : chartW / 2);
    const yScale = (v) => padding.top + chartH - ((v - minVal) / valRange) * chartH;

    // Build SVG path
    const linePath = points.map((p, i) => {
        const x = xScale(i);
        const y = yScale(p.value);
        return `${i === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`;
    }).join(' ');

    // Build area path (filled under the line)
    const areaPath = linePath +
        ` L ${xScale(points.length - 1).toFixed(1)} ${yScale(minVal).toFixed(1)}` +
        ` L ${xScale(0).toFixed(1)} ${yScale(minVal).toFixed(1)} Z`;

    // Y-axis ticks (4 ticks)
    const yTicks = [];
    for (let i = 0; i <= 3; i++) {
        const val = minVal + (valRange * i) / 3;
        yTicks.push({ val, y: yScale(val), label: formatNumber(val) });
    }

    // X-axis labels (show max ~6 labels)
    const xLabelStep = Math.max(1, Math.ceil(points.length / 6));
    const xLabels = points.filter((_, i) => i % xLabelStep === 0 || i === points.length - 1);

    return (
        <div className="timeseries-result" ref={containerRef}>
            {title && <div className="timeseries-title">{title}</div>}
            <svg
                width={width}
                height={height}
                viewBox={`0 0 ${width} ${height}`}
                className="timeseries-svg"
            >
                {/* Grid lines */}
                {yTicks.map((tick, i) => (
                    <g key={i}>
                        <line
                            x1={padding.left}
                            y1={tick.y}
                            x2={width - padding.right}
                            y2={tick.y}
                            stroke="#e8e8e8"
                            strokeDasharray="4,2"
                        />
                        <text
                            x={padding.left - 6}
                            y={tick.y + 4}
                            textAnchor="end"
                            fill="#8c8c8c"
                            fontSize={10}
                        >
                            {tick.label}
                        </text>
                    </g>
                ))}

                {/* Area fill */}
                <path d={areaPath} fill="rgba(22, 119, 255, 0.08)" />

                {/* Line */}
                <path d={linePath} fill="none" stroke="#1677ff" strokeWidth={2} strokeLinejoin="round" />

                {/* Data points + hover */}
                {points.map((p, i) => (
                    <g key={i}>
                        <circle
                            cx={xScale(i)}
                            cy={yScale(p.value)}
                            r={hoveredIndex === i ? 5 : 3}
                            fill={hoveredIndex === i ? '#1677ff' : '#ffffff'}
                            stroke="#1677ff"
                            strokeWidth={2}
                            style={{ cursor: 'pointer' }}
                            onMouseEnter={() => setHoveredIndex(i)}
                            onMouseLeave={() => setHoveredIndex(null)}
                        />
                        {hoveredIndex === i && (
                            <g>
                                <rect
                                    x={xScale(i) - 40}
                                    y={yScale(p.value) - 28}
                                    width={80}
                                    height={22}
                                    rx={4}
                                    fill="rgba(0,0,0,0.75)"
                                />
                                <text
                                    x={xScale(i)}
                                    y={yScale(p.value) - 14}
                                    textAnchor="middle"
                                    fill="#ffffff"
                                    fontSize={11}
                                >
                                    {formatNumber(p.value)}
                                </text>
                            </g>
                        )}
                    </g>
                ))}

                {/* X-axis labels */}
                {xLabels.map((p, i) => {
                    const origIndex = points.indexOf(p);
                    return (
                        <text
                            key={i}
                            x={xScale(origIndex)}
                            y={height - 6}
                            textAnchor="middle"
                            fill="#8c8c8c"
                            fontSize={10}
                        >
                            {truncateLabel(p.label, 8)}
                        </text>
                    );
                })}
            </svg>
        </div>
    );
}

function formatNumber(val) {
    if (val >= 1000000) return (val / 1000000).toFixed(1) + 'M';
    if (val >= 1000) return (val / 1000).toFixed(1) + 'K';
    return Number.isInteger(val) ? String(val) : val.toFixed(1);
}

function truncateLabel(label, maxLen) {
    if (!label) return '';
    return label.length > maxLen ? label.slice(0, maxLen - 1) + '…' : label;
}

export default TimeseriesResult;