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

import {
  Alert,
  Card,
  Col,
  Empty,
  Flex,
  Progress,
  Row,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { DashboardData } from '../../api/metrics';
import MiniLine from '../../components/MiniLine';
import StatusBadge from '../../components/StatusBadge';
import { useLang } from '../../i18n/LangContext';
import {
  buildDashboardTrafficInsights,
  type DashboardTrafficClusterInsight,
  type DashboardTrafficIssue,
  type TrafficHealthLevel,
  type TrafficTrendDirection,
} from '../../utils/dashboardTrafficInsights';

const { Text } = Typography;

interface Props {
  dashboard: DashboardData;
}

const levelColor: Record<TrafficHealthLevel, string> = {
  healthy: 'success',
  notice: 'processing',
  warning: 'warning',
  critical: 'error',
};

const trendColor: Record<TrafficTrendDirection, string> = {
  rising: 'green',
  falling: 'volcano',
  stable: 'blue',
  unknown: 'default',
};

const trendLabelKey: Record<TrafficTrendDirection, string> = {
  rising: 'dashboardTraffic.trendRising',
  falling: 'dashboardTraffic.trendFalling',
  stable: 'dashboardTraffic.trendStable',
  unknown: 'dashboardTraffic.trendUnknown',
};

const formatTps = (value: number) =>
  value.toLocaleString(undefined, { maximumFractionDigits: value >= 100 ? 0 : 1 });

const formatPercent = (value: number) =>
  `${value.toLocaleString(undefined, { maximumFractionDigits: 1 })}%`;

const formatTrendDelta = (value: number | null) => {
  if (value == null) return '';
  const sign = value > 0 ? '+' : '';
  return ` ${sign}${formatPercent(value)}`;
};

const issueTextKey = (issue: DashboardTrafficIssue) => {
  switch (issue.code) {
    case 'NO_ACTIVE_TRAFFIC':
      return 'dashboardTraffic.issueNoActiveTraffic';
    case 'TRAFFIC_CONCENTRATION':
      return 'dashboardTraffic.issueTrafficConcentration';
    case 'UNHEALTHY_TRAFFIC':
      return issue.clusterName
        ? 'dashboardTraffic.issueUnhealthyClusterTraffic'
        : 'dashboardTraffic.issueUnhealthyTraffic';
    case 'BROKER_LOAD_SKEW':
      return 'dashboardTraffic.issueBrokerLoadSkew';
    case 'RECENT_TRAFFIC_DROP':
      return 'dashboardTraffic.issueRecentDrop';
    case 'RECENT_TRAFFIC_SPIKE':
      return 'dashboardTraffic.issueRecentSpike';
    case 'TOPOLOGY_COUNT_UNAVAILABLE':
      return 'dashboardTraffic.issueTopologyUnavailable';
    case 'IDLE_CLUSTER':
      return 'dashboardTraffic.issueIdleCluster';
    default:
      return 'dashboardTraffic.issueUnknown';
  }
};

const DashboardTrafficInsights = ({ dashboard }: Props) => {
  const { t } = useLang();
  const insights = buildDashboardTrafficInsights(dashboard);
  const visibleIssues = insights.issues.slice(0, 4);

  const renderIssue = (issue: DashboardTrafficIssue) => (
    <Tag key={`${issue.code}-${issue.clusterId ?? 'global'}`} color={levelColor[issue.level]}>
      {t(issueTextKey(issue), {
        cluster: issue.clusterName ?? t('dashboardTraffic.allClusters'),
        value:
          issue.value == null
            ? '-'
            : formatPercent(
                issue.code === 'RECENT_TRAFFIC_DROP' ? Math.abs(issue.value) : issue.value,
              ),
        threshold: issue.threshold == null ? '-' : formatPercent(issue.threshold),
      })}
    </Tag>
  );

  const columns: ColumnsType<DashboardTrafficClusterInsight> = [
    {
      title: t('dashboard.clusterName'),
      dataIndex: 'name',
      key: 'name',
      render: (name: string, row) => (
        <Flex vertical gap={2}>
          <Text strong>{name}</Text>
          <Text type="secondary">
            {row.brokers} Broker · {row.proxies == null ? 'N/A' : row.proxies} Proxy
          </Text>
        </Flex>
      ),
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (status: string) => <StatusBadge status={status} />,
    },
    {
      title: t('dashboardTraffic.totalTps'),
      dataIndex: 'totalTps',
      key: 'totalTps',
      width: 130,
      align: 'right',
      render: (value: number, row) => (
        <Flex vertical align="flex-end" gap={2}>
          <Text strong>{formatTps(value)}/s</Text>
          <Text type="secondary">
            {formatTps(row.tpsIn)} in · {formatTps(row.tpsOut)} out
          </Text>
        </Flex>
      ),
    },
    {
      title: t('dashboardTraffic.share'),
      dataIndex: 'sharePercent',
      key: 'sharePercent',
      width: 160,
      render: (value: number) => (
        <Flex vertical gap={4}>
          <Text>{formatPercent(value)}</Text>
          <Progress percent={Math.min(100, value)} showInfo={false} size="small" />
        </Flex>
      ),
    },
    {
      title: t('dashboardTraffic.perBroker'),
      dataIndex: 'perBrokerTps',
      key: 'perBrokerTps',
      width: 130,
      align: 'right',
      render: (value: number) => `${formatTps(value)}/s`,
    },
    {
      title: t('dashboardTraffic.inOutRatio'),
      dataIndex: 'inOutRatio',
      key: 'inOutRatio',
      width: 110,
      align: 'right',
      render: (value: number | null) => (value == null ? 'N/A' : `${value}:1`),
    },
    {
      title: t('dashboard.trend'),
      dataIndex: 'trendDirection',
      key: 'trendDirection',
      width: 170,
      render: (_, row) => (
        <Flex align="center" gap={8}>
          <MiniLine data={row.throughput} width={64} height={24} animated={false} />
          <Tag color={trendColor[row.trendDirection]}>
            {t(trendLabelKey[row.trendDirection])}
            {formatTrendDelta(row.trendDeltaPercent)}
          </Tag>
        </Flex>
      ),
    },
  ];

  return (
    <Card
      title={t('dashboardTraffic.title')}
      extra={
        <Tag color={levelColor[insights.level]}>
          {t(`dashboardTraffic.level.${insights.level}`)}
        </Tag>
      }
      style={{ marginBottom: 24 }}
      styles={{ body: { padding: 20 } }}
    >
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic
              title={t('dashboardTraffic.activeClusters')}
              value={`${insights.activeClusterCount}/${insights.totalClusterCount}`}
            />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic
              title={t('dashboardTraffic.topClusterShare')}
              value={insights.topClusterSharePercent}
              suffix="%"
              precision={1}
            />
            <Text type="secondary">{insights.topCluster?.name ?? '-'}</Text>
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic
              title={t('dashboardTraffic.balanceScore')}
              value={insights.balanceScore}
              suffix="/100"
            />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic
              title={t('dashboardTraffic.unhealthyTraffic')}
              value={formatTps(insights.unhealthyTrafficTps)}
              suffix="/s"
              valueStyle={{ color: insights.unhealthyTrafficTps > 0 ? '#cf1322' : undefined }}
            />
          </Card>
        </Col>
      </Row>

      {visibleIssues.length > 0 && (
        <Alert
          showIcon
          type={
            insights.level === 'critical'
              ? 'error'
              : insights.level === 'warning'
                ? 'warning'
                : 'info'
          }
          message={t('dashboardTraffic.findings')}
          description={
            <Flex wrap="wrap" gap={8}>
              {visibleIssues.map(renderIssue)}
            </Flex>
          }
          style={{ marginBottom: 16 }}
        />
      )}

      {insights.rows.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('dashboardTraffic.noCluster')} />
      ) : (
        <Table
          size="small"
          rowKey="id"
          dataSource={insights.rows}
          columns={columns}
          pagination={false}
          scroll={{ x: 980 }}
        />
      )}
    </Card>
  );
};

export default DashboardTrafficInsights;
