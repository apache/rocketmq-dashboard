import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  Col,
  Row,
  Select,
  Skeleton,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import { ClusterOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { ListDashes, ArrowDown } from '@phosphor-icons/react';
import PageHeader from '../../components/PageHeader';
import StatusBadge from '../../components/StatusBadge';
import MiniBar from '../../components/MiniBar';
import MetricsExplorer from '../../components/MetricsExplorer';
import { CLUSTER_TYPE_MAP } from '../../constants/theme';
import { supportsApacheRuntime, type Instance } from '../../api/instance';
import { getDashboard } from '../../services/dashboardService';
import type { DashboardData } from '../../api/metrics';
import { listInstances } from '../../services/instanceService';
import { useLang } from '../../i18n/LangContext';

const { Text } = Typography;

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

  const dashboardHeader = (
    <PageHeader
      title={t('dashboard.title')}
      subtitle={t('dashboard.subtitle')}
      extra={
        <Space>
          <Select
            aria-label="Dashboard instance"
            allowClear
            placeholder="All configured instances"
            value={selectedInstanceId}
            onChange={setSelectedInstanceId}
            options={instances.map((instance) => ({ value: instance.name, label: instance.name }))}
            style={{ width: 220 }}
          />
          <Button onClick={() => void loadDashboard()} loading={loading}>
            Refresh
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
          message="仪表盘加载失败"
          description="无法获取集群概览，请检查网络连接后重试。"
          action={
            <Button size="small" onClick={() => void loadDashboard()} loading={loading}>
              重试
            </Button>
          }
        />
      </div>
    );
  }

  const { stats, clusters } = visibleDashboard;

  const statCards = [
    {
      title: t('dashboard.clusters'),
      value: stats.totalClusters,
      icon: <ClusterOutlined style={{ fontSize: 22, color: '#52c41a' }} />,
      color: '#52c41a',
      suffix: '',
      detail: `${stats.totalBrokers} Brokers · ${stats.totalProxies} Proxy`,
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

  const clusterColumns = [
    {
      title: t('dashboard.clusterName'),
      dataIndex: 'name',
      key: 'name',
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
      render: (type: string) => {
        const info = CLUSTER_TYPE_MAP[type];
        return info ? <Tag color={info.color}>{t(info.labelKey)}</Tag> : type;
      },
    },
    {
      title: t('common.version'),
      dataIndex: 'version',
      key: 'version',
      render: (v: string) => <span style={{ fontSize: 13 }}>{v}</span>,
    },
    {
      title: t('dashboard.broker'),
      dataIndex: 'brokers',
      key: 'brokers',
      width: 80,
      align: 'center' as const,
      render: (v: number) => Math.max(0, v),
    },
    {
      title: t('dashboard.proxy'),
      dataIndex: 'proxies',
      key: 'proxies',
      width: 80,
      align: 'center' as const,
      render: (v: number) => Math.max(0, v),
    },
    {
      title: t('dashboard.topic'),
      dataIndex: 'topics',
      key: 'topics',
      width: 80,
      align: 'center' as const,
    },
    {
      title: t('dashboard.group'),
      dataIndex: 'groups',
      key: 'groups',
      width: 80,
      align: 'center' as const,
    },
    {
      title: t('dashboard.tpsIn'),
      dataIndex: 'tpsIn',
      key: 'tpsIn',
      width: 90,
      align: 'right' as const,
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: t('dashboard.tpsOut'),
      dataIndex: 'tpsOut',
      key: 'tpsOut',
      width: 90,
      align: 'right' as const,
      render: (v: number) => v.toLocaleString(),
    },
    {
      title: t('dashboard.trend'),
      dataIndex: 'throughput',
      key: 'throughput',
      width: 110,
      render: (data: number[], record: DashboardData['clusters'][0]) => (
        <MiniBar
          data={data}
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
      ),
    },
  ];

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
              <Text type="secondary" style={{ fontSize: 12, marginTop: 4, display: 'block' }}>
                {card.detail}
              </Text>
            </Card>
          </Col>
        ))}
      </Row>

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
          onRow={() => ({
            style: { cursor: 'pointer' },
            onClick: () => navigate(clusterPagePath),
          })}
        />
      </Card>

      <MetricsExplorer instanceId={selectedInstanceId} />
    </div>
  );
};

export default DashboardPage;
