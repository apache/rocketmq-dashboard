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
  Progress,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type {
  MessageQueryHistory,
  QueryHistorySummary,
  TraceQueryHistory,
} from '../api/messageHistory';
import { useLang } from '../i18n/LangContext';
import { formatUtcDateTime } from '../utils/format';
import {
  buildMessageHistoryInsights,
  type MessageHistoryInsightCode,
  type MessageHistoryInsightIssue,
  type MessageHistoryInsightLevel,
  type MessageHistoryNotableRow,
  type MessageHistoryRecommendationCode,
} from '../utils/messageHistoryInsights';
import { tableScrollX } from '../utils/table';

const { Text } = Typography;

interface Props {
  summary?: QueryHistorySummary;
  messageRows: MessageQueryHistory[];
  traceRows: TraceQueryHistory[];
  loading?: boolean;
  now?: number;
}

const levelMeta: Record<
  MessageHistoryInsightLevel,
  {
    color: string;
    progress: 'success' | 'normal' | 'exception';
    alertType: 'success' | 'info' | 'warning' | 'error';
  }
> = {
  healthy: { color: 'success', progress: 'success', alertType: 'success' },
  notice: { color: 'processing', progress: 'normal', alertType: 'info' },
  warning: { color: 'warning', progress: 'exception', alertType: 'warning' },
  critical: { color: 'error', progress: 'exception', alertType: 'error' },
};

const issueTextKey = (code: MessageHistoryInsightCode): string => {
  switch (code) {
    case 'NO_HISTORY':
      return 'messageHistoryInsights.issue.noHistory';
    case 'STALE_HISTORY':
      return 'messageHistoryInsights.issue.staleHistory';
    case 'TRACE_UNDERUSED':
      return 'messageHistoryInsights.issue.traceUnderused';
    case 'ZERO_RESULT_QUERIES':
      return 'messageHistoryInsights.issue.zeroResultQueries';
    case 'BROAD_TOPIC_QUERIES':
      return 'messageHistoryInsights.issue.broadTopicQueries';
    case 'LARGE_RESULT_QUERIES':
      return 'messageHistoryInsights.issue.largeResultQueries';
    case 'TRACE_WITHOUT_NODES':
      return 'messageHistoryInsights.issue.traceWithoutNodes';
    case 'TRACE_WITHOUT_CONSUMERS':
      return 'messageHistoryInsights.issue.traceWithoutConsumers';
    case 'FRAGMENTED_TRACE_TOPICS':
      return 'messageHistoryInsights.issue.fragmentedTraceTopics';
    case 'UNKNOWN_OPERATORS':
      return 'messageHistoryInsights.issue.unknownOperators';
    default:
      return 'messageHistoryInsights.issue.unknown';
  }
};

const issueShortTextKey = (code: MessageHistoryInsightCode): string =>
  `${issueTextKey(code)}.short`;

const recommendationTextKey = (code: MessageHistoryRecommendationCode): string => {
  switch (code) {
    case 'NARROW_QUERY_FILTERS':
      return 'messageHistoryInsights.recommendation.narrowQueryFilters';
    case 'REPLAY_ZERO_RESULTS':
      return 'messageHistoryInsights.recommendation.replayZeroResults';
    case 'PAIR_TRACE_LOOKUPS':
      return 'messageHistoryInsights.recommendation.pairTraceLookups';
    case 'STANDARDIZE_TRACE_TOPIC':
      return 'messageHistoryInsights.recommendation.standardizeTraceTopic';
    case 'CHECK_TRACE_COLLECTION':
      return 'messageHistoryInsights.recommendation.checkTraceCollection';
    case 'ADD_OPERATOR_CONTEXT':
      return 'messageHistoryInsights.recommendation.addOperatorContext';
    case 'KEEP_RECENT_BASELINE':
      return 'messageHistoryInsights.recommendation.keepRecentBaseline';
    default:
      return 'messageHistoryInsights.recommendation.review';
  }
};

const formatNumber = (value: number): string =>
  value.toLocaleString(undefined, { maximumFractionDigits: 0 });

const formatPercent = (value: number | null | undefined): string =>
  value == null ? '-' : `${value.toLocaleString(undefined, { maximumFractionDigits: 1 })}%`;

const formatAge = (hours: number | null): string => {
  if (hours == null) return '-';
  if (hours < 1) return '< 1h';
  if (hours < 24) return `${hours.toLocaleString(undefined, { maximumFractionDigits: 1 })}h`;
  return `${(hours / 24).toLocaleString(undefined, { maximumFractionDigits: 1 })}d`;
};

const MessageHistoryInsightsPanel = ({ summary, messageRows, traceRows, loading, now }: Props) => {
  const { t } = useLang();
  const insights = buildMessageHistoryInsights(summary, messageRows, traceRows, now);
  const meta = levelMeta[insights.level];
  const visibleIssues = insights.issues.slice(0, 5);

  if (!loading && !summary && messageRows.length === 0 && traceRows.length === 0) {
    return null;
  }

  const renderIssue = (issue: MessageHistoryInsightIssue) => (
    <Tag
      key={`${issue.code}-${issue.topic ?? issue.traceTopic ?? issue.count ?? 'global'}`}
      color={levelMeta[issue.level].color}
    >
      {t(issueTextKey(issue.code), {
        count: formatNumber(issue.count ?? 0),
        ratio: formatPercent(issue.ratio),
        value: issue.value == null ? '-' : formatAge(issue.value),
        topic: issue.topic ?? '-',
        traceTopic: issue.traceTopic ?? '-',
      })}
    </Tag>
  );

  const notableColumns: ColumnsType<MessageHistoryNotableRow> = [
    {
      title: t('common.type'),
      dataIndex: 'kind',
      key: 'kind',
      width: 100,
      render: (kind: MessageHistoryNotableRow['kind']) => (
        <Tag>{t(`messageHistoryInsights.kind.${kind}`)}</Tag>
      ),
    },
    {
      title: 'Topic',
      dataIndex: 'topic',
      key: 'topic',
      ellipsis: true,
      render: (topic: string) => <Text ellipsis={{ tooltip: topic }}>{topic}</Text>,
    },
    {
      title: t('messageHistoryInsights.detail'),
      dataIndex: 'detail',
      key: 'detail',
      ellipsis: true,
      render: (detail: string, row) => (
        <Space direction="vertical" size={0}>
          <Text ellipsis={{ tooltip: detail }}>{detail}</Text>
          {row.traceTopic && (
            <Text type="secondary" ellipsis={{ tooltip: row.traceTopic }}>
              {row.traceTopic}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: t('messageHistoryInsights.signal'),
      key: 'signal',
      width: 180,
      render: (_, row) => (
        <Space size={[4, 4]} wrap>
          {row.reasons.slice(0, 2).map((reason) => (
            <Tag key={reason} color={levelMeta[row.level].color}>
              {t(issueShortTextKey(reason), {
                count: formatNumber(row.resultCount ?? row.nodeCount ?? row.consumerCount ?? 0),
                ratio: '-',
                value: '-',
                topic: row.topic,
                traceTopic: row.traceTopic ?? '-',
              })}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: t('messageHistory.operator'),
      dataIndex: 'queriedBy',
      key: 'queriedBy',
      width: 110,
    },
    {
      title: t('messageHistory.queryTime'),
      dataIndex: 'queriedAt',
      key: 'queriedAt',
      width: 170,
      render: (value?: string) => formatUtcDateTime(value),
    },
  ];

  return (
    <Card
      size="small"
      title={t('messageHistoryInsights.title')}
      extra={<Tag color={meta.color}>{t(`messageHistoryInsights.level.${insights.level}`)}</Tag>}
      loading={loading}
      style={{ marginBottom: 16 }}
    >
      <Row gutter={[12, 12]} style={{ marginBottom: 12 }}>
        <Col xs={12} md={6}>
          <Statistic
            title={t('messageHistoryInsights.score')}
            value={insights.score}
            suffix="/100"
          />
          <Progress percent={insights.score} size="small" status={meta.progress} showInfo={false} />
        </Col>
        <Col xs={12} md={6}>
          <Statistic
            title={t('messageHistoryInsights.messageShare')}
            value={formatPercent(insights.stats.messageQueryRatio)}
          />
          <Text type="secondary">
            {t('messageHistoryInsights.totalQueries', {
              count: formatNumber(insights.stats.totalQueries),
            })}
          </Text>
        </Col>
        <Col xs={12} md={6}>
          <Statistic
            title={t('messageHistoryInsights.traceShare')}
            value={formatPercent(insights.stats.traceQueryRatio)}
          />
          <Text type="secondary">
            {t('messageHistoryInsights.traceRows', {
              count: formatNumber(insights.stats.visibleTraceRows),
            })}
          </Text>
        </Col>
        <Col xs={12} md={6}>
          <Statistic
            title={t('messageHistoryInsights.latestAge')}
            value={formatAge(insights.stats.latestQueryAgeHours)}
          />
          <Text type="secondary">
            {t('messageHistoryInsights.messageRows', {
              count: formatNumber(insights.stats.visibleMessageRows),
            })}
          </Text>
        </Col>
      </Row>

      <Alert
        showIcon
        type={meta.alertType}
        message={t('messageHistoryInsights.findings')}
        description={
          visibleIssues.length === 0 ? (
            t('messageHistoryInsights.healthyMessage')
          ) : (
            <Space direction="vertical" size={8}>
              <Space size={[6, 6]} wrap>
                {visibleIssues.map(renderIssue)}
              </Space>
              <Space size={[6, 6]} wrap>
                {insights.recommendations.map((code) => (
                  <Tag key={code}>{t(recommendationTextKey(code))}</Tag>
                ))}
              </Space>
            </Space>
          )
        }
        style={{ marginBottom: 12 }}
      />

      {insights.notableRows.length === 0 ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={t('messageHistoryInsights.noNotableRows')}
        />
      ) : (
        <Table
          size="small"
          rowKey="key"
          columns={notableColumns}
          dataSource={insights.notableRows}
          pagination={false}
          scroll={{ x: tableScrollX(notableColumns) }}
        />
      )}
    </Card>
  );
};

export default MessageHistoryInsightsPanel;
