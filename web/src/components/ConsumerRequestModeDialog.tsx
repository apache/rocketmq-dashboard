/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Checkbox,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  applyConsumerRequestMode,
  previewConsumerRequestMode,
  type RequestMode,
  type BrokerRequestMode,
  type RequestModePreview,
  type RequestModeReceipt,
} from '../api/consumerRequestMode';

export function ConsumerRequestModeDialog({
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
  const [mode, setMode] = useState<RequestMode>('POP');
  const [sharing, setSharing] = useState<number | null>(-1);
  const [preview, setPreview] = useState<RequestModePreview>();
  const [receipt, setReceipt] = useState<RequestModeReceipt>();
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const active = useRef(true);
  const locked = useRef(false);
  const request = useRef<AbortController>();
  const valid =
    sharing !== null && Number.isInteger(sharing) && sharing >= -1 && sharing <= 2147483647;
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      request.current?.abort();
    };
  }, []);
  const invalidate = () => {
    setPreview(undefined);
    setReceipt(undefined);
    setConfirmed(false);
    setError('');
  };
  const run = async (apply: boolean) => {
    if (locked.current || !valid || (apply && (!preview || !confirmed))) return;
    locked.current = true;
    setBusy(true);
    setError('');
    const controller = new AbortController();
    request.current = controller;
    const selection = { instanceId, topic, group };
    try {
      if (apply && preview) {
        setPreview(undefined);
        setConfirmed(false);
        const result = await applyConsumerRequestMode(
          selection,
          mode,
          sharing!,
          preview.brokers,
          controller.signal,
        );
        if (active.current) setReceipt(result);
      } else {
        invalidate();
        const result = await previewConsumerRequestMode(selection, controller.signal);
        if (active.current) setPreview(result);
      }
    } catch (failure) {
      if (active.current)
        setError(
          apply
            ? 'Update was not confirmed. Read every broker before recovery; do not assume that no changes were made.'
            : failure instanceof Error
              ? failure.message
              : 'Request mode preview failed',
        );
    } finally {
      locked.current = false;
      if (active.current) setBusy(false);
    }
  };
  const rows: { before: BrokerRequestMode; status: string; observed: BrokerRequestMode | null }[] =
    receipt?.brokers ??
    preview?.brokers.map((before) => ({ before, status: 'PREVIEW', observed: null })) ??
    [];
  return (
    <Modal
      open
      title="Consumer request mode"
      width={1000}
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
          <Button loading={busy} disabled={!valid} onClick={() => void run(false)}>
            Preview broker modes
          </Button>
          <Button
            danger
            type="primary"
            loading={busy}
            disabled={!preview || !confirmed || !valid}
            onClick={() => void run(true)}
          >
            Apply request mode
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
          message="Stop this consumer group before preview and keep it stopped until verification."
          description="This writes an explicit topic/group override on every topic master. Compatible clients must use server assignments. It does not convert client SDKs, drain in-flight POP messages, or reset offsets."
        />
        <Typography.Text>
          Topic: <strong>{topic}</strong> · Group: <strong>{group}</strong>
        </Typography.Text>
        <Space size={20} wrap>
          <label>
            Requested mode{' '}
            <Select
              aria-label="Requested mode"
              value={mode}
              disabled={busy}
              style={{ width: 110 }}
              options={[
                { value: 'POP', label: 'POP' },
                { value: 'PULL', label: 'PULL' },
              ]}
              onChange={(value) => {
                setMode(value);
                setSharing(value === 'PULL' ? 0 : -1);
                invalidate();
              }}
            />
          </label>
          <label>
            POP sharing{' '}
            <InputNumber
              aria-label="POP sharing"
              min={-1}
              max={2147483647}
              precision={0}
              value={sharing}
              disabled={busy || mode === 'PULL'}
              onChange={(value) => {
                setSharing(value);
                invalidate();
              }}
            />
          </label>
        </Space>
        <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
          POP values -1 and 0 allow each client to access all queues. A positive value shares queues
          assigned to following clients; it also becomes all queues when it reaches the client-count
          threshold. PULL stores 0.
        </Typography.Paragraph>
        {error && <Alert type="error" showIcon message={error} />}
        {receipt?.brokers.some((row) => row.status === 'UNKNOWN') && (
          <Alert
            type="warning"
            showIcon
            message="Update stopped after an unconfirmed broker. Inspect all broker modes before continuing."
          />
        )}
        <Table
          size="small"
          pagination={false}
          dataSource={rows}
          rowKey={(row) => row.before.brokerName}
          scroll={{ x: 800 }}
          columns={[
            {
              title: 'Broker / master',
              render: (_, row) => (
                <>
                  {row.before.brokerName}
                  <br />
                  <Typography.Text code>{row.before.address}</Typography.Text>
                </>
              ),
            },
            {
              title: 'Before / POP sharing',
              render: (_, row) => `${row.before.mode} / ${row.before.popShareQueueNum}`,
            },
            {
              title: 'Before source',
              render: (_, row) => (row.before.explicit ? 'Explicit override' : 'Broker default'),
            },
            {
              title: 'Server load balancing',
              render: (_, row) => row.before.serverLoadBalancerEnable,
            },
            {
              title: 'Observed after',
              render: (_, row) =>
                row.observed
                  ? `${row.observed.mode} / ${row.observed.popShareQueueNum}`
                  : 'Unavailable',
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
            I reviewed every broker and planned client compatibility, in-flight delivery and
            recovery.
          </Checkbox>
        )}
        <Typography.Text type="secondary">
          Restoring a previous default value creates an explicit override; this API cannot remove an
          override. Closing the browser or switching instances cannot cancel broker writes.
        </Typography.Text>
      </Space>
    </Modal>
  );
}
