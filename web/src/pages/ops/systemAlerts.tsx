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

import { useEffect, useState } from 'react';
import { Card, Tag, Flex, Typography, Button, message, Pagination } from 'antd';
import { CheckCircle, Trash } from '@phosphor-icons/react';
import PageHeader from '../../components/PageHeader';
import { useLang } from '../../i18n/LangContext';
import {
  acknowledgeAlert,
  clearAcknowledgedAlerts,
  listSystemAlerts,
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
  const [levelFilter, setLevelFilter] = useState<string>('all');
  const [loading, setLoading] = useState(true);
  const [acknowledgingIds, setAcknowledgingIds] = useState<Set<string>>(() => new Set());
  const [clearing, setClearing] = useState(false);

  useEffect(() => {
    let cancelled = false;

    setLoading(true);
    void listSystemAlerts({
      level: levelFilter === 'all' ? undefined : levelFilter,
      page,
      pageSize,
    })
      .then((data) => {
        if (!cancelled) {
          setAlerts(data.items);
          setTotal(data.total);
        }
      })
      .catch(() => {
        if (!cancelled) message.error('系统告警加载失败，请稍后重试');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [levelFilter, page, pageSize]);

  const handleAck = async (id: string) => {
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
      const fresh = await listSystemAlerts({
        level: levelFilter === 'all' ? undefined : levelFilter,
        page,
        pageSize,
      });
      if (fresh.items.length === 0 && fresh.total > 0 && page > 1) {
        setPage(page - 1);
      } else {
        setAlerts(fresh.items);
        setTotal(fresh.total);
      }
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
        subtitle={t('sysAlerts.subtitle', { n: total })}
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
        {['all', 'error', 'warning', 'info'].map((level) => (
          <Button
            key={level}
            type={levelFilter === level ? 'primary' : 'default'}
            size="small"
            onClick={() => {
              setLevelFilter(level);
              setPage(1);
            }}
          >
            {level === 'all' ? t('common.all') : alertLevelConfig[level]?.label}
          </Button>
        ))}
      </Flex>

      <Flex vertical gap={12}>
        {loading && <Card loading />}
        {!loading &&
          alerts.map((alert) => {
            const normalizedLevel = normalizeAlertLevel(alert.level);
            const cfg = alertLevelConfig[normalizedLevel] ?? {
              color: '#8c8c8c',
              bg: '#fafafa',
              label: alert.level || t('common.na'),
            };
            return (
              <div
                key={alert.id}
                style={{
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: 12,
                  padding: '12px 16px',
                  borderRadius: 8,
                  background: cfg.bg,
                  borderLeft: `3px solid ${cfg.color}`,
                  opacity: alert.acknowledged ? 0.6 : 1,
                }}
              >
                <div style={{ flex: 1 }}>
                  <Flex align="center" gap={8}>
                    <Text strong style={{ fontSize: 13 }}>
                      {alert.title}
                    </Text>
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
                      style={{ fontSize: 11, lineHeight: '18px', padding: '0 6px' }}
                    >
                      {cfg.label}
                    </Tag>
                  </Flex>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {alert.description}
                  </Text>
                </div>
                <Flex align="center" gap={8} style={{ flexShrink: 0 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {alert.time}
                  </Text>
                  {!alert.acknowledged && (
                    <Button
                      size="small"
                      type="link"
                      icon={<CheckCircle size={14} />}
                      onClick={() => handleAck(alert.id)}
                      loading={acknowledgingIds.has(alert.id)}
                    >
                      {t('sysAlerts.acknowledge')}
                    </Button>
                  )}
                </Flex>
              </div>
            );
          })}
        {!loading && alerts.length === 0 && (
          <Card>
            <Flex justify="center" style={{ padding: 40 }}>
              <Text type="secondary">{t('sysAlerts.noAlerts')}</Text>
            </Flex>
          </Card>
        )}
      </Flex>
      {total > 0 && (
        <Flex justify="end" style={{ marginTop: 16 }}>
          <Pagination
            current={page}
            pageSize={pageSize}
            total={total}
            showSizeChanger
            pageSizeOptions={[20, 50, 100]}
            onChange={(nextPage, nextPageSize) => {
              setPage(nextPage);
              setPageSize(nextPageSize);
            }}
          />
        </Flex>
      )}
    </div>
  );
};

export default SystemAlertsPage;
