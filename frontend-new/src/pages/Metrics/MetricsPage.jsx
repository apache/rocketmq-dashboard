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

import React, {useEffect, useState, useCallback, useMemo} from 'react';
import {
    Card,
    Row,
    Col,
    Table,
    Tag,
    Statistic,
    Spin,
    Button,
    Switch,
    Space,
    Tooltip,
    Progress,
    Descriptions,
} from 'antd';
import {
    ReloadOutlined,
    DashboardOutlined,
    DatabaseOutlined,
    BarChartOutlined,
    TeamOutlined,
    CloudServerOutlined,
    LineChartOutlined,
} from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import {remoteApi} from '../../api/remoteApi/remoteApi';
import {useLanguage} from '../../i18n/LanguageContext';

const STATUS_COLORS = {
    healthy: 'green',
    warning: 'orange',
    critical: 'red',
};

const ROLE_COLORS = {
    MASTER: 'blue',
    SLAVE: 'default',
};

// Grafana-style chart theme
const CHART_THEME = {
    bg: 'transparent',
    borderColor: '#30363d',
    textColor: '#c9d1d9',
    subTextColor: '#8b949e',
    colors: ['#58a6ff', '#3fb950', '#d29922', '#f85149', '#bc8cff', '#39d2c0', '#f0883e', '#8b949e'],
};

const MetricsPage = () => {
    const {t} = useLanguage();
    const [loading, setLoading] = useState(false);
    const [autoRefresh, setAutoRefresh] = useState(false);
    const [overview, setOverview] = useState(null);

    const fetchOverview = useCallback(async () => {
        setLoading(true);
        try {
            const result = await remoteApi.getMetricsOverview();
            if (result && result.status === 0 && result.data) {
                setOverview(result.data);
            } else {
                console.error(t.METRICS_FETCH_FAILED || 'Failed to fetch metrics');
            }
        } catch (error) {
            console.error('Failed to fetch metrics overview:', error);
        } finally {
            setLoading(false);
        }
    }, [t]);

    useEffect(() => {
        fetchOverview();
    }, [fetchOverview]);

    useEffect(() => {
        if (!autoRefresh) return;
        const timer = setInterval(fetchOverview, 10000);
        return () => clearInterval(timer);
    }, [autoRefresh, fetchOverview]);

    const formatNumber = (num) => {
        if (num === undefined || num === null) return '-';
        if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
        if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
        return String(num);
    };

    const formatBytes = (bytes) => {
        if (!bytes) return '-';
        if (bytes >= 1024 * 1024 * 1024) return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB';
        if (bytes >= 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
        return (bytes / 1024).toFixed(1) + ' KB';
    };

    // --- Grafana-style ECharts options ---

    const getBrokerTpsChartOption = useMemo(() => {
        if (!overview?.brokers?.length) return null;
        const brokers = overview.brokers;
        const names = brokers.map(b => b.brokerName || b.brokerId);
        return {
            backgroundColor: CHART_THEME.bg,
            tooltip: {trigger: 'axis', axisPointer: {type: 'shadow'}},
            legend: {data: ['TPS In', 'TPS Out'], textStyle: {color: CHART_THEME.subTextColor}},
            grid: {left: 60, right: 30, top: 40, bottom: 30},
            xAxis: {type: 'category', data: names, axisLabel: {color: CHART_THEME.subTextColor}},
            yAxis: {type: 'value', axisLabel: {color: CHART_THEME.subTextColor}, splitLine: {lineStyle: {color: '#21262d'}}},
            series: [
                {name: 'TPS In', type: 'bar', data: brokers.map(b => b.tpsIn || 0), itemStyle: {color: CHART_THEME.colors[0]}},
                {name: 'TPS Out', type: 'bar', data: brokers.map(b => b.tpsOut || 0), itemStyle: {color: CHART_THEME.colors[1]}},
            ],
        };
    }, [overview]);

    const getBrokerResourceGaugeOption = useMemo(() => {
        if (!overview?.brokers?.length) return null;
        const brokers = overview.brokers;
        return {
            backgroundColor: CHART_THEME.bg,
            tooltip: {trigger: 'item', formatter: '{b}: {c}%'},
            series: brokers.slice(0, 6).flatMap((b, i) => [
                {
                    type: 'gauge', center: [`${(i % 3) * 33.3 + 16.65}%`, i < 3 ? '35%' : '75%'],
                    radius: '28%',
                    startAngle: 220, endAngle: -40, min: 0, max: 100,
                    progress: {show: true, width: 8},
                    axisLine: {lineStyle: {width: 8, color: [[0.6, '#3fb950'], [0.8, '#d29922'], [1, '#f85149']]}},
                    axisTick: {show: false}, splitLine: {show: false}, axisLabel: {show: false},
                    pointer: {show: false},
                    title: {offsetCenter: [0, '70%'], fontSize: 11, color: CHART_THEME.subTextColor},
                    detail: {offsetCenter: [0, '30%'], fontSize: 14, fontWeight: 'bold', color: CHART_THEME.textColor, formatter: '{value}%'},
                    data: [{value: parseFloat((b.cpuUsage || 0).toFixed(1)), name: `${b.brokerName || b.brokerId} CPU`}],
                },
                {
                    type: 'gauge', center: [`${(i % 3) * 33.3 + 16.65 + 11}%`, i < 3 ? '35%' : '75%'],
                    radius: '28%',
                    startAngle: 220, endAngle: -40, min: 0, max: 100,
                    progress: {show: true, width: 8},
                    axisLine: {lineStyle: {width: 8, color: [[0.7, '#3fb950'], [0.85, '#d29922'], [1, '#f85149']]}},
                    axisTick: {show: false}, splitLine: {show: false}, axisLabel: {show: false},
                    pointer: {show: false},
                    title: {offsetCenter: [0, '70%'], fontSize: 11, color: CHART_THEME.subTextColor},
                    detail: {offsetCenter: [0, '30%'], fontSize: 14, fontWeight: 'bold', color: CHART_THEME.textColor, formatter: '{value}%'},
                    data: [{value: parseFloat((b.memoryUsage || 0).toFixed(1)), name: 'MEM'}],
                },
            ]),
        };
    }, [overview]);

    const getSystemResourceChartOption = useMemo(() => {
        const sr = overview?.systemResources;
        if (!sr) return null;
        return {
            backgroundColor: CHART_THEME.bg,
            tooltip: {trigger: 'item', formatter: '{b}: {c}%'},
            series: [
                {
                    type: 'gauge', center: ['25%', '50%'], radius: '70%',
                    startAngle: 220, endAngle: -40, min: 0, max: 100,
                    progress: {show: true, width: 14},
                    axisLine: {lineStyle: {width: 14, color: [[0.6, '#3fb950'], [0.8, '#d29922'], [1, '#f85149']]}},
                    axisTick: {show: false}, splitLine: {show: false}, axisLabel: {show: false},
                    pointer: {show: false},
                    title: {offsetCenter: [0, '75%'], fontSize: 14, color: CHART_THEME.subTextColor},
                    detail: {offsetCenter: [0, '35%'], fontSize: 28, fontWeight: 'bold', color: CHART_THEME.textColor, formatter: '{value}%'},
                    data: [{value: parseFloat((sr.cpuUsagePercent || 0).toFixed(1)), name: 'CPU Usage'}],
                },
                {
                    type: 'gauge', center: ['75%', '50%'], radius: '70%',
                    startAngle: 220, endAngle: -40, min: 0, max: 100,
                    progress: {show: true, width: 14},
                    axisLine: {lineStyle: {width: 14, color: [[0.7, '#3fb950'], [0.85, '#d29922'], [1, '#f85149']]}},
                    axisTick: {show: false}, splitLine: {show: false}, axisLabel: {show: false},
                    pointer: {show: false},
                    title: {offsetCenter: [0, '75%'], fontSize: 14, color: CHART_THEME.subTextColor},
                    detail: {offsetCenter: [0, '35%'], fontSize: 28, fontWeight: 'bold', color: CHART_THEME.textColor, formatter: '{value}%'},
                    data: [{value: parseFloat((sr.memoryUsagePercent || 0).toFixed(1)), name: 'Memory Usage'}],
                },
            ],
        };
    }, [overview]);

    const getTopicBacklogChartOption = useMemo(() => {
        if (!overview?.topTopics?.length) return null;
        const topics = overview.topTopics.slice(0, 10);
        return {
            backgroundColor: CHART_THEME.bg,
            tooltip: {trigger: 'axis', axisPointer: {type: 'shadow'}},
            grid: {left: 150, right: 30, top: 10, bottom: 20},
            xAxis: {type: 'value', axisLabel: {color: CHART_THEME.subTextColor}, splitLine: {lineStyle: {color: '#21262d'}}},
            yAxis: {type: 'category', data: topics.map(t => t.topicName), axisLabel: {color: CHART_THEME.subTextColor, width: 130, overflow: 'truncate'}},
            series: [{
                type: 'bar', data: topics.map(t => t.messageBacklog || 0),
                itemStyle: {
                    color: (params) => {
                        const v = params.value;
                        return v > 100000 ? '#f85149' : v > 10000 ? '#d29922' : '#3fb950';
                    },
                },
                barWidth: '60%',
            }],
        };
    }, [overview]);

    const getConsumerGroupDiffChartOption = useMemo(() => {
        if (!overview?.consumerGroups?.length) return null;
        const groups = overview.consumerGroups.slice(0, 10);
        return {
            backgroundColor: CHART_THEME.bg,
            tooltip: {trigger: 'axis', axisPointer: {type: 'shadow'}},
            grid: {left: 150, right: 30, top: 10, bottom: 20},
            xAxis: {type: 'value', axisLabel: {color: CHART_THEME.subTextColor}, splitLine: {lineStyle: {color: '#21262d'}}},
            yAxis: {type: 'category', data: groups.map(g => g.groupName), axisLabel: {color: CHART_THEME.subTextColor, width: 130, overflow: 'truncate'}},
            series: [{
                type: 'bar', data: groups.map(g => g.totalDiff || 0),
                itemStyle: {
                    color: (params) => {
                        const v = params.value;
                        return v > 10000 ? '#f85149' : v > 1000 ? '#d29922' : '#58a6ff';
                    },
                },
                barWidth: '60%',
            }],
        };
    }, [overview]);

    const getBrokerStatusPieOption = useMemo(() => {
        if (!overview?.brokers?.length) return null;
        const brokers = overview.brokers;
        const counts = {healthy: 0, warning: 0, critical: 0};
        brokers.forEach(b => { counts[b.status] = (counts[b.status] || 0) + 1; });
        return {
            backgroundColor: CHART_THEME.bg,
            tooltip: {trigger: 'item', formatter: '{b}: {c} ({d}%)'},
            legend: {bottom: 0, textStyle: {color: CHART_THEME.subTextColor}},
            series: [{
                type: 'pie', radius: ['40%', '70%'], center: ['50%', '45%'],
                avoidLabelOverlap: false,
                itemStyle: {borderRadius: 6, borderColor: '#0d1117', borderWidth: 2},
                label: {show: true, color: CHART_THEME.textColor},
                data: [
                    {value: counts.healthy, name: 'Healthy', itemStyle: {color: '#3fb950'}},
                    {value: counts.warning, name: 'Warning', itemStyle: {color: '#d29922'}},
                    {value: counts.critical, name: 'Critical', itemStyle: {color: '#f85149'}},
                ].filter(d => d.value > 0),
            }],
        };
    }, [overview]);

    const getDiskUsageChartOption = useMemo(() => {
        if (!overview?.systemResources) return null;
        const sr = overview.systemResources;
        const diskUsed = sr.diskUsedGb || 0;
        const diskTotal = sr.diskTotalGb || 1;
        return {
            backgroundColor: CHART_THEME.bg,
            tooltip: {trigger: 'item', formatter: '{b}: {c}%'},
            series: [{
                type: 'gauge', center: ['50%', '55%'], radius: '75%',
                startAngle: 220, endAngle: -40, min: 0, max: 100,
                progress: {show: true, width: 16},
                axisLine: {lineStyle: {width: 16, color: [[0.75, '#3fb950'], [0.9, '#d29922'], [1, '#f85149']]}},
                axisTick: {show: false}, splitLine: {show: false}, axisLabel: {show: false},
                pointer: {show: false},
                title: {offsetCenter: [0, '75%'], fontSize: 14, color: CHART_THEME.subTextColor, formatter: `{a|${diskUsed} GB / ${diskTotal} GB}`, rich: {a: {fontSize: 12, color: CHART_THEME.subTextColor}}},
                detail: {offsetCenter: [0, '35%'], fontSize: 32, fontWeight: 'bold', color: CHART_THEME.textColor, formatter: '{value}%'},
                data: [{value: parseFloat((sr.diskUsagePercent || 0).toFixed(1)), name: 'Disk Usage'}],
            }],
        };
    }, [overview]);

    const brokerColumns = [
        {title: t.METRICS_BROKER_ID, dataIndex: 'brokerId', key: 'brokerId', width: 130},
        {title: t.METRICS_BROKER_NAME, dataIndex: 'brokerName', key: 'brokerName', width: 110},
        {title: t.METRICS_ADDRESS, dataIndex: 'address', key: 'address', width: 160},
        {
            title: t.METRICS_ROLE, dataIndex: 'role', key: 'role', width: 90,
            render: (role) => <Tag color={ROLE_COLORS[role] || 'default'}>{role === 'MASTER' ? (t.METRICS_MASTER || 'Master') : (t.METRICS_SLAVE || 'Slave')}</Tag>,
        },
        {
            title: t.METRICS_CPU_USAGE, dataIndex: 'cpuUsage', key: 'cpuUsage', width: 120,
            render: (val) => <Progress percent={val ? parseFloat(val.toFixed(1)) : 0} size="small"
                status={val > 80 ? 'exception' : val > 60 ? 'active' : 'normal'} />,
        },
        {
            title: t.METRICS_MEMORY_USAGE, dataIndex: 'memoryUsage', key: 'memoryUsage', width: 120,
            render: (val) => <Progress percent={val ? parseFloat(val.toFixed(1)) : 0} size="small"
                status={val > 85 ? 'exception' : val > 70 ? 'active' : 'normal'} />,
        },
        {
            title: t.METRICS_DISK_USAGE, key: 'diskUsage', width: 120,
            render: (_, r) => {
                const pct = r.diskTotal > 0 ? ((r.diskUsage / r.diskTotal) * 100).toFixed(1) : 0;
                return <Progress percent={parseFloat(pct)} size="small"
                    status={pct > 90 ? 'exception' : pct > 75 ? 'active' : 'normal'} />;
            },
        },
        {title: t.METRICS_TPS_IN, dataIndex: 'tpsIn', key: 'tpsIn', width: 90, render: (v) => formatNumber(v)},
        {title: t.METRICS_TPS_OUT, dataIndex: 'tpsOut', key: 'tpsOut', width: 90, render: (v) => formatNumber(v)},
        {title: t.METRICS_PUT_TPS, dataIndex: 'putTps', key: 'putTps', width: 100, render: (v) => v ? v.toFixed(1) : '-'},
        {title: t.METRICS_GET_TRANSFER_TPS, dataIndex: 'getTransferTps', key: 'getTransferTps', width: 110, render: (v) => v ? v.toFixed(1) : '-'},
        {
            title: t.METRICS_MESSAGE_BACKLOG, dataIndex: 'messageBacklog', key: 'messageBacklog', width: 110,
            render: (v) => <span style={{color: v > 5000 ? '#ff4d4f' : v > 1000 ? '#faad14' : 'inherit'}}>{formatNumber(v)}</span>,
        },
        {
            title: t.METRICS_STATUS, dataIndex: 'status', key: 'status', width: 90,
            render: (s) => <Tag color={STATUS_COLORS[s] || 'default'}>{s ? s.charAt(0).toUpperCase() + s.slice(1) : '-'}</Tag>,
        },
    ];

    const topicColumns = [
        {title: t.METRICS_TOPIC_NAME, dataIndex: 'topicName', key: 'topicName', width: 180},
        {title: t.METRICS_QUEUE_COUNT, dataIndex: 'queueCount', key: 'queueCount', width: 100},
        {title: t.METRICS_TOTAL_MSG_COUNT, dataIndex: 'totalMessageCount', key: 'totalMessageCount', width: 130, render: (v) => formatNumber(v)},
        {title: t.METRICS_MSG_TODAY, dataIndex: 'messageCountToday', key: 'messageCountToday', width: 120, render: (v) => formatNumber(v)},
        {title: t.METRICS_PUT_TPS, dataIndex: 'putTps', key: 'putTps', width: 100, render: (v) => v ? v.toFixed(1) : '-'},
        {title: t.METRICS_GET_TRANSFER_TPS, dataIndex: 'getTransferTps', key: 'getTransferTps', width: 120, render: (v) => v ? v.toFixed(1) : '-'},
        {title: t.METRICS_MAX_OFFSET, dataIndex: 'maxOffset', key: 'maxOffset', width: 110, render: (v) => formatNumber(v)},
        {title: t.METRICS_MIN_OFFSET, dataIndex: 'minOffset', key: 'minOffset', width: 110, render: (v) => formatNumber(v)},
        {
            title: t.METRICS_MESSAGE_BACKLOG, dataIndex: 'messageBacklog', key: 'messageBacklog', width: 120,
            render: (v) => <span style={{color: v > 100000 ? '#ff4d4f' : v > 10000 ? '#faad14' : 'inherit'}}>{formatNumber(v)}</span>,
        },
    ];

    const consumerGroupColumns = [
        {title: t.METRICS_GROUP_NAME, dataIndex: 'groupName', key: 'groupName', width: 200},
        {title: t.METRICS_CONSUMER_COUNT, dataIndex: 'consumerCount', key: 'consumerCount', width: 110},
        {
            title: t.METRICS_TOTAL_DIFF, dataIndex: 'totalDiff', key: 'totalDiff', width: 110,
            render: (v) => <span style={{color: v > 10000 ? '#ff4d4f' : v > 1000 ? '#faad14' : 'inherit'}}>{formatNumber(v)}</span>,
        },
        {title: t.METRICS_CONSUME_TPS, dataIndex: 'consumeTps', key: 'consumeTps', width: 110, render: (v) => v ? v.toFixed(1) : '-'},
        {title: t.METRICS_CONSUME_MODEL, dataIndex: 'consumeModel', key: 'consumeModel', width: 120},
        {title: t.METRICS_CONSUME_TYPE, dataIndex: 'consumeType', key: 'consumeType', width: 150},
        {title: t.METRICS_MESSAGE_MODEL, dataIndex: 'messageModel', key: 'messageModel', width: 120},
        {
            title: t.METRICS_STATUS, dataIndex: 'status', key: 'status', width: 90,
            render: (s) => {
                const label = s === 'healthy' ? (t.METRICS_HEALTHY || 'Healthy')
                    : s === 'warning' ? (t.METRICS_WARNING || 'Warning')
                    : s === 'critical' ? (t.METRICS_CRITICAL || 'Critical')
                    : s;
                return <Tag color={STATUS_COLORS[s] || 'default'}>{label}</Tag>;
            },
        },
    ];

    return (
        <div style={{padding: '0 0 24px'}}>
            <div style={{
                display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                marginBottom: 16, padding: '0 0 0 0',
            }}>
                <h2 style={{margin: 0}}><DashboardOutlined /> {t.METRICS || 'Metrics'}</h2>
                <Space>
                    <span>{t.METRICS_AUTO_REFRESH || 'Auto Refresh'}</span>
                    <Switch checked={autoRefresh} onChange={setAutoRefresh} size="small" />
                    <Tooltip title={t.METRICS_REFRESH || 'Refresh'}>
                        <Button icon={<ReloadOutlined />} onClick={fetchOverview} loading={loading} />
                    </Tooltip>
                </Space>
            </div>

            <Spin spinning={loading}>
                {overview && (
                    <>
                        {/* Overview Statistics */}
                        <Row gutter={[16, 16]} style={{marginBottom: 16}}>
                            <Col xs={24} sm={12} md={6}>
                                <Card size="small">
                                    <Statistic
                                        title={t.METRICS_TOTAL_TPS_IN || 'Total TPS In'}
                                        value={overview.totalTpsIn}
                                        suffix="msg/s"
                                        valueStyle={{color: '#1890ff'}}
                                        prefix={<BarChartOutlined />}
                                    />
                                </Card>
                            </Col>
                            <Col xs={24} sm={12} md={6}>
                                <Card size="small">
                                    <Statistic
                                        title={t.METRICS_TOTAL_TPS_OUT || 'Total TPS Out'}
                                        value={overview.totalTpsOut}
                                        suffix="msg/s"
                                        valueStyle={{color: '#52c41a'}}
                                        prefix={<BarChartOutlined />}
                                    />
                                </Card>
                            </Col>
                            <Col xs={24} sm={12} md={6}>
                                <Card size="small">
                                    <Statistic
                                        title={t.METRICS_TOTAL_MSG_TODAY || 'Total Messages Today'}
                                        value={overview.totalMessageCountToday}
                                        formatter={(v) => formatNumber(v)}
                                        valueStyle={{color: '#722ed1'}}
                                    />
                                </Card>
                            </Col>
                            <Col xs={24} sm={12} md={6}>
                                <Card size="small">
                                    <Statistic
                                        title={t.METRICS_HEALTHY_BROKER || 'Healthy Brokers'}
                                        value={overview.healthyBrokerCount}
                                        suffix={`/ ${overview.totalBrokerCount}`}
                                        valueStyle={{color: overview.healthyBrokerCount === overview.totalBrokerCount ? '#52c41a' : '#faad14'}}
                                        prefix={<DatabaseOutlined />}
                                    />
                                </Card>
                            </Col>
                        </Row>

                        {/* Grafana-style Chart Panels */}
                        <Row gutter={[16, 16]} style={{marginBottom: 16}}>
                            <Col xs={24} lg={12}>
                                <Card
                                    title={<><LineChartOutlined /> {t.METRICS_BROKER_TPS || 'Broker TPS Overview'}</>}
                                    size="small"
                                    style={{background: '#0d1117', borderColor: CHART_THEME.borderColor}}
                                    headStyle={{background: '#0d1117', borderColor: CHART_THEME.borderColor, color: CHART_THEME.textColor}}
                                    bodyStyle={{padding: '8px'}}
                                >
                                    {getBrokerTpsChartOption ? (
                                        <ReactECharts option={getBrokerTpsChartOption} style={{height: 260}} />
                                    ) : (
                                        <div style={{height: 260, display: 'flex', alignItems: 'center', justifyContent: 'center', color: CHART_THEME.subTextColor}}>No broker data</div>
                                    )}
                                </Card>
                            </Col>
                            <Col xs={24} lg={12}>
                                <Card
                                    title={<><DatabaseOutlined /> {t.METRICS_BROKER_STATUS || 'Broker Health Status'}</>}
                                    size="small"
                                    style={{background: '#0d1117', borderColor: CHART_THEME.borderColor}}
                                    headStyle={{background: '#0d1117', borderColor: CHART_THEME.borderColor, color: CHART_THEME.textColor}}
                                    bodyStyle={{padding: '8px'}}
                                >
                                    {getBrokerStatusPieOption ? (
                                        <ReactECharts option={getBrokerStatusPieOption} style={{height: 260}} />
                                    ) : (
                                        <div style={{height: 260, display: 'flex', alignItems: 'center', justifyContent: 'center', color: CHART_THEME.subTextColor}}>No broker data</div>
                                    )}
                                </Card>
                            </Col>
                        </Row>

                        {/* System Resource Gauges */}
                        {overview.systemResources && (
                            <Row gutter={[16, 16]} style={{marginBottom: 16}}>
                                <Col xs={24} lg={16}>
                                    <Card
                                        title={<><CloudServerOutlined /> {t.METRICS_SYSTEM_RESOURCE || 'System Resources'}</>}
                                        size="small"
                                        style={{background: '#0d1117', borderColor: CHART_THEME.borderColor}}
                                        headStyle={{background: '#0d1117', borderColor: CHART_THEME.borderColor, color: CHART_THEME.textColor}}
                                        bodyStyle={{padding: '8px'}}
                                    >
                                        {getSystemResourceChartOption ? (
                                            <ReactECharts option={getSystemResourceChartOption} style={{height: 240}} />
                                        ) : (
                                            <div style={{height: 240, display: 'flex', alignItems: 'center', justifyContent: 'center', color: CHART_THEME.subTextColor}}>No system data</div>
                                        )}
                                    </Card>
                                </Col>
                                <Col xs={24} lg={8}>
                                    <Card
                                        title={<><CloudServerOutlined /> {t.METRICS_DISK_USAGE || 'Disk Usage'}</>}
                                        size="small"
                                        style={{background: '#0d1117', borderColor: CHART_THEME.borderColor}}
                                        headStyle={{background: '#0d1117', borderColor: CHART_THEME.borderColor, color: CHART_THEME.textColor}}
                                        bodyStyle={{padding: '8px'}}
                                    >
                                        {getDiskUsageChartOption ? (
                                            <ReactECharts option={getDiskUsageChartOption} style={{height: 240}} />
                                        ) : (
                                            <div style={{height: 240, display: 'flex', alignItems: 'center', justifyContent: 'center', color: CHART_THEME.subTextColor}}>No disk data</div>
                                        )}
                                    </Card>
                                </Col>
                            </Row>
                        )}

                        {/* Broker Resource Gauges */}
                        {overview.brokers?.length > 0 && getBrokerResourceGaugeOption && (
                            <Card
                                title={<><DatabaseOutlined /> {t.METRICS_BROKER_RESOURCES || 'Broker Resource Usage'}</>}
                                size="small"
                                style={{marginBottom: 16, background: '#0d1117', borderColor: CHART_THEME.borderColor}}
                                headStyle={{background: '#0d1117', borderColor: CHART_THEME.borderColor, color: CHART_THEME.textColor}}
                                bodyStyle={{padding: '8px'}}
                            >
                                <ReactECharts option={getBrokerResourceGaugeOption} style={{height: overview.brokers.length <= 3 ? 200 : 380}} />
                            </Card>
                        )}

                        {/* Topic Backlog & Consumer Group Diff Charts */}
                        <Row gutter={[16, 16]} style={{marginBottom: 16}}>
                            <Col xs={24} lg={12}>
                                <Card
                                    title={<><BarChartOutlined /> {t.METRICS_TOPIC_BACKLOG || 'Topic Message Backlog (Top 10)'}</>}
                                    size="small"
                                    style={{background: '#0d1117', borderColor: CHART_THEME.borderColor}}
                                    headStyle={{background: '#0d1117', borderColor: CHART_THEME.borderColor, color: CHART_THEME.textColor}}
                                    bodyStyle={{padding: '8px'}}
                                >
                                    {getTopicBacklogChartOption ? (
                                        <ReactECharts option={getTopicBacklogChartOption} style={{height: 300}} />
                                    ) : (
                                        <div style={{height: 300, display: 'flex', alignItems: 'center', justifyContent: 'center', color: CHART_THEME.subTextColor}}>No topic data</div>
                                    )}
                                </Card>
                            </Col>
                            <Col xs={24} lg={12}>
                                <Card
                                    title={<><TeamOutlined /> {t.METRICS_CONSUMER_DIFF || 'Consumer Group Lag (Top 10)'}</>}
                                    size="small"
                                    style={{background: '#0d1117', borderColor: CHART_THEME.borderColor}}
                                    headStyle={{background: '#0d1117', borderColor: CHART_THEME.borderColor, color: CHART_THEME.textColor}}
                                    bodyStyle={{padding: '8px'}}
                                >
                                    {getConsumerGroupDiffChartOption ? (
                                        <ReactECharts option={getConsumerGroupDiffChartOption} style={{height: 300}} />
                                    ) : (
                                        <div style={{height: 300, display: 'flex', alignItems: 'center', justifyContent: 'center', color: CHART_THEME.subTextColor}}>No consumer group data</div>
                                    )}
                                </Card>
                            </Col>
                        </Row>

                        {/* Broker Metrics Table */}
                        <Card
                            title={<><DatabaseOutlined /> {t.METRICS_BROKER || 'Broker Metrics'}</>}
                            style={{marginBottom: 16}}
                            size="small"
                        >
                            <Table
                                columns={brokerColumns}
                                dataSource={overview.brokers || []}
                                rowKey="brokerId"
                                pagination={false}
                                size="small"
                                scroll={{x: 1400}}
                            />
                        </Card>

                        {/* Top Topics Table */}
                        <Card
                            title={<><BarChartOutlined /> {t.METRICS_TOPIC || 'Topic Metrics'}</>}
                            style={{marginBottom: 16}}
                            size="small"
                        >
                            <Table
                                columns={topicColumns}
                                dataSource={overview.topTopics || []}
                                rowKey="topicName"
                                pagination={false}
                                size="small"
                                scroll={{x: 1200}}
                            />
                        </Card>

                        {/* Consumer Group Metrics Table */}
                        <Card
                            title={<><TeamOutlined /> {t.METRICS_CONSUMER_GROUP || 'Consumer Group Metrics'}</>}
                            style={{marginBottom: 16}}
                            size="small"
                        >
                            <Table
                                columns={consumerGroupColumns}
                                dataSource={overview.consumerGroups || []}
                                rowKey="groupName"
                                pagination={false}
                                size="small"
                                scroll={{x: 1100}}
                            />
                        </Card>

                        {/* System Resources */}
                        {overview.systemResources && (
                            <Card
                                title={<><CloudServerOutlined /> {t.METRICS_SYSTEM_RESOURCE || 'System Resources'}</>}
                                size="small"
                            >
                                <Row gutter={[16, 16]}>
                                    <Col xs={24} md={12}>
                                        <Descriptions column={1} size="small" bordered>
                                            <Descriptions.Item label={t.METRICS_CPU_PERCENT || 'CPU Usage'}>
                                                <Progress percent={overview.systemResources.cpuUsagePercent} size="small"
                                                    status={overview.systemResources.cpuUsagePercent > 80 ? 'exception' : 'normal'} />
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t.METRICS_MEMORY_PERCENT || 'Memory Usage'}>
                                                <Progress percent={overview.systemResources.memoryUsagePercent} size="small"
                                                    status={overview.systemResources.memoryUsagePercent > 85 ? 'exception' : 'normal'} />
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t.METRICS_MEMORY_USED || 'Memory Used'}>
                                                {overview.systemResources.memoryUsedMb} MB / {overview.systemResources.memoryTotalMb} MB
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t.METRICS_DISK_PERCENT || 'Disk Usage'}>
                                                <Progress percent={overview.systemResources.diskUsagePercent} size="small"
                                                    status={overview.systemResources.diskUsagePercent > 90 ? 'exception' : 'normal'} />
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t.METRICS_DISK_USED || 'Disk Used'}>
                                                {overview.systemResources.diskUsedGb} GB / {overview.systemResources.diskTotalGb} GB
                                            </Descriptions.Item>
                                        </Descriptions>
                                    </Col>
                                    <Col xs={24} md={12}>
                                        <Descriptions column={1} size="small" bordered>
                                            <Descriptions.Item label={t.METRICS_NETWORK_IN || 'Network In'}>
                                                {formatNumber(overview.systemResources.networkInKbps)} Kbps
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t.METRICS_NETWORK_OUT || 'Network Out'}>
                                                {formatNumber(overview.systemResources.networkOutKbps)} Kbps
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t.METRICS_GC_COUNT || 'GC Count'}>
                                                {overview.systemResources.gcCount}
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t.METRICS_GC_TIME || 'GC Time'}>
                                                {overview.systemResources.gcTimeMs} ms
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t.METRICS_HEAP_USED || 'Heap Used'}>
                                                {overview.systemResources.heapUsedMb} MB / {overview.systemResources.heapMaxMb} MB
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t.METRICS_ACTIVE_THREADS || 'Active Threads'}>
                                                {overview.systemResources.activeThreadCount}
                                            </Descriptions.Item>
                                        </Descriptions>
                                    </Col>
                                </Row>
                            </Card>
                        )}
                    </>
                )}
            </Spin>
        </div>
    );
};

export default MetricsPage;