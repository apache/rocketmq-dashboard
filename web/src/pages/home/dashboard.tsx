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

import { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Row, Col, Card, Table, Tag, Typography, Flex, Space } from 'antd';
import {
  ClusterOutlined,
  ThunderboltOutlined,
  ArrowDownOutlined,
  RightOutlined,
} from '@ant-design/icons';
import { ListDashes } from '@phosphor-icons/react';
import PageHeader from '../../components/PageHeader';
import StatusBadge from '../../components/StatusBadge';
import MiniLine from '../../components/MiniLine';
import { CLUSTER_TYPE_MAP, THEME_COLORS } from '../../constants/theme';
import { getDashboard } from '../../services/dashboardService';
import type { DashboardData } from '../../api/metrics';
import { useLang } from '../../i18n/LangContext';
import { formatNumber } from '../../utils/format';

const { Text } = Typography;

// ─── Stat card color palette ────────────────────────────────────
const CARD_STYLES = [
  {
    gradient: 'linear-gradient(135deg, #52c41a 0%, #73d13d 100%)',
    bg: '#f6ffed',
    border: '#b7eb8f',
  },
  {
    gradient: 'linear-gradient(135deg, #1677ff 0%, #4096ff 100%)',
    bg: '#e6f4ff',
    border: '#91caff',
  },
  {
    gradient: 'linear-gradient(135deg, #fa8c16 0%, #ffa940 100%)',
    bg: '#fff7e6',
    border: '#ffd591',
  },
  {
    gradient: 'linear-gradient(135deg, #722ed1 0%, #9254de 100%)',
    bg: '#f9f0ff',
    border: '#d3adf7',
  },
] as const;

const DashboardPage = () => {
  const navigate = useNavigate();
  const { t } = useLang();
  const [dashboard, setDashboard] = useState<DashboardData | null>(null);

  useEffect(() => {
    getDashboard().then(setDashboard).catch(console.error);
  }, []);

  const { stats, clusters } = dashboard ?? {};

  // ─── Stat cards config ────────────────────────────────────────
  const statCards = [
    {
      title: t('dashboard.clusters'),
      value: stats.totalClusters,
      icon: <ClusterOutlined style={{ fontSize: 24 }} />,
      style: CARD_STYLES[0],
      detail: `${stats.totalBrokers} Brokers · ${stats.totalProxies} Proxy`,
    },
    {
      title: t('dashboard.topics'),
      value: stats.totalTopics,
      icon: <ListDashes size={24} weight="duotone" />,
      style: CARD_STYLES[1],
      detail: t('dashboard.consumerGroups', { n: stats.totalConsumerGroups }),
    },
    {
      title: t('dashboard.tpsIn'),
      value: stats.tpsIn,
      icon: <ArrowDownOutlined style={{ fontSize: 22 }} />,
      style: CARD_STYLES[2],
      suffix: '/s',
      detail: `${t('dashboard.tpsOut')}: ${formatNumber(stats.tpsOut)}/s`,
    },
    {
      title: t('dashboard.todayMessages'),
      value: stats.totalMessagesToday,
      icon: <ThunderboltOutlined style={{ fontSize: 24 }} />,
      style: CARD_STYLES[3],
      detail: t('dashboard.millionMessages', {
        n: (stats.totalMessagesToday / 1_000_000).toFixed(1),
      }),
    },
  ];

  // ─── TPS trend data (aggregate from clusters) ────────────────
  const tpsInTrend = useMemo(() => {
    if (!clusters.length) return [];
    const len = clusters[0].throughput.length;
    return Array.from({ length: len }, (_, i) =>
      clusters.reduce((sum, c) => sum + (c.throughput[i] ?? 0), 0),
    );
  }, [clusters]);

  if (!dashboard) return null;

  // ── Cluster table columns ────────────────────────────────────
  const clusterColumns = [
    {
      title: t('dashboard.clusterName'),
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: DashboardData['clusters'][0]) => (
        <Flex align="center" gap={8}>
          <div
            style={{
              width: 8,
              height: 8,
              borderRadius: '50%',
              backgroundColor:
                record.status === 'healthy'
                  ? THEME_COLORS.success
                  : record.status === 'warning'
                    ? THEME_COLORS.warning
                    : THEME_COLORS.error,
              flexShrink: 0,
            }}
          />
          <Text strong style={{ fontSize: 13 }}>
            {name}
          </Text>
        </Flex>
      ),
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (status: string) => <StatusBadge status={status} />,
    },
    {
      title: t('common.type'),
      dataIndex: 'type',
      key: 'type',
      width: 120,
      render: (type: string) => {
        const info = CLUSTER_TYPE_MAP[type];
        return info ? <Tag color={info.color}>{t(info.labelKey)}</Tag> : type;
      },
    },
    {
      title: t('common.version'),
      dataIndex: 'version',
      key: 'version',
      width: 80,
      render: (v: string) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {v}
        </Text>
      ),
    },
    {
      title: t('dashboard.broker'),
      dataIndex: 'brokers',
      key: 'brokers',
      width: 70,
      align: 'center' as const,
      render: (v: number) => <Text strong>{Math.max(0, v)}</Text>,
    },
    {
      title: t('dashboard.proxy'),
      dataIndex: 'proxies',
      key: 'proxies',
      width: 70,
      align: 'center' as const,
      render: (v: number) => <Text strong>{Math.max(0, v)}</Text>,
    },
    {
      title: t('dashboard.topic'),
      dataIndex: 'topics',
      key: 'topics',
      width: 70,
      align: 'center' as const,
    },
    {
      title: t('dashboard.group'),
      dataIndex: 'groups',
      key: 'groups',
      width: 70,
      align: 'center' as const,
    },
    {
      title: t('dashboard.tpsIn'),
      dataIndex: 'tpsIn',
      key: 'tpsIn',
      width: 100,
      align: 'right' as const,
      render: (v: number) => (
        <Text style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 500 }}>
          {formatNumber(v)}
        </Text>
      ),
    },
    {
      title: t('dashboard.tpsOut'),
      dataIndex: 'tpsOut',
      key: 'tpsOut',
      width: 100,
      align: 'right' as const,
      render: (v: number) => (
        <Text style={{ fontVariantNumeric: 'tabular-nums', fontWeight: 500 }}>
          {formatNumber(v)}
        </Text>
      ),
    },
    {
      title: t('dashboard.trend'),
      dataIndex: 'throughput',
      key: 'throughput',
      width: 140,
      render: (data: number[], record: DashboardData['clusters'][0]) => (
        <MiniLine
          data={data}
          color={
            record.status === 'healthy'
              ? THEME_COLORS.success
              : record.status === 'warning'
                ? THEME_COLORS.warning
                : THEME_COLORS.error
          }
          height={28}
          width={120}
          strokeWidth={1.5}
        />
      ),
    },
  ];

  return (
    <div style={{ padding: '20px 24px', minHeight: '100%' }}>
      <PageHeader title={t('dashboard.title')} subtitle={t('dashboard.subtitle')} />

      {/* ── Stat Cards ─────────────────────────────────────────── */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        {statCards.map((card) => (
          <Col xs={12} sm={12} md={6} key={card.title}>
            <Card
              size="small"
              style={{
                borderRadius: 12,
                border: `1px solid ${card.style.border}`,
                background: card.style.bg,
                overflow: 'hidden',
                position: 'relative',
              }}
              styles={{ body: { padding: '20px 20px 16px' } }}
            >
              {/* Decorative gradient circle */}
              <div
                style={{
                  position: 'absolute',
                  top: -20,
                  right: -20,
                  width: 80,
                  height: 80,
                  borderRadius: '50%',
                  background: card.style.gradient,
                  opacity: 0.12,
                }}
              />
              <Flex align="flex-start" justify="space-between">
                <div>
                  <Text
                    type="secondary"
                    style={{ fontSize: 13, display: 'block', marginBottom: 8 }}
                  >
                    {card.title}
                  </Text>
                  <div
                    style={{
                      fontSize: 28,
                      fontWeight: 700,
                      color: '#141414',
                      fontVariantNumeric: 'tabular-nums',
                      lineHeight: 1.2,
                    }}
                  >
                    {typeof card.value === 'number' ? formatNumber(card.value) : card.value}
                    {card.suffix && (
                      <Text
                        type="secondary"
                        style={{ fontSize: 14, fontWeight: 400, marginLeft: 2 }}
                      >
                        {card.suffix}
                      </Text>
                    )}
                  </div>
                  <Text type="secondary" style={{ fontSize: 12, marginTop: 6, display: 'block' }}>
                    {card.detail}
                  </Text>
                </div>
                <div
                  style={{
                    width: 44,
                    height: 44,
                    borderRadius: 10,
                    background: card.style.gradient,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: '#fff',
                    flexShrink: 0,
                    boxShadow: `0 4px 12px ${card.style.border}66`,
                  }}
                >
                  {card.icon}
                </div>
              </Flex>
            </Card>
          </Col>
        ))}
      </Row>

      {/* ── TPS Trend Chart ────────────────────────────────────── */}
      <Card
        title={t('dashboard.tpsTrend')}
        extra={
          <Text type="secondary" style={{ fontSize: 12 }}>
            {t('dashboard.last12h')}
          </Text>
        }
        style={{ borderRadius: 12, marginBottom: 24 }}
        styles={{ body: { padding: '20px 24px 16px' } }}
      >
        <Flex gap={40} align="center" style={{ marginBottom: 16 }}>
          <Space size={6}>
            <div
              style={{ width: 12, height: 3, borderRadius: 2, background: THEME_COLORS.primary }}
            />
            <Text style={{ fontSize: 12, color: '#8c8c8c' }}>{t('dashboard.tpsInLabel')}</Text>
          </Space>
          <Space size={6}>
            <div
              style={{ width: 12, height: 3, borderRadius: 2, background: THEME_COLORS.success }}
            />
            <Text style={{ fontSize: 12, color: '#8c8c8c' }}>{t('dashboard.tpsOutLabel')}</Text>
          </Space>
        </Flex>
        <Flex gap={24} align="flex-end" style={{ width: '100%' }}>
          <div style={{ flex: 1, position: 'relative' }}>
            <MiniLine
              data={tpsInTrend}
              color={THEME_COLORS.primary}
              height={80}
              width={600}
              responsive
              fill
              strokeWidth={2}
            />
          </div>
        </Flex>
        {/* Time axis labels */}
        <Flex justify="space-between" style={{ marginTop: 8 }}>
          <Text type="secondary" style={{ fontSize: 11 }}>
            -12h
          </Text>
          <Text type="secondary" style={{ fontSize: 11 }}>
            -6h
          </Text>
          <Text type="secondary" style={{ fontSize: 11 }}>
            now
          </Text>
        </Flex>
      </Card>

      {/* ── Cluster Health Table ───────────────────────────────── */}
      <Card
        title={t('dashboard.clusterHealth')}
        extra={
          <a
            onClick={() => navigate('/cluster')}
            style={{ display: 'flex', alignItems: 'center', gap: 4 }}
          >
            {t('common.viewAll')} <RightOutlined style={{ fontSize: 10 }} />
          </a>
        }
        style={{ borderRadius: 12 }}
        styles={{ body: { padding: '0 20px 16px' } }}
      >
        <Table
          dataSource={clusters}
          columns={clusterColumns}
          rowKey="id"
          size="middle"
          pagination={false}
          onRow={() => ({
            style: { cursor: 'pointer' },
            onClick: () => navigate('/cluster'),
          })}
        />
      </Card>
    </div>
  );
};

export default DashboardPage;
