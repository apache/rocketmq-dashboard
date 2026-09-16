/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
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
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { DownloadOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { GrafanaDashboardInfo } from '../api/metrics';
import { getGrafanaDashboard } from '../services/grafanaService';
import { useLang } from '../i18n/LangContext';
import { buildCsv, downloadCsv } from '../utils/download';
import {
  analyzeGrafanaDashboard,
  buildGrafanaDependencyManifest,
  filterGrafanaDependencyRows,
  grafanaDependencyCsvRows,
  type GrafanaDependencyRow,
} from './grafanaDependencyManifest';

interface Props {
  open: boolean;
  dashboards: GrafanaDashboardInfo[];
  onClose: () => void;
}

const loadInBatches = async (dashboards: GrafanaDashboardInfo[], size = 4) => {
  const rows: GrafanaDependencyRow[] = [];
  for (let index = 0; index < dashboards.length; index += size) {
    const batch = dashboards.slice(index, index + size);
    rows.push(
      ...(await Promise.all(
        batch.map(async (info) =>
          analyzeGrafanaDashboard(info, await getGrafanaDashboard(info.uid)),
        ),
      )),
    );
  }
  return rows;
};

export const GrafanaDependencyDrawer = ({ open, dashboards, onClose }: Props) => {
  const { t } = useLang();
  const [rows, setRows] = useState<GrafanaDependencyRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [search, setSearch] = useState('');
  const [issuesOnly, setIssuesOnly] = useState(false);
  const manifest = useMemo(() => buildGrafanaDependencyManifest(rows), [rows]);
  const filtered = useMemo(
    () => filterGrafanaDependencyRows(manifest.rows, search, issuesOnly),
    [issuesOnly, manifest.rows, search],
  );

  const load = async () => {
    setLoading(true);
    try {
      setRows(await loadInBatches(dashboards));
      setLoaded(true);
    } catch {
      message.error(t('grafana.manifestLoadFailed'));
    } finally {
      setLoading(false);
    }
  };

  const columns: ColumnsType<GrafanaDependencyRow> = [
    {
      title: t('grafana.title'),
      dataIndex: 'title',
      key: 'title',
      fixed: 'left',
      width: 210,
      render: (title: string, row) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{title}</Typography.Text>
          <Typography.Text code>{row.uid}</Typography.Text>
        </Space>
      ),
    },
    {
      title: t('grafana.manifestSchema'),
      dataIndex: 'schemaVersion',
      key: 'schemaVersion',
      width: 90,
      render: (value: number | null) => value ?? '-',
    },
    { title: t('grafana.manifestPanels'), dataIndex: 'panelCount', key: 'panelCount', width: 90 },
    {
      title: t('grafana.manifestPanelTypes'),
      key: 'panelTypes',
      width: 200,
      render: (_, row) => (
        <Space size={[2, 2]} wrap>
          {row.panelTypes.map((value) => (
            <Tag key={value}>{value}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: t('grafana.manifestDataSources'),
      key: 'dataSources',
      width: 220,
      render: (_, row) => row.dataSources.join(', ') || '-',
    },
    {
      title: t('grafana.manifestVariables'),
      key: 'variables',
      width: 180,
      render: (_, row) => row.variableNames.join(', ') || '-',
    },
    {
      title: t('grafana.manifestTargets'),
      dataIndex: 'targetCount',
      key: 'targetCount',
      width: 90,
    },
    {
      title: t('grafana.manifestIssues'),
      key: 'issues',
      width: 220,
      render: (_, row) =>
        row.issues.length ? (
          <Space size={[2, 2]} wrap>
            {row.issues.map((issue) => (
              <Tag color="orange" key={issue}>
                {issue}
              </Tag>
            ))}
          </Space>
        ) : (
          <Tag color="green">{t('grafana.manifestReady')}</Tag>
        ),
    },
  ];

  const exportRows = () => {
    const data = grafanaDependencyCsvRows(filtered);
    const csv = buildCsv(
      [
        { header: 'UID', value: (row) => row.uid },
        { header: 'Title', value: (row) => row.title },
        { header: 'Schema version', value: (row) => row.schemaVersion },
        { header: 'Panels', value: (row) => row.panels },
        { header: 'Panel types', value: (row) => row.panelTypes },
        { header: 'Data sources', value: (row) => row.dataSources },
        { header: 'Variables', value: (row) => row.variables },
        { header: 'Variable types', value: (row) => row.variableTypes },
        { header: 'Targets', value: (row) => row.targets },
        { header: 'Transformations', value: (row) => row.transformations },
        { header: 'Library panels', value: (row) => row.libraryPanels },
        { header: 'Repeated panels', value: (row) => row.repeatedPanels },
        { header: 'Alerts', value: (row) => row.alerts },
        { header: 'Issues', value: (row) => row.issues },
      ],
      data,
    );
    downloadCsv(`rocketmq-grafana-dependencies-${new Date().toISOString().slice(0, 10)}.csv`, csv);
  };

  const cards = [
    ['grafana.manifestDashboards', manifest.summary.dashboards],
    ['grafana.manifestPanels', manifest.summary.panels],
    ['grafana.manifestDataSources', manifest.summary.dataSources],
    ['grafana.manifestIssues', manifest.summary.dashboardsWithIssues],
  ] as const;

  return (
    <Drawer
      title={t('grafana.manifestTitle')}
      open={open}
      onClose={onClose}
      width={1080}
      destroyOnHidden
      extra={
        <Space>
          <Button icon={<DownloadOutlined />} disabled={!filtered.length} onClick={exportRows}>
            {t('common.export')}
          </Button>
          <Button type="primary" icon={<ReloadOutlined />} loading={loading} onClick={load}>
            {loaded ? t('common.refresh') : t('grafana.manifestLoad')}
          </Button>
        </Space>
      }
    >
      <Alert
        showIcon
        type="info"
        message={t('grafana.manifestDescription')}
        style={{ marginBottom: 16 }}
      />
      {!loaded ? (
        <Card>
          <Flex vertical align="center" gap={12}>
            <Typography.Text type="secondary">{t('grafana.manifestEmpty')}</Typography.Text>
            <Button type="primary" loading={loading} onClick={load}>
              {t('grafana.manifestLoad')}
            </Button>
          </Flex>
        </Card>
      ) : (
        <>
          <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
            {cards.map(([key, value]) => (
              <Col xs={12} lg={6} key={key}>
                <Card size="small">
                  <Statistic title={t(key)} value={value} />
                </Card>
              </Col>
            ))}
          </Row>
          <Flex gap={16} wrap style={{ marginBottom: 16 }}>
            <Input.Search
              allowClear
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={t('grafana.manifestSearch')}
              style={{ width: 320 }}
            />
            <Checkbox
              checked={issuesOnly}
              onChange={(event) => setIssuesOnly(event.target.checked)}
            >
              {t('grafana.manifestIssuesOnly')}
            </Checkbox>
            <Typography.Text type="secondary">
              {t('grafana.manifestFiltered', { visible: filtered.length, total: rows.length })}
            </Typography.Text>
          </Flex>
          <Table
            rowKey="uid"
            size="small"
            columns={columns}
            dataSource={filtered}
            scroll={{ x: 1300 }}
            pagination={{ pageSize: 20 }}
          />
        </>
      )}
    </Drawer>
  );
};

export default GrafanaDependencyDrawer;
