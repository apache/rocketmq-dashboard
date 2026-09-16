/* Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. */
import type { GeneralSettings } from '../api/settings';
export type NotificationChannel = 'dingtalk' | 'email' | 'sms';
export type NotificationTestStatus = 'READY' | 'NOT_CONFIGURED' | 'SUCCESS' | 'FAILED';

export interface NotificationChannelResult {
  channel: NotificationChannel;
  status: NotificationTestStatus;
  destination: string;
  recipientCount: number;
  signingEnabled: boolean;
  message: string;
  latencyMs: number | null;
}

export interface NotificationReadinessReport {
  results: NotificationChannelResult[];
  summary: {
    channels: number;
    configured: number;
    unconfigured: number;
    tested: number;
    succeeded: number;
    failed: number;
    recipients: number;
    averageLatencyMs: number | null;
  };
}

const hostOf = (value?: string) => {
  if (!value?.trim()) return '-';
  try {
    return new URL(value).host || '-';
  } catch {
    return 'configured endpoint';
  }
};

const recipients = (value?: string) =>
  value
    ?.split(/[;,\n]/)
    .map((item) => item.trim())
    .filter(Boolean) ?? [];

export const buildNotificationReadiness = (
  settings: GeneralSettings,
): NotificationReadinessReport => {
  const emailRecipients = recipients(settings.emailRecipients);
  const rows: NotificationChannelResult[] = [
    {
      channel: 'dingtalk',
      status:
        settings.dingtalkWebhookConfigured || Boolean(settings.dingtalkWebhook?.trim())
          ? 'READY'
          : 'NOT_CONFIGURED',
      destination: hostOf(settings.dingtalkWebhook),
      recipientCount: 0,
      signingEnabled: Boolean(settings.dingtalkSigningSecretConfigured),
      message:
        settings.dingtalkWebhookConfigured || settings.dingtalkWebhook?.trim()
          ? 'Ready to test'
          : 'Webhook is not configured',
      latencyMs: null,
    },
    {
      channel: 'email',
      status: emailRecipients.length ? 'READY' : 'NOT_CONFIGURED',
      destination: emailRecipients.length ? `${emailRecipients.length} recipients` : '-',
      recipientCount: emailRecipients.length,
      signingEnabled: false,
      message: emailRecipients.length ? 'Ready to test' : 'Recipients are not configured',
      latencyMs: null,
    },
    {
      channel: 'sms',
      status:
        settings.smsWebhookConfigured || Boolean(settings.smsWebhook?.trim())
          ? 'READY'
          : 'NOT_CONFIGURED',
      destination: hostOf(settings.smsWebhook),
      recipientCount: 0,
      signingEnabled: false,
      message:
        settings.smsWebhookConfigured || settings.smsWebhook?.trim()
          ? 'Ready to test'
          : 'Webhook is not configured',
      latencyMs: null,
    },
  ];
  return summarizeNotificationResults(rows);
};

const summarizeNotificationResults = (
  results: NotificationChannelResult[],
): NotificationReadinessReport => {
  const latencies = results
    .map((row) => row.latencyMs)
    .filter((value): value is number => value !== null);
  return {
    results,
    summary: {
      channels: results.length,
      configured: results.filter((row) => row.status !== 'NOT_CONFIGURED').length,
      unconfigured: results.filter((row) => row.status === 'NOT_CONFIGURED').length,
      tested: results.filter((row) => row.status === 'SUCCESS' || row.status === 'FAILED').length,
      succeeded: results.filter((row) => row.status === 'SUCCESS').length,
      failed: results.filter((row) => row.status === 'FAILED').length,
      recipients: results.reduce((sum, row) => sum + row.recipientCount, 0),
      averageLatencyMs: latencies.length
        ? Math.round(latencies.reduce((sum, value) => sum + value, 0) / latencies.length)
        : null,
    },
  };
};

/** 测试已保存且已配置的通道；未配置项保留在结果中，不发请求。 */
export const testReadyNotificationChannels = async (
  readiness: NotificationReadinessReport,
  test: (channel: NotificationChannel) => Promise<void>,
  concurrency = 2,
  clock: () => number = Date.now,
) => {
  const results = readiness.results.map((row) => ({ ...row }));
  const pending = results.filter((row) => row.status === 'READY');
  const requestedConcurrency = Number.isFinite(concurrency) ? Math.floor(concurrency) : 1;
  const workerCount = Math.min(Math.max(1, requestedConcurrency), pending.length);
  let cursor = 0;
  const worker = async () => {
    while (cursor < pending.length) {
      const row = pending[cursor];
      cursor += 1;
      const started = clock();
      try {
        await test(row.channel);
        row.status = 'SUCCESS';
        row.message = 'Test delivered';
      } catch (error) {
        row.status = 'FAILED';
        row.message = error instanceof Error && error.message ? error.message : 'Test failed';
      }
      row.latencyMs = Math.max(0, clock() - started);
    }
  };
  await Promise.all(Array.from({ length: workerCount }, worker));
  return summarizeNotificationResults(results);
};
