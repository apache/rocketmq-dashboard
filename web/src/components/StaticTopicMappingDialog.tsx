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
  Card,
  Descriptions,
  Empty,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  inspectStaticTopicMapping,
  type MappingNode,
  type MappingSegment,
  type StaticMappingSnapshot,
  type StaticMappingTarget,
} from '../api/staticTopicMapping';

function NodeMapping({ node }: { node: MappingNode }) {
  const advertised = node.advertised;
  const local = node.local;
  const changed =
    advertised &&
    local &&
    (advertised.epoch !== local.epoch ||
      advertised.scope !== local.scope ||
      advertised.totalQueues !== local.totalQueues);
  return (
    <Card
      size="small"
      title={
        <Space>
          <Typography.Text>{node.brokerName}</Typography.Text>
          <Tag color={node.status === 'UNAVAILABLE' ? 'orange' : 'blue'}>{node.status}</Tag>
        </Space>
      }
    >
      <Space direction="vertical" style={{ width: '100%' }}>
        <Typography.Text code>{node.address || 'No registered master'}</Typography.Text>
        {node.error && <Alert type="warning" message={node.error} />}
        {node.status === 'NO_MAPPING' && (
          <Alert
            type="info"
            message="This broker returned no local static mapping"
            description="This may be an ordinary topic or a changing route. It does not prove that every broker has no mapping."
          />
        )}
        {changed && (
          <Alert
            type="warning"
            message="Route and local mapping metadata differ"
            description="These are separate samples. Re-read and inspect migration state before drawing a consistency conclusion."
          />
        )}
        <Descriptions
          bordered
          size="small"
          column={1}
          items={[
            {
              key: 'route',
              label: 'Route epoch / scope / total logical queues',
              children: advertised
                ? `${advertised.epoch} / ${advertised.scope} / ${advertised.totalQueues}`
                : 'No advertised mapping',
            },
            {
              key: 'local',
              label: 'Local epoch / scope / total logical queues',
              children: local
                ? `${local.epoch} / ${local.scope} / ${local.totalQueues}`
                : 'Unavailable',
            },
            {
              key: 'dirty',
              label: 'Local dirty flag',
              children: local ? String(local.dirty) : 'Unavailable',
            },
          ]}
        />
        {advertised && (
          <>
            <Typography.Text strong>Advertised current queue IDs</Typography.Text>
            {advertised.currentQueues.length ? (
              <Space wrap>
                {advertised.currentQueues.map((queue) => (
                  <Tag key={queue.logicalQueueId}>
                    Logical {queue.logicalQueueId} → physical {queue.physicalQueueId}
                  </Tag>
                ))}
              </Space>
            ) : (
              <Typography.Text type="secondary">
                No current queue IDs advertised by this broker.
              </Typography.Text>
            )}
          </>
        )}
        {local && local.queues.length === 0 && (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No hosted queue history" />
        )}
        {local?.queues.map((queue) => (
          <section key={queue.logicalQueueId} style={{ width: '100%' }}>
            <Typography.Title level={5}>Logical queue {queue.logicalQueueId}</Typography.Title>
            <Typography.Paragraph type="secondary">
              Last mapped broker in this local history: {queue.lastMappedBroker}
            </Typography.Paragraph>
            <Table<MappingSegment>
              size="small"
              dataSource={queue.segments}
              rowKey="generation"
              pagination={false}
              scroll={{ x: 730 }}
              columns={[
                { title: 'Generation', dataIndex: 'generation', width: 100 },
                { title: 'Broker', dataIndex: 'brokerName', width: 140 },
                { title: 'Physical queue', dataIndex: 'physicalQueueId', width: 120 },
                {
                  title: 'Logical start',
                  dataIndex: 'logicalStart',
                  render: (value) => <code>{value === '-1' ? '-1 (undecided)' : value}</code>,
                },
                {
                  title: 'Physical start',
                  dataIndex: 'physicalStart',
                  render: (value) => <code>{value}</code>,
                },
                {
                  title: 'Physical end (exclusive)',
                  dataIndex: 'physicalEndExclusive',
                  render: (value) => <code>{value === '-1' ? '-1 (open)' : value}</code>,
                },
              ]}
            />
          </section>
        ))}
      </Space>
    </Card>
  );
}

export default function StaticTopicMappingDialog({
  target,
  onClose,
}: {
  target: StaticMappingTarget;
  onClose: () => void;
}) {
  const [snapshot, setSnapshot] = useState<StaticMappingSnapshot>();
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
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
  const read = async () => {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setError(undefined);
    setSnapshot(undefined);
    abort.current = new AbortController();
    try {
      const value = await inspectStaticTopicMapping(target, abort.current.signal);
      if (active.current) setSnapshot(value);
    } catch (failure) {
      if (active.current)
        setError(failure instanceof Error ? failure.message : 'Mapping inspection failed');
    } finally {
      if (active.current) {
        busyRef.current = false;
        setBusy(false);
      }
    }
  };
  return (
    <Modal
      open
      title={`Static queue mapping · ${target.topic}`}
      width={1000}
      style={{ top: 24 }}
      onCancel={onClose}
      footer={
        <Space>
          <Button onClick={onClose}>Close</Button>
          <Button type="primary" loading={busy} disabled={busy} onClick={() => void read()}>
            Read mappings
          </Button>
        </Space>
      }
    >
      <Space
        direction="vertical"
        size="middle"
        style={{ width: '100%', maxHeight: 'calc(100vh - 210px)', overflowY: 'auto' }}
      >
        <Alert
          showIcon
          type="info"
          message="Read-only route and broker mapping samples"
          description="Reads registered masters for brokers named by the current route and mapping advertisements. Historical brokers absent from the route are not queried. Samples are not atomic and do not prove cluster-wide ownership."
        />
        <Typography.Paragraph type="secondary">
          Segments remain in the broker's original order. Offsets are queue offsets, not CommitLog
          positions. For a decided segment: logical offset = logical start + physical offset −
          physical start. The end is exclusive; -1 marks an undecided logical start or open physical
          end. This view does not measure retained messages or migrate queues.
        </Typography.Paragraph>
        {error && <Alert type="error" message={error} />}
        {snapshot && (
          <>
            <Typography.Text type="secondary">
              Sample interval: {snapshot.startedAt} — {snapshot.finishedAt}
            </Typography.Text>
            {snapshot.partial && (
              <Alert
                type="warning"
                message="Some broker mappings are unavailable"
                description="Successful samples remain visible. Missing data must not be interpreted as no mapping."
              />
            )}
            {snapshot.nodes.map((node) => (
              <NodeMapping key={node.brokerName} node={node} />
            ))}
          </>
        )}
      </Space>
    </Modal>
  );
}
