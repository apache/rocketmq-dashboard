/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Drawer,
  Flex,
  Input,
  Popconfirm,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  clearMessageQueryHistory,
  clearTraceQueryHistory,
  deleteMessageQueryHistory,
  deleteTraceQueryHistory,
  getQueryHistorySummary,
  listMessageQueryHistory,
  listTraceQueryHistory,
  type MessageQueryHistory,
  type QueryHistorySummary,
  type TraceQueryHistory,
} from '../api/messageHistory';
import { useLang } from '../i18n/LangContext';

interface Props {
  open: boolean;
  clusterId?: string;
  onClose: () => void;
  onSelectMessage?: (record: MessageQueryHistory) => void;
  onSelectTrace?: (record: TraceQueryHistory) => void;
}

const PAGE_SIZE = 20;
const formatTime = (value?: string) => {
  if (!value) return '-';
  const timestamp = new Date(value);
  return Number.isNaN(timestamp.getTime()) ? '-' : timestamp.toLocaleString();
};

const MessageQueryHistoryDrawer = ({
  open,
  clusterId,
  onClose,
  onSelectMessage,
  onSelectTrace,
}: Props) => {
  const { t } = useLang();
  const [tab, setTab] = useState<'messages' | 'traces'>('messages');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [summary, setSummary] = useState<QueryHistorySummary>();
  const [messageRows, setMessageRows] = useState<MessageQueryHistory[]>([]);
  const [traceRows, setTraceRows] = useState<TraceQueryHistory[]>([]);
  const [total, setTotal] = useState(0);
  const [removing, setRemoving] = useState(false);
  const requestId = useRef(0);

  const load = useCallback(async () => {
    if (!open) return;
    const id = ++requestId.current;
    setLoading(true);
    setError('');
    setSummary(undefined);
    setMessageRows([]);
    setTraceRows([]);
    setTotal(0);
    try {
      const [nextSummary, result] = await Promise.all([
        getQueryHistorySummary(clusterId),
        tab === 'messages'
          ? listMessageQueryHistory({
              clusterId,
              search: search || undefined,
              page,
              pageSize: PAGE_SIZE,
            })
          : listTraceQueryHistory({
              clusterId,
              search: search || undefined,
              page,
              pageSize: PAGE_SIZE,
            }),
      ]);
      if (id !== requestId.current) return;
      setSummary(nextSummary);
      setTotal(result.total);
      if (tab === 'messages') setMessageRows(result.items as MessageQueryHistory[]);
      else setTraceRows(result.items as TraceQueryHistory[]);
    } catch (loadError) {
      if (id === requestId.current) {
        setError(loadError instanceof Error ? loadError.message : t('messageHistory.loadFailed'));
      }
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }, [clusterId, open, page, search, t, tab]);

  useEffect(() => {
    // Loading is asynchronous; state updates happen after the history API resolves.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
    return () => {
      requestId.current += 1;
    };
  }, [load]);

  const handleDeleteRow = async (row: { id: number }) => {
    setRemoving(true);
    try {
      if (tab === 'messages') {
        await deleteMessageQueryHistory(row.id);
      } else {
        await deleteTraceQueryHistory(row.id);
      }
      message.success(t('messageHistory.deleted'));
    } catch (deleteError) {
      message.error(deleteError instanceof Error ? deleteError.message : t('messageHistory.deleteFailed'));
    } finally {
      setRemoving(false);
    }
    void load();
  };

  const handleClear = async () => {
    setRemoving(true);
    try {
      if (tab === 'messages') {
        await clearMessageQueryHistory(clusterId);
      } else {
        await clearTraceQueryHistory(clusterId);
      }
      message.success(t('messageHistory.cleared'));
    } catch (clearError) {
      message.error(clearError instanceof Error ? clearError.message : t('messageHistory.clearFailed'));
    } finally {
      setRemoving(false);
    }
    void load();
  };

  const actionColumn = <T extends { id: number }>(
    renderDelete: (record: T) => React.ReactNode,
  ): ColumnsType<T>[number] => ({
    title: '操作',
    key: 'actions',
    width: 90,
    render: (_, record) => renderDelete(record),
  });

  const messageColumns: ColumnsType<MessageQueryHistory> = [
    {
      title: t('common.type'),
      dataIndex: 'queryType',
      width: 90,
      render: (value) => <Tag>{value}</Tag>,
    },
    { title: 'Topic', dataIndex: 'topic', ellipsis: true },
    { title: 'Message ID / Key', render: (_, row) => row.msgId || row.messageKey || '-' },
    { title: t('messageHistory.resultCount'), dataIndex: 'resultCount', width: 80 },
    { title: t('messageHistory.operator'), dataIndex: 'queriedBy', width: 110 },
    {
      title: t('messageHistory.queryTime'),
      dataIndex: 'queriedAt',
      width: 180,
      render: formatTime,
    },
    actionColumn((record) => (
      <Popconfirm
        title={t('messageHistory.deleteConfirm')}
        okText={t('common.delete')}
        cancelText={t('common.cancel')}
        okButtonProps={{ danger: true }}
        onConfirm={() => void handleDeleteRow(record)}
      >
        <Button type="link" size="small" danger loading={removing}>
          {t('common.delete')}
        </Button>
      </Popconfirm>
    )),
  ];
  const traceColumns: ColumnsType<TraceQueryHistory> = [
    { title: 'Message ID', dataIndex: 'msgId', ellipsis: true },
    { title: 'Topic', dataIndex: 'topic', ellipsis: true },
    {
      title: t('messageHistory.traceTopic'),
      dataIndex: 'traceTopic',
      ellipsis: true,
      render: (value?: string) => value?.trim() || t('common.default'),
    },
    { title: t('messageHistory.traceNodes'), dataIndex: 'nodeCount', width: 90 },
    { title: t('messageHistory.consumers'), dataIndex: 'consumerCount', width: 90 },
    { title: t('messageHistory.operator'), dataIndex: 'queriedBy', width: 110 },
    {
      title: t('messageHistory.queryTime'),
      dataIndex: 'queriedAt',
      width: 180,
      render: formatTime,
    },
    actionColumn((record) => (
      <Popconfirm
        title={t('messageHistory.deleteConfirm')}
        okText={t('common.delete')}
        cancelText={t('common.cancel')}
        okButtonProps={{ danger: true }}
        onConfirm={() => void handleDeleteRow(record)}
      >
        <Button type="link" size="small" danger loading={removing}>
          {t('common.delete')}
        </Button>
      </Popconfirm>
    )),
  ];

  return (
    <Drawer
      title={t('messageHistory.title')}
      width={900}
      open={open}
      onClose={onClose}
      destroyOnHidden
    >
      <Flex justify="space-between" align="center" style={{ marginBottom: 16 }}>
        <Flex gap={32}>
          <Statistic
            title={t('messageHistory.messageQueries')}
            value={summary?.messageQueries ?? 0}
          />
          <Statistic title={t('messageHistory.traceQueries')} value={summary?.traceQueries ?? 0} />
          <Statistic
            title={t('messageHistory.latestQuery')}
            value={formatTime(summary?.latestQueryAt)}
          />
        </Flex>
        <Space>
          <Popconfirm
            title={
              tab === 'messages'
                ? t('messageHistory.clearMessagesConfirm')
                : t('messageHistory.clearTracesConfirm')
            }
            description={t('messageHistory.clearIrreversible')}
            okText={t('messageHistory.clear')}
            cancelText={t('common.cancel')}
            okButtonProps={{ danger: true }}
            onConfirm={() => void handleClear()}
          >
            <Button danger loading={removing}>
              {tab === 'messages'
                ? t('messageHistory.clearMessages')
                : t('messageHistory.clearTraces')}
            </Button>
          </Popconfirm>
        </Space>
      </Flex>
      <Input.Search
        allowClear
        placeholder={t('messageHistory.searchPlaceholder')}
        onSearch={(value) => {
          setPage(1);
          setSearch(value.trim());
        }}
        style={{ marginBottom: 12, width: 420 }}
      />
      {error && (
        <Alert
          type="error"
          showIcon
          message={t('messageHistory.loadFailed')}
          description={error}
          action={
            <Button size="small" onClick={() => void load()}>
              {t('common.retry')}
            </Button>
          }
          style={{ marginBottom: 12 }}
        />
      )}
      <Tabs
        activeKey={tab}
        onChange={(key) => {
          setPage(1);
          setTab(key as 'messages' | 'traces');
        }}
        items={[
          {
            key: 'messages',
            label: t('messageHistory.messageQueries'),
            children: (
              <Table
                rowKey="id"
                loading={loading}
                columns={messageColumns}
                dataSource={messageRows}
                pagination={{ current: page, pageSize: PAGE_SIZE, total, onChange: setPage }}
                onRow={
                  onSelectMessage
                    ? (record) => ({
                        onClick: () => onSelectMessage(record),
                        style: { cursor: 'pointer' },
                      })
                    : undefined
                }
              />
            ),
          },
          {
            key: 'traces',
            label: t('messageHistory.traceQueries'),
            children: (
              <Table
                rowKey="id"
                loading={loading}
                columns={traceColumns}
                dataSource={traceRows}
                pagination={{ current: page, pageSize: PAGE_SIZE, total, onChange: setPage }}
                onRow={
                  onSelectTrace
                    ? (record) => ({
                        onClick: () => onSelectTrace(record),
                        style: { cursor: 'pointer' },
                      })
                    : undefined
                }
              />
            ),
          },
        ]}
      />
    </Drawer>
  );
};

export default MessageQueryHistoryDrawer;
