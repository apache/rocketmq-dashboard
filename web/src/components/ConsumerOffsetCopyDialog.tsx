/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Checkbox, Input, Modal, Space, Table, Tag, Typography } from 'antd';
import {
  applyConsumerOffsetCopy,
  previewConsumerOffsetCopy,
  type CopyPreview,
  type CopyExpected,
  type CopyReceipt,
} from '../api/consumerOffsetCopy';

export function ConsumerOffsetCopyDialog({
  instanceId,
  topic,
  sourceGroup,
  onClose,
}: {
  instanceId: string;
  topic: string;
  sourceGroup: string;
  onClose: () => void;
}) {
  const [targetGroup, setTargetGroup] = useState('');
  const [preview, setPreview] = useState<CopyPreview>();
  const [receipt, setReceipt] = useState<CopyReceipt>();
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const locked = useRef(false);
  const active = useRef(true);
  const request = useRef<AbortController>();
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      request.current?.abort();
    };
  }, []);
  const run = async (apply: boolean) => {
    if (locked.current || !targetGroup.trim() || (apply && (!preview || !confirmed))) return;
    locked.current = true;
    setBusy(true);
    setError('');
    const controller = new AbortController();
    request.current = controller;
    const selection = { instanceId, topic, sourceGroup, targetGroup: targetGroup.trim() };
    try {
      if (apply && preview) {
        // Consume the preview before sending; a lost response must never expose a retry button.
        setPreview(undefined);
        setConfirmed(false);
        const result = await applyConsumerOffsetCopy(
          selection,
          preview.queues.map((q) => q.expected),
          controller.signal,
        );
        if (active.current) setReceipt(result);
      } else {
        setPreview(undefined);
        setReceipt(undefined);
        setConfirmed(false);
        const result = await previewConsumerOffsetCopy(selection, controller.signal);
        if (active.current) setPreview(result);
      }
    } catch (failure) {
      if (active.current)
        setError(
          apply
            ? 'Copy was not confirmed. Read target offsets before taking any further action; do not retry blindly.'
            : failure instanceof Error
              ? failure.message
              : 'Preview failed',
        );
    } finally {
      locked.current = false;
      if (active.current) setBusy(false);
    }
  };
  const rows: (CopyExpected & { status: string; observed: string | null; range: string | null })[] =
    receipt
      ? receipt.queues.map((row) => ({
          ...row.queue,
          status: row.status,
          observed: row.observedOffset,
          range: null,
        }))
      : (preview?.queues.map((row) => ({
          ...row.expected,
          status: 'PREVIEW',
          observed: null,
          range: `${row.minOffset} … ${row.maxOffset}`,
        })) ?? []);
  return (
    <Modal
      open
      title="Copy topic consumer offsets"
      width={960}
      style={{ top: 24 }}
      onCancel={() => {
        if (!locked.current) onClose();
      }}
      closable={!busy}
      maskClosable={!busy}
      footer={
        <Space>
          <Button disabled={busy} onClick={onClose}>
            Close
          </Button>
          <Button
            loading={busy}
            disabled={!targetGroup.trim() || targetGroup.trim() === sourceGroup}
            onClick={() => void run(false)}
          >
            Preview offsets
          </Button>
          <Button
            danger
            type="primary"
            loading={busy}
            disabled={!preview || !confirmed}
            onClick={() => void run(true)}
          >
            Copy reviewed offsets
          </Button>
        </Space>
      }
    >
      <Space
        direction="vertical"
        size={16}
        style={{ width: '100%', maxHeight: 'calc(100vh - 210px)', overflow: 'auto' }}
      >
        <Alert
          type="warning"
          showIcon
          message="Stop both consumer groups before preview and keep them stopped."
          description="This replaces target offsets for every readable queue in this topic. Concurrent clients or administrators can invalidate the result. Switching instances or losing the response does not cancel broker writes."
        />
        <Typography.Text>
          Topic: <strong>{topic}</strong> · Source group: <strong>{sourceGroup}</strong>
        </Typography.Text>
        <label>
          Existing target group
          <Input
            aria-label="Existing target group"
            value={targetGroup}
            disabled={busy}
            onChange={(event) => {
              setTargetGroup(event.target.value);
              setPreview(undefined);
              setReceipt(undefined);
              setConfirmed(false);
              setError('');
            }}
          />
        </label>
        {error && <Alert type="error" showIcon message={error} />}
        {receipt?.queues.some((row) => row.status === 'UNKNOWN') && (
          <Alert
            type="warning"
            showIcon
            message="Copy stopped after an unconfirmed result. Inspect target offsets before recovery."
          />
        )}
        <Table
          size="small"
          pagination={false}
          dataSource={rows}
          scroll={{ x: 820, y: 320 }}
          rowKey={(row) => `${row.brokerName}/${row.queueId}`}
          columns={[
            { title: 'Broker / Queue', render: (_, row) => `${row.brokerName} / ${row.queueId}` },
            {
              title: 'Target before',
              dataIndex: 'targetOffset',
              render: (value) => value ?? 'Not stored',
            },
            { title: 'Source / requested', dataIndex: 'sourceOffset' },
            {
              title: receipt ? 'Observed after' : 'Retained range (end inclusive)',
              render: (_, row) => (receipt ? (row.observed ?? 'Unavailable') : row.range),
            },
            { title: 'State', dataIndex: 'status', render: (value) => <Tag>{value}</Tag> },
          ]}
        />
        {preview && (
          <Checkbox
            checked={confirmed}
            disabled={busy}
            onChange={(event) => setConfirmed(event.target.checked)}
          >
            I reviewed every queue and accept replay or skipped messages at the target group.
          </Checkbox>
        )}
      </Space>
    </Modal>
  );
}
