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
  Skeleton,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { LiteTopicItem, LiteTopicQuota } from '../../api/liteTopic';
import { useLang } from '../../i18n/LangContext';
import {
  buildLiteTopicCapacityInsights,
  type LiteTopicCapacityIssue,
  type LiteTopicCapacityIssueCode,
  type LiteTopicCapacityLevel,
  type LiteTopicNamespaceSignal,
  type LiteTopicPatternSignal,
  type LiteTopicRecommendationCode,
} from '../../utils/liteTopicCapacityInsights';
import { tableScrollX } from '../../utils/table';

const { Text } = Typography;

interface Props {
  quota: LiteTopicQuota | null;
  topics: LiteTopicItem[];
  loading: boolean;
}

const levelMeta: Record<
  LiteTopicCapacityLevel,
  { color: string; alertType: 'success' | 'info' | 'warning' | 'error' }
> = {
  healthy: { color: 'success', alertType: 'success' },
  notice: { color: 'processing', alertType: 'info' },
  warning: { color: 'warning', alertType: 'warning' },
  critical: { color: 'error', alertType: 'error' },
};

const ttlStatusColor = (status: string): string => {
  if (status === 'ACTIVE') return 'success';
  if (status === 'EXPIRING_SOON') return 'warning';
  if (status === 'EXPIRED') return 'error';
  return 'default';
};

const issueTextKey = (code: LiteTopicCapacityIssueCode): string => {
  switch (code) {
    case 'TOPIC_QUOTA_CRITICAL':
      return 'liteTopicInsights.issue.topicQuotaCritical';
    case 'TOPIC_QUOTA_WARNING':
      return 'liteTopicInsights.issue.topicQuotaWarning';
    case 'SESSION_QUOTA_CRITICAL':
      return 'liteTopicInsights.issue.sessionQuotaCritical';
    case 'SESSION_QUOTA_WARNING':
      return 'liteTopicInsights.issue.sessionQuotaWarning';
    case 'CREATION_RATE_CRITICAL':
      return 'liteTopicInsights.issue.creationRateCritical';
    case 'CREATION_RATE_WARNING':
      return 'liteTopicInsights.issue.creationRateWarning';
    case 'EXPIRED_PATTERNS':
      return 'liteTopicInsights.issue.expiredPatterns';
    case 'EXPIRING_PATTERNS':
      return 'liteTopicInsights.issue.expiringPatterns';
    case 'BACKLOG_HOTSPOT':
      return 'liteTopicInsights.issue.backlogHotspot';
    case 'UNKNOWN_TTL_STATUS':
      return 'liteTopicInsights.issue.unknownTtlStatus';
    case 'IDLE_PATTERNS':
      return 'liteTopicInsights.issue.idlePatterns';
    default:
      return 'liteTopicInsights.issue.unknown';
  }
};

const recommendationTextKey = (code: LiteTopicRecommendationCode): string => {
  switch (code) {
    case 'REQUEST_TOPIC_QUOTA':
      return 'liteTopicInsights.recommendation.requestTopicQuota';
    case 'REQUEST_SESSION_QUOTA':
      return 'liteTopicInsights.recommendation.requestSessionQuota';
    case 'THROTTLE_CREATION':
      return 'liteTopicInsights.recommendation.throttleCreation';
    case 'EXTEND_TTL':
      return 'liteTopicInsights.recommendation.extendTtl';
    case 'CLEAN_EXPIRED':
      return 'liteTopicInsights.recommendation.cleanExpired';
    case 'DRAIN_BACKLOG':
      return 'liteTopicInsights.recommendation.drainBacklog';
    case 'CHECK_UNKNOWN_STATUS':
      return 'liteTopicInsights.recommendation.checkUnknownStatus';
    case 'REVIEW_IDLE_PATTERNS':
      return 'liteTopicInsights.recommendation.reviewIdlePatterns';
    default:
      return 'liteTopicInsights.recommendation.review';
  }
};

const formatPercent = (value: number | null): string =>
  value == null ? '-' : `${value.toLocaleString(undefined, { maximumFractionDigits: 1 })}%`;

const formatNumber = (value: number): string =>
  value.toLocaleString(undefined, { maximumFractionDigits: 0 });

const formatDuration = (ms: number | null): string => {
  if (ms == null) return '-';
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`;
  if (ms < 3_600_000) return `${(ms / 60_000).toFixed(1)}min`;
  if (ms < 86_400_000) return `${(ms / 3_600_000).toFixed(1)}h`;
  return `${(ms / 86_400_000).toFixed(1)}d`;
};

const formatLastActive = (timestamp: number | null): string => {
  if (timestamp == null) return '-';
  const date = new Date(timestamp);
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString();
};

const renderQuotaProgress = (percent: number | null) => (
  <Progress
    percent={percent ?? 0}
    showInfo={false}
    size="small"
    status={percent != null && percent >= 90 ? 'exception' : 'normal'}
  />
);

const LiteTopicCapacityInsights = ({ quota, topics, loading }: Props) => {
  const { t } = useLang();
  const insights = buildLiteTopicCapacityInsights(quota, topics);
  const level = levelMeta[insights.level];
  const visibleIssues = insights.issues.slice(0, 5);
  const ttlAndIdlePatterns = [...insights.ttlAttentionPatterns, ...insights.idlePatterns]
    .filter(
      (signal, index, allSignals) =>
        allSignals.findIndex((item) => item.key === signal.key) === index,
    )
    .slice(0, 5);

  const renderIssue = (issue: LiteTopicCapacityIssue) => (
    <Tag key={`${issue.code}-${issue.pattern ?? 'global'}`} color={levelMeta[issue.level].color}>
      {t(issueTextKey(issue.code), {
        count: formatNumber(issue.count ?? 0),
        value: issue.percent == null ? '-' : formatPercent(issue.percent),
        pattern: issue.pattern ?? '-',
        namespace: issue.namespace ?? '-',
        limit: issue.limit == null ? '-' : formatNumber(issue.limit),
      })}
    </Tag>
  );

  const patternColumns: ColumnsType<LiteTopicPatternSignal> = [
    {
      title: t('liteTopic.pattern'),
      dataIndex: 'topicPattern',
      key: 'topicPattern',
      render: (pattern: string, record) => (
        <Flex vertical gap={2}>
          <Text strong ellipsis={{ tooltip: pattern }}>
            {t('liteTopicInsights.patternLabel', { pattern })}
          </Text>
          <Text type="secondary">
            {t('liteTopicInsights.namespaceLabel', { namespace: record.namespace })}
          </Text>
        </Flex>
      ),
    },
    {
      title: t('liteTopic.backlog'),
      dataIndex: 'totalBacklog',
      key: 'totalBacklog',
      width: 110,
      align: 'right',
      render: (value: number) => formatNumber(value),
    },
    {
      title: t('liteTopic.avgTtl'),
      dataIndex: 'averageTTL',
      key: 'averageTTL',
      width: 100,
      render: (value: number | null) => formatDuration(value),
    },
    {
      title: t('liteTopic.status'),
      dataIndex: 'ttlStatus',
      key: 'ttlStatus',
      width: 120,
      render: (status: string) => <Tag color={ttlStatusColor(status)}>{status}</Tag>,
    },
  ];

  const namespaceColumns: ColumnsType<LiteTopicNamespaceSignal> = [
    {
      title: t('liteTopic.namespace'),
      dataIndex: 'namespace',
      key: 'namespace',
      render: (namespace: string, record) => (
        <Flex vertical gap={2}>
          <Text strong>{t('liteTopicInsights.namespaceLabel', { namespace })}</Text>
          <Text type="secondary">
            {t('liteTopicInsights.patternSummary', {
              patterns: formatNumber(record.patternCount),
              topics: formatNumber(record.topicCount),
            })}
          </Text>
        </Flex>
      ),
    },
    {
      title: t('liteTopic.backlog'),
      dataIndex: 'totalBacklog',
      key: 'totalBacklog',
      width: 110,
      align: 'right',
      render: (value: number) => formatNumber(value),
    },
    {
      title: t('liteTopic.consumers'),
      dataIndex: 'consumerCount',
      key: 'consumerCount',
      width: 100,
      align: 'right',
      render: (value: number) => formatNumber(value),
    },
    {
      title: t('liteTopic.status'),
      key: 'status',
      width: 120,
      render: (_, record) => (
        <Tag color={levelMeta[record.level].color}>
          {t(`liteTopicInsights.level.${record.level}`)}
        </Tag>
      ),
    },
  ];
  const ttlColumns: ColumnsType<LiteTopicPatternSignal> = [
    ...patternColumns.slice(0, 3),
    {
      title: t('liteTopic.lastActive'),
      dataIndex: 'lastActiveTime',
      key: 'lastActiveTime',
      width: 170,
      render: (value: number | null) => formatLastActive(value),
    },
  ];

  if (loading) {
    return (
      <Card title={t('liteTopicInsights.title')} style={{ marginBottom: 16 }}>
        <Skeleton active paragraph={{ rows: 4 }} />
      </Card>
    );
  }

  if (!quota && topics.length === 0) {
    return null;
  }

  return (
    <Card
      title={t('liteTopicInsights.title')}
      extra={<Tag color={level.color}>{t(`liteTopicInsights.level.${insights.level}`)}</Tag>}
      style={{ marginBottom: 16, borderRadius: 8 }}
    >
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic
              title={t('liteTopicInsights.topicUsage')}
              value={formatPercent(insights.topicUsagePercent)}
            />
            {renderQuotaProgress(insights.topicUsagePercent)}
            <Text type="secondary">
              {t('liteTopicInsights.remainingTopics', {
                count:
                  insights.remainingTopicSlots == null
                    ? '-'
                    : formatNumber(insights.remainingTopicSlots),
              })}
            </Text>
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic
              title={t('liteTopicInsights.sessionUsage')}
              value={formatPercent(insights.sessionUsagePercent)}
            />
            {renderQuotaProgress(insights.sessionUsagePercent)}
            <Text type="secondary">
              {t('liteTopicInsights.sessions', { count: formatNumber(insights.totalSessions) })}
            </Text>
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic
              title={t('liteTopicInsights.ttlAttention')}
              value={insights.expiredCount + insights.expiringSoonCount}
            />
            <Text type="secondary">
              {t('liteTopicInsights.ttlBreakdown', {
                expired: formatNumber(insights.expiredCount),
                expiring: formatNumber(insights.expiringSoonCount),
              })}
            </Text>
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic
              title={t('liteTopicInsights.totalBacklog')}
              value={formatNumber(insights.totalBacklog)}
            />
            <Text type="secondary">
              {t('liteTopicInsights.patterns', { count: formatNumber(insights.totalPatterns) })}
            </Text>
          </Card>
        </Col>
      </Row>

      <Alert
        showIcon
        type={level.alertType}
        message={t('liteTopicInsights.findings')}
        description={
          visibleIssues.length === 0 ? (
            t('liteTopicInsights.healthyMessage')
          ) : (
            <Flex vertical gap={10}>
              <Space size={[6, 6]} wrap>
                {visibleIssues.map(renderIssue)}
              </Space>
              {insights.recommendations.length > 0 && (
                <Space size={[6, 6]} wrap>
                  {insights.recommendations.map((code) => (
                    <Tag key={code}>{t(recommendationTextKey(code))}</Tag>
                  ))}
                </Space>
              )}
            </Flex>
          )
        }
        style={{ marginBottom: 16 }}
      />

      <Row gutter={[12, 12]}>
        <Col xs={24} lg={12}>
          <Card size="small" title={t('liteTopicInsights.backlogHotspots')}>
            {insights.backlogHotspots.length === 0 ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={t('liteTopicInsights.noBacklog')}
              />
            ) : (
              <Table
                size="small"
                rowKey="key"
                dataSource={insights.backlogHotspots}
                columns={patternColumns}
                pagination={false}
                scroll={{ x: tableScrollX(patternColumns) }}
              />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card size="small" title={t('liteTopicInsights.ttlAttentionPatterns')}>
            {insights.ttlAttentionPatterns.length === 0 && insights.idlePatterns.length === 0 ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={t('liteTopicInsights.noTtlRisk')}
              />
            ) : (
              <Table
                size="small"
                rowKey="key"
                dataSource={ttlAndIdlePatterns}
                columns={ttlColumns}
                pagination={false}
                scroll={{ x: tableScrollX(ttlColumns) }}
              />
            )}
          </Card>
        </Col>
        <Col span={24}>
          <Card size="small" title={t('liteTopicInsights.namespacePressure')}>
            {insights.namespaceSignals.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('common.noData')} />
            ) : (
              <Table
                size="small"
                rowKey="namespace"
                dataSource={insights.namespaceSignals}
                columns={namespaceColumns}
                pagination={false}
                scroll={{ x: tableScrollX(namespaceColumns) }}
              />
            )}
          </Card>
        </Col>
      </Row>
    </Card>
  );
};

export default LiteTopicCapacityInsights;
