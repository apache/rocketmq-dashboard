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

import { useState } from 'react';
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
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '../../components/PageHeader';
import { mockAlertRules, type AlertRule } from '../../mock/alerts';
import { useLang } from '../../i18n/LangContext';

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
  const [rules, setRules] = useState<AlertRule[]>([...mockAlertRules]);
  const [modalVisible, setModalVisible] = useState(false);
  const [form] = Form.useForm();

  const channelLabels: Record<string, string> = {
    dingtalk: 'DingTalk',
    email: 'Email',
    sms: 'SMS',
  };

  const enabledCount = rules.filter((r) => r.enabled).length;

  // eslint-disable-next-line react-hooks/purity
  const dayAgo = Date.now() - 24 * 60 * 60 * 1000;
  const triggered24h = rules.filter(
    (r) => r.lastTriggered && new Date(r.lastTriggered).getTime() > dayAgo,
  ).length;

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
          onChange={() => {
            setRules((prev) =>
              prev.map((r) => (r.id === record.id ? { ...r, enabled: !r.enabled } : r)),
            );
          }}
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
            style={{ borderColor: '#1890ff', color: '#1890ff' }}
          >
            {t('common.edit')}
          </Button>
          <Button
            size="small"
            icon={<Trash size={14} />}
            danger
            style={{ borderColor: '#ff4d4f', color: '#ff4d4f' }}
            onClick={() => setRules((prev) => prev.filter((r) => r.id !== record.id))}
          >
            {t('common.delete')}
          </Button>
        </Flex>
      ),
    },
  ];

  const handleSubmit = async () => {
    await form.validateFields();
    message.success(t('alerts.ruleCreated'));
    setModalVisible(false);
    form.resetFields();
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
            <Button type="primary" icon={<Plus />} onClick={() => setModalVisible(true)}>
              {t('alerts.newRule')}
            </Button>
          </Flex>
        }
      />

      {/* ─── Table ─── */}
      <Card bodyStyle={{ padding: 0 }}>
        <Table<AlertRule>
          columns={columns}
          dataSource={rules}
          rowKey="id"
          size="small"
          pagination={false}
        />
      </Card>

      <Modal
        title={t('alerts.newRule')}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => {
          setModalVisible(false);
          form.resetFields();
        }}
        okText={t('common.create')}
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
