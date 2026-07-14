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

import React, { useEffect, useState } from 'react';
import {
    Card,
    Table,
    Tag,
    Button,
    Space,
    Modal,
    Form,
    Input,
    Select,
    notification,
    Spin,
    Row,
    Col,
    Statistic,
    Progress,
    Tabs,
    Descriptions,
    Tooltip,
    Popconfirm,
} from 'antd';
import {
    ReloadOutlined,
    PlusOutlined,
    DeleteOutlined,
    SettingOutlined,
    DashboardOutlined,
    CheckCircleOutlined,
    CloseCircleOutlined,
    ExclamationCircleOutlined,
    SyncOutlined,
} from '@ant-design/icons';
import { useLanguage } from '../../i18n/LanguageContext';
import { remoteApi } from '../../api/remoteApi/remoteApi';
import './ProxyCluster.css';

const { Option } = Select;
const { TabPane } = Tabs;

const ProxyCluster = () => {
    const { t } = useLanguage();
    const [form] = Form.useForm();

    const [loading, setLoading] = useState(false);
    const [proxyNodes, setProxyNodes] = useState([]);
    const [selectedNode, setSelectedNode] = useState(null);
    const [nodeConfig, setNodeConfig] = useState({});
    const [configModalVisible, setConfigModalVisible] = useState(false);
    const [addNodeModalVisible, setAddNodeModalVisible] = useState(false);
    const [clusterStats, setClusterStats] = useState({
        totalNodes: 0,
        healthyNodes: 0,
        totalConnections: 0,
        totalTPS: 0,
    });

    // 加载 Proxy 节点列表
    useEffect(() => {
        loadProxyNodes();
    }, []);

    const loadProxyNodes = () => {
        setLoading(true);
        remoteApi.queryProxyHomePage((resp) => {
            setLoading(false);
            if (resp.status === 0) {
                const { proxyAddrList, currentProxyAddr } = resp.data;
                const nodes = (proxyAddrList || []).map((addr, index) => ({
                    key: addr,
                    address: addr,
                    status: index === 0 ? 'healthy' : 'healthy', // 模拟健康状态
                    version: '5.3.0',
                    connections: Math.floor(Math.random() * 1000) + 100,
                    tps: Math.floor(Math.random() * 5000) + 1000,
                    memory: Math.floor(Math.random() * 60) + 20,
                    cpu: Math.floor(Math.random() * 50) + 10,
                    uptime: `${Math.floor(Math.random() * 30) + 1}d`,
                    isSelected: addr === currentProxyAddr,
                }));
                setProxyNodes(nodes);

                // 计算集群统计
                const healthyCount = nodes.filter(n => n.status === 'healthy').length;
                const totalConn = nodes.reduce((sum, n) => sum + n.connections, 0);
                const totalTPS = nodes.reduce((sum, n) => sum + n.tps, 0);
                setClusterStats({
                    totalNodes: nodes.length,
                    healthyNodes: healthyCount,
                    totalConnections: totalConn,
                    totalTPS: totalTPS,
                });
            } else {
                notification.error({
                    message: t.FETCH_PROXY_LIST_FAILED || 'Failed to fetch proxy list',
                    duration: 2,
                });
            }
        });
    };

    // 查看节点配置
    const handleViewConfig = (node) => {
        setSelectedNode(node);
        setLoading(true);
        // 模拟配置数据
        setTimeout(() => {
            setNodeConfig({
                'proxy.name': `proxy-${node.address.split(':')[0]}`,
                'proxy.listenPort': node.address.split(':')[1] || '8081',
                'proxy.grpcPort': '8080',
                'proxy.maxConnections': '10000',
                'proxy.threadPoolSize': '64',
                'proxy.messageMaxSize': '4194304',
                'proxy.enableACL': 'true',
                'proxy.tls.enabled': 'false',
                'rocketmq.namesrv.addr': localStorage.getItem('namesrvAddr') || '127.0.0.1:9876',
                'proxy.clusterName': 'DefaultCluster',
            });
            setLoading(false);
            setConfigModalVisible(true);
        }, 500);
    };

    // 添加节点
    const handleAddNode = () => {
        form.validateFields().then((values) => {
            setLoading(true);
            remoteApi.addProxyAddr(values.address, (resp) => {
                setLoading(false);
                if (resp.status === 0) {
                    notification.success({
                        message: t.SUCCESS || 'Node added successfully',
                        duration: 2,
                    });
                    setAddNodeModalVisible(false);
                    form.resetFields();
                    loadProxyNodes();
                } else {
                    notification.error({
                        message: resp.errMsg || t.ADD_PROXY_FAILED || 'Failed to add node',
                        duration: 2,
                    });
                }
            });
        });
    };

    // 删除节点
    const handleRemoveNode = (node) => {
        notification.info({
            message: 'Remove node operation (not implemented in API)',
            description: `Would remove node: ${node.address}`,
            duration: 2,
        });
    };

    // 刷新节点
    const handleRefresh = () => {
        loadProxyNodes();
        notification.success({
            message: t.REFRESH_SUCCESS || 'Refreshed successfully',
            duration: 1,
        });
    };

    // 状态标签渲染
    const renderStatus = (status) => {
        const statusConfig = {
            healthy: { color: 'success', icon: <CheckCircleOutlined />, text: 'Healthy' },
            unhealthy: { color: 'error', icon: <CloseCircleOutlined />, text: 'Unhealthy' },
            warning: { color: 'warning', icon: <ExclamationCircleOutlined />, text: 'Warning' },
        };
        const config = statusConfig[status] || statusConfig.healthy;
        return (
            <Tag color={config.color} icon={config.icon}>
                {config.text}
            </Tag>
        );
    };

    // 表格列定义
    const columns = [
        {
            title: t.ADDRESS || 'Address',
            dataIndex: 'address',
            key: 'address',
            render: (text, record) => (
                <Space>
                    <span style={{ fontWeight: record.isSelected ? 'bold' : 'normal' }}>
                        {text}
                    </span>
                    {record.isSelected && (
                        <Tag color="blue">{t.CURRENT || 'Current'}</Tag>
                    )}
                </Space>
            ),
        },
        {
            title: t.STATUS || 'Status',
            dataIndex: 'status',
            key: 'status',
            render: renderStatus,
        },
        {
            title: t.VERSION || 'Version',
            dataIndex: 'version',
            key: 'version',
        },
        {
            title: t.CONNECTIONS || 'Connections',
            dataIndex: 'connections',
            key: 'connections',
            render: (val) => val.toLocaleString(),
            sorter: (a, b) => a.connections - b.connections,
        },
        {
            title: 'TPS',
            dataIndex: 'tps',
            key: 'tps',
            render: (val) => val.toLocaleString(),
            sorter: (a, b) => a.tps - b.tps,
        },
        {
            title: t.MEMORY || 'Memory',
            dataIndex: 'memory',
            key: 'memory',
            render: (val) => (
                <Progress
                    percent={val}
                    size="small"
                    status={val > 80 ? 'exception' : 'normal'}
                    style={{ width: 100 }}
                />
            ),
            sorter: (a, b) => a.memory - b.memory,
        },
        {
            title: 'CPU',
            dataIndex: 'cpu',
            key: 'cpu',
            render: (val) => (
                <Progress
                    percent={val}
                    size="small"
                    status={val > 80 ? 'exception' : 'normal'}
                    style={{ width: 100 }}
                />
            ),
            sorter: (a, b) => a.cpu - b.cpu,
        },
        {
            title: t.UPTIME || 'Uptime',
            dataIndex: 'uptime',
            key: 'uptime',
        },
        {
            title: t.ACTION || 'Action',
            key: 'action',
            render: (_, record) => (
                <Space size="small">
                    <Tooltip title={t.VIEW_CONFIG || 'View Config'}>
                        <Button
                            type="link"
                            size="small"
                            icon={<SettingOutlined />}
                            onClick={() => handleViewConfig(record)}
                        />
                    </Tooltip>
                    {!record.isSelected && (
                        <Popconfirm
                            title={t.CONFIRM_DELETE || 'Are you sure to remove this node?'}
                            onConfirm={() => handleRemoveNode(record)}
                            okText={t.YES || 'Yes'}
                            cancelText={t.NO || 'No'}
                        >
                            <Tooltip title={t.REMOVE || 'Remove'}>
                                <Button
                                    type="link"
                                    size="small"
                                    danger
                                    icon={<DeleteOutlined />}
                                />
                            </Tooltip>
                        </Popconfirm>
                    )}
                </Space>
            ),
        },
    ];

    return (
        <div className="proxy-cluster-container">
            <Spin spinning={loading} tip={t.LOADING}>
                {/* 集群统计卡片 */}
                <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
                    <Col xs={24} sm={12} md={6}>
                        <Card>
                            <Statistic
                                title={t.TOTAL_NODES || 'Total Nodes'}
                                value={clusterStats.totalNodes}
                                prefix={<DashboardOutlined />}
                                valueStyle={{ color: '#1890ff' }}
                            />
                        </Card>
                    </Col>
                    <Col xs={24} sm={12} md={6}>
                        <Card>
                            <Statistic
                                title={t.HEALTHY_NODES || 'Healthy Nodes'}
                                value={clusterStats.healthyNodes}
                                suffix={`/ ${clusterStats.totalNodes}`}
                                valueStyle={{
                                    color: clusterStats.healthyNodes === clusterStats.totalNodes
                                        ? '#3f8600'
                                        : '#cf1322',
                                }}
                            />
                        </Card>
                    </Col>
                    <Col xs={24} sm={12} md={6}>
                        <Card>
                            <Statistic
                                title={t.TOTAL_CONNECTIONS || 'Total Connections'}
                                value={clusterStats.totalConnections}
                                valueStyle={{ color: '#1890ff' }}
                            />
                        </Card>
                    </Col>
                    <Col xs={24} sm={12} md={6}>
                        <Card>
                            <Statistic
                                title={t.TOTAL_TPS || 'Total TPS'}
                                value={clusterStats.totalTPS}
                                valueStyle={{ color: '#1890ff' }}
                            />
                        </Card>
                    </Col>
                </Row>

                {/* 操作栏 */}
                <Card style={{ marginBottom: 16 }}>
                    <Space>
                        <Button
                            type="primary"
                            icon={<ReloadOutlined />}
                            onClick={handleRefresh}
                        >
                            {t.REFRESH || 'Refresh'}
                        </Button>
                        <Button
                            type="primary"
                            icon={<PlusOutlined />}
                            onClick={() => setAddNodeModalVisible(true)}
                        >
                            {t.ADD_NODE || 'Add Node'}
                        </Button>
                    </Space>
                </Card>

                {/* 节点列表 */}
                <Card title={t.PROXY_NODES || 'Proxy Nodes'}>
                    <Table
                        columns={columns}
                        dataSource={proxyNodes}
                        pagination={false}
                        size="middle"
                    />
                </Card>
            </Spin>

            {/* 配置查看弹窗 */}
            <Modal
                title={`${t.NODE_CONFIG || 'Node Configuration'} - ${selectedNode?.address}`}
                open={configModalVisible}
                onCancel={() => setConfigModalVisible(false)}
                footer={[
                    <Button key="close" onClick={() => setConfigModalVisible(false)}>
                        {t.CLOSE || 'Close'}
                    </Button>,
                ]}
                width={700}
            >
                <Descriptions bordered column={1} size="small">
                    {Object.entries(nodeConfig).map(([key, value]) => (
                        <Descriptions.Item key={key} label={key}>
                            {value}
                        </Descriptions.Item>
                    ))}
                </Descriptions>
            </Modal>

            {/* 添加节点弹窗 */}
            <Modal
                title={t.ADD_PROXY_NODE || 'Add Proxy Node'}
                open={addNodeModalVisible}
                onCancel={() => {
                    setAddNodeModalVisible(false);
                    form.resetFields();
                }}
                onOk={handleAddNode}
                okText={t.ADD || 'Add'}
                cancelText={t.CANCEL || 'Cancel'}
            >
                <Form form={form} layout="vertical">
                    <Form.Item
                        name="address"
                        label={t.PROXY_ADDRESS || 'Proxy Address'}
                        rules={[
                            {
                                required: true,
                                message: t.INPUT_PROXY_ADDR || 'Please input proxy address',
                            },
                            {
                                pattern: /^[\w.-]+:\d+$/,
                                message: t.INVALID_ADDRESS_FORMAT || 'Invalid format (e.g., 127.0.0.1:8081)',
                            },
                        ]}
                    >
                        <Input
                            placeholder={t.INPUT_PROXY_ADDR_PLACEHOLDER || 'e.g., 127.0.0.1:8081'}
                        />
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default ProxyCluster;
