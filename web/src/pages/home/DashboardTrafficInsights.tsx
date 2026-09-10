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

import { Alert, Card, Col, Empty, Row, Statistic, Tag, Typography } from 'antd';
import { useLang } from '../../i18n/LangContext';
import {
  formatTrafficPercent,
  formatTrafficTps,
  type DashboardTrafficInsights,
  type DashboardTrafficIssue,
  type TrafficHealthLevel,
} from '../../utils/dashboardTrafficInsights';

const { Text } = Typography;

interface Props {
  insights: DashboardTrafficInsights;
}

const levelColor: Record<TrafficHealthLevel, string> = {
  healthy: 'success',
  notice: 'processing',
  warning: 'warning',
  critical: 'error',
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

const DashboardTrafficInsights = ({ insights }: Props) => {
  const { t } = useLang();
  const visibleIssues = insights.issues.slice(0, 4);

  const issueText = (issue: DashboardTrafficIssue) =>
    t(issueTextKey(issue), {
      cluster: issue.clusterName ?? t('dashboardTraffic.allClusters'),
      value:
        issue.value == null
          ? '-'
          : formatTrafficPercent(
              issue.code === 'RECENT_TRAFFIC_DROP' ? Math.abs(issue.value) : issue.value,
            ),
      threshold: issue.threshold == null ? '-' : formatTrafficPercent(issue.threshold),
    });

  // All four cards render the same structure, caption slot included. Only the top-share card has
  // something to put there, and letting the others omit it made them shorter than their
  // neighbours — `align="stretch"` cannot fix that because the cards wrap onto two flex lines.
  const summaryCards = [
    {
      key: 'activeClusters',
      title: t('dashboardTraffic.activeClusters'),
      value: `${insights.activeClusterCount}/${insights.totalClusterCount}`,
      caption: '',
    },
    {
      key: 'topClusterShare',
      title: t('dashboardTraffic.topClusterShare'),
      value: insights.topClusterSharePercent,
      suffix: '%',
      precision: 1,
      caption: insights.topCluster?.name ?? '-',
    },
    {
      key: 'balanceScore',
      title: t('dashboardTraffic.balanceScore'),
      value: insights.balanceScore,
      suffix: '/100',
      caption: '',
    },
    {
      key: 'unhealthyTraffic',
      title: t('dashboardTraffic.unhealthyTraffic'),
      value: formatTrafficTps(insights.unhealthyTrafficTps),
      suffix: '/s',
      valueStyle: { color: insights.unhealthyTrafficTps > 0 ? '#cf1322' : undefined },
      caption: '',
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
      <Row gutter={[12, 12]} align="stretch" style={{ marginBottom: 16 }}>
        {summaryCards.map((card) => (
          <Col xs={12} lg={6} key={card.key}>
            <Card size="small" style={{ height: '100%' }}>
              <Statistic
                title={card.title}
                value={card.value}
                suffix={card.suffix}
                precision={card.precision}
                valueStyle={card.valueStyle}
              />
              <Text type="secondary" ellipsis={{ tooltip: card.caption || undefined }}>
                {card.caption || '\u00a0'}
              </Text>
            </Card>
          </Col>
        ))}
      </Row>

      {visibleIssues.length > 0 && (
        /* One compact line rather than a `description` block of one Tag per finding, which took
           several times the vertical space for the same text. */
        <Alert
          showIcon
          type={
            insights.level === 'critical'
              ? 'error'
              : insights.level === 'warning'
                ? 'warning'
                : 'info'
          }
          message={`${t('dashboardTraffic.findings')}：${visibleIssues.map(issueText).join('、')}`}
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
