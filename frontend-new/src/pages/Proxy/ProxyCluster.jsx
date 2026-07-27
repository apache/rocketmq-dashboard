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

import React, { useEffect, useState, useCallback } from 'react';
import {
    Card,
    Table,
    Tag,
    Button,
    Space,
    Modal,
    Form,
    Input,
    notification,
    Spin,
    Row,
    Col,
    Statistic,
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
} from '@ant-design/icons';
import { useLanguage } from '../../i18n/LanguageContext';
import { remoteApi } from '../../api/remoteApi/remoteApi';
import './ProxyCluster.css';

const ProxyCluster = () => {
    const { t } = useLanguage();
    const [form] = Form.useForm();
    const [api, contextHolder] = notification.useNotification();

    const [loading, setLoading] = useState(false);
    const [proxyNodes, setProxyNodes] = useState([]);
    const [selectedNode, setSelectedNode] = useState(null);
    const [nodeConfig, setNodeConfig] = useState({});
    const [configLoading, setConfigLoading] = useState(false);
    const [configModalVisible, setConfigModalVisible] = useState(false);
    const [addNodeModalVisible, setAddNodeModalVisible] = useState(false);

    // 加载 Proxy 节点列表
    const loadProxyNodes = useCallback(() => {
        setLoading(true);
        remoteApi.queryProxyHomePage((resp) => {
            setLoading(false);
            if (resp.status === 0) {
                const { proxyAddrList, currentProxyAddr } = resp.data;
                const nodes = (proxyAddrList || []).map((addr) => ({
                    key: addr,
                    address: addr,
                    isSelected: addr === currentProxyAddr,
                }));
                setProxyNodes(nodes);
            } else {
                api.error({
                    message: t.FETCH_PROXY_LIST_FAILED || 'Failed to fetch proxy list',
                    description: resp.errMsg,
                    duration: 3,
                });
            }
        });
    }, [t, api]);

    useEffect(() => {
        loadProxyNodes();
    }, [loadProxyNodes]);

    // 查看节点配置 — 接入真实 queryBrokerConfig API
    const handleViewConfig = (node) => {
        setSelectedNode(node);
        setConfigModalVisible(true);
        setConfigLoading(true);
        setNodeConfig({});

        remoteApi.queryBrokerConfig(node.address, (resp) => {
            setConfigLoading(false);
            if (resp.status === 0) {
                // 后端返回的配置是 key-value 对象或数组
                const configData = resp.data || {};
                if (Array.isArray(configData)) {
                    // 如果返回数组格式 [{key, value}, ...]
                    const configMap = {};
                    configData.forEach((item) => {
                        configMap[item.keyName || item.key || item.name] = item.value || item.val;
                    });
                    setNodeConfig(configMap);
                } else if (typeof configData === 'object') {
                    // 如果返回对象格式 {key: value, ...}
                    setNodeConfig(configData);
                }
            } else {
                api.error({
                    message: t.FAILED_TO_FETCH_CONFIG || 'Failed to fetch node config',
                    description: resp.errMsg,
                    duration: 3,
                });
                setNodeConfig({});
            }
        });
    };

    // 添加节点
    const handleAddNode = () => {
        form.validateFields().then((values) => {
            setLoading(true);
            remoteApi.addProxyAddr(values.address, (resp) => {
                setLoading(false);
                if (resp.status === 0) {
                    api.success({
                        message: t.SUCCESS || 'Node added successfully',
                        duration: 2,
                    });
                    setAddNodeModalVisible(false);
                    form.resetFields();
                    loadProxyNodes();
                } else {
                    api.error({
                        message: resp.errMsg || t.ADD_PROXY_FAILED || 'Failed to add node',
                        duration: 3,
                    });
                }
            });
        });
    };

    // 删除节点
    const handleRemoveNode = (node) => {
        api.info({
            message: 'Remove node operation (not implemented in API)',
            description: `Would remove node: ${node.address}`,
            duration: 2,
        });
    };

    // 表格列定义 — 仅展示API实际返回的字段
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
            title: t.ACTION || 'Action',
            key: 'action',
            width: 120,
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

    // 统计卡片 — 仅展示API可提供的数据
    const totalNodes = proxyNodes.length;
    const currentNode = proxyNodes.find(n => n.isSelected);

    return (
        <div className="proxy-cluster-container">
            {contextHolder}
            <Spin spinning={loading} tip={t.LOADING}>
                {/* 集群统计卡片 */}
                <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
                    <Col xs={24} sm={12} md={8}>
                        <Card>
                            <Statistic
                                title={t.TOTAL_NODES || 'Total Nodes'}
                                value={totalNodes}
                                prefix={<DashboardOutlined />}
                                valueStyle={{ color: '#1890ff' }}
                            />
                        </Card>
                    </Col>
                    <Col xs={24} sm={12} md={8}>
                        <Card>
                            <Statistic
                                title="当前Proxy"
                                value={currentNode ? currentNode.address : '-'}
                                valueStyle={{ color: '#3f8600', fontSize: 16 }}
                                prefix={<CheckCircleOutlined />}
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
                            onClick={loadProxyNodes}
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
                        locale={{ emptyText: '暂无Proxy节点' }}
                    />
                </Card>
            </Spin>

            {/* 配置查看弹窗 — 使用真实API数据 */}
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
                <Spin spinning={configLoading} tip="加载配置...">
                    {Object.keys(nodeConfig).length > 0 ? (
                        <Descriptions bordered column={1} size="small">
                            {Object.entries(nodeConfig).map(([key, value]) => (
                                <Descriptions.Item key={key} label={key}>
                                    {String(value)}
                                </Descriptions.Item>
                            ))}
                        </Descriptions>
                    ) : !configLoading ? (
                        <div style={{ textAlign: 'center', padding: 20, color: '#999' }}>
                            暂无配置数据
                        </div>
                    ) : null}
                </Spin>
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