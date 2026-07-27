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

import React, { useState, useEffect, useCallback } from 'react';
import {
    Table,
    Button,
    Tag,
    Tabs,
    Card,
    Space,
    Switch,
    Progress,
    Tooltip,
    Spin,
    notification,
} from 'antd';
import {
    PlusOutlined,
    ReloadOutlined,
    SettingOutlined,
    SyncOutlined,
    CloudServerOutlined,
    DashboardOutlined,
    ApiOutlined,
} from '@ant-design/icons';
import { remoteApi, tools } from '../../api/remoteApi/remoteApi';
import { useLanguage } from '../../i18n/LanguageContext';
import './BrokerCluster.css';

// 自动刷新间隔（毫秒）
const AUTO_REFRESH_INTERVAL = 5000;

const BrokerCluster = () => {
    const { t } = useLanguage();
    const [api, contextHolder] = notification.useNotification();

    const [loading, setLoading] = useState(false);
    const [autoRefresh, setAutoRefresh] = useState(false);
    const [activeTab, setActiveTab] = useState('broker');

    const [brokerData, setBrokerData] = useState([]);
    const [nameServerData, setNameServerData] = useState([]);
    const [proxyData, setProxyData] = useState([]);

    // 加载集群数据（Broker + NameServer地址）
    const fetchClusterData = useCallback(async () => {
        setLoading(true);
        try {
            // 并行请求集群数据和运维页面数据
            const [clusterResp, opsResp, proxyResp] = await Promise.allSettled([
                new Promise((resolve) => {
                    remoteApi.queryClusterList((resp) => resolve(resp));
                }),
                remoteApi.queryOpsHomePage(),
                new Promise((resolve) => {
                    remoteApi.queryProxyHomePage((resp) => resolve(resp));
                }),
            ]);

            // 处理集群数据 → Broker列表
            if (clusterResp.status === 'fulfilled' && clusterResp.value?.status === 0) {
                const { clusterInfo, brokerServer } = clusterResp.value.data;
                const { clusterAddrTable, brokerAddrTable } = clusterInfo;
                const generatedBrokers = tools.generateBrokerMap(brokerServer, clusterAddrTable, brokerAddrTable);

                // 将 clusterMap 展平为 brokerData 列表
                const brokers = [];
                Object.entries(generatedBrokers).forEach(([clusterName, instances]) => {
                    instances.forEach((instance) => {
                        const putTpsValue = instance.putTps ? Number(String(instance.putTps).split(' ')[0]) : 0;
                        const getTpsValue = (instance.getTransferedTps || instance.getTransferredTps)
                            ? Number(String(instance.getTransferedTps || instance.getTransferredTps).split(' ')[0])
                            : 0;
                        // 从 dispatchMaxBuffer 推断状态：>0 正常运行，=0 可能只读
                        const status = instance.dispatchMaxBuffer !== undefined
                            ? (Number(instance.dispatchMaxBuffer) > 0 ? 'running' : 'readonly')
                            : 'running';
                        brokers.push({
                            key: `${clusterName}-${instance.brokerName}-${instance.brokerId}`,
                            clusterName,
                            brokerName: instance.brokerName,
                            brokerId: instance.brokerId,
                            status,
                            version: instance.brokerVersionDesc || '-',
                            diskUsage: instance.diskUsageString ? parseFloat(instance.diskUsageString) : 0,
                            address: instance.address,
                            putTps: putTpsValue,
                            getTps: getTpsValue,
                            // 保留原始详情用于配置/状态查看
                            detail: instance.detail || instance,
                        });
                    });
                });
                setBrokerData(brokers);
            } else {
                const errMsg = clusterResp.status === 'fulfilled' ? clusterResp.value?.errMsg : clusterResp.reason?.message;
                api.error({ message: t.QUERY_CLUSTER_LIST_FAILED || 'Failed to fetch cluster list', description: errMsg, duration: 3 });
            }

            // 处理运维页面数据 → NameServer列表
            if (opsResp.status === 'fulfilled' && opsResp.value?.status === 0) {
                const { namesvrAddrList, currentNamesrv } = opsResp.value.data;
                const nsList = (namesvrAddrList || []).map((addr, index) => ({
                    key: addr,
                    name: `nameserver-${index + 1}`,
                    status: 'running', // API不提供健康状态，默认running
                    version: '-', // API不提供版本信息
                    address: addr,
                    isCurrent: addr === currentNamesrv,
                }));
                setNameServerData(nsList);
            }

            // 处理Proxy数据 → Proxy列表
            if (proxyResp.status === 'fulfilled' && proxyResp.value?.status === 0) {
                const { proxyAddrList, currentProxyAddr } = proxyResp.value.data;
                const proxyList = (proxyAddrList || []).map((addr, index) => ({
                    key: addr,
                    name: `proxy-${index + 1}`,
                    status: 'running', // API不提供健康状态，默认running
                    version: '-', // API不提供版本信息，需通过queryBrokerConfig获取
                    address: addr,
                    isCurrent: addr === currentProxyAddr,
                }));
                setProxyData(proxyList);
            }
        } catch (error) {
            console.error('Error fetching cluster data:', error);
            api.error({ message: t.FAILED_TO_FETCH_DATA || 'Failed to fetch data', description: error.message, duration: 3 });
        } finally {
            setLoading(false);
        }
    }, [t, api]);

    // 初始加载
    useEffect(() => {
        fetchClusterData();
    }, [fetchClusterData]);

    // 自动刷新
    useEffect(() => {
        if (!autoRefresh) return;
        const timer = setInterval(fetchClusterData, AUTO_REFRESH_INTERVAL);
        return () => clearInterval(timer);
    }, [autoRefresh, fetchClusterData]);

    // 状态标签渲染
    const renderStatus = (status) => {
        const config = {
            running: { color: 'success', text: '运行中' },
            readonly: { color: 'warning', text: '只读' },
            maintenance: { color: 'error', text: '维护中' },
        };
        const { color, text } = config[status] || config.running;
        return <Tag color={color}>{text}</Tag>;
    };

    // 磁盘使用率渲染
    const renderDiskUsage = (percent) => {
        if (!percent || percent <= 0) return <span style={{ color: '#999' }}>-</span>;
        let status = 'normal';
        let color = '#52c41a';
        if (percent > 85) {
            status = 'exception';
            color = '#ff4d4f';
        } else if (percent > 70) {
            status = 'active';
            color = '#fa8c16';
        }
        return (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Progress
                    percent={percent}
                    size="small"
                    status={status}
                    style={{ width: 80, margin: 0 }}
                    strokeColor={color}
                />
                <span style={{ fontSize: 12, color: color, fontWeight: 500 }}>{percent}%</span>
            </div>
        );
    };

    // TPS格式化
    const formatTps = (value) => {
        if (!value && value !== 0) return '-';
        return value.toLocaleString(undefined, { maximumFractionDigits: 2 });
    };

    // Broker 表格列
    const brokerColumns = [
        {
            title: '集群',
            dataIndex: 'clusterName',
            key: 'clusterName',
            render: (text) => <span style={{ fontWeight: 500 }}>{text}</span>,
        },
        {
            title: 'Broker名称',
            dataIndex: 'brokerName',
            key: 'brokerName',
            render: (text, record) => (
                <span style={{ color: '#1677ff', fontWeight: 500 }}>
                    {text} ({record.brokerId === 0 ? 'Master' : 'Slave'})
                </span>
            ),
        },
        {
            title: '运行状态',
            dataIndex: 'status',
            key: 'status',
            render: renderStatus,
        },
        {
            title: '版本',
            dataIndex: 'version',
            key: 'version',
        },
        {
            title: '磁盘使用率',
            dataIndex: 'diskUsage',
            key: 'diskUsage',
            render: renderDiskUsage,
            width: 160,
        },
        {
            title: '地址',
            dataIndex: 'address',
            key: 'address',
            render: (text) => <code style={{ fontSize: 12, background: '#f5f5f5', padding: '2px 6px', borderRadius: 4 }}>{text}</code>,
        },
        {
            title: 'TPS入流量',
            dataIndex: 'putTps',
            key: 'putTps',
            render: formatTps,
            sorter: (a, b) => (a.putTps || 0) - (b.putTps || 0),
        },
        {
            title: 'TPS出流量',
            dataIndex: 'getTps',
            key: 'getTps',
            render: formatTps,
            sorter: (a, b) => (a.getTps || 0) - (b.getTps || 0),
        },
        {
            title: '操作',
            key: 'action',
            render: (_, record) => (
                <Space size="small">
                    <Tooltip title="配置">
                        <Button type="link" size="small" icon={<SettingOutlined />}>
                            配置
                        </Button>
                    </Tooltip>
                </Space>
            ),
        },
    ];

    // NameServer 表格列
    const nsColumns = [
        {
            title: 'NameServer名称',
            dataIndex: 'name',
            key: 'name',
            render: (text, record) => (
                <span>
                    <span style={{ color: '#1677ff', fontWeight: 500 }}>{text}</span>
                    {record.isCurrent && <Tag color="blue" style={{ marginLeft: 8 }}>当前</Tag>}
                </span>
            ),
        },
        {
            title: '运行状态',
            dataIndex: 'status',
            key: 'status',
            render: renderStatus,
        },
        {
            title: '地址',
            dataIndex: 'address',
            key: 'address',
            render: (text) => <code style={{ fontSize: 12, background: '#f5f5f5', padding: '2px 6px', borderRadius: 4 }}>{text}</code>,
        },
    ];

    // Proxy 表格列
    const proxyColumns = [
        {
            title: 'Proxy名称',
            dataIndex: 'name',
            key: 'name',
            render: (text, record) => (
                <span>
                    <span style={{ color: '#1677ff', fontWeight: 500 }}>{text}</span>
                    {record.isCurrent && <Tag color="blue" style={{ marginLeft: 8 }}>当前</Tag>}
                </span>
            ),
        },
        {
            title: '运行状态',
            dataIndex: 'status',
            key: 'status',
            render: renderStatus,
        },
        {
            title: '地址',
            dataIndex: 'address',
            key: 'address',
            render: (text) => <code style={{ fontSize: 12, background: '#f5f5f5', padding: '2px 6px', borderRadius: 4 }}>{text}</code>,
        },
    ];

    return (
        <div className="broker-cluster-page">
            {contextHolder}
            <div className="page-header">
                <h2 className="page-title">
                    <CloudServerOutlined style={{ marginRight: 8, color: '#1677ff' }} />
                    RocketMQ集群
                </h2>
                <Space size="middle">
                    <Switch
                        checked={autoRefresh}
                        onChange={setAutoRefresh}
                        checkedChildren="实时刷新"
                        unCheckedChildren="手动"
                        size="small"
                    />
                    <Button
                        icon={<ReloadOutlined />}
                        size="small"
                        onClick={fetchClusterData}
                        loading={loading}
                    >
                        刷新
                    </Button>
                </Space>
            </div>

            <Card bordered={false} className="cluster-card">
                <Spin spinning={loading} tip="加载中...">
                    <Tabs
                        activeKey={activeTab}
                        onChange={setActiveTab}
                        items={[
                            {
                                key: 'nameserver',
                                label: (
                                    <span>
                                        <DashboardOutlined style={{ marginRight: 4 }} />
                                        NameServer ({nameServerData.length})
                                    </span>
                                ),
                                children: (
                                    <Table
                                        columns={nsColumns}
                                        dataSource={nameServerData}
                                        pagination={false}
                                        size="middle"
                                        locale={{ emptyText: '暂无NameServer数据' }}
                                    />
                                ),
                            },
                            {
                                key: 'broker',
                                label: (
                                    <span>
                                        <CloudServerOutlined style={{ marginRight: 4 }} />
                                        Broker ({brokerData.length})
                                    </span>
                                ),
                                children: (
                                    <Table
                                        columns={brokerColumns}
                                        dataSource={brokerData}
                                        pagination={{
                                            pageSize: 10,
                                            showTotal: (total) => `共 ${total} 个Broker`,
                                        }}
                                        size="middle"
                                        locale={{ emptyText: '暂无Broker数据' }}
                                    />
                                ),
                            },
                            {
                                key: 'proxy',
                                label: (
                                    <span>
                                        <ApiOutlined style={{ marginRight: 4 }} />
                                        Proxy ({proxyData.length})
                                    </span>
                                ),
                                children: (
                                    <Table
                                        columns={proxyColumns}
                                        dataSource={proxyData}
                                        pagination={false}
                                        size="middle"
                                        locale={{ emptyText: '暂无Proxy数据' }}
                                    />
                                ),
                            },
                        ]}
                    />
                </Spin>
            </Card>
        </div>
    );
};

export default BrokerCluster;