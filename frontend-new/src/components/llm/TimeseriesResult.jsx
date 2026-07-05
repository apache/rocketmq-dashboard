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

import React, { useRef, useEffect } from 'react';
import * as echarts from 'echarts';

function TimeseriesResult({ data, title }) {
    const chartRef = useRef(null);
    const chartInstance = useRef(null);

    useEffect(() => {
        if (!chartRef.current || !data || data.length === 0) return;

        if (chartInstance.current) {
            chartInstance.current.dispose();
        }

        chartInstance.current = echarts.init(chartRef.current);

        const names = data.map(item => item.name || item.label || '');
        const values = data.map(item => item.value || item.count || 0);

        const isTimeData = names.length > 0 && !isNaN(Date.parse(names[0]));

        const option = {
            title: {
                text: title || '',
                left: 'center',
                textStyle: { fontSize: 14 },
            },
            tooltip: {
                trigger: 'axis',
            },
            grid: {
                left: '3%',
                right: '4%',
                bottom: '3%',
                containLabel: true,
            },
            xAxis: {
                type: isTimeData ? 'time' : 'category',
                data: isTimeData ? undefined : names,
                axisLabel: {
                    rotate: names.length > 8 ? 45 : 0,
                },
            },
            yAxis: {
                type: 'value',
            },
            series: [
                {
                    name: title || 'Value',
                    type: 'bar',
                    data: isTimeData
                        ? data.map(item => [item.name || item.label, item.value || item.count])
                        : values,
                    itemStyle: {
                        color: '#1677ff',
                    },
                },
            ],
        };

        chartInstance.current.setOption(option);

        const handleResize = () => {
            chartInstance.current?.resize();
        };
        window.addEventListener('resize', handleResize);

        return () => {
            window.removeEventListener('resize', handleResize);
            chartInstance.current?.dispose();
        };
    }, [data, title]);

    if (!data || data.length === 0) {
        return <div style={{ color: '#999', padding: '8px' }}>No data available</div>;
    }

    return (
        <div
            ref={chartRef}
            style={{ width: '100%', height: '300px' }}
        />
    );
}

export default TimeseriesResult;
