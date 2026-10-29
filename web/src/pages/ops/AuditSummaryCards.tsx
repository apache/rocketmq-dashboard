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

import { Card, Col, Empty, Flex, Progress, Row, Skeleton, Statistic, Tag, Typography } from 'antd';
import type { AuditSummary, AuditSummaryBucket } from '../../api/audit';
import { useLang } from '../../i18n/LangContext';
import { getAuditOperationPresentation, getAuditResourcePresentation } from './auditPresentation';

const { Text } = Typography;

interface Props {
  summary: AuditSummary | null;
  loading: boolean;
}

// 榜项用本地化操作名 / 资源名（如「创建 Topic」），不裸显 CREATE_TOPIC 这类内部码；
// 每行单行放下（名称 + 内联占比条 + 计数），名称与占比条不再各占一行把卡片撑高。
const BucketList = ({
  items,
  total,
  kind,
}: {
  items: AuditSummaryBucket[];
  total: number;
  kind: 'operation' | 'resource';
}) => {
  const { t } = useLang();
  if (!items.length)
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('common.noData')} />;
  return (
    <Flex vertical gap={8}>
      {items.slice(0, 5).map((item) => {
        const presentation =
          kind === 'operation'
            ? getAuditOperationPresentation(item.name)
            : getAuditResourcePresentation(item.name);
        const label = presentation.labelKey ? t(presentation.labelKey) : presentation.label;
        return (
          <Flex key={item.name} align="center" gap={8}>
            <Text ellipsis style={{ flex: 1, minWidth: 0 }} title={label}>
              {label}
            </Text>
            <Progress
              percent={total ? Math.round((item.count / total) * 100) : 0}
              showInfo={false}
              size="small"
              style={{ width: 96, margin: 0 }}
            />
            <Tag style={{ marginInlineEnd: 0 }}>{item.count}</Tag>
          </Flex>
        );
      })}
    </Flex>
  );
};

const AuditSummaryCards = ({ summary, loading }: Props) => {
  const { t } = useLang();
  // Show the placeholder while any fetch is in flight so a filter change does
  // not keep painting a stale summary until the refreshed aggregate arrives.
  if (loading) return <Skeleton active paragraph={{ rows: 4 }} />;
  if (!summary) return <Empty description={t('common.fetchDataFailed')} />;
  const data = summary;
  const successRate = data.total ? Math.round((data.successful / data.total) * 100) : 0;
  // 成功率配色按阈值走，不再写死绿色（用户约定 2026-10-16）：47% 曾照样显示绿色，读起来像健康。
  const successColor = successRate >= 95 ? '#52c41a' : successRate >= 80 ? '#faad14' : '#cf1322';
  return (
    <Row gutter={[12, 12]} align="stretch" style={{ marginBottom: 16 }}>
      <Col xs={12} lg={6}>
        <Card size="small" style={{ height: '100%' }}>
          <Statistic title={t('audit.summaryMatched')} value={data.total} />
        </Card>
      </Col>
      <Col xs={12} lg={6}>
        <Card size="small" style={{ height: '100%' }}>
          <Statistic
            title={t('audit.summarySuccessRate')}
            value={successRate}
            suffix="%"
            valueStyle={{ color: successColor }}
          />
          {/* 分母是匹配记录总数、含未记录结果的操作（只读查询等），单看百分比会和「失败率」
              对不上（47% 成功 + 15% 失败 ≠ 100%），把分子分母摆出来让口径自解释。 */}
          <Text type="secondary" style={{ fontSize: 14 }}>
            {t('audit.summarySuccessDetail', {
              successful: data.successful,
              total: data.total,
            })}
          </Text>
        </Card>
      </Col>
      <Col xs={12} lg={6}>
        <Card size="small" style={{ height: '100%' }}>
          <Statistic
            title={t('auditInsights.failPartial')}
            value={`${data.failed} / ${data.partial}`}
            valueStyle={{ color: data.failed ? '#cf1322' : undefined }}
          />
        </Card>
      </Col>
      <Col xs={12} lg={6}>
        <Card size="small" style={{ height: '100%' }}>
          <Statistic title={t('audit.summaryOperators')} value={data.uniqueOperators} />
        </Card>
      </Col>
      <Col xs={24} lg={12}>
        <Card size="small" title={t('audit.summaryTopOperations')} style={{ height: '100%' }}>
          <BucketList items={data.byOperation} total={data.total} kind="operation" />
        </Card>
      </Col>
      <Col xs={24} lg={12}>
        <Card size="small" title={t('audit.summaryResourceTypes')} style={{ height: '100%' }}>
          <BucketList items={data.byResourceType} total={data.total} kind="resource" />
        </Card>
      </Col>
    </Row>
  );
};

export default AuditSummaryCards;
