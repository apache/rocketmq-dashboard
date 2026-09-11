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
  Modal,
  Popconfirm,
  Statistic,
  Table,
  Tabs,
  Tag,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  getQueryHistorySummary,
  clearQueryHistory,
  deleteMessageQueryHistory,
  deleteTraceQueryHistory,
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
  const [deleteError, setDeleteError] = useState('');
  const [deletingKey, setDeletingKey] = useState<string>();
  const [clearModalOpen, setClearModalOpen] = useState(false);
  const [clearing, setClearing] = useState(false);
  const [summary, setSummary] = useState<QueryHistorySummary>();
  const [messageRows, setMessageRows] = useState<MessageQueryHistory[]>([]);
  const [traceRows, setTraceRows] = useState<TraceQueryHistory[]>([]);
  const [total, setTotal] = useState(0);
  const requestId = useRef(0);

  const load = useCallback(async () => {
    if (!open) return;
    const id = ++requestId.current;
    setLoading(true);
    setError('');
    setDeleteError('');
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

  const deleteMessage = async (id: number) => {
    setDeletingKey(`message-${id}`);
    setDeleteError('');
    try {
      await deleteMessageQueryHistory(id);
      if (messageRows.length === 1 && page > 1) setPage((current) => current - 1);
      else await load();
    } catch (deleteLoadError) {
      setDeleteError(
        deleteLoadError instanceof Error ? deleteLoadError.message : '删除查询历史失败',
      );
    } finally {
      setDeletingKey(undefined);
    }
  };

  const deleteTrace = async (id: number) => {
    setDeletingKey(`trace-${id}`);
    setDeleteError('');
    try {
      await deleteTraceQueryHistory(id);
      if (traceRows.length === 1 && page > 1) setPage((current) => current - 1);
      else await load();
    } catch (deleteLoadError) {
      setDeleteError(
        deleteLoadError instanceof Error ? deleteLoadError.message : '删除查询历史失败',
      );
    } finally {
      setDeletingKey(undefined);
    }
  };

  const clearHistory = async () => {
    setClearing(true);
    setDeleteError('');
    try {
      await clearQueryHistory(clusterId);
      setClearModalOpen(false);
      if (page !== 1) setPage(1);
      else await load();
    } catch (clearLoadError) {
      setDeleteError(clearLoadError instanceof Error ? clearLoadError.message : '清空查询历史失败');
    } finally {
      setClearing(false);
    }
  };

  useEffect(() => {
    // Loading is asynchronous; state updates happen after the history API resolves.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
    return () => {
      requestId.current += 1;
    };
  }, [load]);

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
    {
      title: t('common.actions'),
      key: 'actions',
      width: 90,
      render: (_: unknown, row) => (
        <Popconfirm
          title={t('messageHistory.deleteConfirm')}
          okText={t('common.delete')}
          cancelText={t('common.cancel')}
          onConfirm={() => void deleteMessage(row.id)}
        >
          <Button
            danger
            type="link"
            size="small"
            loading={deletingKey === `message-${row.id}`}
            onClick={(event) => event.stopPropagation()}
          >
            {t('common.delete')}
          </Button>
        </Popconfirm>
      ),
    },
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
    {
      title: t('common.actions'),
      key: 'actions',
      width: 90,
      render: (_: unknown, row) => (
        <Popconfirm
          title={t('messageHistory.deleteConfirm')}
          okText={t('common.delete')}
          cancelText={t('common.cancel')}
          onConfirm={() => void deleteTrace(row.id)}
        >
          <Button
            danger
            type="link"
            size="small"
            loading={deletingKey === `trace-${row.id}`}
            onClick={(event) => event.stopPropagation()}
          >
            {t('common.delete')}
          </Button>
        </Popconfirm>
      ),
    },
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
        <Button
          danger
          onClick={() => setClearModalOpen(true)}
          disabled={!summary?.messageQueries && !summary?.traceQueries}
        >
          {t('messageHistory.clearHistory')}
        </Button>
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
      {deleteError && (
        <Alert
          type="error"
          showIcon
          message="查询历史操作失败"
          description={deleteError}
          closable
          onClose={() => setDeleteError('')}
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
      <Modal
        title="清空查询历史"
        open={clearModalOpen}
        okText="清空"
        cancelText="取消"
        okButtonProps={{ danger: true, loading: clearing }}
        onOk={() => void clearHistory()}
        onCancel={() => setClearModalOpen(false)}
      >
        <p>
          {clusterId
            ? '这会删除当前操作员在所选集群下的消息查询和轨迹查询历史，操作不可撤销。'
            : '这会删除当前操作员的全部消息查询和轨迹查询历史，操作不可撤销。'}
        </p>
      </Modal>
    </Drawer>
  );
};

export default MessageQueryHistoryDrawer;
