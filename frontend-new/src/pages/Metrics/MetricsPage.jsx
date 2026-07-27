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

import React, {useEffect, useState, useCallback} from 'react';
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
    ServerOutlined,
    BarChartOutlined,
    TeamOutlined,
    CloudServerOutlined,
} from '@ant-design/icons';
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

const MetricsPage = () => {
    const {t} = useLanguage();
    const [loading, setLoading] = useState(false);
    const [autoRefresh, setAutoRefresh] = useState(false);
    const [overview, setOverview] = useState(null);

    const fetchOverview = useCallback(async () => {
        setLoading(true);
        try {
            const result = await remoteApi.getMetricsOverview();
            if (result && result.code === 200 && result.data) {
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
                                        prefix={<ServerOutlined />}
                                    />
                                </Card>
                            </Col>
                        </Row>

                        {/* Broker Metrics Table */}
                        <Card
                            title={<><ServerOutlined /> {t.METRICS_BROKER || 'Broker Metrics'}</>}
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