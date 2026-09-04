/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Drawer,
  Empty,
  Flex,
  Input,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Timeline,
  Typography,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { ApartmentOutlined, DownloadOutlined } from '@ant-design/icons';
import { listSystemAlertsPage } from '../services/opsService';
import { useLang } from '../i18n/LangContext';
import { buildCsv, downloadCsv, type CsvColumn } from '../utils/download';
import { formatUtcDateTime } from '../utils/format';
import { tableScrollX } from '../utils/table';
import {
  analyzeSystemAlertIncidents,
  filterSystemAlertIncidents,
  type IncidentStatus,
  type SystemAlertIncident,
  type SystemAlertIncidentAnalysis,
} from '../utils/systemAlertIncidents';

interface SystemAlertIncidentExplorerDrawerProps {
  open: boolean;
  onClose: () => void;
}

const PAGE_SIZE = 100;
const MAX_RECORDS = 10_000;

const CSV_COLUMNS: CsvColumn<SystemAlertIncident>[] = [
  { header: 'Correlation Key', value: (row) => row.key },
  { header: 'Correlation Source', value: (row) => row.correlationSource },
  { header: 'Title', value: (row) => row.title },
  { header: 'Instance', value: (row) => row.instanceId },
  { header: 'Domain', value: (row) => row.domain },
  { header: 'Level', value: (row) => row.level },
  { header: 'Status', value: (row) => row.status },
  { header: 'Events', value: (row) => row.eventCount },
  { header: 'Firing Events', value: (row) => row.firingCount },
  { header: 'Resolved Events', value: (row) => row.resolvedCount },
  { header: 'Suppressed Events', value: (row) => row.suppressedCount },
  { header: 'Acknowledged Events', value: (row) => row.acknowledgedCount },
  { header: 'First Seen (UTC)', value: (row) => row.firstSeen },
  { header: 'Last Seen (UTC)', value: (row) => row.lastSeen },
  { header: 'Duration (ms)', value: (row) => row.durationMs },
  { header: 'Rule IDs', value: (row) => row.ruleIds.join(';') },
];

const loadCompleteAlerts = async () => {
  const first = await listSystemAlertsPage({ page: 1, pageSize: PAGE_SIZE });
  const records = [...first.items];
  const target = Math.min(first.total, MAX_RECORDS);
  for (let page = 2; records.length < target; page += 1) {
    const result = await listSystemAlertsPage({ page, pageSize: PAGE_SIZE });
    records.push(...result.items);
    if (result.items.length === 0) break;
  }
  return { records: records.slice(0, MAX_RECORDS), total: first.total };
};

const formatDuration = (value: number | null) => {
  if (value === null) return '-';
  if (value < 60_000) return `${Math.round(value / 1000)} s`;
  if (value < 3_600_000) return `${Math.round(value / 60_000)} min`;
  return `${Math.round(value / 360_000) / 10} h`;
};

const statusColor: Record<IncidentStatus, string> = {
  ACTIVE: 'error',
  RESOLVED: 'success',
  UNKNOWN: 'default',
};

const SystemAlertIncidentExplorerDrawer = ({
  open,
  onClose,
}: SystemAlertIncidentExplorerDrawerProps) => {
  const { t } = useLang();
  const [analysis, setAnalysis] = useState<SystemAlertIncidentAnalysis | null>(null);
  const [sourceTotal, setSourceTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<IncidentStatus | 'ALL'>('ALL');
  const [domain, setDomain] = useState<'BUSINESS' | 'CLUSTER' | 'ALL'>('ALL');
  const [level, setLevel] = useState('ALL');
  const [unacknowledgedOnly, setUnacknowledgedOnly] = useState(false);
  const requestIdRef = useRef(0);

  const visibleIncidents = useMemo(
    () =>
      filterSystemAlertIncidents(analysis?.incidents ?? [], {
        search,
        status,
        domain,
        level,
        unacknowledgedOnly,
      }),
    [analysis, domain, level, search, status, unacknowledgedOnly],
  );

  const loadIncidents = async () => {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    setLoadError(false);
    try {
      const result = await loadCompleteAlerts();
      if (requestId !== requestIdRef.current) return;
      setAnalysis(analyzeSystemAlertIncidents(result.records));
      setSourceTotal(result.total);
      setSearch('');
      setStatus('ALL');
      setDomain('ALL');
      setLevel('ALL');
      setUnacknowledgedOnly(false);
    } catch {
      if (requestId === requestIdRef.current) setLoadError(true);
    } finally {
      if (requestId === requestIdRef.current) setLoading(false);
    }
  };

  const exportIncidents = () => {
    downloadCsv('rocketmq-system-alert-incidents.csv', buildCsv(CSV_COLUMNS, visibleIncidents));
    message.success(t('incidentExplorer.exported', { count: visibleIncidents.length }));
  };

  const columns: TableColumnsType<SystemAlertIncident> = [
    {
      title: t('incidentExplorer.titleColumn'),
      dataIndex: 'title',
      key: 'title',
      width: 260,
      fixed: 'left',
      ellipsis: true,
      render: (value: string) => <span title={value}>{value}</span>,
    },
    {
      title: t('incidentExplorer.status'),
      dataIndex: 'status',
      width: 110,
      render: (value: IncidentStatus) => (
        <Tag color={statusColor[value]}>{t(`incidentExplorer.status.${value}`)}</Tag>
      ),
    },
    { title: t('incidentExplorer.level'), dataIndex: 'level', width: 100 },
    { title: t('incidentExplorer.domain'), dataIndex: 'domain', width: 110 },
    {
      title: t('incidentExplorer.instance'),
      dataIndex: 'instanceId',
      width: 160,
      ellipsis: true,
      render: (value: string) => <span title={value}>{value || '-'}</span>,
    },
    {
      title: t('incidentExplorer.source'),
      dataIndex: 'correlationSource',
      width: 135,
      render: (value: SystemAlertIncident['correlationSource']) =>
        t(`incidentExplorer.source.${value}`),
    },
    { title: t('incidentExplorer.events'), dataIndex: 'eventCount', width: 90 },
    {
      title: t('incidentExplorer.acknowledged'),
      width: 120,
      render: (_, row) => `${row.acknowledgedCount}/${row.eventCount}`,
    },
    { title: t('incidentExplorer.suppressed'), dataIndex: 'suppressedCount', width: 110 },
    {
      title: t('incidentExplorer.duration'),
      dataIndex: 'durationMs',
      width: 110,
      render: formatDuration,
    },
    {
      title: t('incidentExplorer.lastSeen'),
      dataIndex: 'lastSeen',
      width: 180,
      render: (value: string) => formatUtcDateTime(value),
    },
  ];

  return (
    <Drawer
      title={t('incidentExplorer.title')}
      open={open}
      width={1200}
      destroyOnHidden
      onClose={() => {
        requestIdRef.current += 1;
        onClose();
      }}
    >
      <Flex vertical gap={16}>
        <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
          {t('incidentExplorer.description')}
        </Typography.Paragraph>
        <Flex justify="space-between" gap={12} wrap="wrap">
          <Alert
            type="info"
            showIcon
            message={t('incidentExplorer.boundary')}
            style={{ flex: 1 }}
          />
          <Button
            type="primary"
            icon={<ApartmentOutlined />}
            aria-label={t('incidentExplorer.load')}
            loading={loading}
            onClick={() => void loadIncidents()}
          >
            {t('incidentExplorer.load')}
          </Button>
        </Flex>
        {loadError && <Alert type="error" showIcon message={t('incidentExplorer.loadFailed')} />}
        {!analysis && !loading && !loadError && <Empty description={t('incidentExplorer.empty')} />}
        {analysis && (
          <>
            {sourceTotal > analysis.incidents.reduce((sum, item) => sum + item.eventCount, 0) && (
              <Alert
                type="warning"
                showIcon
                message={t('incidentExplorer.truncated', {
                  total: sourceTotal,
                  loaded: MAX_RECORDS,
                })}
              />
            )}
            <Flex gap={12} wrap="wrap">
              <Card size="small" style={{ flex: 1, minWidth: 130 }}>
                <Statistic
                  title={t('incidentExplorer.incidents')}
                  value={analysis.summary.incidents}
                />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 130 }}>
                <Statistic title={t('incidentExplorer.active')} value={analysis.summary.active} />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 130 }}>
                <Statistic
                  title={t('incidentExplorer.resolved')}
                  value={analysis.summary.resolved}
                />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 130 }}>
                <Statistic
                  title={t('incidentExplorer.suppressed')}
                  value={analysis.summary.suppressedEvents}
                />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 130 }}>
                <Statistic
                  title={t('incidentExplorer.unacknowledged')}
                  value={analysis.summary.unacknowledgedEvents}
                />
              </Card>
              <Card size="small" style={{ flex: 1, minWidth: 130 }}>
                <Statistic
                  title={t('incidentExplorer.longest')}
                  value={formatDuration(analysis.summary.longestDurationMs)}
                />
              </Card>
            </Flex>
            <Flex justify="space-between" gap={8} wrap="wrap">
              <Space wrap>
                <Input.Search
                  aria-label={t('incidentExplorer.search')}
                  placeholder={t('incidentExplorer.search')}
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  style={{ width: 250 }}
                  allowClear
                />
                <Select
                  aria-label={t('incidentExplorer.statusFilter')}
                  value={status}
                  onChange={setStatus}
                  style={{ width: 145 }}
                  options={[
                    { value: 'ALL', label: t('incidentExplorer.allStatuses') },
                    ...(['ACTIVE', 'RESOLVED', 'UNKNOWN'] as IncidentStatus[]).map((value) => ({
                      value,
                      label: t(`incidentExplorer.status.${value}`),
                    })),
                  ]}
                />
                <Select
                  aria-label={t('incidentExplorer.domainFilter')}
                  value={domain}
                  onChange={setDomain}
                  style={{ width: 145 }}
                  options={[
                    { value: 'ALL', label: t('incidentExplorer.allDomains') },
                    { value: 'BUSINESS', label: 'BUSINESS' },
                    { value: 'CLUSTER', label: 'CLUSTER' },
                  ]}
                />
                <Select
                  aria-label={t('incidentExplorer.levelFilter')}
                  value={level}
                  onChange={setLevel}
                  style={{ width: 135 }}
                  options={[
                    { value: 'ALL', label: t('incidentExplorer.allLevels') },
                    { value: 'error', label: 'ERROR' },
                    { value: 'warning', label: 'WARNING' },
                    { value: 'info', label: 'INFO' },
                  ]}
                />
                <Checkbox
                  checked={unacknowledgedOnly}
                  onChange={(event) => setUnacknowledgedOnly(event.target.checked)}
                >
                  {t('incidentExplorer.unackOnly')}
                </Checkbox>
              </Space>
              <Button
                icon={<DownloadOutlined />}
                aria-label={t('incidentExplorer.export')}
                disabled={visibleIncidents.length === 0}
                onClick={exportIncidents}
              >
                {t('incidentExplorer.export')}
              </Button>
            </Flex>
            <Typography.Text type="secondary">
              {t('incidentExplorer.visible', { count: visibleIncidents.length })}
            </Typography.Text>
            <Table
              rowKey="key"
              size="small"
              columns={columns}
              dataSource={visibleIncidents}
              pagination={{ pageSize: 20, showSizeChanger: true }}
              scroll={{ x: tableScrollX(columns) }}
              expandable={{
                expandedRowRender: (incident) => (
                  <Timeline
                    items={incident.alerts.map((event) => ({
                      color:
                        event.transition === 'RESOLVED'
                          ? 'green'
                          : event.level === 'error'
                            ? 'red'
                            : 'blue',
                      children: (
                        <Flex vertical gap={2}>
                          <Typography.Text>
                            {formatUtcDateTime(event.time)} ·{' '}
                            {event.transition ?? t('incidentExplorer.unknownTransition')}
                          </Typography.Text>
                          <Typography.Text type="secondary">
                            #{event.id} · {event.description || event.title}
                          </Typography.Text>
                          <Space>
                            <Tag>
                              {event.acknowledged
                                ? t('incidentExplorer.acked')
                                : t('incidentExplorer.unacked')}
                            </Tag>
                            {event.notificationSuppressed && (
                              <Tag color="warning">{t('incidentExplorer.suppressed')}</Tag>
                            )}
                          </Space>
                        </Flex>
                      ),
                    }))}
                  />
                ),
              }}
            />
          </>
        )}
      </Flex>
    </Drawer>
  );
};

export default SystemAlertIncidentExplorerDrawer;
