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
import { Alert, Button, Descriptions, Modal, Space, Spin, Table, Tag, Typography } from 'antd';
import {
  inspectBrokerHa,
  type BrokerHaSnapshot,
  type HaConnection,
  type HaNode,
} from '../api/brokerHa';

interface Props {
  instanceId: string;
  brokerName: string;
  onClose: () => void;
}

const timestamp = (value: number) =>
  value > 0 ? new Date(value).toLocaleString() : 'Not reported';

function NodeDetails({ node }: { node: HaNode }) {
  if (node.error) return <Alert type="error" showIcon message={node.error} />;
  if (node.master)
    return (
      <Table<HaConnection>
        size="small"
        pagination={false}
        dataSource={node.connections}
        rowKey={(row) => row.address}
        scroll={{ x: 880 }}
        locale={{ emptyText: 'No connected replicas reported by this master' }}
        columns={[
          { title: 'Replica address', dataIndex: 'address' },
          {
            title: 'In sync',
            dataIndex: 'inSync',
            render: (value: boolean) => (value ? 'Yes' : 'No'),
          },
          { title: 'Acknowledged offset', dataIndex: 'ackOffset' },
          { title: 'Difference (bytes)', dataIndex: 'differenceBytes' },
          { title: 'Transfer offset', dataIndex: 'transferOffset' },
          { title: 'Transfer (bytes/s)', dataIndex: 'bytesPerSecond' },
        ]}
      />
    );
  const replica = node.replica;
  if (!replica) return null;
  return (
    <Descriptions bordered size="small" column={2}>
      <Descriptions.Item label="Master address">
        {replica.masterAddress || 'Not reported'}
      </Descriptions.Item>
      <Descriptions.Item label="Replica max offset">{replica.maxOffset}</Descriptions.Item>
      <Descriptions.Item label="Master flush offset">{replica.masterFlushOffset}</Descriptions.Item>
      <Descriptions.Item label="Transfer (bytes/s)">{replica.bytesPerSecond}</Descriptions.Item>
      <Descriptions.Item label="Last read">
        {timestamp(replica.lastReadTimestamp)}
      </Descriptions.Item>
      <Descriptions.Item label="Last write">
        {timestamp(replica.lastWriteTimestamp)}
      </Descriptions.Item>
    </Descriptions>
  );
}

export default function BrokerHaDialog({ instanceId, brokerName, onClose }: Props) {
  const [snapshot, setSnapshot] = useState<BrokerHaSnapshot | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    void Promise.resolve()
      .then(() => {
        if (!active) return;
        setLoading(true);
        setSnapshot(null);
        setError(null);
        return inspectBrokerHa(instanceId, brokerName, controller.signal);
      })
      .then((result) => {
        if (active && result) setSnapshot(result);
      })
      .catch((failure: unknown) => {
        if (active) setError(failure instanceof Error ? failure.message : 'HA query failed');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [instanceId, brokerName, revision]);

  return (
    <Modal
      open
      title={`Replication status: ${brokerName}`}
      onCancel={onClose}
      width={1100}
      footer={
        <Space>
          <Button loading={loading} onClick={() => setRevision((value) => value + 1)}>
            Refresh snapshot
          </Button>
          <Button onClick={onClose}>Close</Button>
        </Space>
      }
    >
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        <Alert
          type="info"
          showIcon
          message="Physical log replication snapshot"
          description="Offsets and differences are bytes, not message counts. Nodes are sampled sequentially. This view does not establish failover readiness."
        />
        {error && <Alert type="error" showIcon message={error} />}
        <Spin spinning={loading}>
          {snapshot && (
            <Space direction="vertical" style={{ width: '100%' }}>
              <Typography.Text>Sample completed: {timestamp(snapshot.sampledAt)}</Typography.Text>
              {!snapshot.complete && (
                <Alert
                  type="warning"
                  showIcon
                  message="Partial snapshot: some replicas could not report HA state. Expand failed rows for details."
                />
              )}
              <Table<HaNode>
                size="small"
                pagination={false}
                dataSource={snapshot.nodes}
                rowKey="brokerId"
                scroll={{ x: 780 }}
                expandable={{ expandedRowRender: (node) => <NodeDetails node={node} /> }}
                columns={[
                  { title: 'Broker ID', dataIndex: 'brokerId' },
                  { title: 'Address', dataIndex: 'address' },
                  {
                    title: 'Reported role',
                    render: (_, node) =>
                      node.master === null ? 'Unavailable' : node.master ? 'Master' : 'Replica',
                  },
                  {
                    title: 'Max log offset (bytes)',
                    dataIndex: 'maxOffset',
                    render: (value) => value ?? '-',
                  },
                  {
                    title: 'In-sync replicas',
                    dataIndex: 'inSyncSlaveCount',
                    render: (value) => value ?? '-',
                  },
                  {
                    title: 'Query',
                    render: (_, node) => (
                      <Tag color={node.error ? 'error' : 'success'}>
                        {node.error ? 'Failed' : 'Available'}
                      </Tag>
                    ),
                  },
                ]}
              />
            </Space>
          )}
        </Spin>
      </Space>
    </Modal>
  );
}
