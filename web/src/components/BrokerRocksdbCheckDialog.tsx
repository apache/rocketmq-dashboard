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
  Descriptions,
  Input,
  Modal,
  Select,
  Space,
  Typography,
} from 'antd';
import {
  previewRocksdbCheck,
  submitRocksdbCheck,
  type RocksdbCheckPreview,
  type RocksdbCheckReceipt,
  type RocksdbCheckTarget,
} from '../api/brokerRocksdbCheck';

export default function BrokerRocksdbCheckDialog({
  target,
  onClose,
}: {
  target: RocksdbCheckTarget;
  onClose: () => void;
}) {
  const [preview, setPreview] = useState<RocksdbCheckPreview>();
  const [receipt, setReceipt] = useState<RocksdbCheckReceipt>();
  const [topic, setTopic] = useState<string>();
  const [checkpoint, setCheckpoint] = useState(() => String(Date.now() - 600000));
  const [validationTime, setValidationTime] = useState(() => Date.now());
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const busyRef = useRef(false);
  const active = useRef(true);
  const abort = useRef<AbortController>();
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      abort.current?.abort();
    };
  }, []);
  const run = async (submit: boolean) => {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setError(undefined);
    setReceipt(undefined);
    abort.current = new AbortController();
    try {
      if (submit && preview && topic && confirmed) {
        const settings = preview.settings;
        setPreview(undefined);
        setConfirmed(false);
        const value = await submitRocksdbCheck(
          target,
          topic,
          checkpoint,
          settings,
          abort.current.signal,
        );
        if (active.current) setReceipt(value);
      } else if (!submit) {
        setPreview(undefined);
        setConfirmed(false);
        const value = await previewRocksdbCheck(target, abort.current.signal);
        if (active.current) {
          setPreview(value);
          setTopic(undefined);
        }
      }
    } catch (failure) {
      if (active.current)
        setError(
          submit
            ? 'Submission outcome is unknown. Check broker logs before starting another scan.'
            : failure instanceof Error
              ? failure.message
              : 'Storage preview failed',
        );
    } finally {
      if (active.current) {
        busyRef.current = false;
        setBusy(false);
      }
    }
  };
  const validTime =
    /^[0-9]+$/.test(checkpoint) &&
    Number.isSafeInteger(Number(checkpoint)) &&
    Number(checkpoint) > 0 &&
    Number(checkpoint) <= validationTime;
  return (
    <Modal
      open
      title="RocksDB queue write check"
      width={780}
      style={{ top: 24 }}
      onCancel={() => {
        if (!busyRef.current) onClose();
      }}
      closable={!busy}
      maskClosable={!busy}
      keyboard={!busy}
      footer={
        <Space>
          <Button disabled={busy} onClick={onClose}>
            Close
          </Button>
          <Button loading={busy} disabled={busy} onClick={() => void run(false)}>
            Read storage scope
          </Button>
          <Button
            type="primary"
            loading={busy}
            disabled={busy || !preview?.eligible || !topic || !validTime || !confirmed}
            onClick={() => void run(true)}
          >
            Start check
          </Button>
        </Space>
      }
    >
      <Space
        direction="vertical"
        style={{ width: '100%', maxHeight: 'calc(100vh - 210px)', overflowY: 'auto' }}
        size="middle"
      >
        <Typography.Text code>
          {target.brokerName} / {target.address}
        </Typography.Text>
        <Alert
          type="info"
          showIcon
          message="Results are written to broker logs"
          description="This command starts an asynchronous scan. The native API provides no task ID, result polling or cancellation. An accepted response does not mean the indexes match."
        />
        {error && <Alert type="error" message={error} />}
        {preview && (
          <>
            <Descriptions
              size="small"
              column={1}
              bordered
              items={[
                {
                  key: 'enabled',
                  label: 'Double write',
                  children: String(preview.settings.doubleWriteEnabled),
                },
                {
                  key: 'stores',
                  label: 'Configured stores',
                  children: preview.settings.loadingStores.join(', '),
                },
                { key: 'time', label: 'Sampled at', children: preview.sampledAt },
              ]}
            />
            {!preview.eligible && (
              <Alert
                type="warning"
                message="Requires double write with default and defaultRocksDB stores"
                description="Configuration alone cannot prove the running store implementation. Verify the broker version and startup logs."
              />
            )}
            <label htmlFor="rocksdb-check-topic">Topic on this node</label>
            <Select
              id="rocksdb-check-topic"
              aria-label="Topic on this node"
              showSearch
              style={{ width: '100%' }}
              value={topic}
              disabled={busy || !preview.eligible}
              options={preview.topics.map((value) => ({ value, label: value }))}
              onChange={(value) => {
                setTopic(value);
                setConfirmed(false);
                setReceipt(undefined);
              }}
            />
            <label htmlFor="rocksdb-check-time">Check from store time (epoch milliseconds)</label>
            <Input
              id="rocksdb-check-time"
              value={checkpoint}
              disabled={busy}
              onChange={(event) => {
                setCheckpoint(event.target.value);
                setValidationTime(Date.now());
                setConfirmed(false);
                setReceipt(undefined);
              }}
            />
            {!validTime && (
              <Typography.Text type="danger">
                Enter positive epoch milliseconds in the past.
              </Typography.Text>
            )}
            <Alert
              type="warning"
              message="Plan for storage I/O"
              description="The broker compares retained queue entries from the checkpoint toward its current end. An older time can scan more data. The checkpoint is not an exact bound or an atomic snapshot; time lookup may fall back to the retained start."
            />
            <Checkbox
              checked={confirmed}
              disabled={busy || !preview.eligible || !topic || !validTime}
              onChange={(event) => setConfirmed(event.target.checked)}
            >
              I reviewed this node, topic, checkpoint and scan load.
            </Checkbox>
          </>
        )}
        {receipt && (
          <>
            <Alert
              type={receipt.status === 'ACCEPTED' ? 'info' : 'warning'}
              message={receipt.status}
              description="This is the command response, not a verified consistency result. Check the selected broker's logs before another submission."
            />
            <Descriptions
              bordered
              size="small"
              column={1}
              items={[
                { key: 'topic', label: 'Topic', children: receipt.topic },
                {
                  key: 'checkpoint',
                  label: 'Store-time checkpoint',
                  children: receipt.checkFromMillis,
                },
                {
                  key: 'status',
                  label: 'Raw broker status',
                  children: receipt.brokerStatus ?? 'Unavailable',
                },
                { key: 'submitted', label: 'Submitted at', children: receipt.submittedAt },
                { key: 'received', label: 'Response received at', children: receipt.receivedAt },
                {
                  key: 'remark',
                  label: 'Broker response detail',
                  children: receipt.brokerRemark || 'No synchronous detail',
                },
              ]}
            />
            <Typography.Paragraph copyable code>
              checkRocksdbCqWriteProgress result:
            </Typography.Paragraph>
            <Typography.Text>
              Search this marker and checkRocksdbCqWriteProgress error on the selected node around
              the submission time. There is no unique task identifier; concurrent scans may have
              overlapping log entries.
            </Typography.Text>
          </>
        )}
      </Space>
    </Modal>
  );
}
