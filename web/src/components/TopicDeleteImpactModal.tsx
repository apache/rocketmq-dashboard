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

import { Alert, Button, Flex, Modal, Space, Spin, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import {
  CheckCircleOutlined,
  DeleteOutlined,
  ExclamationCircleOutlined,
  ReloadOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { formatNumber } from '../utils/format';
import { tableScrollX } from '../utils/table';
import type {
  TopicDeleteImpactAssessment,
  TopicDeleteImpactSummary,
  TopicDeleteIssue,
  TopicDeleteRiskLevel,
} from '../utils/topicDeleteImpact';

const { Text } = Typography;

const RISK_META: Record<
  TopicDeleteRiskLevel,
  {
    label: string;
    color: string;
    alertType: 'success' | 'warning' | 'error';
    icon: React.ReactNode;
  }
> = {
  low: { label: '低风险', color: 'success', alertType: 'success', icon: <CheckCircleOutlined /> },
  medium: { label: '需确认', color: 'warning', alertType: 'warning', icon: <WarningOutlined /> },
  high: {
    label: '高风险',
    color: 'error',
    alertType: 'error',
    icon: <ExclamationCircleOutlined />,
  },
  unknown: { label: '不可确认', color: 'default', alertType: 'warning', icon: <WarningOutlined /> },
};

const ISSUE_COLOR: Record<TopicDeleteIssue['severity'], string> = {
  critical: 'error',
  warning: 'warning',
  info: 'blue',
};

interface TopicDeleteImpactModalProps {
  open: boolean;
  loading: boolean;
  deleting: boolean;
  title: string;
  assessments: TopicDeleteImpactAssessment[];
  summary: TopicDeleteImpactSummary | null;
  error?: string | null;
  onCancel: () => void;
  onRetry: () => void;
  onConfirm: () => void;
}

const riskTag = (riskLevel: TopicDeleteRiskLevel) => {
  const meta = RISK_META[riskLevel];
  return (
    <Tag color={meta.color} icon={meta.icon}>
      {meta.label}
    </Tag>
  );
};

const issueTags = (issues: TopicDeleteIssue[]) => (
  <Space size={[4, 4]} wrap>
    {issues.map((item) => (
      <Tag key={item.code} color={ISSUE_COLOR[item.severity]} title={item.description}>
        {item.title}
      </Tag>
    ))}
  </Space>
);

const metricText = (label: string, value: React.ReactNode, strong = false) => (
  <span>
    <Text type="secondary">{label}</Text>
    <Text strong={strong} style={{ marginLeft: 4, fontVariantNumeric: 'tabular-nums' }}>
      {value}
    </Text>
  </span>
);

const metricBox = (label: string, value: React.ReactNode, helper?: React.ReactNode) => (
  <div
    style={{
      border: '1px solid #f0f0f0',
      borderRadius: 6,
      padding: '10px 12px',
      minWidth: 132,
      flex: '1 1 132px',
      background: '#fafafa',
    }}
  >
    <Text type="secondary" style={{ display: 'block', fontSize: 13 }}>
      {label}
    </Text>
    <Text strong style={{ fontSize: 20, fontVariantNumeric: 'tabular-nums' }}>
      {value}
    </Text>
    {helper && (
      <div style={{ marginTop: 2 }}>
        <Text type="secondary" style={{ fontSize: 13 }}>
          {helper}
        </Text>
      </div>
    )}
  </div>
);

const summaryMessage = (summary: TopicDeleteImpactSummary) => {
  if (!summary.canDelete) return `发现 ${summary.blockedTopics.length} 个系统 Topic，已阻止删除。`;
  if (summary.riskLevel === 'high')
    return `发现 ${summary.highRiskTopics} 个高风险 Topic，删除前需要确认消费者和消息影响。`;
  if (summary.riskLevel === 'unknown') return '部分依赖信息加载失败，删除影响不可完全确认。';
  if (summary.riskLevel === 'medium')
    return `发现 ${summary.mediumRiskTopics} 个需要确认的 Topic。`;
  return '未发现明显消费者或路由依赖。';
};

const TopicDeleteImpactModal = ({
  open,
  loading,
  deleting,
  title,
  assessments,
  summary,
  error,
  onCancel,
  onRetry,
  onConfirm,
}: TopicDeleteImpactModalProps) => {
  const columns: TableColumnsType<TopicDeleteImpactAssessment> = [
    {
      title: 'Topic',
      dataIndex: 'topicName',
      key: 'topicName',
      width: 220,
      render: (topicName: string) => (
        <Text strong ellipsis={{ tooltip: topicName }} style={{ display: 'block' }}>
          {topicName}
        </Text>
      ),
    },
    {
      title: '风险',
      dataIndex: 'riskLevel',
      key: 'riskLevel',
      width: 110,
      render: riskTag,
      sorter: (left, right) => right.riskScore - left.riskScore,
    },
    {
      title: '消费者影响',
      key: 'consumers',
      width: 210,
      render: (_: unknown, record) => (
        <Space direction="vertical" size={2}>
          {metricText('消费者组', formatNumber(record.stats.consumerGroups), true)}
          {metricText('活跃', formatNumber(record.stats.activeConsumerGroups))}
          {metricText('堆积', formatNumber(record.stats.knownTotalLag))}
        </Space>
      ),
    },
    {
      title: '生产与消息',
      key: 'traffic',
      width: 210,
      render: (_: unknown, record) => (
        <Space direction="vertical" size={2}>
          {metricText('TPS', formatNumber(record.stats.topicTps), record.stats.topicTps > 0)}
          {metricText('今日消息', formatNumber(record.stats.topicMessageCount))}
          {metricText('可写路由', formatNumber(record.stats.writableRoutes))}
        </Space>
      ),
    },
    {
      title: '诊断项',
      key: 'issues',
      width: 320,
      render: (_: unknown, record) => issueTags(record.issues),
    },
  ];

  return (
    <Modal
      title={
        <Space>
          <DeleteOutlined />
          <span>{title}</span>
        </Space>
      }
      open={open}
      onCancel={onCancel}
      onOk={onConfirm}
      okText="确认删除"
      okType="danger"
      cancelText="取消"
      confirmLoading={deleting}
      okButtonProps={{ disabled: loading || Boolean(error) || !summary || !summary.canDelete }}
      width={960}
      destroyOnHidden
    >
      {loading ? (
        <Flex justify="center" align="center" style={{ minHeight: 220 }}>
          <Spin tip="正在分析消费者、消息和路由影响">
            <div style={{ width: 240 }} />
          </Spin>
        </Flex>
      ) : error ? (
        <Alert
          showIcon
          type="error"
          message="删除影响预检失败"
          description={error}
          action={
            <Button size="small" icon={<ReloadOutlined />} onClick={onRetry}>
              重试
            </Button>
          }
        />
      ) : summary ? (
        <Space direction="vertical" size={14} style={{ width: '100%' }}>
          <Alert
            showIcon
            type={RISK_META[summary.riskLevel].alertType}
            message={
              <Flex gap={8} align="center" wrap>
                <span>{summaryMessage(summary)}</span>
                {riskTag(summary.riskLevel)}
              </Flex>
            }
            description={
              summary.canDelete
                ? '预检不替代变更审批；确认删除后会调用现有 Topic 删除接口。'
                : `以下 Topic 被识别为系统 Topic：${summary.blockedTopics.join('、')}`
            }
          />
          <Flex gap={10} wrap="wrap">
            {metricBox('Topic 数', formatNumber(summary.totalTopics))}
            {metricBox(
              '消费者组',
              formatNumber(summary.totalConsumerGroups),
              `${formatNumber(summary.activeConsumerGroups)} 个活跃`,
            )}
            {metricBox(
              '已知堆积',
              formatNumber(summary.knownTotalLag),
              `${formatNumber(summary.consumerGroupsWithLag)} 个 Group`,
            )}
            {metricBox('今日消息', formatNumber(summary.topicMessageCount))}
            {metricBox('当前 TPS', formatNumber(summary.topicTps))}
            {metricBox('可写路由', formatNumber(summary.writableRoutes))}
          </Flex>
          {(summary.consumerLookupFailedTopics > 0 ||
            summary.routeLookupFailedTopics > 0 ||
            summary.truncatedConsumerTopics > 0 ||
            summary.metricsUnavailableGroups > 0) && (
            <Alert
              showIcon
              type="warning"
              message="存在不完整依赖信息"
              description={`消费者加载失败 ${summary.consumerLookupFailedTopics} 个 Topic，路由加载失败 ${summary.routeLookupFailedTopics} 个 Topic，消费者列表截断 ${summary.truncatedConsumerTopics} 个 Topic，指标不可用消费者组 ${summary.metricsUnavailableGroups} 个。`}
            />
          )}
          <Table<TopicDeleteImpactAssessment>
            columns={columns}
            dataSource={assessments}
            rowKey="topicName"
            size="small"
            pagination={assessments.length > 8 ? { pageSize: 8, showSizeChanger: false } : false}
            scroll={{ x: tableScrollX(columns) }}
          />
        </Space>
      ) : null}
    </Modal>
  );
};

export default TopicDeleteImpactModal;
