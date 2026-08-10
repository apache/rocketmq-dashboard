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

import { useEffect, useState, type Key } from 'react';
import { Plus, Pencil, Trash } from '@phosphor-icons/react';
import {
  Button,
  Card,
  Table,
  Switch,
  Tag,
  Modal,
  Form,
  Input,
  Select,
  InputNumber,
  Checkbox,
  Flex,
  message,
  Popconfirm,
  theme,
} from 'antd';
import type { ColumnsType, TableRowSelection } from 'antd/es/table/interface';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import type { AlertRule } from '../../api/ops';
import {
  createAlertRule,
  deleteAlertRule,
  listAlertRules,
  toggleAlertRule,
  updateAlertRule,
} from '../../services/opsService';
import { attachThresholdUnit } from './alertRulePayload';

const { TextArea } = Input;

const channelColors: Record<string, string> = {
  dingtalk: 'blue',
  email: 'green',
  sms: 'orange',
};

const metricOptions = ['磁盘使用率', '消费堆积量', 'TPS 异常', 'Broker 离线', 'Proxy 连接数'];

const durationOptions = ['1分钟', '5分钟', '15分钟', '30分钟'];

const AlertsPage = () => {
  const { t } = useLang();
  const { token } = theme.useToken();
  const [rules, setRules] = useState<AlertRule[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingRule, setEditingRule] = useState<AlertRule | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [actionId, setActionId] = useState<string | null>(null);
  const [selectedRuleIds, setSelectedRuleIds] = useState<Key[]>([]);
  const [bulkAction, setBulkAction] = useState<'enable' | 'disable' | null>(null);
  const [form] = Form.useForm();

  const channelLabels: Record<string, string> = {
    dingtalk: 'DingTalk',
    email: 'Email',
    sms: 'SMS',
  };

  useEffect(() => {
    let cancelled = false;

    void listAlertRules()
      .then((nextRules) => {
        if (!cancelled) setRules(nextRules);
      })
      .catch(() => {
        if (!cancelled) message.error('告警规则加载失败，请稍后重试');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const enabledCount = rules.filter((r) => r.enabled).length;
  const selectedCount = selectedRuleIds.length;
  const hasSelectedRules = selectedCount > 0;
  const isBulkRunning = bulkAction !== null;
  const isActionRunning = actionId !== null || isBulkRunning;

  // eslint-disable-next-line react-hooks/purity
  const dayAgo = Date.now() - 24 * 60 * 60 * 1000;
  const triggered24h = rules.filter(
    (r) => r.lastTriggered && new Date(r.lastTriggered).getTime() > dayAgo,
  ).length;

  const openCreateModal = () => {
    setEditingRule(null);
    form.resetFields();
    setModalVisible(true);
  };

  const openEditModal = (rule: AlertRule) => {
    setEditingRule(rule);
    form.setFieldsValue(rule);
    setModalVisible(true);
  };

  const handleToggle = async (rule: AlertRule, enabled: boolean) => {
    if (isActionRunning) return;
    setActionId(`toggle-${rule.id}`);
    try {
      const updated = await toggleAlertRule(rule.id, enabled);
      setRules((previous) => previous.map((item) => (item.id === rule.id ? updated : item)));
    } catch {
      message.error('更新告警规则状态失败，请稍后重试');
    } finally {
      setActionId(null);
    }
  };

  const handleDelete = async (rule: AlertRule) => {
    if (isActionRunning) return;
    setActionId(`delete-${rule.id}`);
    try {
      await deleteAlertRule(rule.id);
      setRules((previous) => previous.filter((item) => item.id !== rule.id));
      setSelectedRuleIds((previous) => previous.filter((id) => id !== rule.id));
      message.success('告警规则已删除');
    } catch {
      message.error('删除告警规则失败，请稍后重试');
    } finally {
      setActionId(null);
    }
  };

  const handleBulkToggle = async (enabled: boolean) => {
    const targetIds = selectedRuleIds.map(String);
    if (targetIds.length === 0 || isActionRunning) return;

    setBulkAction(enabled ? 'enable' : 'disable');
    try {
      const results = await Promise.allSettled(
        targetIds.map(async (id) => ({
          id,
          rule: await toggleAlertRule(id, enabled),
        })),
      );

      const updatedRules = new Map<string, AlertRule>();
      const failedIds: string[] = [];

      results.forEach((result, index) => {
        const id = targetIds[index];
        if (result.status === 'fulfilled') {
          updatedRules.set(result.value.id, result.value.rule);
        } else {
          failedIds.push(id);
        }
      });

      if (updatedRules.size > 0) {
        setRules((previous) => previous.map((rule) => updatedRules.get(rule.id) ?? rule));
      }

      setSelectedRuleIds(failedIds);

      if (failedIds.length === 0) {
        message.success(
          t(enabled ? 'alerts.bulkEnableSuccess' : 'alerts.bulkDisableSuccess', {
            count: updatedRules.size,
          }),
        );
      } else if (updatedRules.size === 0) {
        message.error(
          t(enabled ? 'alerts.bulkEnableFailed' : 'alerts.bulkDisableFailed', {
            count: targetIds.length,
          }),
        );
      } else {
        message.warning(
          t(enabled ? 'alerts.bulkEnablePartial' : 'alerts.bulkDisablePartial', {
            success: updatedRules.size,
            failed: failedIds.length,
          }),
        );
      }
    } finally {
      setBulkAction(null);
    }
  };

  const rowSelection: TableRowSelection<AlertRule> = {
    selectedRowKeys: selectedRuleIds,
    onChange: (keys) => setSelectedRuleIds(keys),
    getCheckboxProps: () => ({
      disabled: isActionRunning,
    }),
  };

  const columns: ColumnsType<AlertRule> = [
    {
      title: t('alerts.ruleName'),
      dataIndex: 'name',
    },
    {
      title: t('alerts.metric'),
      dataIndex: 'metric',
    },
    {
      title: t('alerts.threshold'),
      render: (_, record) => `${record.operator} ${record.threshold}${record.thresholdUnit}`,
    },
    {
      title: t('alerts.duration'),
      dataIndex: 'duration',
    },
    {
      title: t('alerts.channels'),
      render: (_, record) => (
        <Flex gap={4} wrap="wrap">
          {record.channels.map((ch) => (
            <Tag key={ch} color={channelColors[ch]}>
              {channelLabels[ch]}
            </Tag>
          ))}
        </Flex>
      ),
    },
    {
      title: t('common.status'),
      render: (_, record) => (
        <Switch
          checked={record.enabled}
          loading={actionId === `toggle-${record.id}`}
          disabled={isActionRunning}
          onChange={(enabled) => void handleToggle(record, enabled)}
        />
      ),
    },
    {
      title: t('alerts.lastTriggered'),
      render: (_, record) =>
        record.lastTriggered ? (
          record.lastTriggered
        ) : (
          <span style={{ color: '#999' }}>{t('alerts.neverTriggered')}</span>
        ),
    },
    {
      title: t('common.actions'),
      render: (_, record) => (
        <Flex gap={8}>
          <Button
            size="small"
            icon={<Pencil size={14} />}
            disabled={isActionRunning}
            style={{ borderColor: '#1890ff', color: '#1890ff' }}
            onClick={() => openEditModal(record)}
          >
            {t('common.edit')}
          </Button>
          <Popconfirm
            title={t('common.areYouSureToDelete')}
            onConfirm={() => void handleDelete(record)}
            okText={t('common.confirm')}
            cancelText={t('common.cancel')}
          >
            <Button
              size="small"
              icon={<Trash size={14} />}
              danger
              loading={actionId === `delete-${record.id}`}
              disabled={isActionRunning}
              style={{ borderColor: '#ff4d4f', color: '#ff4d4f' }}
            >
              {t('common.delete')}
            </Button>
          </Popconfirm>
        </Flex>
      ),
    },
  ];

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const payload = attachThresholdUnit(values);
      setSubmitting(true);
      if (editingRule) {
        const updated = await updateAlertRule({ ...editingRule, ...payload });
        setRules((previous) =>
          previous.map((rule) => (rule.id === editingRule.id ? updated : rule)),
        );
        message.success('告警规则已更新');
      } else {
        const created = await createAlertRule(payload);
        setRules((previous) => [...previous, created]);
        message.success(t('alerts.ruleCreated'));
      }
      setModalVisible(false);
      form.resetFields();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return; // validation failure; antd already shows field-level errors
      }
      message.error('保存告警规则失败，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ padding: 24 }}>
      {/* ─── Header ─── */}
      <PageHeader
        title={t('alerts.title')}
        subtitle={t('alerts.subtitle')}
        extra={
          <Flex gap={16}>
            <Flex align="center" gap={4}>
              <span style={{ fontSize: 12, color: '#999' }}>{t('alerts.totalRules')}</span>
              <span style={{ fontSize: 18, fontWeight: 600, color: '#3b82f6' }}>
                {rules.length}
              </span>
            </Flex>
            <Flex align="center" gap={4}>
              <span style={{ fontSize: 12, color: '#999' }}>{t('alerts.enabled')}</span>
              <span style={{ fontSize: 18, fontWeight: 600, color: '#14b8a6' }}>
                {enabledCount}
              </span>
            </Flex>
            <Flex align="center" gap={4}>
              <span style={{ fontSize: 12, color: '#999' }}>{t('alerts.triggered24h')}</span>
              <span style={{ fontSize: 18, fontWeight: 600, color: '#8b5cf6' }}>
                {triggered24h}
              </span>
            </Flex>
            <Button
              type="primary"
              icon={<Plus />}
              disabled={isActionRunning}
              onClick={openCreateModal}
            >
              {t('alerts.newRule')}
            </Button>
          </Flex>
        }
      />

      {/* ─── Table ─── */}
      <Card bodyStyle={{ padding: 0 }}>
        <Flex
          align="center"
          justify="space-between"
          style={{
            padding: '12px 16px',
            borderBottom: `1px solid ${token.colorBorderSecondary}`,
          }}
        >
          <span style={{ color: token.colorTextSecondary }}>
            {t('alerts.selectedRules', { count: selectedCount })}
          </span>
          <Flex gap={8}>
            <Button
              size="small"
              disabled={!hasSelectedRules || isActionRunning}
              loading={bulkAction === 'enable'}
              onClick={() => void handleBulkToggle(true)}
            >
              {t('alerts.bulkEnable')}
            </Button>
            <Button
              size="small"
              disabled={!hasSelectedRules || isActionRunning}
              loading={bulkAction === 'disable'}
              onClick={() => void handleBulkToggle(false)}
            >
              {t('alerts.bulkDisable')}
            </Button>
          </Flex>
        </Flex>
        <Table<AlertRule>
          columns={columns}
          dataSource={rules}
          rowKey="id"
          size="small"
          loading={loading}
          rowSelection={rowSelection}
          pagination={false}
        />
      </Card>

      <Modal
        title={editingRule ? t('common.edit') : t('alerts.newRule')}
        open={modalVisible}
        onOk={handleSubmit}
        confirmLoading={submitting}
        onCancel={() => {
          setModalVisible(false);
          setEditingRule(null);
          form.resetFields();
        }}
        okText={editingRule ? t('common.edit') : t('common.create')}
        cancelText={t('common.cancel')}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label={t('alerts.ruleName')}
            rules={[{ required: true, message: '请输入规则名称' }]}
          >
            <Input placeholder="请输入规则名称" />
          </Form.Item>

          <Form.Item
            name="metric"
            label={t('alerts.metric')}
            rules={[{ required: true, message: '请选择监控指标' }]}
          >
            <Select
              placeholder="请选择监控指标"
              options={metricOptions.map((m) => ({ label: m, value: m }))}
            />
          </Form.Item>

          <Form.Item label={t('alerts.threshold')}>
            <Flex gap={8}>
              <Form.Item
                name="operator"
                noStyle
                rules={[{ required: true, message: '请选择运算符' }]}
              >
                <Select
                  placeholder="运算符"
                  style={{ width: 100 }}
                  options={[
                    { label: '>', value: '>' },
                    { label: '<', value: '<' },
                    { label: '>=', value: '>=' },
                    { label: '<=', value: '<=' },
                  ]}
                />
              </Form.Item>
              <Form.Item
                name="threshold"
                noStyle
                rules={[{ required: true, message: '请输入阈值' }]}
              >
                <InputNumber placeholder="阈值" style={{ flex: 1 }} />
              </Form.Item>
            </Flex>
          </Form.Item>

          <Form.Item
            name="duration"
            label={t('alerts.duration')}
            rules={[{ required: true, message: '请选择持续时间' }]}
          >
            <Select
              placeholder="请选择持续时间"
              options={durationOptions.map((d) => ({ label: d, value: d }))}
            />
          </Form.Item>

          <Form.Item
            name="channels"
            label={t('alerts.channels')}
            rules={[{ required: true, message: '请选择通知渠道' }]}
          >
            <Checkbox.Group
              options={[
                { label: 'DingTalk', value: 'dingtalk' },
                { label: 'Email', value: 'email' },
                { label: 'SMS', value: 'sms' },
              ]}
            />
          </Form.Item>

          <Form.Item name="description" label="规则描述">
            <TextArea placeholder="请输入规则描述" rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AlertsPage;
