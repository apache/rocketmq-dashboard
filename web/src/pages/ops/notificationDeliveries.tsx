/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { useEffect, useState } from 'react';
import {
  Button,
  Card,
  Descriptions,
  Drawer,
  Flex,
  Select,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { ArrowClockwise, Eye } from '@phosphor-icons/react';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import type { Instance } from '../../api/instance';
import type { NotificationDeliveryRecord } from '../../api/ops';
import { listInstances } from '../../services/instanceService';
import {
  listAlertDeliveriesPage,
  retryAlertDeliveries,
  retryAlertDelivery,
} from '../../services/opsService';
import { formatUtcDateTime } from '../../utils/format';
import { tableScrollX } from '../../utils/table';

const statusColors: Record<NotificationDeliveryRecord['status'], string> = {
  PENDING: 'default',
  SENDING: 'processing',
  DELIVERED: 'success',
  RETRY_WAIT: 'warning',
  FAILED: 'error',
};

const NotificationDeliveriesPage = () => {
  const { t } = useLang();
  const [items, setItems] = useState<NotificationDeliveryRecord[]>([]);
  const [instances, setInstances] = useState<Instance[]>([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [channel, setChannel] = useState<string>();
  const [status, setStatus] = useState<NotificationDeliveryRecord['status']>();
  const [instanceId, setInstanceId] = useState<string>();
  const [selectedDelivery, setSelectedDelivery] = useState<NotificationDeliveryRecord>();
  const [retryingIds, setRetryingIds] = useState<Set<number>>(() => new Set());
  const [retryingVisible, setRetryingVisible] = useState(false);
  const [refreshNonce, setRefreshNonce] = useState(0);

  const refresh = () => {
    setLoading(true);
    setRefreshNonce((current) => current + 1);
  };

  const retryDelivery = async (record: NotificationDeliveryRecord) => {
    setRetryingIds((current) => new Set(current).add(record.id));
    try {
      await retryAlertDelivery(record.id);
      message.success(t('deliveries.retryQueued'));
      setSelectedDelivery((current) =>
        current?.id === record.id
          ? { ...current, status: 'PENDING', attemptCount: 0, lastError: null }
          : current,
      );
      refresh();
    } catch {
      message.error(t('deliveries.retryFailed'));
    } finally {
      setRetryingIds((current) => {
        const next = new Set(current);
        next.delete(record.id);
        return next;
      });
    }
  };

  const retryVisibleFailures = async () => {
    const ids = items.filter((item) => item.status === 'FAILED').map((item) => item.id);
    if (ids.length === 0) return;
    setRetryingVisible(true);
    try {
      const result = await retryAlertDeliveries(ids);
      const failed = Object.keys(result.failures).length;
      message.success(
        t('deliveries.bulkRetryQueued', {
          succeeded: result.succeededIds.length,
          failed: failed ? t('deliveries.bulkRetryFailures', { count: failed }) : '',
        }),
      );
      refresh();
    } catch {
      message.error(t('deliveries.bulkRetryFailed'));
    } finally {
      setRetryingVisible(false);
    }
  };

  useEffect(() => {
    void listInstances()
      .then(setInstances)
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    let cancelled = false;
    void listAlertDeliveriesPage({ channel, status, instanceId, page, pageSize })
      .then((result) => {
        if (cancelled) return;
        setItems(result.items);
        setTotal(result.total);
      })
      .catch(() => {
        if (!cancelled) message.error(t('deliveries.loadFailed'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [channel, status, instanceId, page, pageSize, refreshNonce, t]);

  const resetPage = (change: () => void) => {
    setLoading(true);
    change();
    setPage(1);
  };

  const columns: ColumnsType<NotificationDeliveryRecord> = [
    {
      title: t('deliveries.alert'),
      dataIndex: 'alertTitle',
      width: 300,
      render: (title, record) => (
        <Flex vertical gap={2}>
          <Typography.Text ellipsis={{ tooltip: title }}>{title}</Typography.Text>
          <Typography.Text type="secondary">
            #{record.alertId} · {record.transition ?? '-'}
          </Typography.Text>
        </Flex>
      ),
    },
    {
      title: t('deliveries.instance'),
      dataIndex: 'instanceId',
      width: 145,
      render: (value) => value ?? '-',
    },
    { title: t('deliveries.channel'), dataIndex: 'channel', width: 105 },
    {
      title: t('common.status'),
      dataIndex: 'status',
      width: 120,
      render: (value: NotificationDeliveryRecord['status']) => (
        <Tag color={statusColors[value]}>{value}</Tag>
      ),
    },
    { title: t('deliveries.attempts'), dataIndex: 'attemptCount', width: 90, align: 'center' },
    {
      title: t('deliveries.result'),
      width: 240,
      render: (_, record) =>
        record.lastError ? (
          <Typography.Text type="danger" ellipsis={{ tooltip: record.lastError }}>
            {record.lastError}
          </Typography.Text>
        ) : record.deliveredAt ? (
          t('deliveries.delivered')
        ) : record.nextAttemptAt ? (
          `${t('deliveries.nextAttempt')} ${formatUtcDateTime(record.nextAttemptAt)}`
        ) : (
          '-'
        ),
    },
    {
      title: t('deliveries.deliveredAt'),
      width: 185,
      render: (_, record) => formatUtcDateTime(record.deliveredAt ?? record.createdAt),
    },
    {
      title: t('common.actions'),
      width: 120,
      align: 'center',
      render: (_, record) => (
        <Tooltip title={t('deliveries.viewDetails')}>
          <Button
            type="text"
            size="small"
            icon={<Eye size={18} />}
            aria-label={t('deliveries.viewDetails')}
            onClick={() => setSelectedDelivery(record)}
          />
          {record.status === 'FAILED' && (
            <Tooltip title={t('deliveries.retry')}>
              <Button
                type="text"
                size="small"
                icon={<ArrowClockwise size={18} />}
                aria-label={t('deliveries.retry')}
                loading={retryingIds.has(record.id)}
                onClick={() => void retryDelivery(record)}
              />
            </Tooltip>
          )}
        </Tooltip>
      ),
    },
  ];

  return (
    <>
      <div style={{ padding: 24 }}>
        <PageHeader title={t('deliveries.title')} subtitle={t('deliveries.subtitle')} />
        <Card bodyStyle={{ padding: 20 }}>
          <Flex gap={12} wrap="wrap" style={{ marginBottom: 20 }}>
            <Button
              icon={<ArrowClockwise size={18} />}
              disabled={!items.some((item) => item.status === 'FAILED')}
              loading={retryingVisible}
              onClick={() => void retryVisibleFailures()}
            >
              {t('deliveries.retryCurrentPage')}
            </Button>
            <Select
              allowClear
              placeholder={t('deliveries.allChannels')}
              value={channel}
              style={{ width: 220, flex: '1 1 220px' }}
              options={['dingtalk', 'email', 'sms'].map((value) => ({ value, label: value }))}
              onChange={(value) => resetPage(() => setChannel(value))}
            />
            <Select
              allowClear
              placeholder={t('deliveries.allStatuses')}
              value={status}
              style={{ width: 220, flex: '1 1 220px' }}
              options={Object.keys(statusColors).map((value) => ({ value, label: value }))}
              onChange={(value) => resetPage(() => setStatus(value))}
            />
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder={t('deliveries.allInstances')}
              value={instanceId}
              style={{ width: 280, flex: '1 1 280px' }}
              options={instances.map((instance) => ({
                value: instance.name,
                label: instance.name,
              }))}
              onChange={(value) => resetPage(() => setInstanceId(value))}
            />
          </Flex>
          <Table
            rowKey="id"
            columns={columns}
            dataSource={items}
            loading={loading}
            scroll={{ x: tableScrollX(columns) }}
            pagination={{
              current: page,
              pageSize,
              total,
              showSizeChanger: true,
              showTotal: (count) => `${t('common.total')} ${count}`,
              onChange: (nextPage, nextPageSize) => {
                setLoading(true);
                setPage(nextPage);
                setPageSize(nextPageSize);
              },
            }}
          />
        </Card>
      </div>
      <Drawer
        title={t('deliveries.details')}
        width={640}
        open={selectedDelivery !== undefined}
        onClose={() => setSelectedDelivery(undefined)}
      >
        {selectedDelivery && (
          <Flex vertical gap={20}>
            <Descriptions size="small" column={1} bordered>
              <Descriptions.Item label={t('deliveries.event')}>
                {selectedDelivery.alertTitle} · #{selectedDelivery.alertId} ·{' '}
                {selectedDelivery.transition ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('deliveries.instance')}>
                {selectedDelivery.instanceId ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('deliveries.channel')}>
                {selectedDelivery.channel}
              </Descriptions.Item>
              <Descriptions.Item label={t('common.status')}>
                <Tag color={statusColors[selectedDelivery.status]}>{selectedDelivery.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('deliveries.attempts')}>
                {selectedDelivery.attemptCount}
              </Descriptions.Item>
              <Descriptions.Item label={t('deliveries.createdAt')}>
                {formatUtcDateTime(selectedDelivery.createdAt)}
              </Descriptions.Item>
              <Descriptions.Item label={t('deliveries.deliveredAt')}>
                {formatUtcDateTime(selectedDelivery.deliveredAt)}
              </Descriptions.Item>
              {selectedDelivery.nextAttemptAt && (
                <Descriptions.Item label={t('deliveries.retryAt')}>
                  {formatUtcDateTime(selectedDelivery.nextAttemptAt)}
                </Descriptions.Item>
              )}
              {selectedDelivery.lastError && (
                <Descriptions.Item label={t('deliveries.result')}>
                  <Typography.Text type="danger" style={{ overflowWrap: 'anywhere' }}>
                    {selectedDelivery.lastError}
                  </Typography.Text>
                </Descriptions.Item>
              )}
            </Descriptions>
            {selectedDelivery.status === 'FAILED' && (
              <Button
                icon={<ArrowClockwise size={18} />}
                loading={retryingIds.has(selectedDelivery.id)}
                onClick={() => void retryDelivery(selectedDelivery)}
              >
                {t('deliveries.retry')}
              </Button>
            )}
            <div>
              <Typography.Text strong>{t('deliveries.messageContent')}</Typography.Text>
              <Typography.Paragraph
                copyable={{ text: selectedDelivery.messageContent ?? '' }}
                style={{
                  marginTop: 8,
                  marginBottom: 0,
                  whiteSpace: 'pre-wrap',
                  overflowWrap: 'anywhere',
                }}
              >
                {selectedDelivery.messageContent || '-'}
              </Typography.Paragraph>
            </div>
          </Flex>
        )}
      </Drawer>
    </>
  );
};

export default NotificationDeliveriesPage;
