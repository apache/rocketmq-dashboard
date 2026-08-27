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

import { useCallback, useEffect, useRef, useState } from 'react';
import { Card, Tag, Typography, Button, Flex, Table, message } from 'antd';
import { CheckCircle, Trash } from '@phosphor-icons/react';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import {
  acknowledgeAlert,
  clearAcknowledgedAlerts,
  listSystemAlertsPage,
} from '../../services/opsService';
import type { SystemAlert } from '../../api/ops';

const { Text } = Typography;

const normalizeAlertLevel = (level?: string | null) => (level ?? '').toLowerCase();

const SystemAlertsPage = () => {
  const { t } = useLang();

  const alertLevelConfig: Record<string, { color: string; bg: string; label: string }> = {
    error: { color: '#ff4d4f', bg: '#fff2f0', label: t('sysAlerts.severe') },
    warning: { color: '#fa8c16', bg: '#fff7e6', label: t('sysAlerts.warning') },
    info: { color: '#1677ff', bg: '#e6f4ff', label: t('sysAlerts.info') },
  };

  const [alerts, setAlerts] = useState<SystemAlert[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [levelFilter, setLevelFilter] = useState<string | undefined>(undefined);
  const [loading, setLoading] = useState(true);
  const [acknowledgingIds, setAcknowledgingIds] = useState<Set<number>>(() => new Set());
  const [clearing, setClearing] = useState(false);
  const requestIdRef = useRef(0);

  const loadAlerts = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    setLoading(true);
    try {
      const result = await listSystemAlertsPage({
        level: levelFilter,
        page,
        pageSize,
      });
      if (requestId !== requestIdRef.current) return;
      setAlerts(result.items);
      setTotal(result.total);
    } catch {
      if (requestId === requestIdRef.current) message.error('系统告警加载失败，请稍后重试');
    } finally {
      if (requestId === requestIdRef.current) setLoading(false);
    }
  }, [levelFilter, page, pageSize]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadAlerts(), 0);
    return () => {
      window.clearTimeout(timer);
      requestIdRef.current += 1;
    };
  }, [loadAlerts]);

  const filtered = alerts;

  const unackCount = alerts.filter((a) => !a.acknowledged).length;

  const handleAck = async (id: number) => {
    setAcknowledgingIds((current) => new Set(current).add(id));
    try {
      await acknowledgeAlert(id);
      setAlerts((prev) => prev.map((a) => (a.id === id ? { ...a, acknowledged: true } : a)));
      message.success(t('sysAlerts.acknowledged'));
    } catch {
      message.error('确认告警失败，请稍后重试');
    } finally {
      setAcknowledgingIds((current) => {
        const next = new Set(current);
        next.delete(id);
        return next;
      });
    }
  };

  const handleClearAcked = async () => {
    setClearing(true);
    try {
      await clearAcknowledgedAlerts();
      await loadAlerts();
      message.success(t('sysAlerts.cleared'));
    } catch {
      message.error('清理已确认告警失败，请稍后重试');
    } finally {
      setClearing(false);
    }
  };

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title={t('sysAlerts.title')}
        subtitle={t('sysAlerts.subtitle', { n: unackCount })}
        extra={
          <Button
            icon={<Trash size={14} />}
            onClick={handleClearAcked}
            disabled={!alerts.some((a) => a.acknowledged)}
            loading={clearing}
          >
            {t('sysAlerts.clearAcked')}
          </Button>
        }
      />

      <Flex gap={8} style={{ marginBottom: 16 }}>
        {[undefined, 'error', 'warning', 'info'].map((level) => (
          <Button
            key={level ?? 'all'}
            type={levelFilter === level ? 'primary' : 'default'}
            size="small"
            onClick={() => {
              setPage(1);
              setLevelFilter(level);
            }}
          >
            {level === undefined ? t('common.all') : alertLevelConfig[level]?.label}
          </Button>
        ))}
      </Flex>

      <Card styles={{ body: { padding: 0 } }}>
        <Table<SystemAlert>
          rowKey="id"
          loading={loading}
          dataSource={filtered}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: ['20', '50', '100'],
            showTotal: (count) => `${t('common.total')} ${count}`,
            onChange: (nextPage, nextPageSize) => {
              if (nextPageSize !== pageSize) {
                setPage(1);
                setPageSize(nextPageSize);
              } else {
                setPage(nextPage);
              }
            },
          }}
          columns={[
            {
              title: t('sysAlerts.severe'),
              dataIndex: 'level',
              width: 110,
              render: (level: string) => {
                const normalizedLevel = normalizeAlertLevel(level);
                const cfg = alertLevelConfig[normalizedLevel] ?? {
                  color: '#8c8c8c',
                  bg: '#fafafa',
                  label: level || t('common.na'),
                };
                return (
                  <Tag
                    color={
                      normalizedLevel === 'error'
                        ? 'error'
                        : normalizedLevel === 'warning'
                          ? 'warning'
                          : normalizedLevel === 'info'
                            ? 'processing'
                            : 'default'
                    }
                    style={{ fontSize: 14, lineHeight: '18px', padding: '0 6px' }}
                  >
                    {cfg.label}
                  </Tag>
                );
              },
            },
            {
              title: t('sysAlerts.title'),
              dataIndex: 'title',
              render: (_: string, alert) => (
                <>
                  <Text strong style={{ fontSize: 14 }}>
                    {alert.title}
                  </Text>
                  <Text type="secondary" style={{ fontSize: 14 }}>
                    {alert.description}
                  </Text>
                </>
              ),
            },
            {
              title: t('audit.time'),
              dataIndex: 'time',
              width: 180,
              render: (time: string) => (
                <Text type="secondary" style={{ fontSize: 14 }}>
                  {time}
                </Text>
              ),
            },
            {
              title: t('common.actions'),
              key: 'actions',
              width: 130,
              render: (_: unknown, alert: SystemAlert) =>
                alert.acknowledged ? (
                  <Tag>{t('sysAlerts.acknowledged')}</Tag>
                ) : (
                  <Button
                    size="small"
                    type="link"
                    icon={<CheckCircle size={14} />}
                    onClick={() => handleAck(alert.id)}
                    loading={acknowledgingIds.has(alert.id)}
                  >
                    {t('sysAlerts.acknowledge')}
                  </Button>
                ),
            },
          ]}
          onRow={(alert) => ({
            style: {
              background: alertLevelConfig[normalizeAlertLevel(alert.level)]?.bg ?? '#fafafa',
              opacity: alert.acknowledged ? 0.6 : 1,
            },
          })}
        />
      </Card>
    </div>
  );
};

export default SystemAlertsPage;
