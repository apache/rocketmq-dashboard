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
    DownloadOutlined,
    ReloadOutlined,
    AlertOutlined,
    SearchOutlined
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

const TEAM_COLORS = {
    broker: 'purple',
    topic: 'cyan',
    consumer: 'green',
    client: 'geekblue',
    proxy: 'magenta',
    security: 'volcano',
    reliability: 'gold'
};

const AlertManagement = () => {
    const [alertRules, setAlertRules] = useState([]);
    const [loading, setLoading] = useState(false);
    const [modalVisible, setModalVisible] = useState(false);
    const [editingRule, setEditingRule] = useState(null);
    const [yamlSource, setYamlSource] = useState('');
    const [searchText, setSearchText] = useState('');
    const [filterGroup, setFilterGroup] = useState('all');
    const [filterSeverity, setFilterSeverity] = useState('all');
    const [filterStatus, setFilterStatus] = useState('all');
    const [disabledRules, setDisabledRules] = useState(() => {
        try {
            const saved = localStorage.getItem('alertDisabledRules');
            return saved ? JSON.parse(saved) : {};
        } catch {
            return {};
        }
    });
    const [form] = Form.useForm();
    const [messageApi, msgContextHolder] = message.useMessage();
    const {t} = useLanguage();

    useEffect(() => {
        localStorage.setItem('alertDisabledRules', JSON.stringify(disabledRules));
    }, [disabledRules]);

    const fetchAlertRules = async () => {
        setLoading(true);
        try {
            const result = await new Promise((resolve) => {
                remoteApi.queryAlertRules(resolve);
            });
            if (result && result.status === 0 && result.data) {
                const yamlStr = result.data.rules || '';
                setYamlSource(yamlStr);
                const parsed = parseYamlRules(yamlStr);
                setAlertRules(parsed);
            } else {
                messageApi.error(t.ALERT_FETCH_FAILED || 'Failed to fetch alert rules');
            }
        } catch (error) {
            console.error('Error fetching alert rules:', error);
            messageApi.error(t.ALERT_FETCH_FAILED || 'Failed to fetch alert rules');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchAlertRules();
    }, []);

    const parseYamlRules = (yamlStr) => {
        const rules = [];
        if (!yamlStr) return rules;

        const groupBlocks = yamlStr.split(/\n(?=\s*- name:)/);
        let ruleIndex = 0;

        for (const block of groupBlocks) {
            const groupNameMatch = block.match(/- name:\s*(.+)/);
            if (!groupNameMatch) continue;
            const groupName = groupNameMatch[1].trim();

            const ruleBlocks = block.split(/\n\s*#\s*Rule\s+\d+:/);
            for (let i = 1; i < ruleBlocks.length; i++) {
                const ruleBlock = ruleBlocks[i];
                const alertMatch = ruleBlock.match(/alert:\s*(.+)/);
                const exprMatch = ruleBlock.match(/expr:\s*(.+)/);
                const forMatch = ruleBlock.match(/for:\s*(.+)/);
                const severityMatch = ruleBlock.match(/severity:\s*(.+)/);
                const teamMatch = ruleBlock.match(/team:\s*(.+)/);
                const summaryMatch = ruleBlock.match(/summary:\s*"(.+)"/);
                const descMatch = ruleBlock.match(/description:\s*"(.+)"/);

                if (alertMatch) {
                    ruleIndex++;
                    rules.push({
                        key: alertMatch[1].trim(),
                        index: ruleIndex,
                        alert: alertMatch[1].trim(),
                        group: groupName,
                        expr: exprMatch ? exprMatch[1].trim() : '',
                        for: forMatch ? forMatch[1].trim() : '',
                        severity: severityMatch ? severityMatch[1].trim() : 'warning',
                        team: teamMatch ? teamMatch[1].trim() : '',
                        summary: summaryMatch ? summaryMatch[1].trim() : '',
                        description: descMatch ? descMatch[1].trim() : '',
                        enabled: !disabledRules[alertMatch[1].trim()]
                    });
                }
            }
        }
        return rules;
    };

    const handleToggleRule = (ruleKey) => {
        setDisabledRules(prev => {
            const updated = {...prev, [ruleKey]: !prev[ruleKey]};
            return updated;
        });
        setAlertRules(prev => prev.map(rule =>
            rule.key === ruleKey ? {...rule, enabled: !rule.enabled} : rule
        ));
    };

    const handleAddRule = () => {
        setEditingRule(null);
        form.resetFields();
        form.setFieldsValue({
            severity: 'warning',
            for: '5m',
            team: 'broker',
            enabled: true
        });
        setModalVisible(true);
    };

    const handleEditRule = (rule) => {
        setEditingRule(rule);
        form.setFieldsValue({
            alert: rule.alert,
            group: rule.group,
            expr: rule.expr,
            for: rule.for,
            severity: rule.severity,
            team: rule.team,
            summary: rule.summary,
            description: rule.description,
            enabled: rule.enabled
        });
        setModalVisible(true);
    };

    const handleDeleteRule = (ruleKey) => {
        setAlertRules(prev => prev.filter(rule => rule.key !== ruleKey));
        setDisabledRules(prev => {
            const updated = {...prev};
            delete updated[ruleKey];
            return updated;
        });
        messageApi.success(t.ALERT_DELETE_SUCCESS || 'Alert rule deleted');
    };

    const handleModalOk = async () => {
        try {
            const values = await form.validateFields();
            if (editingRule) {
                setAlertRules(prev => prev.map(rule =>
                    rule.key === editingRule.key
                        ? {
                            ...rule,
                            alert: values.alert,
                            group: values.group,
                            expr: values.expr,
                            for: values.for,
                            severity: values.severity,
                            team: values.team,
                            summary: values.summary || '',
                            description: values.description || '',
                            enabled: values.enabled !== false
                        }
                        : rule
                ));
                messageApi.success(t.ALERT_UPDATE_SUCCESS || 'Alert rule updated');
            } else {
                const newRule = {
                    key: values.alert,
                    index: alertRules.length + 1,
                    alert: values.alert,
                    group: values.group,
                    expr: values.expr,
                    for: values.for,
                    severity: values.severity,
                    team: values.team,
                    summary: values.summary || '',
                    description: values.description || '',
                    enabled: values.enabled !== false
                };
                setAlertRules(prev => [...prev, newRule]);
                messageApi.success(t.ALERT_CREATE_SUCCESS || 'Alert rule created');
            }
            setModalVisible(false);
            form.resetFields();
        } catch (err) {
            // validation failed
        }
    };

    const handleExportYaml = () => {
        const enabledRules = alertRules.filter(r => r.enabled);
        const groups = {};

        for (const rule of enabledRules) {
            if (!groups[rule.group]) {
                groups[rule.group] = [];
            }
            groups[rule.group].push(rule);
        }

        let yaml = '# ==============================================================\n';
        yaml += '# RocketMQ 5.x Monitoring — Alert Rules (Exported from Dashboard)\n';
        yaml += '# Compatible with Prometheus / VictoriaMetrics / Thanos alerting\n';
        yaml += '# ==============================================================\n\n';
        yaml += 'groups:\n';

        for (const [groupName, groupRules] of Object.entries(groups)) {
            yaml += `  - name: ${groupName}\n`;
            yaml += '    rules:\n';
            for (const rule of groupRules) {
                yaml += `      - alert: ${rule.alert}\n`;
                yaml += `        expr: ${rule.expr}\n`;
                yaml += `        for: ${rule.for}\n`;
                yaml += '        labels:\n';
                yaml += `          severity: ${rule.severity}\n`;
                yaml += `          team: ${rule.team}\n`;
                yaml += '        annotations:\n';
                yaml += `          summary: "${rule.summary}"\n`;
                if (rule.description) {
                    yaml += `          description: "${rule.description}"\n`;
                }
                yaml += '\n';
            }
        }

        const blob = new Blob([yaml], {type: 'text/yaml'});
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'rocketmq-alert-rules.yaml';
        a.click();
        URL.revokeObjectURL(url);
        messageApi.success(t.ALERT_EXPORT_SUCCESS || 'Alert rules exported');
    };

    const filteredRules = useMemo(() => {
        return alertRules.filter(rule => {
            const matchesSearch = !searchText ||
                rule.alert.toLowerCase().includes(searchText.toLowerCase()) ||
                rule.expr.toLowerCase().includes(searchText.toLowerCase()) ||
                rule.summary.toLowerCase().includes(searchText.toLowerCase());
            const matchesGroup = filterGroup === 'all' || rule.group === filterGroup;
            const matchesSeverity = filterSeverity === 'all' || rule.severity === filterSeverity;
            const matchesStatus = filterStatus === 'all' ||
                (filterStatus === 'enabled' && rule.enabled) ||
                (filterStatus === 'disabled' && !rule.enabled);
            return matchesSearch && matchesGroup && matchesSeverity && matchesStatus;
        });
    }, [alertRules, searchText, filterGroup, filterSeverity, filterStatus]);

    const groups = useMemo(() => {
        const set = new Set(alertRules.map(r => r.group));
        return Array.from(set);
    }, [alertRules]);

    const stats = useMemo(() => {
        const total = alertRules.length;
        const enabled = alertRules.filter(r => r.enabled).length;
        const critical = alertRules.filter(r => r.severity === 'critical').length;
        const warning = alertRules.filter(r => r.severity === 'warning').length;
        return {total, enabled, disabled: total - enabled, critical, warning};
    }, [alertRules]);

    const columns = [
        {
            title: '#',
            dataIndex: 'index',
            width: 50,
            render: (text) => <span style={{color: '#999'}}>{text}</span>
        },
        {
            title: t.ALERT_NAME || 'Alert Name',
            dataIndex: 'alert',
            width: 260,
            render: (text, record) => (
                <span style={{fontWeight: 500, opacity: record.enabled ? 1 : 0.5}}>
                    {text}
                </span>
            )
        },
        {
            title: t.ALERT_GROUP || 'Group',
            dataIndex: 'group',
            width: 200,
            render: (text) => (
                <Tag color="blue">{text.replace('rocketmq-', '').replace('.rules', '')}</Tag>
            )
        },
        {
            title: t.ALERT_SEVERITY || 'Severity',
            dataIndex: 'severity',
            width: 100,
            render: (text) => (
                <Tag color={SEVERITY_COLORS[text] || 'default'}>
                    {text.toUpperCase()}
                </Tag>
            )
        },
        {
            title: t.ALERT_TEAM || 'Team',
            dataIndex: 'team',
            width: 100,
            render: (text) => (
                <Tag color={TEAM_COLORS[text] || 'default'}>{text}</Tag>
            )
        },
        {
            title: t.ALERT_EXPR || 'Expression',
            dataIndex: 'expr',
            ellipsis: true,
            render: (text, record) => (
                <Tooltip title={text}>
                    <code style={{
                        fontSize: 12,
                        opacity: record.enabled ? 1 : 0.5,
                        background: '#f5f5f5',
                        padding: '2px 6px',
                        borderRadius: 4
                    }}>
                        {text}
                    </code>
                </Tooltip>
            )
        },
        {
            title: t.ALERT_FOR || 'For',
            dataIndex: 'for',
            width: 80,
            render: (text) => <Tag>{text}</Tag>
        },
        {
            title: t.ALERT_STATUS || 'Status',
            dataIndex: 'enabled',
            width: 80,
            render: (enabled, record) => (
                <Switch
                    size="small"
                    checked={enabled}
                    onChange={() => handleToggleRule(record.key)}
                />
            )
        },
        {
            title: t.ALERT_ACTIONS || 'Actions',
            width: 120,
            render: (_, record) => (
                <Space size="small">
                    <Tooltip title={t.ALERT_EDIT || 'Edit'}>
                        <Button
                            type="text"
                            size="small"
                            icon={<EditOutlined/>}
                            onClick={() => handleEditRule(record)}
                        />
                    </Tooltip>
                    <Popconfirm
                        title={t.ARE_YOU_SURE_TO_DELETE || 'Are you sure to delete?'}
                        onConfirm={() => handleDeleteRule(record.key)}
                        okText={t.YES || 'Yes'}
                        cancelText={t.NO || 'No'}
                    >
                        <Tooltip title={t.ALERT_DELETE || 'Delete'}>
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
                            title={t.ALERT_TOTAL || 'Total Rules'}
                            value={stats.total}
                            prefix={<AlertOutlined/>}
                        />
                    </Col>
                    <Col span={6}>
                        <Statistic
                            title={t.ALERT_ENABLED || 'Enabled'}
                            value={stats.enabled}
                            valueStyle={{color: '#52c41a'}}
                        />
                    </Col>
                    <Col span={6}>
                        <Statistic
                            title={t.ALERT_CRITICAL || 'Critical'}
                            value={stats.critical}
                            valueStyle={{color: '#cf1322'}}
                        />
                    </Col>
                    <Col span={6}>
                        <Statistic
                            title={t.ALERT_WARNING_COUNT || 'Warning'}
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
                        <AlertOutlined/>
                        <span>{t.ALERT_MANAGEMENT || 'Alert Management'}</span>
                        <Badge count={filteredRules.length} style={{backgroundColor: '#1890ff'}}/>
                    </Space>
                }
                extra={
                    <Space>
                        <Button
                            icon={<ReloadOutlined/>}
                            onClick={fetchAlertRules}
                            loading={loading}
                            size="small"
                        >
                            {t.ALERT_REFRESH || 'Refresh'}
                        </Button>
                        <Button
                            type="primary"
                            icon={<PlusOutlined/>}
                            onClick={handleAddRule}
                            size="small"
                        >
                            {t.ALERT_ADD || 'Add Rule'}
                        </Button>
                        <Button
                            icon={<DownloadOutlined/>}
                            onClick={handleExportYaml}
                            size="small"
                        >
                            {t.ALERT_EXPORT_YAML || 'Export YAML'}
                        </Button>
                    </Space>
                }
            >
                <div style={{marginBottom: 16, display: 'flex', gap: 12, flexWrap: 'wrap'}}>
                    <Input
                        placeholder={t.ALERT_SEARCH_PLACEHOLDER || 'Search alert name, expression...'}
                        prefix={<SearchOutlined/>}
                        value={searchText}
                        onChange={e => setSearchText(e.target.value)}
                        style={{width: 280}}
                        allowClear
                    />
                    <Select
                        value={filterGroup}
                        onChange={setFilterGroup}
                        style={{width: 180}}
                    >
                        <Option value="all">{t.ALERT_ALL_GROUPS || 'All Groups'}</Option>
                        {groups.map(g => (
                            <Option key={g} value={g}>{g.replace('rocketmq-', '').replace('.rules', '')}</Option>
                        ))}
                    </Select>
                    <Select
                        value={filterSeverity}
                        onChange={setFilterSeverity}
                        style={{width: 130}}
                    >
                        <Option value="all">{t.ALERT_ALL_SEVERITY || 'All Severity'}</Option>
                        <Option value="critical">Critical</Option>
                        <Option value="warning">Warning</Option>
                    </Select>
                    <Select
                        value={filterStatus}
                        onChange={setFilterStatus}
                        style={{width: 130}}
                    >
                        <Option value="all">{t.ALERT_ALL_STATUS || 'All Status'}</Option>
                        <Option value="enabled">{t.ALERT_ENABLED || 'Enabled'}</Option>
                        <Option value="disabled">{t.ALERT_DISABLED || 'Disabled'}</Option>
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
                        showTotal: (total) => `${t.ALERT_TOTAL || 'Total'} ${total} ${t.ALERT_RULES || 'rules'}`
                    }}
                    scroll={{x: 1200}}
                />
            </Card>

            <Modal
                title={editingRule ? (t.ALERT_EDIT_RULE || 'Edit Alert Rule') : (t.ALERT_ADD_RULE || 'Add Alert Rule')}
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
                                name="alert"
                                label={t.ALERT_NAME || 'Alert Name'}
                                rules={[{required: true, message: t.ALERT_NAME_REQUIRED || 'Alert name is required'}]}
                            >
                                <Input placeholder="e.g. RocketMQ_Broker_Down"/>
                            </Form.Item>
                        </Col>
                        <Col span={12}>
                            <Form.Item
                                name="group"
                                label={t.ALERT_GROUP || 'Group'}
                                rules={[{required: true, message: t.ALERT_GROUP_REQUIRED || 'Group is required'}]}
                            >
                                <Select placeholder="Select group">
                                    <Option value="rocketmq-broker.rules">broker</Option>
                                    <Option value="rocketmq-topic.rules">topic</Option>
                                    <Option value="rocketmq-consumer.rules">consumer</Option>
                                    <Option value="rocketmq-client.rules">client</Option>
                                    <Option value="rocketmq-proxy.rules">proxy</Option>
                                    <Option value="rocketmq-errors.rules">errors</Option>
                                    <Option value="rocketmq-broker-extended.rules">broker-extended</Option>
                                </Select>
                            </Form.Item>
                        </Col>
                    </Row>
                    <Form.Item
                        name="expr"
                        label={t.ALERT_EXPR || 'Expression (PromQL)'}
                        rules={[{required: true, message: t.ALERT_EXPR_REQUIRED || 'Expression is required'}]}
                    >
                        <TextArea rows={2} placeholder={'e.g. up{job=~"rocketmq.*broker.*"} == 0'}/>
                    </Form.Item>
                    <Row gutter={16}>
                        <Col span={8}>
                            <Form.Item
                                name="for"
                                label={t.ALERT_FOR || 'For Duration'}
                                rules={[{required: true, message: t.ALERT_FOR_REQUIRED || 'Duration is required'}]}
                            >
                                <Input placeholder="e.g. 5m"/>
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item
                                name="severity"
                                label={t.ALERT_SEVERITY || 'Severity'}
                                rules={[{required: true}]}
                            >
                                <Select>
                                    <Option value="critical">Critical</Option>
                                    <Option value="warning">Warning</Option>
                                </Select>
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item
                                name="team"
                                label={t.ALERT_TEAM || 'Team'}
                                rules={[{required: true}]}
                            >
                                <Select>
                                    <Option value="broker">broker</Option>
                                    <Option value="topic">topic</Option>
                                    <Option value="consumer">consumer</Option>
                                    <Option value="client">client</Option>
                                    <Option value="proxy">proxy</Option>
                                    <Option value="security">security</Option>
                                    <Option value="reliability">reliability</Option>
                                </Select>
                            </Form.Item>
                        </Col>
                    </Row>
                    <Form.Item
                        name="summary"
                        label={t.ALERT_SUMMARY || 'Summary'}
                        rules={[{required: true, message: t.ALERT_SUMMARY_REQUIRED || 'Summary is required'}]}
                    >
                        <Input placeholder="Brief description of the alert"/>
                    </Form.Item>
                    <Form.Item
                        name="description"
                        label={t.ALERT_DESCRIPTION || 'Description'}
                    >
                        <TextArea rows={2} placeholder="Detailed description (optional)"/>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default AlertManagement;