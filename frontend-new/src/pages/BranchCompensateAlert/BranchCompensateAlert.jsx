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

import React, {useEffect, useState, useMemo} from 'react';
import {
    Button,
    Table,
    Tag,
    Space,
    Modal,
    Form,
    Input,
    InputNumber,
    Select,
    Switch,
    message,
    Popconfirm,
    Tooltip,
    Card,
    Row,
    Col,
    Statistic,
    Badge
} from 'antd';
import {
    PlusOutlined,
    EditOutlined,
    DeleteOutlined,
    ReloadOutlined,
    AlertOutlined,
    SearchOutlined,
    SyncOutlined
} from '@ant-design/icons';
import {remoteApi} from '../../api/remoteApi/remoteApi';
import {useLanguage} from '../../i18n/LanguageContext';

const {Option} = Select;
const {TextArea} = Input;

const SEVERITY_COLORS = {
    critical: 'red',
    warning: 'orange',
    info: 'blue'
};

const LAG_UNIT_OPTIONS = ['B', 'KB', 'MB', 'GB'];

const BranchCompensateAlert = () => {
    const [rules, setRules] = useState([]);
    const [loading, setLoading] = useState(false);
    const [modalVisible, setModalVisible] = useState(false);
    const [editingRule, setEditingRule] = useState(null);
    const [searchText, setSearchText] = useState('');
    const [filterSeverity, setFilterSeverity] = useState('all');
    const [filterStatus, setFilterStatus] = useState('all');
    const [form] = Form.useForm();
    const [messageApi, msgContextHolder] = message.useMessage();
    const {t} = useLanguage();

    const fetchRules = async () => {
        setLoading(true);
        try {
            const result = await remoteApi.listBranchCompensateAlertRules();
            if (result && result.status === 0 && Array.isArray(result.data)) {
                setRules(result.data.map((r, i) => ({...r, key: r.id, index: i + 1})));
            } else {
                messageApi.error(result?.errMsg || t.BC_ALERT_FETCH_FAILED || 'Failed to fetch branch compensate alert rules');
            }
        } catch (error) {
            console.error('Error fetching branch compensate alert rules:', error);
            messageApi.error(t.BC_ALERT_FETCH_FAILED || 'Failed to fetch branch compensate alert rules');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchRules();
    }, []);

    const handleToggleRule = async (record) => {
        const next = !record.enabled;
        setRules(prev => prev.map(r => r.id === record.id ? {...r, enabled: next} : r));
        try {
            const result = await remoteApi.toggleBranchCompensateAlertRule(record.id, next);
            if (!(result && result.status === 0)) {
                setRules(prev => prev.map(r => r.id === record.id ? {...r, enabled: !next} : r));
                messageApi.error(result?.errMsg || t.BC_ALERT_UPDATE_FAILED || 'Failed to update rule');
            }
        } catch (error) {
            setRules(prev => prev.map(r => r.id === record.id ? {...r, enabled: !next} : r));
            messageApi.error(t.BC_ALERT_UPDATE_FAILED || 'Failed to update rule');
        }
    };

    const handleAddRule = () => {
        setEditingRule(null);
        form.resetFields();
        form.setFieldsValue({
            brokerName: '*',
            clusterName: '*',
            lagThreshold: 1,
            lagThresholdUnit: 'GB',
            duration: '5m',
            severity: 'warning',
            enabled: true,
            channels: ['email']
        });
        setModalVisible(true);
    };

    const handleEditRule = (rule) => {
        setEditingRule(rule);
        form.setFieldsValue({
            name: rule.name,
            brokerName: rule.brokerName,
            clusterName: rule.clusterName,
            lagThreshold: rule.lagThreshold,
            lagThresholdUnit: rule.lagThresholdUnit,
            duration: rule.duration,
            severity: rule.severity,
            channels: rule.channels || [],
            description: rule.description,
            enabled: rule.enabled
        });
        setModalVisible(true);
    };

    const handleDeleteRule = async (id) => {
        try {
            const result = await remoteApi.deleteBranchCompensateAlertRule(id);
            if (result && result.status === 0) {
                setRules(prev => prev.filter(r => r.id !== id));
                messageApi.success(t.BC_ALERT_DELETE_SUCCESS || 'Rule deleted');
            } else {
                messageApi.error(result?.errMsg || t.BC_ALERT_DELETE_FAILED || 'Failed to delete rule');
            }
        } catch (error) {
            messageApi.error(t.BC_ALERT_DELETE_FAILED || 'Failed to delete rule');
        }
    };

    const handleModalOk = async () => {
        try {
            const values = await form.validateFields();
            const payload = {
                name: values.name,
                brokerName: values.brokerName,
                clusterName: values.clusterName,
                lagThreshold: values.lagThreshold,
                lagThresholdUnit: values.lagThresholdUnit,
                duration: values.duration,
                severity: values.severity,
                channels: values.channels || [],
                description: values.description || '',
                enabled: values.enabled !== false
            };
            setModalVisible(false);
            setLoading(true);
            if (editingRule) {
                payload.id = editingRule.id;
                payload.createdAt = editingRule.createdAt;
                const result = await remoteApi.updateBranchCompensateAlertRule(payload);
                if (result && result.status === 0) {
                    messageApi.success(t.BC_ALERT_UPDATE_SUCCESS || 'Rule updated');
                    await fetchRules();
                } else {
                    messageApi.error(result?.errMsg || t.BC_ALERT_UPDATE_FAILED || 'Failed to update rule');
                }
            } else {
                const result = await remoteApi.createBranchCompensateAlertRule(payload);
                if (result && result.status === 0) {
                    messageApi.success(t.BC_ALERT_CREATE_SUCCESS || 'Rule created');
                    await fetchRules();
                } else {
                    messageApi.error(result?.errMsg || t.BC_ALERT_CREATE_FAILED || 'Failed to create rule');
                }
            }
        } catch (err) {
            return;
        } finally {
            setLoading(false);
        }
    };

    const filteredRules = useMemo(() => {
        return rules.filter(rule => {
            const matchesSearch = !searchText ||
                (rule.name && rule.name.toLowerCase().includes(searchText.toLowerCase())) ||
                (rule.brokerName && rule.brokerName.toLowerCase().includes(searchText.toLowerCase())) ||
                (rule.clusterName && rule.clusterName.toLowerCase().includes(searchText.toLowerCase())) ||
                (rule.description && rule.description.toLowerCase().includes(searchText.toLowerCase()));
            const matchesSeverity = filterSeverity === 'all' || rule.severity === filterSeverity;
            const matchesStatus = filterStatus === 'all' ||
                (filterStatus === 'enabled' && rule.enabled) ||
                (filterStatus === 'disabled' && !rule.enabled);
            return matchesSearch && matchesSeverity && matchesStatus;
        });
    }, [rules, searchText, filterSeverity, filterStatus]);

    const stats = useMemo(() => {
        const total = rules.length;
        const enabled = rules.filter(r => r.enabled).length;
        const critical = rules.filter(r => r.severity === 'critical').length;
        const warning = rules.filter(r => r.severity === 'warning').length;
        return {total, enabled, disabled: total - enabled, critical, warning};
    }, [rules]);

    const formatLagThreshold = (value, unit) => {
        return `${value} ${unit}`;
    };

    const columns = [
        {
            title: '#',
            dataIndex: 'index',
            width: 50,
            render: (text) => <span style={{color: '#999'}}>{text}</span>
        },
        {
            title: t.BC_ALERT_NAME || 'Rule Name',
            dataIndex: 'name',
            width: 220,
            render: (text, record) => (
                <span style={{fontWeight: 500, opacity: record.enabled ? 1 : 0.5}}>
                    {text}
                </span>
            )
        },
        {
            title: t.BC_ALERT_BROKER_NAME || 'Broker',
            dataIndex: 'brokerName',
            width: 130,
            render: (text) => (
                <Tag color="purple">{text}</Tag>
            )
        },
        {
            title: t.BC_ALERT_CLUSTER_NAME || 'Cluster',
            dataIndex: 'clusterName',
            width: 130,
            render: (text) => (
                <Tag color="blue">{text}</Tag>
            )
        },
        {
            title: t.BC_ALERT_LAG_THRESHOLD || 'Lag Threshold',
            dataIndex: 'lagThreshold',
            width: 140,
            render: (text, record) => (
                <Tag color="volcano">{formatLagThreshold(record.lagThreshold, record.lagThresholdUnit)}</Tag>
            )
        },
        {
            title: t.BC_ALERT_DURATION || 'Duration',
            dataIndex: 'duration',
            width: 100,
            render: (text) => <Tag>{text}</Tag>
        },
        {
            title: t.BC_ALERT_SEVERITY || 'Severity',
            dataIndex: 'severity',
            width: 100,
            render: (text) => (
                <Tag color={SEVERITY_COLORS[text] || 'default'}>
                    {text ? text.toUpperCase() : ''}
                </Tag>
            )
        },
        {
            title: t.BC_ALERT_CHANNELS || 'Channels',
            dataIndex: 'channels',
            width: 150,
            render: (channels) => (
                <Space size={4} wrap>
                    {(channels || []).map(ch => (
                        <Tag key={ch} color="cyan" style={{fontSize: 11}}>{ch}</Tag>
                    ))}
                </Space>
            )
        },
        {
            title: t.BC_ALERT_STATUS || 'Status',
            dataIndex: 'enabled',
            width: 80,
            render: (enabled, record) => (
                <Switch
                    size="small"
                    checked={enabled}
                    onChange={() => handleToggleRule(record)}
                />
            )
        },
        {
            title: t.BC_ALERT_UPDATED_AT || 'Updated',
            dataIndex: 'updatedAt',
            width: 160,
            render: (text) => text ? <span style={{fontSize: 12, color: '#888'}}>{text}</span> : '-'
        },
        {
            title: t.BC_ALERT_ACTIONS || 'Actions',
            width: 120,
            render: (_, record) => (
                <Space size="small">
                    <Tooltip title={t.BC_ALERT_EDIT || 'Edit'}>
                        <Button
                            type="text"
                            size="small"
                            icon={<EditOutlined/>}
                            onClick={() => handleEditRule(record)}
                        />
                    </Tooltip>
                    <Popconfirm
                        title={t.BC_ALERT_DELETE_CONFIRM || 'Are you sure to delete this rule?'}
                        onConfirm={() => handleDeleteRule(record.id)}
                        okText={t.YES || 'Yes'}
                        cancelText={t.NO || 'No'}
                    >
                        <Tooltip title={t.BC_ALERT_DELETE || 'Delete'}>
                            <Button
                                type="text"
                                size="small"
                                danger
                                icon={<DeleteOutlined/>}
                            />
                        </Tooltip>
                    </Popconfirm>
                </Space>
            )
        }
    ];

    return (
        <div style={{padding: '0 0 24px'}}>
            {msgContextHolder}
            <Card size="small" style={{marginBottom: 16}}>
                <Row gutter={16}>
                    <Col span={6}>
                        <Statistic
                            title={t.BC_ALERT_TOTAL || 'Total Rules'}
                            value={stats.total}
                            prefix={<SyncOutlined/>}
                        />
                    </Col>
                    <Col span={6}>
                        <Statistic
                            title={t.BC_ALERT_ENABLED || 'Enabled'}
                            value={stats.enabled}
                            valueStyle={{color: '#52c41a'}}
                        />
                    </Col>
                    <Col span={6}>
                        <Statistic
                            title={t.BC_ALERT_CRITICAL || 'Critical'}
                            value={stats.critical}
                            valueStyle={{color: '#cf1322'}}
                        />
                    </Col>
                    <Col span={6}>
                        <Statistic
                            title={t.BC_ALERT_WARNING_COUNT || 'Warning'}
                            value={stats.warning}
                            valueStyle={{color: '#fa8c16'}}
                        />
                    </Col>
                </Row>
            </Card>

            <Card
                size="small"
                title={
                    <Space>
                        <SyncOutlined/>
                        <span>{t.BC_ALERT_MANAGEMENT || 'Branch Compensate Alert Rules'}</span>
                        <Badge count={filteredRules.length} style={{backgroundColor: '#1890ff'}}/>
                    </Space>
                }
                extra={
                    <Space>
                        <Button
                            icon={<ReloadOutlined/>}
                            onClick={fetchRules}
                            loading={loading}
                            size="small"
                        >
                            {t.BC_ALERT_REFRESH || 'Refresh'}
                        </Button>
                        <Button
                            type="primary"
                            icon={<PlusOutlined/>}
                            onClick={handleAddRule}
                            size="small"
                        >
                            {t.BC_ALERT_ADD || 'Add Rule'}
                        </Button>
                    </Space>
                }
            >
                <div style={{marginBottom: 16, display: 'flex', gap: 12, flexWrap: 'wrap'}}>
                    <Input
                        placeholder={t.BC_ALERT_SEARCH_PLACEHOLDER || 'Search rule name, broker, cluster...'}
                        prefix={<SearchOutlined/>}
                        value={searchText}
                        onChange={e => setSearchText(e.target.value)}
                        style={{width: 300}}
                        allowClear
                    />
                    <Select
                        value={filterSeverity}
                        onChange={setFilterSeverity}
                        style={{width: 130}}
                    >
                        <Option value="all">{t.BC_ALERT_ALL_SEVERITY || 'All Severity'}</Option>
                        <Option value="critical">Critical</Option>
                        <Option value="warning">Warning</Option>
                        <Option value="info">Info</Option>
                    </Select>
                    <Select
                        value={filterStatus}
                        onChange={setFilterStatus}
                        style={{width: 130}}
                    >
                        <Option value="all">{t.BC_ALERT_ALL_STATUS || 'All Status'}</Option>
                        <Option value="enabled">{t.BC_ALERT_ENABLED || 'Enabled'}</Option>
                        <Option value="disabled">{t.BC_ALERT_DISABLED || 'Disabled'}</Option>
                    </Select>
                </div>

                <Table
                    columns={columns}
                    dataSource={filteredRules}
                    loading={loading}
                    rowKey="key"
                    size="small"
                    pagination={{
                        pageSize: 10,
                        showSizeChanger: true,
                        showTotal: (total) => `${t.BC_ALERT_TOTAL || 'Total'} ${total} ${t.BC_ALERT_RULES || 'rules'}`
                    }}
                    scroll={{x: 1300}}
                />
            </Card>

            <Modal
                title={editingRule ? (t.BC_ALERT_EDIT_RULE || 'Edit Branch Compensate Alert Rule') : (t.BC_ALERT_ADD_RULE || 'Add Branch Compensate Alert Rule')}
                open={modalVisible}
                onOk={handleModalOk}
                onCancel={() => {
                    setModalVisible(false);
                    form.resetFields();
                }}
                width={720}
                destroyOnClose
            >
                <Form
                    form={form}
                    layout="vertical"
                    style={{marginTop: 16}}
                >
                    <Row gutter={16}>
                        <Col span={12}>
                            <Form.Item
                                name="name"
                                label={t.BC_ALERT_NAME || 'Rule Name'}
                                rules={[{required: true, message: t.BC_ALERT_NAME_REQUIRED || 'Rule name is required'}]}
                            >
                                <Input placeholder="e.g. SlaveLagAlert"/>
                            </Form.Item>
                        </Col>
                        <Col span={12}>
                            <Form.Item
                                name="severity"
                                label={t.BC_ALERT_SEVERITY || 'Severity'}
                                rules={[{required: true}]}
                            >
                                <Select>
                                    <Option value="critical">Critical</Option>
                                    <Option value="warning">Warning</Option>
                                    <Option value="info">Info</Option>
                                </Select>
                            </Form.Item>
                        </Col>
                    </Row>
                    <Row gutter={16}>
                        <Col span={12}>
                            <Form.Item
                                name="brokerName"
                                label={t.BC_ALERT_BROKER_NAME || 'Broker Name'}
                                rules={[{required: true, message: t.BC_ALERT_BROKER_NAME_REQUIRED || 'Broker name is required'}]}
                            >
                                <Input placeholder="e.g. broker-a, or * for all"/>
                            </Form.Item>
                        </Col>
                        <Col span={12}>
                            <Form.Item
                                name="clusterName"
                                label={t.BC_ALERT_CLUSTER_NAME || 'Cluster Name'}
                                rules={[{required: true, message: t.BC_ALERT_CLUSTER_NAME_REQUIRED || 'Cluster name is required'}]}
                            >
                                <Input placeholder="e.g. DefaultCluster, or * for all"/>
                            </Form.Item>
                        </Col>
                    </Row>
                    <Row gutter={16}>
                        <Col span={8}>
                            <Form.Item
                                name="lagThreshold"
                                label={t.BC_ALERT_LAG_THRESHOLD || 'Lag Threshold'}
                                rules={[{required: true, message: t.BC_ALERT_LAG_THRESHOLD_REQUIRED || 'Lag threshold is required'}]}
                            >
                                <InputNumber min={1} style={{width: '100%'}} placeholder="e.g. 1"/>
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item
                                name="lagThresholdUnit"
                                label={t.BC_ALERT_LAG_UNIT || 'Unit'}
                                rules={[{required: true}]}
                            >
                                <Select>
                                    {LAG_UNIT_OPTIONS.map(u => (
                                        <Option key={u} value={u}>{u}</Option>
                                    ))}
                                </Select>
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item
                                name="duration"
                                label={t.BC_ALERT_DURATION || 'Duration'}
                                rules={[{required: true, message: t.BC_ALERT_DURATION_REQUIRED || 'Duration is required'}]}
                            >
                                <Input placeholder="e.g. 5m, 10m"/>
                            </Form.Item>
                        </Col>
                    </Row>
                    <Form.Item
                        name="channels"
                        label={t.BC_ALERT_CHANNELS || 'Notification Channels'}
                    >
                        <Select mode="multiple" placeholder={t.BC_ALERT_CHANNELS_PLACEHOLDER || 'Select channels'}>
                            <Option value="email">Email</Option>
                            <Option value="webhook">Webhook</Option>
                            <Option value="dingtalk">DingTalk</Option>
                            <Option value="wechat">WeChat</Option>
                            <Option value="sms">SMS</Option>
                        </Select>
                    </Form.Item>
                    <Form.Item
                        name="description"
                        label={t.BC_ALERT_DESCRIPTION || 'Description'}
                    >
                        <TextArea rows={2} placeholder={t.BC_ALERT_DESCRIPTION_PLACEHOLDER || 'Detailed description (optional)'}/>
                    </Form.Item>
                    <Form.Item
                        name="enabled"
                        label={t.BC_ALERT_ENABLED || 'Enabled'}
                        valuePropName="checked"
                    >
                        <Switch/>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default BranchCompensateAlert;