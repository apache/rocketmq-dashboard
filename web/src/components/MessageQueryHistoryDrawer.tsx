/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Drawer, Flex, Input, Statistic, Table, Tabs, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  getQueryHistorySummary,
  listMessageQueryHistory,
  listTraceQueryHistory,
  type MessageQueryHistory,
  type QueryHistorySummary,
  type TraceQueryHistory,
} from '../api/messageHistory';

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
  const [tab, setTab] = useState<'messages' | 'traces'>('messages');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
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
        setError(loadError instanceof Error ? loadError.message : '查询历史加载失败');
      }
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }, [clusterId, open, page, search, tab]);

  useEffect(() => {
    // Loading is asynchronous; state updates happen after the history API resolves.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
    return () => {
      requestId.current += 1;
    };
  }, [load]);

  const messageColumns: ColumnsType<MessageQueryHistory> = [
    { title: '类型', dataIndex: 'queryType', width: 90, render: (value) => <Tag>{value}</Tag> },
    { title: 'Topic', dataIndex: 'topic', ellipsis: true },
    { title: 'Message ID / Key', render: (_, row) => row.msgId || row.messageKey || '-' },
    { title: '结果数', dataIndex: 'resultCount', width: 80 },
    { title: '操作者', dataIndex: 'queriedBy', width: 110 },
    { title: '查询时间', dataIndex: 'queriedAt', width: 180, render: formatTime },
  ];
  const traceColumns: ColumnsType<TraceQueryHistory> = [
    { title: 'Message ID', dataIndex: 'msgId', ellipsis: true },
    { title: 'Topic', dataIndex: 'topic', ellipsis: true },
    {
      title: '轨迹 Topic',
      dataIndex: 'traceTopic',
      ellipsis: true,
      render: (value?: string) => value?.trim() || '默认',
    },
    { title: '轨迹节点', dataIndex: 'nodeCount', width: 90 },
    { title: '消费者', dataIndex: 'consumerCount', width: 90 },
    { title: '操作者', dataIndex: 'queriedBy', width: 110 },
    { title: '查询时间', dataIndex: 'queriedAt', width: 180, render: formatTime },
  ];

  return (
    <Drawer title="服务端查询历史" width={900} open={open} onClose={onClose} destroyOnHidden>
      <Flex gap={32} style={{ marginBottom: 16 }}>
        <Statistic title="消息查询" value={summary?.messageQueries ?? 0} />
        <Statistic title="轨迹查询" value={summary?.traceQueries ?? 0} />
        <Statistic title="最近查询" value={formatTime(summary?.latestQueryAt)} />
      </Flex>
      <Input.Search
        allowClear
        placeholder="搜索 Topic、轨迹 Topic、Message ID、Key 或操作者"
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
          message="查询历史加载失败"
          description={error}
          action={
            <Button size="small" onClick={() => void load()}>
              重试
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
            label: '消息查询',
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
            label: '轨迹查询',
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
