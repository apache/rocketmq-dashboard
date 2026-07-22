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

import React, { useMemo, useRef, useState } from 'react';
import {
  App,
  Badge,
  Button,
  Card,
  Col,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Switch,
  Table,
  Tag,
  Tooltip,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowClockwise,
  DownloadSimple,
  MagnifyingGlass,
  Pencil,
  Plus,
  Trash,
  Warning,
} from '@phosphor-icons/react';
import { useLang } from '../../i18n/LangContext';
import { queryAlertRules } from '../../api/alertManagement';

const { TextArea } = Input;

// ─── Types ────────────────────────────────────────────────────────
interface AlertRule {
  key: string;
  index: number;
  alert: string;
  group: string;
  expr: string;
  for: string;
  severity: string;
  team: string;
  summary: string;
  description: string;
  enabled: boolean;
}

// ─── Constants ────────────────────────────────────────────────────
const SEVERITY_COLORS: Record<string, string> = {
  critical: 'red',
  warning: 'orange',
  info: 'blue',
};

const TEAM_COLORS: Record<string, string> = {
  broker: 'purple',
  topic: 'cyan',
  consumer: 'green',
  client: 'geekblue',
  proxy: 'magenta',
  security: 'volcano',
  reliability: 'gold',
};

const GROUP_OPTIONS = [
  'rocketmq-broker.rules',
  'rocketmq-topic.rules',
  'rocketmq-consumer.rules',
  'rocketmq-client.rules',
  'rocketmq-proxy.rules',
  'rocketmq-errors.rules',
  'rocketmq-broker-extended.rules',
];

const TEAM_OPTIONS = ['broker', 'topic', 'consumer', 'client', 'proxy', 'security', 'reliability'];

// ─── YAML Parser ──────────────────────────────────────────────────
function parseYamlRules(yamlStr: string, disabledRules: Record<string, boolean>): AlertRule[] {
  const rules: AlertRule[] = [];
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
        const alertName = alertMatch[1].trim();
        rules.push({
          key: alertName,
          index: ruleIndex,
          alert: alertName,
          group: groupName,
          expr: exprMatch ? exprMatch[1].trim() : '',
          for: forMatch ? forMatch[1].trim() : '',
          severity: severityMatch ? severityMatch[1].trim() : 'warning',
          team: teamMatch ? teamMatch[1].trim() : '',
          summary: summaryMatch ? summaryMatch[1].trim() : '',
          description: descMatch ? descMatch[1].trim() : '',
          enabled: !disabledRules[alertName],
        });
      }
    }
  }
  return rules;
}

// ─── Component ────────────────────────────────────────────────────
const AlertManagementPage: React.FC = () => {
  const { t } = useLang();
  const { message } = App.useApp();
  const [form] = Form.useForm();

  const [alertRules, setAlertRules] = useState<AlertRule[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingRule, setEditingRule] = useState<AlertRule | null>(null);
  const [searchText, setSearchText] = useState('');
  const [filterGroup, setFilterGroup] = useState('all');
  const [filterSeverity, setFilterSeverity] = useState('all');
  const [filterStatus, setFilterStatus] = useState('all');
  const [disabledRules, setDisabledRules] = useState<Record<string, boolean>>(() => {
    try {
      const saved = localStorage.getItem('alertDisabledRules');
      return saved ? JSON.parse(saved) : {};
    } catch {
      return {};
    }
  });

  // One-time initialization (ESLint-compliant)
  const initialized = useRef<boolean | null>(null);
  if (initialized.current == null) {
    initialized.current = true;
    const loadRules = async () => {
      setLoading(true);
      try {
        const data = await queryAlertRules();
        const yamlStr = data.rules || '';
        setAlertRules(parseYamlRules(yamlStr, disabledRules));
      } catch {
        message.error(t('alertMgmt.fetchFailed'));
      } finally {
        setLoading(false);
      }
    };
    loadRules();
  }

  const fetchAlertRules = async () => {
    setLoading(true);
    try {
      const data = await queryAlertRules();
      const yamlStr = data.rules || '';
      setAlertRules(parseYamlRules(yamlStr, disabledRules));
    } catch {
      message.error(t('alertMgmt.fetchFailed'));
    } finally {
      setLoading(false);
    }
  };

  const handleToggleRule = (ruleKey: string) => {
    const updated = { ...disabledRules, [ruleKey]: !disabledRules[ruleKey] };
    setDisabledRules(updated);
    localStorage.setItem('alertDisabledRules', JSON.stringify(updated));
    setAlertRules((prev) =>
      prev.map((rule) => (rule.key === ruleKey ? { ...rule, enabled: !rule.enabled } : rule)),
    );
  };

  const handleAddRule = () => {
    setEditingRule(null);
    form.resetFields();
    form.setFieldsValue({ severity: 'warning', for: '5m', team: 'broker', enabled: true });
    setModalVisible(true);
  };

  const handleEditRule = (rule: AlertRule) => {
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
      enabled: rule.enabled,
    });
    setModalVisible(true);
  };

  const handleDeleteRule = (ruleKey: string) => {
    setAlertRules((prev) => prev.filter((rule) => rule.key !== ruleKey));
    const updated = { ...disabledRules };
    delete updated[ruleKey];
    setDisabledRules(updated);
    localStorage.setItem('alertDisabledRules', JSON.stringify(updated));
    message.success(t('alertMgmt.deleteSuccess'));
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      if (editingRule) {
        setAlertRules((prev) =>
          prev.map((rule) =>
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
                  enabled: values.enabled !== false,
                }
              : rule,
          ),
        );
        message.success(t('alertMgmt.updateSuccess'));
      } else {
        const newRule: AlertRule = {
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
          enabled: values.enabled !== false,
        };
        setAlertRules((prev) => [...prev, newRule]);
        message.success(t('alertMgmt.createSuccess'));
      }
      setModalVisible(false);
      form.resetFields();
    } catch {
      // validation failed
    }
  };

  const handleExportYaml = () => {
    const enabledRules = alertRules.filter((r) => r.enabled);
    const groups: Record<string, AlertRule[]> = {};
    for (const rule of enabledRules) {
      if (!groups[rule.group]) groups[rule.group] = [];
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

    const blob = new Blob([yaml], { type: 'text/yaml' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'rocketmq-alert-rules.yaml';
    a.click();
    URL.revokeObjectURL(url);
    message.success(t('alertMgmt.exportSuccess'));
  };

  // ─── Derived data ─────────────────────────────────────────────
  const filteredRules = useMemo(() => {
    return alertRules.filter((rule) => {
      const matchesSearch =
        !searchText ||
        rule.alert.toLowerCase().includes(searchText.toLowerCase()) ||
        rule.expr.toLowerCase().includes(searchText.toLowerCase()) ||
        rule.summary.toLowerCase().includes(searchText.toLowerCase());
      const matchesGroup = filterGroup === 'all' || rule.group === filterGroup;
      const matchesSeverity = filterSeverity === 'all' || rule.severity === filterSeverity;
      const matchesStatus =
        filterStatus === 'all' ||
        (filterStatus === 'enabled' && rule.enabled) ||
        (filterStatus === 'disabled' && !rule.enabled);
      return matchesSearch && matchesGroup && matchesSeverity && matchesStatus;
    });
  }, [alertRules, searchText, filterGroup, filterSeverity, filterStatus]);

  const groups = useMemo(() => Array.from(new Set(alertRules.map((r) => r.group))), [alertRules]);

  const stats = useMemo(() => {
    const total = alertRules.length;
    const enabled = alertRules.filter((r) => r.enabled).length;
    const critical = alertRules.filter((r) => r.severity === 'critical').length;
    const warning = alertRules.filter((r) => r.severity === 'warning').length;
    return { total, enabled, disabled: total - enabled, critical, warning };
  }, [alertRules]);

  // ─── Table columns ─────────────────────────────────────────────
  const columns: ColumnsType<AlertRule> = [
    {
      title: '#',
      dataIndex: 'index',
      width: 50,
      render: (text: number) => <span style={{ color: '#999' }}>{text}</span>,
    },
    {
      title: t('alertMgmt.alertName'),
      dataIndex: 'alert',
      width: 260,
      render: (text: string, record: AlertRule) => (
        <span style={{ fontWeight: 500, opacity: record.enabled ? 1 : 0.5 }}>{text}</span>
      ),
    },
    {
      title: t('alertMgmt.group'),
      dataIndex: 'group',
      width: 200,
      render: (text: string) => (
        <Tag color="blue">{text.replace('rocketmq-', '').replace('.rules', '')}</Tag>
      ),
    },
    {
      title: t('alertMgmt.severity'),
      dataIndex: 'severity',
      width: 100,
      render: (text: string) => (
        <Tag color={SEVERITY_COLORS[text] || 'default'}>{text.toUpperCase()}</Tag>
      ),
    },
    {
      title: t('alertMgmt.team'),
      dataIndex: 'team',
      width: 100,
      render: (text: string) => <Tag color={TEAM_COLORS[text] || 'default'}>{text}</Tag>,
    },
    {
      title: t('alertMgmt.expression'),
      dataIndex: 'expr',
      ellipsis: true,
      render: (text: string, record: AlertRule) => (
        <Tooltip title={text}>
          <code
            style={{
              fontSize: 12,
              opacity: record.enabled ? 1 : 0.5,
              background: '#f5f5f5',
              padding: '2px 6px',
              borderRadius: 4,
            }}
          >
            {text}
          </code>
        </Tooltip>
      ),
    },
    {
      title: t('alertMgmt.forDuration'),
      dataIndex: 'for',
      width: 80,
      render: (text: string) => <Tag>{text}</Tag>,
    },
    {
      title: t('common.status'),
      dataIndex: 'enabled',
      width: 80,
      render: (enabled: boolean, record: AlertRule) => (
        <Switch size="small" checked={enabled} onChange={() => handleToggleRule(record.key)} />
      ),
    },
    {
      title: t('common.actions'),
      width: 120,
      render: (_: unknown, record: AlertRule) => (
        <Space size="small">
          <Tooltip title={t('common.edit')}>
            <Button
              type="text"
              size="small"
              icon={<Pencil size={16} />}
              onClick={() => handleEditRule(record)}
            />
          </Tooltip>
          <Popconfirm
            title={t('common.areYouSureToDelete')}
            onConfirm={() => handleDeleteRule(record.key)}
            okText={t('common.confirm')}
            cancelText={t('common.cancel')}
          >
            <Tooltip title={t('common.delete')}>
              <Button type="text" size="small" danger icon={<Trash size={16} />} />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: '0 0 24px' }}>
      {/* Statistics */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={6}>
            <Statistic
              title={t('alertMgmt.totalRules')}
              value={stats.total}
              prefix={<Warning size={18} />}
            />
          </Col>
          <Col span={6}>
            <Statistic
              title={t('alertMgmt.enabled')}
              value={stats.enabled}
              valueStyle={{ color: '#52c41a' }}
            />
          </Col>
          <Col span={6}>
            <Statistic
              title={t('alertMgmt.critical')}
              value={stats.critical}
              valueStyle={{ color: '#cf1322' }}
            />
          </Col>
          <Col span={6}>
            <Statistic
              title={t('alertMgmt.warningCount')}
              value={stats.warning}
              valueStyle={{ color: '#fa8c16' }}
            />
          </Col>
        </Row>
      </Card>

      {/* Main Card */}
      <Card
        size="small"
        title={
          <Space>
            <Warning size={18} />
            <span>{t('alertMgmt.title')}</span>
            <Badge count={filteredRules.length} style={{ backgroundColor: '#1890ff' }} />
          </Space>
        }
        extra={
          <Space>
            <Button
              icon={<ArrowClockwise size={16} />}
              onClick={fetchAlertRules}
              loading={loading}
              size="small"
            >
              {t('common.refresh')}
            </Button>
            <Button type="primary" icon={<Plus size={16} />} onClick={handleAddRule} size="small">
              {t('alertMgmt.addRule')}
            </Button>
            <Button icon={<DownloadSimple size={16} />} onClick={handleExportYaml} size="small">
              {t('alertMgmt.exportYaml')}
            </Button>
          </Space>
        }
      >
        {/* Filters */}
        <div style={{ marginBottom: 16, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <Input
            placeholder={t('alertMgmt.searchPlaceholder')}
            prefix={<MagnifyingGlass size={16} />}
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            style={{ width: 280 }}
            allowClear
          />
          <Select
            value={filterGroup}
            onChange={setFilterGroup}
            style={{ width: 180 }}
            options={[
              { value: 'all', label: t('alertMgmt.allGroups') },
              ...groups.map((g) => ({
                value: g,
                label: g.replace('rocketmq-', '').replace('.rules', ''),
              })),
            ]}
          />
          <Select
            value={filterSeverity}
            onChange={setFilterSeverity}
            style={{ width: 130 }}
            options={[
              { value: 'all', label: t('alertMgmt.allSeverity') },
              { value: 'critical', label: 'Critical' },
              { value: 'warning', label: 'Warning' },
            ]}
          />
          <Select
            value={filterStatus}
            onChange={setFilterStatus}
            style={{ width: 130 }}
            options={[
              { value: 'all', label: t('alertMgmt.allStatus') },
              { value: 'enabled', label: t('alertMgmt.enabled') },
              { value: 'disabled', label: t('alertMgmt.disabled') },
            ]}
          />
        </div>

        {/* Table */}
        <Table
          columns={columns}
          dataSource={filteredRules}
          loading={loading}
          rowKey="key"
          size="small"
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showTotal: (total) => `${t('common.total')} ${total}`,
          }}
          scroll={{ x: 1200 }}
        />
      </Card>

      {/* Add/Edit Modal */}
      <Modal
        title={editingRule ? t('alertMgmt.editRule') : t('alertMgmt.addRule')}
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={() => {
          setModalVisible(false);
          form.resetFields();
        }}
        width={720}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="alert"
                label={t('alertMgmt.alertName')}
                rules={[{ required: true, message: t('alertMgmt.alertNameRequired') }]}
              >
                <Input placeholder="e.g. RocketMQ_Broker_Down" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="group"
                label={t('alertMgmt.group')}
                rules={[{ required: true, message: t('alertMgmt.groupRequired') }]}
              >
                <Select
                  placeholder={t('common.pleaseSelect')}
                  options={GROUP_OPTIONS.map((g) => ({
                    value: g,
                    label: g.replace('rocketmq-', '').replace('.rules', ''),
                  }))}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="expr"
            label={t('alertMgmt.expression')}
            rules={[{ required: true, message: t('alertMgmt.expressionRequired') }]}
          >
            <TextArea rows={2} placeholder={'e.g. up{job=~"rocketmq.*broker.*"} == 0'} />
          </Form.Item>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="for"
                label={t('alertMgmt.forDuration')}
                rules={[{ required: true, message: t('alertMgmt.forDurationRequired') }]}
              >
                <Input placeholder="e.g. 5m" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="severity"
                label={t('alertMgmt.severity')}
                rules={[{ required: true }]}
              >
                <Select
                  options={[
                    { value: 'critical', label: 'Critical' },
                    { value: 'warning', label: 'Warning' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="team" label={t('alertMgmt.team')} rules={[{ required: true }]}>
                <Select options={TEAM_OPTIONS.map((t) => ({ value: t, label: t }))} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="summary"
            label={t('alertMgmt.summary')}
            rules={[{ required: true, message: t('alertMgmt.summaryRequired') }]}
          >
            <Input placeholder="Brief description of the alert" />
          </Form.Item>
          <Form.Item name="description" label={t('alertMgmt.description')}>
            <TextArea rows={2} placeholder="Detailed description (optional)" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AlertManagementPage;
