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

import React, { useEffect, useMemo, useState } from 'react';
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
import {
  createAlertRule,
  deleteAlertRule,
  exportAlertRulesYaml,
  listAlertRules,
  toggleAlertRule,
  updateAlertRule,
} from '../../api/alertManagement';
import type { AlertRule as PersistedAlertRule, AlertRuleRequest } from '../../api/alertManagement';
import { downloadBlob } from '../../utils/download';

const { TextArea } = Input;

// ─── Types ────────────────────────────────────────────────────────
interface AlertRule {
  key: string;
  id?: string;
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

interface AlertRuleFormValues {
  alert: string;
  expr: string;
  for: string;
  severity: string;
  summary: string;
  description?: string;
  enabled?: boolean;
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

// ─── Mapping helpers ──────────────────────────────────────────────
const DESCRIPTION_SEPARATOR = ' - ';
const EXPRESSION_PATTERN = /^\s*(.+?)\s*(>=|<=|==|!=|>|<)\s*(-?\d+(?:\.\d+)?)\s*$/;

function inferTeam(metric?: string): string {
  const value = metric || '';
  if (value.startsWith('rocketmq_proxy_')) return 'proxy';
  if (value.includes('replication') || value.includes('fall_behind') || value.includes('slave')) {
    return 'broker';
  }
  if (value.includes('consumer') || value.includes('lag')) return 'consumer';
  if (value.includes('producer') || value.includes('client')) return 'client';
  if (value.includes('topic') || value.includes('messages_in') || value.includes('messages_out')) {
    return 'topic';
  }
  return 'broker';
}

function groupName(team: string): string {
  if (team === 'client') return 'rocketmq-client.rules';
  if (team === 'consumer') return 'rocketmq-consumer.rules';
  if (team === 'topic') return 'rocketmq-topic.rules';
  if (team === 'proxy') return 'rocketmq-proxy.rules';
  return 'rocketmq-broker.rules';
}

function parseYamlRules(yamlStr: string): AlertRule[] {
  const rules: AlertRule[] = [];
  if (!yamlStr) return rules;

  const groupBlocks = yamlStr.split(/\n(?=\s*- name:)/);
  let ruleIndex = 0;
  for (const block of groupBlocks) {
    const groupNameMatch = block.match(/- name:\s*(.+)/);
    if (!groupNameMatch) continue;
    const parsedGroupName = groupNameMatch[1].trim();
    const ruleBlocks = block.split(/\n\s*#\s*Rule\s+\d+:/);
    for (let index = 1; index < ruleBlocks.length; index += 1) {
      const ruleBlock = ruleBlocks[index];
      const alertMatch = ruleBlock.match(/alert:\s*(.+)/);
      if (!alertMatch) continue;
      const alertName = alertMatch[1].trim();
      const exprMatch = ruleBlock.match(/expr:\s*(.+)/);
      const forMatch = ruleBlock.match(/for:\s*(.+)/);
      const severityMatch = ruleBlock.match(/severity:\s*(.+)/);
      const teamMatch = ruleBlock.match(/team:\s*(.+)/);
      const summaryMatch = ruleBlock.match(/summary:\s*"(.+)"/);
      const descMatch = ruleBlock.match(/description:\s*"(.+)"/);
      ruleIndex += 1;
      rules.push({
        key: alertName,
        index: ruleIndex,
        alert: alertName,
        group: parsedGroupName,
        expr: exprMatch ? exprMatch[1].trim() : '',
        for: forMatch ? forMatch[1].trim() : '',
        severity: severityMatch ? severityMatch[1].trim() : 'warning',
        team: teamMatch ? teamMatch[1].trim() : inferTeam(exprMatch?.[1]),
        summary: summaryMatch ? summaryMatch[1].trim() : alertName,
        description: descMatch ? descMatch[1].trim() : '',
        enabled: true,
      });
    }
  }
  return rules;
}

function buildExpression(rule: PersistedAlertRule): string {
  const metric = scopedMetric(rule);
  const operator = rule.operator || '>';
  const threshold = rule.threshold ?? 0;
  return `${metric} ${operator} ${threshold}`;
}

function scopedMetric(rule: PersistedAlertRule): string {
  const metric = rule.metric || 'rocketmq_consumer_lag_messages';
  if (metric.includes('{')) return metric;

  const labels = [
    ['cluster', rule.clusterName],
    ['broker', rule.brokerName],
  ]
    .filter(([, value]) => hasRuleScope(value))
    .map(([name, value]) => `${name}="${escapeLabelValue(value || '')}"`);
  return labels.length > 0 ? `${metric}{${labels.join(',')}}` : metric;
}

function hasRuleScope(value?: string): boolean {
  return Boolean(value && value.trim() && value.trim() !== '*');
}

function escapeLabelValue(value: string): string {
  return value.trim().replace(/\\/g, '\\\\').replace(/"/g, '\\"');
}

function splitDescription(description?: string): { summary: string; detail: string } {
  if (!description) return { summary: '', detail: '' };
  const separatorIndex = description.indexOf(DESCRIPTION_SEPARATOR);
  if (separatorIndex < 0) return { summary: description, detail: '' };
  return {
    summary: description.slice(0, separatorIndex),
    detail: description.slice(separatorIndex + DESCRIPTION_SEPARATOR.length),
  };
}

function combineDescription(summary: string, detail?: string): string {
  const trimmedSummary = summary.trim();
  const trimmedDetail = detail?.trim();
  return trimmedDetail
    ? `${trimmedSummary}${DESCRIPTION_SEPARATOR}${trimmedDetail}`
    : trimmedSummary;
}

function toUiRule(rule: PersistedAlertRule, index: number): AlertRule {
  const team = inferTeam(rule.metric);
  const description = splitDescription(rule.description);
  return {
    key: rule.id || rule.name,
    id: rule.id,
    index: index + 1,
    alert: rule.name,
    group: groupName(team),
    expr: buildExpression(rule),
    for: rule.duration || '5m',
    severity: (rule.severity || 'warning').toLowerCase(),
    team,
    summary: description.summary || rule.name,
    description: description.detail,
    enabled: rule.enabled,
  };
}

function parseExpression(
  expr: string,
): Pick<AlertRuleRequest, 'metric' | 'operator' | 'threshold'> {
  const match = expr.match(EXPRESSION_PATTERN);
  if (!match) {
    throw new Error('Expression must look like: metric{labels} > 100');
  }
  return {
    metric: match[1].trim(),
    operator: match[2],
    threshold: Number(match[3]),
  };
}

function parseScopeLabels(metric: string): {
  metric: string;
  clusterName?: string;
  brokerName?: string;
} {
  const open = metric.indexOf('{');
  if (open < 0) return { metric };
  const metricName = metric.slice(0, open).trim();
  const inner = metric.slice(open + 1, metric.lastIndexOf('}'));
  let clusterName: string | undefined;
  let brokerName: string | undefined;
  const remaining: string[] = [];
  for (const part of inner.split(',')) {
    const eq = part.indexOf('=');
    if (eq < 0) continue;
    const key = part.slice(0, eq).trim();
    const raw = part.slice(eq + 1).trim();
    const value = raw.replace(/^"|"$/g, '').replace(/\\\\/g, '\\').replace(/\\"/g, '"');
    if (key === 'cluster') clusterName = value;
    else if (key === 'broker') brokerName = value;
    else remaining.push(part.trim());
  }
  // Rebuild the metric expression preserving every non-scope label.
  const metricExpr =
    remaining.length > 0
      ? `${metricName}{${remaining.join(',')}}`
      : clusterName || brokerName
        ? metricName
        : metric;
  return { metric: metricExpr, clusterName, brokerName };
}

function toAlertRuleRequest(
  values: AlertRuleFormValues,
  editingRule: AlertRule | null,
): AlertRuleRequest {
  const expression = parseExpression(values.expr);
  const scope = parseScopeLabels(expression.metric ?? '');
  return {
    id: editingRule?.id,
    name: values.alert.trim(),
    ...expression,
    metric: scope.metric,
    clusterName: scope.clusterName,
    brokerName: scope.brokerName,
    duration: values.for,
    enabled: values.enabled ?? true,
    description: combineDescription(values.summary, values.description),
    severity: values.severity,
  };
}

async function loadAlertRuleRows(): Promise<AlertRule[]> {
  const persistedRules = await listAlertRules();
  if (persistedRules.length > 0) {
    return persistedRules.map(toUiRule);
  }
  const exported = await exportAlertRulesYaml();
  return parseYamlRules(exported.rules || '');
}

// ─── Component ────────────────────────────────────────────────────
const AlertManagementPage: React.FC = () => {
  const { t } = useLang();
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const fetchFailedMessage = t('alertMgmt.fetchFailed');

  const [alertRules, setAlertRules] = useState<AlertRule[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingRule, setEditingRule] = useState<AlertRule | null>(null);
  const [searchText, setSearchText] = useState('');
  const [filterGroup, setFilterGroup] = useState('all');
  const [filterSeverity, setFilterSeverity] = useState('all');
  const [filterStatus, setFilterStatus] = useState('all');
  const [selectedRuleKeys, setSelectedRuleKeys] = useState<React.Key[]>([]);

  useEffect(() => {
    let cancelled = false;

    const loadRules = async () => {
      if (!cancelled) {
        setLoading(true);
      }
      try {
        const loadedRules = await loadAlertRuleRows();
        if (!cancelled) {
          const enabledRuleKeys = new Set<React.Key>(
            loadedRules.filter((rule) => rule.enabled).map((rule) => rule.key),
          );
          setAlertRules(loadedRules);
          setSelectedRuleKeys((currentKeys) =>
            currentKeys.filter((key) => enabledRuleKeys.has(key)),
          );
        }
      } catch {
        if (!cancelled) {
          message.error(fetchFailedMessage);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    void loadRules();

    return () => {
      cancelled = true;
    };
  }, [fetchFailedMessage, message]);

  const fetchAlertRules = async () => {
    setLoading(true);
    try {
      const loadedRules = await loadAlertRuleRows();
      const enabledRuleKeys = new Set<React.Key>(
        loadedRules.filter((rule) => rule.enabled).map((rule) => rule.key),
      );
      setAlertRules(loadedRules);
      setSelectedRuleKeys((currentKeys) => currentKeys.filter((key) => enabledRuleKeys.has(key)));
    } catch {
      message.error(t('alertMgmt.fetchFailed'));
    } finally {
      setLoading(false);
    }
  };

  const handleToggleRule = async (rule: AlertRule, enabled: boolean) => {
    if (!rule.id) {
      message.error(t('alertMgmt.updateFailed'));
      return;
    }
    try {
      const updated = await toggleAlertRule(rule.id, enabled);
      const nextRule = toUiRule(updated, rule.index - 1);
      setAlertRules((rules) => rules.map((item) => (item.key === rule.key ? nextRule : item)));
      setSelectedRuleKeys((keys) => (enabled ? keys : keys.filter((key) => key !== rule.key)));
      message.success(t('alertMgmt.updateSuccess'));
    } catch {
      message.error(t('alertMgmt.updateFailed'));
    }
  };

  const handleAddRule = () => {
    setEditingRule(null);
    form.resetFields();
    form.setFieldsValue({
      severity: 'warning',
      for: '5m',
      enabled: true,
    });
    setModalVisible(true);
  };

  const handleEditRule = (rule: AlertRule) => {
    setEditingRule(rule);
    form.setFieldsValue({
      alert: rule.alert,
      expr: rule.expr,
      for: rule.for,
      severity: rule.severity,
      summary: rule.summary,
      description: rule.description,
      enabled: rule.enabled,
    });
    setModalVisible(true);
  };

  const handleDeleteRule = async (rule: AlertRule) => {
    if (!rule.id) {
      message.error(t('alertMgmt.deleteFailed'));
      return;
    }
    try {
      await deleteAlertRule(rule.id);
      setAlertRules((rules) =>
        rules
          .filter((item) => item.key !== rule.key)
          .map((item, index) => ({ ...item, index: index + 1 })),
      );
      setSelectedRuleKeys((keys) => keys.filter((key) => key !== rule.key));
      message.success(t('alertMgmt.deleteSuccess'));
    } catch {
      message.error(t('alertMgmt.deleteFailed'));
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      const request = toAlertRuleRequest(values, editingRule);
      if (editingRule) {
        if (!request.id) {
          message.error(t('alertMgmt.updateFailed'));
          return;
        }
        const updated = await updateAlertRule(request);
        const nextRule = toUiRule(updated, editingRule.index - 1);
        setAlertRules((rules) =>
          rules.map((rule) => (rule.key === editingRule.key ? nextRule : rule)),
        );
        message.success(t('alertMgmt.updateSuccess'));
      } else {
        const created = await createAlertRule(request);
        setAlertRules((rules) =>
          [toUiRule(created, 0), ...rules].map((rule, index) => ({ ...rule, index: index + 1 })),
        );
        message.success(t('alertMgmt.createSuccess'));
      }
      setModalVisible(false);
      form.resetFields();
    } catch (error) {
      if (error instanceof Error && error.message.startsWith('Expression must')) {
        message.error(error.message);
        return;
      }
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      message.error(editingRule ? t('alertMgmt.updateFailed') : t('alertMgmt.createFailed'));
      // Ant Design validation errors are already rendered near the fields.
    }
  };

  const selectedRules = useMemo(
    () => alertRules.filter((rule) => rule.enabled && selectedRuleKeys.includes(rule.key)),
    [alertRules, selectedRuleKeys],
  );

  const handleExportYaml = async () => {
    try {
      const selectedRuleIds = selectedRules
        .map((rule) => rule.id)
        .filter((id): id is string => Boolean(id));
      const data = await exportAlertRulesYaml(
        selectedRuleIds.length > 0 ? selectedRuleIds : undefined,
      );
      downloadBlob(new Blob([data.rules], { type: 'text/yaml' }), 'rocketmq-alert-rules.yaml');
      message.success(t('alertMgmt.exportSuccess'));
    } catch {
      message.error(t('alertMgmt.fetchFailed'));
    }
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
        <Switch
          size="small"
          checked={enabled}
          disabled={!record.id}
          onChange={(checked) => {
            void handleToggleRule(record, checked);
          }}
        />
      ),
    },
    {
      title: t('common.actions'),
      width: 120,
      render: (_: unknown, record: AlertRule) => (
        <Space size="small">
          <Tooltip title={record.id ? t('common.edit') : t('alertMgmt.defaultRuleReadonly')}>
            <Button
              type="text"
              size="small"
              disabled={!record.id}
              icon={<Pencil size={16} />}
              onClick={() => handleEditRule(record)}
            />
          </Tooltip>
          <Popconfirm
            disabled={!record.id}
            title={t('common.areYouSureToDelete')}
            onConfirm={() => {
              void handleDeleteRule(record);
            }}
            okText={t('common.confirm')}
            cancelText={t('common.cancel')}
          >
            <Tooltip title={record.id ? t('common.delete') : t('alertMgmt.defaultRuleReadonly')}>
              <Button
                type="text"
                size="small"
                danger
                disabled={!record.id}
                icon={<Trash size={16} />}
              />
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
          rowSelection={{
            selectedRowKeys: selectedRules.map((rule) => rule.key),
            onChange: setSelectedRuleKeys,
            preserveSelectedRowKeys: true,
            getCheckboxProps: (record) => ({ disabled: !record.enabled }),
          }}
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
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Row gutter={16}>
            <Col span={16}>
              <Form.Item
                name="alert"
                label={t('alertMgmt.alertName')}
                rules={[{ required: true, message: t('alertMgmt.alertNameRequired') }]}
              >
                <Input placeholder="e.g. RocketMQ_Broker_Down" />
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
          </Row>
          <Form.Item
            name="expr"
            label={t('alertMgmt.expression')}
            rules={[
              { required: true, message: t('alertMgmt.expressionRequired') },
              {
                validator: (_, value: string) =>
                  !value || EXPRESSION_PATTERN.test(value)
                    ? Promise.resolve()
                    : Promise.reject(new Error(t('alertMgmt.expressionInvalid'))),
              },
            ]}
          >
            <TextArea rows={2} placeholder={'e.g. up{job=~"rocketmq.*broker.*"} == 0'} />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="for"
                label={t('alertMgmt.forDuration')}
                rules={[{ required: true, message: t('alertMgmt.forDurationRequired') }]}
              >
                <Input placeholder="e.g. 5m" />
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
          <Form.Item name="enabled" valuePropName="checked">
            <Switch
              checkedChildren={t('common.enabled')}
              unCheckedChildren={t('common.disabled')}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AlertManagementPage;
