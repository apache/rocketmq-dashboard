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
    Spin,
    notification,
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
import { remoteApi } from '../../api/remoteApi/remoteApi';
import { useLanguage } from '../../i18n/LanguageContext';
import './GroupManagement.css';

// 自动刷新间隔（毫秒）
const AUTO_REFRESH_INTERVAL = 10000;

const GroupManagement = () => {
    const { t } = useLanguage();
    const [api, contextHolder] = notification.useNotification();

    const [loading, setLoading] = useState(false);
    const [searchText, setSearchText] = useState('');
    const [modalVisible, setModalVisible] = useState(false);
    const [selectedGroup, setSelectedGroup] = useState(null);
    const [autoRefresh, setAutoRefresh] = useState(false);

    // 列表数据
    const [groupData, setGroupData] = useState([]);

    // 详情弹窗数据（按需加载）
    const [detailLoading, setDetailLoading] = useState(false);
    const [connectionData, setConnectionData] = useState(null);
    const [subscriptionData, setSubscriptionData] = useState(null);

    // 加载消费组列表
    const loadGroupList = useCallback(async () => {
        setLoading(true);
        try {
            const resp = await remoteApi.queryConsumerGroupList(false);
            if (resp.status === 0) {
                const groups = (resp.data || []).map((item, index) => ({
                    key: item.group || index,
                    group: item.group,
                    count: item.count || 0,
                    consumeType: item.consumeType || '-',
                    messageModel: item.messageModel || '-',
                    diffTotal: item.diffTotal || 0,
                    consumeTps: item.consumeTps || 0,
                    version: item.version || '-',
                    updateTime: item.updateTime || '-',
                    subGroupType: item.subGroupType || 'NORMAL',
                    address: item.address || '',
                    // 根据堆积量推断状态
                    status: (item.diffTotal || 0) > 10000 ? 'warning' : 'running',
                }));
                setGroupData(groups);
            } else {
                api.error({ message: t.FAILED_TO_FETCH_DATA || 'Failed to fetch consumer groups', description: resp.errMsg, duration: 3 });
            }
        } catch (error) {
            console.error('Error loading consumer groups:', error);
            api.error({ message: t.FAILED_TO_FETCH_DATA || 'Failed to fetch data', description: error.message, duration: 3 });
        } finally {
            setLoading(false);
        }
    }, [t, api]);

    // 加载消费组详情（连接信息 + 订阅关系）
    const loadGroupDetail = useCallback(async (groupName, address) => {
        setDetailLoading(true);
        try {
            const [connResp, topicResp] = await Promise.allSettled([
                remoteApi.queryConsumerConnection(groupName, address || ''),
                remoteApi.queryTopicByConsumer(groupName, address || ''),
            ]);

            // 处理连接信息
            if (connResp.status === 'fulfilled' && connResp.value?.status === 0) {
                setConnectionData(connResp.value.data);
            } else {
                setConnectionData(null);
            }

            // 处理订阅关系
            if (topicResp.status === 'fulfilled' && topicResp.value?.status === 0) {
                const topics = (topicResp.value.data || []).map((item, index) => ({
                    key: item.topic || index,
                    topic: item.topic || '-',
                    subString: item.subString || '*',
                    expressionType: item.expressionType || 'TAG',
                }));
                setSubscriptionData(topics);
            } else {
                setSubscriptionData([]);
            }
        } catch (error) {
            console.error('Error loading group detail:', error);
            setConnectionData(null);
            setSubscriptionData([]);
        } finally {
            setDetailLoading(false);
        }
    }, []);

    // 初始加载
    useEffect(() => {
        loadGroupList();
    }, [loadGroupList]);

    // 自动刷新
    useEffect(() => {
        if (!autoRefresh) return;
        const timer = setInterval(loadGroupList, AUTO_REFRESH_INTERVAL);
        return () => clearInterval(timer);
    }, [autoRefresh, loadGroupList]);

    // 表格列定义
    const columns = [
        {
            title: 'Group名称',
            dataIndex: 'group',
            key: 'group',
            render: (text) => {
                if (!text) return '-';
                const sysFlag = text.startsWith('%SYS%');
                return (
                    <a onClick={() => handleViewDetail(text)} style={{ color: sysFlag ? 'red' : '#1677ff', fontWeight: 500 }}>
                        {sysFlag ? text.substring(5) : text}
                    </a>
                );
            },
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
            title: '消费类型',
            dataIndex: 'consumeType',
            key: 'consumeType',
        },
        {
            title: '消费TPS',
            dataIndex: 'consumeTps',
            key: 'consumeTps',
            render: (tps) => <span style={{ fontWeight: 500 }}>{tps}</span>,
        },
        {
            title: '堆积量',
            dataIndex: 'diffTotal',
            key: 'diffTotal',
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
                    <Button type="link" size="small" onClick={() => handleViewDetail(record.group, record.address)}>
                        详情
                    </Button>
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
            title: '订阅表达式',
            dataIndex: 'subString',
            key: 'subString',
            render: (text) => <code style={{ background: '#f5f5f5', padding: '2px 6px', borderRadius: 4, fontSize: 12 }}>{text}</code>,
        },
        {
            title: '表达式类型',
            dataIndex: 'expressionType',
            key: 'expressionType',
            render: (text) => <Tag color="blue">{text}</Tag>,
        },
    ];

    // 实例表格列
    const instanceColumns = [
        { title: '客户端ID', dataIndex: 'clientId', key: 'clientId', ellipsis: true },
        { title: '客户端地址', dataIndex: 'clientAddr', key: 'clientAddr', ellipsis: true },
        { title: '语言', dataIndex: 'language', key: 'language' },
        { title: '版本', dataIndex: 'versionDesc', key: 'versionDesc' },
    ];

    const handleViewDetail = (groupName, address) => {
        setSelectedGroup(groupName);
        setModalVisible(true);
        setConnectionData(null);
        setSubscriptionData(null);
        loadGroupDetail(groupName, address);
    };

    // 从连接数据中提取概览信息
    const getOverviewFromData = () => {
        const connInfo = connectionData;
        const groupInfo = groupData.find(g => g.group === selectedGroup);
        return {
            onlineInstances: connInfo?.connectionSet?.length || groupInfo?.count || 0,
            totalDiff: groupInfo?.diffTotal?.toLocaleString() || '0',
            subscribedTopicCount: subscriptionData?.length || 0,
            groupName: selectedGroup,
            messageModel: groupInfo?.messageModel || '-',
            consumeType: groupInfo?.consumeType || '-',
            consumeTps: groupInfo?.consumeTps || 0,
        };
    };

    const overview = getOverviewFromData();

    return (
        <div className="group-management-page">
            {contextHolder}
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
                    <Switch
                        checked={autoRefresh}
                        onChange={setAutoRefresh}
                        checkedChildren="自动刷新"
                        unCheckedChildren="手动"
                        size="small"
                    />
                    <Button
                        icon={<ReloadOutlined />}
                        size="small"
                        onClick={loadGroupList}
                        loading={loading}
                    >
                        刷新
                    </Button>
                </Space>
            </div>

            <Card bordered={false} className="table-card">
                <Table
                    columns={columns}
                    dataSource={groupData.filter((d) =>
                        !searchText || d.group.toLowerCase().includes(searchText.toLowerCase())
                    )}
                    pagination={{
                        pageSize: 10,
                        showTotal: (total) => `共 ${total} 个Group`,
                        showSizeChanger: true,
                    }}
                    size="middle"
                    loading={loading}
                    locale={{ emptyText: '暂无消费组数据' }}
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
                <Spin spinning={detailLoading} tip="加载详情...">
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
                                                        {overview.onlineInstances}
                                                        <Tag color="success" style={{ marginLeft: 8 }}>在线</Tag>
                                                    </div>
                                                </Card>
                                            </Col>
                                            <Col span={8}>
                                                <Card className="stat-card stat-card-danger" bordered={false}>
                                                    <div className="stat-label">总堆积</div>
                                                    <div className="stat-value danger">{overview.totalDiff}</div>
                                                </Card>
                                            </Col>
                                            <Col span={8}>
                                                <Card className="stat-card" bordered={false}>
                                                    <div className="stat-label">订阅Topic数量</div>
                                                    <div className="stat-value">{overview.subscribedTopicCount}</div>
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
                                            <Descriptions.Item label="Group名称">{overview.groupName}</Descriptions.Item>
                                            <Descriptions.Item label="订阅模式">
                                                <Tag color={overview.messageModel === 'CLUSTERING' ? 'blue' : 'orange'}>
                                                    {overview.messageModel === 'CLUSTERING' ? '集群消费' : '广播消费'}
                                                </Tag>
                                            </Descriptions.Item>
                                            <Descriptions.Item label="消费类型">{overview.consumeType}</Descriptions.Item>
                                            <Descriptions.Item label="消费TPS">{overview.consumeTps}</Descriptions.Item>
                                        </Descriptions>

                                        {/* 订阅关系表格 */}
                                        <h4 className="section-title" style={{ marginTop: 20, marginBottom: 12 }}>
                                            订阅关系
                                        </h4>
                                        <Table
                                            columns={subscriptionColumns}
                                            dataSource={subscriptionData || []}
                                            pagination={false}
                                            size="small"
                                            locale={{ emptyText: '暂无订阅关系数据' }}
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
                                            columns={instanceColumns}
                                            dataSource={(connectionData?.connectionSet || []).map((c, i) => ({
                                                key: c.clientId || i,
                                                ...c,
                                            }))}
                                            pagination={false}
                                            size="small"
                                            locale={{ emptyText: '暂无在线实例' }}
                                        />
                                    </div>
                                ),
                            },
                        ]}
                    />
                </Spin>
            </Modal>
        </div>
    );
};

export default GroupManagement;