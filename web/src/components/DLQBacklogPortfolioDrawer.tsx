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
  message,
} from 'antd';
import { DownloadOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { listDLQGroups } from '../services/messageService';
import { buildCsv, downloadCsv } from '../utils/download';
import {
  buildDLQBacklogPortfolio,
  filterDLQBacklogRows,
  type DLQAgeBucket,
  type DLQBacklogRow,
} from '../utils/dlqBacklogPortfolio';

interface Props {
  open: boolean;
  instanceId?: string;
  onClose: () => void;
}
const PAGE_SIZE = 100;
const MAX_PAGES = 100;
const ageLabels: Record<DLQAgeBucket, string> = {
  EMPTY: '无积压',
  LAST_HOUR: '最近 1 小时',
  TODAY: '1–24 小时',
  THIS_WEEK: '1–7 天',
  DORMANT: '超过 7 天',
  UNKNOWN: '时间未知',
  UNAVAILABLE: '统计不可用',
};
const loadAll = async (instanceId: string) => {
  const result = [];
  for (let page = 1; page <= MAX_PAGES; page += 1) {
    const response = await listDLQGroups(instanceId, undefined, page, PAGE_SIZE);
    result.push(...response.items);
    if (result.length >= response.total || response.items.length < PAGE_SIZE) return result;
  }
  throw new Error('DLQ portfolio exceeded page limit');
};

export const DLQBacklogPortfolioDrawer = ({ open, instanceId, onClose }: Props) => {
  const [groups, setGroups] = useState<Awaited<ReturnType<typeof loadAll>>>([]);
  const [snapshotAt, setSnapshotAt] = useState<number>();
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [bucket, setBucket] = useState<DLQAgeBucket>();
  const portfolio = useMemo(
    () => buildDLQBacklogPortfolio(groups, snapshotAt),
    [groups, snapshotAt],
  );
  const filtered = useMemo(
    () => filterDLQBacklogRows(portfolio.rows, search, bucket),
    [bucket, portfolio.rows, search],
  );
  const load = async () => {
    if (!instanceId) return;
    setLoading(true);
    try {
      const result = await loadAll(instanceId);
      setGroups(result);
      setSnapshotAt(Date.now());
    } catch {
      message.error('DLQ 全量积压分析加载失败');
    } finally {
      setLoading(false);
    }
  };
  const columns: ColumnsType<DLQBacklogRow> = [
    {
      title: 'Consumer Group',
      dataIndex: 'groupName',
      key: 'groupName',
      fixed: 'left',
      width: 220,
    },
    { title: 'DLQ Topic', dataIndex: 'dlqTopic', key: 'dlqTopic', width: 220 },
    {
      title: '消息数',
      dataIndex: 'messageCount',
      key: 'messageCount',
      width: 110,
      sorter: (a, b) => a.messageCount - b.messageCount,
    },
    {
      title: '积压占比',
      dataIndex: 'backlogShare',
      key: 'backlogShare',
      width: 180,
      render: (value: number) => <Progress percent={value} size="small" />,
    },
    {
      title: '最近入队年龄',
      dataIndex: 'ageBucket',
      key: 'ageBucket',
      width: 140,
      render: (value: DLQAgeBucket) => (
        <Tag
          color={
            value === 'DORMANT'
              ? 'orange'
              : value === 'UNKNOWN' || value === 'UNAVAILABLE'
                ? 'default'
                : 'blue'
          }
        >
          {ageLabels[value]}
        </Tag>
      ),
    },
    { title: '累计重试', dataIndex: 'retryCount', key: 'retryCount', width: 110 },
    { title: '状态', dataIndex: 'status', key: 'status', width: 120 },
  ];
  const exportRows = () =>
    downloadCsv(
      `rocketmq-dlq-backlog-${new Date().toISOString().slice(0, 10)}.csv`,
      buildCsv(
        [
          { header: 'Consumer Group', value: (row) => row.groupName },
          { header: 'DLQ Topic', value: (row) => row.dlqTopic },
          { header: 'Messages', value: (row) => row.messageCount },
          { header: 'Backlog share', value: (row) => row.backlogShare },
          { header: 'Age bucket', value: (row) => row.ageBucket },
          { header: 'Last enqueue', value: (row) => row.lastEnqueueTime },
          { header: 'Retry count', value: (row) => row.retryCount },
          { header: 'Status', value: (row) => row.status },
          { header: 'Stats available', value: (row) => row.statsAvailable !== false },
        ],
        filtered,
      ),
    );
  const cards = [
    ['消费组', portfolio.summary.groups],
    ['积压消息', portfolio.summary.totalMessages],
    ['有积压组', portfolio.summary.groupsWithBacklog],
    ['超过 7 天', portfolio.summary.dormantGroups],
  ] as const;
  return (
    <Drawer
      title="DLQ 全量积压与老化分析"
      open={open}
      onClose={onClose}
      width={1050}
      destroyOnHidden
      extra={
        <Space>
          <Button icon={<DownloadOutlined />} disabled={!filtered.length} onClick={exportRows}>
            导出当前结果
          </Button>
          <Button
            type="primary"
            icon={<ReloadOutlined />}
            loading={loading}
            disabled={!instanceId}
            onClick={load}
          >
            {snapshotAt ? '刷新快照' : '加载全量'}
          </Button>
        </Space>
      }
    >
      <Alert
        showIcon
        type="info"
        message="只读全量快照"
        description={`按每页 ${PAGE_SIZE} 个消费组读取当前实例，最多 ${MAX_PAGES} 页；不读取消息正文，也不触发重投。${snapshotAt ? ` 快照时间：${new Date(snapshotAt).toLocaleString()}` : ''}`}
        style={{ marginBottom: 16 }}
      />
      {!snapshotAt ? (
        <Card>
          <Flex vertical align="center" gap={12}>
            <Typography.Text type="secondary">
              加载后分析全部 DLQ 消费组，而不是仅分析当前表格页。
            </Typography.Text>
            <Button type="primary" loading={loading} disabled={!instanceId} onClick={load}>
              加载全量积压
            </Button>
          </Flex>
        </Card>
      ) : (
        <>
          <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
            {cards.map(([title, value]) => (
              <Col xs={12} lg={6} key={title}>
                <Card size="small">
                  <Statistic title={title} value={value} />
                </Card>
              </Col>
            ))}
          </Row>
          {(portfolio.summary.unavailableGroups > 0 || portfolio.summary.unknownAgeGroups > 0) && (
            <Alert
              type="warning"
              showIcon
              message={`统计不可用 ${portfolio.summary.unavailableGroups} 组，入队时间未知 ${portfolio.summary.unknownAgeGroups} 组`}
              style={{ marginBottom: 16 }}
            />
          )}
          <Tabs
            items={[
              {
                key: 'groups',
                label: `消费组 (${filtered.length})`,
                children: (
                  <>
                    <Flex gap={12} wrap style={{ marginBottom: 16 }}>
                      <Input.Search
                        allowClear
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        placeholder="搜索 Group、Topic 或状态"
                        style={{ width: 300 }}
                      />
                      <Select
                        allowClear
                        value={bucket}
                        onChange={setBucket}
                        placeholder="全部年龄"
                        style={{ width: 180 }}
                        options={Object.entries(ageLabels).map(([value, label]) => ({
                          value,
                          label,
                        }))}
                      />
                    </Flex>
                    <Table
                      rowKey="groupName"
                      size="small"
                      columns={columns}
                      dataSource={filtered}
                      scroll={{ x: 1100 }}
                      pagination={{ pageSize: 20 }}
                    />
                  </>
                ),
              },
              {
                key: 'age',
                label: '年龄分层',
                children: (
                  <Space direction="vertical" style={{ width: '100%' }}>
                    {portfolio.ageBuckets.map((item) => (
                      <Card size="small" key={item.bucket}>
                        <Flex justify="space-between">
                          <Typography.Text>{ageLabels[item.bucket]}</Typography.Text>
                          <Space>
                            <Typography.Text>
                              {item.groups} 组 / {item.messages} 条
                            </Typography.Text>
                            <Progress percent={item.percent} size="small" style={{ width: 220 }} />
                          </Space>
                        </Flex>
                      </Card>
                    ))}
                  </Space>
                ),
              },
              {
                key: 'status',
                label: '状态分布',
                children: (
                  <Table
                    rowKey="status"
                    size="small"
                    pagination={false}
                    dataSource={portfolio.statusBuckets}
                    columns={[
                      { title: '状态', dataIndex: 'status' },
                      { title: '消费组', dataIndex: 'groups' },
                      { title: '消息数', dataIndex: 'messages' },
                    ]}
                  />
                ),
              },
            ]}
          />
        </>
      )}
    </Drawer>
  );
};
export default DLQBacklogPortfolioDrawer;
