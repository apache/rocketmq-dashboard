/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Descriptions, Empty, Modal, Space, Tag, Typography } from 'antd';
import {
  inspectConsumerTimeSpan,
  type ConsumerQueueTimeSpan,
  type ConsumerTimeSpanSnapshot,
  type CursorState,
} from '../api/consumerTimeSpan';

const stateLabels: Record<CursorState, string> = {
  RECORDED_OFFSET_REFERENCE: 'Recorded offset reference',
  EARLIEST_MESSAGE_FALLBACK: 'Earliest message fallback',
  OUTSIDE_RETAINED_RANGE: 'Offset outside retained range',
  OFFSET_UNAVAILABLE: 'Consumer offset unavailable',
  TIMESTAMP_UNAVAILABLE: 'Cursor timestamp unavailable',
};

function time(value: string | null): string {
  if (value === null) return 'Unavailable';
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number <= 0 || number > 8640000000000000) {
    return 'Outside displayable date range (' + value + ')';
  }
  return new Date(number).toISOString();
}

function QueueTimeline({ row }: { row: ConsumerQueueTimeSpan }) {
  const start = row.earliestTime === null ? NaN : Number(row.earliestTime);
  const end = row.latestTime === null ? NaN : Number(row.latestTime);
  const cursor = row.cursorTime === null ? NaN : Number(row.cursorTime);
  const hasRange = Number.isSafeInteger(start) && Number.isSafeInteger(end) && end >= start;
  const hasMarker =
    hasRange &&
    row.cursorState === 'RECORDED_OFFSET_REFERENCE' &&
    Number.isSafeInteger(cursor) &&
    cursor >= start &&
    cursor <= end;
  const position = hasMarker ? (end === start ? 50 : (100 * (cursor - start)) / (end - start)) : 0;
  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      {hasRange ? (
        <div
          role="img"
          aria-label={
            'Retained time range: ' +
            time(row.earliestTime) +
            ' to ' +
            time(row.latestTime) +
            (hasMarker ? '; offset reference: ' + time(row.cursorTime) : '')
          }
          style={{ padding: '12px 8px' }}
        >
          <div style={{ height: 8, background: '#bae0ff', borderRadius: 4, position: 'relative' }}>
            {hasMarker && (
              <span
                title="Recorded offset reference"
                style={{
                  position: 'absolute',
                  left: position + '%',
                  top: -4,
                  width: 4,
                  height: 16,
                  background: '#0958d9',
                  transform: 'translateX(-50%)',
                }}
              />
            )}
          </div>
        </div>
      ) : (
        <Typography.Text type="secondary">
          Retained time range unavailable or inconsistent
        </Typography.Text>
      )}
      <Descriptions size="small" column={1}>
        <Descriptions.Item label="Earliest retained">{time(row.earliestTime)}</Descriptions.Item>
        <Descriptions.Item label="Latest retained">{time(row.latestTime)}</Descriptions.Item>
        <Descriptions.Item label="Broker cursor reference">
          {time(row.cursorTime)}
        </Descriptions.Item>
      </Descriptions>
    </Space>
  );
}

export function ConsumerTimeSpanDialog({
  instanceId,
  topic,
  group,
  onClose,
}: {
  instanceId: string;
  topic: string;
  group: string;
  onClose: () => void;
}) {
  const [snapshot, setSnapshot] = useState<ConsumerTimeSpanSnapshot | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const busyRef = useRef(false);
  const active = useRef(true);
  const request = useRef<AbortController | null>(null);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      request.current?.abort();
    };
  }, []);
  const read = async () => {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setError(null);
    setSnapshot(null);
    const controller = new AbortController();
    request.current = controller;
    try {
      const result = await inspectConsumerTimeSpan({ instanceId, topic, group }, controller.signal);
      if (active.current) setSnapshot(result);
    } catch (failure) {
      if (active.current && !controller.signal.aborted) {
        setError(failure instanceof Error ? failure.message : 'Time span inspection failed');
      }
    } finally {
      busyRef.current = false;
      if (active.current) setBusy(false);
    }
  };
  return (
    <Modal
      open
      title="Consumer queue time spans"
      width={980}
      style={{ top: 24 }}
      onCancel={onClose}
      footer={
        <Space>
          <Button onClick={onClose}>Close</Button>
          <Button type="primary" loading={busy} onClick={() => void read()}>
            Read time spans
          </Button>
        </Space>
      }
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          showIcon
          type="info"
          message="Retained message times and offset references"
          description="Times are message store timestamps, not processing times. A fallback is not proof of consumption. Samples are not atomic; no delay SLO or completion percentage is inferred."
        />
        <Typography.Text>
          {group} / {topic}
        </Typography.Text>
        {error && <Alert type="error" showIcon message={error} />}
        {snapshot && (
          <>
            <Typography.Text type="secondary">
              Sample completed: {snapshot.sampledAt} · UTC
            </Typography.Text>
            {snapshot.queues.length === 0 && (
              <Empty description="No queues returned in the topic metadata" />
            )}
          <div style={{ maxHeight: 'calc(100vh - 390px)', overflowY: 'auto' }}>
              <Space direction="vertical" style={{ width: '100%' }}>
                {snapshot.queues.map((row) => (
                  <Card
                    size="small"
                    key={row.brokerName + ':' + row.queueId}
                    title={row.brokerName + ' / Queue ' + row.queueId}
                    extra={
                      <Tag
                        color={row.cursorState === 'RECORDED_OFFSET_REFERENCE' ? 'blue' : 'orange'}
                      >
                        {stateLabels[row.cursorState]}
                      </Tag>
                    }
                  >
                    {!row.spanAvailable && (
                      <Alert type="warning" message="Broker returned no time span for this queue" />
                    )}
                    <Descriptions size="small" column={2}>
                      <Descriptions.Item label="Retained offsets">
                        [{row.minOffset}, {row.maxOffset})
                      </Descriptions.Item>
                      <Descriptions.Item label="Consumer offset">
                        {row.consumerOffset ?? 'Unavailable'}
                      </Descriptions.Item>
                    </Descriptions>
                    <QueueTimeline row={row} />
                  </Card>
                ))}
              </Space>
            </div>
          </>
        )}
      </Space>
    </Modal>
  );
}
