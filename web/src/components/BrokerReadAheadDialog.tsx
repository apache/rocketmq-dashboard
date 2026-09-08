/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Checkbox, Descriptions, Modal, Radio, Space, Tag, Typography } from 'antd';
import {
  inspectBrokerReadAhead,
  updateBrokerReadAhead,
  type ReadAheadTarget,
  type ReadAheadSnapshot,
  type ReadAheadReceipt,
} from '../api/brokerReadAhead';

export function BrokerReadAheadDialog({
  target,
  onClose,
}: {
  target: ReadAheadTarget;
  onClose: () => void;
}) {
  const [snapshot, setSnapshot] = useState<ReadAheadSnapshot>();
  const [receipt, setReceipt] = useState<ReadAheadReceipt>();
  const [enabled, setEnabled] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
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
    if (locked.current || (apply && (!snapshot || !confirmed))) return;
    locked.current = true;
    setBusy(true);
    setError('');
    const controller = new AbortController();
    request.current = controller;
    try {
      if (apply && snapshot) {
        setSnapshot(undefined);
        setConfirmed(false);
        const result = await updateBrokerReadAhead(
          target,
          snapshot.enabled,
          enabled,
          controller.signal,
        );
        if (active.current) setReceipt(result);
      } else {
        setSnapshot(undefined);
        setReceipt(undefined);
        setConfirmed(false);
        const result = await inspectBrokerReadAhead(target, controller.signal);
        if (active.current) {
          setSnapshot(result);
          setEnabled(result.enabled);
        }
      }
    } catch (failure) {
      if (active.current)
        setError(
          apply
            ? 'Update was not confirmed. Inspect current configuration and broker logs before retrying.'
            : failure instanceof Error
              ? failure.message
              : 'Read-ahead inspection failed',
        );
    } finally {
      locked.current = false;
      if (active.current) setBusy(false);
    }
  };
  return (
    <Modal
      open
      title="CommitLog read-ahead"
      width={780}
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
            Inspect current mode
          </Button>
          <Button
            type="primary"
            danger
            loading={busy}
            disabled={!snapshot || !confirmed || snapshot.enabled === enabled}
            onClick={() => void run(true)}
          >
            Apply runtime mode
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
          message="This changes read-ahead advice for one broker's mapped CommitLog files."
          description="Evaluate disk I/O and cache behavior in a maintenance window. Broker acknowledgement and configuration readback do not prove that every OS advice call succeeded; Windows skips the file scan."
        />
        <Descriptions
          size="small"
          column={1}
          items={[
            { key: 'broker', label: 'Broker', children: target.brokerName },
            {
              key: 'address',
              label: 'Registered node',
              children: <Typography.Text code>{target.address}</Typography.Text>,
            },
            {
              key: 'before',
              label: 'Reviewed configuration',
              children:
                (snapshot ?? receipt?.before)
                  ? (snapshot ?? receipt?.before)?.enabled
                    ? 'Normal read-ahead'
                    : 'Random access advice'
                  : 'Not read',
            },
          ]}
        />
        {error && <Alert type="error" showIcon message={error} />}
        {snapshot && (
          <>
            <Radio.Group
              value={enabled}
              disabled={busy}
              onChange={(event) => {
                setEnabled(event.target.value);
                setConfirmed(false);
              }}
            >
              <Radio value={true}>Normal read-ahead</Radio>
              <Radio value={false}>Random access advice</Radio>
            </Radio.Group>
            <Checkbox
              checked={confirmed}
              disabled={busy}
              onChange={(event) => setConfirmed(event.target.checked)}
            >
              I reviewed this node and planned I/O observation and recovery.
            </Checkbox>
          </>
        )}
        {receipt && (
          <Descriptions
            size="small"
            column={1}
            items={[
              { key: 'status', label: 'Result', children: <Tag>{receipt.status}</Tag> },
              {
                key: 'ack',
                label: 'Broker acknowledgement',
                children: receipt.acknowledged ? 'Received' : 'Not received or no write needed',
              },
              {
                key: 'after',
                label: 'Configuration readback',
                children: receipt.observed
                  ? receipt.observed.enabled
                    ? 'Normal read-ahead'
                    : 'Random access advice'
                  : 'Unavailable',
              },
            ]}
          />
        )}
        {receipt?.status === 'UNKNOWN' && (
          <Alert
            type="warning"
            showIcon
            message="The result is uncertain. Inspect configuration and broker logs before recovery."
          />
        )}
        <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
          The native command changes runtime state and does not explicitly persist broker
          configuration. To restore it, inspect again and apply the recorded previous mode. Check
          deployment configuration separately for restart behavior.
        </Typography.Paragraph>
      </Space>
    </Modal>
  );
}
