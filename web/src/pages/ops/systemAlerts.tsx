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
import { Card, Tag, Flex, Typography, Badge, Button, message } from 'antd';
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

const SystemAlertsPage = () => {
  const { t } = useLang();

  const alertLevelConfig: Record<string, { color: string; bg: string; label: string }> = {
    error: { color: '#ff4d4f', bg: '#fff2f0', label: t('sysAlerts.severe') },
    warning: { color: '#fa8c16', bg: '#fff7e6', label: t('sysAlerts.warning') },
    info: { color: '#1677ff', bg: '#e6f4ff', label: t('sysAlerts.info') },
  };

  const [alerts, setAlerts] = useState<SystemAlert[]>([]);
  const [levelFilter, setLevelFilter] = useState<string>('all');
  const [loading, setLoading] = useState(true);
  const [acknowledgingId, setAcknowledgingId] = useState<string | null>(null);
  const [clearing, setClearing] = useState(false);

  useEffect(() => {
    let cancelled = false;

    void listSystemAlerts()
      .then((data) => {
        if (!cancelled) setAlerts(data);
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
  }, []);

  const filtered = levelFilter === 'all' ? alerts : alerts.filter((a) => a.level === levelFilter);

  const unackCount = alerts.filter((a) => !a.acknowledged).length;

  const handleAck = async (id: string) => {
    setAcknowledgingId(id);
    try {
      await acknowledgeAlert(id);
      setAlerts((prev) => prev.map((a) => (a.id === id ? { ...a, acknowledged: true } : a)));
      message.success(t('sysAlerts.acknowledged'));
    } catch {
      message.error('确认告警失败，请稍后重试');
    } finally {
      setAcknowledgingId(null);
    }
  };

  const handleClearAcked = async () => {
    setClearing(true);
    try {
      await clearAcknowledgedAlerts();
      setAlerts((prev) => prev.filter((a) => !a.acknowledged));
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
        {['all', 'error', 'warning', 'info'].map((level) => (
          <Button
            key={level}
            type={levelFilter === level ? 'primary' : 'default'}
            size="small"
            onClick={() => setLevelFilter(level)}
          >
            {level === 'all' ? t('common.all') : alertLevelConfig[level]?.label}
            {level !== 'all' && (
              <Badge
                count={alerts.filter((a) => a.level === level).length}
                style={{
                  marginLeft: 4,
                  backgroundColor:
                    level === 'error' ? '#ff4d4f' : level === 'warning' ? '#fa8c16' : '#1677ff',
                }}
                size="small"
              />
            )}
          </Button>
        ))}
      </Flex>

      <Flex vertical gap={12}>
        {loading && <Card loading />}
        {!loading &&
          filtered.map((alert) => {
            const cfg = alertLevelConfig[alert.level];
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
                        alert.level === 'error'
                          ? 'error'
                          : alert.level === 'warning'
                            ? 'warning'
                            : 'processing'
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
                      loading={acknowledgingId === alert.id}
                    >
                      {t('sysAlerts.acknowledge')}
                    </Button>
                  )}
                </Flex>
              </div>
            );
          })}
        {!loading && filtered.length === 0 && (
          <Card>
            <Flex justify="center" style={{ padding: 40 }}>
              <Text type="secondary">{t('sysAlerts.noAlerts')}</Text>
            </Flex>
          </Card>
        )}
      </Flex>
    </div>
  );
};

export default SystemAlertsPage;
