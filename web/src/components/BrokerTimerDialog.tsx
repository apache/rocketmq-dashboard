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

import { useEffect, useState } from 'react';
import { Alert, Button, Descriptions, Modal, Space, Spin, Typography } from 'antd';
import {
  inspectBrokerTimer,
  type BrokerTimerSnapshot,
  type TimerSection,
} from '../api/brokerTimer';

const configurationFields = [
  ['timerWheelEnable', 'Timer wheel enabled'],
  ['timerStopEnqueue', 'Enqueue paused'],
  ['timerStopDequeue', 'Dequeue paused'],
  ['timerRocksDBEnable', 'RocksDB timer enabled'],
  ['timerRocksDBStopScan', 'RocksDB scan paused'],
  ['recallMessageEnable', 'Recall enabled'],
  ['timerPrecisionMs', 'Precision (ms)'],
  ['timerMaxDelaySec', 'Maximum delay (seconds)'],
];

const runtimeFields = [
  ['timerReadBehind', 'Dequeue behind (seconds)'],
  ['timerOffsetBehind', 'Enqueue behind (queue positions)'],
  ['timerCongestNum', 'Timer wheel pending entries'],
  ['timerEnqueueTps', 'Enqueue rate (messages/s)'],
  ['timerDequeueTps', 'Dequeue rate (messages/s)'],
];

function Section({
  title,
  data,
  fields,
}: {
  title: string;
  data: TimerSection;
  fields: string[][];
}) {
  return (
    <section aria-label={title}>
      <Typography.Title level={5}>{title}</Typography.Title>
      {data.error && <Alert type="warning" showIcon message={data.error} />}
      <Descriptions
        bordered
        size="small"
        column={1}
        items={fields.map(([key, label]) => ({
          key,
          label: <span title={key}>{label}</span>,
          children: <Typography.Text code>{data.values[key] ?? 'Not reported'}</Typography.Text>,
        }))}
      />
    </section>
  );
}

export default function BrokerTimerDialog({
  instanceId,
  brokerName,
  onClose,
}: {
  instanceId: string;
  brokerName: string;
  onClose: () => void;
}) {
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [snapshot, setSnapshot] = useState<BrokerTimerSnapshot>();
  const [error, setError] = useState<string>();

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    void Promise.resolve().then(async () => {
      if (!active) return;
      setLoading(true);
      setSnapshot(undefined);
      setError(undefined);
      try {
        const result = await inspectBrokerTimer(instanceId, brokerName, controller.signal);
        if (active) setSnapshot(result);
      } catch (failure) {
        if (active)
          setError(failure instanceof Error ? failure.message : 'Timer inspection failed');
      } finally {
        if (active) setLoading(false);
      }
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [instanceId, brokerName, revision]);

  return (
    <Modal
      open
      title={`Timer status: ${brokerName}`}
      onCancel={onClose}
      width={820}
      styles={{ body: { maxHeight: '70vh', overflowY: 'auto' } }}
      footer={
        <Space>
          <Button loading={loading} onClick={() => setRevision((value) => value + 1)}>
            Refresh snapshot
          </Button>
          <Button onClick={onClose}>Close</Button>
        </Space>
      }
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          type="info"
          showIcon
          message="Read-only timer diagnostics"
          description="Pending entries include messages that are not due yet. Zero metrics do not prove the timer service is enabled or healthy. Configuration and runtime are sampled separately."
        />
        {error && <Alert type="error" showIcon message={error} />}
        {loading && <Spin aria-label="Loading timer status" />}
        {snapshot && (
          <>
            <Typography.Text type="secondary">
              {instanceId} / {snapshot.address} · {new Date(snapshot.sampledAt).toLocaleString()}
            </Typography.Text>
            {snapshot.configuration.values.timerWheelEnable === 'false' && (
              <Alert
                type="warning"
                showIcon
                message="Timer wheel is disabled; reported zero metrics do not indicate normal operation."
              />
            )}
            {(snapshot.configuration.error || snapshot.runtime.error) && (
              <Alert
                type="warning"
                showIcon
                message="Partial snapshot: a data source is unavailable."
              />
            )}
            <Section
              title="Timer configuration"
              data={snapshot.configuration}
              fields={configurationFields}
            />
            <Section title="Timer runtime" data={snapshot.runtime} fields={runtimeFields} />
          </>
        )}
      </Space>
    </Modal>
  );
}
