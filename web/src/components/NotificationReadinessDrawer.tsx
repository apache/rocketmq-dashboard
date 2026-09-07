/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import { useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Drawer,
  Row,
  Statistic,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { GeneralSettings } from '../api/settings';
import { testNotification } from '../api/settings';
import { useLang } from '../i18n/LangContext';
import {
  buildNotificationReadiness,
  testReadyNotificationChannels,
  type NotificationChannelResult,
  type NotificationReadinessReport,
  type NotificationTestStatus,
} from '../utils/notificationChannelReadiness';

interface Props {
  open: boolean;
  settings: GeneralSettings | null;
  onClose: () => void;
}

const colors: Record<NotificationTestStatus, string> = {
  READY: 'blue',
  NOT_CONFIGURED: 'default',
  SUCCESS: 'green',
  FAILED: 'red',
};

export const NotificationReadinessDrawer = ({ open, settings, onClose }: Props) => {
  const { t } = useLang();
  const initial = useMemo(
    () => (settings ? buildNotificationReadiness(settings) : undefined),
    [settings],
  );
  const [tested, setTested] = useState<{
    settings: GeneralSettings;
    report: NotificationReadinessReport;
  }>();
  const [loading, setLoading] = useState(false);
  const testedForCurrentSettings = tested?.settings === settings ? tested.report : undefined;
  const report = testedForCurrentSettings ?? initial;

  const label = (status: NotificationTestStatus) =>
    t(
      status === 'READY'
        ? 'settings.notifyReady'
        : status === 'NOT_CONFIGURED'
          ? 'settings.notifyNotConfigured'
          : status === 'SUCCESS'
            ? 'settings.notifyTestSuccess'
            : 'settings.notifyTestFailed',
    );

  const channelLabel = (channel: string) =>
    channel === 'dingtalk'
      ? 'DingTalk'
      : channel === 'email'
        ? t('settings.notifyEmail')
        : t('settings.smsWebhook');

  const run = async () => {
    if (!initial || !settings) return;
    setLoading(true);
    try {
      setTested({
        settings,
        report: await testReadyNotificationChannels(initial, testNotification, 2),
      });
    } catch {
      message.error(t('settings.notifyBatchFailed'));
    } finally {
      setLoading(false);
    }
  };

  const columns: ColumnsType<NotificationChannelResult> = [
    {
      title: t('settings.notifyChannel'),
      dataIndex: 'channel',
      key: 'channel',
      render: channelLabel,
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      render: (value: NotificationTestStatus) => <Tag color={colors[value]}>{label(value)}</Tag>,
    },
    { title: t('settings.notifyDestination'), dataIndex: 'destination', key: 'destination' },
    { title: t('settings.notifyRecipients'), dataIndex: 'recipientCount', key: 'recipientCount' },
    {
      title: t('settings.notifySigning'),
      dataIndex: 'signingEnabled',
      key: 'signingEnabled',
      render: (value: boolean) => (value ? t('common.yes') : '-'),
    },
    {
      title: t('settings.notifyLatency'),
      dataIndex: 'latencyMs',
      key: 'latencyMs',
      render: (value: number | null) => (value === null ? '-' : `${value} ms`),
    },
    { title: t('settings.notifyMessage'), dataIndex: 'message', key: 'message' },
  ];
  const summary = report?.summary;
  const cards = [
    ['settings.notifyChannels', summary?.channels ?? 0],
    ['settings.notifyConfigured', summary?.configured ?? 0],
    ['settings.notifyTestSuccess', summary?.succeeded ?? 0],
    ['settings.notifyTestFailed', summary?.failed ?? 0],
  ] as const;

  return (
    <Drawer
      title={t('settings.notifyReadinessTitle')}
      open={open}
      onClose={onClose}
      width={900}
      destroyOnHidden
      extra={
        <Button
          type="primary"
          icon={<ReloadOutlined />}
          loading={loading}
          disabled={!summary?.configured}
          onClick={run}
        >
          {testedForCurrentSettings ? t('settings.notifyRetest') : t('settings.notifyTestAll')}
        </Button>
      }
    >
      <Alert
        type="info"
        showIcon
        message={t('settings.notifyReadinessDescription')}
        description={t('settings.notifySavedConfigHint')}
        style={{ marginBottom: 16 }}
      />
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        {cards.map(([key, value]) => (
          <Col xs={12} lg={6} key={key}>
            <Card size="small">
              <Statistic title={t(key)} value={value} />
            </Card>
          </Col>
        ))}
      </Row>
      {summary?.unconfigured ? (
        <Alert
          type="warning"
          showIcon
          message={t('settings.notifyMissingCount', { count: summary.unconfigured })}
          style={{ marginBottom: 16 }}
        />
      ) : null}
      <Table
        rowKey="channel"
        size="small"
        columns={columns}
        dataSource={report?.results ?? []}
        pagination={false}
        scroll={{ x: 900 }}
      />
      {testedForCurrentSettings && (
        <Typography.Text type="secondary">
          {t('settings.notifyAverageLatency', { value: summary?.averageLatencyMs ?? '-' })}
        </Typography.Text>
      )}
    </Drawer>
  );
};
export default NotificationReadinessDrawer;
