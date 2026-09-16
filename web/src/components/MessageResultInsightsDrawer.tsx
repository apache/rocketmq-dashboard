/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import { useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Drawer,
  Flex,
  Input,
  Progress,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { MessageRecord } from '../api/message';
import { buildCsv, downloadCsv } from '../utils/download';
import {
  buildMessageResultInsights,
  filterMessageDimensionRows,
  type MessageDimensionRow,
} from '../utils/messageResultInsights';

interface Props {
  open: boolean;
  messages: MessageRecord[];
  serverTotal: number;
  truncated: boolean;
  onClose: () => void;
}
const formatBytes = (bytes: number) =>
  bytes >= 1024 * 1024
    ? `${(bytes / 1024 / 1024).toFixed(2)} MiB`
    : bytes >= 1024
      ? `${(bytes / 1024).toFixed(2)} KiB`
      : `${bytes} B`;
const dimensionLabel: Record<MessageDimensionRow['dimension'], string> = {
  TAG: 'Tag',
  BROKER: 'Broker',
  QUEUE: 'Broker / Queue',
  BORN_HOST: '生产主机',
  STORE_HOST: '存储主机',
  HOUR: '小时',
};

export const MessageResultInsightsDrawer = ({
  open,
  messages,
  serverTotal,
  truncated,
  onClose,
}: Props) => {
  const [dimension, setDimension] = useState<MessageDimensionRow['dimension']>();
  const [search, setSearch] = useState('');
  const insights = useMemo(
    () => buildMessageResultInsights(messages, serverTotal),
    [messages, serverTotal],
  );
  const rows = useMemo(
    () => filterMessageDimensionRows(insights.dimensions, dimension, search),
    [dimension, insights.dimensions, search],
  );
  const columns: ColumnsType<MessageDimensionRow> = [
    {
      title: '维度',
      dataIndex: 'dimension',
      key: 'dimension',
      width: 130,
      render: (value: MessageDimensionRow['dimension']) => <Tag>{dimensionLabel[value]}</Tag>,
    },
    { title: '值', dataIndex: 'value', key: 'value' },
    {
      title: '消息数',
      dataIndex: 'count',
      key: 'count',
      width: 100,
      sorter: (a, b) => a.count - b.count,
    },
    {
      title: '占比',
      dataIndex: 'percent',
      key: 'percent',
      width: 180,
      render: (value: number) => <Progress percent={value} size="small" />,
    },
    { title: '消息体积', dataIndex: 'bytes', key: 'bytes', width: 130, render: formatBytes },
  ];
  const exportRows = () =>
    downloadCsv(
      `rocketmq-message-result-insights-${new Date().toISOString().slice(0, 10)}.csv`,
      buildCsv(
        [
          { header: 'Dimension', value: (row) => row.dimension },
          { header: 'Value', value: (row) => row.value },
          { header: 'Count', value: (row) => row.count },
          { header: 'Percent', value: (row) => row.percent },
          { header: 'Bytes', value: (row) => row.bytes },
        ],
        rows,
      ),
    );
  const cards = [
    ['当前页消息', insights.summary.loadedMessages],
    ['服务端总数', insights.summary.serverTotal],
    ['Broker 数', insights.summary.uniqueBrokers],
    ['队列数', insights.summary.uniqueQueues],
  ] as const;
  return (
    <Drawer
      title="消息查询结果分析"
      open={open}
      onClose={onClose}
      width={1000}
      destroyOnHidden
      extra={
        <Button icon={<DownloadOutlined />} disabled={!rows.length} onClick={exportRows}>
          导出维度数据
        </Button>
      }
    >
      <Alert
        type={truncated || messages.length < serverTotal ? 'warning' : 'info'}
        showIcon
        message={
          truncated
            ? '服务端扫描结果可能被截断'
            : messages.length < serverTotal
              ? '分析范围仅包含当前加载页'
              : '分析覆盖全部返回结果'
        }
        description={`已加载 ${messages.length} / ${serverTotal} 条；统计不会额外请求或推断未加载消息。`}
        style={{ marginBottom: 16 }}
      />
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        {cards.map(([title, value]) => (
          <Col xs={12} lg={6} key={title}>
            <Card size="small">
              <Statistic title={title} value={value} />
            </Card>
          </Col>
        ))}
      </Row>
      <Tabs
        items={[
          {
            key: 'distribution',
            label: '维度分布',
            children: (
              <>
                <Flex gap={12} wrap style={{ marginBottom: 16 }}>
                  <Select
                    allowClear
                    value={dimension}
                    onChange={setDimension}
                    placeholder="全部维度"
                    style={{ width: 180 }}
                    options={Object.entries(dimensionLabel).map(([value, label]) => ({
                      value,
                      label,
                    }))}
                  />
                  <Input.Search
                    allowClear
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                    placeholder="搜索维度值"
                    style={{ width: 260 }}
                  />
                  <Typography.Text type="secondary">{rows.length} 个分组</Typography.Text>
                </Flex>
                <Table
                  rowKey={(row) => `${row.dimension}-${row.value}`}
                  size="small"
                  columns={columns}
                  dataSource={rows}
                  pagination={{ pageSize: 20, showSizeChanger: true }}
                />
              </>
            ),
          },
          {
            key: 'size',
            label: '消息大小',
            children: (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Flex gap={24} wrap>
                  <Statistic title="总体积" value={formatBytes(insights.summary.totalBytes)} />
                  <Statistic title="平均大小" value={formatBytes(insights.summary.averageBytes)} />
                  <Statistic title="最大消息" value={formatBytes(insights.summary.largestBytes)} />
                </Flex>
                {insights.sizeBuckets.map((bucket) => (
                  <Card size="small" key={bucket.bucket}>
                    <Flex justify="space-between" align="center">
                      <Typography.Text>{bucket.bucket}</Typography.Text>
                      <Space>
                        <Typography.Text>
                          {bucket.count} 条 / {formatBytes(bucket.bytes)}
                        </Typography.Text>
                        <Progress percent={bucket.percent} size="small" style={{ width: 180 }} />
                      </Space>
                    </Flex>
                  </Card>
                ))}
              </Space>
            ),
          },
          {
            key: 'quality',
            label: '元数据完整性',
            children: (
              <Row gutter={[12, 12]}>
                {[
                  ['缺少 Key', insights.summary.missingKeys],
                  ['缺少 Tag', insights.summary.missingTags],
                  ['缺少路由', insights.summary.missingRoutes],
                  ['无效时间', insights.summary.invalidTimestamps],
                ].map(([title, value]) => (
                  <Col xs={12} md={6} key={String(title)}>
                    <Card size="small">
                      <Statistic
                        title={title}
                        value={value}
                        valueStyle={{ color: value ? '#d46b08' : undefined }}
                      />
                    </Card>
                  </Col>
                ))}
              </Row>
            ),
          },
        ]}
      />
    </Drawer>
  );
};
export default MessageResultInsightsDrawer;
