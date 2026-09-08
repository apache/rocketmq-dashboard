/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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
import { Alert, Button, Descriptions, Input, Modal, Space, Typography } from 'antd';
import {
  applyQueueOffset,
  previewQueueOffset,
  type QueueOffsetPreview,
  type QueueOffsetTarget,
} from '../api/queueOffset';

interface Props {
  target: QueueOffsetTarget;
  onClose: () => void;
  onApplied: () => void;
}

export default function QueueOffsetDialog({ target, onClose, onApplied }: Props) {
  const [offset, setOffset] = useState('');
  const [preview, setPreview] = useState<QueueOffsetPreview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [applied, setApplied] = useState(false);
  const [busy, setBusy] = useState<'preview' | 'apply' | null>(null);
  const busyRef = useRef(false);
  const activeRef = useRef(true);
  useEffect(() => {
    activeRef.current = true;
    return () => {
      activeRef.current = false;
    };
  }, []);

  const run = async (mode: 'preview' | 'apply') => {
    if (busyRef.current || !/^(0|[1-9][0-9]{0,18})$/.test(offset)) return;
    if (mode === 'apply' && (!preview || applied)) return;
    busyRef.current = true;
    setBusy(mode);
    setError(null);
    try {
      if (mode === 'preview') {
        setPreview(null);
        setApplied(false);
        const result = await previewQueueOffset(target, offset);
        if (activeRef.current) setPreview(result);
      } else if (preview) {
        await applyQueueOffset(target, preview.targetOffset, preview.currentOffset);
        if (activeRef.current) {
          setApplied(true);
          onApplied();
        }
      }
    } catch (failure) {
      if (activeRef.current) {
        setError(failure instanceof Error ? failure.message : 'Queue offset operation failed');
        setPreview(null);
      }
    } finally {
      busyRef.current = false;
      if (activeRef.current) setBusy(null);
    }
  };

  return (
    <Modal
      open
      title="Set one queue's consumer offset"
      width={720}
      onCancel={onClose}
      closable={busy !== 'apply'}
      keyboard={busy !== 'apply'}
      maskClosable={busy !== 'apply'}
      styles={{ body: { maxHeight: '65vh', overflowY: 'auto' } }}
      footer={
        <Space>
          <Button onClick={onClose} disabled={busy === 'apply'}>
            Close
          </Button>
          <Button
            onClick={() => void run('preview')}
            loading={busy === 'preview'}
            disabled={busy === 'apply' || !/^(0|[1-9][0-9]{0,18})$/.test(offset)}
          >
            Preview change
          </Button>
          <Button
            danger
            type="primary"
            loading={busy === 'apply'}
            disabled={!preview || applied || busy === 'preview' || preview.offsetDelta === '0'}
            onClick={() => void run('apply')}
          >
            Confirm queue offset
          </Button>
        </Space>
      }
    >
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        <Alert
          type="warning"
          showIcon
          message="Stop all consumers and keep them stopped until this operation completes"
          description="This changes one persisted broker offset for traditional clustered consumption. It does not clear POP in-flight messages or update broadcasting clients' local offsets."
        />
        <Descriptions bordered column={1} size="small">
          <Descriptions.Item label="Instance">{target.instanceId}</Descriptions.Item>
          <Descriptions.Item label="Consumer group">{target.group}</Descriptions.Item>
          <Descriptions.Item label="Topic / Broker / Queue">
            {target.topic} / {target.brokerName} / {target.queueId}
          </Descriptions.Item>
        </Descriptions>
        <label>
          <Typography.Text>Target offset</Typography.Text>
          <Input
            aria-label="Target offset"
            value={offset}
            inputMode="numeric"
            disabled={busy !== null}
            onChange={(event) => {
              setOffset(event.target.value);
              setPreview(null);
              setApplied(false);
              setError(null);
            }}
          />
        </label>
        {preview && (
          <Descriptions bordered column={2} size="small">
            <Descriptions.Item label="Master address">{preview.brokerAddress}</Descriptions.Item>
            <Descriptions.Item label="Current offset">{preview.currentOffset}</Descriptions.Item>
            <Descriptions.Item label="Minimum offset">{preview.minOffset}</Descriptions.Item>
            <Descriptions.Item label="End offset">{preview.maxOffset}</Descriptions.Item>
            <Descriptions.Item label="Target offset">{preview.targetOffset}</Descriptions.Item>
            <Descriptions.Item label="Projected lag">{preview.projectedLag}</Descriptions.Item>
            <Descriptions.Item label="Impact" span={2}>
              {preview.offsetDelta === '0'
                ? 'No change'
                : preview.offsetDelta.startsWith('-')
                  ? `Replay ${preview.offsetDelta.slice(1)} queue positions`
                  : `Skip ${preview.offsetDelta} queue positions`}
            </Descriptions.Item>
          </Descriptions>
        )}
        {error && <Alert type="error" showIcon message={error} />}
        {applied && (
          <Alert type="success" showIcon message="Broker accepted the queue offset update" />
        )}
      </Space>
    </Modal>
  );
}
