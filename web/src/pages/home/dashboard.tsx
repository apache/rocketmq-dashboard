import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Flex,
  Progress,
  Row,
  Select,
  Skeleton,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ClusterOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { ListDashes, ArrowDown } from '@phosphor-icons/react';
import PageHeader from '../../components/PageHeader';
import StatusBadge from '../../components/StatusBadge';
import MiniBar from '../../components/MiniBar';
import MetricsExplorer from '../../components/MetricsExplorer';
import { CLUSTER_TYPE_MAP } from '../../constants/theme';
import { getDashboard } from '../../services/dashboardService';
import type { DashboardData } from '../../api/metrics';
import { supportsApacheRuntime, type Instance } from '../../api/instance';
import { listInstances } from '../../services/instanceService';
import { useLang } from '../../i18n/LangContext';
import DashboardTrafficInsights from './DashboardTrafficInsights';
import {
  buildDashboardTrafficInsights,
  formatTrafficPercent,
  formatTrafficTps,
  type TrafficTrendDirection,
} from '../../utils/dashboardTrafficInsights';
import { tableScrollX } from '../../utils/table';

const { Text } = Typography;

type ClusterRow = DashboardData['clusters'][number];

const trafficTrendColor: Record<TrafficTrendDirection, string> = {
  rising: 'green',
  falling: 'volcano',
  stable: 'blue',
  unknown: 'default',
};

const trafficTrendLabelKey: Record<TrafficTrendDirection, string> = {
  rising: 'dashboardTraffic.trendRising',
  falling: 'dashboardTraffic.trendFalling',
  stable: 'dashboardTraffic.trendStable',
  unknown: 'dashboardTraffic.trendUnknown',
};

const renderTopologyCount = (value: number | null) =>
  value === null ? 'N/A' : value.toLocaleString();

const formatTrafficTrendDelta = (value: number | null) => {
  if (value == null) return '';
  const sign = value > 0 ? '+' : '';
  return ` ${sign}${formatTrafficPercent(value)}`;
};

const DashboardPage = () => {
  const navigate = useNavigate();
  const { t } = useLang();
  const [dashboard, setDashboard] = useState<DashboardData | null>(null);
  const [dashboardInstanceId, setDashboardInstanceId] = useState<string>();
  const [instances, setInstances] = useState<Instance[]>([]);
  const [selectedInstanceId, setSelectedInstanceId] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const dashboardRequestIdRef = useRef(0);
  const clusterPagePath = selectedInstanceId
    ? `/cluster?instanceId=${encodeURIComponent(selectedInstanceId)}`
    : '/cluster';

  const loadDashboard = useCallback(async () => {
    const requestId = ++dashboardRequestIdRef.current;
    setLoading(true);
    setLoadError(false);
    try {
      const nextDashboard = await getDashboard(selectedInstanceId);
      if (requestId === dashboardRequestIdRef.current) {
        setDashboard(nextDashboard);
        setDashboardInstanceId(selectedInstanceId);
      }
    } catch {
      if (requestId === dashboardRequestIdRef.current) {
        setLoadError(true);
      }
    } finally {
      if (requestId === dashboardRequestIdRef.current) {
        setLoading(false);
      }
    }
  }, [selectedInstanceId]);

  useEffect(() => {
    let cancelled = false;
    void listInstances()
      .then((nextInstances) => {
        if (!cancelled) setInstances(nextInstances.filter(supportsApacheRuntime));
      })
      .catch(() => {
        if (!cancelled) setInstances([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    void Promise.resolve().then(loadDashboard);
  }, [loadDashboard]);

  const visibleDashboard = dashboardInstanceId === selectedInstanceId ? dashboard : null;

  const trafficInsights = useMemo(
    () => buildDashboardTrafficInsights(visibleDashboard),
    [visibleDashboard],
  );

  const dashboardHeader = (
    <PageHeader
      title={t('dashboard.title')}
      subtitle={t('dashboard.subtitle')}
      extra={
        <Space>
          <Select
            aria-label={t('dashboard.instanceFilter')}
            allowClear
            placeholder={t('dashboard.allInstances')}
            value={selectedInstanceId}
            onChange={setSelectedInstanceId}
            options={instances.map((instance) => ({ value: instance.name, label: instance.name }))}
            style={{ width: 220 }}
          />
          <Button onClick={() => void loadDashboard()} loading={loading}>
            {t('common.refresh')}
          </Button>
        </Space>
      }
    />
  );

  if (loading && !visibleDashboard) {
    return (
      <div style={{ padding: 24 }}>
        {dashboardHeader}
        <Skeleton active paragraph={{ rows: 8 }} />
      </div>
    );
  }

  if (loadError || !visibleDashboard) {
    return (
      <div style={{ padding: 24 }}>
        {dashboardHeader}
        <Alert
          type="error"
          showIcon
          message={t('dashboard.loadFailed')}
          description={t('dashboard.loadFailedDesc')}
          action={
            <Button size="small" onClick={() => void loadDashboard()} loading={loading}>
              {t('dashboard.retry')}
            </Button>
          }
        />
      </div>
    );
  }

  const { stats, clusters } = visibleDashboard;
  const trafficInsightByClusterId = new Map(trafficInsights.rows.map((row) => [row.id, row]));

  const statCards = [
    {
      title: t('dashboard.clusters'),
      value: stats.totalClusters,
      icon: <ClusterOutlined style={{ fontSize: 22, color: '#52c41a' }} />,
      color: '#52c41a',
      suffix: '',
      detail: `${stats.totalBrokers} Brokers · ${renderTopologyCount(stats.totalProxies)} Proxy`,
    },
    {
      title: t('dashboard.topics'),
      value: stats.totalTopics,
      icon: <ListDashes size={22} weight="duotone" color="#1677ff" />,
      color: '#1677ff',
      suffix: '',
      detail: `${stats.totalConsumerGroups} Consumer Groups`,
    },
    {
      title: t('dashboard.tpsIn'),
      value: stats.tpsIn,
      icon: <ArrowDown size={22} weight="duotone" color="#fa8c16" />,
      color: '#fa8c16',
      suffix: '/s',
      detail: `${t('dashboard.tpsOut')}: ${stats.tpsOut.toLocaleString()}/s`,
    },
    {
      title: t('dashboard.todayMessages'),
      value: stats.totalMessagesToday,
      icon: <ThunderboltOutlined style={{ fontSize: 22, color: '#722ed1' }} />,
      color: '#722ed1',
      suffix: '',
      detail: t('dashboard.millionMessages', {
        n: (stats.totalMessagesToday / 1_000_000).toFixed(1),
      }),
    },
  ];

  // Identity + topology only. The seven traffic columns this table used to carry pushed it to
  // 1620px — a permanent horizontal scrollbar — and duplicated the 流量洞察 card above, so they
  // now live in the expandable row instead.
  const clusterColumns: ColumnsType<ClusterRow> = [
    {
      title: t('dashboard.clusterName'),
      dataIndex: 'name',
      key: 'name',
      // The one flexible column: it absorbs the surplus on a wide window so the fixed columns
      // below keep their declared widths instead of all inflating proportionally.
      minWidth: 220,
      ellipsis: true,
      render: (name: string) => <Text strong>{name}</Text>,
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => <StatusBadge status={status} />,
    },
    {
      title: t('common.type'),
      dataIndex: 'type',
      key: 'type',
      width: 110,
      render: (type: string) => {
        const info = CLUSTER_TYPE_MAP[type];
        return info ? <Tag color={info.color}>{t(info.labelKey)}</Tag> : type;
      },
    },
    {
      title: t('common.version'),
      dataIndex: 'version',
      key: 'version',
      width: 110,
      render: (v: string) => <span style={{ fontSize: 14 }}>{v}</span>,
    },
    {
      title: t('dashboard.broker'),
      dataIndex: 'brokers',
      key: 'brokers',
      width: 90,
      align: 'center' as const,
      render: renderTopologyCount,
    },
    {
      title: t('dashboard.proxy'),
      dataIndex: 'proxies',
      key: 'proxies',
      width: 90,
      align: 'center' as const,
      render: renderTopologyCount,
    },
    {
      title: t('dashboard.topic'),
      dataIndex: 'topics',
      key: 'topics',
      width: 90,
      align: 'center' as const,
    },
    {
      title: t('dashboard.group'),
      dataIndex: 'groups',
      key: 'groups',
      width: 90,
      align: 'center' as const,
    },
  ];

  const renderClusterTraffic = (record: ClusterRow) => {
    const insight = trafficInsightByClusterId.get(record.id);
    const trendDirection = insight?.trendDirection ?? 'unknown';
    const items = [
      { key: 'tpsIn', label: t('dashboard.tpsIn'), children: `${record.tpsIn.toLocaleString()}/s` },
      {
        key: 'tpsOut',
        label: t('dashboard.tpsOut'),
        children: `${record.tpsOut.toLocaleString()}/s`,
      },
      {
        key: 'totalTps',
        label: t('dashboardTraffic.totalTps'),
        children: insight ? `${formatTrafficTps(insight.totalTps)}/s` : '-',
      },
      {
        key: 'perBroker',
        label: t('dashboardTraffic.perBroker'),
        children: insight ? `${formatTrafficTps(insight.perBrokerTps)}/s` : '-',
      },
      {
        key: 'inOutRatio',
        label: t('dashboardTraffic.inOutRatio'),
        children: insight?.inOutRatio == null ? t('common.na') : `${insight.inOutRatio}:1`,
      },
      {
        key: 'share',
        label: t('dashboardTraffic.share'),
        children: insight ? (
          <Flex align="center" gap={8}>
            <Text>{formatTrafficPercent(insight.sharePercent)}</Text>
            <Progress
              percent={Math.min(100, insight.sharePercent)}
              showInfo={false}
              size="small"
              style={{ width: 90, margin: 0 }}
            />
          </Flex>
        ) : (
          '-'
        ),
      },
      {
        key: 'trend',
        label: t('dashboard.trend'),
        children: (
          <Flex align="center" gap={8}>
            <MiniBar
              data={record.throughput}
              color={
                record.status === 'healthy'
                  ? '#52c41a'
                  : record.status === 'warning'
                    ? '#faad14'
                    : '#d9d9d9'
              }
              height={26}
              width={100}
            />
            <Tag color={trafficTrendColor[trendDirection]} style={{ marginInlineEnd: 0 }}>
              {t(trafficTrendLabelKey[trendDirection])}
              {formatTrafficTrendDelta(insight?.trendDeltaPercent ?? null)}
            </Tag>
          </Flex>
        ),
      },
    ];
    return <Descriptions size="small" column={{ xs: 1, sm: 2, md: 3, lg: 4 }} items={items} />;
  };

  return (
    <div style={{ padding: 24 }}>
      {dashboardHeader}

      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        {statCards.map((card) => (
          <Col xs={12} sm={12} md={6} key={card.title}>
            <Card size="small" style={{ borderTop: `3px solid ${card.color}`, borderRadius: 8 }}>
              <Statistic
                title={card.title}
                value={card.value}
                prefix={card.icon}
                suffix={card.suffix}
                valueStyle={{ fontSize: 28, fontWeight: 600 }}
              />
              <Text type="secondary" style={{ fontSize: 14, marginTop: 4, display: 'block' }}>
                {card.detail}
              </Text>
            </Card>
          </Col>
        ))}
      </Row>

      <DashboardTrafficInsights insights={trafficInsights} />

      <Card
        title={t('dashboard.clusterHealth')}
        extra={<a onClick={() => navigate(clusterPagePath)}>{t('common.viewAll')}</a>}
        styles={{ body: { padding: '0 20px 16px' } }}
      >
        <Table
          dataSource={clusters}
          columns={clusterColumns}
          rowKey="id"
          size="small"
          pagination={false}
          tableLayout="fixed"
          scroll={{ x: tableScrollX(clusterColumns, { expandable: true }) }}
          expandable={{
            expandedRowRender: renderClusterTraffic,
            rowExpandable: () => true,
          }}
          onRow={() => ({
            style: { cursor: 'pointer' },
          })}
        />
      </Card>

      <MetricsExplorer instanceId={selectedInstanceId} />
    </div>
  );
};

export default DashboardPage;
