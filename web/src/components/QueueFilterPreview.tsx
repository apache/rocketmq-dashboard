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
  Descriptions,
  Drawer,
  Flex,
  Input,
  InputNumber,
  Select,
  Table,
  Typography,
} from 'antd';
import { previewQueueFilter } from '../api/message';
import type { QueueFilterPage, QueueOffset, MessageRecord } from '../api/message';

interface Props {
  instanceId: string;
  topic: string;
  queue: QueueOffset;
  initialOffset: number;
  onClose: () => void;
}

export default function QueueFilterPreview({
  instanceId,
  topic,
  queue,
  initialOffset,
  onClose,
}: Props) {
  const [offset, setOffset] = useState<number | null>(initialOffset);
  const [expressionType, setExpressionType] = useState<'TAG' | 'SQL92'>('TAG');
  const [expression, setExpression] = useState('*');
  const [result, setResult] = useState<QueueFilterPage | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const activeRequest = useRef<AbortController | null>(null);

  useEffect(() => {
    const request = activeRequest;
    return () => request.current?.abort();
  }, []);

  const resetResult = () => {
    setResult(null);
    setError('');
  };
  const scan = async (start: number | null) => {
    if (start === null || !expression.trim() || activeRequest.current) return;
    const controller = new AbortController();
    activeRequest.current = controller;
    setLoading(true);
    setError('');
    try {
      const page = await previewQueueFilter(
        {
          instanceId,
          topic,
          brokerName: queue.brokerName,
          queueId: queue.queueId,
          offset: start,
          expressionType,
          expression,
        },
        controller.signal,
      );
      if (!controller.signal.aborted) setResult(page);
    } catch (reason) {
      if (!controller.signal.aborted)
        setError(reason instanceof Error ? reason.message : 'Filter preview failed');
    } finally {
      activeRequest.current = null;
      if (!controller.signal.aborted) setLoading(false);
    }
  };

  return (
    <Drawer
      open
      title={`Filter preview: ${queue.brokerName} / ${queue.queueId}`}
      width={860}
      onClose={onClose}
    >
      <Flex vertical gap={16}>
        <Alert
          type="info"
          showIcon
          message="Inspect broker-filtered messages without changing consumer offsets"
          description="Each request reads one batch of up to 20 matching messages. Continue explicitly when more offsets remain. SQL92 requires broker property filtering support."
        />
        <Descriptions
          size="small"
          column={1}
          items={[{ key: 'topic', label: 'Topic', children: topic }]}
        />
        <Flex gap={12} wrap align="center">
          <Select
            aria-label="Expression type"
            value={expressionType}
            disabled={loading}
            style={{ width: 130 }}
            options={[
              { value: 'TAG', label: 'Tag' },
              { value: 'SQL92', label: 'SQL92' },
            ]}
            onChange={(value) => {
              setExpressionType(value);
              setExpression('');
              resetResult();
            }}
          />
          <InputNumber
            aria-label="Starting offset"
            min={0}
            precision={0}
            value={offset}
            disabled={loading}
            style={{ width: 210 }}
            onChange={(value) => {
              setOffset(value);
              resetResult();
            }}
          />
        </Flex>
        <Input.TextArea
          aria-label="Filter expression"
          rows={3}
          value={expression}
          maxLength={4096}
          disabled={loading}
          placeholder={
            expressionType === 'TAG' ? 'TagA || TagB' : "amount > 100 AND region = 'east'"
          }
          onChange={(event) => {
            setExpression(event.target.value);
            resetResult();
          }}
        />
        <Flex gap={8}>
          <Button
            type="primary"
            aria-label="Preview from offset"
            loading={loading}
            disabled={offset === null || !expression.trim()}
            onClick={() => void scan(offset)}
          >
            Preview from offset
          </Button>
          <Button
            disabled={loading || !result?.hasMore}
            onClick={() => result && void scan(result.nextOffset)}
          >
            Next batch
          </Button>
        </Flex>
        {error && <Alert type="error" showIcon message={error} />}
        {result && (
          <>
            {result.offsetAdjusted && (
              <Alert
                type="warning"
                showIcon
                message="Queue bounds changed or the requested offset is outside retention."
                description={`The broker returned continuation offset ${result.nextOffset}. Inspect the range before continuing.`}
              />
            )}
            <Typography.Text type="secondary">
              {`Read from ${result.startOffset}; next offset ${result.nextOffset}; retained range [${result.minOffset}, ${result.maxOffset}).`}
            </Typography.Text>
            <Table<MessageRecord>
              dataSource={result.items}
              rowKey={(item) => `${item.queueOffset}:${item.msgId}`}
              size="small"
              pagination={false}
              scroll={{ x: 650 }}
              locale={{
                emptyText: result.hasMore
                  ? 'No matches in this batch. Continue to inspect later offsets.'
                  : 'No matching messages; the queue scan has reached its end.',
              }}
              columns={[
                { title: 'Offset', dataIndex: 'queueOffset', width: 110 },
                { title: 'Message ID', dataIndex: 'msgId', ellipsis: true },
                { title: 'Tag', dataIndex: 'tag', width: 140 },
                {
                  title: 'Stored at',
                  dataIndex: 'storeTime',
                  width: 190,
                  render: (value: string | number) => new Date(value).toLocaleString(),
                },
              ]}
              expandable={{
                expandedRowRender: (item) => (
                  <Flex vertical gap={8}>
                    <Typography.Text strong>Properties</Typography.Text>
                    <pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', margin: 0 }}>
                      {JSON.stringify(item.properties, null, 2)}
                    </pre>
                    <Typography.Text
                      strong
                    >{`Body (${item.bodyEncoding || 'UTF-8'})`}</Typography.Text>
                    {(item.bodyTruncated || item.propertiesTruncated) && (
                      <Alert
                        type="warning"
                        message="The displayed body or properties were truncated by the server."
                      />
                    )}
                    <pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', margin: 0 }}>
                      {item.body}
                    </pre>
                  </Flex>
                ),
              }}
            />
          </>
        )}
      </Flex>
    </Drawer>
  );
}
