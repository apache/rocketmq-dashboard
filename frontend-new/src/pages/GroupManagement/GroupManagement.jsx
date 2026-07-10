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
    Input,
    Tag,
    Modal,
    Tabs,
    Card,
    Row,
    Col,
    Descriptions,
    Space,
    Switch,
} from 'antd';
import {
    SearchOutlined,
    PlusOutlined,
    ReloadOutlined,
    TeamOutlined,
    CheckCircleOutlined,
    ExclamationCircleOutlined,
    EyeOutlined,
} from '@ant-design/icons';
import './GroupManagement.css';

const GroupManagement = () => {
    const [searchText, setSearchText] = useState('');
    const [modalVisible, setModalVisible] = useState(false);
    const [selectedGroup, setSelectedGroup] = useState(null);
    const [autoRefresh, setAutoRefresh] = useState(false);

    // 模拟数据
    const groupData = [
        {
            key: '1',
            group: 'order-consumer-group',
            namespace: 'default',
            cluster: 'cluster-production',
            count: 4,
            consumeType: 'PUSH',
            messageModel: 'CLUSTERING',
            diff: 1280,
            status: 'running',
        },
        {
            key: '2',
            group: 'payment-consumer-group',
            namespace: 'default',
            cluster: 'cluster-production',
            count: 2,
            consumeType: 'PUSH',
            messageModel: 'CLUSTERING',
            diff: 0,
            status: 'running',
        },
        {
            key: '3',
            group: 'inventory-sync-group',
            namespace: 'ns-warehouse',
            cluster: 'cluster-warehouse',
            count: 3,
            consumeType: 'PUSH',
            messageModel: 'CLUSTERING',
            diff: 56800,
            status: 'warning',
        },
        {
            key: '4',
            group: 'log-processor-group',
            namespace: 'default',
            cluster: 'cluster-production',
            count: 1,
            consumeType: 'PUSH',
            messageModel: 'BROADCASTING',
            diff: 0,
            status: 'running',
        },
        {
            key: '5',
            group: 'notification-group',
            namespace: 'ns-notify',
            cluster: 'cluster-production',
            count: 2,
            consumeType: 'PUSH',
            messageModel: 'CLUSTERING',
            diff: 3,
            status: 'running',
        },
    ];

    // 订阅关系数据
    const subscriptionData = [
        {
            key: '1',
            topic: 'ORDER_TOPIC',
            consistency: 'consistent',
            subMode: 'TAG',
            expression: '*',
        },
        {
            key: '2',
            topic: 'PAYMENT_TOPIC',
            consistency: 'consistent',
            subMode: 'TAG',
            expression: 'pay_success || pay_fail',
        },
        {
            key: '3',
            topic: 'INVENTORY_TOPIC',
            consistency: 'inconsistent',
            subMode: 'TAG',
            expression: 'stock_update',
        },
    ];

    // 表格列定义
    const columns = [
        {
            title: 'Group名称',
            dataIndex: 'group',
            key: 'group',
            render: (text) => (
                <a onClick={() => handleViewDetail(text)} style={{ color: '#1677ff', fontWeight: 500 }}>
                    {text}
                </a>
            ),
        },
        {
            title: '命名空间',
            dataIndex: 'namespace',
            key: 'namespace',
        },
        {
            title: '所属集群',
            dataIndex: 'cluster',
            key: 'cluster',
        },
        {
            title: '在线实例',
            dataIndex: 'count',
            key: 'count',
            render: (count) => <span style={{ fontWeight: 500 }}>{count}</span>,
        },
        {
            title: '消费模式',
            dataIndex: 'messageModel',
            key: 'messageModel',
            render: (mode) => (
                <Tag color={mode === 'CLUSTERING' ? 'blue' : 'orange'}>
                    {mode === 'CLUSTERING' ? '集群消费' : '广播消费'}
                </Tag>
            ),
        },
        {
            title: '堆积量',
            dataIndex: 'diff',
            key: 'diff',
            render: (diff) => (
                <span style={{ color: diff > 10000 ? '#ff4d4f' : diff > 0 ? '#fa8c16' : '#52c41a', fontWeight: 500 }}>
                    {diff.toLocaleString()}
                </span>
            ),
        },
        {
            title: '状态',
            dataIndex: 'status',
            key: 'status',
            render: (status) => {
                const config = {
                    running: { color: 'success', text: '运行中' },
                    warning: { color: 'warning', text: '堆积告警' },
                    stopped: { color: 'error', text: '已停止' },
                };
                const { color, text } = config[status] || config.running;
                return <Tag color={color}>{text}</Tag>;
            },
        },
        {
            title: '操作',
            key: 'action',
            render: (_, record) => (
                <Space size="small">
                    <Button type="link" size="small" onClick={() => handleViewDetail(record.group)}>
                        详情
                    </Button>
                    <Button type="link" size="small">配置</Button>
                </Space>
            ),
        },
    ];

    // 订阅关系表格列
    const subscriptionColumns = [
        {
            title: 'Topic主题',
            dataIndex: 'topic',
            key: 'topic',
            render: (text) => <span style={{ fontWeight: 500 }}>{text}</span>,
        },
        {
            title: '订阅一致性',
            dataIndex: 'consistency',
            key: 'consistency',
            render: (consistency) => (
                <Tag color={consistency === 'consistent' ? 'success' : 'warning'}>
                    {consistency === 'consistent' ? '一致' : '不一致'}
                </Tag>
            ),
        },
        {
            title: '订阅模式',
            dataIndex: 'subMode',
            key: 'subMode',
        },
        {
            title: '订阅表达式',
            dataIndex: 'expression',
            key: 'expression',
            render: (text) => <code style={{ background: '#f5f5f5', padding: '2px 6px', borderRadius: 4, fontSize: 12 }}>{text}</code>,
        },
        {
            title: '操作',
            key: 'action',
            render: () => (
                <Button type="link" size="small" icon={<EyeOutlined />}>
                    查看分布
                </Button>
            ),
        },
    ];

    const handleViewDetail = (groupName) => {
        setSelectedGroup(groupName);
        setModalVisible(true);
    };

    return (
        <div className="group-management-page">
            <div className="page-header">
                <h2 className="page-title">
                    <TeamOutlined style={{ marginRight: 8, color: '#1677ff' }} />
                    Group管理
                </h2>
                <Space size="middle">
                    <Input
                        placeholder="搜索Group名称..."
                        prefix={<SearchOutlined />}
                        value={searchText}
                        onChange={(e) => setSearchText(e.target.value)}
                        style={{ width: 240 }}
                        allowClear
                    />
                    <Button type="primary" icon={<PlusOutlined />}>
                        新建Group
                    </Button>
                    <Switch
                        checked={autoRefresh}
                        onChange={setAutoRefresh}
                        checkedChildren="自动刷新"
                        unCheckedChildren="手动"
                        size="small"
                    />
                    <Button icon={<ReloadOutlined />} size="small">
                        刷新
                    </Button>
                </Space>
            </div>

            <Card bordered={false} className="table-card">
                <Table
                    columns={columns}
                    dataSource={groupData.filter((d) =>
                        !searchText || d.group.includes(searchText)
                    )}
                    pagination={{
                        pageSize: 10,
                        showTotal: (total) => `共 ${total} 个Group`,
                        showSizeChanger: true,
                    }}
                    size="middle"
                />
            </Card>

            {/* Group详情弹窗 */}
            <Modal
                title={null}
                open={modalVisible}
                onCancel={() => setModalVisible(false)}
                footer={null}
                width={720}
                className="group-detail-modal"
                destroyOnClose
            >
                <div className="modal-header">
                    <h3 className="modal-title">
                        <TeamOutlined style={{ marginRight: 8, color: '#1677ff' }} />
                        {selectedGroup}
                    </h3>
                </div>
                <Tabs
                    defaultActiveKey="overview"
                    items={[
                        {
                            key: 'overview',
                            label: '概览',
                            children: (
                                <div className="tab-overview">
                                    {/* 统计卡片 */}
                                    <Row gutter={16} style={{ marginBottom: 20 }}>
                                        <Col span={8}>
                                            <Card className="stat-card" bordered={false}>
                                                <div className="stat-label">在线实例</div>
                                                <div className="stat-value">
                                                    4
                                                    <Tag color="success" style={{ marginLeft: 8 }}>在线</Tag>
                                                </div>
                                            </Card>
                                        </Col>
                                        <Col span={8}>
                                            <Card className="stat-card stat-card-danger" bordered={false}>
                                                <div className="stat-label">总堆积</div>
                                                <div className="stat-value danger">1,280</div>
                                            </Card>
                                        </Col>
                                        <Col span={8}>
                                            <Card className="stat-card" bordered={false}>
                                                <div className="stat-label">订阅Topic数量</div>
                                                <div className="stat-value">3</div>
                                            </Card>
                                        </Col>
                                    </Row>

                                    {/* 基本信息 */}
                                    <Descriptions
                                        column={2}
                                        bordered
                                        size="small"
                                        className="info-descriptions"
                                    >
                                        <Descriptions.Item label="Group名称">order-consumer-group</Descriptions.Item>
                                        <Descriptions.Item label="命名空间">default</Descriptions.Item>
                                        <Descriptions.Item label="所属集群">cluster-production</Descriptions.Item>
                                        <Descriptions.Item label="订阅模式">
                                            <Tag color="blue">集群消费</Tag>
                                        </Descriptions.Item>
                                        <Descriptions.Item label="消费类型">PUSH</Descriptions.Item>
                                        <Descriptions.Item label="消费延迟">12ms</Descriptions.Item>
                                        <Descriptions.Item label="最大重试次数">16</Descriptions.Item>
                                        <Descriptions.Item label="创建时间">2025-03-15 10:30:00</Descriptions.Item>
                                        <Descriptions.Item label="已订阅Topic" span={2}>
                                            ORDER_TOPIC, PAYMENT_TOPIC, INVENTORY_TOPIC
                                        </Descriptions.Item>
                                    </Descriptions>

                                    {/* 订阅关系表格 */}
                                    <h4 className="section-title" style={{ marginTop: 20, marginBottom: 12 }}>
                                        订阅关系
                                    </h4>
                                    <Table
                                        columns={subscriptionColumns}
                                        dataSource={subscriptionData}
                                        pagination={false}
                                        size="small"
                                    />
                                </div>
                            ),
                        },
                        {
                            key: 'instances',
                            label: '在线实例',
                            children: (
                                <div className="tab-instances">
                                    <Table
                                        columns={[
                                            { title: '实例ID', dataIndex: 'instanceId', key: 'instanceId' },
                                            { title: '地址', dataIndex: 'address', key: 'address' },
                                            { title: '版本', dataIndex: 'version', key: 'version' },
                                            {
                                                title: '状态',
                                                dataIndex: 'status',
                                                key: 'status',
                                                render: () => <Tag color="success">在线</Tag>,
                                            },
                                        ]}
                                        dataSource={[
                                            { key: '1', instanceId: 'cid-001', address: '10.0.1.10:10911', version: '5.3.0', status: 'online' },
                                            { key: '2', instanceId: 'cid-002', address: '10.0.1.11:10911', version: '5.3.0', status: 'online' },
                                            { key: '3', instanceId: 'cid-003', address: '10.0.1.12:10911', version: '5.3.0', status: 'online' },
                                            { key: '4', instanceId: 'cid-004', address: '10.0.1.13:10911', version: '5.3.0', status: 'online' },
                                        ]}
                                        pagination={false}
                                        size="small"
                                    />
                                </div>
                            ),
                        },
                        {
                            key: 'progress',
                            label: '消费进度',
                            children: (
                                <div className="tab-progress">
                                    <Table
                                        columns={[
                                            { title: 'Topic', dataIndex: 'topic', key: 'topic' },
                                            { title: 'QueueId', dataIndex: 'queueId', key: 'queueId' },
                                            { title: 'Broker Offset', dataIndex: 'brokerOffset', key: 'brokerOffset' },
                                            { title: 'Consumer Offset', dataIndex: 'consumerOffset', key: 'consumerOffset' },
                                            {
                                                title: 'Diff',
                                                dataIndex: 'diff',
                                                key: 'diff',
                                                render: (v) => (
                                                    <span style={{ color: v > 100 ? '#ff4d4f' : '#52c41a', fontWeight: 500 }}>
                                                        {v}
                                                    </span>
                                                ),
                                            },
                                        ]}
                                        dataSource={[
                                            { key: '1', topic: 'ORDER_TOPIC', queueId: '0', brokerOffset: '125,680', consumerOffset: '125,400', diff: 280 },
                                            { key: '2', topic: 'ORDER_TOPIC', queueId: '1', brokerOffset: '98,200', consumerOffset: '98,200', diff: 0 },
                                            { key: '3', topic: 'PAYMENT_TOPIC', queueId: '0', brokerOffset: '45,300', consumerOffset: '45,300', diff: 0 },
                                            { key: '4', topic: 'INVENTORY_TOPIC', queueId: '0', brokerOffset: '230,100', consumerOffset: '173,300', diff: 56800 },
                                        ]}
                                        pagination={false}
                                        size="small"
                                    />
                                </div>
                            ),
                        },
                    ]}
                />
            </Modal>
        </div>
    );
};

export default GroupManagement;