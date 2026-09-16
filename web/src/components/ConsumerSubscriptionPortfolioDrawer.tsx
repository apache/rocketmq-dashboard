/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import { useMemo, useState } from 'react';
import {
  Alert,
  Card,
  Col,
  Drawer,
  Input,
  Row,
  Segmented,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useLang } from '../i18n/LangContext';
import {
  buildConsumerSubscriptionPortfolio,
  type ConsumerSubscriptionSnapshot,
  type SubscriptionExpressionKind,
  type SubscriptionPortfolioProfile,
} from '../utils/consumerSubscriptionPortfolio';

interface Props {
  open: boolean;
  loading: boolean;
  snapshots: ConsumerSubscriptionSnapshot[];
  availableGroups: number;
  onClose: () => void;
}
type Filter = 'ANY' | 'ATTENTION' | SubscriptionExpressionKind;
const colors: Record<SubscriptionExpressionKind, string> = {
  ALL: 'green',
  TAG_SET: 'blue',
  SQL: 'purple',
  EMPTY: 'red',
  OTHER: 'orange',
};

export const ConsumerSubscriptionPortfolioDrawer = ({
  open,
  loading,
  snapshots,
  availableGroups,
  onClose,
}: Props) => {
  const { t } = useLang();
  const [filter, setFilter] = useState<Filter>('ANY');
  const [search, setSearch] = useState('');
  const portfolio = useMemo(
    () => buildConsumerSubscriptionPortfolio(snapshots, availableGroups),
    [availableGroups, snapshots],
  );
  const rows = useMemo(() => {
    const query = search.trim().toLowerCase();
    return portfolio.profiles.filter((profile) => {
      const attention =
        profile.inconsistentCount > 0 ||
        profile.expressionKind === 'EMPTY' ||
        profile.expressionKind === 'OTHER';
      const statusMatches =
        filter === 'ANY' ||
        (filter === 'ATTENTION' ? attention : profile.expressionKind === filter);
      const textMatches =
        !query ||
        [
          profile.type,
          profile.filterMode,
          profile.consistency,
          ...profile.sampleGroups,
          ...profile.sampleTopics,
          ...profile.sampleExpressions,
        ]
          .join(' ')
          .toLowerCase()
          .includes(query);
      return statusMatches && textMatches;
    });
  }, [filter, portfolio.profiles, search]);
  const expressionLabel = (kind: SubscriptionExpressionKind) => t(`subscriptionPortfolio.${kind}`);
  const samples = (values: string[]) => (
    <Typography.Text ellipsis={{ tooltip: values.join(', ') }} style={{ maxWidth: 220 }}>
      {values.join(', ') || '-'}
    </Typography.Text>
  );
  const columns: ColumnsType<SubscriptionPortfolioProfile> = [
    {
      title: t('subscriptionPortfolio.expressionKind'),
      dataIndex: 'expressionKind',
      key: 'expressionKind',
      width: 120,
      render: (value: SubscriptionExpressionKind) => (
        <Tag color={colors[value]}>{expressionLabel(value)}</Tag>
      ),
    },
    {
      title: t('subscriptionPortfolio.filterMode'),
      dataIndex: 'filterMode',
      key: 'filterMode',
      width: 120,
    },
    { title: t('subscriptionPortfolio.type'), dataIndex: 'type', key: 'type', width: 110 },
    {
      title: t('subscriptionPortfolio.consistency'),
      dataIndex: 'consistency',
      key: 'consistency',
      width: 130,
      render: (value: string, row) => (
        <Tag color={row.inconsistentCount ? 'red' : 'default'}>{value}</Tag>
      ),
    },
    {
      title: t('subscriptionPortfolio.subscriptions'),
      dataIndex: 'subscriptionCount',
      key: 'subscriptionCount',
      width: 100,
    },
    {
      title: t('subscriptionPortfolio.groups'),
      dataIndex: 'groupCount',
      key: 'groupCount',
      width: 90,
    },
    {
      title: t('subscriptionPortfolio.topics'),
      dataIndex: 'topicCount',
      key: 'topicCount',
      width: 90,
    },
    {
      title: t('subscriptionPortfolio.inconsistent'),
      dataIndex: 'inconsistentCount',
      key: 'inconsistentCount',
      width: 100,
    },
    {
      title: t('subscriptionPortfolio.sampleGroups'),
      dataIndex: 'sampleGroups',
      key: 'sampleGroups',
      render: samples,
    },
    {
      title: t('subscriptionPortfolio.sampleTopics'),
      dataIndex: 'sampleTopics',
      key: 'sampleTopics',
      render: samples,
    },
    {
      title: t('subscriptionPortfolio.sampleExpressions'),
      dataIndex: 'sampleExpressions',
      key: 'sampleExpressions',
      render: samples,
    },
  ];
  const summary = portfolio.summary;
  const cards = [
    ['subscriptionPortfolio.inspectedGroups', summary.inspectedGroups],
    ['subscriptionPortfolio.subscriptions', summary.subscriptions],
    ['subscriptionPortfolio.profiles', summary.profiles],
    ['subscriptionPortfolio.inconsistent', summary.inconsistentSubscriptions],
  ] as const;

  return (
    <Drawer
      title={t('subscriptionPortfolio.title')}
      open={open}
      onClose={onClose}
      width={1180}
      destroyOnHidden
    >
      <Alert
        showIcon
        type={summary.failedGroups || summary.omittedGroups ? 'warning' : 'info'}
        message={t('subscriptionPortfolio.description')}
        description={t('subscriptionPortfolio.coverage', {
          inspected: summary.inspectedGroups,
          available: summary.availableGroups,
          failed: summary.failedGroups,
          omitted: summary.omittedGroups,
        })}
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
            { value: 'ANY', label: t('common.all') },
            { value: 'ATTENTION', label: t('subscriptionPortfolio.attention') },
            { value: 'ALL', label: expressionLabel('ALL') },
            { value: 'TAG_SET', label: expressionLabel('TAG_SET') },
            { value: 'SQL', label: expressionLabel('SQL') },
            { value: 'OTHER', label: expressionLabel('OTHER') },
          ]}
        />
        <Input.Search
          allowClear
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder={t('subscriptionPortfolio.search')}
          style={{ width: 330 }}
        />
        <Typography.Text type="secondary">
          {t('subscriptionPortfolio.emptyGroups', { count: summary.emptyGroups })}
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
        locale={{ emptyText: t('subscriptionPortfolio.empty') }}
      />
    </Drawer>
  );
};

export default ConsumerSubscriptionPortfolioDrawer;
