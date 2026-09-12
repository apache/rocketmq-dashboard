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
import { Alert, Button, Checkbox, Descriptions, Input, Modal, Space, Typography } from 'antd';
import {
  inspectTransactionRecovery,
  recoverTransactionCheck,
  type TransactionRecoveryPreview,
} from '../api/transactionRecovery';
import useAuthStore from '../stores/authStore';

export default function TransactionRecoveryDialog({
  instanceId,
  initialId = '',
  onClose,
}: {
  instanceId: string;
  initialId?: string;
  onClose: () => void;
}) {
  const admin = useAuthStore((state) => state.admin);
  const [id, setId] = useState(initialId);
  const [preview, setPreview] = useState<TransactionRecoveryPreview>();
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState<'inspect' | 'recover'>();
  const [error, setError] = useState<string>();
  const [accepted, setAccepted] = useState(false);
  const busyRef = useRef(false);
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);

  const run = async (operation: 'inspect' | 'recover') => {
    if (busyRef.current || admin === false) return;
    if (operation === 'recover' && (!preview || !confirmed || accepted)) return;
    busyRef.current = true;
    setBusy(operation);
    setError(undefined);
    try {
      const command = {
        instanceId,
        offsetMessageId: operation === 'recover' ? preview!.offsetMessageId : id,
      };
      const result =
        operation === 'inspect'
          ? await inspectTransactionRecovery(command)
          : await recoverTransactionCheck(command);
      if (active.current) {
        setPreview(result);
        setConfirmed(false);
        setAccepted(operation === 'recover');
      }
    } catch (failure) {
      if (active.current) {
        setError(
          failure instanceof Error ? failure.message : 'Transaction recovery request failed',
        );
        setConfirmed(false);
        if (operation === 'inspect') setPreview(undefined);
      }
    } finally {
      busyRef.current = false;
      if (active.current) setBusy(undefined);
    }
  };

  return (
    <Modal
      open
      title="Recover transaction checks"
      width={800}
      onCancel={() => !busyRef.current && onClose()}
      closable={!busy}
      maskClosable={!busy}
      keyboard={!busy}
      styles={{ body: { maxHeight: '65vh', overflowY: 'auto' } }}
      footer={
        <Space>
          <Button disabled={!!busy} onClick={onClose}>
            Close
          </Button>
          <Button
            type="primary"
            loading={busy === 'recover'}
            disabled={!preview || !confirmed || !!busy || accepted || admin === false}
            onClick={() => void run('recover')}
          >
            Recover checks
          </Button>
        </Space>
      }
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          type="warning"
          showIcon
          message="Resume business transaction checks"
          description="This places a discarded transaction back into the half-message queue and resets its check count. The producer decides the transaction outcome. Repeating recovery can create another check request; do not retry automatically after a timeout."
        />
        <Typography.Paragraph>
          Use the physical offset message ID from TRANS_CHECK_MAX_TIME_TOPIC on the selected
          instance ({instanceId}). The original broker must still be a registered master. Ensure the
          producer group can answer checks before recovery.
        </Typography.Paragraph>
        {admin === false && <Alert type="info" message="Administrator access is required." />}
        <label>
          Discarded physical message ID
          <Input
            aria-label="Discarded physical message ID"
            value={id}
            disabled={!!busy || admin === false}
            onChange={(event) => {
              setId(event.target.value);
              setPreview(undefined);
              setConfirmed(false);
              setAccepted(false);
              setError(undefined);
            }}
          />
        </label>
        <Button
          loading={busy === 'inspect'}
          disabled={!!busy || admin === false || !/^([0-9a-f]{32}|[0-9a-f]{56})$/i.test(id)}
          onClick={() => void run('inspect')}
        >
          Inspect discarded transaction
        </Button>
        {error && <Alert type="error" showIcon message={error} />}
        {preview && (
          <>
            <Descriptions
              bordered
              size="small"
              column={1}
              items={[
                { key: 'id', label: 'Physical message ID', children: preview.offsetMessageId },
                { key: 'broker', label: 'Broker address', children: preview.brokerAddress },
                { key: 'topic', label: 'Original topic', children: preview.originalTopic },
                { key: 'group', label: 'Producer group', children: preview.producerGroup },
                {
                  key: 'count',
                  label: 'Previous check count',
                  children: preview.checkTimes ?? 'Not reported',
                },
                {
                  key: 'transaction',
                  label: 'Transaction ID',
                  children: preview.transactionId ?? 'Not reported',
                },
                {
                  key: 'stored',
                  label: 'Discarded message stored at',
                  children:
                    preview.storedAt > 0
                      ? new Date(preview.storedAt).toLocaleString()
                      : 'Not reported',
                },
              ]}
            />
            <Checkbox
              checked={confirmed}
              disabled={!!busy || accepted}
              onChange={(event) => setConfirmed(event.target.checked)}
            >
              Confirm recovery for {preview.originalTopic} through producer group{' '}
              {preview.producerGroup}
            </Checkbox>
          </>
        )}
        {accepted && (
          <Alert
            type="success"
            showIcon
            message="Broker accepted transaction check recovery"
            description="The transaction outcome is pending producer checks. Inspect the producer and message trace to verify the outcome."
          />
        )}
      </Space>
    </Modal>
  );
}
