/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
import { useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Drawer,
  Empty,
  Flex,
  Input,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import type { ProducerConnection } from '../api/producer';
import { useLang } from '../i18n/LangContext';
import { buildCsv, downloadCsv, type CsvColumn } from '../utils/download';
import { tableScrollX } from '../utils/table';
import {
  analyzeProducerGroupComposition,
  filterProducerGroupComposition,
  type ProducerDistributionItem,
  type ProducerGroupCompositionRow,
  type ProducerGroupFinding,
  type ProducerGroupHealth,
} from '../utils/producerGroupComposition';

interface Props {
  open: boolean;
  instanceId: string;
  topic: string;
  connections: ProducerConnection[];
  onClose: () => void;
}
const CSV_COLUMNS: CsvColumn<ProducerGroupCompositionRow>[] = [
  { header: 'Producer Group', value: (r) => r.producerGroup },
  { header: 'Health', value: (r) => r.health },
  { header: 'Connections', value: (r) => r.connections },
  { header: 'Unique Clients', value: (r) => r.uniqueClients },
  { header: 'Unique Addresses', value: (r) => r.uniqueAddresses },
  { header: 'Languages', value: (r) => r.languages.map((i) => `${i.value}:${i.count}`).join(';') },
  { header: 'Versions', value: (r) => r.versions.map((i) => `${i.value}:${i.count}`).join(';') },
  { header: 'Findings', value: (r) => r.findings.join(';') },
  { header: 'Client IDs', value: (r) => r.clientIds.join(';') },
  { header: 'Addresses', value: (r) => r.addresses.join(';') },
];
const healthColor: Record<ProducerGroupHealth, string> = {
  HEALTHY: 'success',
  WARNING: 'warning',
  CRITICAL: 'error',
};

const ProducerGroupCompositionDrawer = ({
  open,
  instanceId,
  topic,
  connections,
  onClose,
}: Props) => {
  const { t } = useLang();
  const report = useMemo(() => analyzeProducerGroupComposition(connections), [connections]);
  const [search, setSearch] = useState('');
  const [health, setHealth] = useState<ProducerGroupHealth | 'ALL'>('ALL');
  const [finding, setFinding] = useState<ProducerGroupFinding | 'ALL'>('ALL');
  const rows = useMemo(
    () => filterProducerGroupComposition(report.rows, { search, health, finding }),
    [finding, health, report.rows, search],
  );
  const findingLabel = (value: ProducerGroupFinding) => t(`producerComposition.finding.${value}`);
  const tags = (items: ProducerDistributionItem[]) =>
    items.length === 0
      ? '-'
      : items.map((item) => (
          <Tag key={item.value}>
            {item.value}: {item.count}
          </Tag>
        ));
  const columns: TableColumnsType<ProducerGroupCompositionRow> = [
    {
      title: t('producerComposition.group'),
      dataIndex: 'producerGroup',
      width: 210,
      fixed: 'left',
      ellipsis: true,
      render: (value: string) => (
        <span title={value}>{value || t('producerComposition.unreported')}</span>
      ),
    },
    {
      title: t('producerComposition.health'),
      dataIndex: 'health',
      width: 110,
      render: (value: ProducerGroupHealth) => (
        <Tag color={healthColor[value]}>{t(`producerComposition.health.${value}`)}</Tag>
      ),
    },
    { title: t('producerComposition.connections'), dataIndex: 'connections', width: 105 },
    { title: t('producerComposition.clients'), dataIndex: 'uniqueClients', width: 100 },
    { title: t('producerComposition.addresses'), dataIndex: 'uniqueAddresses', width: 110 },
    { title: t('producerComposition.languages'), dataIndex: 'languages', width: 220, render: tags },
    { title: t('producerComposition.versions'), dataIndex: 'versions', width: 240, render: tags },
    {
      title: t('producerComposition.findings'),
      dataIndex: 'findings',
      width: 300,
      render: (values: ProducerGroupFinding[]) =>
        values.length === 0
          ? t('producerComposition.noFindings')
          : values.map((value) => <Tag key={value}>{findingLabel(value)}</Tag>),
    },
  ];
  const exportReport = () => {
    downloadCsv(
      `rocketmq-producer-group-composition-${instanceId}.csv`,
      buildCsv(CSV_COLUMNS, rows),
    );
    message.success(t('producerComposition.exported', { count: rows.length }));
  };
  return (
    <Drawer
      title={t('producerComposition.title')}
      open={open}
      onClose={onClose}
      width={1120}
      destroyOnHidden
    >
      <Flex vertical gap={16}>
        <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
          {t('producerComposition.description')}
        </Typography.Paragraph>
        <Alert
          type="info"
          showIcon
          message={t('producerComposition.scope', { instance: instanceId, topic })}
        />
        {connections.length === 0 ? (
          <Empty description={t('producerComposition.empty')} />
        ) : (
          <>
            <Flex gap={12} wrap="wrap">
              <Card size="small" style={{ flex: 1 }}>
                <Statistic title={t('producerComposition.groups')} value={report.summary.groups} />
              </Card>
              <Card size="small" style={{ flex: 1 }}>
                <Statistic
                  title={t('producerComposition.connections')}
                  value={report.summary.connections}
                />
              </Card>
              <Card size="small" style={{ flex: 1 }}>
                <Statistic
                  title={t('producerComposition.healthy')}
                  value={report.summary.healthy}
                />
              </Card>
              <Card size="small" style={{ flex: 1 }}>
                <Statistic
                  title={t('producerComposition.warning')}
                  value={report.summary.warning}
                />
              </Card>
              <Card size="small" style={{ flex: 1 }}>
                <Statistic
                  title={t('producerComposition.critical')}
                  value={report.summary.critical}
                />
              </Card>
            </Flex>
            <Flex justify="space-between" gap={8} wrap="wrap">
              <Space wrap>
                <Input.Search
                  aria-label={t('producerComposition.search')}
                  placeholder={t('producerComposition.search')}
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  allowClear
                  style={{ width: 260 }}
                />
                <Select
                  aria-label={t('producerComposition.healthFilter')}
                  value={health}
                  onChange={setHealth}
                  style={{ width: 150 }}
                  options={[
                    { value: 'ALL', label: t('producerComposition.allHealth') },
                    ...(['HEALTHY', 'WARNING', 'CRITICAL'] as ProducerGroupHealth[]).map(
                      (value) => ({ value, label: t(`producerComposition.health.${value}`) }),
                    ),
                  ]}
                />
                <Select
                  aria-label={t('producerComposition.findingFilter')}
                  value={finding}
                  onChange={setFinding}
                  style={{ width: 220 }}
                  options={[
                    { value: 'ALL', label: t('producerComposition.allFindings') },
                    ...(
                      [
                        'DUPLICATE_CLIENT_ID',
                        'MIXED_VERSION',
                        'MIXED_LANGUAGE',
                        'INCOMPLETE_METADATA',
                        'UNREPORTED_GROUP',
                      ] as ProducerGroupFinding[]
                    ).map((value) => ({ value, label: findingLabel(value) })),
                  ]}
                />
              </Space>
              <Button
                icon={<DownloadOutlined />}
                aria-label={t('producerComposition.export')}
                disabled={rows.length === 0}
                onClick={exportReport}
              >
                {t('producerComposition.export')}
              </Button>
            </Flex>
            <Typography.Text type="secondary">
              {t('producerComposition.visible', { count: rows.length })}
            </Typography.Text>
            <Table
              rowKey="key"
              size="small"
              columns={columns}
              dataSource={rows}
              pagination={{ pageSize: 20 }}
              scroll={{ x: tableScrollX(columns) }}
              expandable={{
                expandedRowRender: (row) => (
                  <Flex vertical gap={8}>
                    <Typography.Text strong>{t('producerComposition.clientIds')}</Typography.Text>
                    <Typography.Text copyable>{row.clientIds.join(', ') || '-'}</Typography.Text>
                    <Typography.Text strong>
                      {t('producerComposition.clientAddresses')}
                    </Typography.Text>
                    <Typography.Text copyable>{row.addresses.join(', ') || '-'}</Typography.Text>
                  </Flex>
                ),
              }}
            />
          </>
        )}
      </Flex>
    </Drawer>
  );
};
export default ProducerGroupCompositionDrawer;
