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
import { useCallback, useState } from 'react';
import {
  Button,
  Card,
  Descriptions,
  Empty,
  Flex,
  Select,
  Slider,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { CloseOutlined, SearchOutlined } from '@ant-design/icons';
import type { MessageRecord, QueueOffset } from '../api/message';
import { getQueueOffsets, pullMessageAtOffset } from '../api/message';

const { Text, Paragraph } = Typography;

export interface TopicOption {
  label: string;
  value: string;
}

const formatTimeMs = (value: number | string) => {
  const ts = typeof value === 'string' ? new Date(value).getTime() : value;
  if (!ts || Number.isNaN(ts)) return '-';
  return new Date(ts).toLocaleString('zh-CN', { hour12: false });
};

export interface PulledEntry {
  key: string;
  offset: number;
  message: MessageRecord | null;
}

export const useQueueBrowser = (instanceId?: string) => {
  const [topic, setTopic] = useState<string | undefined>();
  const [queues, setQueues] = useState<QueueOffset[]>([]);
  const [loading, setLoading] = useState(false);
  const [offsets, setOffsets] = useState<Record<string, number>>({});
  const [pulling, setPulling] = useState<string | null>(null);
  const [entries, setEntries] = useState<PulledEntry[]>([]);

  const loadQueues = useCallback(async () => {
    if (!instanceId || !topic) return;
    setLoading(true);
    setQueues([]);
    setOffsets({});
    setEntries([]);
    try {
      const result = await getQueueOffsets({ instanceId, topic });
      setQueues(result);
      const initial: Record<string, number> = {};
      for (const q of result) {
        initial[`${q.brokerName}-${q.queueId}`] =
          q.maxOffset > q.minOffset ? q.maxOffset - 1 : q.minOffset;
      }
      setOffsets(initial);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '加载队列信息失败');
    } finally {
      setLoading(false);
    }
  }, [instanceId, topic]);

  const handlePull = async (queue: QueueOffset) => {
    if (!instanceId || !topic) return;
    const key = `${queue.brokerName}-${queue.queueId}`;
    const offset = offsets[key] ?? queue.minOffset;
    setPulling(key);
    try {
      const msg = await pullMessageAtOffset({
        instanceId,
        topic,
        brokerName: queue.brokerName,
        queueId: queue.queueId,
        offset,
      });
      setEntries((prev) => [
        ...prev.filter((entry) => entry.key !== key),
        { key, offset, message: msg },
      ]);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '拉取消息失败');
    } finally {
      setPulling(null);
    }
  };

  const closeEntry = (key: string) => {
    setEntries((prev) => prev.filter((entry) => entry.key !== key));
  };

  return {
    topic,
    setTopic,
    queues,
    loading,
    offsets,
    setOffsets,
    pulling,
    entries,
    loadQueues,
    handlePull,
    closeEntry,
  };
};

export type QueueBrowserState = ReturnType<typeof useQueueBrowser>;

interface ControlsProps {
  instanceId?: string;
  state: QueueBrowserState;
  topicOptions: TopicOption[];
  topicLoading?: boolean;
}

export const QueueBrowserControls = ({
  instanceId,
  state,
  topicOptions,
  topicLoading,
}: ControlsProps) => (
  <Flex gap={12} align="center">
    <Select
      showSearch
      allowClear
      placeholder="选择 Topic"
      value={state.topic}
      onChange={state.setTopic}
      options={topicOptions}
      loading={topicLoading}
      style={{ width: 280 }}
    />
    <Button
      type="primary"
      icon={<SearchOutlined />}
      disabled={!instanceId || !state.topic}
      loading={state.loading}
      onClick={() => void state.loadQueues()}
    >
      加载队列
    </Button>
  </Flex>
);

export const QueueBrowserResults = ({ state }: { state: QueueBrowserState }) => (
  <Card>
    {state.loading ? (
      <Flex justify="center" style={{ padding: 32 }}>
        <Spin />
      </Flex>
    ) : state.queues.length === 0 ? (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description="选择 Topic 并点击「加载队列」，按队列浏览消息"
        style={{ padding: '32px 0' }}
      />
    ) : (
      <Flex gap={16} align="flex-start">
        {/* 左侧：队列表格 */}
        <div style={{ width: '50%', flexShrink: 0 }}>
          <Table<QueueOffset>
            rowKey={(r) => `${r.brokerName}-${r.queueId}`}
            dataSource={state.queues}
            size="small"
            pagination={false}
            columns={[
              {
                title: 'Broker',
                dataIndex: 'brokerName',
                width: 180,
                ellipsis: { showTitle: false },
                render: (v: string) => (
                  <Tooltip title={v}>
                    <Text strong style={{ fontSize: 14 }}>
                      {v}
                    </Text>
                  </Tooltip>
                ),
              },
              {
                title: 'Queue',
                dataIndex: 'queueId',
                width: 50,
                align: 'center',
                render: (v: number) => <Text style={{ fontSize: 14 }}>{v}</Text>,
              },
              {
                title: 'Offset 范围',
                key: 'offset',
                width: 170,
                render: (_: unknown, record: QueueOffset) => {
                  const key = `${record.brokerName}-${record.queueId}`;
                  const currentOffset = state.offsets[key] ?? record.minOffset;
                  return (
                    <Flex align="center" gap={8}>
                      <Text type="secondary" style={{ fontSize: 14, flexShrink: 0 }}>
                        {record.minOffset}
                      </Text>
                      <Slider
                        style={{ flex: 1, margin: 0 }}
                        min={record.minOffset}
                        max={
                          record.maxOffset > record.minOffset
                            ? record.maxOffset - 1
                            : record.minOffset
                        }
                        value={currentOffset}
                        onChange={(value) =>
                          state.setOffsets((prev) => ({ ...prev, [key]: value }))
                        }
                        tooltip={{ formatter: (v) => `offset: ${v}` }}
                      />
                      <Text code style={{ fontSize: 14, flexShrink: 0 }}>
                        {currentOffset}
                      </Text>
                    </Flex>
                  );
                },
              },
              {
                title: '操作',
                key: 'action',
                width: 70,
                align: 'center',
                render: (_: unknown, record: QueueOffset) => {
                  const key = `${record.brokerName}-${record.queueId}`;
                  return (
                    <Button
                      size="small"
                      type="primary"
                      loading={state.pulling === key}
                      onClick={() => void state.handlePull(record)}
                    >
                      查看
                    </Button>
                  );
                },
              },
            ]}
          />
          <Text type="secondary" style={{ display: 'block', marginTop: 8, fontSize: 14 }}>
            共 {state.queues.length} 个队列，总消息量{' '}
            {state.queues.reduce((sum, q) => sum + (q.maxOffset - q.minOffset), 0)} 条
          </Text>
        </div>

        {/* 右侧：消息详情（2 列，可多条并存） */}
        <div style={{ flex: 1, minWidth: 0 }}>
          {state.entries.length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="点击左侧「查看」，消息详情将显示在这里"
              style={{ padding: '32px 0' }}
            />
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              {state.entries.map((entry) => (
                <Card
                  key={entry.key}
                  size="small"
                  style={{ borderRadius: 8 }}
                  title={
                    <Space size={4}>
                      <Tag color="blue" style={{ marginInlineEnd: 0 }}>
                        {entry.key}
                      </Tag>
                      <Text type="secondary" style={{ fontSize: 14 }}>
                        @ {entry.offset}
                      </Text>
                    </Space>
                  }
                  extra={
                    <Button
                      type="text"
                      size="small"
                      icon={<CloseOutlined />}
                      onClick={() => state.closeEntry(entry.key)}
                    />
                  }
                >
                  {entry.message ? (
                    <>
                      <Descriptions column={1} size="small">
                        <Descriptions.Item label="Message ID">
                          <Paragraph
                            copyable
                            style={{ marginBottom: 0, fontFamily: 'monospace', fontSize: 14 }}
                          >
                            {entry.message.msgId}
                          </Paragraph>
                        </Descriptions.Item>
                        <Descriptions.Item label="Tag">
                          <Tag>{entry.message.tag || '-'}</Tag>
                        </Descriptions.Item>
                        <Descriptions.Item label="Key">
                          <span style={{ fontFamily: 'monospace', fontSize: 14 }}>
                            {entry.message.key || '-'}
                          </span>
                        </Descriptions.Item>
                        <Descriptions.Item label="存储时间">
                          <span style={{ fontFamily: 'monospace', fontSize: 14 }}>
                            {formatTimeMs(entry.message.storeTime)}
                          </span>
                        </Descriptions.Item>
                        <Descriptions.Item label="大小">
                          {entry.message.size} bytes
                        </Descriptions.Item>
                        <Descriptions.Item label="Born Host">
                          {entry.message.bornHost || '-'}
                        </Descriptions.Item>
                      </Descriptions>
                      {entry.message.body && (
                        <Card size="small" title="Body" style={{ marginTop: 12 }}>
                          <pre
                            style={{
                              maxHeight: 160,
                              overflow: 'auto',
                              fontSize: 14,
                              fontFamily: 'monospace',
                              whiteSpace: 'pre-wrap',
                              wordBreak: 'break-all',
                              margin: 0,
                            }}
                          >
                            {entry.message.body}
                          </pre>
                        </Card>
                      )}
                    </>
                  ) : (
                    <Text type="secondary">该 offset 处无消息</Text>
                  )}
                </Card>
              ))}
            </div>
          )}
        </div>
      </Flex>
    )}
  </Card>
);
