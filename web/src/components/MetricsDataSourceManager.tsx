// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.

import React, { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, Switch, Space, Tag, message } from 'antd';
import { useLang } from '../i18n/LangContext';
import * as metricsService from '../services/metricsService';
import type { MetricsDataSource, MetricsProviderType } from '../api/metrics';

const PROVIDER_TYPE_LABEL_KEY: Record<MetricsProviderType, string> = {
  PROMETHEUS: 'metricsDataSource.providerPrometheus',
  VICTORIAMETRICS: 'metricsDataSource.providerVictoriaMetrics',
  THANOS: 'metricsDataSource.providerThanos',
  CORTEX: 'metricsDataSource.providerCortex',
  MIMIR: 'metricsDataSource.providerMimir',
  ARMS: 'metricsDataSource.providerArms',
  CUSTOM: 'metricsDataSource.providerCustom',
};

const PROVIDER_TYPES = Object.keys(PROVIDER_TYPE_LABEL_KEY) as MetricsProviderType[];

const AUTH_TYPES = ['none', 'basic', 'bearer'];

export function MetricsDataSourceManager() {
  const { t } = useLang();
  const [sources, setSources] = useState<MetricsDataSource[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<MetricsDataSource | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<MetricsDataSource>();

  const load = async () => {
    setLoading(true);
    try {
      setSources(await metricsService.listMetricDataSources());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const data = await metricsService.listMetricDataSources();
        if (!cancelled) setSources(data);
      } catch {
        if (!cancelled) message.error(t('metricsDataSource.loadFailed'));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [t]);

  const openAdd = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ providerType: 'PROMETHEUS', authType: 'none', enabled: true });
    setModalOpen(true);
  };

  const openEdit = (record: MetricsDataSource) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const handleDelete = async (record: MetricsDataSource) => {
    await metricsService.deleteMetricDataSource(record.name);
    await load();
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      if (editing) {
        await metricsService.updateMetricDataSource({ ...editing, ...values });
      } else {
        await metricsService.createMetricDataSource(values);
      }
      setModalOpen(false);
      await load();
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    {
      title: t('metricsDataSource.name'),
      dataIndex: 'name' as const,
      key: 'name',
    },
    {
      title: t('metricsDataSource.providerType'),
      dataIndex: 'providerType' as const,
      key: 'providerType',
      render: (value: MetricsProviderType) => <Tag>{t(PROVIDER_TYPE_LABEL_KEY[value])}</Tag>,
    },
    {
      title: t('metricsDataSource.url'),
      dataIndex: 'url' as const,
      key: 'url',
    },
    {
      title: t('metricsDataSource.enabled'),
      dataIndex: 'enabled' as const,
      key: 'enabled',
      render: (value: boolean) =>
        value ? t('metricsDataSource.enabledStatus') : t('metricsDataSource.disabledStatus'),
    },
    {
      title: '',
      key: 'actions',
      render: (_: unknown, record: MetricsDataSource) => (
        <Space>
          <Button size="small" onClick={() => openEdit(record)}>
            {t('metricsDataSource.edit')}
          </Button>
          <Button size="small" danger onClick={() => handleDelete(record)}>
            {t('metricsDataSource.delete')}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <div>
          <h2>{t('metricsDataSource.title')}</h2>
          <p>{t('metricsDataSource.subtitle')}</p>
        </div>
        <Button type="primary" onClick={openAdd}>
          {t('metricsDataSource.add')}
        </Button>
      </div>
      <Table<MetricsDataSource>
        rowKey="name"
        loading={loading}
        dataSource={sources}
        columns={columns}
        pagination={false}
      />
      <Modal
        open={modalOpen}
        title={editing ? t('metricsDataSource.edit') : t('metricsDataSource.add')}
        onOk={handleSubmit}
        confirmLoading={submitting}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label={t('metricsDataSource.name')} rules={[{ required: true }]}>
            <Input disabled={!!editing} />
          </Form.Item>
          <Form.Item
            name="providerType"
            label={t('metricsDataSource.providerType')}
            rules={[{ required: true }]}
          >
            <Select
              options={PROVIDER_TYPES.map((type) => ({
                value: type,
                label: t(PROVIDER_TYPE_LABEL_KEY[type]),
              }))}
            />
          </Form.Item>
          <Form.Item name="url" label={t('metricsDataSource.url')} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="authType" label={t('metricsDataSource.authType')}>
            <Select options={AUTH_TYPES.map((type) => ({ value: type, label: type }))} />
          </Form.Item>
          <Form.Item name="enabled" label={t('metricsDataSource.enabled')} valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

export default MetricsDataSourceManager;
