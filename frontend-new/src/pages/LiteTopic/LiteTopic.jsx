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
    Select,
    Tag,
    Modal,
    Drawer,
    Card,
    Row,
    Col,
    Progress,
    Space,
    Statistic,
    notification,
    Descriptions,
    Form,
    InputNumber,
    Alert,
} from 'antd';
import {
    SearchOutlined,
    ReloadOutlined,
    ClockCircleOutlined,
    AppstoreOutlined,
    TeamOutlined,
    DashboardOutlined,
    ExclamationCircleOutlined,
    EyeOutlined,
    EditOutlined,
} from '@ant-design/icons';
import { useLanguage } from '../../i18n/LanguageContext';
import { remoteApi } from '../../api/remoteApi/remoteApi';
import './LiteTopic.css';

const { Option } = Select;

const LiteTopic = () => {
    const { t } = useLanguage();

    const [loading, setLoading] = useState(false);
    const [topicList, setTopicList] = useState([]);
    const [quota, setQuota] = useState(null);
    const [capabilitySupported, setCapabilitySupported] = useState(true);
    const [patternFilter, setPatternFilter] = useState('');
    const [namespaceFilter, setNamespaceFilter] = useState('');

    // Session drawer state
    const [sessionDrawerVisible, setSessionDrawerVisible] = useState(false);
    const [sessionData, setSessionData] = useState(null);
    const [sessionLoading, setSessionLoading] = useState(false);

    // Extend TTL modal state
    const [extendTTLModalVisible, setExtendTTLModalVisible] = useState(false);
    const [extendTTLForm, setExtendTTLForm] = useState({ topicPattern: '', newTTL: null });
    const [extendTTLLoading, setExtendTTLLoading] = useState(false);

    // Check capability on mount
    useEffect(() => {
        checkCapability();
    }, []);

    // Load data on mount and when filters change
    useEffect(() => {
        if (capabilitySupported) {
            fetchQuota();
            fetchTopicList();
        }
    }, [capabilitySupported, namespaceFilter]);

    const checkCapability = async () => {
        try {
            const result = await remoteApi.queryLiteTopicCapability();
            if (result.status === 0 && result.data) {
                setCapabilitySupported(result.data.supported !== false);
            } else if (result.status !== 0) {
                setCapabilitySupported(false);
            }
        } catch (error) {
            setCapabilitySupported(false);
        }
    };

    const fetchQuota = async () => {
        try {
            const result = await remoteApi.queryLiteTopicQuota(namespaceFilter);
            if (result.status === 0 && result.data) {
                setQuota(result.data);
            }
        } catch (error) {
            console.error('Failed to fetch quota:', error);
        }
    };

    const fetchTopicList = async () => {
        setLoading(true);
        try {
            const result = await remoteApi.queryLiteTopicList(patternFilter, namespaceFilter);
            if (result.status === 0 && result.data) {
                setTopicList(Array.isArray(result.data) ? result.data : []);
            } else {
                setTopicList([]);
                if (result.errMsg) {
                    notification.error({ message: t.LITETOPIC_FETCH_LIST_FAILED, description: result.errMsg });
                }
            }
        } catch (error) {
            console.error('Failed to fetch topic list:', error);
            notification.error({ message: t.LITETOPIC_FETCH_LIST_FAILED });
            setTopicList([]);
        } finally {
            setLoading(false);
        }
    };

    const handleSearch = () => {
        fetchTopicList();
    };

    const handleRefresh = () => {
        fetchQuota();
        fetchTopicList();
    };

    const handleViewSessions = async (sessionId) => {
        setSessionDrawerVisible(true);
        setSessionLoading(true);
        try {
            const result = await remoteApi.queryLiteTopicSession(sessionId);
            if (result.status === 0 && result.data) {
                setSessionData(result.data);
            } else {
                notification.error({ message: t.LITETOPIC_FETCH_SESSION_FAILED, description: result.errMsg });
                setSessionData(null);
            }
        } catch (error) {
            console.error('Failed to fetch session:', error);
            notification.error({ message: t.LITETOPIC_FETCH_SESSION_FAILED });
            setSessionData(null);
        } finally {
            setSessionLoading(false);
        }
    };

    const handleOpenExtendTTL = (record) => {
        setExtendTTLForm({ topicPattern: record.topicPattern || '', newTTL: null });
        setExtendTTLModalVisible(true);
    };

    const handleExtendTTL = async () => {
        if (!extendTTLForm.topicPattern || !extendTTLForm.newTTL) {
            return;
        }
        setExtendTTLLoading(true);
        try {
            const result = await remoteApi.extendLiteTopicTTL(extendTTLForm.topicPattern, extendTTLForm.newTTL);
            if (result.status === 0) {
                notification.success({ message: t.LITETOPIC_EXTEND_TTL_SUCCESS });
                setExtendTTLModalVisible(false);
                fetchTopicList();
                fetchQuota();
            } else {
                notification.error({ message: t.LITETOPIC_EXTEND_TTL_FAILED, description: result.errMsg });
            }
        } catch (error) {
            console.error('Failed to extend TTL:', error);
            notification.error({ message: t.LITETOPIC_EXTEND_TTL_FAILED });
        } finally {
            setExtendTTLLoading(false);
        }
    };

    const formatTime = (timestamp) => {
        if (!timestamp) return '-';
        const date = new Date(timestamp);
        return date.toLocaleString();
    };

    const formatDuration = (ms) => {
        if (!ms && ms !== 0) return '-';
        if (ms < 1000) return `${ms}ms`;
        if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
        if (ms < 3600000) return `${(ms / 60000).toFixed(1)}min`;
        return `${(ms / 3600000).toFixed(1)}h`;
    };

    const getTTLStatusTag = (status) => {
        const statusMap = {
            ACTIVE: { color: 'success', label: t.LITETOPIC_ACTIVE },
            EXPIRING_SOON: { color: 'warning', label: t.LITETOPIC_EXPIRING_SOON },
            EXPIRED: { color: 'error', label: t.LITETOPIC_EXPIRED },
            UNKNOWN: { color: 'default', label: t.LITETOPIC_UNKNOWN },
        };
        const config = statusMap[status] || statusMap.UNKNOWN;
        return <Tag color={config.color}>{config.label}</Tag>;
    };

    const getProgressStatus = (percent) => {
        if (percent >= 90) return 'exception';
        if (percent >= 70) return 'active';
        return 'normal';
    };

    // Table columns
    const columns = [
        {
            title: t.LITETOPIC_PATTERN,
            dataIndex: 'topicPattern',
            key: 'topicPattern',
            render: (text) => <span style={{ fontWeight: 500, color: '#1677ff' }}>{text}</span>,
            ellipsis: true,
        },
        {
            title: t.LITETOPIC_TOPIC_COUNT,
            dataIndex: 'topicCount',
            key: 'topicCount',
            render: (val) => <span style={{ fontWeight: 500 }}>{val ?? '-'}</span>,
            sorter: (a, b) => (a.topicCount || 0) - (b.topicCount || 0),
        },
        {
            title: t.LITETOPIC_CONSUMERS,
            dataIndex: 'consumerCount',
            key: 'consumerCount',
            render: (val) => <span style={{ fontWeight: 500 }}>{val ?? '-'}</span>,
            sorter: (a, b) => (a.consumerCount || 0) - (b.consumerCount || 0),
        },
        {
            title: t.LITETOPIC_BACKLOG,
            dataIndex: 'totalBacklog',
            key: 'totalBacklog',
            render: (val) => {
                const num = val ?? 0;
                const color = num > 10000 ? '#ff4d4f' : num > 0 ? '#fa8c16' : '#52c41a';
                return <span style={{ color, fontWeight: 500 }}>{num.toLocaleString()}</span>;
            },
            sorter: (a, b) => (a.totalBacklog || 0) - (b.totalBacklog || 0),
        },
        {
            title: t.LITETOPIC_AVG_TTL,
            dataIndex: 'averageTTL',
            key: 'averageTTL',
            render: (val) => formatDuration(val),
            sorter: (a, b) => (a.averageTTL || 0) - (b.averageTTL || 0),
        },
        {
            title: t.LITETOPIC_STATUS,
            dataIndex: 'ttlStatus',
            key: 'ttlStatus',
            render: (status) => getTTLStatusTag(status),
        },
        {
            title: t.LITETOPIC_LAST_ACTIVE,
            dataIndex: 'lastActiveTime',
            key: 'lastActiveTime',
            render: (val) => formatTime(val),
            sorter: (a, b) => (a.lastActiveTime || 0) - (b.lastActiveTime || 0),
        },
        {
            title: t.LITETOPIC_ACTIONS,
            key: 'action',
            render: (_, record) => (
                <Space size="small">
                    <Button
                        type="link"
                        size="small"
                        icon={<EditOutlined />}
                        onClick={(e) => { e.stopPropagation(); handleOpenExtendTTL(record); }}
                    >
                        {t.LITETOPIC_EXTEND_TTL}
                    </Button>
                    {record.sessionIds && record.sessionIds.length > 0 && (
                        <Button
                            type="link"
                            size="small"
                            icon={<EyeOutlined />}
                            onClick={(e) => { e.stopPropagation(); handleViewSessions(record.sessionIds[0]); }}
                        >
                            {t.LITETOPIC_VIEW_SESSIONS}
                        </Button>
                    )}
                </Space>
            ),
        },
    ];

    // Render quota panel
    const renderQuotaPanel = () => {
        if (!quota) return null;

        const topicUsagePercent = quota.usageRate != null
            ? Math.round(quota.usageRate * 100)
            : (quota.maxTopicCount > 0 ? Math.round((quota.currentTopicCount / quota.maxTopicCount) * 100) : 0);
        const sessionUsagePercent = quota.sessionUsageRate != null
            ? Math.round(quota.sessionUsageRate * 100)
            : (quota.maxSessionCount > 0 ? Math.round((quota.currentSessionCount / quota.maxSessionCount) * 100) : 0);
        const creationRatePercent = quota.maxCreationRate > 0
            ? Math.round((quota.currentCreationRate / quota.maxCreationRate) * 100)
            : 0;

        return (
            <Card bordered={false} className="quota-card" style={{ marginBottom: 16 }}>
                <div className="quota-header">
                    <h3 style={{ margin: 0, fontSize: 15, fontWeight: 600 }}>
                        <DashboardOutlined style={{ marginRight: 8, color: '#1677ff' }} />
                        {t.LITETOPIC_QUOTA_OVERVIEW}
                    </h3>
                </div>
                <Row gutter={24} style={{ marginTop: 16 }}>
                    <Col span={8}>
                        <div className="quota-item">
                            <div className="quota-label">{t.LITETOPIC_TOPIC_USAGE}</div>
                            <Progress
                                percent={topicUsagePercent}
                                status={getProgressStatus(topicUsagePercent)}
                                strokeColor={topicUsagePercent >= 90 ? '#ff4d4f' : topicUsagePercent >= 70 ? '#fa8c16' : '#1677ff'}
                            />
                            <div className="quota-detail">
                                {quota.currentTopicCount} / {quota.maxTopicCount}
                            </div>
                        </div>
                    </Col>
                    <Col span={8}>
                        <div className="quota-item">
                            <div className="quota-label">{t.LITETOPIC_SESSION_USAGE}</div>
                            <Progress
                                percent={sessionUsagePercent}
                                status={getProgressStatus(sessionUsagePercent)}
                                strokeColor={sessionUsagePercent >= 90 ? '#ff4d4f' : sessionUsagePercent >= 70 ? '#fa8c16' : '#1677ff'}
                            />
                            <div className="quota-detail">
                                {quota.currentSessionCount} / {quota.maxSessionCount}
                            </div>
                        </div>
                    </Col>
                    <Col span={8}>
                        <div className="quota-item">
                            <div className="quota-label">{t.LITETOPIC_CREATION_RATE}</div>
                            <Progress
                                percent={creationRatePercent}
                                status={getProgressStatus(creationRatePercent)}
                                strokeColor={creationRatePercent >= 90 ? '#ff4d4f' : creationRatePercent >= 70 ? '#fa8c16' : '#1677ff'}
                            />
                            <div className="quota-detail">
                                {quota.currentCreationRate} / {quota.maxCreationRate}
                            </div>
                        </div>
                    </Col>
                </Row>
                <Row gutter={16} style={{ marginTop: 16 }}>
                    <Col span={6}>
                        <Statistic title={t.LITETOPIC_DEFAULT_TTL} value={formatDuration(quota.defaultTTL)} />
                    </Col>
                    <Col span={6}>
                        <Statistic title={t.LITETOPIC_MAX_TTL} value={formatDuration(quota.maxTTL)} />
                    </Col>
                    <Col span={6}>
                        <Statistic title={t.LITETOPIC_REMAINING_QUOTA} value={quota.remainingQuota ?? '-'} />
                    </Col>
                    <Col span={6}>
                        <Statistic title={t.LITETOPIC_CONSUMER_DENSITY} value={quota.consumerDensity ?? '-'} />
                    </Col>
                </Row>
            </Card>
        );
    };

    // Render session drawer content
    const renderSessionContent = () => {
        if (sessionLoading) {
            return <div style={{ textAlign: 'center', padding: 40 }}>{t.LOADING}...</div>;
        }
        if (!sessionData) {
            return <div style={{ textAlign: 'center', padding: 40 }}>{t.NO_DATA}</div>;
        }

        const consumptionPercent = sessionData.totalMessages > 0
            ? Math.round((sessionData.consumedMessages / sessionData.totalMessages) * 100)
            : 0;

        return (
            <div className="session-detail-content">
                <Descriptions
                    column={2}
                    bordered
                    size="small"
                    className="info-descriptions"
                >
                    <Descriptions.Item label={t.LITETOPIC_SESSION_ID} span={2}>
                        <code style={{ fontSize: 12 }}>{sessionData.sessionId}</code>
                    </Descriptions.Item>
                    <Descriptions.Item label={t.LITETOPIC_CLIENT_ID}>
                        {sessionData.clientId || '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label={t.LITETOPIC_CLIENT_ADDRESS}>
                        <code style={{ fontSize: 12 }}>{sessionData.clientAddress || '-'}</code>
                    </Descriptions.Item>
                    <Descriptions.Item label={t.LITETOPIC_PARENT_TOPIC}>
                        {sessionData.parentTopic || '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label={t.LITETOPIC_CONSUMER_GROUP}>
                        {sessionData.consumerGroup || '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label={t.LITETOPIC_CREATE_TIME}>
                        {formatTime(sessionData.createTime)}
                    </Descriptions.Item>
                    <Descriptions.Item label={t.LITETOPIC_LAST_ACTIVE}>
                        {formatTime(sessionData.lastActiveTime)}
                    </Descriptions.Item>
                    <Descriptions.Item label={t.LITETOPIC_TTL}>
                        {formatDuration(sessionData.ttl)}
                    </Descriptions.Item>
                    <Descriptions.Item label={t.LITETOPIC_TTL_REMAINING}>
                        {formatDuration(sessionData.ttlRemaining)}
                    </Descriptions.Item>
                    <Descriptions.Item label={t.LITETOPIC_SESSION_STATUS}>
                        <Tag color={sessionData.status === 'ACTIVE' ? 'success' : sessionData.status === 'EXPIRED' ? 'error' : 'default'}>
                            {sessionData.status || '-'}
                        </Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label={t.LITETOPIC_CREATION_COUNT}>
                        {sessionData.liteTopicCreationCount ?? '-'}
                    </Descriptions.Item>
                </Descriptions>

                <h4 className="section-title" style={{ marginTop: 20, marginBottom: 12 }}>
                    {t.LITETOPIC_CONSUMPTION_RATE}
                </h4>
                <Row gutter={16}>
                    <Col span={8}>
                        <Card className="stat-card" bordered={false}>
                            <div className="stat-label">{t.LITETOPIC_TOTAL_MESSAGES}</div>
                            <div className="stat-value">{sessionData.totalMessages ?? 0}</div>
                        </Card>
                    </Col>
                    <Col span={8}>
                        <Card className="stat-card" bordered={false}>
                            <div className="stat-label">{t.LITETOPIC_CONSUMED_MESSAGES}</div>
                            <div className="stat-value">{sessionData.consumedMessages ?? 0}</div>
                        </Card>
                    </Col>
                    <Col span={8}>
                        <Card className="stat-card stat-card-warning" bordered={false}>
                            <div className="stat-label">{t.LITETOPIC_PENDING_MESSAGES}</div>
                            <div className="stat-value warning">{sessionData.pendingMessages ?? 0}</div>
                        </Card>
                    </Col>
                </Row>

                <div style={{ marginTop: 16 }}>
                    <div style={{ marginBottom: 8, fontWeight: 500 }}>{t.LITETOPIC_CONSUMPTION_RATE}</div>
                    <Progress
                        percent={consumptionPercent}
                        status={consumptionPercent >= 100 ? 'success' : 'active'}
                        strokeColor={consumptionPercent >= 100 ? '#52c41a' : '#1677ff'}
                    />
                </div>

                {sessionData.popProgress != null && (
                    <div style={{ marginTop: 16 }}>
                        <div style={{ marginBottom: 8, fontWeight: 500 }}>{t.LITETOPIC_POP_PROGRESS}</div>
                        <Progress
                            percent={Math.round(sessionData.popProgress * 100)}
                            status="active"
                            strokeColor="#722ed1"
                        />
                    </div>
                )}

                {sessionData.liteTopics && sessionData.liteTopics.length > 0 && (
                    <>
                        <h4 className="section-title" style={{ marginTop: 20, marginBottom: 12 }}>
                            {t.LITETOPIC_LITETOPICS}
                        </h4>
                        <Table
                            dataSource={sessionData.liteTopics.map((lt, idx) => ({
                                key: idx,
                                ...lt,
                            }))}
                            columns={[
                                { title: t.TOPIC_NAME, dataIndex: 'topicName', key: 'topicName', render: (text) => <span style={{ fontWeight: 500 }}>{text}</span> },
                                { title: t.LITETOPIC_STATUS, dataIndex: 'status', key: 'status', render: (s) => <Tag color={s === 'ACTIVE' ? 'success' : 'default'}>{s}</Tag> },
                                { title: t.LITETOPIC_TTL_REMAINING, dataIndex: 'ttlRemaining', key: 'ttlRemaining', render: (v) => formatDuration(v) },
                            ]}
                            pagination={false}
                            size="small"
                        />
                    </>
                )}
            </div>
        );
    };

    // Graceful degradation for 4.0 clusters
    if (!capabilitySupported) {
        return (
            <div className="litetopic-page">
                <div className="page-header">
                    <h2 className="page-title">
                        <AppstoreOutlined style={{ marginRight: 8, color: '#1677ff' }} />
                        {t.LITETOPIC_MANAGEMENT}
                    </h2>
                </div>
                <Alert
                    message={t.LITETOPIC_NOT_SUPPORTED}
                    type="info"
                    showIcon
                    icon={<ExclamationCircleOutlined />}
                    style={{ marginTop: 16 }}
                />
            </div>
        );
    }

    return (
        <div className="litetopic-page">
            <div className="page-header">
                <h2 className="page-title">
                    <AppstoreOutlined style={{ marginRight: 8, color: '#1677ff' }} />
                    {t.LITETOPIC_MANAGEMENT}
                </h2>
                <Space size="middle">
                    <Button icon={<ReloadOutlined />} size="small" onClick={handleRefresh}>
                        {t.REFRESH}
                    </Button>
                </Space>
            </div>

            {/* Quota Panel */}
            {renderQuotaPanel()}

            {/* Search / Filter Bar */}
            <Card bordered={false} className="filter-card" style={{ marginBottom: 16 }}>
                <Space size="middle" wrap>
                    <Input
                        placeholder={t.LITETOPIC_SEARCH_PATTERN_PLACEHOLDER}
                        prefix={<SearchOutlined />}
                        value={patternFilter}
                        onChange={(e) => setPatternFilter(e.target.value)}
                        onPressEnter={handleSearch}
                        style={{ width: 260 }}
                        allowClear
                    />
                    <Select
                        placeholder={t.LITETOPIC_NAMESPACE_PLACEHOLDER}
                        value={namespaceFilter || undefined}
                        onChange={(val) => setNamespaceFilter(val || '')}
                        style={{ width: 180 }}
                        allowClear
                    >
                        <Option value="">{t.LITETOPIC_ALL_NAMESPACES}</Option>
                    </Select>
                    <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
                        {t.SEARCH}
                    </Button>
                </Space>
            </Card>

            {/* Main Table */}
            <Card bordered={false} className="table-card">
                <Table
                    columns={columns}
                    dataSource={topicList}
                    rowKey={(record) => record.topicPattern || record.key || Math.random().toString()}
                    loading={loading}
                    pagination={{
                        pageSize: 10,
                        showTotal: (total) => t.LITETOPIC_TOTAL.replace('{total}', total),
                        showSizeChanger: true,
                    }}
                    size="middle"
                />
            </Card>

            {/* Session Detail Drawer */}
            <Drawer
                title={
                    <span>
                        <TeamOutlined style={{ marginRight: 8, color: '#1677ff' }} />
                        {t.LITETOPIC_SESSION_DETAIL}
                    </span>
                }
                placement="right"
                width={680}
                open={sessionDrawerVisible}
                onClose={() => { setSessionDrawerVisible(false); setSessionData(null); }}
                destroyOnClose
            >
                {renderSessionContent()}
            </Drawer>

            {/* Extend TTL Modal */}
            <Modal
                title={t.LITETOPIC_EXTEND_TTL_MODAL_TITLE}
                open={extendTTLModalVisible}
                onOk={handleExtendTTL}
                onCancel={() => setExtendTTLModalVisible(false)}
                confirmLoading={extendTTLLoading}
                okText={t.CONFIRM}
                cancelText={t.CANCEL}
                destroyOnClose
            >
                <Form layout="vertical" style={{ marginTop: 16 }}>
                    <Form.Item label={t.LITETOPIC_PATTERN}>
                        <Input
                            value={extendTTLForm.topicPattern}
                            onChange={(e) => setExtendTTLForm({ ...extendTTLForm, topicPattern: e.target.value })}
                            disabled
                        />
                    </Form.Item>
                    <Form.Item label={t.LITETOPIC_NEW_TTL} required>
                        <InputNumber
                            style={{ width: '100%' }}
                            placeholder={t.LITETOPIC_NEW_TTL_PLACEHOLDER}
                            value={extendTTLForm.newTTL}
                            onChange={(val) => setExtendTTLForm({ ...extendTTLForm, newTTL: val })}
                            min={1}
                        />
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default LiteTopic;
