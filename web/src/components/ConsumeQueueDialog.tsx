/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  inspectConsumeQueue,
  type ConsumeQueueSnapshot,
  type ConsumeQueueEntry,
} from '../api/consumeQueue';

export interface ConsumeQueueTarget {
  topic: string;
  brokerName: string;
  queueId: number;
}

export function ConsumeQueueDialog({
  instanceId,
  target,
  onClose,
}: {
  instanceId: string;
  target: ConsumeQueueTarget;
  onClose: () => void;
}) {
  const [index, setIndex] = useState('0');
  const [count, setCount] = useState(16);
  const [group, setGroup] = useState('');
  const [snapshot, setSnapshot] = useState<ConsumeQueueSnapshot | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const request = useRef<AbortController | null>(null);
  const active = useRef(true);
  const busyRef = useRef(false);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      request.current?.abort();
    };
  }, []);
  const clear = () => {
    setSnapshot(null);
    setError(null);
  };
  const read = async () => {
    if (busyRef.current) return;
    if (!/^[0-9]{1,19}$/.test(index) || BigInt(index) > 9223372036854775807n) {
      setError('Index must be a nonnegative signed 64-bit decimal string');
      return;
    }
    busyRef.current = true;
    setBusy(true);
    clear();
    const controller = new AbortController();
    request.current = controller;
    try {
      const result = await inspectConsumeQueue(
        {
          instanceId,
          ...target,
          index,
          count,
          consumerGroup: group.trim() || undefined,
        },
        controller.signal,
      );
      if (active.current) setSnapshot(result);
    } catch (failure) {
      if (active.current && !controller.signal.aborted) {
        setError(failure instanceof Error ? failure.message : 'ConsumeQueue inspection failed');
      }
    } finally {
      busyRef.current = false;
      if (active.current) setBusy(false);
    }
  };
  return (
    <Modal
      open
      title="ConsumeQueue index inspection"
      width={1020}
      onCancel={onClose}
      footer={
        <Space>
          <Button onClick={onClose}>Close</Button>
          <Button type="primary" loading={busy} onClick={() => void read()}>
            Read indices
          </Button>
        </Space>
      }
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          type="info"
          showIcon
          message="Read-only storage diagnostics"
          description="Entries expose physical positions, not message bodies. Ordinals are not logical queue offsets. An index-stage pass does not guarantee a final message match."
        />
        <Typography.Text>
          {target.topic} / {target.brokerName} / Queue {target.queueId}
        </Typography.Text>
        <Form layout="inline" disabled={busy}>
          <Form.Item label="Start index">
            <Input
              aria-label="Start index"
              value={index}
              onChange={(event) => {
                setIndex(event.target.value);
                clear();
              }}
            />
          </Form.Item>
          <Form.Item label="Logical span">
            <InputNumber
              aria-label="Logical span"
              min={1}
              max={32}
              precision={0}
              value={count}
              onChange={(value) => {
                setCount(value ?? 16);
                clear();
              }}
            />
          </Form.Item>
          <Form.Item label="Consumer group (optional)">
            <Input
              aria-label="Consumer group"
              value={group}
              onChange={(event) => {
                setGroup(event.target.value);
                clear();
              }}
            />
          </Form.Item>
        </Form>
        {error && <Alert type="error" showIcon message={error} />}
        {snapshot && (
          <>
            <Descriptions size="small" bordered column={2}>
              <Descriptions.Item label="Broker">{snapshot.brokerAddress}</Descriptions.Item>
              <Descriptions.Item label="Sampled range">
                [{snapshot.minIndex}, {snapshot.maxIndex})
              </Descriptions.Item>
              <Descriptions.Item label="Requested index">
                {snapshot.requestedIndex}
              </Descriptions.Item>
              <Descriptions.Item label="Subscription">
                {snapshot.expressionType ?? 'Unavailable'}: {snapshot.expression ?? 'Unavailable'}
              </Descriptions.Item>
            </Descriptions>
            {snapshot.atEnd && <Alert type="info" message="Index is at the sampled queue end" />}
            {snapshot.filterData && (
              <details>
                <summary>Broker filter metadata</summary>
                <pre style={{ whiteSpace: 'pre-wrap' }}>{snapshot.filterData}</pre>
              </details>
            )}
            <Table<ConsumeQueueEntry>
              rowKey="ordinal"
              size="small"
              pagination={false}
              dataSource={snapshot.entries}
              scroll={{ x: 850 }}
              expandable={{
                expandedRowRender: (row) => (
                  <Space direction="vertical">
                    <Typography.Text>Extension: {row.extension ?? 'Unavailable'}</Typography.Text>
                    <Typography.Text>Bitmap: {row.bitmap ?? 'Unavailable'}</Typography.Text>
                    <Typography.Text>Broker message: {row.message ?? 'None'}</Typography.Text>
                  </Space>
                ),
              }}
              columns={[
                { title: 'Ordinal', dataIndex: 'ordinal' },
                {
                  title: 'Physical offset',
                  dataIndex: 'physicalOffset',
                  render: (value) => <Typography.Text copyable>{value}</Typography.Text>,
                },
                { title: 'Physical bytes', dataIndex: 'physicalSize' },
                { title: 'Tags code', dataIndex: 'tagsCode' },
                {
                  title: 'Index stage',
                  dataIndex: 'indexMatch',
                  render: (value) => (
                    <Tag color={value === null ? 'default' : value ? 'blue' : 'orange'}>
                      {value === null ? 'Not evaluated' : value ? 'Passed' : 'Rejected'}
                    </Tag>
                  ),
                },
              ]}
            />
          </>
        )}
      </Space>
    </Modal>
  );
}
