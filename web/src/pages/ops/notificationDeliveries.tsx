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
import { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Modal,
  Flex,
  Input,
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
import { localizeDeliveryError } from '../../utils/deliveryResult';

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
  const [searchDraft, setSearchDraft] = useState('');
  const [search, setSearch] = useState<string>();
  const [timeRange, setTimeRange] = useState<{ from?: string; to?: string }>({});
  const [instanceLoadFailed, setInstanceLoadFailed] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);
  const [instanceReloadNonce, setInstanceReloadNonce] = useState(0);
  const [selectedDelivery, setSelectedDelivery] = useState<NotificationDeliveryRecord>();
  const [retryingIds, setRetryingIds] = useState<Set<number>>(() => new Set());
  const [retryingVisible, setRetryingVisible] = useState(false);
  const retryingIdsInFlight = useRef(new Set<number>());
  const retryingVisibleInFlight = useRef(false);
  const [refreshNonce, setRefreshNonce] = useState(0);

  const refresh = () => {
    setLoading(true);
    setLoadFailed(false);
    setRefreshNonce((current) => current + 1);
  };

  const retryDelivery = async (record: NotificationDeliveryRecord) => {
    if (loading || loadFailed) return;
    if (retryingVisibleInFlight.current || retryingIdsInFlight.current.has(record.id)) return;
    retryingIdsInFlight.current.add(record.id);
    setRetryingIds((current) => new Set(current).add(record.id));
    try {
      await retryAlertDelivery(record.id);
      message.success(t('deliveries.retryQueued'));
      // The dialog reads `items` first and the snapshot only as a fallback, so the optimistic patch
      // has to cover both: without the row patch, the stale FAILED row still on the page wins until
      // the reload lands; without the snapshot patch, a row the reload drops off the page (a FAILED
      // filter stops matching it once the retry is queued) falls back to FAILED and offers a retry
      // the backend refuses.
      setItems((rows) =>
        rows.map((row) =>
          row.id === record.id
            ? { ...row, status: 'PENDING', attemptCount: 0, lastError: null }
            : row,
        ),
      );
      setSelectedDelivery((current) =>
        current?.id === record.id
          ? { ...current, status: 'PENDING', attemptCount: 0, lastError: null }
          : current,
      );
      refresh();
    } catch {
      message.error(t('deliveries.retryFailed'));
    } finally {
      retryingIdsInFlight.current.delete(record.id);
      setRetryingIds((current) => {
        const next = new Set(current);
        next.delete(record.id);
        return next;
      });
    }
  };

  const retryVisibleFailures = async () => {
    if (loading || loadFailed) return;
    const ids = items.filter((item) => item.status === 'FAILED').map((item) => item.id);
    if (ids.length === 0) return;
    if (retryingVisibleInFlight.current || ids.some((id) => retryingIdsInFlight.current.has(id)))
      return;
    retryingVisibleInFlight.current = true;
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
      retryingVisibleInFlight.current = false;
      setRetryingVisible(false);
    }
  };

  useEffect(() => {
    let cancelled = false;
    void listInstances()
      .then((loaded) => {
        if (cancelled) return;
        setInstances(loaded);
        setInstanceLoadFailed(false);
      })
      .catch(() => {
        // Swallowing this leaves an empty filter, which reads as "this deployment has no instances"
        // — a claim a failed request cannot make, and one the user has no way to retry.
        if (!cancelled) setInstanceLoadFailed(true);
      });
    return () => {
      cancelled = true;
    };
  }, [instanceReloadNonce]);

  useEffect(() => {
    let cancelled = false;
    void listAlertDeliveriesPage({
      channel,
      status,
      instanceId,
      search,
      ...timeRange,
      page,
      pageSize,
    })
      .then((result) => {
        if (cancelled) return;
        setItems(result.items);
        setTotal(result.total);
        setLoadFailed(false);
      })
      .catch(() => {
        if (cancelled) return;
        setItems([]);
        setTotal(0);
        setSelectedDelivery(undefined);
        setLoadFailed(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [channel, status, instanceId, search, timeRange, page, pageSize, refreshNonce, t]);

  // The dialog shows the freshest row it can see. A bulk retry — or another operator — reloads the
  // page, and a snapshot taken when the dialog opened would keep rendering FAILED and offer a retry
  // the backend refuses ("Only failed notification deliveries can be retried"). The snapshot stays
  // as the fallback for a row the current page no longer carries.
  const currentDelivery = selectedDelivery
    ? (items.find((item) => item.id === selectedDelivery.id) ?? selectedDelivery)
    : undefined;

  const resetPage = (change: () => void) => {
    setLoading(true);
    setLoadFailed(false);
    setSelectedDelivery(undefined);
    change();
    setPage(1);
  };

  const applySearch = (value: string) => {
    const normalized = value.trim() || undefined;
    if (normalized !== search) resetPage(() => setSearch(normalized));
  };

  const columns: ColumnsType<NotificationDeliveryRecord> = [
    {
      title: t('deliveries.alert'),
      dataIndex: 'alertTitle',
      // 唯一可伸展列：容器比表宽时余量集中在此，其余列保持声明宽度
      minWidth: 300,
      // One line per row (project row-density rule): the alert id and transition share the
      // title's line as a bounded secondary segment instead of stacking a second line.
      render: (title, record) => (
        <Flex align="center" gap={6} style={{ minWidth: 0 }}>
          <Typography.Text ellipsis={{ tooltip: title }} style={{ flex: '1 1 auto', minWidth: 0 }}>
            {title}
          </Typography.Text>
          <Typography.Text
            type="secondary"
            ellipsis={{ tooltip: `#${record.alertId} · ${record.transition ?? '-'}` }}
            style={{ flex: '0 1 150px', minWidth: 0 }}
          >
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
          <Typography.Text
            type="danger"
            ellipsis={{ tooltip: localizeDeliveryError(record.lastError, t) }}
          >
            {localizeDeliveryError(record.lastError, t)}
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
            disabled={loading || loadFailed}
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
                disabled={loading || loadFailed}
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
              disabled={loading || loadFailed || !items.some((item) => item.status === 'FAILED')}
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
              status={instanceLoadFailed ? 'error' : undefined}
              style={{ width: 280, flex: '1 1 280px' }}
              options={instances.map((instance) => ({
                value: instance.name,
                label: instance.name,
              }))}
              onChange={(value) => resetPage(() => setInstanceId(value))}
            />
            <Input.Search
              aria-label={t('deliveries.search')}
              placeholder={t('deliveries.search')}
              allowClear
              value={searchDraft}
              style={{ width: 280, flex: '1 1 280px' }}
              onChange={(event) => {
                setSearchDraft(event.target.value);
                if (!event.target.value) applySearch('');
              }}
              onBlur={() => applySearch(searchDraft)}
              onSearch={applySearch}
            />
            <DatePicker.RangePicker
              aria-label={t('deliveries.timeRange')}
              showTime
              format="YYYY-MM-DD HH:mm:ss"
              placeholder={[t('deliveries.startTime'), t('deliveries.endTime')]}
              style={{ width: 380, flex: '1 1 380px' }}
              onChange={(dates) =>
                resetPage(() =>
                  setTimeRange(
                    dates?.[0] && dates[1]
                      ? {
                          from: dates[0].toDate().toISOString().replace('Z', ''),
                          to: dates[1].toDate().toISOString().replace('Z', ''),
                        }
                      : {},
                  ),
                )
              }
            />
            {instanceLoadFailed && (
              <Tooltip title={t('deliveries.instancesLoadFailed')}>
                <Button
                  size="small"
                  icon={<ArrowClockwise size={16} />}
                  onClick={() => setInstanceReloadNonce((nonce) => nonce + 1)}
                >
                  {t('common.retry')}
                </Button>
              </Tooltip>
            )}
          </Flex>
          {loadFailed && (
            <Alert
              type="error"
              showIcon
              message={t('deliveries.loadFailed')}
              action={<Button onClick={refresh}>{t('common.retry')}</Button>}
              style={{ marginBottom: 16 }}
            />
          )}
          <Table
            rowKey="id"
            size="small"
            columns={columns}
            dataSource={items}
            loading={loading}
            tableLayout="fixed"
            scroll={{ x: tableScrollX(columns) }}
            pagination={{
              current: page,
              pageSize,
              total,
              showSizeChanger: true,
              showTotal: (count) => `${t('common.total')} ${count}`,
              onChange: (nextPage, nextPageSize) => {
                setLoading(true);
                setLoadFailed(false);
                setPage(nextPage);
                setPageSize(nextPageSize);
              },
            }}
          />
        </Card>
      </div>
      <Modal
        title={t('deliveries.details')}
        width="min(640px, calc(100vw - 32px))"
        open={currentDelivery !== undefined}
        onCancel={() => setSelectedDelivery(undefined)}
        footer={null}
        destroyOnHidden
        styles={{
          body: { maxHeight: 'calc(100vh - 240px)', overflowY: 'auto', overflowX: 'hidden' },
        }}
      >
        {currentDelivery && (
          <Flex vertical gap={20}>
            <Descriptions size="small" column={1} bordered>
              <Descriptions.Item label={t('deliveries.event')}>
                {currentDelivery.alertTitle} · #{currentDelivery.alertId} ·{' '}
                {currentDelivery.transition ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('deliveries.instance')}>
                {currentDelivery.instanceId ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('deliveries.channel')}>
                {currentDelivery.channel}
              </Descriptions.Item>
              <Descriptions.Item label={t('common.status')}>
                <Tag color={statusColors[currentDelivery.status]}>{currentDelivery.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('deliveries.attempts')}>
                {currentDelivery.attemptCount}
              </Descriptions.Item>
              <Descriptions.Item label={t('deliveries.createdAt')}>
                {formatUtcDateTime(currentDelivery.createdAt)}
              </Descriptions.Item>
              <Descriptions.Item label={t('deliveries.deliveredAt')}>
                {formatUtcDateTime(currentDelivery.deliveredAt)}
              </Descriptions.Item>
              {currentDelivery.nextAttemptAt && (
                <Descriptions.Item label={t('deliveries.retryAt')}>
                  {formatUtcDateTime(currentDelivery.nextAttemptAt)}
                </Descriptions.Item>
              )}
              {currentDelivery.lastError && (
                <Descriptions.Item label={t('deliveries.result')}>
                  <Typography.Text type="danger" style={{ overflowWrap: 'anywhere' }}>
                    {localizeDeliveryError(currentDelivery.lastError, t)}
                  </Typography.Text>
                </Descriptions.Item>
              )}
            </Descriptions>
            {currentDelivery.status === 'FAILED' && (
              <Button
                icon={<ArrowClockwise size={18} />}
                loading={retryingIds.has(currentDelivery.id)}
                disabled={loading || loadFailed}
                onClick={() => void retryDelivery(currentDelivery)}
              >
                {t('deliveries.retry')}
              </Button>
            )}
            <div>
              <Typography.Text strong>{t('deliveries.messageContent')}</Typography.Text>
              <Typography.Paragraph
                copyable={{ text: currentDelivery.messageContent ?? '' }}
                style={{
                  marginTop: 8,
                  marginBottom: 0,
                  whiteSpace: 'pre-wrap',
                  overflowWrap: 'anywhere',
                }}
              >
                {currentDelivery.messageContent || '-'}
              </Typography.Paragraph>
            </div>
          </Flex>
        )}
      </Modal>
    </>
  );
};

export default NotificationDeliveriesPage;
