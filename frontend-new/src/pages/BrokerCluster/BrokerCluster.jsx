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

import React, { useState } from 'react';
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
import './BrokerCluster.css';

const BrokerCluster = () => {
    const [autoRefresh, setAutoRefresh] = useState(false);
    const [activeTab, setActiveTab] = useState('broker');

    // 模拟 Broker 数据
    const brokerData = [
        {
            key: '1',
            k8sCluster: 'prod-cn-east-1',
            brokerName: 'broker-a',
            status: 'running',
            version: '5.3.0',
            diskUsage: 62,
            address: '10.0.1.10:10911',
            tpsIn: '12,580',
            tpsOut: '8,340',
        },
        {
            key: '2',
            k8sCluster: 'prod-cn-east-1',
            brokerName: 'broker-b',
            status: 'readonly',
            version: '5.3.0',
            diskUsage: 89,
            address: '10.0.1.11:10911',
            tpsIn: '0',
            tpsOut: '3,120',
        },
        {
            key: '3',
            k8sCluster: 'prod-cn-east-1',
            brokerName: 'broker-c',
            status: 'running',
            version: '5.2.0',
            diskUsage: 45,
            address: '10.0.1.12:10911',
            tpsIn: '9,750',
            tpsOut: '6,280',
        },
        {
            key: '4',
            k8sCluster: 'prod-cn-south-1',
            brokerName: 'broker-d',
            status: 'maintenance',
            version: '5.3.0',
            diskUsage: 33,
            address: '10.0.2.10:10911',
            tpsIn: '0',
            tpsOut: '0',
        },
        {
            key: '5',
            k8sCluster: 'prod-cn-south-1',
            brokerName: 'broker-e',
            status: 'running',
            version: '5.3.0',
            diskUsage: 51,
            address: '10.0.2.11:10911',
            tpsIn: '7,890',
            tpsOut: '5,430',
        },
        {
            key: '6',
            k8sCluster: 'staging-cn-east-1',
            brokerName: 'broker-staging-a',
            status: 'running',
            version: '5.3.1',
            diskUsage: 28,
            address: '10.0.10.10:10911',
            tpsIn: '1,230',
            tpsOut: '980',
        },
    ];

    // NameServer 数据
    const nameServerData = [
        {
            key: '1',
            k8sCluster: 'prod-cn-east-1',
            name: 'nameserver-a',
            status: 'running',
            version: '5.3.0',
            address: '10.0.1.20:9876',
            connections: 156,
        },
        {
            key: '2',
            k8sCluster: 'prod-cn-east-1',
            name: 'nameserver-b',
            status: 'running',
            version: '5.3.0',
            address: '10.0.1.21:9876',
            connections: 148,
        },
        {
            key: '3',
            k8sCluster: 'prod-cn-south-1',
            name: 'nameserver-c',
            status: 'running',
            version: '5.3.0',
            address: '10.0.2.20:9876',
            connections: 92,
        },
    ];

    // Proxy 数据
    const proxyData = [
        {
            key: '1',
            k8sCluster: 'prod-cn-east-1',
            name: 'proxy-a',
            status: 'running',
            version: '5.3.0',
            address: '10.0.1.30:8080',
            grpcPort: '10.0.1.30:8081',
            connections: 2340,
        },
        {
            key: '2',
            k8sCluster: 'prod-cn-south-1',
            name: 'proxy-b',
            status: 'running',
            version: '5.3.0',
            address: '10.0.2.30:8080',
            grpcPort: '10.0.2.30:8081',
            connections: 1560,
        },
    ];

    // 状态标签渲染
    const renderStatus = (status) => {
        const config = {
            running: { color: 'success', text: '运行中', icon: null },
            readonly: { color: 'warning', text: '只读', icon: null },
            maintenance: { color: 'error', text: '维护中', icon: null },
        };
        const { color, text } = config[status] || config.running;
        return <Tag color={color}>{text}</Tag>;
    };

    // 磁盘使用率渲染
    const renderDiskUsage = (percent) => {
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

    // Broker 表格列
    const brokerColumns = [
        {
            title: 'K8s集群名称',
            dataIndex: 'k8sCluster',
            key: 'k8sCluster',
            render: (text) => <span style={{ fontWeight: 500 }}>{text}</span>,
        },
        {
            title: 'Broker名称',
            dataIndex: 'brokerName',
            key: 'brokerName',
            render: (text) => (
                <span style={{ color: '#1677ff', fontWeight: 500 }}>{text}</span>
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
            dataIndex: 'tpsIn',
            key: 'tpsIn',
            render: (text) => <span style={{ fontWeight: 500 }}>{text}</span>,
            sorter: (a, b) => parseFloat(a.tpsIn.replace(/,/g, '')) - parseFloat(b.tpsIn.replace(/,/g, '')),
        },
        {
            title: 'TPS出流量',
            dataIndex: 'tpsOut',
            key: 'tpsOut',
            render: (text) => <span style={{ fontWeight: 500 }}>{text}</span>,
            sorter: (a, b) => parseFloat(a.tpsOut.replace(/,/g, '')) - parseFloat(b.tpsOut.replace(/,/g, '')),
        },
        {
            title: '操作',
            key: 'action',
            render: () => (
                <Space size="small">
                    <Tooltip title="配置">
                        <Button type="link" size="small" icon={<SettingOutlined />}>
                            配置
                        </Button>
                    </Tooltip>
                    <Tooltip title="重启">
                        <Button type="link" size="small" icon={<SyncOutlined />}>
                            重启
                        </Button>
                    </Tooltip>
                </Space>
            ),
        },
    ];

    // NameServer 表格列
    const nsColumns = [
        { title: 'K8s集群名称', dataIndex: 'k8sCluster', key: 'k8sCluster', render: (t) => <span style={{ fontWeight: 500 }}>{t}</span> },
        { title: 'NameServer名称', dataIndex: 'name', key: 'name', render: (t) => <span style={{ color: '#1677ff', fontWeight: 500 }}>{t}</span> },
        { title: '运行状态', dataIndex: 'status', key: 'status', render: renderStatus },
        { title: '版本', dataIndex: 'version', key: 'version' },
        { title: '地址', dataIndex: 'address', key: 'address', render: (t) => <code style={{ fontSize: 12, background: '#f5f5f5', padding: '2px 6px', borderRadius: 4 }}>{t}</code> },
        { title: '连接数', dataIndex: 'connections', key: 'connections', render: (t) => <span style={{ fontWeight: 500 }}>{t}</span> },
        {
            title: '操作', key: 'action',
            render: () => (
                <Space size="small">
                    <Button type="link" size="small" icon={<SettingOutlined />}>配置</Button>
                    <Button type="link" size="small" icon={<SyncOutlined />}>重启</Button>
                </Space>
            ),
        },
    ];

    // Proxy 表格列
    const proxyColumns = [
        { title: 'K8s集群名称', dataIndex: 'k8sCluster', key: 'k8sCluster', render: (t) => <span style={{ fontWeight: 500 }}>{t}</span> },
        { title: 'Proxy名称', dataIndex: 'name', key: 'name', render: (t) => <span style={{ color: '#1677ff', fontWeight: 500 }}>{t}</span> },
        { title: '运行状态', dataIndex: 'status', key: 'status', render: renderStatus },
        { title: '版本', dataIndex: 'version', key: 'version' },
        { title: 'HTTP地址', dataIndex: 'address', key: 'address', render: (t) => <code style={{ fontSize: 12, background: '#f5f5f5', padding: '2px 6px', borderRadius: 4 }}>{t}</code> },
        { title: 'gRPC地址', dataIndex: 'grpcPort', key: 'grpcPort', render: (t) => <code style={{ fontSize: 12, background: '#f5f5f5', padding: '2px 6px', borderRadius: 4 }}>{t}</code> },
        { title: '连接数', dataIndex: 'connections', key: 'connections', render: (t) => <span style={{ fontWeight: 500 }}>{t}</span> },
        {
            title: '操作', key: 'action',
            render: () => (
                <Space size="small">
                    <Button type="link" size="small" icon={<SettingOutlined />}>配置</Button>
                    <Button type="link" size="small" icon={<SyncOutlined />}>重启</Button>
                </Space>
            ),
        },
    ];

    return (
        <div className="broker-cluster-page">
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
                    <Button icon={<ReloadOutlined />} size="small">
                        刷新
                    </Button>
                    <Button type="primary" icon={<PlusOutlined />}>
                        新建集群
                    </Button>
                </Space>
            </div>

            <Card bordered={false} className="cluster-card">
                <Tabs
                    activeKey={activeTab}
                    onChange={setActiveTab}
                    items={[
                        {
                            key: 'nameserver',
                            label: (
                                <span>
                                    <DashboardOutlined style={{ marginRight: 4 }} />
                                    NameServer管理
                                </span>
                            ),
                            children: (
                                <Table
                                    columns={nsColumns}
                                    dataSource={nameServerData}
                                    pagination={false}
                                    size="middle"
                                />
                            ),
                        },
                        {
                            key: 'broker',
                            label: (
                                <span>
                                    <CloudServerOutlined style={{ marginRight: 4 }} />
                                    Broker管理
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
                                />
                            ),
                        },
                        {
                            key: 'proxy',
                            label: (
                                <span>
                                    <ApiOutlined style={{ marginRight: 4 }} />
                                    Proxy管理
                                </span>
                            ),
                            children: (
                                <Table
                                    columns={proxyColumns}
                                    dataSource={proxyData}
                                    pagination={false}
                                    size="middle"
                                />
                            ),
                        },
                    ]}
                />
            </Card>
        </div>
    );
};

export default BrokerCluster;