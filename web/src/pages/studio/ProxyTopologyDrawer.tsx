/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import { useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Col,
  Drawer,
  Flex,
  Input,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { ProxyNode, ProxyTopologyNode } from '../../api/proxy';
import { useLang } from '../../i18n/LangContext';
import { buildCsv, downloadCsv } from '../../utils/download';
import {
  buildProxyTopologyReport,
  filterProxyTopologyRows,
  proxyTopologyCsvRows,
} from './proxyTopologyReport';
import type { ProxyHostGroup, ProxyTopologyRow, ProxyTopologyStatus } from './proxyTopologyReport';

interface Props {
  open: boolean;
  registeredNodes: ProxyNode[];
  topologyNodes: ProxyTopologyNode[];
  onClose: () => void;
}

const statusColor: Record<ProxyTopologyStatus, string> = {
  UP: 'green',
  PARTIAL: 'orange',
  DOWN: 'red',
  REGISTERED_ONLY: 'default',
  DISCOVERED_ONLY: 'purple',
};

export const ProxyTopologyDrawer = ({ open, registeredNodes, topologyNodes, onClose }: Props) => {
  const { t } = useLang();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<ProxyTopologyStatus>();
  const [gapOnly, setGapOnly] = useState(false);
  const report = useMemo(
    () => buildProxyTopologyReport(registeredNodes, topologyNodes),
    [registeredNodes, topologyNodes],
  );
  const filteredRows = useMemo(
    () => filterProxyTopologyRows(report.rows, { search, status, registrationGapOnly: gapOnly }),
    [gapOnly, report.rows, search, status],
  );

  const statusLabel = (value: ProxyTopologyStatus) =>
    t(
      value === 'UP'
        ? 'proxy.topologyUp'
        : value === 'PARTIAL'
          ? 'proxy.topologyPartial'
          : value === 'DOWN'
            ? 'proxy.topologyDown'
            : value === 'REGISTERED_ONLY'
              ? 'proxy.topologyRegisteredOnly'
              : 'proxy.topologyDiscoveredOnly',
    );

  const renderReachability = (reachable: boolean | null) => {
    if (reachable === null) return <Typography.Text type="secondary">-</Typography.Text>;
    return (
      <Tag color={reachable ? 'green' : 'red'}>
        {reachable ? t('proxy.topologyReachable') : t('proxy.topologyUnreachable')}
      </Tag>
    );
  };

  const nodeColumns: ColumnsType<ProxyTopologyRow> = [
    {
      title: t('common.address'),
      dataIndex: 'address',
      key: 'address',
      fixed: 'left',
      width: 220,
      render: (address: string, row) => (
        <Space>
          <Typography.Text code>{address}</Typography.Text>
          {row.selected && <Tag color="blue">{t('proxy.current')}</Tag>}
        </Space>
      ),
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 150,
      render: (value: ProxyTopologyStatus) => (
        <Tag color={statusColor[value]}>{statusLabel(value)}</Tag>
      ),
    },
    { title: t('proxy.topologyHost'), dataIndex: 'host', key: 'host', width: 180 },
    {
      title: t('proxy.topologyGrpc'),
      key: 'grpc',
      width: 170,
      render: (_, row) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>{row.grpcPort ?? '-'}</Typography.Text>
          {renderReachability(row.grpcReachable)}
        </Space>
      ),
    },
    {
      title: t('proxy.topologyRemoting'),
      key: 'remoting',
      width: 170,
      render: (_, row) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>{row.remotingPort ?? '-'}</Typography.Text>
          {renderReachability(row.remotingReachable)}
        </Space>
      ),
    },
    {
      title: t('proxy.topologyLatency'),
      dataIndex: 'latencyMs',
      key: 'latencyMs',
      width: 120,
      render: (value: number | null) => (value === null ? '-' : `${value} ms`),
      sorter: (left, right) => (left.latencyMs ?? Infinity) - (right.latencyMs ?? Infinity),
    },
  ];

  const hostColumns: ColumnsType<ProxyHostGroup> = [
    { title: t('proxy.topologyHost'), dataIndex: 'host', key: 'host' },
    { title: t('proxy.topologyNodeCount'), dataIndex: 'nodes', key: 'nodes' },
    {
      title: t('proxy.topologyReachableNodes'),
      key: 'reachableNodes',
      render: (_, row) => `${row.reachableNodes} / ${row.nodes}`,
    },
    {
      title: t('proxy.topologyPorts'),
      key: 'ports',
      render: (_, row) => row.ports.join(', ') || '-',
    },
    {
      title: t('common.address'),
      key: 'addresses',
      render: (_, row) => (
        <Space size={[4, 4]} wrap>
          {row.addresses.map((address) => (
            <Tag key={address}>{address}</Tag>
          ))}
        </Space>
      ),
    },
  ];

  const exportRows = () => {
    const csv = buildCsv(
      [
        { header: 'Address', value: (row) => row.address },
        { header: 'Host', value: (row) => row.host },
        { header: 'Registered port', value: (row) => row.registeredPort },
        { header: 'Status', value: (row) => row.status },
        { header: 'Registered', value: (row) => row.registered },
        { header: 'Selected', value: (row) => row.selected },
        { header: 'gRPC port', value: (row) => row.grpcPort },
        { header: 'gRPC reachable', value: (row) => row.grpcReachable },
        { header: 'Remoting port', value: (row) => row.remotingPort },
        { header: 'Remoting reachable', value: (row) => row.remotingReachable },
        { header: 'Latency ms', value: (row) => row.latencyMs },
      ],
      proxyTopologyCsvRows(filteredRows),
    );
    downloadCsv(`rocketmq-proxy-topology-${new Date().toISOString().slice(0, 10)}.csv`, csv);
  };

  const summaryCards = [
    ['proxy.topologyRegistered', report.summary.registeredNodes, undefined],
    ['proxy.topologyProbed', report.summary.probedNodes, undefined],
    ['proxy.topologyFullyReachable', report.summary.fullyReachable, '#389e0d'],
    [
      'proxy.topologyAttention',
      report.summary.degradedOrDown + report.summary.registrationGaps,
      report.summary.degradedOrDown + report.summary.registrationGaps ? '#cf1322' : undefined,
    ],
  ] as const;

  return (
    <Drawer
      title={t('proxy.topologyTitle')}
      open={open}
      onClose={onClose}
      width={1080}
      destroyOnHidden
      extra={
        <Button icon={<DownloadOutlined />} onClick={exportRows} disabled={!filteredRows.length}>
          {t('proxy.topologyExport')}
        </Button>
      }
    >
      <Alert
        type="info"
        showIcon
        message={t('proxy.topologyDescription')}
        style={{ marginBottom: 16 }}
      />
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        {summaryCards.map(([key, value, color]) => (
          <Col xs={12} lg={6} key={key}>
            <Card size="small">
              <Statistic title={t(key)} value={value} valueStyle={{ color }} />
            </Card>
          </Col>
        ))}
      </Row>
      {report.summary.registrationGaps > 0 && (
        <Alert
          type="warning"
          showIcon
          message={t('proxy.topologyGapWarning', { count: report.summary.registrationGaps })}
          style={{ marginBottom: 16 }}
        />
      )}
      <Flex gap={12} wrap style={{ marginBottom: 16 }}>
        <Input.Search
          allowClear
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder={t('proxy.topologySearch')}
          style={{ width: 280 }}
        />
        <Select<ProxyTopologyStatus>
          allowClear
          value={status}
          onChange={setStatus}
          placeholder={t('proxy.topologyAllStatuses')}
          style={{ width: 190 }}
          options={(['UP', 'PARTIAL', 'DOWN', 'REGISTERED_ONLY', 'DISCOVERED_ONLY'] as const).map(
            (value) => ({ value, label: statusLabel(value) }),
          )}
        />
        <Checkbox checked={gapOnly} onChange={(event) => setGapOnly(event.target.checked)}>
          {t('proxy.topologyGapOnly')}
        </Checkbox>
        <Typography.Text type="secondary" style={{ alignSelf: 'center' }}>
          {t('proxy.topologyFiltered', { visible: filteredRows.length, total: report.rows.length })}
        </Typography.Text>
      </Flex>
      <Tabs
        items={[
          {
            key: 'nodes',
            label: t('proxy.topologyNodesTab', { count: filteredRows.length }),
            children: (
              <Table<ProxyTopologyRow>
                rowKey="address"
                columns={nodeColumns}
                dataSource={filteredRows}
                size="small"
                scroll={{ x: 1010 }}
                pagination={{ pageSize: 20, showSizeChanger: true }}
              />
            ),
          },
          {
            key: 'hosts',
            label: t('proxy.topologyHostsTab', { count: report.hosts.length }),
            children: (
              <Table<ProxyHostGroup>
                rowKey="host"
                columns={hostColumns}
                dataSource={report.hosts}
                size="small"
                pagination={false}
              />
            ),
          },
        ]}
      />
    </Drawer>
  );
};

export default ProxyTopologyDrawer;
