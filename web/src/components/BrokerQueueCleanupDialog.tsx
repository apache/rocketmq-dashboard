/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Input, Modal, Radio, Space, Tag, Typography } from 'antd';
import {
  applyBrokerQueueCleanup,
  previewBrokerQueueCleanup,
  type QueueCleanupOperation,
  type QueueCleanupPreview,
  type QueueCleanupReceipt,
  type QueueCleanupTarget,
} from '../api/brokerQueueCleanup';

export function BrokerQueueCleanupDialog({
  target,
  onClose,
}: {
  target: QueueCleanupTarget;
  onClose: () => void;
}) {
  const [operation, setOperation] = useState<QueueCleanupOperation>('UNUSED_TOPIC_QUEUES');
  const [preview, setPreview] = useState<QueueCleanupPreview>();
  const [receipt, setReceipt] = useState<QueueCleanupReceipt>();
  const [confirmation, setConfirmation] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const active = useRef(true);
  const locked = useRef(false);
  const request = useRef<AbortController>();
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      request.current?.abort();
    };
  }, []);
  const run = async (apply: boolean) => {
    if (locked.current || (apply && (!preview || confirmation !== target.address))) return;
    locked.current = true;
    setBusy(true);
    setError('');
    const controller = new AbortController();
    request.current = controller;
    try {
      if (apply && preview) {
        setPreview(undefined);
        setConfirmation('');
        const result = await applyBrokerQueueCleanup(
          target,
          operation,
          preview.configuredTopics,
          confirmation,
          controller.signal,
        );
        if (active.current) setReceipt(result);
      } else {
        setPreview(undefined);
        setReceipt(undefined);
        setConfirmation('');
        const result = await previewBrokerQueueCleanup(target, controller.signal);
        if (active.current) setPreview(result);
      }
    } catch (failure) {
      if (active.current)
        setError(
          apply
            ? 'Cleanup was not confirmed. Check broker logs and storage; some files may already have been removed.'
            : failure instanceof Error
              ? failure.message
              : 'Cleanup scope could not be read',
        );
    } finally {
      locked.current = false;
      if (active.current) setBusy(false);
    }
  };
  return (
    <Modal
      open
      title="Broker queue cleanup"
      width={800}
      style={{ top: 24 }}
      closable={!busy}
      maskClosable={!busy}
      onCancel={() => {
        if (!locked.current) onClose();
      }}
      footer={
        <Space>
          <Button disabled={busy} onClick={onClose}>
            Close
          </Button>
          <Button loading={busy} onClick={() => void run(false)}>
            Review node scope
          </Button>
          <Button
            danger
            type="primary"
            loading={busy}
            disabled={!preview || confirmation !== target.address}
            onClick={() => void run(true)}
          >
            Run cleanup
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
          message="This permanently removes broker-selected queue storage. There is no automatic undo."
          description="Use a maintenance window with verified backups and recovery procedures. The native API has no dry run or deleted-file inventory. Reviewing configured topics is not a list of deletion candidates."
        />
        <Typography.Text>
          Broker: <strong>{target.brokerName}</strong> · Node:{' '}
          <Typography.Text code>{target.address}</Typography.Text>
        </Typography.Text>
        <Radio.Group
          value={operation}
          disabled={busy}
          onChange={(event) => {
            setOperation(event.target.value);
            setPreview(undefined);
            setReceipt(undefined);
            setConfirmation('');
            setError('');
          }}
        >
          <Space direction="vertical">
            <Radio value="UNUSED_TOPIC_QUEUES">Remove queues for unconfigured topics</Radio>
            <Radio value="EXPIRED_CONSUME_QUEUES">Remove expired ConsumeQueues</Radio>
          </Space>
        </Radio.Group>
        <Typography.Paragraph style={{ margin: 0 }}>
          {operation === 'UNUSED_TOPIC_QUEUES'
            ? 'The broker keeps configured topics and native exclusions, then removes remaining unused queue stores.'
            : 'The broker uses the current minimum CommitLog offset to remove fully expired queues. This may include queues belonging to still-configured topics.'}
        </Typography.Paragraph>
        {error && <Alert type="error" showIcon message={error} />}
        {preview && (
          <>
            <Typography.Text strong>
              Currently configured topics ({preview.configuredTopics.length})
            </Typography.Text>
            <div
              style={{
                maxHeight: 160,
                overflow: 'auto',
                border: '1px solid #d9d9d9',
                borderRadius: 8,
                padding: 12,
              }}
            >
              {preview.configuredTopics.length
                ? preview.configuredTopics.map((topic) => (
                    <div key={topic}>
                      <Typography.Text code>{topic}</Typography.Text>
                    </div>
                  ))
                : 'No configured topics were returned'}
            </div>
            <label>
              Type the node address to confirm irreversible cleanup
              <Input
                aria-label="Node address confirmation"
                value={confirmation}
                disabled={busy}
                onChange={(event) => setConfirmation(event.target.value)}
                autoComplete="off"
              />
            </label>
          </>
        )}
        {receipt && (
          <Alert
            type={receipt.status === 'UNKNOWN' ? 'warning' : 'info'}
            showIcon
            message={<Tag>{receipt.status}</Tag>}
            description={
              receipt.status === 'UNKNOWN'
                ? 'Check broker logs and storage before another attempt. Cleanup may be partially complete.'
                : 'The broker reported completion. No deleted-file count or reclaimed-byte measurement is available; verify storage and broker logs.'
            }
          />
        )}
        <Typography.Text type="secondary">
          This targets one registered node and does not trigger manual CommitLog deletion. Closing
          the browser or switching instances cannot stop a cleanup already sent to the broker.
        </Typography.Text>
      </Space>
    </Modal>
  );
}
