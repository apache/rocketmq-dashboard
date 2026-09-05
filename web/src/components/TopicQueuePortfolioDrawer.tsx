/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import { useMemo, useState } from 'react';
import {
  Alert,
  Card,
  Col,
  Drawer,
  Input,
  Progress,
  Row,
  Segmented,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Topic } from '../api/metadata';
import { useLang } from '../i18n/LangContext';
import {
  buildTopicQueuePortfolio,
  type TopicQueueProfile,
  type TopicQueueProfileStatus,
} from '../utils/topicQueuePortfolio';

interface Props {
  open: boolean;
  loading: boolean;
  topics: Topic[];
  onClose: () => void;
}
type Filter = 'ALL' | 'ATTENTION' | TopicQueueProfileStatus;
const colors: Record<TopicQueueProfileStatus, string> = {
  BALANCED: 'green',
  ASYMMETRIC: 'gold',
  READ_ONLY: 'blue',
  WRITE_ONLY: 'orange',
  NO_ACCESS: 'red',
  UNKNOWN_PERMISSION: 'purple',
};

export const TopicQueuePortfolioDrawer = ({ open, loading, topics, onClose }: Props) => {
  const { t } = useLang();
  const [filter, setFilter] = useState<Filter>('ALL');
  const [search, setSearch] = useState('');
  const portfolio = useMemo(() => buildTopicQueuePortfolio(topics), [topics]);
  const rows = useMemo(() => {
    const query = search.trim().toLowerCase();
    return portfolio.profiles.filter((profile) => {
      const statusMatches =
        filter === 'ALL' ||
        (filter === 'ATTENTION' ? profile.status !== 'BALANCED' : profile.status === filter);
      const textMatches =
        !query ||
        [profile.type, profile.namespace, profile.permission, ...profile.sampleTopics]
          .join(' ')
          .toLowerCase()
          .includes(query);
      return statusMatches && textMatches;
    });
  }, [filter, portfolio.profiles, search]);
  const label = (status: TopicQueueProfileStatus) => t(`topicPortfolio.${status}`);
  const columns: ColumnsType<TopicQueueProfile> = [
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 130,
      render: (value: TopicQueueProfileStatus) => <Tag color={colors[value]}>{label(value)}</Tag>,
    },
    { title: t('topicPortfolio.type'), dataIndex: 'type', key: 'type', width: 130 },
    { title: t('topicPortfolio.namespace'), dataIndex: 'namespace', key: 'namespace', width: 150 },
    {
      title: t('topicPortfolio.permission'),
      dataIndex: 'permission',
      key: 'permission',
      width: 100,
      render: (value: string) => <Tag>{value}</Tag>,
    },
    {
      title: t('topicPortfolio.writeReadQueues'),
      key: 'queues',
      width: 130,
      render: (_, row) => `${row.writeQueues} / ${row.readQueues}`,
    },
    {
      title: t('topicPortfolio.topics'),
      dataIndex: 'topicCount',
      key: 'topicCount',
      width: 100,
      sorter: (a, b) => a.topicCount - b.topicCount,
    },
    {
      title: t('topicPortfolio.share'),
      dataIndex: 'sharePercent',
      key: 'sharePercent',
      width: 160,
      render: (value: number) => <Progress percent={value} size="small" />,
    },
    {
      title: t('topicPortfolio.capacity'),
      key: 'capacity',
      width: 140,
      render: (_, row) => `${row.totalWriteQueues} / ${row.totalReadQueues}`,
    },
    {
      title: t('topicPortfolio.messages'),
      dataIndex: 'messageCount',
      key: 'messageCount',
      width: 120,
    },
    { title: 'TPS', dataIndex: 'tps', key: 'tps', width: 100 },
    {
      title: t('topicPortfolio.consumers'),
      dataIndex: 'consumerGroups',
      key: 'consumerGroups',
      width: 110,
    },
    {
      title: t('topicPortfolio.samples'),
      dataIndex: 'sampleTopics',
      key: 'sampleTopics',
      render: (values: string[]) => (
        <Typography.Text ellipsis={{ tooltip: values.join(', ') }} style={{ maxWidth: 260 }}>
          {values.join(', ') || '-'}
        </Typography.Text>
      ),
    },
  ];
  const attention =
    portfolio.summary.asymmetricTopics +
    portfolio.summary.restrictedTopics +
    portfolio.summary.unknownPermissionTopics;
  const cards = [
    ['topicPortfolio.topics', portfolio.summary.topics],
    ['topicPortfolio.profiles', portfolio.summary.profiles],
    ['topicPortfolio.balanced', portfolio.summary.balancedTopics],
    ['topicPortfolio.attention', attention],
  ] as const;

  return (
    <Drawer
      title={t('topicPortfolio.title')}
      open={open}
      onClose={onClose}
      width={1180}
      destroyOnHidden
    >
      <Alert
        showIcon
        type={attention ? 'warning' : 'success'}
        message={attention ? t('topicPortfolio.hasAttention') : t('topicPortfolio.allBalanced')}
        description={t('topicPortfolio.description')}
        style={{ marginBottom: 16 }}
      />
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        {cards.map(([key, value]) => (
          <Col xs={12} xl={6} key={key}>
            <Card size="small">
              <Statistic title={t(key)} value={value} />
            </Card>
          </Col>
        ))}
      </Row>
      <Space wrap style={{ marginBottom: 16 }}>
        <Segmented
          value={filter}
          onChange={(value) => setFilter(value as Filter)}
          options={[
            { value: 'ALL', label: t('common.all') },
            { value: 'ATTENTION', label: t('topicPortfolio.attention') },
            { value: 'BALANCED', label: label('BALANCED') },
            { value: 'ASYMMETRIC', label: label('ASYMMETRIC') },
            { value: 'READ_ONLY', label: label('READ_ONLY') },
            { value: 'WRITE_ONLY', label: label('WRITE_ONLY') },
          ]}
        />
        <Input.Search
          allowClear
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder={t('topicPortfolio.search')}
          style={{ width: 300 }}
        />
        <Typography.Text type="secondary">
          {t('topicPortfolio.totalCapacity', {
            write: portfolio.summary.writeQueues,
            read: portfolio.summary.readQueues,
            share: portfolio.summary.dominantProfilePercent,
          })}
        </Typography.Text>
      </Space>
      <Table
        rowKey="key"
        loading={loading}
        columns={columns}
        dataSource={rows}
        size="small"
        pagination={{ pageSize: 20 }}
        scroll={{ x: 1600 }}
        locale={{ emptyText: t('topicPortfolio.empty') }}
      />
    </Drawer>
  );
};

export default TopicQueuePortfolioDrawer;
