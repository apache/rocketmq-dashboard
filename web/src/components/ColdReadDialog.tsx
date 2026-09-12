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
import {
  Alert,
  Button,
  Checkbox,
  Descriptions,
  Input,
  Modal,
  Radio,
  Space,
  Spin,
  Table,
  Typography,
} from 'antd';
import {
  changeColdRead,
  inspectColdRead,
  type ColdReadCommand,
  type ColdReadReceipt,
  type ColdReadSnapshot,
} from '../api/coldRead';
import useAuthStore from '../stores/authStore';

const value = (text: string | null) => text ?? 'Not reported';

export default function ColdReadDialog({
  instanceId,
  brokerName,
  onClose,
}: {
  instanceId: string;
  brokerName: string;
  onClose: () => void;
}) {
  const admin = useAuthStore((state) => state.admin);
  const [snapshot, setSnapshot] = useState<ColdReadSnapshot>();
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [group, setGroup] = useState('');
  const [threshold, setThreshold] = useState('');
  const [action, setAction] = useState<ColdReadCommand['action']>('SET');
  const [confirmed, setConfirmed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [receipt, setReceipt] = useState<ColdReadReceipt>();
  const busy = useRef(false);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    void Promise.resolve().then(async () => {
      if (!active) return;
      setLoading(true);
      setSnapshot(undefined);
      setError(undefined);
      try {
        const result = await inspectColdRead(instanceId, brokerName, controller.signal);
        if (active) setSnapshot(result);
      } catch (failure) {
        if (active)
          setError(failure instanceof Error ? failure.message : 'Cold-read inspection failed');
      } finally {
        if (active) setLoading(false);
      }
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [instanceId, brokerName, revision]);

  const invalidate = () => {
    setConfirmed(false);
    setReceipt(undefined);
  };
  const validGroup = /^[%a-zA-Z0-9_|-]{1,255}$/.test(group) && !group.endsWith('||adaptive');
  const validThreshold =
    /^[1-9][0-9]{0,18}$/.test(threshold) && BigInt(threshold) <= 9223372036854775807n;
  const canSave =
    admin !== false &&
    !!snapshot &&
    validGroup &&
    (action === 'REMOVE' || validThreshold) &&
    confirmed &&
    !saving &&
    !receipt;

  const save = async () => {
    if (!canSave || busy.current) return;
    busy.current = true;
    setSaving(true);
    setError(undefined);
    try {
      const result = await changeColdRead({
        instanceId,
        brokerName,
        group,
        action,
        ...(action === 'SET' ? { threshold } : {}),
      });
      if (mounted.current) {
        setReceipt(result);
        setConfirmed(false);
      }
    } catch (failure) {
      if (mounted.current)
        setError(
          failure instanceof Error
            ? failure.message
            : 'Cold-read update failed; refresh before retrying.',
        );
    } finally {
      busy.current = false;
      if (mounted.current) setSaving(false);
    }
  };

  return (
    <Modal
      open
      title={`Cold-read control: ${brokerName}`}
      width={1050}
      onCancel={() => !busy.current && onClose()}
      closable={!saving}
      maskClosable={!saving}
      keyboard={!saving}
      styles={{ body: { maxHeight: '70vh', overflowY: 'auto' } }}
      footer={
        <Space>
          <Button
            disabled={saving}
            loading={loading}
            onClick={() => {
              invalidate();
              setRevision((v) => v + 1);
            }}
          >
            Refresh snapshot
          </Button>
          <Button disabled={saving} onClick={onClose}>
            Close
          </Button>
        </Space>
      }
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          type="info"
          showIcon
          message="Broker-local cold-read limits"
          description="Counters are bytes accumulated in a reset interval, not bytes per second. Group overrides are held in broker memory and must be reapplied after restart. Removing an override restores adaptive or default behavior."
        />
        {error && <Alert type="error" showIcon message={error} />}
        {loading && <Spin aria-label="Loading cold-read control" />}
        {snapshot && (
          <>
            <Typography.Text type="secondary">
              {instanceId} / {snapshot.address} · {new Date(snapshot.sampledAt).toLocaleString()}
            </Typography.Text>
            {snapshot.enabled === 'false' && (
              <Alert
                type="warning"
                showIcon
                message="Cold-data flow control is disabled. Saving a group limit does not enable it."
              />
            )}
            <Descriptions
              bordered
              size="small"
              column={2}
              items={[
                {
                  key: 'enabled',
                  label: 'Flow control enabled',
                  children: value(snapshot.enabled),
                },
                {
                  key: 'adaptive',
                  label: 'Adaptive strategy enabled',
                  children: value(snapshot.adaptiveEnabled),
                },
                {
                  key: 'default',
                  label: 'Default group threshold (bytes)',
                  children: value(snapshot.defaultThreshold),
                },
                {
                  key: 'global',
                  label: 'Global threshold (bytes)',
                  children: value(snapshot.globalThreshold),
                },
                {
                  key: 'acc',
                  label: 'Global accumulated bytes',
                  children: value(snapshot.globalBytes),
                },
              ]}
            />
            <Table
              rowKey="name"
              size="small"
              dataSource={snapshot.groups}
              pagination={{ pageSize: 5 }}
              scroll={{ x: 850 }}
              columns={[
                { title: 'Consumer group', dataIndex: 'name' },
                { title: 'Admin limit (bytes)', dataIndex: 'configuredThreshold', render: value },
                { title: 'Adaptive limit (bytes)', dataIndex: 'adaptiveThreshold', render: value },
                { title: 'Accumulated bytes', dataIndex: 'coldBytes', render: value },
                { title: 'Last cold read (epoch ms)', dataIndex: 'lastReadMillis', render: value },
                {
                  title: 'Edit',
                  key: 'edit',
                  render: (_, row) => (
                    <Button
                      size="small"
                      disabled={saving || admin === false}
                      onClick={() => {
                        setGroup(row.name);
                        setThreshold(row.configuredThreshold ?? '');
                        invalidate();
                      }}
                    >
                      Use group
                    </Button>
                  ),
                },
              ]}
            />
            <Typography.Title level={5}>Change one group override</Typography.Title>
            {admin === false && (
              <Alert type="info" message="Administrator access is required to change limits." />
            )}
            <label>
              Consumer group
              <Input
                aria-label="Consumer group"
                value={group}
                disabled={saving || admin === false}
                onChange={(e) => {
                  setGroup(e.target.value);
                  invalidate();
                }}
              />
            </label>
            <Radio.Group
              aria-label="Limit action"
              value={action}
              disabled={saving || admin === false}
              onChange={(e) => {
                setAction(e.target.value);
                invalidate();
              }}
            >
              <Radio value="SET">Set byte threshold</Radio>
              <Radio value="REMOVE">Remove admin override</Radio>
            </Radio.Group>
            {action === 'SET' && (
              <label>
                Positive threshold in bytes
                <Input
                  aria-label="Positive threshold in bytes"
                  inputMode="numeric"
                  value={threshold}
                  disabled={saving || admin === false}
                  onChange={(e) => {
                    setThreshold(e.target.value);
                    invalidate();
                  }}
                />
              </label>
            )}
            <Checkbox
              checked={confirmed}
              disabled={saving || admin === false}
              onChange={(e) => setConfirmed(e.target.checked)}
            >
              Confirm {action === 'SET' ? `limit ${threshold || '?'} bytes for` : 'removal for'}{' '}
              {group || '?'} on {brokerName} ({snapshot.address}) only
            </Checkbox>
            <Button type="primary" loading={saving} disabled={!canSave} onClick={() => void save()}>
              Apply group limit
            </Button>
          </>
        )}
        {receipt && (
          <Alert
            showIcon
            type={receipt.verified ? 'success' : 'warning'}
            message={
              receipt.verified
                ? 'Broker configuration verified'
                : 'Broker acknowledged; verification incomplete'
            }
            description={
              receipt.verificationError ??
              `${receipt.group}: ${receipt.action === 'SET' ? receipt.threshold + ' bytes' : 'admin override removed'} on ${receipt.address}. Refresh to inspect the latest counters.`
            }
          />
        )}
      </Space>
    </Modal>
  );
}
