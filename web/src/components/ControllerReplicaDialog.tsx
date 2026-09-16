/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Descriptions, Modal, Space, Table, Tag, Typography } from 'antd';
import {
  inspectControllerReplicas,
  type ControllerReplicaSnapshot,
} from '../api/controllerReplicas';

export function ControllerReplicaDialog({
  instanceId,
  brokerName,
  onClose,
}: {
  instanceId: string;
  brokerName: string;
  onClose: () => void;
}) {
  const [snapshot, setSnapshot] = useState<ControllerReplicaSnapshot>();
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
  const read = async () => {
    if (locked.current) return;
    locked.current = true;
    setBusy(true);
    setError('');
    setSnapshot(undefined);
    const controller = new AbortController();
    request.current = controller;
    try {
      const result = await inspectControllerReplicas(instanceId, brokerName, controller.signal);
      if (active.current) setSnapshot(result);
    } catch (failure) {
      if (active.current)
        setError(failure instanceof Error ? failure.message : 'Controller inspection failed');
    } finally {
      locked.current = false;
      if (active.current) setBusy(false);
    }
  };
  const membership = snapshot?.membership;
  return (
    <Modal
      open
      title={`Controller replicas · ${brokerName}`}
      width={1000}
      style={{ top: 24 }}
      onCancel={onClose}
      footer={
        <Space>
          <Button onClick={onClose}>Close</Button>
          <Button type="primary" loading={busy} onClick={() => void read()}>
            Read controller state
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
          type="info"
          showIcon
          message="Controller observations are separate from broker replication progress."
          description="Matching metadata does not prove quorum health or failover readiness. Sync-set membership and liveness are reported independently. The SDK discovers the leader again when reading membership."
        />
        {error && <Alert type="error" showIcon message={error} />}
        {snapshot && (
          <>
            <Descriptions
              size="small"
              column={2}
              items={[
                { key: 'mode', label: 'Controller mode', children: snapshot.mode },
                { key: 'source', label: 'Broker config source', children: snapshot.configSource },
                {
                  key: 'agreement',
                  label: 'Metadata agreement',
                  children: (
                    <Tag color={snapshot.agreement === 'DIVERGENT' ? 'orange' : 'default'}>
                      {snapshot.agreement}
                    </Tag>
                  ),
                },
                { key: 'sampled', label: 'Read completed at', children: snapshot.sampledAt },
              ]}
            />
            {snapshot.controllers.map((node) => (
              <Card
                size="small"
                key={node.address}
                title={node.address}
                extra={
                  <Tag>
                    {node.leader === null
                      ? 'Unknown role'
                      : node.leader
                        ? 'Reports leader'
                        : 'Reports follower'}
                  </Tag>
                }
              >
                {node.error && <Alert type="warning" showIcon message={node.error} />}
                <Descriptions
                  size="small"
                  column={2}
                  items={[
                    {
                      key: 'group',
                      label: 'Controller group',
                      children: node.group ?? 'Unavailable',
                    },
                    {
                      key: 'leaderId',
                      label: 'Leader ID',
                      children: node.leaderId ?? 'Unavailable',
                    },
                    {
                      key: 'leaderAddress',
                      label: 'Leader address',
                      children: node.leaderAddress ?? 'Unavailable',
                    },
                    {
                      key: 'peers',
                      label: 'Reported peers',
                      children: (
                        <Typography.Text code style={{ overflowWrap: 'anywhere' }}>
                          {node.peers ?? 'Unavailable'}
                        </Typography.Text>
                      ),
                    },
                  ]}
                />
              </Card>
            ))}
            {snapshot.membershipError && (
              <Alert type="warning" showIcon message={snapshot.membershipError} />
            )}
            {membership && (
              <Card size="small" title="Broker replica membership">
                <Descriptions
                  size="small"
                  column={2}
                  items={[
                    {
                      key: 'discovery',
                      label: 'Leader discovery endpoint',
                      children: membership.discoveryAddress,
                    },
                    {
                      key: 'master',
                      label: 'Master ID / address',
                      children: `${membership.masterBrokerId ?? 'Unavailable'} / ${membership.masterAddress ?? 'Unavailable'}`,
                    },
                    { key: 'masterEpoch', label: 'Master epoch', children: membership.masterEpoch },
                    {
                      key: 'setEpoch',
                      label: 'Sync-set epoch',
                      children: membership.syncStateSetEpoch,
                    },
                  ]}
                />
                <Table
                  size="small"
                  pagination={false}
                  rowKey="brokerId"
                  dataSource={membership.replicas}
                  scroll={{ x: 600 }}
                  columns={[
                    { title: 'Broker ID', dataIndex: 'brokerId' },
                    {
                      title: 'Address',
                      dataIndex: 'address',
                      render: (value) => value ?? 'Unavailable',
                    },
                    {
                      title: 'Sync-set member',
                      dataIndex: 'inSyncSet',
                      render: (value) => (value ? 'In set' : 'Outside set'),
                    },
                    {
                      title: 'Controller liveness',
                      dataIndex: 'alive',
                      render: (value) => (
                        <Tag color={value === false ? 'orange' : 'default'}>
                          {value === null ? 'Unknown' : value ? 'Alive' : 'Not alive'}
                        </Tag>
                      ),
                    },
                  ]}
                />
              </Card>
            )}
          </>
        )}
      </Space>
    </Modal>
  );
}
