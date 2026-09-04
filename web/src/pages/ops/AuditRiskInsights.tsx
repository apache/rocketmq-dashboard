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
import type { AuditSummary } from '../../api/audit';
import type { AuditRecord } from '../../api/ops';
import { useLang } from '../../i18n/LangContext';
import { formatDateTime } from '../../utils/format';
import {
  auditRiskOperationLabel,
  buildAuditRiskInsights,
  type AuditRiskIssue,
  type AuditRiskLevel,
  type AuditRiskRecord,
  type AuditRiskTarget,
} from './auditRiskInsightModel';
import {
  getAuditOperationPresentation,
  getAuditResourcePresentation,
  getAuditResultPresentation,
} from './auditPresentation';

const { Text } = Typography;

interface Props {
  summary: AuditSummary | null;
  records: AuditRecord[];
  loading: boolean;
}

const levelColor: Record<AuditRiskLevel, string> = {
  healthy: 'success',
  notice: 'processing',
  warning: 'warning',
  critical: 'error',
};

const issueTextKey = (issue: AuditRiskIssue) => {
  switch (issue.code) {
    case 'NO_MATCHING_RECORDS':
      return 'auditInsights.issue.noMatchingRecords';
    case 'HIGH_FAILURE_RATE':
      return 'auditInsights.issue.highFailureRate';
    case 'PARTIAL_OUTCOMES':
      return 'auditInsights.issue.partialOutcomes';
    case 'CONTROL_PLANE_FAILURES':
      return 'auditInsights.issue.controlPlaneFailures';
    case 'HIGH_RISK_FAILURES':
      return 'auditInsights.issue.highRiskFailures';
    case 'REPEATED_TARGET_FAILURES':
      return 'auditInsights.issue.repeatedTargetFailures';
    case 'OPERATOR_CONCENTRATION':
      return 'auditInsights.issue.operatorConcentration';
    default:
      return 'auditInsights.issue.unknown';
  }
};

const formatPercent = (value: number) =>
  `${value.toLocaleString(undefined, { maximumFractionDigits: 1 })}%`;

const formatNumber = (value: number) =>
  value.toLocaleString(undefined, { maximumFractionDigits: 0 });

const formatOperationLabel = (operationType: string, t: (key: string) => string): string => {
  const presentation = getAuditOperationPresentation(operationType);
  return presentation.labelKey ? t(presentation.labelKey) : auditRiskOperationLabel(operationType);
};

const formatResourceLabel = (resourceType: string, t: (key: string) => string): string => {
  const presentation = getAuditResourcePresentation(resourceType);
  return presentation.labelKey ? t(presentation.labelKey) : presentation.label;
};

const formatResultLabel = (result: string, t: (key: string) => string): string => {
  const presentation = getAuditResultPresentation(result);
  return presentation.labelKey ? t(presentation.labelKey) : presentation.label;
};

const AuditRiskInsights = ({ summary, records, loading }: Props) => {
  const { t } = useLang();
  const insights = buildAuditRiskInsights(summary, records);
  const visibleIssues = insights.issues.slice(0, 5);

  const renderIssue = (issue: AuditRiskIssue) => (
    <Tag
      key={`${issue.code}-${issue.target ?? issue.operator ?? 'global'}`}
      color={levelColor[issue.level]}
    >
      {t(issueTextKey(issue), {
        count: issue.count ?? 0,
        value: issue.percent == null ? '-' : formatPercent(issue.percent),
        operator: issue.operator ?? '-',
        target: issue.target ?? '-',
        threshold: issue.threshold == null ? '-' : formatPercent(issue.threshold),
      })}
    </Tag>
  );

  const hotTargetColumns: ColumnsType<AuditRiskTarget> = [
    {
      title: t('auditInsights.target'),
      dataIndex: 'target',
      key: 'target',
      render: (target: string, row) => (
        <Flex vertical gap={2}>
          <Text strong>{target}</Text>
          <Text type="secondary">
            {formatResourceLabel(row.resourceType, t)} · {row.clusterId}
          </Text>
        </Flex>
      ),
    },
    {
      title: t('auditInsights.failPartial'),
      key: 'failPartial',
      width: 130,
      align: 'right',
      render: (_, row) => `${row.failed} / ${row.partial}`,
    },
    {
      title: t('audit.opType'),
      dataIndex: 'operationTypes',
      key: 'operationTypes',
      render: (operationTypes: string[]) => (
        <Space size={[4, 4]} wrap>
          {operationTypes.slice(0, 3).map((operationType) => (
            <Tag key={operationType} style={{ marginInlineEnd: 0 }}>
              {formatOperationLabel(operationType, t)}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: t('audit.time'),
      dataIndex: 'latestAt',
      key: 'latestAt',
      width: 180,
      render: (latestAt: string | null) => formatDateTime(latestAt),
    },
  ];

  const riskyRecordColumns: ColumnsType<AuditRiskRecord> = [
    {
      title: t('audit.time'),
      dataIndex: 'timestamp',
      key: 'timestamp',
      width: 180,
      render: (timestamp: string) => formatDateTime(timestamp),
    },
    {
      title: t('audit.operator'),
      dataIndex: 'operator',
      key: 'operator',
      width: 130,
    },
    {
      title: t('audit.opType'),
      dataIndex: 'operationType',
      key: 'operationType',
      render: (operationType: string) => {
        const presentation = getAuditOperationPresentation(operationType);
        return <Tag color={presentation.color}>{formatOperationLabel(operationType, t)}</Tag>;
      },
    },
    {
      title: t('audit.target'),
      dataIndex: 'target',
      key: 'target',
      ellipsis: true,
    },
    {
      title: t('audit.result'),
      dataIndex: 'result',
      key: 'result',
      width: 100,
      align: 'center',
      render: (result: string) => {
        const presentation = getAuditResultPresentation(result);
        return <Tag color={presentation.color}>{formatResultLabel(result, t)}</Tag>;
      },
    },
  ];

  if (loading) {
    return (
      <Card title={t('auditInsights.title')} style={{ marginBottom: 16 }}>
        <Skeleton active paragraph={{ rows: 4 }} />
      </Card>
    );
  }

  return (
    <Card
      title={t('auditInsights.title')}
      extra={
        <Tag color={levelColor[insights.level]}>{t(`auditInsights.level.${insights.level}`)}</Tag>
      }
      style={{ marginBottom: 16 }}
    >
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic
              title={t('auditInsights.failureRate')}
              value={insights.failureRate}
              suffix="%"
              precision={1}
              valueStyle={{ color: insights.failureRate >= 10 ? '#cf1322' : undefined }}
            />
            <Text type="secondary">
              {t('auditInsights.filteredTotal', { count: formatNumber(insights.total) })}
            </Text>
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic
              title={t('auditInsights.partialRate')}
              value={insights.partialRate}
              suffix="%"
              precision={1}
            />
            <Text type="secondary">
              {t('auditInsights.partialCount', { count: formatNumber(insights.partial) })}
            </Text>
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic
              title={t('auditInsights.controlPlaneFailures')}
              value={insights.controlPlaneFailureCount}
              valueStyle={{ color: insights.controlPlaneFailureCount ? '#cf1322' : undefined }}
            />
            <Text type="secondary">
              {t('auditInsights.currentPageRecords', {
                count: formatNumber(insights.pageRecordCount),
              })}
            </Text>
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card size="small">
            <Statistic
              title={t('auditInsights.topOperator')}
              value={insights.topOperator?.name ?? '-'}
            />
            <Text type="secondary">
              {insights.topOperator
                ? t('auditInsights.operatorShare', {
                    value: formatPercent(insights.topOperator.percent),
                  })
                : t('common.noData')}
            </Text>
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
          message={t('auditInsights.findings')}
          description={
            <Flex gap={8} wrap>
              {visibleIssues.map(renderIssue)}
            </Flex>
          }
          style={{ marginBottom: 16 }}
        />
      )}

      <Row gutter={[12, 12]}>
        <Col xs={24} lg={12}>
          <Card size="small" title={t('auditInsights.hotTargets')}>
            {insights.hotTargets.length === 0 ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={t('auditInsights.noHotTargets')}
              />
            ) : (
              <Table
                size="small"
                rowKey="key"
                dataSource={insights.hotTargets}
                columns={hotTargetColumns}
                pagination={false}
              />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card size="small" title={t('auditInsights.riskyRecords')}>
            {insights.riskyRecords.length === 0 ? (
              <Flex vertical gap={8}>
                <Progress percent={100} status="success" showInfo={false} />
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={t('auditInsights.noRiskyRecords')}
                />
              </Flex>
            ) : (
              <Table
                size="small"
                rowKey="id"
                dataSource={insights.riskyRecords}
                columns={riskyRecordColumns}
                pagination={false}
              />
            )}
          </Card>
        </Col>
      </Row>
    </Card>
  );
};

export default AuditRiskInsights;
