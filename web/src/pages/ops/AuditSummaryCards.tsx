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

const { Text } = Typography;

interface Props {
  summary: AuditSummary | null;
  loading: boolean;
}

const BucketList = ({ items, total }: { items: AuditSummaryBucket[]; total: number }) => {
  if (!items.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" />;
  return (
    <Flex vertical gap={10}>
      {items.slice(0, 5).map((item) => (
        <div key={item.name}>
          <Flex justify="space-between" gap={8}>
            <Text ellipsis title={item.name}>
              {item.name.replace(/_/g, ' ')}
            </Text>
            <Tag>{item.count}</Tag>
          </Flex>
          <Progress
            percent={total ? Math.round((item.count / total) * 100) : 0}
            showInfo={false}
            size="small"
          />
        </div>
      ))}
    </Flex>
  );
};

const AuditSummaryCards = ({ summary, loading }: Props) => {
  // Show the placeholder while any fetch is in flight so a filter change does
  // not keep painting a stale summary until the refreshed aggregate arrives.
  if (loading) return <Skeleton active paragraph={{ rows: 4 }} />;
  const data = summary || {
    total: 0,
    successful: 0,
    failed: 0,
    partial: 0,
    uniqueOperators: 0,
    latestAt: null,
    byOperation: [],
    byResourceType: [],
  };
  const successRate = data.total ? Math.round((data.successful / data.total) * 100) : 0;
  return (
    <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
      <Col xs={12} lg={6}>
        <Card size="small">
          <Statistic title="匹配记录" value={data.total} />
        </Card>
      </Col>
      <Col xs={12} lg={6}>
        <Card size="small">
          <Statistic
            title="成功率"
            value={successRate}
            suffix="%"
            valueStyle={{ color: '#52c41a' }}
          />
        </Card>
      </Col>
      <Col xs={12} lg={6}>
        <Card size="small">
          <Statistic
            title="失败 / 部分成功"
            value={`${data.failed} / ${data.partial}`}
            valueStyle={{ color: data.failed ? '#cf1322' : undefined }}
          />
        </Card>
      </Col>
      <Col xs={12} lg={6}>
        <Card size="small">
          <Statistic title="操作人数" value={data.uniqueOperators} />
        </Card>
      </Col>
      <Col xs={24} lg={12}>
        <Card size="small" title="高频操作">
          <BucketList items={data.byOperation} total={data.total} />
        </Card>
      </Col>
      <Col xs={24} lg={12}>
        <Card size="small" title="资源类型分布">
          <BucketList items={data.byResourceType} total={data.total} />
        </Card>
      </Col>
    </Row>
  );
};

export default AuditSummaryCards;
