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

import { Alert, Card, Col, Empty, Flex, Row, Statistic, Tag, Typography } from 'antd';
import type { DashboardData } from '../../api/metrics';
import { useLang } from '../../i18n/LangContext';
import {
  buildDashboardTrafficInsights,
  type DashboardTrafficIssue,
  type TrafficHealthLevel,
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

const formatTps = (value: number) =>
  value.toLocaleString(undefined, { maximumFractionDigits: value >= 100 ? 0 : 1 });

const formatPercent = (value: number) =>
  `${value.toLocaleString(undefined, { maximumFractionDigits: 1 })}%`;

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
      ) : null}
    </Card>
  );
};

export default DashboardTrafficInsights;
